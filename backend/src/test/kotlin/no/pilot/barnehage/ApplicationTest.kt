package no.pilot.barnehage

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun `health endpoint returns ok`() = testApplication {
        application {
            module()
        }
        val response = client.get("/health")
        assertTrue(response.bodyAsText().contains("ok"))
    }
}
