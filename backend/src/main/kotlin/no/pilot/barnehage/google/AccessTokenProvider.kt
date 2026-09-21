package no.pilot.barnehage.google

import no.pilot.barnehage.db.TokenRepository
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class AccessTokenProvider(
    private val oauthClient: GoogleOAuthClient,
    private val tokenRepository: TokenRepository,
) {
    private val logger = LoggerFactory.getLogger(AccessTokenProvider::class.java)

    suspend fun getValidAccessToken(parentId: UUID): String? {
        val stored = tokenRepository.find(parentId) ?: return null
        if (stored.expiresAt.isAfter(LocalDateTime.now().plusMinutes(2))) {
            return stored.accessToken
        }

        val refreshed = try {
            oauthClient.refreshAccessToken(stored.refreshToken)
        } catch (e: GoogleTokenRefreshException) {
            // Google most commonly returns invalid_grant when a refresh token has been revoked
            // or has expired due to long inactivity. There is no way to recover the token; the
            // parent must reconnect their Google account. Drop the stale token so we stop
            // retrying, and let the caller treat this the same as "no calendar connected" rather
            // than failing the whole request (e.g. assigning a day to the other parent).
            logger.warn("Kunne ikke fornye Google-tilgangstoken for parentId={}: {}", parentId, e.message)
            tokenRepository.delete(parentId)
            return null
        }
        tokenRepository.upsert(
            parentId = parentId,
            accessToken = refreshed.access_token,

            refreshToken = refreshed.refresh_token ?: stored.refreshToken,
            expiresAt = LocalDateTime.now().plusSeconds(refreshed.expires_in.toLong()),
        )
        return refreshed.access_token
    }
}
