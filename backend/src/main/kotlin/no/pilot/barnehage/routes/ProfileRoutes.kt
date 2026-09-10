package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import no.pilot.barnehage.auth.SESSION_AUTH_NAME
import no.pilot.barnehage.auth.userSession
import no.pilot.barnehage.db.FamilyRepository
import java.util.UUID

val ALLOWED_AVATARS = setOf("🐻", "🦊", "🐰", "🐼", "🐨", "🐯", "🦁", "🐵", "🐶", "🐱", "🐸", "🦄")

@Serializable
data class ProfileResponse(val name: String, val avatar: String? = null)

@Serializable
data class UpdateProfileRequest(val name: String, val avatar: String? = null)

fun Route.profileRoutes(familyRepository: FamilyRepository) {
    authenticate(SESSION_AUTH_NAME) {
        get("/api/profile") {
            val session = call.userSession()!!
            val parent = familyRepository.findParent(UUID.fromString(session.parentId))
                ?: return@get call.respond(HttpStatusCode.InternalServerError, ErrorResponse("forelder ikke funnet"))
            call.respond(ProfileResponse(name = parent.name, avatar = parent.avatar))
        }

        put("/api/profile") {
            val session = call.userSession()!!
            val parentId = UUID.fromString(session.parentId)
            val request = call.receive<UpdateProfileRequest>()
            val trimmedName = request.name.trim()
            if (trimmedName.isBlank()) {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("navn kan ikke være tomt"))
            }
            if (request.avatar != null && request.avatar !in ALLOWED_AVATARS) {
                return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig avatar"))
            }
            familyRepository.updateProfile(parentId, trimmedName, request.avatar)
            call.respond(HttpStatusCode.OK, ProfileResponse(name = trimmedName, avatar = request.avatar))
        }
    }
}
