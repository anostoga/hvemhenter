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
 * Mock-innlogging for lokal utvikling — helt frakoblet ekte Google OAuth, så du
 * slipper å sette opp en Google Cloud-klient bare for å teste appen lokalt.
 * MÅ ALDRI skrus på utenfor lokal dev (se MOCK_GOOGLE_AUTH-sjekken i authRoutes()
 * over — kun lest fra .env, aldri satt i Fly.io/Vercel-hemmeligheter).
 *
 * "google_sub" for en mock-bruker er deterministisk avledet fra e-posten
 * (`mock:<epost>`), slik at samme testbruker gjenbrukes ved gjentatte innlogginger
 * i stedet for å opprette en ny forelder-rad hver gang.
 */
fun Route.registerMockGoogleLogin(
    familyRepository: FamilyRepository,
    tokenRepository: TokenRepository,
    frontendSuccessUrl: String,
    frontendJoinUrl: String,
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
            handleJoin(code, mockGoogleSub, email, name, familyRepository)
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig kode eller familien er full"))
        } else {
            familyRepository.findParentByGoogleSub(mockGoogleSub)?.familyId?.toString()
                ?: return@get call.respondRedirect("$frontendJoinUrl?error=ikke_registrert")
        }

        val parent = familyRepository.findParentByGoogleSub(mockGoogleSub)
            ?: return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("uventet feil i mock-innlogging"))

        // Fiktive tokens — ekte Google Calendar-kall vil feile med disse (forventet:
        // det er nettopp POENGET med mock-modus at ingen ekte Google-kall skjer).
        // "connected" vises likevel som true i UI siden en token-rad finnes.
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

    // Kun aktivert lokalt via MOCK_GOOGLE_AUTH=true (se Routing.kt) — lar deg logge inn/
    // opprette en familie i dev uten en ekte Google OAuth-klient. Se dev-login.md/README.
    if (Env.get("MOCK_GOOGLE_AUTH")?.toBooleanStrictOrNull() == true) {
        registerMockGoogleLogin(familyRepository, tokenRepository, frontendSuccessUrl, frontendJoinUrl)
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
