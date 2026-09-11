package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

data class AdminStats(val familyCount: Long, val userCount: Long, val helperCount: Long)

data class InviteCodeRecord(
    val id: UUID,
    val code: String,
    val createdBy: UUID,
    val createdAt: Instant,
    val usedAt: Instant?,
    val usedByFamilyId: UUID?,
)

class AdminRepository(private val database: Database) {

    fun countFamilies(): Long = transaction(database) { FamiliesTable.selectAll().count() }

    fun countUsers(): Long = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.isHelper eq false }.count()
    }

    fun countHelpers(): Long = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.isHelper eq true }.count()
    }

    fun stats(): AdminStats = AdminStats(countFamilies(), countUsers(), countHelpers())

    fun listInviteCodes(): List<InviteCodeRecord> = transaction(database) {
        InviteCodesTable.selectAll()
            .orderBy(InviteCodesTable.createdAt, org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { it.toInviteCodeRecord() }
    }

    fun createInviteCode(createdBy: UUID): InviteCodeRecord = transaction(database) {
        val code = generateAdminInviteCode()
        val id = InviteCodesTable.insert {
            it[InviteCodesTable.code] = code
            it[InviteCodesTable.createdBy] = createdBy
        }[InviteCodesTable.id]
        InviteCodesTable.selectAll().where { InviteCodesTable.id eq id }.first().toInviteCodeRecord()
    }

    fun findUnusedInviteCode(code: String): InviteCodeRecord? = transaction(database) {
        InviteCodesTable.selectAll()
            .where { (InviteCodesTable.code eq code) and (InviteCodesTable.usedAt.isNull()) }
            .firstOrNull()?.toInviteCodeRecord()
    }

    /**
     * Finn en admin-generert kode uavhengig av bruksstatus. Brukes for å avgjøre om en
     * allerede brukt kode fortsatt kan slippe inn en andre forelder i familien den opprettet.
     */
    fun findInviteCode(code: String): InviteCodeRecord? = transaction(database) {
        InviteCodesTable.selectAll()
            .where { InviteCodesTable.code eq code }
            .firstOrNull()?.toInviteCodeRecord()
    }

    fun markInviteCodeUsed(id: UUID, familyId: UUID) = transaction(database) {
        InviteCodesTable.update({ InviteCodesTable.id eq id }) {
            it[InviteCodesTable.usedAt] = java.time.Instant.now()
            it[InviteCodesTable.usedByFamilyId] = familyId
        }
    }

    private fun org.jetbrains.exposed.sql.ResultRow.toInviteCodeRecord() = InviteCodeRecord(
        id = this[InviteCodesTable.id],
        code = this[InviteCodesTable.code],
        createdBy = this[InviteCodesTable.createdBy],
        createdAt = this[InviteCodesTable.createdAt],
        usedAt = this[InviteCodesTable.usedAt],
        usedByFamilyId = this[InviteCodesTable.usedByFamilyId],
    )
}

private fun generateAdminInviteCode(): String {
    val bytes = ByteArray(9)
    java.security.SecureRandom().nextBytes(bytes)
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
