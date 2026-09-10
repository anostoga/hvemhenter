package no.pilot.barnehage.crypto

import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class StateSigner(secret: String, private val ttlSeconds: Long = 600) {
    private val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")

    fun sign(parentId: String): String {
        val expiresAt = Instant.now().epochSecond + ttlSeconds
        val payload = "$parentId:$expiresAt"
        val mac = Mac.getInstance("HmacSHA256").apply { init(keySpec) }
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8)) + "." + signature
    }

    fun verify(state: String): String? {
        val parts = state.split(".")
        if (parts.size != 2) return null
        val (payloadEncoded, signature) = parts
        val payload = String(Base64.getUrlDecoder().decode(payloadEncoded), Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256").apply { init(keySpec) }
        val expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))
        if (expectedSignature != signature) return null

        val lastColon = payload.lastIndexOf(":")
        if (lastColon == -1) return null
        val parentId = payload.substring(0, lastColon)
        val expiresAtRaw = payload.substring(lastColon + 1)
        val expiresAt = expiresAtRaw.toLongOrNull() ?: return null
        if (Instant.now().epochSecond > expiresAt) return null
        return parentId
    }
}
