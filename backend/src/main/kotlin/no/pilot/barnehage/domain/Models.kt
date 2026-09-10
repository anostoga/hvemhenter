package no.pilot.barnehage.domain

import kotlinx.serialization.Serializable

@Serializable
enum class AssignmentType { DROPOFF, PICKUP }

@Serializable
enum class AssignmentSource { AUTO, MANUAL }

@Serializable
data class Parent(
    val id: String,
    val name: String,

    val avatar: String? = null,

    val connected: Boolean = false,

    val calendarId: String? = null,

    val availabilityCalendarId: String? = null,

    val availabilityDisabled: Boolean = false,

    val isHelper: Boolean = false,
)

fun Parent.effectiveAvailabilityCalendarId(): String? =
    if (availabilityDisabled) null else (availabilityCalendarId ?: calendarId)

@Serializable
data class Assignment(
    val id: String? = null,

    val date: String,
    val type: AssignmentType,
    val parentId: String,
    val source: AssignmentSource,
    val googleEventId: String? = null,
)

data class BusyPeriod(val startEpochMillis: Long, val endEpochMillis: Long)

@Serializable
data class Suggestion(
    val date: String,
    val type: AssignmentType,
    val suggestedParentId: String,
    val reason: String,
    val conflict: Boolean = false,
)
