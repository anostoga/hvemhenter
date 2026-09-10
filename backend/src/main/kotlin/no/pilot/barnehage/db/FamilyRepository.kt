package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

data class FamilyRecord(val id: UUID, val sharedCalendarId: String, val inviteCode: String?)
data class ParentRecord(
    val id: UUID,
    val familyId: UUID,

    val googleSub: String?,

    val email: String?,
    val name: String,
    val avatar: String? = null,

    val calendarId: String? = null,

    val availabilityCalendarId: String? = null,

    val availabilityDisabled: Boolean = false,

    val isHelper: Boolean = false,

    val isAdmin: Boolean = false,
)

fun ParentRecord.effectiveAvailabilityCalendarId(): String? =
    if (availabilityDisabled) null else (availabilityCalendarId ?: calendarId)

class FamilyRepository(private val database: Database) {

    fun findParentByGoogleSub(googleSub: String): ParentRecord? = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.googleSub eq googleSub }
            .firstOrNull()?.toParentRecord()
    }

    fun findParent(id: UUID): ParentRecord? = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.id eq id }
            .firstOrNull()?.toParentRecord()
    }

    fun findFamily(familyId: UUID): FamilyRecord? = transaction(database) {
        FamiliesTable.selectAll().where { FamiliesTable.id eq familyId }
            .firstOrNull()?.toFamilyRecord()
    }

    fun findFamilyByInviteCode(code: String): FamilyRecord? = transaction(database) {
        FamiliesTable.selectAll().where { FamiliesTable.inviteCode eq code }
            .firstOrNull()?.toFamilyRecord()
    }

    fun parentCount(familyId: UUID): Long = transaction(database) {
        ParentsTable.selectAll()
            .where { (ParentsTable.familyId eq familyId) and (ParentsTable.isHelper eq false) }
            .count()
    }

    fun findParents(familyId: UUID): List<ParentRecord> = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.familyId eq familyId }.map { it.toParentRecord() }
    }

    fun findFamilyWithRoom(): FamilyRecord? = transaction(database) {
        FamiliesTable.selectAll()
            .map { it.toFamilyRecord() }
            .firstOrNull { family ->
                ParentsTable.selectAll()
                    .where { (ParentsTable.familyId eq family.id) and (ParentsTable.isHelper eq false) }
                    .count() < 2
            }
    }

    fun createFamilyWithFirstParent(
        sharedCalendarId: String,
        inviteCode: String,
        googleSub: String,
        email: String,
        name: String,
    ): ParentRecord = transaction(database) {
        val familyId = FamiliesTable.insert {
            it[FamiliesTable.sharedCalendarId] = sharedCalendarId
            it[FamiliesTable.inviteCode] = inviteCode
        }[FamiliesTable.id]

        val parentId = ParentsTable.insert {
            it[ParentsTable.familyId] = familyId
            it[ParentsTable.googleSub] = googleSub
            it[ParentsTable.email] = email
            it[ParentsTable.name] = name
        }[ParentsTable.id]

        ParentRecord(parentId, familyId, googleSub, email, name)
    }

    fun addParentToFamily(familyId: UUID, googleSub: String, email: String, name: String): ParentRecord? = transaction(database) {
        val existingParents = ParentsTable.selectAll()
            .where { (ParentsTable.familyId eq familyId) and (ParentsTable.isHelper eq false) }
            .count()
        if (existingParents >= 2) return@transaction null

        val parentId = ParentsTable.insert {
            it[ParentsTable.familyId] = familyId
            it[ParentsTable.googleSub] = googleSub
            it[ParentsTable.email] = email
            it[ParentsTable.name] = name
        }[ParentsTable.id]

        ParentRecord(parentId, familyId, googleSub, email, name)
    }

    fun joinFamilyWithInviteCode(
        inviteCode: String,
        googleSub: String,
        email: String,
        name: String,
    ): ParentRecord? = transaction(database) {
        val family = FamiliesTable.selectAll().where { FamiliesTable.inviteCode eq inviteCode }
            .firstOrNull()?.toFamilyRecord() ?: return@transaction null

        val existingParents = ParentsTable.selectAll()
            .where { (ParentsTable.familyId eq family.id) and (ParentsTable.isHelper eq false) }
            .count()
        if (existingParents >= 2) return@transaction null

        val parentId = ParentsTable.insert {
            it[ParentsTable.familyId] = family.id
            it[ParentsTable.googleSub] = googleSub
            it[ParentsTable.email] = email
            it[ParentsTable.name] = name
        }[ParentsTable.id]

        FamiliesTable.update({ FamiliesTable.id eq family.id }) {
            it[FamiliesTable.inviteCode] = null
        }

        ParentRecord(parentId, family.id, googleSub, email, name)
    }

    fun updateProfile(parentId: UUID, name: String, avatar: String?) = transaction(database) {
        ParentsTable.update({ ParentsTable.id eq parentId }) {
            it[ParentsTable.name] = name
            it[ParentsTable.avatar] = avatar
        }
    }

    fun updateParentCalendars(
        parentId: UUID,
        calendarId: String?,
        availabilityCalendarId: String?,
        availabilityDisabled: Boolean = false,
    ) = transaction(database) {
        ParentsTable.update({ ParentsTable.id eq parentId }) {
            it[ParentsTable.calendarId] = calendarId
            it[ParentsTable.availabilityCalendarId] = if (availabilityDisabled) null else availabilityCalendarId
            it[ParentsTable.availabilityDisabled] = availabilityDisabled
        }
    }

    fun addHelper(familyId: UUID, name: String, avatar: String?): ParentRecord = transaction(database) {
        val parentId = ParentsTable.insert {
            it[ParentsTable.familyId] = familyId
            it[ParentsTable.name] = name
            it[ParentsTable.avatar] = avatar
            it[ParentsTable.isHelper] = true
        }[ParentsTable.id]
        ParentRecord(
            id = parentId,
            familyId = familyId,
            googleSub = null,
            email = null,
            name = name,
            avatar = avatar,
            isHelper = true,
        )
    }

    fun updateHelper(familyId: UUID, parentId: UUID, name: String, avatar: String?): ParentRecord? = transaction(database) {
        val updated = ParentsTable.update({
            (ParentsTable.id eq parentId) and (ParentsTable.familyId eq familyId) and (ParentsTable.isHelper eq true)
        }) {
            it[ParentsTable.name] = name
            it[ParentsTable.avatar] = avatar
        }
        if (updated == 0) return@transaction null
        ParentsTable.selectAll().where { ParentsTable.id eq parentId }.firstOrNull()?.toParentRecord()
    }

    fun removeHelper(familyId: UUID, parentId: UUID): Boolean = transaction(database) {
        val deleted = ParentsTable.deleteWhere {
            org.jetbrains.exposed.sql.Op.build {
                (ParentsTable.id eq parentId) and (ParentsTable.familyId eq familyId) and (ParentsTable.isHelper eq true)
            }
        }
        deleted > 0
    }

    fun removeParent(familyId: UUID, parentId: UUID): Boolean = transaction(database) {
        val deleted = ParentsTable.deleteWhere {
            org.jetbrains.exposed.sql.Op.build {
                (ParentsTable.id eq parentId) and (ParentsTable.familyId eq familyId) and (ParentsTable.isHelper eq false)
            }
        }
        deleted > 0
    }

    fun deleteFamily(familyId: UUID) = transaction(database) {
        FamiliesTable.deleteWhere { org.jetbrains.exposed.sql.Op.build { FamiliesTable.id eq familyId } }
    }

    fun setAdmin(parentId: UUID, isAdmin: Boolean) = transaction(database) {
        ParentsTable.update({ ParentsTable.id eq parentId }) {
            it[ParentsTable.isAdmin] = isAdmin
        }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toParentRecord() = ParentRecord(
        id = this[ParentsTable.id],
        familyId = this[ParentsTable.familyId],
        googleSub = this[ParentsTable.googleSub],
        email = this[ParentsTable.email],
        name = this[ParentsTable.name],
        avatar = this[ParentsTable.avatar],
        calendarId = this[ParentsTable.calendarId],
        availabilityCalendarId = this[ParentsTable.availabilityCalendarId],
        availabilityDisabled = this[ParentsTable.availabilityDisabled],
        isHelper = this[ParentsTable.isHelper],
        isAdmin = this[ParentsTable.isAdmin],
    )

    private fun org.jetbrains.exposed.sql.ResultRow.toFamilyRecord() = FamilyRecord(
        id = this[FamiliesTable.id],
        sharedCalendarId = this[FamiliesTable.sharedCalendarId],
        inviteCode = this[FamiliesTable.inviteCode],
    )
}
