package no.pilot.barnehage.routes

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.pilot.barnehage.Env
import no.pilot.barnehage.auth.UserSession
import no.pilot.barnehage.auth.configureSessionAuth
import no.pilot.barnehage.db.AdminRepository
import no.pilot.barnehage.db.FamiliesTable
import no.pilot.barnehage.db.FamilyActivityRepository
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.db.ParentsTable
import no.pilot.barnehage.plugins.configureSerialization
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FamilyActivityRoutesTest {
    private val database = Database.connect(
        url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:9876/barnehage",
        user = System.getenv("DATABASE_USER") ?: "postgres",
        password = System.getenv("DATABASE_PASSWORD") ?: "localdev",
        driver = "org.postgresql.Driver",
    )
    private lateinit var familyId: UUID
    private lateinit var otherFamilyId: UUID
    private lateinit var parentId: UUID
    private lateinit var adminId: UUID

    @BeforeTest
    fun setUp() {
        transaction(database) {
            familyId = FamiliesTable.insert { it[sharedCalendarId] = "" }[FamiliesTable.id]
            otherFamilyId = FamiliesTable.insert { it[sharedCalendarId] = "" }[FamiliesTable.id]
            fun parent(admin: Boolean) = ParentsTable.insert {
                it[ParentsTable.familyId] = this@FamilyActivityRoutesTest.familyId
                it[name] = "Test"
                it[googleSub] = "activity-route-${UUID.randomUUID()}"
                it[isAdmin] = admin
            }[ParentsTable.id]
            parentId = parent(false)
            adminId = parent(true)
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            ParentsTable.deleteWhere { Op.build { ParentsTable.familyId eq familyId } }
            FamiliesTable.deleteWhere { Op.build { FamiliesTable.id inList listOf(familyId, otherFamilyId) } }
        }
    }

    @Test
    fun `view endpoint uses session family and admin activity requires admin access`() = testApplication {
        application {
            Env.overrideForTests("SESSION_SIGNING_SECRET", "test-session-signing-secret")
            Env.overrideForTests("SESSION_COOKIE_SECURE", "false")
            configureSerialization()
            configureSessionAuth()
            val families = FamilyRepository(database)
            routing {
                get("/test-login/{role}") {
                    val role = call.parameters["role"]
                    call.sessions.set(UserSession(
                        parentId = (if (role == "admin") adminId else parentId).toString(),
                        familyId = (if (role == "wrong-family") otherFamilyId else familyId).toString(),
                    ))
                    call.respondText("ok")
                }
                familyActivityRoutes(families, FamilyActivityRepository(database))
                adminRoutes(families, AdminRepository(database))
            }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/family/calendar-views").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin/stats").status)

        val member = createClient { install(HttpCookies) }
        member.get("/test-login/member")
        assertEquals(HttpStatusCode.Forbidden, member.get("/api/admin/stats").status)
        assertEquals(HttpStatusCode.NoContent, member.post("/api/family/calendar-views") {
            setBody("""{"familyId":"$otherFamilyId","timestamp":"2099-01-01T00:00:00Z"}""")
        }.status)
        val stats = AdminRepository(database).stats()
        assertNotNull(stats.families.single { it.id == familyId }.lastCalendarViewedAt)
        assertNull(stats.families.single { it.id == otherFamilyId }.lastActiveAt)

        member.get("/test-login/wrong-family")
        assertEquals(HttpStatusCode.Forbidden, member.post("/api/family/calendar-views").status)
        assertNull(AdminRepository(database).stats().families.single { it.id == otherFamilyId }.lastActiveAt)

        val admin = createClient { install(HttpCookies) }
        admin.get("/test-login/admin")
        val response = admin.get("/api/admin/stats")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val rows = json.getValue("families").jsonArray.map { it.jsonObject }
        val own = rows.single { it.getValue("id").jsonPrimitive.content == familyId.toString() }
        assertEquals(
            stats.families.single { it.id == familyId }.lastCalendarViewedAt.toString(),
            own.getValue("lastActiveAt").jsonPrimitive.content,
        )
        assertEquals(own.getValue("lastCalendarViewedAt"), own.getValue("lastActiveAt"))
    }
}
