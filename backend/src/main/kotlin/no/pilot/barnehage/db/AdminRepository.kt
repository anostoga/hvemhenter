package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

data class AdminStats(val familyCount: Long, val userCount: Long, val helperCount: Long)

/**
 * Admin-genererte opprettingskoder (InviteCodesTable) er avviklet: siden nye brukere nå kan
 * opprette en familie uten kode (se OnboardingRoutes.kt), har de ikke lenger noen funksjon.
 * `invite_codes`-tabellen består i databasen som en historisk logg over tidligere bruk, men
 * denne repositoryen eksponerer bare aggregert statistikk.
 */
class AdminRepository(private val database: Database) {

    fun countFamilies(): Long = transaction(database) { FamiliesTable.selectAll().count() }

    fun countUsers(): Long = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.isHelper eq false }.count()
    }

    fun countHelpers(): Long = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.isHelper eq true }.count()
    }

    fun stats(): AdminStats = AdminStats(countFamilies(), countUsers(), countHelpers())
}
