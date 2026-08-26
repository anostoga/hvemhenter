package no.pilot.barnehage.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.CORS
import no.pilot.barnehage.Env
import java.net.URI

/**
 * Tillater frontend (Next.js på en annen origin/port) å kalle API-et fra nettleser.
 * Kun FRONTEND_URL sin origin slippes gjennom — ingen wildcard, siden det ville
 * blokkert credentials-støtte og vært unødvendig åpent for et to-brukers hobbyprosjekt.
 */
fun Application.configureCORS() {
    val frontendUrl = Env.get("FRONTEND_URL") ?: "http://localhost:3000"
    val uri = URI(frontendUrl)
    val host = if (uri.port != -1) "${uri.host}:${uri.port}" else uri.host

    install(CORS) {
        allowHost(host, schemes = listOf(uri.scheme))
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
    }
}
