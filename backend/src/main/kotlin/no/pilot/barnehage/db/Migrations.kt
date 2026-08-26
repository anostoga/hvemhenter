package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Enkel versjonert migrasjonsrunner for SQLite (Flyway har ikke førsteklasses
 * SQLite-støtte). Hver migrasjon er en navngitt, idempotent handling som kun
 * kjøres én gang per database, sporet i `schema_version`.
 */
object SchemaVersion : Table("schema_version") {
    val version = integer("version")
    override val primaryKey = PrimaryKey(version)
}

data class Migration(val version: Int, val description: String, val apply: () -> Unit)

object Migrations {
    private val all = listOf(
        Migration(1, "opprett tokens, assignments, config") {
            SchemaUtils.create(Tokens, Assignments, Config)
        },
    )

    fun runAll(database: Database) {
        transaction(database) {
            SchemaUtils.create(SchemaVersion)
            val applied = SchemaVersion.select(SchemaVersion.version)
                .map { it[SchemaVersion.version] }
                .toSet()

            all.filterNot { it.version in applied }
                .sortedBy { it.version }
                .forEach { migration ->
                    migration.apply()
                    SchemaVersion.insert { it[version] = migration.version }
                }
        }
    }
}
