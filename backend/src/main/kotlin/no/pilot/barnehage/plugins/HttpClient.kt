package no.pilot.barnehage.plugins

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Delt HttpClient for kall mot Google sine API-er. */
fun createGoogleHttpClient(): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            // Google legger stadig til nye felter i svarene sine (f.eks.
            // refresh_token_expires_in) — vi bryr oss kun om et fast subsett.
            ignoreUnknownKeys = true
        })
    }
}
