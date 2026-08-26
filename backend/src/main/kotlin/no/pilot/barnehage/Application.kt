package no.pilot.barnehage

import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.pilot.barnehage.auth.configureSessionAuth
import no.pilot.barnehage.plugins.PostgresDatabase
import no.pilot.barnehage.plugins.configureCORS
import no.pilot.barnehage.plugins.configureRouting
import no.pilot.barnehage.plugins.configureSerialization

fun main() {
    embeddedServer(Netty, port = Env.get("PORT")?.toIntOrNull() ?: 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    configureCORS()
    configureSerialization()

    val database = PostgresDatabase.connect()
    configureSessionAuth()

    configureRouting(database)
}
