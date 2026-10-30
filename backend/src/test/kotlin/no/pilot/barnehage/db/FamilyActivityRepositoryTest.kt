package no.pilot.barnehage.db

import no.pilot.barnehage.domain.ConfirmationStatus
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FamilyActivityRepositoryTest {
    private val database = Database.connect(
        url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:9876/barnehage",
        user = System.getenv("DATABASE_USER") ?: "postgres",
        password = System.getenv("DATABASE_PASSWORD") ?: "localdev",
        driver = "org.postgresql.Driver",
    )
    private val familyIds = mutableListOf<UUID>()
    private val now = Instant.parse("2026-10-30T12:00:00Z")
    private lateinit var familyA: UUID
    private lateinit var familyB: UUID
    private lateinit var parentA: UUID

    @BeforeTest
    fun setUp() {
        familyA = createFamily()
        familyB = createFamily()
        parentA = transaction(database) {
            ParentsTable.insert {
                it[familyId] = familyA
                it[name] = "Testforelder"
                it[googleSub] = "activity-${UUID.randomUUID()}"
            }[ParentsTable.id]
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            AssignmentsTable.deleteWhere { Op.build { AssignmentsTable.familyId inList familyIds } }
            ParentsTable.deleteWhere { Op.build { ParentsTable.familyId inList familyIds } }
            FamiliesTable.deleteWhere { Op.build { FamiliesTable.id inList familyIds } }
        }
    }

    private fun createFamily(): UUID = transaction(database) {
        FamiliesTable.insert { it[sharedCalendarId] = "" }[FamiliesTable.id].also(familyIds::add)
    }

    private fun activity(id: UUID = familyA): FamilyActivity =
        AdminRepository(database).stats().families.single { it.id == id }

    private fun viewsAt(time: Instant) = FamilyActivityRepository(database, Clock.fixed(time, ZoneOffset.UTC))

    @Test
    fun `views are throttled per family for fifteen minutes without inventing past activity`() {
        assertNull(activity().lastActiveAt)
        assertTrue(viewsAt(now).recordCalendarView(familyA))
        assertEquals(false, viewsAt(now.plusSeconds(899)).recordCalendarView(familyA))
        assertEquals(now, activity().lastCalendarViewedAt)
        assertNull(activity().lastCalendarUpdatedAt)
        assertNull(activity(familyB).lastActiveAt)
        assertTrue(viewsAt(now.plusSeconds(1)).recordCalendarView(familyB))
        assertTrue(viewsAt(now.plusSeconds(900)).recordCalendarView(familyA))
        assertEquals(now.plusSeconds(900), activity().lastCalendarViewedAt)
    }

    @Test
    fun `concurrent views only update the family once`() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll(List(8) {
                Callable { viewsAt(now).recordCalendarView(familyA) }
            }).map { it.get() }
            assertEquals(1, results.count { it })
            assertEquals(now, activity().lastCalendarViewedAt)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `calendar mutations update activity but reads and missing or foreign assignments do not`() {
        val repo = FamilyScopedAssignmentRepository(familyA, database)
        repo.all()
        assertNull(activity().lastActiveAt)

        val id = repo.insert(LocalDate.of(2026, 11, 2), "PICKUP", parentA, "MANUAL", null)
        assertNotNull(activity().lastCalendarUpdatedAt)
        assertNull(activity().lastCalendarViewedAt)
        assertNull(activity(familyB).lastActiveAt)
        val foreignRepo = FamilyScopedAssignmentRepository(familyB, database)
        assertEquals(0, foreignRepo.update(id, parentA, "AUTO", null))
        assertEquals(0, foreignRepo.delete(id))
        assertNull(activity(familyB).lastActiveAt)

        fun resetUpdateTime() = transaction(database) {
            FamiliesTable.update({ FamiliesTable.id eq familyA }) {
                it[lastCalendarUpdatedAt] = now.minus(Duration.ofDays(30))
            }
        }
        resetUpdateTime()
        assertEquals(1, repo.update(id, parentA, "AUTO", null))
        assertTrue(activity().lastCalendarUpdatedAt!! > now.minus(Duration.ofDays(30)))
        resetUpdateTime()
        assertEquals(1, repo.delete(id))
        val afterDeletion = activity().lastCalendarUpdatedAt
        assertTrue(afterDeletion!! > now.minus(Duration.ofDays(30)))
        assertEquals(0, repo.delete(id))
        assertEquals(afterDeletion, activity().lastCalendarUpdatedAt)
    }

    @Test
    fun `failed transaction rolls back the assignment and its activity timestamp`() {
        assertFailsWith<IllegalStateException> {
            transaction(database) {
                FamilyScopedAssignmentRepository(familyA, database)
                    .insert(LocalDate.of(2026, 11, 3), "DROPOFF", parentA, "MANUAL", null)
                error("Rollback test")
            }
        }
        assertTrue(FamilyScopedAssignmentRepository(familyA, database).all().isEmpty())
        assertNull(activity().lastCalendarUpdatedAt)
    }

    @Test
    fun `successful confirmation answers count as changes but repeated answers do not`() {
        val repo = FamilyScopedAssignmentRepository(familyA, database)
        val id = repo.insert(
            LocalDate.of(2026, 11, 4), "PICKUP", parentA, "MANUAL", null,
            confirmationStatus = ConfirmationStatus.PENDING,
            confirmationTokenHash = "activity-test-token",
            confirmationTokenExpiresAt = Instant.now().plusSeconds(3600),
        )
        transaction(database) {
            FamiliesTable.update({ FamiliesTable.id eq familyA }) { it[lastCalendarUpdatedAt] = null }
        }
        val confirmations = AssignmentConfirmationRepository(database)
        assertEquals(ConfirmationAnswerResult.UPDATED, confirmations.answer("activity-test-token", ConfirmationStatus.ACCEPTED))
        val recorded = assertNotNull(activity().lastCalendarUpdatedAt)
        assertEquals(ConfirmationAnswerResult.ALREADY_RECORDED, confirmations.answer("activity-test-token", ConfirmationStatus.ACCEPTED))
        assertEquals(recorded, activity().lastCalendarUpdatedAt)
        assertEquals(ConfirmationAnswerResult.CONFLICT, confirmations.answer("activity-test-token", ConfirmationStatus.DECLINED))
        assertEquals(recorded, activity().lastCalendarUpdatedAt)
    }

    @Test
    fun `admin counts unique families by most recent activity and leaves untracked families empty`() {
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val admin = AdminRepository(database, clock)
        val baseline = admin.stats()
        fun setTimes(id: UUID, viewed: Instant?, updated: Instant?) = transaction(database) {
            FamiliesTable.update({ FamiliesTable.id eq id }) {
                it[lastCalendarViewedAt] = viewed
                it[lastCalendarUpdatedAt] = updated
            }
        }
        setTimes(familyA, now.minusSeconds(3600), now.minusSeconds(1800))
        setTimes(familyB, now.minus(Duration.ofDays(3)), now.minus(Duration.ofDays(20)))
        val older = createFamily()
        setTimes(older, null, now.minus(Duration.ofDays(14)))
        val inactive = createFamily()
        setTimes(inactive, now.minus(Duration.ofDays(31)), null)
        val untracked = createFamily()

        val stats = admin.stats()
        assertEquals(baseline.familyCount + 3, stats.familyCount)
        assertEquals(baseline.activeFamiliesLast24Hours + 1, stats.activeFamiliesLast24Hours)
        assertEquals(baseline.activeFamiliesLast7Days + 2, stats.activeFamiliesLast7Days)
        assertEquals(baseline.activeFamiliesLast30Days + 3, stats.activeFamiliesLast30Days)
        assertEquals(baseline.updatedFamiliesLast7Days + 1, stats.updatedFamiliesLast7Days)
        assertEquals(baseline.updatedFamiliesLast30Days + 3, stats.updatedFamiliesLast30Days)
        assertEquals(now.minusSeconds(1800), stats.families.single { it.id == familyA }.lastActiveAt)
        assertEquals(now.minus(Duration.ofDays(3)), stats.families.single { it.id == familyB }.lastActiveAt)
        assertNull(stats.families.single { it.id == untracked }.lastActiveAt)
        assertEquals(familyIds.size, stats.families.count { it.id in familyIds })
        assertEquals(stats.families.size, stats.families.map { it.id }.distinct().size)
    }
}
