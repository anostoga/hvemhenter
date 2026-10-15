package no.pilot.barnehage.routes

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.pilot.barnehage.Env
import no.pilot.barnehage.auth.configureSessionAuth
import no.pilot.barnehage.crypto.TokenCipher
import no.pilot.barnehage.db.FamiliesTable
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.db.OnboardingRepository
import no.pilot.barnehage.db.ParentsTable
import no.pilot.barnehage.db.TokenRepository
import no.pilot.barnehage.plugins.configureSerialization
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dekker /velkommen-flyten i sin helhet: en ukjent Google-bruker har allerede fått en
 * oppstartsøkt (se AuthRoutes.startOnboarding) og velger her å opprette en ny familie eller bli
 * med i en eksisterende via kode. Ekte Google-callback bruker OnboardingRepository identisk
 * derfra — her testes HTTP-laget isolert, uten å gå via Google.
 */
class OnboardingRoutesTest {
    private val database = Database.connect(
        url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:9876/barnehage",
        user = System.getenv("DATABASE_USER") ?: "postgres",
        password = System.getenv("DATABASE_PASSWORD") ?: "localdev",
        driver = "org.postgresql.Driver",
    )
    private val cipher = TokenCipher(TokenCipher.generateKey())
    private val familyRepository = FamilyRepository(database)
    private val tokenRepository = TokenRepository(cipher, database)
    private val onboardingRepository = OnboardingRepository(cipher, database)
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

    private fun sub() = "onboarding-sub-${UUID.randomUUID()}".also { createdParentGoogleSubs.add(it) }

    private fun seedOnboardingSession(googleSub: String, email: String, name: String) =
        onboardingRepository.create(
            googleSub = googleSub,
            email = email,
            name = name,
            accessToken = "access-$googleSub",
            refreshToken = "refresh-$googleSub",
            tokenExpiresAt = LocalDateTime.now().plusHours(1),
        )

    private fun io.ktor.server.application.Application.setUpOnboardingRoutes() {
        Env.overrideForTests("SESSION_SIGNING_SECRET", "test-secret-onboarding")
        Env.overrideForTests("SESSION_COOKIE_SECURE", "false")
        Env.overrideForTests("FRONTEND_URL", TEST_FRONTEND_ORIGIN)
        configureSerialization()
        configureSessionAuth()
        routing { onboardingRoutes(onboardingRepository, familyRepository, tokenRepository) }
    }

    companion object {
        private const val TEST_FRONTEND_ORIGIN = "http://test.local"
    }

    @Test
    fun `fullt scenario - status, opprett familie, og dobbel-innsending`() = testApplication {
        application { setUpOnboardingRoutes() }
        val client = createClient { install(HttpCookies) }

        // Uten cookie: avvist
        val unauthenticated = client.get("/auth/onboarding/status")
        assertEquals(HttpStatusCode.Unauthorized, unauthenticated.status)

        val googleSub = sub()
        val handle = seedOnboardingSession(googleSub, "a@example.com", "Kari Nordmann")

        val status = client.get("/auth/onboarding/status") {
            header("Cookie", "bhg_onboarding=${handle.reference}")
        }
        assertEquals(HttpStatusCode.OK, status.status)
        val statusBody = Json.parseToJsonElement(status.bodyAsText()).jsonObject
        assertEquals("Kari Nordmann", statusBody["name"]!!.jsonPrimitive.content)
        val csrfToken = statusBody["csrfToken"]!!.jsonPrimitive.content
        assertEquals(csrfToken, handle.csrfToken)

        // Feil CSRF-token avvises
        val wrongCsrf = client.post("/auth/onboarding/create-family") {
            header("Cookie", "bhg_onboarding=${handle.reference}")
            header("Origin", TEST_FRONTEND_ORIGIN)
            contentType(ContentType.Application.Json)
            setBody("""{"csrfToken":"feil-token"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, wrongCsrf.status)

        // Riktig CSRF-token oppretter familien
        val created = client.post("/auth/onboarding/create-family") {
            header("Cookie", "bhg_onboarding=${handle.reference}")
            header("Origin", TEST_FRONTEND_ORIGIN)
            contentType(ContentType.Application.Json)
            setBody("""{"csrfToken":"$csrfToken"}""")
        }
        assertEquals(HttpStatusCode.OK, created.status)

        val parent = familyRepository.findParentByGoogleSub(googleSub)
        assertTrue(parent != null, "forelderen skal være opprettet")
        assertEquals(1, familyRepository.parentCount(parent!!.familyId))

        // Oppstartsøkten er engangsbruk: et andre forsøk (dobbel-innsending) finner ingen økt,
        // men det skal fortsatt ikke oppstå noen duplikat-familie.
        val secondAttempt = client.post("/auth/onboarding/create-family") {
            header("Cookie", "bhg_onboarding=${handle.reference}")
            header("Origin", TEST_FRONTEND_ORIGIN)
            contentType(ContentType.Application.Json)
            setBody("""{"csrfToken":"$csrfToken"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, secondAttempt.status)
        assertEquals(1, familyRepository.parentCount(parent.familyId))
    }

    @Test
    fun `bli med med gyldig kode kobler forelder 2 til riktig familie`() = testApplication {
        application { setUpOnboardingRoutes() }
        val client = createClient { install(HttpCookies) }

        val parent1Sub = sub()
        val parent1 = familyRepository.createFamilyWithFirstParent("", "onboarding-invite-1", parent1Sub, "a@example.com", "A")

        val parent2Sub = sub()
        val handle = seedOnboardingSession(parent2Sub, "b@example.com", "B")

        val response = client.post("/auth/onboarding/join-family") {
            header("Cookie", "bhg_onboarding=${handle.reference}")
            header("Origin", TEST_FRONTEND_ORIGIN)
            contentType(ContentType.Application.Json)
            setBody("""{"code":"onboarding-invite-1","csrfToken":"${handle.csrfToken}"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val parent2 = familyRepository.findParentByGoogleSub(parent2Sub)
        assertEquals(parent1.familyId, parent2?.familyId)
        assertEquals(2, familyRepository.parentCount(parent1.familyId))
    }

    @Test
    fun `bli med med ugyldig kode gir 400 og oppretter ingen forelder`() = testApplication {
        application { setUpOnboardingRoutes() }
        val client = createClient { install(HttpCookies) }

        val googleSub = sub()
        val handle = seedOnboardingSession(googleSub, "c@example.com", "C")

        val response = client.post("/auth/onboarding/join-family") {
            header("Cookie", "bhg_onboarding=${handle.reference}")
            header("Origin", TEST_FRONTEND_ORIGIN)
            contentType(ContentType.Application.Json)
            setBody("""{"code":"finnes-ikke","csrfToken":"${handle.csrfToken}"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNull(familyRepository.findParentByGoogleSub(googleSub))
    }

    @Test
    fun `bli med nar familien allerede har 2 foreldre gir 400`() = testApplication {
        application { setUpOnboardingRoutes() }
        val client = createClient { install(HttpCookies) }

        val parent1Sub = sub()
        familyRepository.createFamilyWithFirstParent("", "onboarding-invite-full", parent1Sub, "a@example.com", "A")
        val parent2Sub = sub()
        familyRepository.joinFamilyWithInviteCode("onboarding-invite-full", parent2Sub, "b@example.com", "B")

        val parent3Sub = sub()
        val handle = seedOnboardingSession(parent3Sub, "d@example.com", "D")
        val response = client.post("/auth/onboarding/join-family") {
            header("Cookie", "bhg_onboarding=${handle.reference}")
            header("Origin", TEST_FRONTEND_ORIGIN)
            contentType(ContentType.Application.Json)
            setBody("""{"code":"onboarding-invite-full","csrfToken":"${handle.csrfToken}"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNull(familyRepository.findParentByGoogleSub(parent3Sub))
    }
}
