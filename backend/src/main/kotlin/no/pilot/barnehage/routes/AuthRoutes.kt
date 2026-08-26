package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import no.pilot.barnehage.AppConfig
import no.pilot.barnehage.crypto.StateSigner
import no.pilot.barnehage.db.TokenRepository
import no.pilot.barnehage.google.GoogleOAuthClient
import java.time.LocalDateTime

@Serializable
data class ErrorResponse(val error: String)

/**
 * Google OAuth-flyt per forelder. `state` er signert (HMAC) og inneholder parentId + utløp,
 * slik at vi ikke trenger server-side sesjon og callback ikke kan forfalskes (CSRF-beskyttelse).
 */
fun Route.authRoutes(
    oauthClient: GoogleOAuthClient,
    stateSigner: StateSigner,
    tokenRepository: TokenRepository,
    frontendSuccessUrl: String,
    config: AppConfig,
) {
    get("/auth/google/{parentId}/start") {
        val parentId = call.parameters["parentId"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("parentId mangler"))
        val state = stateSigner.sign(parentId)
        call.respondRedirect(oauthClient.buildAuthorizeUrl(state))
    }

    get("/auth/google/callback") {
        val code = call.parameters["code"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("code mangler"))
        val state = call.parameters["state"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("state mangler"))

        val parentId = stateSigner.verify(state)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("ugyldig eller utløpt state"))

        val tokenResponse = oauthClient.exchangeCode(code)
        val refreshToken = tokenResponse.refresh_token
            ?: return@get call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse("Google returnerte ikke refresh_token — prøv å koble til på nytt (krever prompt=consent)"),
            )

        tokenRepository.upsert(
            parentId = parentId,
            accessToken = tokenResponse.access_token,
            refreshToken = refreshToken,
            expiresAt = LocalDateTime.now().plusSeconds(tokenResponse.expires_in.toLong()),
            // Begge foreldre sjekker ledighet mot samme delte kalender (SHARED_CALENDAR_ID),
            // ikke sin egen personlige "primary"-kalender.
            googleCalendarId = config.sharedCalendarId,
        )

        call.respondRedirect(frontendSuccessUrl)
    }
}
