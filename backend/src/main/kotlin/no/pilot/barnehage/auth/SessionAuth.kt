package no.pilot.barnehage.auth

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.Principal
import io.ktor.server.auth.principal
import io.ktor.server.auth.session
import io.ktor.server.response.respond
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.util.hex
import no.pilot.barnehage.Env
import kotlinx.serialization.Serializable

/**
 * Innholdet i den signerte sesjonscookien. Kun det strengt nødvendige —
 * IKKE navn/e-post her (unngå PII i en cookie som ligger i nettleseren).
 */
@Serializable
data class UserSession(val parentId: String, val familyId: String) : Principal

private const val SESSION_COOKIE_NAME = "bhg_session"
private const val AUTH_NAME = "session-auth"

/**
 * Sesjonsbasert autentisering med HMAC-signert cookie (Ktor Sessions).
 *
 * - Cookien er signert (SessionTransportTransformerMessageAuthentication), ikke kryptert —
 *   innholdet (parentId/familyId) er ikke hemmelig, men MÅ ikke kunne forfalskes.
 *   En forfalsket familyId ville omgått all family-scoping.
 * - `SESSION_SIGNING_SECRET` er en EGEN hemmelighet, atskilt fra `STATE_SIGNING_SECRET`
 *   (som kun brukes til den kortlivede OAuth-callback-staten) — de bør kunne roteres uavhengig.
 * - `SameSite=Lax` er nå trygt uansett miljø: nettleseren snakker kun med Next.js sitt
 *   origin (som proxyer auth-, api- og join-rutene videre til dette API-et
 *   server-til-server, se frontend/next.config.mjs) — cookien er dermed alltid
 *   samme-origin fra nettleserens ståsted, selv om frontend (Vercel) og backend
 *   (Fly.io) er ulike domener bak kulissene.
 */
fun Application.configureSessionAuth() {
    val signingKey = Env.get("SESSION_SIGNING_SECRET")
        ?: error("SESSION_SIGNING_SECRET mangler (egen hemmelighet, ikke gjenbruk STATE_SIGNING_SECRET)")
    // Lokal utvikling kjører over http://localhost, der `Secure`-cookies aldri sendes av
    // nettleseren (og heller ikke av Ktors test-klient). Styres eksplisitt via env,
    // IKKE gjettet fra request-URL, slik at oppførselen er forutsigbar og testbar.
    val secureCookies = Env.get("SESSION_COOKIE_SECURE")?.toBooleanStrictOrNull() ?: true

    install(Sessions) {
        cookie<UserSession>(SESSION_COOKIE_NAME) {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "Lax"
            cookie.secure = secureCookies
            cookie.maxAgeInSeconds = 60 * 60 * 24 * 30 // 30 dager
            transform(SessionTransportTransformerMessageAuthentication(hex(signingKeyHex(signingKey))))
        }
    }

    install(Authentication) {
        session<UserSession>(AUTH_NAME) {
            validate { session -> session }
            challenge {
                call.respond(io.ktor.http.HttpStatusCode.Unauthorized)
            }
        }
    }
}

/** Navnet på auth-providern, brukt i `authenticate(SESSION_AUTH_NAME) { ... }` i ruter. */
const val SESSION_AUTH_NAME = AUTH_NAME

/** Henter innlogget forelder/familie fra requesten, eller null hvis ikke autentisert. */
fun ApplicationCall.userSession(): UserSession? = principal<UserSession>()

/** Utleder en 32-byte (64 hex-tegn) nøkkel fra hemmeligheten via SHA-256,
 * slik at `SESSION_SIGNING_SECRET` kan være en vanlig lesbar streng i .env/secrets,
 * ikke nødvendigvis allerede hex-kodet. */
private fun signingKeyHex(secret: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
