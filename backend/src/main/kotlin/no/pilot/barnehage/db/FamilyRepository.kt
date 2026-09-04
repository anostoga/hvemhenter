package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

data class FamilyRecord(val id: UUID, val sharedCalendarId: String, val inviteCode: String?)
data class ParentRecord(
    val id: UUID,
    val familyId: UUID,
    val googleSub: String,
    val email: String,
    val name: String,
    val avatar: String? = null,
    /** Forelderens egen valgte kalender for skriving av tildelinger (se /api/calendars/mine). */
    val calendarId: String? = null,
    /** Kalenderen tilgjengelighet (opptatte tider) hentes fra. Null betyr "samme
     * som calendarId" — bruk `effectiveAvailabilityCalendarId()` for oppslag. */
    val availabilityCalendarId: String? = null,
)

/** Kalenderen som faktisk skal spørres for opptatte tider — `availabilityCalendarId`
 * hvis forelderen har valgt en avvikende kalender, ellers `calendarId` (den
 * avkrysningsboksen "bruk samme kalender" på /innstillinger tilsvarer). */
fun ParentRecord.effectiveAvailabilityCalendarId(): String? = availabilityCalendarId ?: calendarId

/**
 * Oppslag/oppretting av familier og foreldre. Brukes av JoinRoutes (familieopprettelse
 * og invitasjon) og av SessionAuth (finn forelder ved google_sub etter innlogging).
 *
 * `findParentByGoogleSub` og `createFamily` er de eneste stedene familie-medlemskap
 * avgjøres — alt annet i appen stoler på `familyId` som allerede er slått opp her
 * og lagt i sesjonen, ikke på noe klienten selv oppgir.
 */
class FamilyRepository(private val database: Database) {

    fun findParentByGoogleSub(googleSub: String): ParentRecord? = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.googleSub eq googleSub }
            .firstOrNull()?.toParentRecord()
    }

    /** Slår opp en forelder ved parent-id (fra sesjonen) — brukt av /auth/whoami til
     * å vise navnet til den innloggede brukeren selv (ikke andre), ikke lagret i cookien. */
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

    /** Antall foreldre i familien — brukt til å håndheve maks 2 foreldre per familie. */
    fun parentCount(familyId: UUID): Long = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.familyId eq familyId }.count()
    }

    /** Alle foreldre i familien — brukt av /api/parents og forslagslogikken. */
    fun findParents(familyId: UUID): List<ParentRecord> = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.familyId eq familyId }.map { it.toParentRecord() }
    }

    /** Finner en familie som fortsatt har plass (< 2 foreldre) opprettet via
     * FAMILY_CREATION_CODE — brukt til å la samme kode brukes av begge foreldre
     * uten at de ender opp i hver sin (tomme) familie. */
    fun findFamilyWithRoom(): FamilyRecord? = transaction(database) {
        FamiliesTable.selectAll()
            .map { it.toFamilyRecord() }
            .firstOrNull { family ->
                ParentsTable.selectAll().where { ParentsTable.familyId eq family.id }.count() < 2
            }
    }

    /** Oppretter en ny familie med den oppgitte forelderen som første medlem.
     * Kalles kun etter at FAMILY_CREATION_CODE er validert (se JoinRoutes). */
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

    /** Legger en forelder direkte til en gitt familie (brukt når FAMILY_CREATION_CODE
     * gjenbrukes av forelder #2 — familien er allerede kjent, ingen invite_code involvert). */
    fun addParentToFamily(familyId: UUID, googleSub: String, email: String, name: String): ParentRecord? = transaction(database) {
        val existingParents = ParentsTable.selectAll().where { ParentsTable.familyId eq familyId }.count()
        if (existingParents >= 2) return@transaction null

        val parentId = ParentsTable.insert {
            it[ParentsTable.familyId] = familyId
            it[ParentsTable.googleSub] = googleSub
            it[ParentsTable.email] = email
            it[ParentsTable.name] = name
        }[ParentsTable.id]

        ParentRecord(parentId, familyId, googleSub, email, name)
    }

    /** Legger forelder #2 til en eksisterende familie via invitasjonskode.
     * Invalidér koden (sett til null) etter kall — engangsbruk. */
    fun joinFamilyWithInviteCode(
        inviteCode: String,
        googleSub: String,
        email: String,
        name: String,
    ): ParentRecord? = transaction(database) {
        val family = FamiliesTable.selectAll().where { FamiliesTable.inviteCode eq inviteCode }
            .firstOrNull()?.toFamilyRecord() ?: return@transaction null

        val existingParents = ParentsTable.selectAll().where { ParentsTable.familyId eq family.id }.count()
        if (existingParents >= 2) return@transaction null // familien er allerede full

        val parentId = ParentsTable.insert {
            it[ParentsTable.familyId] = family.id
            it[ParentsTable.googleSub] = googleSub
            it[ParentsTable.email] = email
            it[ParentsTable.name] = name
        }[ParentsTable.id]

        // Engangsbruk: koden kan ikke brukes igjen etter at forelder #2 er lagt til.
        FamiliesTable.update({ FamiliesTable.id eq family.id }) {
            it[FamiliesTable.inviteCode] = null
        }

        ParentRecord(parentId, family.id, googleSub, email, name)
    }

    /** Oppdaterer visningsnavn og/eller avatar for den innloggede brukeren selv
     * (se ProfileRoutes) — aldri kallbart for andre parentId enn ens egen sesjon. */
    fun updateProfile(parentId: UUID, name: String, avatar: String?) = transaction(database) {
        ParentsTable.update({ ParentsTable.id eq parentId }) {
            it[ParentsTable.name] = name
            it[ParentsTable.avatar] = avatar
        }
    }

    /** Setter forelderens egen valgte kalender for skriving av tildelinger, og
     * (valgfritt) en avvikende kalender for tilgjengelighetssjekk (se
     * CalendarRoutes) — samme "kun egen rad"-mønster som `updateProfile`,
     * `parentId` er alltid fra sesjonen. `availabilityCalendarId = null`
     * betyr "bruk samme kalender som calendarId" (checkboxen i UI-et). */
    fun updateParentCalendars(parentId: UUID, calendarId: String, availabilityCalendarId: String?) = transaction(database) {
        ParentsTable.update({ ParentsTable.id eq parentId }) {
            it[ParentsTable.calendarId] = calendarId
            it[ParentsTable.availabilityCalendarId] = availabilityCalendarId
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
    )

    private fun org.jetbrains.exposed.sql.ResultRow.toFamilyRecord() = FamilyRecord(
        id = this[FamiliesTable.id],
        sharedCalendarId = this[FamiliesTable.sharedCalendarId],
        inviteCode = this[FamiliesTable.inviteCode],
    )
}
