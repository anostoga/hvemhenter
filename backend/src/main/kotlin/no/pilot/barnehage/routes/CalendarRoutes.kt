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
data class MyCalendarResponse(val calendarId: String?, val availabilityCalendarId: String?, val availabilityDisabled: Boolean = false)

@Serializable
data class UpdateMyCalendarRequest(val calendarId: String? = null, val availabilityCalendarId: String? = null, val availabilityDisabled: Boolean = false)

@Serializable
data class AvailableCalendarResponse(val id: String, val summary: String, val primary: Boolean)

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
            call.respond(
                MyCalendarResponse(
                    calendarId = parent.calendarId,
                    availabilityCalendarId = parent.availabilityCalendarId,
                    availabilityDisabled = parent.availabilityDisabled,
                ),
            )
        }

        put("/api/calendars/mine") {
            val session = call.userSession()!!
            val parentId = UUID.fromString(session.parentId)
            val request = call.receive<UpdateMyCalendarRequest>()

            val calendarId = request.calendarId?.takeIf { it.isNotBlank() }

            val availabilityCalendarId = request.availabilityCalendarId?.takeIf { it.isNotBlank() }
            familyRepository.updateParentCalendars(parentId, calendarId, availabilityCalendarId, request.availabilityDisabled)
            call.respond(
                HttpStatusCode.OK,
                MyCalendarResponse(
                    calendarId = calendarId,
                    availabilityCalendarId = if (request.availabilityDisabled) null else availabilityCalendarId,
                    availabilityDisabled = request.availabilityDisabled,
                ),
            )
        }

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
