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

@Serializable
data class UserSession(val parentId: String, val familyId: String)

private const val SESSION_COOKIE_NAME = "bhg_session"
private const val AUTH_NAME = "session-auth"

fun Application.configureSessionAuth() {
    val signingKey = Env.get("SESSION_SIGNING_SECRET")
        ?: error("SESSION_SIGNING_SECRET mangler (egen hemmelighet, ikke gjenbruk STATE_SIGNING_SECRET)")

    val secureCookies = Env.get("SESSION_COOKIE_SECURE")?.toBooleanStrictOrNull() ?: true

    install(Sessions) {
        cookie<UserSession>(SESSION_COOKIE_NAME) {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "Lax"
            cookie.secure = secureCookies
            cookie.maxAgeInSeconds = 60 * 60 * 24 * 30
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

const val SESSION_AUTH_NAME = AUTH_NAME

fun ApplicationCall.userSession(): UserSession? = principal<UserSession>()

private fun signingKeyHex(secret: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
