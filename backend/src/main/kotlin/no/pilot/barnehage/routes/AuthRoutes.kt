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
import no.pilot.barnehage.auth.bindNonceToStatePayload
import no.pilot.barnehage.auth.consumeOAuthNonce
import no.pilot.barnehage.auth.issueOAuthNonce
import no.pilot.barnehage.auth.unbindNonceFromStatePayload
import no.pilot.barnehage.auth.userSession
import no.pilot.barnehage.crypto.StateSigner
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.db.JoinFamilyResult
import no.pilot.barnehage.db.OnboardingRepository
import no.pilot.barnehage.db.TokenRepository
import no.pilot.barnehage.google.GoogleOAuthClient
import no.pilot.barnehage.Env
import java.time.LocalDateTime
import java.util.UUID

@Serializable
data class ErrorResponse(val error: String)

private fun isConfiguredAdminEmail(email: String): Boolean =
    Env.get("ADMIN_EMAILS")?.split(",")?.map { it.trim().lowercase() }?.contains(email.lowercase()) == true

/**
 * Gir admin-rettigheter basert på en statisk e-postliste i miljøvariabelen `ADMIN_EMAILS`.
 * `emailVerified = false` (en ekte Google-konto der e-posten ikke er bekreftet) nekter dette —
 * uten den sjekken kunne noen i teorien hevde en vilkårlig e-postadresse og få adminrettigheter.
 * Mock-innlogging og reconnect har ingen `GoogleUserInfo` å sjekke mot og sender `true`.
 */
internal fun FamilyRepository.promoteToAdminIfConfigured(parent: no.pilot.barnehage.db.ParentRecord, emailVerified: Boolean = true) {
    if (!parent.isAdmin && emailVerified && parent.email != null && isConfiguredAdminEmail(parent.email)) {
        setAdmin(parent.id, true)
    }
}

private fun secureCookiesEnabled(): Boolean = Env.get("SESSION_COOKIE_SECURE")?.toBooleanStrictOrNull() ?: true

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

/**
 * Oppretter en midlertidig oppstartsøkt for en Google-bruker vi ikke kjenner fra før, og sender
 * videre til /velkommen der brukeren velger "opprett ny familie" eller "bli med med kode" (se
 * OnboardingRoutes.kt). Brukes av både ekte Google-innlogging og mock-innlogging.
 */
private suspend fun ApplicationCall.startOnboarding(
    onboardingRepository: OnboardingRepository,
    frontendOnboardingUrl: String,
    googleSub: String,
    email: String,
    name: String,
    accessToken: String,
    refreshToken: String,
    expiresAt: LocalDateTime,
) {
    onboardingRepository.purgeExpired()
    val handle = onboardingRepository.create(googleSub, email, name, accessToken, refreshToken, expiresAt)
    response.cookies.append(
        io.ktor.http.Cookie(
            name = "bhg_onboarding",
            value = handle.reference,
            path = "/auth/onboarding",
            maxAge = 600,
            secure = secureCookiesEnabled(),
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
    respondRedirect(frontendOnboardingUrl)
}

fun Route.registerMockGoogleLogin(
    familyRepository: FamilyRepository,
    tokenRepository: TokenRepository,
    onboardingRepository: OnboardingRepository,
    frontendSuccessUrl: String,
    frontendOnboardingUrl: String,
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

        val existing = familyRepository.findParentByGoogleSub(mockGoogleSub)
        if (existing == null && code == null) {
            // Ukjent mock-bruker uten kode: gå gjennom samme oppstartsvalg som ekte Google-innlogging.
            call.startOnboarding(
                onboardingRepository,
                frontendOnboardingUrl,
                mockGoogleSub,
                email,
                name,
                accessToken = "mock-access-token",
                refreshToken = "mock-refresh-token",
                expiresAt = LocalDateTime.now().plusDays(365),
            )
            return@get
        }

        val familyId = when {
            existing != null -> existing.familyId.toString()
            code != null -> {
                // Dev-snarvei: bli med direkte med en invitasjonskode uten å gå via /velkommen.
                when (val result = familyRepository.joinFamilyWithInviteCode(code, mockGoogleSub, email, name)) {
                    is JoinFamilyResult.Joined -> result.parent.familyId.toString()
                    is JoinFamilyResult.AlreadyMember -> result.parent.familyId.toString()
                    JoinFamilyResult.InvalidCode, JoinFamilyResult.FamilyFull ->
                        return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig kode eller familien er full"))
                }
            }
            else -> return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("uventet tilstand"))
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
      <p>La koden stå tom for å oppleve den vanlige flyten: ukjent bruker sendes til
         /velkommen for å velge "opprett ny familie" eller "bli med med kode". Fyll inn en
         invitasjonskode her KUN som en snarvei forbi /velkommen under utvikling.
         Allerede registrerte mock-brukere (samme navn/e-post) logges rett inn uansett.</p>
      <form method="get" action="/auth/mock-login">
        <label>Navn <input name="name" required></label><br>
        <label>E-post (valgfri) <input name="email"></label><br>
        <label>Invitasjonskode (valgfri, kun dev-snarvei) <input name="code"></label><br>
        <button type="submit">Logg inn</button>
      </form>
    </body>
    </html>
""".trimIndent()

fun Route.authRoutes(
    oauthClient: GoogleOAuthClient,
    stateSigner: StateSigner,
    tokenRepository: TokenRepository,
    onboardingRepository: OnboardingRepository,
    frontendSuccessUrl: String,
    frontendOnboardingUrl: String,
    familyRepository: FamilyRepository,
) {
    registerWhoAmIAndLogout(familyRepository)

    get("/auth/login") {
        val nonce = call.issueOAuthNonce(secureCookiesEnabled())
        val state = stateSigner.sign(bindNonceToStatePayload(nonce, "login"))
        call.respondRedirect(oauthClient.buildAuthorizeUrl(state))
    }

    if (Env.get("MOCK_GOOGLE_AUTH")?.toBooleanStrictOrNull() == true) {
        registerMockGoogleLogin(familyRepository, tokenRepository, onboardingRepository, frontendSuccessUrl, frontendOnboardingUrl)
    }

    authenticate(SESSION_AUTH_NAME) {
        get("/auth/google/start") {
            val session = call.userSession()!!
            val nonce = call.issueOAuthNonce(secureCookiesEnabled())
            val state = stateSigner.sign(bindNonceToStatePayload(nonce, "reconnect:${session.parentId}"))
            call.respondRedirect(oauthClient.buildAuthorizeUrl(state))
        }
    }

    get("/auth/google/callback") {
        val code = call.parameters["code"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("code mangler"))
        val state = call.parameters["state"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("state mangler"))

        val signedPayload = stateSigner.verify(state)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("ugyldig eller utløpt state"))

        val cookieNonce = call.consumeOAuthNonce(secureCookiesEnabled())
        val statePayload = unbindNonceFromStatePayload(signedPayload, cookieNonce)
            ?: return@get call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse("innloggingen ble ikke startet i denne nettleseren — prøv på nytt"),
            )

        val tokenResponse = oauthClient.exchangeCode(code)
        val refreshToken = tokenResponse.refresh_token
            ?: return@get call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse("Google returnerte ikke refresh_token — prøv å koble til på nytt (krever prompt=consent)"),
            )
        val expiresAt = LocalDateTime.now().plusSeconds(tokenResponse.expires_in.toLong())

        if (statePayload.startsWith("join:")) {
            val joinCode = statePayload.removePrefix("join:")
            val userInfo = oauthClient.fetchUserInfo(tokenResponse.access_token)

            val result = runCatching {
                familyRepository.joinFamilyWithInviteCode(joinCode, userInfo.sub, userInfo.email, userInfo.name ?: userInfo.email)
            }.getOrElse {
                // google_sub finnes allerede (reell samtidighet med en annen fullføring) — logg inn på den i stedet.
                familyRepository.findParentByGoogleSub(userInfo.sub)?.let { JoinFamilyResult.AlreadyMember(it) }
                    ?: return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("uventet feil ved innmelding"))
            }

            val parent = when (result) {
                is JoinFamilyResult.Joined -> result.parent
                is JoinFamilyResult.AlreadyMember -> result.parent
                JoinFamilyResult.InvalidCode ->
                    return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig eller allerede brukt kode"))
                JoinFamilyResult.FamilyFull ->
                    return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("familien har allerede to foreldre"))
            }
            familyRepository.promoteToAdminIfConfigured(parent, userInfo.email_verified)

            tokenRepository.upsert(parent.id, tokenResponse.access_token, refreshToken, expiresAt)
            call.sessions.set(UserSession(parentId = parent.id.toString(), familyId = parent.familyId.toString()))
            call.respondRedirect(frontendSuccessUrl)
            return@get
        }

        if (statePayload == "login") {
            val userInfo = oauthClient.fetchUserInfo(tokenResponse.access_token)
            val parent = familyRepository.findParentByGoogleSub(userInfo.sub)

            if (parent == null) {
                call.startOnboarding(
                    onboardingRepository,
                    frontendOnboardingUrl,
                    userInfo.sub,
                    userInfo.email,
                    userInfo.name ?: userInfo.email,
                    tokenResponse.access_token,
                    refreshToken,
                    expiresAt,
                )
                return@get
            }
            familyRepository.promoteToAdminIfConfigured(parent, userInfo.email_verified)

            tokenRepository.upsert(parent.id, tokenResponse.access_token, refreshToken, expiresAt)
            call.sessions.set(UserSession(parentId = parent.id.toString(), familyId = parent.familyId.toString()))
            call.respondRedirect(frontendSuccessUrl)
            return@get
        }

        if (statePayload.startsWith("reconnect:")) {
            val parentId = UUID.fromString(statePayload.removePrefix("reconnect:"))
            tokenRepository.upsert(parentId, tokenResponse.access_token, refreshToken, expiresAt)
            call.respondRedirect(frontendSuccessUrl)
            return@get
        }

        call.respond(HttpStatusCode.BadRequest, ErrorResponse("ukjent state-type"))
    }
}
