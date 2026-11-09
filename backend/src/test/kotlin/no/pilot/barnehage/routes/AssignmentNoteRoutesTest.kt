package no.pilot.barnehage.routes

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
import no.pilot.barnehage.crypto.TokenCipher
import no.pilot.barnehage.db.AssignmentsTable
import no.pilot.barnehage.db.FamiliesTable
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.db.FamilyScopedAssignmentRepository
import no.pilot.barnehage.db.ParentsTable
import no.pilot.barnehage.db.TokenRepository
import no.pilot.barnehage.domain.AssignmentService
import no.pilot.barnehage.google.AccessTokenProvider
import no.pilot.barnehage.google.CalendarService
import no.pilot.barnehage.google.GoogleOAuthClient
import no.pilot.barnehage.google.GoogleOAuthConfig
import no.pilot.barnehage.plugins.configureSerialization
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssignmentNoteRoutesTest {
    private val database = Database.connect(
        url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:9876/barnehage",
        user = System.getenv("DATABASE_USER") ?: "postgres",
        password = System.getenv("DATABASE_PASSWORD") ?: "localdev",
        driver = "org.postgresql.Driver",
    )
    private lateinit var familyId: UUID
    private lateinit var otherFamilyId: UUID
    private lateinit var parentId: UUID
    private lateinit var assignmentId: UUID

    @BeforeTest
    fun setUp() {
        val families = FamilyRepository(database)
        val parent = families.createFamilyWithFirstParent("", UUID.randomUUID().toString(), "note-${UUID.randomUUID()}", "test@example.com", "Test")
        familyId = parent.familyId
        parentId = parent.id
        otherFamilyId = families.createFamilyWithFirstParent("", UUID.randomUUID().toString(), "other-note-${UUID.randomUUID()}", "other@example.com", "Other").familyId
        assignmentId = FamilyScopedAssignmentRepository(familyId, database)
            .insert(LocalDate.of(2026, 11, 9), "DROPOFF", parentId, "MANUAL", null)
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            val ids = listOf(familyId, otherFamilyId)
            AssignmentsTable.deleteWhere { Op.build { AssignmentsTable.familyId inList ids } }
            ParentsTable.deleteWhere { Op.build { ParentsTable.familyId inList ids } }
            FamiliesTable.deleteWhere { Op.build { FamiliesTable.id inList ids } }
        }
    }

    @Test
    fun `notater lagres valideres og avgrenses til innlogget familie`() = testApplication {
        val googleHttp = HttpClient()
        try {
            application {
                Env.overrideForTests("SESSION_COOKIE_SECURE", "false")
                configureSerialization()
                configureSessionAuth()
                val tokens = TokenRepository(TokenCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="), database)
                routing {
                    get("/test-login/{familyId}") {
                        call.sessions.set(UserSession(parentId.toString(), call.parameters["familyId"]!!))
                        call.respondText("ok")
                    }
                    assignmentRoutes(
                        FamilyRepository(database),
                        AssignmentService(),
                        CalendarService(googleHttp),
                        AccessTokenProvider(GoogleOAuthClient(googleHttp, GoogleOAuthConfig("", "", "")), tokens),
                        tokens,
                        database,
                    )
                }
            }
            val path = "/api/assignments/$assignmentId/note"
            assertEquals(HttpStatusCode.Unauthorized, client.put(path).status)
            val member = createClient { install(HttpCookies) }
            member.get("/test-login/$familyId")

            suspend fun save(body: String, target: String = path) = member.put(target) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            val response = save("""{"note":"  Ta med flere bleier\nHusk regntøy  "}""")
            assertEquals(HttpStatusCode.OK, response.status)
            val expected = "Ta med flere bleier\nHusk regntøy"
            assertEquals(expected, Json.parseToJsonElement(response.bodyAsText()).jsonObject["note"]?.jsonPrimitive?.content)
            val assignments = Json.parseToJsonElement(member.get("/api/assignments").bodyAsText()).jsonArray
            assertEquals(expected, assignments.single().jsonObject["note"]?.jsonPrimitive?.content)
            val reassigned = member.post("/api/assign") {
                contentType(ContentType.Application.Json)
                setBody("""{"date":"2026-11-09","type":"DROPOFF","parentId":"$parentId"}""")
            }
            assertEquals(HttpStatusCode.OK, reassigned.status)
            assertEquals(expected, Json.parseToJsonElement(reassigned.bodyAsText()).jsonObject["note"]?.jsonPrimitive?.content)

            assertEquals(HttpStatusCode.BadRequest, save("""{"note":"${"a".repeat(1001)}"}""").status)
            val repo = FamilyScopedAssignmentRepository(familyId, database)
            assertEquals(expected, repo.findById(assignmentId)?.note)
            assertEquals(HttpStatusCode.OK, save("""{"note":"${"a".repeat(1000)}"}""").status)
            assertEquals(HttpStatusCode.OK, save("""{"note":"  \n  "}""").status)
            assertNull(repo.findById(assignmentId)?.note)
            assertEquals(HttpStatusCode.OK, save("""{"note":null}""").status)
            assertEquals(HttpStatusCode.BadRequest, save("""{"note":"test"}""", "/api/assignments/invalid/note").status)
            assertEquals(HttpStatusCode.NotFound, save("""{"note":"test"}""", "/api/assignments/${UUID.randomUUID()}/note").status)

            member.get("/test-login/$otherFamilyId")
            assertEquals(HttpStatusCode.NotFound, save("""{"note":"Andre familie"}""").status)
            assertNull(repo.findById(assignmentId)?.note)
        } finally {
            googleHttp.close()
        }
    }
}
