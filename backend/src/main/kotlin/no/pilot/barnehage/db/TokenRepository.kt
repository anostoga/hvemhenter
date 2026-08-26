package no.pilot.barnehage.db

import no.pilot.barnehage.crypto.TokenCipher
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

data class StoredToken(
    val parentId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: LocalDateTime,
    val googleCalendarId: String?,
)

/** Leser/skriver krypterte OAuth-tokens. Tokens er aldri i klartekst i databasen eller i logger. */
class TokenRepository(private val cipher: TokenCipher) {

    fun upsert(parentId: String, accessToken: String, refreshToken: String, expiresAt: LocalDateTime, googleCalendarId: String?) = transaction {
        val encryptedAccess = cipher.encrypt(accessToken)
        val encryptedRefresh = cipher.encrypt(refreshToken)
        val exists = Tokens.selectAll().where { Tokens.parentId eq parentId }.any()

        if (exists) {
            Tokens.update({ Tokens.parentId eq parentId }) {
                it[accessTokenEnc] = encryptedAccess
                it[refreshTokenEnc] = encryptedRefresh
                it[Tokens.expiresAt] = expiresAt
                if (googleCalendarId != null) it[Tokens.googleCalendarId] = googleCalendarId
            }
        } else {
            Tokens.insert {
                it[Tokens.parentId] = parentId
                it[accessTokenEnc] = encryptedAccess
                it[refreshTokenEnc] = encryptedRefresh
                it[Tokens.expiresAt] = expiresAt
                it[Tokens.googleCalendarId] = googleCalendarId
            }
        }
    }

    fun find(parentId: String): StoredToken? = transaction {
        Tokens.selectAll().where { Tokens.parentId eq parentId }.firstOrNull()?.let { row ->
            StoredToken(
                parentId = row[Tokens.parentId],
                accessToken = cipher.decrypt(row[Tokens.accessTokenEnc]),
                refreshToken = cipher.decrypt(row[Tokens.refreshTokenEnc]),
                expiresAt = row[Tokens.expiresAt],
                googleCalendarId = row[Tokens.googleCalendarId],
            )
        }
    }

    fun delete(parentId: String) = transaction {
        Tokens.deleteWhere { Op.build { Tokens.parentId eq parentId } }
    }
}
