package no.pilot.barnehage.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enkel AES-256-GCM-kryptering for OAuth-tokens lagret i Postgres.
 * Nøkkelen kommer fra miljøvariabelen TOKEN_ENCRYPTION_KEY (base64, 32 byte).
 * Genererer en ny tilfeldig IV per kryptering og lagrer den sammen med chifferteksten.
 */
class TokenCipher(base64Key: String) {
    private val keySpec = SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES")
    private val secureRandom = SecureRandom()

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    fun decrypt(encoded: String): String {
        val combined = Base64.getDecoder().decode(encoded)
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128

        /** Genererer en ny base64-kodet 256-bit nøkkel, til bruk ved førstegangsoppsett. */
        fun generateKey(): String {
            val key = ByteArray(32)
            SecureRandom().nextBytes(key)
            return Base64.getEncoder().encodeToString(key)
        }
    }
}
