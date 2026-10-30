package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import no.pilot.barnehage.auth.SESSION_AUTH_NAME
import no.pilot.barnehage.auth.userSession
import no.pilot.barnehage.db.AdminRepository
import no.pilot.barnehage.db.FamilyRepository
import java.util.UUID

@Serializable
data class FamilyActivityResponse(
    val id: String,
    val lastCalendarViewedAt: String?,
    val lastCalendarUpdatedAt: String?,
    val lastActiveAt: String?,
)

@Serializable
data class AdminStatsResponse(
    val familyCount: Long,
    val userCount: Long,
    val helperCount: Long,
    val activeFamiliesLast24Hours: Int,
    val activeFamiliesLast7Days: Int,
    val activeFamiliesLast30Days: Int,
    val updatedFamiliesLast7Days: Int,
    val updatedFamiliesLast30Days: Int,
    val families: List<FamilyActivityResponse>,
)

fun Route.adminRoutes(familyRepository: FamilyRepository, adminRepository: AdminRepository) {
    authenticate(SESSION_AUTH_NAME) {
        get("/api/admin/stats") {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            val session = call.userSession()!!
            val admin = familyRepository.findParent(UUID.fromString(session.parentId))
            if (admin?.isAdmin != true) {
                return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("krever adminrettigheter"))
            }
            val stats = adminRepository.stats()
            call.respond(
                AdminStatsResponse(
                    familyCount = stats.familyCount,
                    userCount = stats.userCount,
                    helperCount = stats.helperCount,
                    activeFamiliesLast24Hours = stats.activeFamiliesLast24Hours,
                    activeFamiliesLast7Days = stats.activeFamiliesLast7Days,
                    activeFamiliesLast30Days = stats.activeFamiliesLast30Days,
                    updatedFamiliesLast7Days = stats.updatedFamiliesLast7Days,
                    updatedFamiliesLast30Days = stats.updatedFamiliesLast30Days,
                    families = stats.families.map {
                        FamilyActivityResponse(
                            it.id.toString(),
                            it.lastCalendarViewedAt?.toString(),
                            it.lastCalendarUpdatedAt?.toString(),
                            it.lastActiveAt?.toString(),
                        )
                    },
                ),
            )
        }
    }
}
