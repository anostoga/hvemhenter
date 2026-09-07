package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Familie-isolasjonstest — den viktigste sikkerhetstesten i hele multi-familie-
 * refaktoreringen: bevis at familie A ALDRI kan lese familie B sine data.
 *
 * Kjører mot en ekte lokal Postgres (docker-compose i backend/), samme database
 * som brukes til `flywayMigrate`. Testene rydder opp etter seg (sletter egne rader).
 */
class FamilyScopedAssignmentRepositoryTest {
    private val database = Database.connect(
        url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/barnehage",
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
        // Regresjonstest: family_id/date/type har en unik-constraint i databasen,
        // så et forsøk på å SETTE INN en ny rad for samme dato/type ville feilet.
        // findByDateAndType() + update() må brukes i stedet for insert() ved reassignment.
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
}
