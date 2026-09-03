package no.pilot.barnehage.domain

/**
 * Kjernelogikk for å foreslå hvilken forelder som skal ta en gitt oppgave
 * (levering/henting) en gitt dato.
 *
 * Strategi:
 * 1. Samme dag: hvis den andre oppgaven (levering/henting) samme dato allerede
 *    er tildelt en forelder, foreslå den ANDRE forelderen for denne oppgaven —
 *    slik at levering og henting samme dag fordeles på begge foreldre, i
 *    stedet for at én forelder gjør begge oppgavene én dag og den andre gjør
 *    begge oppgavene neste dag.
 * 2. Rettferdighet: ellers, velg forelderen med færrest historiske tildelinger av denne typen.
 * 3. Uavgjort: alternér basert på hvem som ble tildelt sist (round-robin).
 * 4. Ledighet: hvis den foretrukne forelderen har en kalenderkonflikt i det aktuelle
 *    tidsrommet og den andre forelderen er ledig, foreslå den ledige i stedet.
 * 5. Hvis begge har konflikt, foreslå likevel den mest rettferdige, men marker `conflict = true`
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

        // Er den andre oppgaven (levering/henting) samme dato allerede tildelt?
        // I så fall prioriteres det å fordele dagens to oppgaver på begge foreldre
        // (én leverer, én henter) fremfor den generelle rettferdighets-tellingen.
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
                // Den som skulle utfylt dagen har kalenderkonflikt — fall gjennom til
                // vanlig rettferdighets-/ledighetslogikk under i stedet for å tvinge
                // frem et forslag som uansett må overstyres manuelt.
            }
        }

        val relevant = history.filter { it.type == type }
        val countByParent = parents.associate { p -> p.id to relevant.count { it.parentId == p.id } }
        val minCount = countByParent.values.min()
        val leastAssigned = parents.filter { countByParent[it.id] == minCount }

        val preferred = if (leastAssigned.size == 1) {
            leastAssigned.first()
        } else {
            // Uavgjort i antall: alternér fra hvem som ble tildelt sist (round-robin).
            // `relevant` er i kronologisk rekkefølge (eldst→nyest), så siste element er sist tildelt.
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
            // Selv om den foretrukne forelderen er ledig og blir foreslått, nevner vi
            // det hvis den ANDRE forelderen har en kalenderkonflikt — nyttig kontekst,
            // selv om det ikke endrer selve forslaget.
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

        // Begge har konflikt — foreslå den mest rettferdige, men flagg for manuell bekreftelse.
        return Suggestion(
            date = date,
            type = type,
            suggestedParentId = preferred.id,
            reason = "${preferred.name}: begge foreldre har mulig kalenderkonflikt — bekreft manuelt",
            conflict = true,
        )
    }
}
