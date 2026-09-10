package no.pilot.barnehage.google

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.Parameters
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
data class GoogleUserInfo(
    val sub: String,
    val email: String,
    val name: String? = null,
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
        ).body()

    suspend fun refreshAccessToken(refreshToken: String): GoogleTokenResponse =
        httpClient.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = Parameters.build {
                append("refresh_token", refreshToken)
                append("client_id", config.clientId)
                append("client_secret", config.clientSecret)
                append("grant_type", "refresh_token")
            },
        ).body()

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
