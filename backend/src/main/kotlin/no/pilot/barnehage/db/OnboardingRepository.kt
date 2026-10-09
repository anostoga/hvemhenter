package no.pilot.barnehage.db

import no.pilot.barnehage.crypto.TokenCipher
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64

data class OnboardingSessionRecord(
    val googleSub: String,
    val email: String,
    val name: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenExpiresAt: LocalDateTime,
    val csrfToken: String,
)

/** Resultat av å opprette en ny oppstartsøkt: cookie-referansen og CSRF-tokenet frontend må sende tilbake. */
data class OnboardingHandle(val reference: String, val csrfToken: String)

/**
 * Midlertidig, kortlivet lagring av Google-identitet + OAuth-tokens for en bruker som har
 * logget inn med Google for første gang, men ennå ikke valgt om hen vil opprette en ny familie
 * eller bli med i en eksisterende (se OnboardingRoutes.kt). Lagret i Postgres (ikke i minnet i
 * prosessen) slik at valget overlever på tvers av backend-instanser/omstart.
 *
 * Cookien brukeren får inneholder kun en tilfeldig 256-bit referanse — aldri selve
 * Google-identiteten eller tokens. Referansen hashes med SHA-256 før oppslag/lagring, slik at en
 * lekkasje av databasen alene ikke gir tilgang til en pågående økt.
 */
class OnboardingRepository(private val cipher: TokenCipher, private val database: Database) {

    private val secureRandom = SecureRandom()

    fun create(
        googleSub: String,
        email: String,
        name: String,
        accessToken: String,
        refreshToken: String,
        tokenExpiresAt: LocalDateTime,
        ttlSeconds: Long = 600,
    ): OnboardingHandle = transaction(database) {
        // Én pågående oppstartsøkt per Google-bruker — en ny innlogging erstatter en gammel.
        OnboardingSessionsTable.deleteWhere { Op.build { OnboardingSessionsTable.googleSub eq googleSub } }

        val reference = randomToken(32)
        val csrfToken = randomToken(24)
        val expiresAtInstant = Instant.now().plusSeconds(ttlSeconds)

        OnboardingSessionsTable.insert {
            it[tokenHash] = sha256Hex(reference)
            it[OnboardingSessionsTable.googleSub] = googleSub
            it[OnboardingSessionsTable.email] = email
            it[OnboardingSessionsTable.name] = name
            it[accessTokenEnc] = cipher.encrypt(accessToken)
            it[refreshTokenEnc] = cipher.encrypt(refreshToken)
            it[OnboardingSessionsTable.tokenExpiresAt] = tokenExpiresAt.atZone(ZoneId.systemDefault()).toInstant()
            it[OnboardingSessionsTable.csrfToken] = csrfToken
            it[expiresAt] = expiresAtInstant
        }

        OnboardingHandle(reference, csrfToken)
    }

    fun find(reference: String): OnboardingSessionRecord? = transaction(database) {
        val hash = sha256Hex(reference)
        val row = OnboardingSessionsTable.selectAll().where { OnboardingSessionsTable.tokenHash eq hash }.firstOrNull()
            ?: return@transaction null

        if (row[OnboardingSessionsTable.expiresAt].isBefore(Instant.now())) {
            OnboardingSessionsTable.deleteWhere { Op.build { OnboardingSessionsTable.id eq row[OnboardingSessionsTable.id] } }
            return@transaction null
        }

        OnboardingSessionRecord(
            googleSub = row[OnboardingSessionsTable.googleSub],
            email = row[OnboardingSessionsTable.email],
            name = row[OnboardingSessionsTable.name],
            accessToken = cipher.decrypt(row[OnboardingSessionsTable.accessTokenEnc]),
            refreshToken = cipher.decrypt(row[OnboardingSessionsTable.refreshTokenEnc]),
            tokenExpiresAt = LocalDateTime.ofInstant(row[OnboardingSessionsTable.tokenExpiresAt], ZoneId.systemDefault()),
            csrfToken = row[OnboardingSessionsTable.csrfToken],
        )
    }

    /** Engangsbruk — kalles når oppstarten er fullført (familie opprettet/koblet til), eller avbrutt. */
    fun consume(reference: String) = transaction(database) {
        OnboardingSessionsTable.deleteWhere { Op.build { OnboardingSessionsTable.tokenHash eq sha256Hex(reference) } }
    }

    /** Rydder utløpte økter. Kalles best-effort ved oppstart av nye økter for å unngå ubegrenset vekst. */
    fun purgeExpired() = transaction(database) {
        OnboardingSessionsTable.deleteWhere { Op.build { OnboardingSessionsTable.expiresAt less Instant.now() } }
    }

    private fun randomToken(bytes: Int): String {
        val raw = ByteArray(bytes)
        secureRandom.nextBytes(raw)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
