package no.pilot.barnehage.google

import no.pilot.barnehage.db.TokenRepository
import java.time.LocalDateTime
import java.util.UUID

/**
 * Henter en gyldig access token for en forelder, og fornyer automatisk via
 * refresh_token hvis den er utløpt (eller utløper snart).
 */
class AccessTokenProvider(
    private val oauthClient: GoogleOAuthClient,
    private val tokenRepository: TokenRepository,
) {
    suspend fun getValidAccessToken(parentId: UUID): String? {
        val stored = tokenRepository.find(parentId) ?: return null
        if (stored.expiresAt.isAfter(LocalDateTime.now().plusMinutes(2))) {
            return stored.accessToken
        }

        val refreshed = oauthClient.refreshAccessToken(stored.refreshToken)
        tokenRepository.upsert(
            parentId = parentId,
            accessToken = refreshed.access_token,
            // Google returnerer ikke alltid en ny refresh_token — behold den forrige hvis fraværende.
            refreshToken = refreshed.refresh_token ?: stored.refreshToken,
            expiresAt = LocalDateTime.now().plusSeconds(refreshed.expires_in.toLong()),
        )
        return refreshed.access_token
    }
}
