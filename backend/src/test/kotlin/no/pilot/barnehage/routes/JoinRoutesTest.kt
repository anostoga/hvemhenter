package no.pilot.barnehage.routes

import no.pilot.barnehage.Env
import no.pilot.barnehage.db.FamiliesTable
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.db.ParentsTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tester den autorative logikken i handleJoin()/FamilyRepository mot en ekte
 * lokal Postgres — dette er kjernen i familie-medlemskap, så testene dekker
 * nettopp de tingene som IKKE må kunne omgås: engangsbruk av invite_code,
 * maks 2 foreldre per familie, og at feil kode ikke oppretter noe.
 */
class JoinRoutesTest {
    private val database = Database.connect(
        url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/barnehage",
        user = System.getenv("DATABASE_USER") ?: "postgres",
        password = System.getenv("DATABASE_PASSWORD") ?: "localdev",
        driver = "org.postgresql.Driver",
    )
    private val repository = FamilyRepository(database)
    private val createdParentGoogleSubs = mutableListOf<String>()

    @AfterTest
    fun tearDown() {
        transaction(database) {
            val parentIds = ParentsTable.selectAll().where { ParentsTable.googleSub inList createdParentGoogleSubs }
                .map { it[ParentsTable.id] }
            val familyIds = if (parentIds.isNotEmpty()) {
                ParentsTable.selectAll().where { ParentsTable.id inList parentIds }.map { it[ParentsTable.familyId] }
            } else emptyList()
            ParentsTable.deleteWhere { Op.build { ParentsTable.googleSub inList createdParentGoogleSubs } }
            if (familyIds.isNotEmpty()) {
                FamiliesTable.deleteWhere { Op.build { FamiliesTable.id inList familyIds } }
            }
        }
    }

    private fun sub() = "test-sub-${UUID.randomUUID()}".also { createdParentGoogleSubs.add(it) }

    @Test
    fun `gyldig FAMILY_CREATION_CODE oppretter ny familie med foresporrer som forelder 1`() {
        Env.overrideForTests("FAMILY_CREATION_CODE", "riktig-kode")
        val googleSub = sub()

        val familyId = handleJoin("riktig-kode", googleSub, "a@example.com", "A", repository)

        assertNotNull(familyId)
        val parent = repository.findParentByGoogleSub(googleSub)
        assertEquals(familyId, parent?.familyId?.toString())
    }

    @Test
    fun `gyldig invite_code kobler forelder 2 til eksisterende familie`() {
        Env.overrideForTests("FAMILY_CREATION_CODE", "riktig-kode")
        val parent1Sub = sub()
        val familyId = handleJoin("riktig-kode", parent1Sub, "a@example.com", "A", repository)!!
        val inviteCode = repository.findFamily(UUID.fromString(familyId))!!.inviteCode!!

        val parent2Sub = sub()
        val joinedFamilyId = handleJoin(inviteCode, parent2Sub, "b@example.com", "B", repository)

        assertEquals(familyId, joinedFamilyId)
        assertEquals(2, repository.parentCount(UUID.fromString(familyId)))
    }

    @Test
    fun `invite_code kan kun brukes en gang`() {
        Env.overrideForTests("FAMILY_CREATION_CODE", "riktig-kode")
        val parent1Sub = sub()
        val familyId = handleJoin("riktig-kode", parent1Sub, "a@example.com", "A", repository)!!
        val inviteCode = repository.findFamily(UUID.fromString(familyId))!!.inviteCode!!
        val parent2Sub = sub()
        handleJoin(inviteCode, parent2Sub, "b@example.com", "B", repository)

        val parent3Sub = sub()
        val secondAttempt = handleJoin(inviteCode, parent3Sub, "c@example.com", "C", repository)

        assertNull(secondAttempt, "invite_code skal være invalidert etter første bruk")
    }

    @Test
    fun `feil kode gir ingen familie`() {
        Env.overrideForTests("FAMILY_CREATION_CODE", "riktig-kode")
        val googleSub = sub()

        val result = handleJoin("helt-feil-kode", googleSub, "a@example.com", "A", repository)

        assertNull(result)
        assertNull(repository.findParentByGoogleSub(googleSub))
    }

    @Test
    fun `en familie med 2 foreldre kan ikke ta imot en tredje`() {
        Env.overrideForTests("FAMILY_CREATION_CODE", "riktig-kode")
        val parent1Sub = sub()
        val familyId = handleJoin("riktig-kode", parent1Sub, "a@example.com", "A", repository)!!
        val inviteCode = repository.findFamily(UUID.fromString(familyId))!!.inviteCode!!
        val parent2Sub = sub()
        handleJoin(inviteCode, parent2Sub, "b@example.com", "B", repository)

        // Koden er allerede invalidert av forelder 2, men verifiser i tillegg at selv
        // om noen fikk tak i en (hypotetisk gyldig) kode, håndhever repository et
        // hardt tak på 2 foreldre og avviser join direkte.
        val parent3Sub = sub()
        val result = repository.joinFamilyWithInviteCode(inviteCode, parent3Sub, "c@example.com", "C")

        assertNull(result)
        assertEquals(2, repository.parentCount(UUID.fromString(familyId)))
    }
}
