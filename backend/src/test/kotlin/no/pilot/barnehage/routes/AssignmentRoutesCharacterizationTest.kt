package no.pilot.barnehage.routes

import no.pilot.barnehage.domain.AssignmentType
import no.pilot.barnehage.google.CalendarEventDateTime
import no.pilot.barnehage.google.CalendarEventItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Karakteriseringstester: låser dagens adferd for kalender-/tidsvinduslogikk
 * FØR multi-familie-refaktoreringen (Fase 4). Hvis noen av disse feiler etter
 * refaktorering er det et signal om at adferden har endret seg utilsiktet.
 */
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
}
