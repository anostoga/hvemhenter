package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import no.pilot.barnehage.domain.ConfirmationStatus

class FamilyScopedAssignmentRepositoryTest {
    private val database = Database.connect(
        url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:9876/barnehage",
        user = System.getenv("DATABASE_USER") ?: "postgres",
        password = System.getenv("DATABASE_PASSWORD") ?: "localdev",
        driver = "org.postgresql.Driver",
    )

    private lateinit var familyA: UUID
    private lateinit var familyB: UUID
    private lateinit var parentA: UUID
    private lateinit var parentB: UUID

    @BeforeTest
    fun setUp() {
        transaction(database) {
            familyA = FamiliesTable.insert { it[sharedCalendarId] = "cal-a" }[FamiliesTable.id]
            familyB = FamiliesTable.insert { it[sharedCalendarId] = "cal-b" }[FamiliesTable.id]
            parentA = ParentsTable.insert {
                it[familyId] = familyA
                it[googleSub] = "sub-a-${UUID.randomUUID()}"
                it[email] = "a@example.com"
                it[name] = "Forelder A"
            }[ParentsTable.id]
            parentB = ParentsTable.insert {
                it[familyId] = familyB
                it[googleSub] = "sub-b-${UUID.randomUUID()}"
                it[email] = "b@example.com"
                it[name] = "Forelder B"
            }[ParentsTable.id]
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            AssignmentsTable.deleteWhere { org.jetbrains.exposed.sql.Op.build { (AssignmentsTable.familyId eq familyA) or (AssignmentsTable.familyId eq familyB) } }
            ParentsTable.deleteWhere { org.jetbrains.exposed.sql.Op.build { (ParentsTable.id eq parentA) or (ParentsTable.id eq parentB) } }
            FamiliesTable.deleteWhere { org.jetbrains.exposed.sql.Op.build { (FamiliesTable.id eq familyA) or (FamiliesTable.id eq familyB) } }
        }
    }

    @Test
    fun `familie A kan ikke lese familie B sine tildelinger`() {
        val repoB = FamilyScopedAssignmentRepository(familyB, database)
        repoB.insert(LocalDate.of(2026, 8, 27), "DROPOFF", parentB, "MANUAL", null)

        val repoA = FamilyScopedAssignmentRepository(familyA, database)
        val result = repoA.findByDate(LocalDate.of(2026, 8, 27))

        assertTrue(result.isEmpty(), "familie A skal ikke se familie B sine tildelinger")
    }

    @Test
    fun `familie A ser kun sine egne tildelinger, ikke andre familiers`() {
        val repoA = FamilyScopedAssignmentRepository(familyA, database)
        val repoB = FamilyScopedAssignmentRepository(familyB, database)
        repoA.insert(LocalDate.of(2026, 8, 28), "PICKUP", parentA, "AUTO", null)
        repoB.insert(LocalDate.of(2026, 8, 28), "PICKUP", parentB, "AUTO", null)

        val resultA = repoA.all()

        assertEquals(1, resultA.size)
        assertEquals(parentA, resultA.first().parentId)
    }

    @Test
    fun `sletting rammer kun egen familie`() {
        val repoA = FamilyScopedAssignmentRepository(familyA, database)
        val repoB = FamilyScopedAssignmentRepository(familyB, database)
        repoA.insert(LocalDate.of(2026, 8, 29), "DROPOFF", parentA, "MANUAL", null)
        repoB.insert(LocalDate.of(2026, 8, 29), "DROPOFF", parentB, "MANUAL", null)

        repoA.deleteAllForThisFamily()

        assertTrue(repoA.all().isEmpty())
        assertEquals(1, repoB.all().size)
    }

    @Test
    fun `oppdatering av eksisterende tildeling bytter forelder uten å opprette en ny rad`() {

        val repoA = FamilyScopedAssignmentRepository(familyA, database)
        val id = repoA.insert(LocalDate.of(2026, 8, 30), "PICKUP", parentA, "MANUAL", null)

        val existing = repoA.findByDateAndType(LocalDate.of(2026, 8, 30), "PICKUP")
        assertEquals(id, existing?.id)

        repoA.update(id, parentB, "MANUAL", null)

        val all = repoA.all()
        assertEquals(1, all.size, "skal fortsatt bare være én rad for datoen/typen")
        assertEquals(parentB, all.first().parentId)
    }

    @Test
    fun `hasAssignmentsForParent er sann naar personen har en tildeling, usann ellers`() {
        val repoA = FamilyScopedAssignmentRepository(familyA, database)

        assertEquals(false, repoA.hasAssignmentsForParent(parentA))

        repoA.insert(LocalDate.of(2026, 8, 31), "DROPOFF", parentA, "MANUAL", null)

        assertEquals(true, repoA.hasAssignmentsForParent(parentA))
        assertEquals(
            false,
            repoA.hasAssignmentsForParent(parentB),
            "parentB har ingen tildeling i familie A (og tilhører uansett familie B)",
        )
    }

    @Test
    fun `tildeling lagrer ventende bekreftelse og tokenhash`() {
        val repo = FamilyScopedAssignmentRepository(familyA, database)
        val tokenHash = "a".repeat(64)
        val expiresAt = Instant.now().plusSeconds(3600)
        val id = repo.insert(
            date = LocalDate.of(2026, 9, 1),
            type = "PICKUP",
            parentId = parentA,
            source = "MANUAL",
            googleEventId = null,
            confirmationStatus = ConfirmationStatus.PENDING,
            confirmationTokenHash = tokenHash,
            confirmationTokenExpiresAt = expiresAt,
        )

        val saved = repo.findById(id)

        assertEquals(false, saved?.confirmationLinkIssued)
    }

    @Test
    fun `helper kan tildeles uten e-posttoken`() {
        val helper = FamilyRepository(database).addHelper(familyA, "Bestemor", null)
        val repo = FamilyScopedAssignmentRepository(familyA, database)

        val id = repo.insert(
            date = LocalDate.of(2026, 9, 3),
            type = "PICKUP",
            parentId = helper.id,
            source = "MANUAL",
            googleEventId = null,
            confirmationStatus = ConfirmationStatus.PENDING,
        )

        val saved = repo.findById(id)
        assertEquals(ConfirmationStatus.PENDING, saved?.confirmationStatus)
        assertNull(AssignmentConfirmationRepository(database).find("d".repeat(64)))
    }

    @Test
    fun `endring av helper-e-post ugyldiggjør gammel lenke og lar familien sende ny`() {
        val familyRepository = FamilyRepository(database)
        val helper = familyRepository.addHelper(familyA, "Bestemor", null, "old@example.com")
        val repo = FamilyScopedAssignmentRepository(familyA, database)
        val oldHash = "b".repeat(64)
        val id = repo.insert(
            date = LocalDate.of(2026, 9, 2),
            type = "DROPOFF",
            parentId = helper.id,
            source = "MANUAL",
            googleEventId = null,
            confirmationStatus = ConfirmationStatus.PENDING,
            confirmationTokenHash = oldHash,
            confirmationTokenExpiresAt = Instant.now().plusSeconds(3600),
        )

        familyRepository.updateHelper(familyA, helper.id, "Bestemor", null, "new@example.com")

        assertNull(AssignmentConfirmationRepository(database).find(oldHash))
        assertEquals(ConfirmationStatus.PENDING, repo.findById(id)?.confirmationStatus)
        assertEquals(false, repo.findById(id)?.confirmationLinkIssued)
        assertEquals(false, repo.findById(id)?.confirmationEmailSent)
        val newHash = "c".repeat(64)
        assertTrue(repo.updateConfirmationToken(id, newHash, Instant.now().plusSeconds(3600)))
        assertEquals(true, repo.findById(id)?.confirmationLinkIssued)
        assertTrue(repo.markConfirmationLinkIssued(id, newHash))
        assertEquals(true, repo.findById(id)?.confirmationLinkIssued)
        val confirmationRepository = AssignmentConfirmationRepository(database)
        assertEquals(ConfirmationStatus.PENDING, confirmationRepository.find(newHash)?.status)
        assertEquals(ConfirmationAnswerResult.UPDATED, confirmationRepository.answer(newHash, ConfirmationStatus.ACCEPTED))
        assertEquals(ConfirmationAnswerResult.ALREADY_RECORDED, confirmationRepository.answer(newHash, ConfirmationStatus.ACCEPTED))
        assertEquals(ConfirmationAnswerResult.CONFLICT, confirmationRepository.answer(newHash, ConfirmationStatus.DECLINED))
    }

    @Test
    fun `ny delingslenke erstatter den forrige lenken`() {
        val helper = FamilyRepository(database).addHelper(familyA, "Bestemor", null)
        val repo = FamilyScopedAssignmentRepository(familyA, database)
        val firstHash = "d".repeat(64)
        val secondHash = "e".repeat(64)
        val id = repo.insert(
            date = LocalDate.of(2026, 9, 4),
            type = "PICKUP",
            parentId = helper.id,
            source = "MANUAL",
            googleEventId = null,
            confirmationStatus = ConfirmationStatus.PENDING,
        )

        assertTrue(repo.replaceConfirmationToken(id, firstHash, Instant.now().plusSeconds(3600)))
        assertTrue(repo.replaceConfirmationToken(id, secondHash, Instant.now().plusSeconds(3600)))
        val confirmations = AssignmentConfirmationRepository(database)
        assertNull(confirmations.find(firstHash))
        assertEquals(ConfirmationStatus.PENDING, confirmations.find(secondHash)?.status)
    }

    @Test
    fun `e-post kan bare markeres sendt én gang for aktiv lenke`() {
        val helper = FamilyRepository(database).addHelper(familyA, "Bestemor", null)
        val repo = FamilyScopedAssignmentRepository(familyA, database)
        val tokenHash = "f".repeat(64)
        val id = repo.insert(
            date = LocalDate.of(2026, 9, 5),
            type = "PICKUP",
            parentId = helper.id,
            source = "MANUAL",
            googleEventId = null,
            confirmationStatus = ConfirmationStatus.PENDING,
        )
        assertTrue(repo.replaceConfirmationToken(id, tokenHash, Instant.now().plusSeconds(3600)))

        assertTrue(repo.hasActiveConfirmationToken(id, tokenHash))
        assertTrue(repo.markConfirmationEmailSent(id, tokenHash))
        assertEquals(false, repo.markConfirmationEmailSent(id, tokenHash))
        assertEquals(true, repo.findById(id)?.confirmationEmailSent)

        repo.clearConfirmationEmailSent(id, tokenHash)
        assertEquals(false, repo.findById(id)?.confirmationEmailSent)
    }

    @Test
    fun `familien kan registrere svar på ventende hjelpertildeling`() {
        val helper = FamilyRepository(database).addHelper(familyA, "Bestemor", null)
        val repo = FamilyScopedAssignmentRepository(familyA, database)
        val id = repo.insert(
            date = LocalDate.of(2026, 9, 6),
            type = "PICKUP",
            parentId = helper.id,
            source = "MANUAL",
            googleEventId = null,
            confirmationStatus = ConfirmationStatus.PENDING,
        )

        assertEquals(ConfirmationAnswerResult.UPDATED, repo.answerPendingConfirmation(id, helper.id, ConfirmationStatus.ACCEPTED))
        assertEquals(ConfirmationStatus.ACCEPTED, repo.findById(id)?.confirmationStatus)
        assertEquals(ConfirmationAnswerResult.ALREADY_RECORDED, repo.answerPendingConfirmation(id, helper.id, ConfirmationStatus.ACCEPTED))
        assertEquals(ConfirmationAnswerResult.CONFLICT, repo.answerPendingConfirmation(id, helper.id, ConfirmationStatus.DECLINED))

        val otherFamilyRepository = FamilyScopedAssignmentRepository(familyB, database)
        assertEquals(ConfirmationAnswerResult.CONFLICT, otherFamilyRepository.answerPendingConfirmation(id, helper.id, ConfirmationStatus.DECLINED))
    }
}
