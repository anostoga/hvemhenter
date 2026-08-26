package no.pilot.barnehage.domain

import kotlinx.serialization.Serializable

/** Type oppgave knyttet til en barnehagedag. */
@Serializable
enum class AssignmentType { DROPOFF, PICKUP }

/** Hvordan en tildeling ble bestemt. */
@Serializable
enum class AssignmentSource { AUTO, MANUAL }

@Serializable
data class Parent(
    val id: String,
    val name: String,
    /** Om forelderen har koblet til Google Kalender (dvs. har en gyldig/fornybar token lagret). */
    val connected: Boolean = false,
)

@Serializable
data class Assignment(
    val id: String? = null,
    /** ISO-8601 dato (yyyy-MM-dd) */
    val date: String,
    val type: AssignmentType,
    val parentId: String,
    val source: AssignmentSource,
    val googleEventId: String? = null,
)

/** Representerer et opptatt tidsrom hentet fra Google Calendar sin freebusy-API. */
data class BusyPeriod(val startEpochMillis: Long, val endEpochMillis: Long)

/** Resultatet av et forslag om hvem som bør ta en oppgave. */
@Serializable
data class Suggestion(
    val date: String,
    val type: AssignmentType,
    val suggestedParentId: String,
    val reason: String,
    val conflict: Boolean = false,
)
