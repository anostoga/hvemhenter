package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import no.pilot.barnehage.auth.userSession
import no.pilot.barnehage.auth.SESSION_AUTH_NAME
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.db.FamilyScopedAssignmentRepository
import no.pilot.barnehage.db.TokenRepository
import no.pilot.barnehage.domain.Assignment
import no.pilot.barnehage.domain.AssignmentService
import no.pilot.barnehage.domain.AssignmentSource
import no.pilot.barnehage.domain.AssignmentType
import no.pilot.barnehage.domain.BusyPeriod
import no.pilot.barnehage.domain.Parent
import no.pilot.barnehage.google.AccessTokenProvider
import no.pilot.barnehage.google.CalendarEventDateTime
import no.pilot.barnehage.google.CalendarEventItem
import no.pilot.barnehage.google.CalendarEventRequest
import no.pilot.barnehage.google.CalendarEventTime
import no.pilot.barnehage.google.CalendarService
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

private val logger = LoggerFactory.getLogger("no.pilot.barnehage.routes.AssignmentRoutes")

@Serializable
data class AssignRequest(
    val date: String,
    val type: AssignmentType,
    val parentId: String,
    val source: AssignmentSource = AssignmentSource.MANUAL,
    val startTime: String? = null,
    val endTime: String? = null,
)

/** Standard tidsvindu for levering/henting, brukt både til konfliktsjekk og som
 * forvalgte tidspunkter når kalenderhendelser opprettes (kan overstyres per kall). */
internal fun defaultWindow(type: AssignmentType): Pair<String, String> = when (type) {
    AssignmentType.DROPOFF -> "07:30" to "09:15"
    AssignmentType.PICKUP -> "15:00" to "17:00"
}

/**
 * Alle ruter her krever en gyldig sesjon (`authenticate(SESSION_AUTH_NAME)`) og er
 * scopet til den innloggede brukerens familie — `familyId` hentes ALDRI fra
 * request-body/query, kun fra den signerte sesjonscookien (se auth/SessionAuth.kt).
 * Dette er det som gjør at én families data er strukturelt utilgjengelig for en
 * annen families innloggede bruker.
 */
fun Route.assignmentRoutes(
    familyRepository: FamilyRepository,
    assignmentService: AssignmentService,
    calendarService: CalendarService,
    accessTokenProvider: AccessTokenProvider,
    tokenRepository: TokenRepository,
    database: Database,
) {
    authenticate(SESSION_AUTH_NAME) {
        get("/api/parents") {
            val session = call.userSession()!!
            call.respond(effectiveParents(familyRepository, tokenRepository, UUID.fromString(session.familyId)))
        }

        get("/api/assignments") {
            val session = call.userSession()!!
            val repo = FamilyScopedAssignmentRepository(UUID.fromString(session.familyId), database)
            call.respond(repo.all().map { it.toApiAssignment() })
        }

        get("/api/suggest") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val date = call.parameters["date"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("date mangler (yyyy-MM-dd)"))
            val type = call.parameters["type"]?.let { AssignmentType.valueOf(it.uppercase()) }
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("type mangler (DROPOFF|PICKUP)"))

            val parents = effectiveParents(familyRepository, tokenRepository, familyId)
            val repo = FamilyScopedAssignmentRepository(familyId, database)
            val history = repo.all().map { it.toApiAssignment() }
            val busyByParent = fetchBusyPeriods(parents, date, calendarService, accessTokenProvider)

            val (startTime, endTime) = defaultWindow(type)
            val (windowStart, windowEnd) = timeWindow(date, startTime, endTime)
            val suggestion = assignmentService.suggest(
                parents = parents,
                history = history,
                date = date,
                type = type,
                busyByParent = busyByParent,
                windowStart = windowStart,
                windowEnd = windowEnd,
            )
            call.respond(suggestion)
        }

        post("/api/assign") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val request = call.receive<AssignRequest>()

            val parents = familyRepository.findParents(familyId)
            val parent = parents.find { it.id.toString() == request.parentId }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("ukjent parentId"))

            val repo = FamilyScopedAssignmentRepository(familyId, database)
            val requestDate = LocalDate.parse(request.date)
            // Finnes det allerede en tildeling for denne datoen/typen (unik-constraint i
            // databasen på family_id/date/type)? I så fall ERSTATTER vi den (oppdaterer
            // forelder/kilde, sletter ev. gammel kalenderhendelse og oppretter en ny),
            // i stedet for å prøve å sette inn en ny rad (som ville feilet på constraint-en).
            val existing = repo.findByDateAndType(requestDate, request.type.name)

            // VIKTIG: den gamle hendelsen ligger i DEN OPPRINNELIG TILDELTE forelderens
            // kalender, ikke nødvendigvis i den nye forelderens — hvis tildelingen flyttes
            // fra én forelder til en annen, må slettingen skje mot riktig (gamle) kalender.
            if (existing?.googleEventId != null) {
                val oldParent = parents.find { it.id == existing.parentId }
                val oldCalendarId = oldParent?.calendarId
                if (!oldCalendarId.isNullOrBlank()) {
                    val accessTokenForDelete = accessTokenProvider.getValidAccessToken(oldParent.id)
                    if (accessTokenForDelete != null) {
                        calendarService.deleteEvent(accessTokenForDelete, oldCalendarId, existing.googleEventId)
                    }
                }
            }

            val newCalendarId = parent.calendarId
            val accessToken = if (newCalendarId.isNullOrBlank()) null else accessTokenProvider.getValidAccessToken(parent.id)
            var googleEventId: String? = null
            if (accessToken != null && !newCalendarId.isNullOrBlank()) {
                val (defaultStart, defaultEnd) = defaultWindow(request.type)
                val (startIso, endIso) = isoWindow(request.date, request.startTime ?: defaultStart, request.endTime ?: defaultEnd)
                googleEventId = calendarService.insertEvent(
                    accessToken = accessToken,
                    calendarId = newCalendarId,
                    event = CalendarEventRequest(
                        summary = "${typeLabel(request.type)}: ${parent.name}",
                        start = CalendarEventTime(startIso, ZoneId.systemDefault().id),
                        end = CalendarEventTime(endIso, ZoneId.systemDefault().id),
                    ),
                )
            }

            val id = if (existing != null) {
                repo.update(existing.id, parent.id, request.source.name, googleEventId)
                existing.id
            } else {
                repo.insert(
                    date = requestDate,
                    type = request.type.name,
                    parentId = parent.id,
                    source = request.source.name,
                    googleEventId = googleEventId,
                )
            }
            call.respond(
                if (existing != null) HttpStatusCode.OK else HttpStatusCode.Created,
                Assignment(id = id.toString(), date = request.date, type = request.type, parentId = request.parentId, source = request.source, googleEventId = googleEventId),
            )
        }

        delete("/api/assignments/{id}") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val assignmentId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig id"))

            val repo = FamilyScopedAssignmentRepository(familyId, database)
            // findById() er scopet til familyId — en annen families tildeling gir null her,
            // ikke en treff, selv om assignmentId skulle vært gjettet riktig.
            val assignment = repo.findById(assignmentId)
                ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("tildeling ikke funnet"))

            if (assignment.googleEventId != null) {
                val owningParent = familyRepository.findParents(familyId).find { it.id == assignment.parentId }
                val calendarId = owningParent?.calendarId
                if (!calendarId.isNullOrBlank()) {
                    val accessToken = accessTokenProvider.getValidAccessToken(assignment.parentId)
                    if (accessToken != null) {
                        calendarService.deleteEvent(accessToken, calendarId, assignment.googleEventId)
                    }
                }
            }

            repo.delete(assignmentId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun typeLabel(type: AssignmentType) = if (type == AssignmentType.DROPOFF) "Levering" else "Henting"

private fun no.pilot.barnehage.db.FamilyAssignment.toApiAssignment() = Assignment(
    id = id.toString(),
    date = date.toString(),
    type = AssignmentType.valueOf(type),
    parentId = parentId.toString(),
    source = AssignmentSource.valueOf(source),
    googleEventId = googleEventId,
)

/**
 * Slår sammen foreldre-rader (familyId/id/navn fra Postgres) med reell
 * OAuth-tilkoblingsstatus fra tokens-tabellen. `findParents` alene sier ingenting
 * om hvorvidt Google-kalender er koblet til.
 */
private fun effectiveParents(familyRepository: FamilyRepository, tokenRepository: TokenRepository, familyId: UUID): List<Parent> =
    familyRepository.findParents(familyId).map { parent ->
        Parent(
            id = parent.id.toString(),
            name = parent.name,
            avatar = parent.avatar,
            connected = tokenRepository.find(parent.id) != null,
            calendarId = parent.calendarId,
        )
    }

/**
 * Henter opptatte perioder PER FORELDER fra forelderens EGEN valgte kalender
 * (se CalendarRoutes/`/api/calendars/mine`) — ikke lenger fra én kalender delt
 * av hele familien. En forelder uten valgt kalender, eller uten gyldig
 * access-token, gir rett og slett ingen opptatte perioder for seg selv (i stedet
 * for en feil) — konfliktsjekken degraderer da bare til "vi vet ikke", ikke krasj.
 */
private suspend fun fetchBusyPeriods(
    parents: List<Parent>,
    date: String,
    calendarService: CalendarService,
    accessTokenProvider: AccessTokenProvider,
): Map<String, List<BusyPeriod>> {
    val (windowStartIso, windowEndIso) = isoWindow(date, "07:00", "17:30")

    return parents.associate { parent ->
        val calendarId = parent.calendarId
        val accessToken = if (calendarId.isNullOrBlank()) null else accessTokenProvider.getValidAccessToken(UUID.fromString(parent.id))
        val busy = if (accessToken == null || calendarId.isNullOrBlank()) {
            emptyList()
        } else {
            calendarService.listEvents(accessToken, calendarId, windowStartIso, windowEndIso).mapNotNull { event ->
                val start = event.start?.toEpochMillis() ?: return@mapNotNull null
                val end = event.end?.toEpochMillis() ?: return@mapNotNull null
                BusyPeriod(start, end)
            }
        }
        parent.id to busy
    }
}

/**
 * Avgjør om en hendelse tilhører en gitt forelder ved å se om forelderens FORNAVN
 * inngår i hendelsestittelen (case-insensitive). Brukt tidligere til å fordele
 * hendelser fra én delt familiekalender mellom foreldre (se `fetchBusyPeriods`
 * over, som nå bruker per-forelder-kalendere i stedet og ikke lenger trenger
 * denne). Beholdt urørt (og fortsatt dekket av
 * AssignmentRoutesCharacterizationTest) for å unngå å røre en fungerende,
 * testet funksjon uten grunn.
 */
internal fun matchParent(event: CalendarEventItem, parentName: String): BusyPeriod? {
    val summary = event.summary ?: return null
    val firstName = parentName.substringBefore(" ")
    if (!summary.contains(firstName, ignoreCase = true)) return null

    val start = event.start?.toEpochMillis() ?: return null
    val end = event.end?.toEpochMillis() ?: return null
    return BusyPeriod(start, end)
}

internal fun CalendarEventDateTime.toEpochMillis(): Long? {
    dateTime?.let { return java.time.Instant.parse(it).toEpochMilli() }
    date?.let { return LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    return null
}

private fun timeWindow(date: String, startTime: String, endTime: String): Pair<Long, Long> {
    val (startIso, endIso) = isoWindow(date, startTime, endTime)
    return java.time.Instant.parse(startIso).toEpochMilli() to java.time.Instant.parse(endIso).toEpochMilli()
}

private fun isoWindow(date: String, startTime: String, endTime: String): Pair<String, String> {
    val zone = ZoneId.systemDefault()
    val day = LocalDate.parse(date)
    val start = day.atTime(LocalTime.parse(startTime)).atZone(zone).toInstant().toString()
    val end = day.atTime(LocalTime.parse(endTime)).atZone(zone).toInstant().toString()
    return start to end
}
