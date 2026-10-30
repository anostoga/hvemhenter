package no.pilot.barnehage.db

import no.pilot.barnehage.domain.ConfirmationStatus
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

class AssignmentConfirmationRepository(private val database: Database) {
    fun find(tokenHash: String): AssignmentConfirmation? = transaction(database) {
        val assignment = AssignmentsTable.selectAll()
            .where {
                (AssignmentsTable.confirmationTokenHash eq tokenHash) and
                    (AssignmentsTable.confirmationTokenExpiresAt greater Instant.now())
            }
            .firstOrNull() ?: return@transaction null
        val parent = ParentsTable.selectAll()
            .where { ParentsTable.id eq assignment[AssignmentsTable.parentId] }
            .firstOrNull() ?: return@transaction null

        AssignmentConfirmation(
            familyId = assignment[AssignmentsTable.familyId],
            date = assignment[AssignmentsTable.date],
            type = assignment[AssignmentsTable.type],
            helperName = parent[ParentsTable.name],
            status = ConfirmationStatus.valueOf(assignment[AssignmentsTable.confirmationStatus]),
        )
    }

    fun answer(tokenHash: String, answer: ConfirmationStatus): ConfirmationAnswerResult = transaction(database) {
        val assignment = AssignmentsTable.selectAll()
            .where {
                (AssignmentsTable.confirmationTokenHash eq tokenHash) and
                    (AssignmentsTable.confirmationTokenExpiresAt greater Instant.now())
            }
            .firstOrNull() ?: return@transaction ConfirmationAnswerResult.INVALID_OR_EXPIRED
        val current = ConfirmationStatus.valueOf(assignment[AssignmentsTable.confirmationStatus])
        if (current == answer) return@transaction ConfirmationAnswerResult.ALREADY_RECORDED
        if (current != ConfirmationStatus.PENDING) return@transaction ConfirmationAnswerResult.CONFLICT

        val assignmentId = assignment[AssignmentsTable.id]
        val updated = AssignmentsTable.update({
            (AssignmentsTable.id eq assignmentId) and
                (AssignmentsTable.confirmationTokenHash eq tokenHash) and
                (AssignmentsTable.confirmationStatus eq ConfirmationStatus.PENDING.name)
        }) {
            it[AssignmentsTable.confirmationStatus] = answer.name
        }
        if (updated > 0) {
            recordCalendarUpdate(assignment[AssignmentsTable.familyId])
            ConfirmationAnswerResult.UPDATED
        } else {
            val latest = AssignmentsTable.selectAll()
                .where { AssignmentsTable.id eq assignmentId }
                .firstOrNull()
            if (latest?.get(AssignmentsTable.confirmationStatus) == answer.name) {
                ConfirmationAnswerResult.ALREADY_RECORDED
            } else {
                ConfirmationAnswerResult.CONFLICT
            }
        }
    }
}
