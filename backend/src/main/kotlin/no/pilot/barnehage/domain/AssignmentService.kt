package no.pilot.barnehage.domain

class AssignmentService {

    fun suggest(
        parents: List<Parent>,
        history: List<Assignment>,
        date: String,
        type: AssignmentType,
        busyByParent: Map<String, List<BusyPeriod>> = emptyMap(),
        windowStart: Long? = null,
        windowEnd: Long? = null,
    ): Suggestion {
        require(parents.size >= 2) { "Trenger minst to foreldre for å foreslå fordeling" }

        val sameDayOtherType = history.lastOrNull { it.date == date && it.type != type }
        if (sameDayOtherType != null) {
            val complement = parents.firstOrNull { it.id != sameDayOtherType.parentId }
            val otherParentName = parents.firstOrNull { it.id == sameDayOtherType.parentId }?.name ?: "den andre forelderen"
            if (complement != null) {
                val complementBusy = if (windowStart != null && windowEnd != null) {
                    (busyByParent[complement.id] ?: emptyList()).any { it.startEpochMillis < windowEnd && it.endEpochMillis > windowStart }
                } else {
                    false
                }
                if (!complementBusy) {
                    return Suggestion(
                        date = date,
                        type = type,
                        suggestedParentId = complement.id,
                        reason = "${complement.name}: fordeler levering/henting samme dag ($otherParentName har allerede ${sameDayOtherType.type.name.lowercase()} denne dagen)",
                    )
                }

            }
        }

        val relevant = history.filter { it.type == type }
        val countByParent = parents.associate { p -> p.id to relevant.count { it.parentId == p.id } }
        val minCount = countByParent.values.min()
        val leastAssigned = parents.filter { countByParent[it.id] == minCount }

        val preferred = if (leastAssigned.size == 1) {
            leastAssigned.first()
        } else {

            val lastAssignedId = relevant.lastOrNull()?.parentId
            leastAssigned.firstOrNull { it.id != lastAssignedId } ?: leastAssigned.first()
        }

        fun isBusy(parentId: String): Boolean {
            if (windowStart == null || windowEnd == null) return false
            val busyPeriods = busyByParent[parentId] ?: return false
            return busyPeriods.any { it.startEpochMillis < windowEnd && it.endEpochMillis > windowStart }
        }

        val preferredBusy = isBusy(preferred.id)
        if (!preferredBusy) {
            val alternative = parents.first { it.id != preferred.id }

            val note = if (isBusy(alternative.id)) " (${alternative.name} har kalenderkonflikt i dette tidsrommet)" else ""
            return Suggestion(
                date = date,
                type = type,
                suggestedParentId = preferred.id,
                reason = "Rettferdig fordeling: ${preferred.name} (færrest tidligere ${type.name.lowercase()})$note",
            )
        }

        val alternative = parents.first { it.id != preferred.id }
        val alternativeBusy = isBusy(alternative.id)
        if (!alternativeBusy) {
            return Suggestion(
                date = date,
                type = type,
                suggestedParentId = alternative.id,
                reason = "${alternative.name}: foretrukket forelder (${preferred.name}) har kalenderkonflikt, ${alternative.name} er ledig",
            )
        }

        return Suggestion(
            date = date,
            type = type,
            suggestedParentId = preferred.id,
            reason = "${preferred.name}: begge foreldre har mulig kalenderkonflikt — bekreft manuelt",
            conflict = true,
        )
    }
}
