package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class FamilyActivityRepository(private val database: Database, private val clock: Clock = Clock.systemUTC()) {
    fun recordCalendarView(familyId: UUID): Boolean = transaction(database) {
        val now = Instant.now(clock)
        FamiliesTable.update({
            (FamiliesTable.id eq familyId) and (
                FamiliesTable.lastCalendarViewedAt.isNull() or
                    (FamiliesTable.lastCalendarViewedAt lessEq now.minus(Duration.ofMinutes(15)))
                )
        }) {
            it[lastCalendarViewedAt] = now
        } > 0
    }
}

internal fun Transaction.recordCalendarUpdate(familyId: UUID) {
    FamiliesTable.update({ FamiliesTable.id eq familyId }) {
        it[lastCalendarUpdatedAt] = CurrentTimestamp
    }
}
