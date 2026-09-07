package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Dekker `updateParentCalendars`/`effectiveAvailabilityCalendarId()` mot en
 * ekte lokal Postgres — spesielt normaliseringen som hindrer at en forelder
 * havner i en selvmotsigende tilstand (både en spesifikk
 * tilgjengelighetskalender OG "deaktivert" satt samtidig), at
 * `availabilityDisabled = true` faktisk lar en tidligere valgt
 * tilgjengelighetskalender-registrering fjernes, og at `calendarId` selv kan
 * settes til `null` (brukeren velger å ikke skrive tildelinger til noen
 * kalender i det hele tatt).
 */
class FamilyRepositoryTest {
    private val database = Database.connect(
        url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/barnehage",
        user = System.getenv("DATABASE_USER") ?: "postgres",
        password = System.getenv("DATABASE_PASSWORD") ?: "localdev",
        driver = "org.postgresql.Driver",
    )
    private val repository = FamilyRepository(database)

    private lateinit var familyId: UUID
    private lateinit var parentId: UUID

    @AfterTest
    fun tearDown() {
        transaction(database) {
            ParentsTable.deleteWhere { Op.build { ParentsTable.id eq parentId } }
            FamiliesTable.deleteWhere { Op.build { FamiliesTable.id eq familyId } }
        }
    }

    private fun createParent(): UUID = transaction(database) {
        val newFamilyId = FamiliesTable.insert { it[sharedCalendarId] = "cal-shared" }[FamiliesTable.id]
        familyId = newFamilyId
        parentId = ParentsTable.insert {
            // NB: bruker `newFamilyId` (ikke `familyId`) på høyre side her — unqualifisert
            // `familyId` inne i denne lambdaen ville resolvet til `ParentsTable.familyId`
            // (kolonnen, siden `this` er den implisitte mottakeren), IKKE testklassens
            // `familyId: UUID`-felt, og gitt en kryptisk "invalid reference to FROM-clause
            // entry"-feil fra Postgres. Samme fallgruve håndteres med
            // `this@FamilyScopedAssignmentRepository.familyId` i FamilyScopedAssignmentRepository.kt.
            it[ParentsTable.familyId] = newFamilyId
            it[googleSub] = "sub-${UUID.randomUUID()}"
            it[email] = "p@example.com"
            it[name] = "Forelder"
        }[ParentsTable.id]
        parentId
    }

    @Test
    fun `uten avvikende tilgjengelighetskalender faller den tilbake til skrivekalenderen`() {
        createParent()
        repository.updateParentCalendars(parentId, "skrive-kalender", null)

        val parent = repository.findParent(parentId)!!

        assertEquals(false, parent.availabilityDisabled)
        assertNull(parent.availabilityCalendarId)
        assertEquals("skrive-kalender", parent.effectiveAvailabilityCalendarId())
    }

    @Test
    fun `en spesifikk tilgjengelighetskalender lagres og brukes fremfor skrivekalenderen`() {
        createParent()
        repository.updateParentCalendars(parentId, "skrive-kalender", "tilgjengelighet-kalender")

        val parent = repository.findParent(parentId)!!

        assertEquals("tilgjengelighet-kalender", parent.availabilityCalendarId)
        assertEquals("tilgjengelighet-kalender", parent.effectiveAvailabilityCalendarId())
    }

    @Test
    fun `availabilityDisabled lagres og gir null uansett kalendervalg`() {
        createParent()
        repository.updateParentCalendars(parentId, "skrive-kalender", null, availabilityDisabled = true)

        val parent = repository.findParent(parentId)!!

        assertEquals(true, parent.availabilityDisabled)
        assertNull(parent.effectiveAvailabilityCalendarId())
    }

    @Test
    fun `availabilityDisabled=true normaliserer bort en medsendt tilgjengelighetskalender`() {
        createParent()

        // Selvmotsigende input (en spesifikk kalender-id OG deaktivert samtidig) skal
        // ikke kunne lagres i databasen — deaktivert vinner, kalender-id nulles ut.
        repository.updateParentCalendars(parentId, "skrive-kalender", "tilgjengelighet-kalender", availabilityDisabled = true)

        val parent = repository.findParent(parentId)!!

        assertEquals(true, parent.availabilityDisabled)
        assertNull(parent.availabilityCalendarId)
    }

    @Test
    fun `en tidligere valgt tilgjengelighetskalender kan fjernes ved aa deaktivere`() {
        createParent()
        repository.updateParentCalendars(parentId, "skrive-kalender", "tilgjengelighet-kalender")
        assertEquals("tilgjengelighet-kalender", repository.findParent(parentId)!!.availabilityCalendarId)

        repository.updateParentCalendars(parentId, "skrive-kalender", null, availabilityDisabled = true)

        val parent = repository.findParent(parentId)!!
        assertNull(parent.availabilityCalendarId)
        assertEquals(true, parent.availabilityDisabled)
    }

    @Test
    fun `calendarId kan settes til null - brukeren velger aa ikke skrive til noen kalender`() {
        createParent()

        repository.updateParentCalendars(parentId, null, null)

        val parent = repository.findParent(parentId)!!
        assertNull(parent.calendarId)
        // Uten en skrivekalender degraderer tilgjengelighetssjekken naturlig til
        // null også (ingen fallback-kalender å falle tilbake på) — samme
        // fail-soft-oppførsel som når forelderen aldri har valgt noen kalender.
        assertNull(parent.effectiveAvailabilityCalendarId())
    }

    @Test
    fun `en tidligere valgt skrivekalender kan fjernes ved aa sette calendarId til null`() {
        createParent()
        repository.updateParentCalendars(parentId, "skrive-kalender", null)
        assertEquals("skrive-kalender", repository.findParent(parentId)!!.calendarId)

        repository.updateParentCalendars(parentId, null, null)

        assertNull(repository.findParent(parentId)!!.calendarId)
    }
}
