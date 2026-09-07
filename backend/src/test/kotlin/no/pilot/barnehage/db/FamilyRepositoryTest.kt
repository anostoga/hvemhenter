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

    @Test
    fun `addHelper oppretter en hjelper uten googleSub eller email`() {
        createParent() // sørger for familyId er satt (via createParent) — sletter i tearDown
        val helper = repository.addHelper(familyId, "Bestemor", "🐻")

        assertEquals(true, helper.isHelper)
        assertNull(helper.googleSub)
        assertNull(helper.email)
        assertEquals("Bestemor", helper.name)
        assertEquals("🐻", helper.avatar)

        val reloaded = repository.findParent(helper.id)!!
        assertEquals(true, reloaded.isHelper)
        assertNull(reloaded.googleSub)

        // Rydd opp den ekstra hjelper-raden selv, siden testklassens @AfterTest kun
        // sletter `parentId` (den vanlige forelderen created av createParent()).
        transaction(database) { ParentsTable.deleteWhere { Op.build { ParentsTable.id eq helper.id } } }
    }

    @Test
    fun `hjelpere telles ikke med i parentCount`() {
        createParent()
        val helper = repository.addHelper(familyId, "Bestefar", null)

        assertEquals(1, repository.parentCount(familyId), "kun den innloggede forelderen skal telle, ikke hjelperen")

        transaction(database) { ParentsTable.deleteWhere { Op.build { ParentsTable.id eq helper.id } } }
    }

    @Test
    fun `findParents inkluderer baade innloggede foreldre og hjelpere`() {
        createParent()
        val helper = repository.addHelper(familyId, "Tante Kari", null)

        val all = repository.findParents(familyId)

        assertEquals(2, all.size)
        assertEquals(setOf(parentId, helper.id), all.map { it.id }.toSet())

        transaction(database) { ParentsTable.deleteWhere { Op.build { ParentsTable.id eq helper.id } } }
    }

    @Test
    fun `removeHelper fjerner kun hjelpere, ikke innloggede foreldre`() {
        createParent()
        val helper = repository.addHelper(familyId, "Onkel Ola", null)

        val removedHelper = repository.removeHelper(familyId, helper.id)
        val removedParent = repository.removeHelper(familyId, parentId)

        assertEquals(true, removedHelper, "en ekte hjelper skal kunne fjernes")
        assertEquals(false, removedParent, "en innlogget forelder skal IKKE kunne fjernes via removeHelper")
        assertNull(repository.findParent(helper.id))
        assertEquals(parentId, repository.findParent(parentId)!!.id, "forelderen skal fortsatt finnes")
    }

    @Test
    fun `removeHelper er scopet til familien - kan ikke fjerne en annen families hjelper`() {
        createParent()
        val helper = repository.addHelper(familyId, "Fetter Per", null)
        val otherFamilyId = UUID.randomUUID() // finnes ikke i databasen — nok til å bevise scopingen

        val removed = repository.removeHelper(otherFamilyId, helper.id)

        assertEquals(false, removed)
        assertEquals(helper.id, repository.findParent(helper.id)!!.id, "hjelperen skal fortsatt finnes")

        transaction(database) { ParentsTable.deleteWhere { Op.build { ParentsTable.id eq helper.id } } }
    }

    @Test
    fun `updateHelper endrer navn og avatar paa en hjelper`() {
        createParent()
        val helper = repository.addHelper(familyId, "Bestemor", "🐻")

        val updated = repository.updateHelper(familyId, helper.id, "Bestemor Anne", "🐰")

        assertEquals("Bestemor Anne", updated?.name)
        assertEquals("🐰", updated?.avatar)
        val reloaded = repository.findParent(helper.id)!!
        assertEquals("Bestemor Anne", reloaded.name)
        assertEquals("🐰", reloaded.avatar)

        transaction(database) { ParentsTable.deleteWhere { Op.build { ParentsTable.id eq helper.id } } }
    }

    @Test
    fun `updateHelper kan ikke endre en innlogget forelder`() {
        createParent()

        val updated = repository.updateHelper(familyId, parentId, "Nytt navn", null)

        assertNull(updated)
        assertEquals("Forelder", repository.findParent(parentId)!!.name, "forelderens navn skal være uendret")
    }

    @Test
    fun `updateHelper er scopet til familien - kan ikke endre en annen families hjelper`() {
        createParent()
        val helper = repository.addHelper(familyId, "Fetter Per", null)
        val otherFamilyId = UUID.randomUUID()

        val updated = repository.updateHelper(otherFamilyId, helper.id, "Uvedkommende navn", null)

        assertNull(updated)
        assertEquals("Fetter Per", repository.findParent(helper.id)!!.name)

        transaction(database) { ParentsTable.deleteWhere { Op.build { ParentsTable.id eq helper.id } } }
    }
}
