package no.pilot.barnehage.db

import no.pilot.barnehage.domain.Assignment
import no.pilot.barnehage.domain.AssignmentSource
import no.pilot.barnehage.domain.AssignmentType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class AssignmentRepository {

    fun all(): List<Assignment> = transaction {
        Assignments.selectAll()
            .orderBy(Assignments.date, SortOrder.DESC)
            .map { it.toAssignment() }
    }

    fun insert(date: String, type: AssignmentType, parentId: String, source: AssignmentSource, googleEventId: String?): Long = transaction {
        val id = Assignments.insert {
            it[Assignments.date] = date
            it[Assignments.type] = type.name
            it[Assignments.parentId] = parentId
            it[Assignments.assignmentSource] = source.name
            it[Assignments.googleEventId] = googleEventId
            it[createdAt] = LocalDateTime.now()
        } get Assignments.id
        id
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toAssignment() = Assignment(
        id = this[Assignments.id],
        date = this[Assignments.date],
        type = AssignmentType.valueOf(this[Assignments.type]),
        parentId = this[Assignments.parentId],
        source = AssignmentSource.valueOf(this[Assignments.assignmentSource]),
        googleEventId = this[Assignments.googleEventId],
    )
}
