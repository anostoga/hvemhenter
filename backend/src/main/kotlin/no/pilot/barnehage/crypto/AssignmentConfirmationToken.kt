package no.pilot.barnehage.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class AssignmentConfirmationToken(val value: String, val hash: String)

object AssignmentConfirmationTokens {
    private val secureRandom = SecureRandom()

    fun generate(): AssignmentConfirmationToken {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return AssignmentConfirmationToken(value, hash)
    }

    fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
