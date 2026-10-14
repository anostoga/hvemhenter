package no.pilot.barnehage.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AssignmentConfirmationTokenTest {
    @Test
    fun `token er tilfeldig url-safe og bare hash trenger lagres`() {
        val first = AssignmentConfirmationTokens.generate()
        val second = AssignmentConfirmationTokens.generate()

        assertEquals(43, first.value.length)
        assertTrue(first.value.matches(Regex("^[A-Za-z0-9_-]+$")))
        assertEquals(64, first.hash.length)
        assertEquals(first.hash, AssignmentConfirmationTokens.hash(first.value))
        assertNotEquals(first.hash, second.hash)
        assertNotEquals(first.value, first.hash)
    }
}
