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

            val family = familyRepository.findFamily(familyId)
                ?: return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("familie ikke funnet"))
            val parents = effectiveParents(familyRepository, tokenRepository, familyId)
            val repo = FamilyScopedAssignmentRepository(familyId, database)
            val history = repo.all().map { it.toApiAssignment() }
            val busyByParent = fetchBusyPeriods(parents, family.sharedCalendarId, date, calendarService, accessTokenProvider)

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
            val family = familyRepository.findFamily(familyId)
                ?: return@post call.respond(HttpStatusCode.InternalServerError, ErrorResponse("familie ikke funnet"))

            val repo = FamilyScopedAssignmentRepository(familyId, database)
            val requestDate = LocalDate.parse(request.date)
            // Finnes det allerede en tildeling for denne datoen/typen (unik-constraint i
            // databasen på family_id/date/type)? I så fall ERSTATTER vi den (oppdaterer
            // forelder/kilde, sletter ev. gammel kalenderhendelse og oppretter en ny),
            // i stedet for å prøve å sette inn en ny rad (som ville feilet på constraint-en).
            val existing = repo.findByDateAndType(requestDate, request.type.name)

            if (existing?.googleEventId != null && family.sharedCalendarId.isNotBlank()) {
                val accessTokenForDelete = accessTokenProvider.getValidAccessToken(parent.id)
                    ?: parents.firstNotNullOfOrNull { accessTokenProvider.getValidAccessToken(it.id) }
                if (accessTokenForDelete != null) {
                    calendarService.deleteEvent(accessTokenForDelete, family.sharedCalendarId, existing.googleEventId)
                }
            }

            val accessToken = accessTokenProvider.getValidAccessToken(parent.id)
            var googleEventId: String? = null
            if (accessToken != null && family.sharedCalendarId.isNotBlank()) {
                val (defaultStart, defaultEnd) = defaultWindow(request.type)
                val (startIso, endIso) = isoWindow(request.date, request.startTime ?: defaultStart, request.endTime ?: defaultEnd)
                googleEventId = calendarService.insertEvent(
                    accessToken = accessToken,
                    calendarId = family.sharedCalendarId,
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
                val family = familyRepository.findFamily(familyId)
                if (family != null && family.sharedCalendarId.isNotBlank()) {
                    val parents = familyRepository.findParents(familyId)
                    val accessToken = parents.firstNotNullOfOrNull { accessTokenProvider.getValidAccessToken(it.id) }
                    if (accessToken != null) {
                        calendarService.deleteEvent(accessToken, family.sharedCalendarId, assignment.googleEventId)
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
        Parent(id = parent.id.toString(), name = parent.name, connected = tokenRepository.find(parent.id) != null)
    }

private suspend fun fetchBusyPeriods(
    parents: List<Parent>,
    sharedCalendarId: String,
    date: String,
    calendarService: CalendarService,
    accessTokenProvider: AccessTokenProvider,
): Map<String, List<BusyPeriod>> {
    if (sharedCalendarId.isBlank()) return emptyMap()
    val (windowStartIso, windowEndIso) = isoWindow(date, "07:00", "17:30")

    // Kalenderen er delt mellom foreldrene, så vi henter hendelsene én gang (med
    // hvilken som helst tilkoblet forelders token — begge har lesetilgang til
    // samme kalender) og fordeler dem etterpå basert på tittel.
    val accessToken = parents.firstNotNullOfOrNull { accessTokenProvider.getValidAccessToken(UUID.fromString(it.id)) } ?: return emptyMap()
    val events = calendarService.listEvents(accessToken, sharedCalendarId, windowStartIso, windowEndIso)

    logger.info(
        "Hentet {} hendelser fra delt kalender for {}: {}",
        events.size,
        date,
        events.joinToString { "\"${it.summary ?: "(uten tittel)"}\" ${it.start?.dateTime ?: it.start?.date}–${it.end?.dateTime ?: it.end?.date}" },
    )

    return parents.associate { parent ->
        parent.id to events.mapNotNull { event -> matchParent(event, parent.name) }
    }
}

/**
 * Avgjør om en hendelse tilhører en gitt forelder ved å se om forelderens FORNAVN
 * inngår i hendelsestittelen (case-insensitive). Vi matcher kun på fornavn (ikke
 * hele "Fornavn Etternavn") fordi folk typisk skriver kalendertitler som
 * "Halvor i møte" — et krav om at hele navnet matcher ville gjort at ekte
 * konflikter aldri ble oppdaget. Finnes ikke fornavnet i tittelen, regnes
 * hendelsen som ueid — den brukes ikke i forslagslogikken for noen.
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
