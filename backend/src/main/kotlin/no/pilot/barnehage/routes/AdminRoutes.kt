package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
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
data class AdminStatsResponse(val familyCount: Long, val userCount: Long, val helperCount: Long)

fun Route.adminRoutes(familyRepository: FamilyRepository, adminRepository: AdminRepository) {
    authenticate(SESSION_AUTH_NAME) {
        get("/api/admin/stats") {
            val session = call.userSession()!!
            val admin = familyRepository.findParent(UUID.fromString(session.parentId))
            if (admin?.isAdmin != true) {
                return@get call.respond(HttpStatusCode.Forbidden, ErrorResponse("krever adminrettigheter"))
            }
            val stats = adminRepository.stats()
            call.respond(AdminStatsResponse(stats.familyCount, stats.userCount, stats.helperCount))
        }
    }
}
