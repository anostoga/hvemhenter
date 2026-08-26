package no.pilot.barnehage.google

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import no.pilot.barnehage.domain.BusyPeriod
import org.slf4j.LoggerFactory
import kotlinx.coroutines.delay

@Serializable
data class FreeBusyRequest(
    val timeMin: String,
    val timeMax: String,
    val items: List<CalendarId>,
)

@Serializable
data class CalendarId(val id: String)

@Serializable
data class FreeBusyResponse(val calendars: Map<String, FreeBusyCalendar>)

@Serializable
data class FreeBusyCalendar(val busy: List<BusyInterval> = emptyList())

@Serializable
data class BusyInterval(val start: String, val end: String)

@Serializable
data class CalendarEventTime(val dateTime: String, val timeZone: String? = null)

@Serializable
data class CalendarEventRequest(
    val summary: String,
    val start: CalendarEventTime,
    val end: CalendarEventTime,
    val description: String? = null,
)

@Serializable
data class CalendarEventResponse(val id: String)

@Serializable
data class CalendarEventDateTime(val dateTime: String? = null, val date: String? = null)

@Serializable
data class CalendarEventItem(
    val summary: String? = null,
    val start: CalendarEventDateTime? = null,
    val end: CalendarEventDateTime? = null,
)

@Serializable
data class CalendarEventsResponse(val items: List<CalendarEventItem> = emptyList())

/**
 * Tynn wrapper rundt Google Calendar API v3. Kaller med enkel retry (eksponentiell backoff,
 * 3 forsøk) ved forbigående feil (5xx/nettverk), jf. beslutning i planen.
 */
class CalendarService(private val httpClient: HttpClient) {
    private val logger = LoggerFactory.getLogger(CalendarService::class.java)

    suspend fun freeBusy(accessToken: String, calendarIds: List<String>, timeMinIso: String, timeMaxIso: String): Map<String, List<BusyPeriod>> {
        val response: FreeBusyResponse = withRetry("freeBusy") {
            httpClient.post("https://www.googleapis.com/calendar/v3/freeBusy") {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(FreeBusyRequest(timeMinIso, timeMaxIso, calendarIds.map { CalendarId(it) }))
            }.body()
        }

        return response.calendars.mapValues { (_, cal) ->
            cal.busy.map { BusyPeriod(java.time.Instant.parse(it.start).toEpochMilli(), java.time.Instant.parse(it.end).toEpochMilli()) }
        }
    }

    suspend fun insertEvent(accessToken: String, calendarId: String, event: CalendarEventRequest): String {
        val httpResponse = withRetry("insertEvent") {
            httpClient.post("https://www.googleapis.com/calendar/v3/calendars/$calendarId/events") {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(event)
            }
        }
        val bodyText = httpResponse.bodyAsText()
        if (!httpResponse.status.isSuccess()) {
            // Google returnerte en feil (f.eks. 403/404 -- ugyldig calendarId, mangler
            // tilgang, token uten skrivetilgang). Uten denne sjekken ville koden prøve å
            // parse feilteksten som en CalendarEventResponse og feile med en kryptisk
            // JsonConvertException("Field 'id' is required...") i stedet for reell årsak.
            logger.warn("insertEvent feilet mot Google Calendar ({}): {}", httpResponse.status, bodyText)
            throw IllegalStateException("Google Calendar avviste opprettelse av hendelse (${httpResponse.status}): $bodyText")
        }
        return kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(CalendarEventResponse.serializer(), bodyText).id
    }

    /** Sletter en kalenderhendelse — brukt når en tildeling erstattes med en annen
     * forelder/tidspunkt, slik at den gamle hendelsen ikke blir hengende igjen i
     * kalenderen uten at noen tildeling peker på den. Feiler stille (logger bare en
     * advarsel) hvis hendelsen allerede er slettet manuelt i Google Kalender. */
    suspend fun deleteEvent(accessToken: String, calendarId: String, eventId: String) {
        try {
            withRetry("deleteEvent") {
                httpClient.delete("https://www.googleapis.com/calendar/v3/calendars/$calendarId/events/$eventId") {
                    header("Authorization", "Bearer $accessToken")
                }
            }
        } catch (e: Exception) {
            logger.warn("Kunne ikke slette kalenderhendelse $eventId (kanskje allerede slettet manuelt): ${e.message}")
        }
    }

    suspend fun listUpcoming(accessToken: String, calendarId: String, timeMinIso: String, timeMaxIso: String): String {
        // Returnerer rå JSON-tekst for enkelhets skyld i MVP; kan modelleres fullt ut senere ved behov.
        return withRetry("listUpcoming") {
            httpClient.get("https://www.googleapis.com/calendar/v3/calendars/$calendarId/events") {
                header("Authorization", "Bearer $accessToken")
                parameter("timeMin", timeMinIso)
                parameter("timeMax", timeMaxIso)
                parameter("singleEvents", "true")
                parameter("orderBy", "startTime")
            }.body()
        }
    }

    /**
     * Henter hendelser (med tittel) i tidsvinduet fra en delt kalender. Brukes til å avgjøre
     * hvilken forelder en hendelse tilhører (se matchParent i AssignmentRoutes) — freeBusy-API-et
     * gir kun tidsrom uten tittel, og duger derfor ikke når kalenderen er delt mellom foreldrene.
     */
    suspend fun listEvents(accessToken: String, calendarId: String, timeMinIso: String, timeMaxIso: String): List<CalendarEventItem> {
        val response: CalendarEventsResponse = withRetry("listEvents") {
            httpClient.get("https://www.googleapis.com/calendar/v3/calendars/$calendarId/events") {
                header("Authorization", "Bearer $accessToken")
                parameter("timeMin", timeMinIso)
                parameter("timeMax", timeMaxIso)
                parameter("singleEvents", "true")
                parameter("orderBy", "startTime")
            }.body()
        }
        return response.items
    }

    private suspend fun <T> withRetry(operation: String, maxAttempts: Int = 3, block: suspend () -> T): T {
        var lastError: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                logger.warn("Google Calendar-kall '$operation' feilet (forsøk ${attempt + 1}/$maxAttempts): ${e.message}")
                if (attempt < maxAttempts - 1) {
                    delay(200L * (1 shl attempt)) // 200ms, 400ms, 800ms
                }
            }
        }
        throw lastError ?: IllegalStateException("Ukjent feil i $operation")
    }
}
