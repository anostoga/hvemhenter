package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.Serializable
import no.pilot.barnehage.auth.SESSION_AUTH_NAME
import no.pilot.barnehage.auth.UserSession
import no.pilot.barnehage.auth.userSession
import no.pilot.barnehage.crypto.StateSigner
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.db.TokenRepository
import no.pilot.barnehage.google.GoogleOAuthClient
import java.time.LocalDateTime
import java.util.UUID

@Serializable
data class ErrorResponse(val error: String)

/**
 * Google OAuth-flyt. `state` er signert (HMAC) og bærer enten:
 * - `reconnect:<parentId>` (allerede innlogget forelder som kobler til/fornyer
 *   kalendertilgangen sin — se `/auth/google/start`), eller
 * - `join:<kode>` (kommer fra /join/start — familieopprettelse/invitasjon, se JoinRoutes).
 * Callback skiller på prefikset og setter/gjenbruker sesjonscookien (UserSession).
 */
fun Route.authRoutes(
    oauthClient: GoogleOAuthClient,
    stateSigner: StateSigner,
    tokenRepository: TokenRepository,
    frontendSuccessUrl: String,
    familyRepository: FamilyRepository,
) {
    // Krever eksisterende sesjon — en forelder kan kun koble til/fornye SIN EGEN
    // kalendertilgang, aldri en annen forelders (parentId hentes fra sesjonen, ikke fra klienten).
    authenticate(SESSION_AUTH_NAME) {
        get("/auth/google/start") {
            val session = call.userSession()!!
            val state = stateSigner.sign("reconnect:${session.parentId}")
            call.respondRedirect(oauthClient.buildAuthorizeUrl(state))
        }
    }

    get("/auth/google/callback") {
        val code = call.parameters["code"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("code mangler"))
        val state = call.parameters["state"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("state mangler"))

        val statePayload = stateSigner.verify(state)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("ugyldig eller utløpt state"))

        val tokenResponse = oauthClient.exchangeCode(code)
        val refreshToken = tokenResponse.refresh_token
            ?: return@get call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse("Google returnerte ikke refresh_token — prøv å koble til på nytt (krever prompt=consent)"),
            )

        if (statePayload.startsWith("join:")) {
            val joinCode = statePayload.removePrefix("join:")
            val userInfo = oauthClient.fetchUserInfo(tokenResponse.access_token)
            val familyId = handleJoin(joinCode, userInfo.sub, userInfo.email, userInfo.name ?: userInfo.email, familyRepository)
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig kode eller familien er full"))

            val parent = familyRepository.findParentByGoogleSub(userInfo.sub)
                ?: return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("uventet feil ved opprettelse"))

            tokenRepository.upsert(
                parentId = parent.id,
                accessToken = tokenResponse.access_token,
                refreshToken = refreshToken,
                expiresAt = LocalDateTime.now().plusSeconds(tokenResponse.expires_in.toLong()),
            )

            call.sessions.set(UserSession(parentId = parent.id.toString(), familyId = familyId))
            call.respondRedirect(frontendSuccessUrl)
            return@get
        }

        if (statePayload.startsWith("reconnect:")) {
            val parentId = UUID.fromString(statePayload.removePrefix("reconnect:"))
            tokenRepository.upsert(
                parentId = parentId,
                accessToken = tokenResponse.access_token,
                refreshToken = refreshToken,
                expiresAt = LocalDateTime.now().plusSeconds(tokenResponse.expires_in.toLong()),
            )
            call.respondRedirect(frontendSuccessUrl)
            return@get
        }

        call.respond(HttpStatusCode.BadRequest, ErrorResponse("ukjent state-type"))
    }
}
