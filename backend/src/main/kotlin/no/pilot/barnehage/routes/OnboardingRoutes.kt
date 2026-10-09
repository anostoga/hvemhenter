package no.pilot.barnehage.routes

import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.set
import io.ktor.server.sessions.sessions
import kotlinx.serialization.Serializable
import no.pilot.barnehage.Env
import no.pilot.barnehage.auth.UserSession
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.db.JoinFamilyResult
import no.pilot.barnehage.db.OnboardingRepository
import no.pilot.barnehage.db.OnboardingSessionRecord
import no.pilot.barnehage.db.ParentRecord
import no.pilot.barnehage.db.TokenRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private const val ONBOARDING_COOKIE = "bhg_onboarding"
private const val ONBOARDING_PATH = "/auth/onboarding"

@Serializable
data class OnboardingStatusResponse(val name: String, val email: String, val csrfToken: String)

@Serializable
data class CreateFamilyRequest(val csrfToken: String)

@Serializable
data class JoinFamilyRequest(val code: String, val csrfToken: String)

/**
 * Fullfører innlogging for den gitte forelderen: lagrer OAuth-tokenene som ble holdt midlertidig
 * i oppstartsøkten, rydder bort selve oppstartsøkten (engangsbruk) og setter den vanlige,
 * innloggede sesjonscookien. Brukes av begge fullføringsendepunktene under, slik at "opprett" og
 * "bli med" ender opp i nøyaktig samme innloggede tilstand som et vanlig Google-innlogging.
 */
private suspend fun ApplicationCall.finishOnboarding(
    onboardingRepository: OnboardingRepository,
    tokenRepository: TokenRepository,
    familyRepository: FamilyRepository,
    reference: String,
    record: OnboardingSessionRecord,
    parent: ParentRecord,
) {
    familyRepository.promoteToAdminIfConfigured(parent)
    tokenRepository.upsert(parent.id, record.accessToken, record.refreshToken, record.tokenExpiresAt)
    onboardingRepository.consume(reference)
    clearOnboardingCookie(this)
    sessions.set(UserSession(parentId = parent.id.toString(), familyId = parent.familyId.toString()))
    respond(HttpStatusCode.OK)
}

private fun clearOnboardingCookie(call: ApplicationCall) {
    call.response.cookies.append(
        Cookie(
            name = ONBOARDING_COOKIE,
            value = "",
            path = ONBOARDING_PATH,
            maxAge = 0,
            secure = secureCookiesForOnboarding(),
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
}

private fun secureCookiesForOnboarding(): Boolean = Env.get("SESSION_COOKIE_SECURE")?.toBooleanStrictOrNull() ?: true

/**
 * `SameSite=Lax` beskytter ikke alene mot CSRF for en state-endrende POST — en cross-site
 * `<form>`-innsending ville fortsatt sendt cookien med. Vi krever derfor i tillegg at
 * `Origin`-headeren matcher konfigurert frontend-url (hoppes over i lokal dev uten
 * `FRONTEND_URL` satt, der vi uansett stoler på CSRF-tokenet alene), OG et CSRF-token som ble
 * utstedt sammen med oppstartsøkten og aldri er tilgjengelig for en annen side.
 */
private fun originIsTrusted(call: ApplicationCall): Boolean {
    val configured = Env.get("FRONTEND_URL") ?: return true
    val origin = call.request.header("Origin") ?: return false
    return origin.trimEnd('/') == configured.trimEnd('/')
}

private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

private fun generateInviteCode(): String {
    val bytes = ByteArray(9)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/**
 * Siste steg i innlogging med Google for en ny bruker: /auth/login sender ukjente brukere hit
 * (via en midlertidig oppstartsøkt, se AuthRoutes.startOnboarding) i stedet for å opprette en
 * familie med en gang. Brukeren velger først om hen vil opprette en helt ny familie uten kode,
 * eller bli med i en eksisterende familie med koden fra den andre forelderen.
 */
fun Route.onboardingRoutes(
    onboardingRepository: OnboardingRepository,
    familyRepository: FamilyRepository,
    tokenRepository: TokenRepository,
) {
    get("$ONBOARDING_PATH/status") {
        val reference = call.request.cookies[ONBOARDING_COOKIE]
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("ingen aktiv oppstartsøkt"))
        val record = onboardingRepository.find(reference)
            ?: run {
                clearOnboardingCookie(call)
                return@get call.respond(HttpStatusCode.Unauthorized, ErrorResponse("oppstartsøkten er utløpt, logg inn på nytt"))
            }
        call.respond(OnboardingStatusResponse(record.name, record.email, record.csrfToken))
    }

    post("$ONBOARDING_PATH/create-family") {
        val reference = call.request.cookies[ONBOARDING_COOKIE]
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("ingen aktiv oppstartsøkt"))
        if (!originIsTrusted(call)) {
            return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("uventet opprinnelse"))
        }
        val record = onboardingRepository.find(reference)
            ?: run {
                clearOnboardingCookie(call)
                return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("oppstartsøkten er utløpt, logg inn på nytt"))
            }
        val body = call.receive<CreateFamilyRequest>()
        if (!constantTimeEquals(record.csrfToken, body.csrfToken)) {
            return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("ugyldig forespørsel"))
        }

        val inviteCode = generateInviteCode()
        val parent = runCatching {
            familyRepository.createFamilyWithFirstParent("", inviteCode, record.googleSub, record.email, record.name)
        }.getOrElse {
            // Unik google_sub-constraint brutt — en samtidig forespørsel rakk å fullføre først.
            // Logg brukeren inn på den familien i stedet for å feile.
            familyRepository.findParentByGoogleSub(record.googleSub)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, ErrorResponse("uventet feil ved opprettelse"))
        }

        call.finishOnboarding(onboardingRepository, tokenRepository, familyRepository, reference, record, parent)
    }

    post("$ONBOARDING_PATH/join-family") {
        val reference = call.request.cookies[ONBOARDING_COOKIE]
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("ingen aktiv oppstartsøkt"))
        if (!originIsTrusted(call)) {
            return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("uventet opprinnelse"))
        }
        val record = onboardingRepository.find(reference)
            ?: run {
                clearOnboardingCookie(call)
                return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("oppstartsøkten er utløpt, logg inn på nytt"))
            }
        val body = call.receive<JoinFamilyRequest>()
        if (!constantTimeEquals(record.csrfToken, body.csrfToken)) {
            return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("ugyldig forespørsel"))
        }

        val result = runCatching {
            familyRepository.joinFamilyWithInviteCode(body.code, record.googleSub, record.email, record.name)
        }.getOrElse {
            familyRepository.findParentByGoogleSub(record.googleSub)?.let { JoinFamilyResult.AlreadyMember(it) }
                ?: return@post call.respond(HttpStatusCode.InternalServerError, ErrorResponse("uventet feil ved innmelding"))
        }

        val parent = when (result) {
            is JoinFamilyResult.Joined -> result.parent
            is JoinFamilyResult.AlreadyMember -> result.parent
            JoinFamilyResult.InvalidCode ->
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig eller allerede brukt kode"))
            JoinFamilyResult.FamilyFull ->
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("familien har allerede to foreldre"))
        }

        call.finishOnboarding(onboardingRepository, tokenRepository, familyRepository, reference, record, parent)
    }
}
