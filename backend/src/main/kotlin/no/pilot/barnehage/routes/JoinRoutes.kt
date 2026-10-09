package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import no.pilot.barnehage.Env
import no.pilot.barnehage.auth.bindNonceToStatePayload
import no.pilot.barnehage.auth.issueOAuthNonce
import no.pilot.barnehage.crypto.StateSigner
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.google.GoogleOAuthClient
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class JoinErrorResponse(val error: String)

private val joinAttempts = ConcurrentHashMap<String, MutableList<Instant>>()
private const val MAX_ATTEMPTS_PER_HOUR = 5

/**
 * Identifiserer klienten for rate-limiting. Bak Fly.io sin proxy er `remoteHost` proxyens egen
 * adresse (samme for alle brukere), så vi foretrekker headeren Fly setter med den faktiske
 * klient-IP-en. `X-Forwarded-For` brukes som fallback for andre miljøer bak reverse proxy.
 */
private fun clientIp(call: io.ktor.server.application.ApplicationCall): String =
    call.request.header("Fly-Client-IP")
        ?: call.request.header("X-Forwarded-For")?.substringBefore(",")?.trim()
        ?: call.request.origin.remoteHost

private fun rateLimited(ip: String): Boolean {
    val now = Instant.now()
    val attempts = joinAttempts.computeIfAbsent(ip) { mutableListOf() }
    synchronized(attempts) {
        attempts.removeAll { it.isBefore(now.minusSeconds(3600)) }
        if (attempts.size >= MAX_ATTEMPTS_PER_HOUR) return true
        attempts.add(now)
    }
    return false
}

/**
 * Bli med i en eksisterende familie som forelder 2, via familiens invitasjonskode (delt av
 * forelder 1 — se /familie). Dette er den ENESTE måten å bli med i en allerede opprettet familie
 * på; det finnes ingen kode som kan brukes til å opprette en ny familie lenger — det gjøres uten
 * kode via /velkommen etter vanlig Google-innlogging (se OnboardingRoutes.kt).
 */
fun Route.joinRoutes(
    oauthClient: GoogleOAuthClient,
    stateSigner: StateSigner,
    familyRepository: FamilyRepository,
) {
    get("/join/start") {
        val code = call.parameters["code"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, JoinErrorResponse("code mangler"))

        if (rateLimited(clientIp(call))) {
            return@get call.respond(HttpStatusCode.TooManyRequests, JoinErrorResponse("for mange forsøk, prøv igjen senere"))
        }

        if (familyRepository.findFamilyByInviteCode(code) == null) {
            return@get call.respond(HttpStatusCode.BadRequest, JoinErrorResponse("ugyldig kode"))
        }

        val secureCookies = Env.get("SESSION_COOKIE_SECURE")?.toBooleanStrictOrNull() ?: true
        val nonce = call.issueOAuthNonce(secureCookies)
        val state = stateSigner.sign(bindNonceToStatePayload(nonce, "join:$code"))
        call.respondRedirect(oauthClient.buildAuthorizeUrl(state))
    }
}
