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
import no.pilot.barnehage.db.FamilyRepository
import java.util.UUID

@Serializable
data class FamilyResponse(val id: String, val inviteCode: String?)

/**
 * Innstillinger for den innloggede brukerens egen familie — `familyId` hentes
 * kun fra sesjonen, aldri fra klienten, samme mønster som AssignmentRoutes.
 * Kalender-tilknytning er nå PER FORELDER, ikke familie-delt — se CalendarRoutes.
 */
fun Route.familyRoutes(familyRepository: FamilyRepository) {
    authenticate(SESSION_AUTH_NAME) {
        get("/api/family") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val family = familyRepository.findFamily(familyId)
                ?: return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("familie ikke funnet"))
            call.respond(FamilyResponse(id = family.id.toString(), inviteCode = family.inviteCode))
        }
    }
}
