package no.pilot.barnehage.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

        val parent = Parent(id = "p1", name = "Forelder", calendarId = null, availabilityCalendarId = null)

        assertNull(parent.effectiveAvailabilityCalendarId())
    }
}
