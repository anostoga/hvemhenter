package no.pilot.barnehage.routes

import no.pilot.barnehage.domain.AssignmentType
import no.pilot.barnehage.domain.Parent
import no.pilot.barnehage.google.CalendarEventDateTime
import no.pilot.barnehage.google.CalendarEventItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssignmentRoutesCharacterizationTest {

    @Test
    fun `standard tidsvindu for levering er 07-30 til 09-15`() {
        assertEquals("07:30" to "09:15", defaultWindow(AssignmentType.DROPOFF))
    }

    @Test
    fun `standard tidsvindu for henting er 15-00 til 17-00`() {
        assertEquals("15:00" to "17:00", defaultWindow(AssignmentType.PICKUP))
    }

    @Test
    fun `hendelse matcher forelder ved case-insensitiv delstreng i tittel`() {
        val event = CalendarEventItem(
            summary = "Tannlege - Marthe",
            start = CalendarEventDateTime(dateTime = "2026-08-27T09:00:00+02:00"),
            end = CalendarEventDateTime(dateTime = "2026-08-27T10:00:00+02:00"),
        )

        val busy = matchParent(event, "marthe")

        assertEquals(true, busy != null)
    }

    @Test
    fun `hendelse uten forelderens navn i tittelen matcher ikke`() {
        val event = CalendarEventItem(
            summary = "Formiddagsmøte",
            start = CalendarEventDateTime(dateTime = "2026-08-27T09:00:00+02:00"),
            end = CalendarEventDateTime(dateTime = "2026-08-27T10:00:00+02:00"),
        )

        assertNull(matchParent(event, "Mor"))
    }

    @Test
    fun `hendelse uten tittel matcher aldri`() {
        val event = CalendarEventItem(
            summary = null,
            start = CalendarEventDateTime(dateTime = "2026-08-27T09:00:00+02:00"),
            end = CalendarEventDateTime(dateTime = "2026-08-27T10:00:00+02:00"),
        )

        assertNull(matchParent(event, "Marthe"))
    }

    @Test
    fun `heldagshendelse konverteres til midnatt lokal tid`() {
        val dt = CalendarEventDateTime(date = "2026-08-27")

        val millis = dt.toEpochMillis()

        assertEquals(true, millis != null)
    }

    @Test
    fun `hendelse uten dato eller dateTime gir null`() {
        val dt = CalendarEventDateTime(dateTime = null, date = null)
        assertNull(dt.toEpochMillis())
    }

    @Test
    fun `excludeHelpers fjerner hjelpere, men beholder innloggede foreldre`() {
        val mor = Parent(id = "1", name = "Mor", isHelper = false)
        val bestemor = Parent(id = "2", name = "Bestemor", isHelper = true)
        val far = Parent(id = "3", name = "Far", isHelper = false)

        val result = excludeHelpers(listOf(mor, bestemor, far))

        assertEquals(listOf(mor, far), result)
    }

    @Test
    fun `excludeHelpers med kun hjelpere gir en tom liste`() {
        val bestemor = Parent(id = "1", name = "Bestemor", isHelper = true)
        assertEquals(emptyList(), excludeHelpers(listOf(bestemor)))
    }
}
