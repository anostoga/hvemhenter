package no.pilot.barnehage.crypto

import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signerer OAuth `state`-parameteren (parentId + utløpstid) med HMAC-SHA256 for å
 * beskytte callback-endepunktet mot CSRF, uten å måtte holde server-side sesjon.
 */
class StateSigner(secret: String, private val ttlSeconds: Long = 600) {
    private val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")

    fun sign(parentId: String): String {
        val expiresAt = Instant.now().epochSecond + ttlSeconds
        val payload = "$parentId:$expiresAt"
        val mac = Mac.getInstance("HmacSHA256").apply { init(keySpec) }
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8)) + "." + signature
    }

    /** Returnerer parentId hvis state er gyldig og ikke utløpt, ellers null. */
    fun verify(state: String): String? {
        val parts = state.split(".")
        if (parts.size != 2) return null
        val (payloadEncoded, signature) = parts
        val payload = String(Base64.getUrlDecoder().decode(payloadEncoded), Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256").apply { init(keySpec) }
        val expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
        if (expectedSignature != signature) return null

        val segments = payload.split(":")
        if (segments.size != 2) return null
        val (parentId, expiresAtRaw) = segments
        val expiresAt = expiresAtRaw.toLongOrNull() ?: return null
        if (Instant.now().epochSecond > expiresAt) return null
        return parentId
    }
}
