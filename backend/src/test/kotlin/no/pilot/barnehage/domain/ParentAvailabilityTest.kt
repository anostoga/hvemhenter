package no.pilot.barnehage.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Låser oppførselen til de tre gjensidig utelukkende tilstandene for
 * tilgjengelighetssjekk (se `effectiveAvailabilityCalendarId()`): "samme som
 * skrivekalender" (default), "annen spesifikk kalender", og eksplisitt
 * "deaktivert" (uavhengig av om `calendarId`/`availabilityCalendarId` er
 * satt) — samme logikk speiles i `ParentRecord.effectiveAvailabilityCalendarId()`
 * i db/FamilyRepository.kt.
 */
class ParentAvailabilityTest {

    @Test
    fun `uten eksplisitt tilgjengelighetskalender faller den tilbake til skrivekalenderen`() {
        val parent = Parent(id = "p1", name = "Forelder", calendarId = "skrive-kalender")

        assertEquals("skrive-kalender", parent.effectiveAvailabilityCalendarId())
    }

    @Test
    fun `med en spesifikk tilgjengelighetskalender brukes den, ikke skrivekalenderen`() {
        val parent = Parent(
            id = "p1",
            name = "Forelder",
            calendarId = "skrive-kalender",
            availabilityCalendarId = "tilgjengelighet-kalender",
        )

        assertEquals("tilgjengelighet-kalender", parent.effectiveAvailabilityCalendarId())
    }

    @Test
    fun `deaktivert tilgjengelighetssjekk gir null selv om kalendere er satt`() {
        val parent = Parent(
            id = "p1",
            name = "Forelder",
            calendarId = "skrive-kalender",
            availabilityCalendarId = "tilgjengelighet-kalender",
            availabilityDisabled = true,
        )

        assertNull(parent.effectiveAvailabilityCalendarId())
    }

    @Test
    fun `deaktivert tilgjengelighetssjekk gir null naar ingen kalender er valgt`() {
        val parent = Parent(id = "p1", name = "Forelder", availabilityDisabled = true)

        assertNull(parent.effectiveAvailabilityCalendarId())
    }

    @Test
    fun `uten skrivekalender og uten avvikende tilgjengelighetskalender gir null`() {
        // Brukeren har valgt å ikke skrive til noen kalender (calendarId = null) og
        // ikke satt noen egen tilgjengelighetskalender — degraderer til "ingen
        // kalender å spørre", samme fail-soft som når ingenting er konfigurert ennå.
        val parent = Parent(id = "p1", name = "Forelder", calendarId = null, availabilityCalendarId = null)

        assertNull(parent.effectiveAvailabilityCalendarId())
    }
}
