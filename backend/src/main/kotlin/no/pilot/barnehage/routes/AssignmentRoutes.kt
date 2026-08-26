package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import no.pilot.barnehage.AppConfig
import no.pilot.barnehage.db.AssignmentRepository
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
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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
private fun defaultWindow(type: AssignmentType): Pair<String, String> = when (type) {
    AssignmentType.DROPOFF -> "07:30" to "09:15"
    AssignmentType.PICKUP -> "15:00" to "17:00"
}

fun Route.assignmentRoutes(
    config: AppConfig,
    assignmentService: AssignmentService,
    assignmentRepository: AssignmentRepository,
    calendarService: CalendarService,
    accessTokenProvider: AccessTokenProvider,
    tokenRepository: TokenRepository,
) {
    get("/api/parents") {
        call.respond(effectiveParents(config, tokenRepository))
    }

    get("/api/assignments") {
        call.respond(assignmentRepository.all())
    }

    get("/api/suggest") {
        val date = call.parameters["date"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("date mangler (yyyy-MM-dd)"))
        val type = call.parameters["type"]?.let { AssignmentType.valueOf(it.uppercase()) }
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("type mangler (DROPOFF|PICKUP)"))

        val parents = effectiveParents(config, tokenRepository)
        val history = assignmentRepository.all()
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
        val request = call.receive<AssignRequest>()
        if (config.parents.none { it.id == request.parentId }) {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("ukjent parentId"))
        }

        val accessToken = accessTokenProvider.getValidAccessToken(request.parentId)
        var googleEventId: String? = null
        if (accessToken != null) {
            val (defaultStart, defaultEnd) = defaultWindow(request.type)
            val (startIso, endIso) = isoWindow(request.date, request.startTime ?: defaultStart, request.endTime ?: defaultEnd)
            googleEventId = calendarService.insertEvent(
                accessToken = accessToken,
                calendarId = config.sharedCalendarId,
                event = CalendarEventRequest(
                    summary = "${typeLabel(request.type)}: ${config.parents.first { it.id == request.parentId }.name}",
                    start = CalendarEventTime(startIso, ZoneId.systemDefault().id),
                    end = CalendarEventTime(endIso, ZoneId.systemDefault().id),
                ),
            )
        }

        val id = assignmentRepository.insert(
            date = request.date,
            type = request.type,
            parentId = request.parentId,
            source = request.source,
            googleEventId = googleEventId,
        )
        call.respond(
            HttpStatusCode.Created,
            Assignment(id = id, date = request.date, type = request.type, parentId = request.parentId, source = request.source, googleEventId = googleEventId),
        )
    }
}

private fun typeLabel(type: AssignmentType) = if (type == AssignmentType.DROPOFF) "Levering" else "Henting"

/**
 * Slår sammen statisk foreldrekonfigurasjon (id/navn fra miljøvariabler) med reell
 * tilkoblingsstatus fra tokens-tabellen. AppConfig.parents alene reflekterer aldri
 * OAuth-tilkobling, siden den er lest én gang ved oppstart.
 */
private fun effectiveParents(config: AppConfig, tokenRepository: TokenRepository): List<Parent> =
    config.parents.map { parent ->
        parent.copy(googleCalendarId = tokenRepository.find(parent.id)?.googleCalendarId)
    }

private suspend fun fetchBusyPeriods(
    parents: List<Parent>,
    date: String,
    calendarService: CalendarService,
    accessTokenProvider: AccessTokenProvider,
): Map<String, List<BusyPeriod>> {
    val (windowStartIso, windowEndIso) = isoWindow(date, "07:00", "17:30")

    // Kalenderen er delt mellom foreldrene, så vi henter hendelsene én gang (med
    // hvilken som helst tilkoblet forelders token — begge har lesetilgang til
    // samme kalender) og fordeler dem etterpå basert på tittel.
    val sharedCalendarId = parents.firstNotNullOfOrNull { it.googleCalendarId } ?: return emptyMap()
    val accessToken = parents.firstNotNullOfOrNull { accessTokenProvider.getValidAccessToken(it.id) } ?: return emptyMap()
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
 * Avgjør om en hendelse tilhører en gitt forelder ved å se om forelderens navn
 * inngår i hendelsestittelen (case-insensitive). Finnes ikke navnet i tittelen,
 * regnes hendelsen som ueid — den brukes ikke i forslagslogikken for noen.
 */
private fun matchParent(event: CalendarEventItem, parentName: String): BusyPeriod? {
    val summary = event.summary ?: return null
    if (!summary.contains(parentName, ignoreCase = true)) return null

    val start = event.start?.toEpochMillis() ?: return null
    val end = event.end?.toEpochMillis() ?: return null
    return BusyPeriod(start, end)
}

private fun CalendarEventDateTime.toEpochMillis(): Long? {
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
