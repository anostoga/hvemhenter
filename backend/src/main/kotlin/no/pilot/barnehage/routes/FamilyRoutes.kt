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
import io.ktor.server.routing.put
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
data class AddHelperRequest(val name: String, val avatar: String? = null, val email: String? = null)

@Serializable
data class UpdateHelperRequest(val name: String, val avatar: String? = null, val email: String? = null)

@Serializable
data class HelperResponse(val id: String, val name: String, val avatar: String? = null, val email: String? = null)

fun Route.familyRoutes(familyRepository: FamilyRepository, database: Database) {
    authenticate(SESSION_AUTH_NAME) {
        get("/api/family") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val family = familyRepository.findFamily(familyId)
                ?: return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("familie ikke funnet"))
            call.respond(FamilyResponse(id = family.id.toString(), inviteCode = family.inviteCode))
        }

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
            val email = normalizedHelperEmail(request.email)
                ?: if (request.email.isNullOrBlank()) null else return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig e-postadresse"))
            val helper = familyRepository.addHelper(familyId, trimmedName, request.avatar, email)
            call.respond(HttpStatusCode.Created, helper.toHelperResponse())
        }

        put("/api/family/helpers/{id}") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val helperId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig id"))
            val request = call.receive<UpdateHelperRequest>()
            val trimmedName = request.name.trim()
            if (trimmedName.isBlank()) {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("navn kan ikke være tomt"))
            }
            if (request.avatar != null && request.avatar !in ALLOWED_AVATARS) {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig avatar"))
            }
            val email = normalizedHelperEmail(request.email)
                ?: if (request.email.isNullOrBlank()) null else return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig e-postadresse"))

            val updated = familyRepository.updateHelper(familyId, helperId, trimmedName, request.avatar, email)
                ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("hjelper ikke funnet"))
            call.respond(updated.toHelperResponse())
        }

        delete("/api/family/helpers/{id}") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val helperId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig id"))

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

private fun normalizedHelperEmail(email: String?): String? =
    email?.trim()?.takeIf { it.isNotEmpty() }?.takeIf { it.length <= 254 && EMAIL_PATTERN.matches(it) }?.lowercase()

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+\$")

private fun no.pilot.barnehage.db.ParentRecord.toHelperResponse() =
    HelperResponse(id = id.toString(), name = name, avatar = avatar, email = email)
