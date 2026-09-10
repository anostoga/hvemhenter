package no.pilot.barnehage.routes

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.set
import io.ktor.server.sessions.sessions
import io.ktor.server.testing.testApplication
import no.pilot.barnehage.Env
import no.pilot.barnehage.auth.UserSession
import no.pilot.barnehage.auth.configureSessionAuth
import no.pilot.barnehage.plugins.configureSerialization
import no.pilot.barnehage.db.FamiliesTable
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.db.ParentsTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRoutesWhoAmITest {
    private val database = Database.connect(
        url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/barnehage",
        user = System.getenv("DATABASE_USER") ?: "postgres",
        password = System.getenv("DATABASE_PASSWORD") ?: "localdev",
        driver = "org.postgresql.Driver",
    )
    private val repository = FamilyRepository(database)
    private val createdParentGoogleSubs = mutableListOf<String>()

    @AfterTest
    fun tearDown() {
        transaction(database) {
            val parentIds = ParentsTable.selectAll().where { ParentsTable.googleSub inList createdParentGoogleSubs }
                .map { it[ParentsTable.id] }
            val familyIds = if (parentIds.isNotEmpty()) {
                ParentsTable.selectAll().where { ParentsTable.id inList parentIds }.map { it[ParentsTable.familyId] }
            } else emptyList()
            ParentsTable.deleteWhere { Op.build { ParentsTable.googleSub inList createdParentGoogleSubs } }
            if (familyIds.isNotEmpty()) {
                FamiliesTable.deleteWhere { Op.build { FamiliesTable.id inList familyIds } }
            }
        }
    }

    private fun sub() = "whoami-sub-${UUID.randomUUID()}".also { createdParentGoogleSubs.add(it) }

    @Test
    fun `whoami uten cookie svarer loggedIn false uten 401`() = testApplication {
        application {
            Env.overrideForTests("SESSION_SIGNING_SECRET", "test-secret-abc")
            Env.overrideForTests("SESSION_COOKIE_SECURE", "false")
            configureSerialization()
            configureSessionAuth()
            routing { registerWhoAmIAndLogout(repository) }
        }

        val response = client.get("/auth/whoami")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"loggedIn\": false"))
    }

    @Test
    fun `whoami med gyldig sesjon returnerer navnet til den innloggede brukeren`() = testApplication {
        Env.overrideForTests("FAMILY_CREATION_CODE", "riktig-kode")
        val googleSub = sub()
        val familyId = handleJoin("riktig-kode", googleSub, "a@example.com", "Kari Nordmann", repository)!!
        val parent = repository.findParentByGoogleSub(googleSub)!!

        application {
            Env.overrideForTests("SESSION_SIGNING_SECRET", "test-secret-abc")
            Env.overrideForTests("SESSION_COOKIE_SECURE", "false")
            configureSerialization()
            configureSessionAuth()
            routing {
                get("/logg-inn-test") {
                    call.sessions.set(UserSession(parentId = parent.id.toString(), familyId = familyId))
                    call.respondText("ok")
                }
                registerWhoAmIAndLogout(repository)
            }
        }
        val testClient = createClient { install(HttpCookies) }

        testClient.get("/logg-inn-test")
        val response = testClient.get("/auth/whoami")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"loggedIn\": true"))
        assertTrue(body.contains("Kari Nordmann"))
    }

    @Test
    fun `logout fjerner sesjonen slik at whoami deretter svarer loggedIn false`() = testApplication {
        Env.overrideForTests("FAMILY_CREATION_CODE", "riktig-kode")
        val googleSub = sub()
        val familyId = handleJoin("riktig-kode", googleSub, "b@example.com", "Ola Nordmann", repository)!!
        val parent = repository.findParentByGoogleSub(googleSub)!!

        application {
            Env.overrideForTests("SESSION_SIGNING_SECRET", "test-secret-abc")
            Env.overrideForTests("SESSION_COOKIE_SECURE", "false")
            configureSerialization()
            configureSessionAuth()
            routing {
                get("/logg-inn-test") {
                    call.sessions.set(UserSession(parentId = parent.id.toString(), familyId = familyId))
                    call.respondText("ok")
                }
                registerWhoAmIAndLogout(repository)
            }
        }
        val testClient = createClient { install(HttpCookies) }

        testClient.get("/logg-inn-test")
        val loggedInResponse = testClient.get("/auth/whoami")
        assertTrue(loggedInResponse.bodyAsText().contains("\"loggedIn\": true"))

        val logoutResponse = testClient.post("/auth/logout")
        assertEquals(HttpStatusCode.OK, logoutResponse.status)

        val afterLogoutResponse = testClient.get("/auth/whoami")
        assertTrue(afterLogoutResponse.bodyAsText().contains("\"loggedIn\": false"))
    }
}
