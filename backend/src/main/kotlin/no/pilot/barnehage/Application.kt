package no.pilot.barnehage

import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.pilot.barnehage.plugins.configureDatabase
import no.pilot.barnehage.plugins.configureRouting
import no.pilot.barnehage.plugins.configureSerialization
import no.pilot.barnehage.plugins.configureCORS

fun main() {
    embeddedServer(Netty, port = Env.get("PORT")?.toIntOrNull() ?: 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    val database = configureDatabase()
    val config = AppConfig.fromEnv()
    configureCORS()
    configureSerialization()
    configureRouting(database, config)
}
