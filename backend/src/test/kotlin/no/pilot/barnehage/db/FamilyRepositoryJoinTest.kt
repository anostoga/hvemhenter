package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dekker den nye kodefrie familieopprettelsen og den atomiske innmeldingen av forelder 2 via
 * `families.invite_code` (se FamilyRepository.kt). Den tidligere FAMILY_CREATION_CODE/
 * admin-opprettingskode-logikken (handleJoin) er fjernet — en ny bruker oppretter nå familien sin
 * direkte uten kode (se OnboardingRoutesTest.kt for selve HTTP-flyten).
 */
class FamilyRepositoryJoinTest {
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
    fun `opprettelse uten kode gir en helt ny familie med foresporrer som forelder 1`() {
        val googleSub = sub()

        val parent = repository.createFamilyWithFirstParent("", "invite-1", googleSub, "a@example.com", "A")

        val found = repository.findParentByGoogleSub(googleSub)
        assertEquals(parent.familyId, found?.familyId)
        assertEquals(1, repository.parentCount(parent.familyId))
    }

    @Test
    fun `opprettelse er idempotent for samme google-bruker`() {
        val googleSub = sub()
        val first = repository.createFamilyWithFirstParent("", "invite-1", googleSub, "a@example.com", "A")

        val second = repository.createFamilyWithFirstParent("", "invite-2", googleSub, "a@example.com", "A")

        assertEquals(first.id, second.id, "et doble-innsendt opprettelsesforsøk skal gi samme forelder, ikke en duplikat")
        assertEquals(1, repository.parentCount(first.familyId))
    }

    @Test
    fun `gyldig invite_code kobler forelder 2 til eksisterende familie`() {
        val parent1Sub = sub()
        val parent1 = repository.createFamilyWithFirstParent("", "inviteCode123", parent1Sub, "a@example.com", "A")

        val parent2Sub = sub()
        val result = repository.joinFamilyWithInviteCode("inviteCode123", parent2Sub, "b@example.com", "B")

        val joined = assertIs<JoinFamilyResult.Joined>(result)
        assertEquals(parent1.familyId, joined.parent.familyId)
        assertEquals(2, repository.parentCount(parent1.familyId))
    }

    @Test
    fun `invite_code kan kun brukes en gang`() {
        val parent1Sub = sub()
        repository.createFamilyWithFirstParent("", "inviteCode456", parent1Sub, "a@example.com", "A")
        val parent2Sub = sub()
        repository.joinFamilyWithInviteCode("inviteCode456", parent2Sub, "b@example.com", "B")

        val parent3Sub = sub()
        val secondAttempt = repository.joinFamilyWithInviteCode("inviteCode456", parent3Sub, "c@example.com", "C")

        assertIs<JoinFamilyResult.InvalidCode>(secondAttempt, "invite_code skal være invalidert etter første bruk")
    }

    @Test
    fun `feil kode gir ingen familie`() {
        val googleSub = sub()

        val result = repository.joinFamilyWithInviteCode("helt-feil-kode", googleSub, "a@example.com", "A")

        assertIs<JoinFamilyResult.InvalidCode>(result)
        assertNull(repository.findParentByGoogleSub(googleSub))
    }

    @Test
    fun `en familie med 2 foreldre kan ikke ta imot en tredje`() {
        val parent1Sub = sub()
        val parent1 = repository.createFamilyWithFirstParent("", "inviteCode789", parent1Sub, "a@example.com", "A")
        val parent2Sub = sub()
        repository.joinFamilyWithInviteCode("inviteCode789", parent2Sub, "b@example.com", "B")

        // Koden er allerede konsumert av forelder 2 over, så et tredje forsøk ser den som ugyldig —
        // akkurat slik det skal oppføre seg: det finnes ingen gyldig kode igjen for denne familien.
        val parent3Sub = sub()
        val result = repository.joinFamilyWithInviteCode("inviteCode789", parent3Sub, "c@example.com", "C")

        assertIs<JoinFamilyResult.InvalidCode>(result)
        assertEquals(2, repository.parentCount(parent1.familyId))
    }

    @Test
    fun `samtidige forsok pa samme invite_code slipper bare en forelder 2 inn`() {
        val parent1Sub = sub()
        val parent1 = repository.createFamilyWithFirstParent("", "inviteCodeRace", parent1Sub, "a@example.com", "A")
        val parent2Sub = sub()
        val parent3Sub = sub()

        val pool = Executors.newFixedThreadPool(2)
        val readyLatch = CountDownLatch(2)
        val startLatch = CountDownLatch(1)
        val results = java.util.concurrent.ConcurrentLinkedQueue<JoinFamilyResult>()

        listOf(parent2Sub to "b@example.com", parent3Sub to "c@example.com").forEach { (sub, email) ->
            pool.submit {
                readyLatch.countDown()
                startLatch.await()
                results.add(repository.joinFamilyWithInviteCode("inviteCodeRace", sub, email, "Navn"))
            }
        }
        readyLatch.await()
        startLatch.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))

        val joined = results.filterIsInstance<JoinFamilyResult.Joined>()
        val rejected = results.filterIsInstance<JoinFamilyResult.InvalidCode>()
        assertEquals(1, joined.size, "nøyaktig én av de to samtidige forsøkene skal lykkes")
        assertEquals(1, rejected.size)
        assertEquals(2, repository.parentCount(parent1.familyId))
    }
}
