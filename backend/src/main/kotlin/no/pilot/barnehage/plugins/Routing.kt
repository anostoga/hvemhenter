package no.pilot.barnehage.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import no.pilot.barnehage.Env
import no.pilot.barnehage.crypto.StateSigner
import no.pilot.barnehage.crypto.TokenCipher
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.db.TokenRepository
import no.pilot.barnehage.db.AdminRepository
import no.pilot.barnehage.db.FamilyActivityRepository
import no.pilot.barnehage.db.OnboardingRepository
import no.pilot.barnehage.domain.AssignmentService
import no.pilot.barnehage.google.AccessTokenProvider
import no.pilot.barnehage.google.CalendarService
import no.pilot.barnehage.google.GoogleOAuthClient
import no.pilot.barnehage.google.GoogleOAuthConfig
import no.pilot.barnehage.routes.assignmentRoutes
import no.pilot.barnehage.routes.assignmentConfirmationRoutes
import no.pilot.barnehage.routes.accountRoutes
import no.pilot.barnehage.routes.adminRoutes
import no.pilot.barnehage.routes.authRoutes
import no.pilot.barnehage.routes.calendarRoutes
import no.pilot.barnehage.routes.familyRoutes
import no.pilot.barnehage.routes.familyActivityRoutes
import no.pilot.barnehage.routes.joinRoutes
import no.pilot.barnehage.routes.onboardingRoutes
import no.pilot.barnehage.routes.profileRoutes
import no.pilot.barnehage.notification.ResendHelperAssignmentNotifier
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class HealthResponse(val status: String)

fun Application.configureRouting(database: Database) {
    val httpClient = createGoogleHttpClient()
    val tokenEncryptionKey = Env.get("TOKEN_ENCRYPTION_KEY") ?: error("TOKEN_ENCRYPTION_KEY mangler")
    val stateSigningSecret = Env.get("STATE_SIGNING_SECRET") ?: error("STATE_SIGNING_SECRET mangler")
    val tokenCipher = TokenCipher(tokenEncryptionKey)
    val stateSigner = StateSigner(stateSigningSecret)
    val tokenRepository = TokenRepository(tokenCipher, database)
    val oauthClient = GoogleOAuthClient(httpClient, GoogleOAuthConfig.fromEnv())
    val calendarService = CalendarService(httpClient)
    val accessTokenProvider = AccessTokenProvider(oauthClient, tokenRepository)
    val assignmentService = AssignmentService()
    val familyRepository = FamilyRepository(database)
    val adminRepository = AdminRepository(database)
    val onboardingRepository = OnboardingRepository(tokenCipher, database)
    val helperAssignmentNotifier = ResendHelperAssignmentNotifier.fromEnvironment(httpClient)
    val frontendSuccessUrl = Env.get("FRONTEND_URL") ?: "/"
    val frontendOnboardingUrl = Env.get("FRONTEND_URL")?.let { "$it/velkommen" } ?: "/velkommen"

    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, HealthResponse(status = "ok"))
        }

        get("/ready") {
            val dbOk = runCatching { transaction(database) { exec("SELECT 1") } }.isSuccess
            if (dbOk) {
                call.respond(HttpStatusCode.OK, HealthResponse(status = "ready"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse(status = "database utilgjengelig"))
            }
        }

        authRoutes(oauthClient, stateSigner, tokenRepository, onboardingRepository, frontendSuccessUrl, frontendOnboardingUrl, familyRepository)
        onboardingRoutes(onboardingRepository, familyRepository, tokenRepository)
        joinRoutes(oauthClient, stateSigner, familyRepository)
        familyRoutes(familyRepository, database)
        familyActivityRoutes(familyRepository, FamilyActivityRepository(database))
        calendarRoutes(familyRepository, accessTokenProvider, calendarService)
        profileRoutes(familyRepository)
        assignmentRoutes(familyRepository, assignmentService, calendarService, accessTokenProvider, tokenRepository, database)
        assignmentConfirmationRoutes(familyRepository, database, helperAssignmentNotifier)
        accountRoutes(familyRepository, tokenRepository, oauthClient, calendarService, accessTokenProvider, database)
        adminRoutes(familyRepository, adminRepository)
    }
}
