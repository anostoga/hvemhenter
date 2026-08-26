package no.pilot.barnehage.auth

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tester ekte SessionAuth.kt-logikk: signert cookie gir tilgang, manipulert eller
 * manglende cookie gir 401. Dette er den viktigste sikkerhetstesten for selve
 * autentiseringsmekanismen — en svakhet her ville omgått familie-scoping i hele appen.
 */
class SessionAuthTest {

    @Test
    fun `gyldig signert sesjonscookie gir autentisert prinsipal`() = testApplication {
        application {
            no.pilot.barnehage.Env.overrideForTests("SESSION_SIGNING_SECRET", "test-secret-abc")
            no.pilot.barnehage.Env.overrideForTests("SESSION_COOKIE_SECURE", "false")
            configureSessionAuth()
            routing {
                get("/logg-inn-test") {
                    call.sessions.set(UserSession(parentId = "parent-1", familyId = "family-1"))
                    call.respondText("ok")
                }
                authenticate(SESSION_AUTH_NAME) {
                    get("/beskyttet") {
                        val session = call.userSession()
                        call.respondText(session?.familyId ?: "ingen")
                    }
                }
            }
        }
        val client = createClient { install(HttpCookies) }

        client.get("/logg-inn-test")
        val response = client.get("/beskyttet")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("family-1", response.bodyAsText())
    }

    @Test
    fun `request uten sesjonscookie gir 401 paa beskyttede ruter`() = testApplication {
        application {
            no.pilot.barnehage.Env.overrideForTests("SESSION_SIGNING_SECRET", "test-secret-abc")
            no.pilot.barnehage.Env.overrideForTests("SESSION_COOKIE_SECURE", "false")
            configureSessionAuth()
            routing {
                authenticate(SESSION_AUTH_NAME) {
                    get("/beskyttet") { call.respondText("skal ikke naas") }
                }
            }
        }

        val response = client.get("/beskyttet")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `manipulert sesjonscookie avvises med 401`() = testApplication {
        application {
            no.pilot.barnehage.Env.overrideForTests("SESSION_SIGNING_SECRET", "test-secret-abc")
            no.pilot.barnehage.Env.overrideForTests("SESSION_COOKIE_SECURE", "false")
            configureSessionAuth()
            routing {
                get("/logg-inn-test") {
                    call.sessions.set(UserSession(parentId = "parent-1", familyId = "family-1"))
                    call.respondText("ok")
                }
                authenticate(SESSION_AUTH_NAME) {
                    get("/beskyttet") { call.respondText("skal ikke naas") }
                }
            }
        }

        val loginResponse = client.get("/logg-inn-test")
        val realCookie = loginResponse.headers.getAll("Set-Cookie")?.firstOrNull { it.startsWith("bhg_session=") }
            ?: error("forventet at innlogging setter bhg_session-cookie")
        val cookieValue = realCookie.substringAfter("bhg_session=").substringBefore(";")
        // Tukle med signaturen: reverser den signerte verdien slik at signaturen ikke lenger stemmer
        // med det (nå endrede) innholdet — verifiserer at HMAC-signeringen faktisk håndheves.
        // Tukle med signaturen: bytt ett hex-tegn i den signerte cookien slik at
        // signaturen ikke lenger stemmer med innholdet — verifiserer at HMAC-
        // signeringen faktisk håndheves (ikke bare et format-krav).
        val tamperedValue = tamperOneHexChar(cookieValue)

        val response = client.get("/beskyttet") {
            headers.append("Cookie", "bhg_session=$tamperedValue")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private fun tamperOneHexChar(value: String): String {
        val index = value.indexOfFirst { it in "0123456789abcdef" }
        require(index >= 0) { "fant ingen hex-tegn å tukle med i cookien" }
        val original = value[index]
        val replacement = if (original == '0') '1' else '0'
        return value.substring(0, index) + replacement + value.substring(index + 1)
    }
}
