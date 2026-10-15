package no.pilot.barnehage.routes

import kotlin.test.Test
import kotlin.test.assertEquals

class AssignmentConfirmationLinkTest {
    @Test
    fun `confirmation share link uses the public response page`() {
        assertEquals("/bekreft/${"a".repeat(43)}", confirmationPath("a".repeat(43)))
    }
}
