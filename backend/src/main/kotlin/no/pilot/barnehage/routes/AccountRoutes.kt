package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import no.pilot.barnehage.auth.SESSION_AUTH_NAME
import no.pilot.barnehage.auth.UserSession
import no.pilot.barnehage.auth.userSession
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.db.FamilyScopedAssignmentRepository
import no.pilot.barnehage.db.TokenRepository
import no.pilot.barnehage.google.AccessTokenProvider
import no.pilot.barnehage.google.CalendarService
import no.pilot.barnehage.google.GoogleOAuthClient
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

private val logger = LoggerFactory.getLogger("no.pilot.barnehage.routes.AccountRoutes")

fun Route.accountRoutes(
    familyRepository: FamilyRepository,
    tokenRepository: TokenRepository,
    oauthClient: GoogleOAuthClient,
    calendarService: CalendarService,
    accessTokenProvider: AccessTokenProvider,
    database: Database,
) {
    authenticate(SESSION_AUTH_NAME) {
        delete("/api/account") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val parentId = UUID.fromString(session.parentId)

            val parent = familyRepository.findParent(parentId)
                ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("konto ikke funnet"))
            if (parent.isHelper) {

                return@delete call.respond(HttpStatusCode.Forbidden, ErrorResponse("hjelpere kan ikke slette konto"))
            }

            val storedToken = tokenRepository.find(parentId)
            val accessToken = accessTokenProvider.getValidAccessToken(parentId)

            val repo = FamilyScopedAssignmentRepository(familyId, database)
            val futureAssignments = repo.findFutureForParent(parentId, LocalDate.now())
            for (assignment in futureAssignments) {
                if (assignment.googleEventId != null && accessToken != null && !parent.calendarId.isNullOrBlank()) {
                    calendarService.deleteEvent(accessToken, parent.calendarId, assignment.googleEventId)
                }
                repo.delete(assignment.id)
            }

            val isLastParent = familyRepository.parentCount(familyId) <= 1
            if (isLastParent) {
                familyRepository.deleteFamily(familyId)
            } else {
                familyRepository.removeParent(familyId, parentId)
            }

            val revokeToken = storedToken?.refreshToken ?: storedToken?.accessToken
            if (revokeToken != null) {
                runCatching { oauthClient.revokeToken(revokeToken) }
                    .onFailure { logger.warn("Kunne ikke tilbakekalle Google-token for slettet konto $parentId", it) }
            }

            call.sessions.clear<UserSession>()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
