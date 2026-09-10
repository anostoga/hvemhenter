package no.pilot.barnehage.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssignmentServiceTest {
    private val service = AssignmentService()
    private val mor = Parent(id = "mor", name = "Mor")
    private val far = Parent(id = "far", name = "Far")
    private val parents = listOf(mor, far)

    @Test
    fun `foreslaar forelder med faerrest tidligere tildelinger`() {
        val history = listOf(
            Assignment(id = "1", date = "2026-08-20", type = AssignmentType.DROPOFF, parentId = mor.id, source = AssignmentSource.AUTO),
        )

        val suggestion = service.suggest(parents, history, "2026-08-21", AssignmentType.DROPOFF)

        assertEquals(far.id, suggestion.suggestedParentId)
        assertFalse(suggestion.conflict)
    }

    @Test
    fun `alternerer basert paa sist tildelte ved uavgjort`() {
        val history = listOf(
            Assignment(id = "1", date = "2026-08-19", type = AssignmentType.DROPOFF, parentId = mor.id, source = AssignmentSource.AUTO),
            Assignment(id = "2", date = "2026-08-20", type = AssignmentType.DROPOFF, parentId = far.id, source = AssignmentSource.AUTO),
        )

        val suggestion = service.suggest(parents, history, "2026-08-21", AssignmentType.DROPOFF)

        assertEquals(mor.id, suggestion.suggestedParentId)
    }

    @Test
    fun `foreslaar ledig forelder naar foretrukket har kalenderkonflikt`() {
        val history = emptyList<Assignment>()
        val windowStart = 1_000L
        val windowEnd = 2_000L

        val busyByParent = mapOf(mor.id to listOf(BusyPeriod(windowStart, windowEnd)))

        val suggestion = service.suggest(
            parents, history, "2026-08-21", AssignmentType.PICKUP,
            busyByParent = busyByParent, windowStart = windowStart, windowEnd = windowEnd,
        )

        assertEquals(far.id, suggestion.suggestedParentId)
        assertFalse(suggestion.conflict)
    }

    @Test
    fun `flagger konflikt naar begge foreldre er opptatt`() {
        val history = emptyList<Assignment>()
        val windowStart = 1_000L
        val windowEnd = 2_000L
        val busyByParent = mapOf(
            mor.id to listOf(BusyPeriod(windowStart, windowEnd)),
            far.id to listOf(BusyPeriod(windowStart, windowEnd)),
        )

        val suggestion = service.suggest(
            parents, history, "2026-08-21", AssignmentType.PICKUP,
            busyByParent = busyByParent, windowStart = windowStart, windowEnd = windowEnd,
        )

        assertTrue(suggestion.conflict)
    }

    @Test
    fun `foreslaar den andre forelderen naar levering samme dag allerede er tildelt`() {
        val history = listOf(
            Assignment(id = "1", date = "2026-08-21", type = AssignmentType.DROPOFF, parentId = mor.id, source = AssignmentSource.AUTO),
        )

        val suggestion = service.suggest(parents, history, "2026-08-21", AssignmentType.PICKUP)

        assertEquals(far.id, suggestion.suggestedParentId)
        assertFalse(suggestion.conflict)
    }

    @Test
    fun `faller tilbake til rettferdighet naar komplementaer forelder har kalenderkonflikt samme dag`() {
        val history = listOf(
            Assignment(id = "1", date = "2026-08-21", type = AssignmentType.DROPOFF, parentId = mor.id, source = AssignmentSource.AUTO),
        )
        val windowStart = 1_000L
        val windowEnd = 2_000L

        val busyByParent = mapOf(far.id to listOf(BusyPeriod(windowStart, windowEnd)))

        val suggestion = service.suggest(
            parents, history, "2026-08-21", AssignmentType.PICKUP,
            busyByParent = busyByParent, windowStart = windowStart, windowEnd = windowEnd,
        )

        assertEquals(mor.id, suggestion.suggestedParentId)
    }

    @Test
    fun `krever minst to foreldre`() {
        var threw = false
        try {
            service.suggest(listOf(mor), emptyList(), "2026-08-21", AssignmentType.DROPOFF)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
