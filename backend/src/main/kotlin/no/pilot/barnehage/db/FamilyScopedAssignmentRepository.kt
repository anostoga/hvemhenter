package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.Database
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class FamilyAssignment(
    val id: UUID,
    val date: LocalDate,
    val type: String,
    val parentId: UUID,
    val source: String,
    val googleEventId: String?,
)

class FamilyScopedAssignmentRepository(private val familyId: UUID, private val database: Database) {

    fun findByDate(date: LocalDate): List<FamilyAssignment> = transaction(database) {
        AssignmentsTable
            .selectAll()
            .where { (AssignmentsTable.familyId eq familyId) and (AssignmentsTable.date eq date) }
            .map { it.toFamilyAssignment() }
    }

    fun all(): List<FamilyAssignment> = transaction(database) {
        AssignmentsTable
            .selectAll()
            .where { AssignmentsTable.familyId eq familyId }
            .orderBy(AssignmentsTable.createdAt to org.jetbrains.exposed.sql.SortOrder.ASC)
            .map { it.toFamilyAssignment() }
    }

    fun insert(date: LocalDate, type: String, parentId: UUID, source: String, googleEventId: String?): UUID = transaction(database) {
        AssignmentsTable.insert {
            it[AssignmentsTable.familyId] = this@FamilyScopedAssignmentRepository.familyId
            it[AssignmentsTable.date] = date
            it[AssignmentsTable.type] = type
            it[AssignmentsTable.parentId] = parentId
            it[AssignmentsTable.assignmentSource] = source
            it[AssignmentsTable.googleEventId] = googleEventId
            it[createdAt] = Instant.now()
        }[AssignmentsTable.id]
    }

    fun findByDateAndType(date: LocalDate, type: String): FamilyAssignment? = transaction(database) {
        AssignmentsTable
            .selectAll()
            .where { (AssignmentsTable.familyId eq familyId) and (AssignmentsTable.date eq date) and (AssignmentsTable.type eq type) }
            .firstOrNull()?.toFamilyAssignment()
    }

    fun update(id: UUID, parentId: UUID, source: String, googleEventId: String?) = transaction(database) {
        AssignmentsTable.update({ AssignmentsTable.id eq id }) {
            it[AssignmentsTable.parentId] = parentId
            it[AssignmentsTable.assignmentSource] = source
            it[AssignmentsTable.googleEventId] = googleEventId
        }
    }

    fun findById(id: UUID): FamilyAssignment? = transaction(database) {
        AssignmentsTable
            .selectAll()
            .where { (AssignmentsTable.familyId eq familyId) and (AssignmentsTable.id eq id) }
            .firstOrNull()?.toFamilyAssignment()
    }

    fun delete(id: UUID) = transaction(database) {
        AssignmentsTable.deleteWhere { org.jetbrains.exposed.sql.Op.build { (AssignmentsTable.familyId eq familyId) and (AssignmentsTable.id eq id) } }
    }

    fun hasAssignmentsForParent(parentId: UUID): Boolean = transaction(database) {
        AssignmentsTable
            .selectAll()
            .where { (AssignmentsTable.familyId eq familyId) and (AssignmentsTable.parentId eq parentId) }
            .limit(1)
            .any()
    }

    fun findFutureForParent(parentId: UUID, fromDate: LocalDate): List<FamilyAssignment> = transaction(database) {
        AssignmentsTable
            .selectAll()
            .where { (AssignmentsTable.familyId eq familyId) and (AssignmentsTable.parentId eq parentId) and (AssignmentsTable.date greaterEq fromDate) }
            .map { it.toFamilyAssignment() }
    }

    fun deleteAllForThisFamily() = transaction(database) {
        val id = familyId
        AssignmentsTable.deleteWhere { org.jetbrains.exposed.sql.Op.build { AssignmentsTable.familyId eq id } }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toFamilyAssignment() = FamilyAssignment(
        id = this[AssignmentsTable.id],
        date = this[AssignmentsTable.date],
        type = this[AssignmentsTable.type],
        parentId = this[AssignmentsTable.parentId],
        source = this[AssignmentsTable.assignmentSource],
        googleEventId = this[AssignmentsTable.googleEventId],
    )
}
