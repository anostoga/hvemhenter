package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import no.pilot.barnehage.auth.SESSION_AUTH_NAME
import no.pilot.barnehage.auth.userSession
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.db.FamilyScopedAssignmentRepository
import org.jetbrains.exposed.sql.Database
import java.util.UUID

@Serializable
data class FamilyResponse(val id: String, val inviteCode: String?)

@Serializable
data class AddHelperRequest(val name: String, val avatar: String? = null)

@Serializable
data class HelperResponse(val id: String, val name: String, val avatar: String? = null)

/**
 * Innstillinger for den innloggede brukerens egen familie — `familyId` hentes
 * kun fra sesjonen, aldri fra klienten, samme mønster som AssignmentRoutes.
 * Kalender-tilknytning er nå PER FORELDER, ikke familie-delt — se CalendarRoutes.
 */
fun Route.familyRoutes(familyRepository: FamilyRepository, database: Database) {
    authenticate(SESSION_AUTH_NAME) {
        get("/api/family") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val family = familyRepository.findFamily(familyId)
                ?: return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("familie ikke funnet"))
            call.respond(FamilyResponse(id = family.id.toString(), inviteCode = family.inviteCode))
        }

        // "Hjelpere" — personer (typisk besteforeldre/slektninger) som kan tildeles
        // levering/henting akkurat som en innlogget forelder (se AssignmentRoutes/
        // effectiveParents og /api/parents, som returnerer begge typer), men som
        // ALDRI logger inn selv. Ingen Google-konto/kalender er mulig for disse.
        post("/api/family/helpers") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val request = call.receive<AddHelperRequest>()
            val trimmedName = request.name.trim()
            if (trimmedName.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("navn kan ikke være tomt"))
            }
            if (request.avatar != null && request.avatar !in ALLOWED_AVATARS) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig avatar"))
            }
            val helper = familyRepository.addHelper(familyId, trimmedName, request.avatar)
            call.respond(HttpStatusCode.Created, HelperResponse(id = helper.id.toString(), name = helper.name, avatar = helper.avatar))
        }

        delete("/api/family/helpers/{id}") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val helperId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig id"))

            // Avvis fjerning hvis hjelperen fortsatt har tildelinger — `parents.id`
            // har ingen `on delete cascade` fra `assignments`, så en rå sletting her
            // ville gitt en kryptisk FK-feil i stedet for en forståelig 409. Brukeren
            // må selv fjerne/omfordele tildelingene først (se /ukeplan).
            val assignmentRepo = FamilyScopedAssignmentRepository(familyId, database)
            if (assignmentRepo.hasAssignmentsForParent(helperId)) {
                return@delete call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("hjelperen har tildelinger — fjern eller omfordel disse først"),
                )
            }

            val removed = familyRepository.removeHelper(familyId, helperId)
            if (!removed) {
                return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("hjelper ikke funnet"))
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

