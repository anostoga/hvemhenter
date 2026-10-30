package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.pilot.barnehage.auth.SESSION_AUTH_NAME
import no.pilot.barnehage.auth.userSession
import no.pilot.barnehage.db.FamilyActivityRepository
import no.pilot.barnehage.db.FamilyRepository
import java.util.UUID

fun Route.familyActivityRoutes(familyRepository: FamilyRepository, activityRepository: FamilyActivityRepository) {
    authenticate(SESSION_AUTH_NAME) {
        post("/api/family/calendar-views") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val parent = familyRepository.findParent(UUID.fromString(session.parentId))
            if (parent?.familyId != familyId) {
                return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("krever familiemedlemskap"))
            }
            activityRepository.recordCalendarView(familyId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
