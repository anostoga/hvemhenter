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
import no.pilot.barnehage.google.AccessTokenProvider
import no.pilot.barnehage.google.CalendarService
import java.util.UUID

@Serializable
data class FamilyResponse(val id: String, val sharedCalendarId: String, val inviteCode: String?)

@Serializable
data class UpdateSharedCalendarRequest(val sharedCalendarId: String)

@Serializable
data class AvailableCalendarResponse(val id: String, val summary: String, val primary: Boolean)

/**
 * Innstillinger for den innloggede brukerens egen familie — `familyId` hentes
 * kun fra sesjonen, aldri fra klienten, samme mønster som AssignmentRoutes.
 */
fun Route.familyRoutes(
    familyRepository: FamilyRepository,
    accessTokenProvider: AccessTokenProvider,
    calendarService: CalendarService,
) {
    authenticate(SESSION_AUTH_NAME) {
        get("/api/family") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val family = familyRepository.findFamily(familyId)
                ?: return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("familie ikke funnet"))
            call.respond(FamilyResponse(id = family.id.toString(), sharedCalendarId = family.sharedCalendarId, inviteCode = family.inviteCode))
        }

        put("/api/family/shared-calendar") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val request = call.receive<UpdateSharedCalendarRequest>()
            if (request.sharedCalendarId.isBlank()) {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("sharedCalendarId mangler"))
            }
            familyRepository.updateSharedCalendarId(familyId, request.sharedCalendarId)
            val family = familyRepository.findFamily(familyId)
            call.respond(
                HttpStatusCode.OK,
                FamilyResponse(id = familyId.toString(), sharedCalendarId = request.sharedCalendarId, inviteCode = family?.inviteCode),
            )
        }

        // Lar brukeren velge delt kalender fra en nedtrekksliste i stedet for å skrive
        // inn en rå kalender-ID. Krever at brukeren allerede har koblet til Google
        // (samme "connected"-sjekk som /api/parents) — uten det finnes ingen access
        // token å liste kalendere med.
        get("/api/family/available-calendars") {
            val session = call.userSession()!!
            val parentId = UUID.fromString(session.parentId)
            val accessToken = accessTokenProvider.getValidAccessToken(parentId)
                ?: return@get call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("koble til Google-kalenderen din først"),
                )
            val calendars = calendarService.listCalendars(accessToken)
            call.respond(calendars.map { AvailableCalendarResponse(id = it.id, summary = it.summary, primary = it.primary) })
        }
    }
}
