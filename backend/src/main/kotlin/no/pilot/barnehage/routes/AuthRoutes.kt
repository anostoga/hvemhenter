package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
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

/** Svar fra /auth/whoami — brukt av frontend til å vise riktig meny (innlogget/ikke)
 * uten å måtte kalle et familie-scopet API-endepunkt under /api (som ville 401 for uinnloggede
 * og dermed tvinge frem en redirect). Navnet her er brukerens eget, hentet fra databasen
 * via parentId i sesjonen — IKKE lagret i selve cookien (se UserSession/SessionAuth.kt). */
@Serializable
data class WhoAmIResponse(val loggedIn: Boolean, val name: String? = null)

/**
 * Offentlige ruter som må fungere for BÅDE innloggede og uinnloggede uten å svare 401,
 * slik at forsiden kan vise riktig meny for alle uten å bli tvunget videre til /join.
 * Leser cookien direkte (call.sessions.get), ikke via authenticate{}/principal —
 * sistnevnte krever et vellykket auth-pipeline-løp og finnes derfor ikke utenfor
 * authenticate(SESSION_AUTH_NAME) { }-blokker. Utskilt i egen funksjon slik at den
 * kan testes uten å måtte konstruere en ekte GoogleOAuthClient/StateSigner.
 */
fun Route.registerWhoAmIAndLogout(familyRepository: FamilyRepository) {
    get("/auth/whoami") {
        val session = call.sessions.get<UserSession>()
        if (session == null) {
            call.respond(HttpStatusCode.OK, WhoAmIResponse(loggedIn = false))
            return@get
        }
        val parent = familyRepository.findParent(UUID.fromString(session.parentId))
        call.respond(HttpStatusCode.OK, WhoAmIResponse(loggedIn = true, name = parent?.name))
    }

    // Å logge ut når man allerede er logget ut skal bare være en no-op, ikke en feil.
    // POST (ikke GET) fordi dette er en muterende handling.
    post("/auth/logout") {
        call.sessions.clear<UserSession>()
        call.respond(HttpStatusCode.OK)
    }
}

/**
 * Google OAuth-flyt. `state` er signert (HMAC) og bærer enten:
 * - `reconnect:<parentId>` (allerede innlogget forelder som kobler til/fornyer
 *   kalendertilgangen sin — se `/auth/google/start`),
 * - `join:<kode>` (kommer fra /join/start — familieopprettelse/invitasjon, se JoinRoutes), eller
 * - `login` (kommer fra /auth/login — vanlig innlogging for en forelder som ALLEREDE
 *   er registrert i en familie fra før, uten å måtte oppgi en kode på nytt).
 * Callback skiller på prefikset og setter/gjenbruker sesjonscookien (UserSession).
 */
fun Route.authRoutes(
    oauthClient: GoogleOAuthClient,
    stateSigner: StateSigner,
    tokenRepository: TokenRepository,
    frontendSuccessUrl: String,
    frontendJoinUrl: String,
    familyRepository: FamilyRepository,
) {
    registerWhoAmIAndLogout(familyRepository)

    // Innlogging for en forelder som ALLEREDE er medlem av en familie — krever ingen
    // kode (i motsetning til /join/start). Går via samme Google OAuth-flyt; callback
    // ser `state == "login"` og slår opp forelderen på google_sub. Finnes ingen
    // forelder med den sub-en ennå, sendes brukeren til /join (må ha en kode først).
    get("/auth/login") {
        val state = stateSigner.sign("login")
        call.respondRedirect(oauthClient.buildAuthorizeUrl(state))
    }

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

        if (statePayload == "login") {
            val userInfo = oauthClient.fetchUserInfo(tokenResponse.access_token)
            val parent = familyRepository.findParentByGoogleSub(userInfo.sub)
                // Ikke registrert i noen familie ennå — /auth/login oppretter ALDRI en ny
                // familie (det er kun /join/start sin jobb). Send til join-siden i stedet.
                ?: return@get call.respondRedirect("$frontendJoinUrl?error=ikke_registrert")

            tokenRepository.upsert(
                parentId = parent.id,
                accessToken = tokenResponse.access_token,
                refreshToken = refreshToken,
                expiresAt = LocalDateTime.now().plusSeconds(tokenResponse.expires_in.toLong()),
            )

            call.sessions.set(UserSession(parentId = parent.id.toString(), familyId = parent.familyId.toString()))
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
