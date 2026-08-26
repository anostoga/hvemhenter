package no.pilot.barnehage.domain

/**
 * Kjernelogikk for å foreslå hvilken forelder som skal ta en gitt oppgave
 * (levering/henting) en gitt dato.
 *
 * Strategi:
 * 1. Rettferdighet: velg forelderen med færrest historiske tildelinger av denne typen.
 * 2. Uavgjort: alternér basert på hvem som ble tildelt sist (round-robin).
 * 3. Ledighet: hvis den foretrukne forelderen har en kalenderkonflikt i det aktuelle
 *    tidsrommet og den andre forelderen er ledig, foreslå den ledige i stedet.
 * 4. Hvis begge har konflikt, foreslå likevel den mest rettferdige, men marker `conflict = true`
 *    slik at en forelder må bekrefte/overstyre manuelt.
 */
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

        val relevant = history.filter { it.type == type }
        val countByParent = parents.associate { p -> p.id to relevant.count { it.parentId == p.id } }
        val minCount = countByParent.values.min()
        val leastAssigned = parents.filter { countByParent[it.id] == minCount }

        val preferred = if (leastAssigned.size == 1) {
            leastAssigned.first()
        } else {
            // Uavgjort i antall: alternér fra hvem som ble tildelt sist (round-robin).
            val lastAssignedId = relevant.maxByOrNull { it.id ?: -1 }?.parentId
            leastAssigned.firstOrNull { it.id != lastAssignedId } ?: leastAssigned.first()
        }

        fun isBusy(parentId: String): Boolean {
            if (windowStart == null || windowEnd == null) return false
            val busyPeriods = busyByParent[parentId] ?: return false
            return busyPeriods.any { it.startEpochMillis < windowEnd && it.endEpochMillis > windowStart }
        }

        val preferredBusy = isBusy(preferred.id)
        if (!preferredBusy) {
            return Suggestion(
                date = date,
                type = type,
                suggestedParentId = preferred.id,
                reason = "Rettferdig fordeling (færrest tidligere ${type.name.lowercase()})",
            )
        }

        val alternative = parents.first { it.id != preferred.id }
        val alternativeBusy = isBusy(alternative.id)
        if (!alternativeBusy) {
            return Suggestion(
                date = date,
                type = type,
                suggestedParentId = alternative.id,
                reason = "Foretrukket forelder har kalenderkonflikt, ${alternative.name} er ledig",
            )
        }

        // Begge har konflikt — foreslå den mest rettferdige, men flagg for manuell bekreftelse.
        return Suggestion(
            date = date,
            type = type,
            suggestedParentId = preferred.id,
            reason = "Begge foreldre har mulig kalenderkonflikt — bekreft manuelt",
            conflict = true,
        )
    }
}
