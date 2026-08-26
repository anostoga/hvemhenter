package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import no.pilot.barnehage.auth.SESSION_AUTH_NAME
import no.pilot.barnehage.auth.userSession
import no.pilot.barnehage.db.FamilyRepository
import java.util.UUID

@Serializable
data class FamilyResponse(val id: String, val sharedCalendarId: String)

@Serializable
data class UpdateSharedCalendarRequest(val sharedCalendarId: String)

/**
 * Innstillinger for den innloggede brukerens egen familie — `familyId` hentes
 * kun fra sesjonen, aldri fra klienten, samme mønster som AssignmentRoutes.
 */
fun Route.familyRoutes(familyRepository: FamilyRepository) {
    authenticate(SESSION_AUTH_NAME) {
        get("/api/family") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val family = familyRepository.findFamily(familyId)
                ?: return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("familie ikke funnet"))
            call.respond(FamilyResponse(id = family.id.toString(), sharedCalendarId = family.sharedCalendarId))
        }

        put("/api/family/shared-calendar") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val request = call.receive<UpdateSharedCalendarRequest>()
            if (request.sharedCalendarId.isBlank()) {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("sharedCalendarId mangler"))
            }
            familyRepository.updateSharedCalendarId(familyId, request.sharedCalendarId)
            call.respond(HttpStatusCode.OK, FamilyResponse(id = familyId.toString(), sharedCalendarId = request.sharedCalendarId))
        }
    }
}
