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
data class MyCalendarResponse(val calendarId: String?, val availabilityCalendarId: String?)

@Serializable
data class UpdateMyCalendarRequest(val calendarId: String, val availabilityCalendarId: String? = null)

@Serializable
data class AvailableCalendarResponse(val id: String, val summary: String, val primary: Boolean)

/**
 * Hver forelder velger sin EGEN Google-kalender — tildelinger der forelderen er
 * satt opp skrives som hendelser dit (se AssignmentRoutes), i stedet for til én
 * kalender delt av hele familien (den tidligere `families.shared_calendar_id`-
 * modellen). `parentId` hentes kun fra sesjonen, aldri fra klienten — én
 * forelder kan derfor aldri lese eller endre en annen forelders kalendervalg.
 */
fun Route.calendarRoutes(
    familyRepository: FamilyRepository,
    accessTokenProvider: AccessTokenProvider,
    calendarService: CalendarService,
) {
    authenticate(SESSION_AUTH_NAME) {
        get("/api/calendars/mine") {
            val session = call.userSession()!!
            val parent = familyRepository.findParent(UUID.fromString(session.parentId))
                ?: return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("forelder ikke funnet"))
            call.respond(MyCalendarResponse(calendarId = parent.calendarId, availabilityCalendarId = parent.availabilityCalendarId))
        }

        put("/api/calendars/mine") {
            val session = call.userSession()!!
            val parentId = UUID.fromString(session.parentId)
            val request = call.receive<UpdateMyCalendarRequest>()
            if (request.calendarId.isBlank()) {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("calendarId mangler"))
            }
            // Tom streng betyr "ikke valgt" for tilgjengelighetskalenderen (samme som
            // avkrysningsboksen "bruk samme kalender" i UI-et) — lagres som null,
            // ikke som en tom streng, slik at effectiveAvailabilityCalendarId() faller
            // tilbake til calendarId.
            val availabilityCalendarId = request.availabilityCalendarId?.takeIf { it.isNotBlank() }
            familyRepository.updateParentCalendars(parentId, request.calendarId, availabilityCalendarId)
            call.respond(HttpStatusCode.OK, MyCalendarResponse(calendarId = request.calendarId, availabilityCalendarId = availabilityCalendarId))
        }

        // Lar brukeren velge kalender fra en nedtrekksliste i stedet for å skrive inn
        // en rå kalender-ID. Krever at brukeren allerede har koblet til Google (samme
        // "connected"-sjekk som /api/parents) — uten det finnes ingen access token å
        // liste kalendere med.
        get("/api/calendars/available") {
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
