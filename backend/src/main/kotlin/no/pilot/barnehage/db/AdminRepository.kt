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

/**
 * Aggregert statistikk og adminstyrte invitasjonskoder — kun tilgjengelig via
 * AdminRoutes (bak `requireAdmin`, se der). Skilt ut fra FamilyRepository
 * fordi dette IKKE er familie-scopet data (motsatt av alt annet i den
 * repositoryen), men på tvers av alle familier.
 */
class AdminRepository(private val database: Database) {

    /** Antall familier totalt (uavhengig av om de er fulle eller ikke). */
    fun countFamilies(): Long = transaction(database) { FamiliesTable.selectAll().count() }

    /** Antall INNLOGGEDE brukere (foreldre) totalt, på tvers av alle familier —
     * teller aldri hjelpere, samme avgrensning som FamilyRepository.parentCount. */
    fun countUsers(): Long = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.isHelper eq false }.count()
    }

    /** Antall hjelpere totalt — egen tall, ikke inkludert i `countUsers()`. */
    fun countHelpers(): Long = transaction(database) {
        ParentsTable.selectAll().where { ParentsTable.isHelper eq true }.count()
    }

    fun stats(): AdminStats = AdminStats(countFamilies(), countUsers(), countHelpers())

    fun listInviteCodes(): List<InviteCodeRecord> = transaction(database) {
        InviteCodesTable.selectAll()
            .orderBy(InviteCodesTable.createdAt, org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { it.toInviteCodeRecord() }
    }

    /** Genererer og lagrer en ny, ubrukt engangskode for å OPPRETTE en ny
     * familie (se JoinRoutes.handleJoin) — `createdBy` er admin-forelderen som
     * ba om koden (kun til sporing, håndhever ingenting selv). */
    fun createInviteCode(createdBy: UUID): InviteCodeRecord = transaction(database) {
        val code = generateAdminInviteCode()
        val id = InviteCodesTable.insert {
            it[InviteCodesTable.code] = code
            it[InviteCodesTable.createdBy] = createdBy
        }[InviteCodesTable.id]
        InviteCodesTable.selectAll().where { InviteCodesTable.id eq id }.first().toInviteCodeRecord()
    }

    /** Finner en ENNÅ UBRUKT admin-generert invitasjonskode — brukt av
     * JoinRoutes.handleJoin til å avgjøre om en kode skal opprette en ny
     * familie. Brukte koder (usedAt != null) regnes ikke som gyldige (engangsbruk). */
    fun findUnusedInviteCode(code: String): InviteCodeRecord? = transaction(database) {
        InviteCodesTable.selectAll()
            .where { (InviteCodesTable.code eq code) and (InviteCodesTable.usedAt.isNull()) }
            .firstOrNull()?.toInviteCodeRecord()
    }

    /** Markerer en admin-generert kode som brukt (engangsbruk, samme mønster
     * som families.invite_code) — kalles rett etter at den nye familien er
     * opprettet i JoinRoutes.handleJoin. */
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
