package no.pilot.barnehage.plugins

import io.ktor.server.application.*
import no.pilot.barnehage.Env
import no.pilot.barnehage.db.Migrations
import org.jetbrains.exposed.sql.Database
import java.io.File

/**
 * Kobler til en lokal SQLite-fil. Dette er bevisst ikke en egen databasetjeneste
 * (jf. pilot-planen) — kun én fil på en persistent volume i produksjon (Fly.io),
 * brukt til OAuth-refresh-tokens, tildelingshistorikk og rotasjonskonfigurasjon.
 * Kjører versjonerte migrasjoner ved oppstart (se db/Migrations.kt).
 */
fun Application.configureDatabase(): Database {
    val dbPath = Env.get("DB_PATH") ?: "./data/barnehage.db"
    File(dbPath).parentFile?.mkdirs()

    val database = Database.connect(
        url = "jdbc:sqlite:$dbPath",
        driver = "org.sqlite.JDBC",
    )
    Migrations.runAll(database)
    return database
}
