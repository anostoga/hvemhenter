package no.pilot.barnehage.db

import no.pilot.barnehage.Env
import no.pilot.barnehage.routes.handleJoin
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
import kotlin.test.assertTrue

/**
 * Dekker kjernen i adminfunksjonaliteten mot en ekte lokal Postgres:
 * statistikk-telling, engangsbruk av admin-genererte invitasjonskoder, og at
 * en slik kode OPPRETTER en ny familie (i motsetning til families.invite_code,
 * som kun lar forelder 2 bli med i en eksisterende familie) — se JoinRoutesTest
 * for de tilsvarende testene av FAMILY_CREATION_CODE/families.invite_code.
 */
class AdminRepositoryTest {
    private val database = Database.connect(
        url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/barnehage",
        user = System.getenv("DATABASE_USER") ?: "postgres",
        password = System.getenv("DATABASE_PASSWORD") ?: "localdev",
        driver = "org.postgresql.Driver",
    )
    private val familyRepository = FamilyRepository(database)
    private val adminRepository = AdminRepository(database)
    private val createdParentGoogleSubs = mutableListOf<String>()

    @AfterTest
    fun tearDown() {
        transaction(database) {
            val parentIds = ParentsTable.selectAll().where { ParentsTable.googleSub inList createdParentGoogleSubs }
                .map { it[ParentsTable.id] }
            val familyIds = if (parentIds.isNotEmpty()) {
                ParentsTable.selectAll().where { ParentsTable.id inList parentIds }.map { it[ParentsTable.familyId] }
            } else emptyList()
            InviteCodesTable.deleteWhere { Op.build { InviteCodesTable.createdBy inList parentIds } }
            ParentsTable.deleteWhere { Op.build { ParentsTable.googleSub inList createdParentGoogleSubs } }
            if (familyIds.isNotEmpty()) {
                FamiliesTable.deleteWhere { Op.build { FamiliesTable.id inList familyIds } }
            }
        }
    }

    private fun sub() = "admin-test-sub-${UUID.randomUUID()}".also { createdParentGoogleSubs.add(it) }

    @Test
    fun `admin-generert kode oppretter en ny familie og markeres brukt`() {
        Env.overrideForTests("FAMILY_CREATION_CODE", "riktig-kode")
        val adminSub = sub()
        val adminFamilyId = handleJoin("riktig-kode", adminSub, "admin@example.com", "Admin", familyRepository)!!
        val admin = familyRepository.findParentByGoogleSub(adminSub)!!

        val created = adminRepository.createInviteCode(admin.id)
        assertNull(created.usedAt)

        val newParentSub = sub()
        val newFamilyId = handleJoin(created.code, newParentSub, "b@example.com", "B", familyRepository, adminRepository)

        assertNotNull(newFamilyId)
        assertTrue(newFamilyId != adminFamilyId, "admin-kode skal opprette en NY familie, ikke bli med i admins egen")

        val used = adminRepository.listInviteCodes().first { it.code == created.code }
        assertNotNull(used.usedAt)
        assertEquals(UUID.fromString(newFamilyId), used.usedByFamilyId)
    }

    @Test
    fun `admin-generert kode kan kun brukes en gang`() {
        Env.overrideForTests("FAMILY_CREATION_CODE", "riktig-kode")
        val adminSub = sub()
        handleJoin("riktig-kode", adminSub, "admin@example.com", "Admin", familyRepository)
        val admin = familyRepository.findParentByGoogleSub(adminSub)!!
        val created = adminRepository.createInviteCode(admin.id)

        val firstUseSub = sub()
        handleJoin(created.code, firstUseSub, "b@example.com", "B", familyRepository, adminRepository)

        val secondUseSub = sub()
        val secondAttempt = handleJoin(created.code, secondUseSub, "c@example.com", "C", familyRepository, adminRepository)

        assertNull(secondAttempt, "admin-kode skal være invalidert etter første bruk")
        assertNull(familyRepository.findParentByGoogleSub(secondUseSub))
    }

    @Test
    fun `stats teller familier og brukere uten a telle hjelpere`() {
        Env.overrideForTests("FAMILY_CREATION_CODE", "riktig-kode")
        val familiesBefore = adminRepository.countFamilies()
        val usersBefore = adminRepository.countUsers()

        val parentSub = sub()
        val familyId = handleJoin("riktig-kode", parentSub, "a@example.com", "A", familyRepository)!!
        familyRepository.addHelper(UUID.fromString(familyId), "Bestemor", null)

        assertEquals(familiesBefore + 1, adminRepository.countFamilies())
        assertEquals(usersBefore + 1, adminRepository.countUsers())
    }
}
