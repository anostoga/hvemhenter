package no.pilot.barnehage.db

import no.pilot.barnehage.crypto.TokenCipher
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

data class StoredToken(
    val parentId: UUID,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: LocalDateTime,
)

class TokenRepository(private val cipher: TokenCipher, private val database: Database) {

    fun upsert(parentId: UUID, accessToken: String, refreshToken: String, expiresAt: LocalDateTime) = transaction(database) {
        val encryptedAccess = cipher.encrypt(accessToken)
        val encryptedRefresh = cipher.encrypt(refreshToken)
        val expiresAtInstant = expiresAt.atZone(ZoneId.systemDefault()).toInstant()
        val exists = OauthTokensTable.selectAll().where { OauthTokensTable.parentId eq parentId }.any()

        if (exists) {
            OauthTokensTable.update({ OauthTokensTable.parentId eq parentId }) {
                it[accessTokenEnc] = encryptedAccess
                it[refreshTokenEnc] = encryptedRefresh
                it[OauthTokensTable.expiresAt] = expiresAtInstant
            }
        } else {
            OauthTokensTable.insert {
                it[OauthTokensTable.parentId] = parentId
                it[accessTokenEnc] = encryptedAccess
                it[refreshTokenEnc] = encryptedRefresh
                it[OauthTokensTable.expiresAt] = expiresAtInstant
            }
        }
    }

    fun find(parentId: UUID): StoredToken? = transaction(database) {
        OauthTokensTable.selectAll().where { OauthTokensTable.parentId eq parentId }.firstOrNull()?.let { row ->
            StoredToken(
                parentId = row[OauthTokensTable.parentId],
                accessToken = cipher.decrypt(row[OauthTokensTable.accessTokenEnc]),
                refreshToken = cipher.decrypt(row[OauthTokensTable.refreshTokenEnc]),
                expiresAt = LocalDateTime.ofInstant(row[OauthTokensTable.expiresAt], ZoneId.systemDefault()),
            )
        }
    }

    fun delete(parentId: UUID) = transaction(database) {
        OauthTokensTable.deleteWhere { Op.build { OauthTokensTable.parentId eq parentId } }
    }
}
