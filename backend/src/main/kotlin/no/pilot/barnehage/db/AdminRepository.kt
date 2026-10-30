package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class FamilyActivity(
    val id: UUID,
    val lastCalendarViewedAt: Instant?,
    val lastCalendarUpdatedAt: Instant?,
) {
    val lastActiveAt: Instant? get() = listOfNotNull(lastCalendarViewedAt, lastCalendarUpdatedAt).maxOrNull()
}

data class AdminStats(
    val familyCount: Long,
    val userCount: Long,
    val helperCount: Long,
    val activeFamiliesLast24Hours: Int,
    val activeFamiliesLast7Days: Int,
    val activeFamiliesLast30Days: Int,
    val updatedFamiliesLast7Days: Int,
    val updatedFamiliesLast30Days: Int,
    val families: List<FamilyActivity>,
)

/**
 * Admin-genererte opprettingskoder (InviteCodesTable) er avviklet: siden nye brukere nå kan
 * opprette en familie uten kode (se OnboardingRoutes.kt), har de ikke lenger noen funksjon.
 * `invite_codes`-tabellen består i databasen som en historisk logg over tidligere bruk, men
 * denne repositoryen eksponerer statistikk og aktivitet per familie, uten hvem som utførte handlingene.
 */
class AdminRepository(private val database: Database, private val clock: Clock = Clock.systemUTC()) {

    fun countFamilies(): Long = transaction(database) { FamiliesTable.selectAll().count() }

    fun countUsers(): Long = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.isHelper eq false }.count()
    }

    fun countHelpers(): Long = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.isHelper eq true }.count()
    }

    fun stats(): AdminStats = transaction(database) {
        val now = Instant.now(clock)
        val families = FamiliesTable.selectAll().map {
            FamilyActivity(it[FamiliesTable.id], it[FamiliesTable.lastCalendarViewedAt], it[FamiliesTable.lastCalendarUpdatedAt])
        }
        fun Instant?.within(days: Long): Boolean = this != null && this >= now.minus(Duration.ofDays(days))
        AdminStats(
            familyCount = families.size.toLong(),
            userCount = ParentsTable.selectAll().where { ParentsTable.isHelper eq false }.count(),
            helperCount = ParentsTable.selectAll().where { ParentsTable.isHelper eq true }.count(),
            activeFamiliesLast24Hours = families.count { it.lastActiveAt.within(1) },
            activeFamiliesLast7Days = families.count { it.lastActiveAt.within(7) },
            activeFamiliesLast30Days = families.count { it.lastActiveAt.within(30) },
            updatedFamiliesLast7Days = families.count { it.lastCalendarUpdatedAt.within(7) },
            updatedFamiliesLast30Days = families.count { it.lastCalendarUpdatedAt.within(30) },
            families = families,
        )
    }
}
