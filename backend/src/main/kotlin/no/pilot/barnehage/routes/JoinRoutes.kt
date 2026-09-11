package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import no.pilot.barnehage.Env
import no.pilot.barnehage.crypto.StateSigner
import no.pilot.barnehage.db.AdminRepository
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.google.GoogleOAuthClient
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class JoinErrorResponse(val error: String)

private val joinAttempts = ConcurrentHashMap<String, MutableList<Instant>>()
private const val MAX_ATTEMPTS_PER_HOUR = 5

private fun rateLimited(ip: String): Boolean {
    val now = Instant.now()
    val attempts = joinAttempts.computeIfAbsent(ip) { mutableListOf() }
    synchronized(attempts) {
        attempts.removeAll { it.isBefore(now.minusSeconds(3600)) }
        if (attempts.size >= MAX_ATTEMPTS_PER_HOUR) return true
        attempts.add(now)
    }
    return false
}

fun Route.joinRoutes(
    oauthClient: GoogleOAuthClient,
    stateSigner: StateSigner,
    familyRepository: FamilyRepository,
    adminRepository: AdminRepository? = null,
) {
    get("/join/start") {
        val code = call.parameters["code"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, JoinErrorResponse("code mangler"))

        val ip = call.request.origin.remoteHost
        if (rateLimited(ip)) {
            return@get call.respond(HttpStatusCode.TooManyRequests, JoinErrorResponse("for mange forsøk, prøv igjen senere"))
        }

        val familyCreationCode = Env.get("FAMILY_CREATION_CODE")
        val looksValid = code == familyCreationCode ||
            familyRepository.findFamilyByInviteCode(code) != null ||
            (adminRepository != null && isAdminCodeUsable(adminRepository, familyRepository, code))
        if (!looksValid) {
            return@get call.respond(HttpStatusCode.BadRequest, JoinErrorResponse("ugyldig kode"))
        }

        val state = stateSigner.sign("join:$code")
        call.respondRedirect(oauthClient.buildAuthorizeUrl(state))
    }
}

fun handleJoin(
    code: String,
    googleSub: String,
    email: String,
    name: String,
    familyRepository: FamilyRepository,
    adminRepository: AdminRepository? = null,
): String? {
    val familyCreationCode = Env.get("FAMILY_CREATION_CODE")

    if (familyCreationCode != null && code == familyCreationCode) {

        val existingFamily = familyRepository.findFamilyWithRoom()
        if (existingFamily != null) {
            val joined = familyRepository.addParentToFamily(existingFamily.id, googleSub, email, name)
            if (joined != null) return joined.familyId.toString()
        }

        val inviteCode = generateInviteCode()
        val parent = familyRepository.createFamilyWithFirstParent(
            sharedCalendarId = "",
            inviteCode = inviteCode,
            googleSub = googleSub,
            email = email,
            name = name,
        )
        return parent.familyId.toString()
    }

    val existingParent = familyRepository.findParentByGoogleSub(googleSub)
    if (existingParent != null) return existingParent.familyId.toString()

    val adminCode = adminRepository?.findInviteCode(code)
    if (adminCode != null) {
        val alreadyUsedFamilyId = adminCode.usedByFamilyId
        if (alreadyUsedFamilyId != null) {
            // Koden er brukt før — la en andre forelder bli med i samme familie hvis det er plass.
            val joined = familyRepository.addParentToFamily(alreadyUsedFamilyId, googleSub, email, name)
            return joined?.familyId?.toString()
        }

        val inviteCode = generateInviteCode()
        val parent = familyRepository.createFamilyWithFirstParent(
            sharedCalendarId = "",
            inviteCode = inviteCode,
            googleSub = googleSub,
            email = email,
            name = name,
        )
        adminRepository.markInviteCodeUsed(adminCode.id, parent.familyId)
        return parent.familyId.toString()
    }

    val joined = familyRepository.joinFamilyWithInviteCode(code, googleSub, email, name) ?: return null
    return joined.familyId.toString()
}

private fun isAdminCodeUsable(adminRepository: AdminRepository, familyRepository: FamilyRepository, code: String): Boolean {
    val adminCode = adminRepository.findInviteCode(code) ?: return false
    val usedByFamilyId = adminCode.usedByFamilyId ?: return true
    return familyRepository.parentCount(usedByFamilyId) < 2
}

private fun generateInviteCode(): String {
    val bytes = ByteArray(9)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
