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

/**
 * Familie-scopet lesing/skriving av tildelinger. `familyId` er et PÅKREVD
 * konstruktørparameter (ikke et valgfritt filter man må huske) — det finnes
 * ingen metode her som kan spørre eller skrive på tvers av familier.
 *
 * Instansier én av disse PER REQUEST, med `familyId` hentet fra den
 * autentiserte sesjonen (se auth/SessionAuth.kt) — ALDRI fra request-body,
 * query-param eller annen klient-styrt input. Det er nettopp det som ville
 * gjort family-scoping virkningsløst.
 */
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

    /** Oppretter en ny tildeling. Krever at kalleren allerede har verifisert at
     * `parentId` faktisk tilhører `familyId` (se JoinRoutes/parent-oppslag) —
     * denne metoden stoler på det, den slår ikke opp parents-tabellen selv. */
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

    /** Finnes det allerede en tildeling for denne datoen/typen i familien? Brukes til
     * å avgjøre om `/api/assign` skal opprette en ny rad eller erstatte en eksisterende
     * (kolonnene `family_id, date, type` har en unik-constraint i databasen). */
    fun findByDateAndType(date: LocalDate, type: String): FamilyAssignment? = transaction(database) {
        AssignmentsTable
            .selectAll()
            .where { (AssignmentsTable.familyId eq familyId) and (AssignmentsTable.date eq date) and (AssignmentsTable.type eq type) }
            .firstOrNull()?.toFamilyAssignment()
    }

    /** Erstatter en eksisterende tildeling for `date`/`type` med ny forelder/kilde/
     * kalenderhendelse. Kalleren er ansvarlig for å slette den gamle Google-kalenderhendelsen
     * (`googleEventId` på raden som erstattes) FØR dette kalles, ellers blir den værende igjen
     * i kalenderen uten at noen tildeling peker på den lenger. */
    fun update(id: UUID, parentId: UUID, source: String, googleEventId: String?) = transaction(database) {
        AssignmentsTable.update({ AssignmentsTable.id eq id }) {
            it[AssignmentsTable.parentId] = parentId
            it[AssignmentsTable.assignmentSource] = source
            it[AssignmentsTable.googleEventId] = googleEventId
        }
    }

    /** Finner en enkelt tildeling ved id — scopet til `familyId`, slik at et forsøk på å
     * hente/slette en annen families tildeling ved å gjette en UUID gir null, ikke en
     * treff fra en annen familie. */
    fun findById(id: UUID): FamilyAssignment? = transaction(database) {
        AssignmentsTable
            .selectAll()
            .where { (AssignmentsTable.familyId eq familyId) and (AssignmentsTable.id eq id) }
            .firstOrNull()?.toFamilyAssignment()
    }

    /** Sletter én tildeling. Scopet til `familyId` (se `findById`) — sletter kun hvis
     * raden faktisk tilhører DENNE familien, ellers er dette en no-op (0 rader rammet).
     * Kalleren er ansvarlig for å slette en ev. tilhørende Google-kalenderhendelse
     * FØR dette kalles (se AssignmentRoutes). */
    fun delete(id: UUID) = transaction(database) {
        AssignmentsTable.deleteWhere { org.jetbrains.exposed.sql.Op.build { (AssignmentsTable.familyId eq familyId) and (AssignmentsTable.id eq id) } }
    }

    /** Kun til bruk i tester/nullstilling — rammer utelukkende `familyId`
     * repositoryet ble instansiert med, aldri andre familier. */
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
