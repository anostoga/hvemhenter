package no.pilot.barnehage.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.pilot.barnehage.Env
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

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

        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        return Database.connect(ds)
    }
}
