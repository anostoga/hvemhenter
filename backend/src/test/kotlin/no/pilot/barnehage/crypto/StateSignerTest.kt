package no.pilot.barnehage.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StateSignerTest {

    private val signer = StateSigner("test-secret")

    @Test
    fun `signerer og verifiserer en enkel payload`() {
        val state = signer.sign("some-parent-id")
        assertEquals("some-parent-id", signer.verify(state))
    }

    @Test
    fun `payload med kolon (join- og reconnect-prefiks) rundtrippes korrekt`() {

        val joinState = signer.sign("join:min-invitasjonskode")
        assertEquals("join:min-invitasjonskode", signer.verify(joinState))

        val reconnectState = signer.sign("reconnect:11111111-1111-1111-1111-111111111111")
        assertEquals("reconnect:11111111-1111-1111-1111-111111111111", signer.verify(reconnectState))
    }

    @Test
    fun `forfalsket signatur avvises`() {
        val state = signer.sign("some-parent-id")
        val tampered = state.dropLast(1) + "x"
        assertNull(signer.verify(tampered))
    }

    @Test
    fun `utløpt state avvises`() {
        val expiredSigner = StateSigner("test-secret", ttlSeconds = -1)
        val state = expiredSigner.sign("some-parent-id")
        assertNull(signer.verify(state))
    }
}
