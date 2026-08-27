package no.pilot.barnehage.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.pilot.barnehage.Env
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

/**
 * Tilkobling til Postgres (Supabase lokalt/i prod) — eneste database appen bruker,
 * for alt fra families/parents/oauth_tokens til assignments (se PostgresTables.kt).
 *
 * HikariCP-pool er bevisst lite (containere har begrenset med tilkoblinger,
 * og Supabase sin pooler har egne grenser per prosjekt).
 */
object PostgresDatabase {
    private var dataSource: HikariDataSource? = null

    fun connect(): Database {
        val url = Env.get("DATABASE_URL") ?: error("DATABASE_URL mangler")
        val user = Env.get("DATABASE_USER") ?: "postgres"
        val password = Env.get("DATABASE_PASSWORD") ?: ""

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = 3
            idleTimeout = 300_000
            maxLifetime = 1_800_000
            driverClassName = "org.postgresql.Driver"
        }
        val ds = HikariDataSource(hikariConfig)
        dataSource = ds

        // Kjører migrasjoner mot SAMME datasource/tilkobling som appen ellers bruker —
        // garanterer at riktig database (lokal/CI/prod, avhengig av DATABASE_URL) migreres,
        // uten en egen kopi av databasepassordet et tredje sted (f.eks. GitHub secrets).
        // Idempotent: Flyway sjekker `flyway_schema_history` og gjør ingenting om alt
        // allerede er migrert, så dette er trygt å kjøre ved hver oppstart/scale-to-zero-oppvåkning.
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        return Database.connect(ds)
    }
}
