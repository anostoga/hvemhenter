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
    /** Null for hjelpere (`isHelper = true`) — de logger aldri inn. */
    val googleSub: String?,
    /** Null for hjelpere, samme begrunnelse som `googleSub`. */
    val email: String?,
    val name: String,
    val avatar: String? = null,
    /** Forelderens egen valgte kalender for skriving av tildelinger (se /api/calendars/mine). */
    val calendarId: String? = null,
    /** Kalenderen tilgjengelighet (opptatte tider) hentes fra. Null betyr "samme
     * som calendarId" — bruk `effectiveAvailabilityCalendarId()` for oppslag. */
    val availabilityCalendarId: String? = null,
    /** Eksplisitt "ikke sjekk tilgjengelighet i det hele tatt", atskilt fra
     * `availabilityCalendarId = null` (som betyr "samme som calendarId"). */
    val availabilityDisabled: Boolean = false,
    /** Sant for en "hjelper" (typisk en slektning) — kan tildeles levering/henting,
     * men logger aldri inn selv og teller ikke mot maks-2-foreldre-grensen. */
    val isHelper: Boolean = false,
)

/** Kalenderen som faktisk skal spørres for opptatte tider — `null` hvis
 * forelderen eksplisitt har skrudd av tilgjengelighetssjekk
 * (`availabilityDisabled`), ellers `availabilityCalendarId` hvis forelderen
 * har valgt en avvikende kalender, ellers `calendarId` (den avkrysningsboksen
 * "bruk samme kalender" på /innstillinger tilsvarer). */
fun ParentRecord.effectiveAvailabilityCalendarId(): String? =
    if (availabilityDisabled) null else (availabilityCalendarId ?: calendarId)

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

    /** Antall INNLOGGEDE foreldre i familien — brukt til å håndheve maks 2 foreldre
     * per familie. Teller aldri hjelpere (`isHelper = true`, se addHelper) — de har
     * ingen egen innloggingsgrense. */
    fun parentCount(familyId: UUID): Long = transaction(database) {
        ParentsTable.selectAll()
            .where { (ParentsTable.familyId eq familyId) and (ParentsTable.isHelper eq false) }
            .count()
    }

    /** Alle foreldre OG hjelpere i familien — brukt av /api/parents, forslagslogikken
     * og tildeling (begge typer kan tildeles levering/henting likt, se
     * AssignmentRoutes/effectiveParents). */
    fun findParents(familyId: UUID): List<ParentRecord> = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.familyId eq familyId }.map { it.toParentRecord() }
    }

    /** Finner en familie som fortsatt har plass (< 2 INNLOGGEDE foreldre, hjelpere
     * teller ikke) opprettet via FAMILY_CREATION_CODE — brukt til å la samme kode
     * brukes av begge foreldre uten at de ender opp i hver sin (tomme) familie. */
    fun findFamilyWithRoom(): FamilyRecord? = transaction(database) {
        FamiliesTable.selectAll()
            .map { it.toFamilyRecord() }
            .firstOrNull { family ->
                ParentsTable.selectAll()
                    .where { (ParentsTable.familyId eq family.id) and (ParentsTable.isHelper eq false) }
                    .count() < 2
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

        val existingParents = ParentsTable.selectAll()
            .where { (ParentsTable.familyId eq family.id) and (ParentsTable.isHelper eq false) }
            .count()
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
     * `parentId` er alltid fra sesjonen. `calendarId = null` betyr "skriv ikke
     * tildelinger til noen kalender" (se AssignmentRoutes, som allerede
     * degraderer gracefully til "ingen kalenderhendelse opprettes" i det
     * tilfellet). `availabilityCalendarId = null` betyr "bruk samme kalender
     * som calendarId" (checkboxen i UI-et). `availabilityDisabled = true`
     * betyr "ikke sjekk tilgjengelighet i det hele tatt" — i så fall tvinges
     * `availabilityCalendarId` til `null` her (normalisering) slik at
     * databasen aldri havner i en selvmotsigende tilstand (både en
     * spesifikk kalender OG "deaktivert" satt samtidig). */
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

    /** Legger en "hjelper" til familien — en person (typisk en slektning) som kan
     * tildeles levering/henting akkurat som en innlogget forelder (se
     * AssignmentRoutes/effectiveParents), men som ALDRI logger inn selv: ingen
     * `googleSub`/`email`, ingen kalender-tilkobling er mulig for denne raden.
     * Teller ikke mot maks-2-foreldre-grensen (se `parentCount`). */
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

    /** Fjerner en hjelper. KUN rader med `isHelper = true` kan fjernes her — det
     * finnes (fortsatt) ingen funksjon for å slette en ekte innlogget forelder.
     * Scopet til `familyId` (samme mønster som resten av repositoryet), så et
     * forsøk på å fjerne en annen families hjelper ved å gjette en UUID er en
     * no-op. Returnerer `false` hvis id-en ikke fantes, ikke var en hjelper,
     * eller ikke tilhørte familien. Kalleren er ansvarlig for å sjekke at
     * hjelperen ikke har eksisterende tildelinger FØR dette kalles (se
     * FamilyRoutes) — denne metoden håndhever ikke det selv, ellers ville
     * `parents`-tabellens manglende `on delete cascade` for tildelinger gitt en
     * rå FK-feil i stedet for en forståelig 409. */
    fun removeHelper(familyId: UUID, parentId: UUID): Boolean = transaction(database) {
        val deleted = ParentsTable.deleteWhere {
            org.jetbrains.exposed.sql.Op.build {
                (ParentsTable.id eq parentId) and (ParentsTable.familyId eq familyId) and (ParentsTable.isHelper eq true)
            }
        }
        deleted > 0
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
    )

    private fun org.jetbrains.exposed.sql.ResultRow.toFamilyRecord() = FamilyRecord(
        id = this[FamiliesTable.id],
        sharedCalendarId = this[FamiliesTable.sharedCalendarId],
        inviteCode = this[FamiliesTable.inviteCode],
    )
}
