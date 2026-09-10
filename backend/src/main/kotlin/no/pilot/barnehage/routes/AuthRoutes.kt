package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
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
import no.pilot.barnehage.Env
import java.time.LocalDateTime
import java.util.UUID

@Serializable
data class ErrorResponse(val error: String)

private fun isConfiguredAdminEmail(email: String): Boolean =
    Env.get("ADMIN_EMAILS")?.split(",")?.map { it.trim().lowercase() }?.contains(email.lowercase()) == true

private fun FamilyRepository.promoteToAdminIfConfigured(parent: no.pilot.barnehage.db.ParentRecord) {
    if (!parent.isAdmin && parent.email != null && isConfiguredAdminEmail(parent.email)) {
        setAdmin(parent.id, true)
    }
}

@Serializable
data class WhoAmIResponse(val loggedIn: Boolean, val name: String? = null, val avatar: String? = null, val isAdmin: Boolean = false)

fun Route.registerWhoAmIAndLogout(familyRepository: FamilyRepository) {
    get("/auth/whoami") {
        val session = call.sessions.get<UserSession>()
        if (session == null) {
            call.respond(HttpStatusCode.OK, WhoAmIResponse(loggedIn = false))
            return@get
        }
        val parent = familyRepository.findParent(UUID.fromString(session.parentId))
        call.respond(HttpStatusCode.OK, WhoAmIResponse(loggedIn = true, name = parent?.name, avatar = parent?.avatar, isAdmin = parent?.isAdmin == true))
    }

    post("/auth/logout") {
        call.sessions.clear<UserSession>()
        call.respond(HttpStatusCode.OK)
    }
}

fun Route.registerMockGoogleLogin(
    familyRepository: FamilyRepository,
    tokenRepository: TokenRepository,
    frontendSuccessUrl: String,
    frontendJoinUrl: String,
    adminRepository: no.pilot.barnehage.db.AdminRepository? = null,
) {
    get("/auth/mock-login") {
        val name = call.parameters["name"]
        if (name.isNullOrBlank()) {
            call.respondText(mockLoginFormHtml(), contentType = io.ktor.http.ContentType.Text.Html)
            return@get
        }

        val email = call.parameters["email"]?.takeIf { it.isNotBlank() } ?: "${name.lowercase().replace(" ", ".")}@mock.local"
        val code = call.parameters["code"]?.takeIf { it.isNotBlank() }
        val mockGoogleSub = "mock:$email"

        val familyId = if (code != null) {
            handleJoin(code, mockGoogleSub, email, name, familyRepository, adminRepository)
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig kode eller familien er full"))
        } else {
            familyRepository.findParentByGoogleSub(mockGoogleSub)?.familyId?.toString()
                ?: return@get call.respondRedirect("$frontendJoinUrl?error=ikke_registrert")
        }

        val parent = familyRepository.findParentByGoogleSub(mockGoogleSub)
            ?: return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("uventet feil i mock-innlogging"))
        familyRepository.promoteToAdminIfConfigured(parent)

        tokenRepository.upsert(
            parentId = parent.id,
            accessToken = "mock-access-token",
            refreshToken = "mock-refresh-token",
            expiresAt = LocalDateTime.now().plusDays(365),
        )

        call.sessions.set(UserSession(parentId = parent.id.toString(), familyId = familyId))
        call.respondRedirect(frontendSuccessUrl)
    }
}

private fun mockLoginFormHtml(): String = """
    <!DOCTYPE html>
    <html lang="no">
    <body style="font-family: system-ui; max-width: 480px; margin: 2rem auto;">
      <h1>Mock Google-innlogging (kun lokal dev)</h1>
      <p>Fyll inn en kode KUN hvis du oppretter en ny familie eller blir med i en via
         invitasjonskode — la den stå tom for å logge inn som en allerede registrert testbruker.</p>
      <form method="get" action="/auth/mock-login">
        <label>Navn <input name="name" required></label><br>
        <label>E-post (valgfri) <input name="email"></label><br>
        <label>Kode (valgfri) <input name="code"></label><br>
        <button type="submit">Logg inn</button>
      </form>
    </body>
    </html>
""".trimIndent()

fun Route.authRoutes(
    oauthClient: GoogleOAuthClient,
    stateSigner: StateSigner,
    tokenRepository: TokenRepository,
    frontendSuccessUrl: String,
    frontendJoinUrl: String,
    familyRepository: FamilyRepository,
    adminRepository: no.pilot.barnehage.db.AdminRepository? = null,
) {
    registerWhoAmIAndLogout(familyRepository)

    get("/auth/login") {
        val state = stateSigner.sign("login")
        call.respondRedirect(oauthClient.buildAuthorizeUrl(state))
    }

    if (Env.get("MOCK_GOOGLE_AUTH")?.toBooleanStrictOrNull() == true) {
        registerMockGoogleLogin(familyRepository, tokenRepository, frontendSuccessUrl, frontendJoinUrl, adminRepository)
    }

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
            val familyId = handleJoin(joinCode, userInfo.sub, userInfo.email, userInfo.name ?: userInfo.email, familyRepository, adminRepository)
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig kode eller familien er full"))

            val parent = familyRepository.findParentByGoogleSub(userInfo.sub)
                ?: return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("uventet feil ved opprettelse"))
            familyRepository.promoteToAdminIfConfigured(parent)

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

                ?: return@get call.respondRedirect("$frontendJoinUrl?error=ikke_registrert")
            familyRepository.promoteToAdminIfConfigured(parent)

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
