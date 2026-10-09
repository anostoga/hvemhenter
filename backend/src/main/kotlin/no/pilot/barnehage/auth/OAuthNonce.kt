package no.pilot.barnehage.auth

import io.ktor.http.Cookie
import io.ktor.server.application.ApplicationCall
import java.security.SecureRandom
import java.util.Base64

/**
 * Binder en Google OAuth-forespørsel (login/join/reconnect) til nettleseren som startet den.
 *
 * Uten dette kan en angriper starte en OAuth-flyt selv og lure et offer til å fullføre
 * angriperens callback-URL (f.eks. i en lenke/bilde) — offeret ville da logget inn på eller
 * opprettet en familie for angriperens Google-konto. `state` alene (HMAC-signert med utløpstid)
 * hindrer forfalskning av innholdet, men ikke gjenbruk på tvers av nettlesere.
 *
 * Løsning: en tilfeldig nonce legges i en kortlivet, HttpOnly cookie OG i den signerte
 * `state`-verdien som sendes til Google. Ved callback må de to være like — callback-forespørselen
 * må komme fra samme nettleser (med samme cookie) som startet innloggingen.
 */
private const val OAUTH_NONCE_COOKIE = "bhg_oauth_nonce"
private const val NONCE_SEPARATOR = "|"

fun ApplicationCall.issueOAuthNonce(secureCookies: Boolean): String {
    val bytes = ByteArray(24)
    SecureRandom().nextBytes(bytes)
    val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    response.cookies.append(
        Cookie(
            name = OAUTH_NONCE_COOKIE,
            value = nonce,
            path = "/",
            maxAge = 600,
            secure = secureCookies,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
    return nonce
}

/** Fjerner nonce-cookien (engangsbruk) og returnerer verdien den hadde, uansett om den fantes. */
fun ApplicationCall.consumeOAuthNonce(secureCookies: Boolean): String? {
    val nonce = request.cookies[OAUTH_NONCE_COOKIE]
    response.cookies.append(
        Cookie(
            name = OAUTH_NONCE_COOKIE,
            value = "",
            path = "/",
            maxAge = 0,
            secure = secureCookies,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
    return nonce
}

fun bindNonceToStatePayload(nonce: String, payload: String): String = "$nonce$NONCE_SEPARATOR$payload"

/** Returnerer den opprinnelige payloaden hvis nonce-delen matcher cookien, ellers null. */
fun unbindNonceFromStatePayload(statePayload: String, cookieNonce: String?): String? {
    val separatorIndex = statePayload.indexOf(NONCE_SEPARATOR)
    if (separatorIndex == -1) return null
    val stateNonce = statePayload.substring(0, separatorIndex)
    if (cookieNonce == null || stateNonce != cookieNonce) return null
    return statePayload.substring(separatorIndex + 1)
}
