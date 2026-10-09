package no.pilot.barnehage.google

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import no.pilot.barnehage.Env

@Serializable
data class GoogleTokenResponse(
    val access_token: String,
    val expires_in: Int,
    val refresh_token: String? = null,
    val scope: String? = null,
    val token_type: String? = null,
)

@Serializable
data class GoogleTokenErrorResponse(
    val error: String,
    val error_description: String? = null,
)

/**
 * Thrown when Google rejects a token exchange/refresh request, e.g. because a refresh token has
 * been revoked or has expired from inactivity ("invalid_grant"). Distinct from a malformed
 * success response so callers can tell "Google said no" apart from "we couldn't parse a 200".
 */
class GoogleTokenRefreshException(val error: String, val errorDescription: String?) :
    Exception("Google avviste token-forespørselen: $error${errorDescription?.let { " ($it)" } ?: ""}")

@Serializable
data class GoogleUserInfo(
    val sub: String,
    val email: String,
    val name: String? = null,
    val email_verified: Boolean = false,
)

data class GoogleOAuthConfig(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
) {
    companion object {

        fun fromEnv(): GoogleOAuthConfig {
            val mockAuth = Env.get("MOCK_GOOGLE_AUTH")?.toBooleanStrictOrNull() == true
            return GoogleOAuthConfig(
                clientId = Env.get("GOOGLE_CLIENT_ID") ?: if (mockAuth) "mock-client-id" else error("GOOGLE_CLIENT_ID mangler"),
                clientSecret = Env.get("GOOGLE_CLIENT_SECRET") ?: if (mockAuth) "mock-client-secret" else error("GOOGLE_CLIENT_SECRET mangler"),
                redirectUri = Env.get("GOOGLE_REDIRECT_URI") ?: if (mockAuth) "http://localhost:3000/auth/google/callback" else error("GOOGLE_REDIRECT_URI mangler"),
            )
        }
    }
}

private val CALENDAR_SCOPES = listOf(
    "openid",
    "email",
    "profile",
    "https://www.googleapis.com/auth/calendar.events",
    "https://www.googleapis.com/auth/calendar.readonly",
)

class GoogleOAuthClient(
    private val httpClient: HttpClient,
    private val config: GoogleOAuthConfig,
) {
    fun buildAuthorizeUrl(state: String): String {
        val scope = CALENDAR_SCOPES.joinToString(" ")
        return "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=${config.clientId}" +
            "&redirect_uri=${config.redirectUri}" +
            "&response_type=code" +
            "&access_type=offline" +
            "&prompt=consent" +
            "&scope=${scope.encodeUrl()}" +
            "&state=${state.encodeUrl()}"
    }

    suspend fun exchangeCode(code: String): GoogleTokenResponse =
        httpClient.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = Parameters.build {
                append("code", code)
                append("client_id", config.clientId)
                append("client_secret", config.clientSecret)
                append("redirect_uri", config.redirectUri)
                append("grant_type", "authorization_code")
            },
        ).toTokenResponseOrThrow()

    suspend fun refreshAccessToken(refreshToken: String): GoogleTokenResponse =
        httpClient.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = Parameters.build {
                append("refresh_token", refreshToken)
                append("client_id", config.clientId)
                append("client_secret", config.clientSecret)
                append("grant_type", "refresh_token")
            },
        ).toTokenResponseOrThrow()

    /**
     * Google returns 4xx with an `{"error": "..."}` body (e.g. invalid_grant when a refresh
     * token was revoked or expired from long inactivity) instead of a GoogleTokenResponse.
     * Deserializing that error body straight into GoogleTokenResponse previously blew up with a
     * confusing "missing fields" error. Check the status first and surface a clear exception.
     */
    private suspend fun HttpResponse.toTokenResponseOrThrow(): GoogleTokenResponse {
        if (!status.isSuccess()) {
            val raw = bodyAsText()
            val parsed = runCatching {
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(GoogleTokenErrorResponse.serializer(), raw)
            }.getOrNull()
            throw GoogleTokenRefreshException(parsed?.error ?: "ukjent_feil", parsed?.error_description ?: raw)
        }
        return body()
    }

    suspend fun revokeToken(token: String) {
        httpClient.submitForm(
            url = "https://oauth2.googleapis.com/revoke",
            formParameters = Parameters.build {
                append("token", token)
            },
        )
    }

    suspend fun fetchUserInfo(accessToken: String): GoogleUserInfo =
        httpClient.get("https://openidconnect.googleapis.com/v1/userinfo") {
            header("Authorization", "Bearer $accessToken")
        }.body()

    private fun String.encodeUrl(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8)
}
