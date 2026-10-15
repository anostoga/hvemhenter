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

sealed class JoinFamilyResult {
    data class Joined(val parent: ParentRecord) : JoinFamilyResult()
    data class AlreadyMember(val parent: ParentRecord) : JoinFamilyResult()
    object InvalidCode : JoinFamilyResult()
    object FamilyFull : JoinFamilyResult()
}

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

    /**
     * Oppretter en helt ny familie med den innloggede brukeren som forelder 1 — ingen kode
     * kreves lenger (se OnboardingRoutes.kt). Idempotent: hvis Google-brukeren allerede har en
     * forelder-rad (f.eks. et doble-innsendt skjema, eller en samtidig forespørsel som rakk å
     * fullføre først), returneres den eksisterende raden i stedet for å opprette en duplikat —
     * `google_sub` er unik, så en reell kappløping ville uansett feile med constraint-brudd;
     * den feilen fanges og håndteres likt av kalleren (se handleGoogleSubRaceOrRethrow).
     */
    fun createFamilyWithFirstParent(
        sharedCalendarId: String,
        inviteCode: String,
        googleSub: String,
        email: String,
        name: String,
    ): ParentRecord = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.googleSub eq googleSub }.firstOrNull()?.let {
            return@transaction it.toParentRecord()
        }

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

    /**
     * Forelder 2 blir med i en eksisterende familie via familiens invitasjonskode.
     * `forUpdate()` låser familieraden for varigheten av transaksjonen: to samtidige forsøk på å
     * bruke samme kode blir dermed serialisert — den andre ser koden som allerede konsumert
     * (null) når den får låsen, i stedet for at begge kan passere tellingen av antall foreldre.
     */
    fun joinFamilyWithInviteCode(
        inviteCode: String,
        googleSub: String,
        email: String,
        name: String,
    ): JoinFamilyResult = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.googleSub eq googleSub }.firstOrNull()?.let {
            return@transaction JoinFamilyResult.AlreadyMember(it.toParentRecord())
        }

        val family = FamiliesTable.selectAll().where { FamiliesTable.inviteCode eq inviteCode }
            .forUpdate()
            .firstOrNull()?.toFamilyRecord() ?: return@transaction JoinFamilyResult.InvalidCode

        val existingParents = ParentsTable.selectAll()
            .where { (ParentsTable.familyId eq family.id) and (ParentsTable.isHelper eq false) }
            .count()
        if (existingParents >= 2) return@transaction JoinFamilyResult.FamilyFull

        val parentId = ParentsTable.insert {
            it[ParentsTable.familyId] = family.id
            it[ParentsTable.googleSub] = googleSub
            it[ParentsTable.email] = email
            it[ParentsTable.name] = name
        }[ParentsTable.id]

        FamiliesTable.update({ FamiliesTable.id eq family.id }) {
            it[FamiliesTable.inviteCode] = null
        }

        JoinFamilyResult.Joined(ParentRecord(parentId, family.id, googleSub, email, name))
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

    fun addHelper(familyId: UUID, name: String, avatar: String?, email: String? = null): ParentRecord = transaction(database) {
        val parentId = ParentsTable.insert {
            it[ParentsTable.familyId] = familyId
            it[ParentsTable.name] = name
            it[ParentsTable.avatar] = avatar
            it[ParentsTable.email] = email
            it[ParentsTable.isHelper] = true
        }[ParentsTable.id]
        ParentRecord(
            id = parentId,
            familyId = familyId,
            googleSub = null,
            email = email,
            name = name,
            avatar = avatar,
            isHelper = true,
        )
    }

    fun updateHelper(familyId: UUID, parentId: UUID, name: String, avatar: String?, email: String? = null): ParentRecord? = transaction(database) {
        val existing = ParentsTable.selectAll().where {
            (ParentsTable.id eq parentId) and (ParentsTable.familyId eq familyId) and (ParentsTable.isHelper eq true)
        }.firstOrNull() ?: return@transaction null
        val updated = ParentsTable.update({
            (ParentsTable.id eq parentId) and (ParentsTable.familyId eq familyId) and (ParentsTable.isHelper eq true)
        }) {
            it[ParentsTable.name] = name
            it[ParentsTable.avatar] = avatar
            it[ParentsTable.email] = email
        }
        if (updated == 0) return@transaction null
        if (existing[ParentsTable.email] != email) {
            AssignmentsTable.update({
                (AssignmentsTable.familyId eq familyId) and
                    (AssignmentsTable.parentId eq parentId)
            }) {
                it[AssignmentsTable.confirmationTokenHash] = null
                it[AssignmentsTable.confirmationTokenExpiresAt] = null
                it[AssignmentsTable.confirmationLinkIssued] = false
                it[AssignmentsTable.confirmationEmailSent] = false
            }
        }
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
