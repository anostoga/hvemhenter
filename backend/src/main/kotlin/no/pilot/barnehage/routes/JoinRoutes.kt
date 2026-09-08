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

/**
 * Familieopprettelse/invitasjon. `code` (enten FAMILY_CREATION_CODE eller en
 * familie sin invite_code) sendes gjennom OAuth-`state` slik at vi kan avgjøre
 * hva innloggingen skal gjøre i callback — uten server-side sesjon FØR innlogging.
 *
 * Rate-limiting: enkel in-memory teller per IP (holdbart nok for et hobbyprosjekt
 * med én instans; hadde trengt delt lagring — f.eks. Postgres eller Redis —
 * ved flere Fly-maskiner samtidig).
 */
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

        // Rask, ufarlig forhåndssjekk (unngår en unødvendig Google-runde for åpenbart
        // ugyldige koder). Selve autorativ validering skjer likevel i handleJoin()
        // etter innlogging — denne sjekken er kun en snarvei, ikke sikkerhetsgrensen.
        val familyCreationCode = Env.get("FAMILY_CREATION_CODE")
        val looksValid = code == familyCreationCode ||
            familyRepository.findFamilyByInviteCode(code) != null ||
            adminRepository?.findUnusedInviteCode(code) != null
        if (!looksValid) {
            return@get call.respond(HttpStatusCode.BadRequest, JoinErrorResponse("ugyldig kode"))
        }

        // `state` her har samme form som i AuthRoutes (parentId-feltet gjenbrukes til
        // å bære join-koden), signert/HMAC-beskyttet — koden kan derfor ikke endres
        // av klienten mellom /join/start og /auth/google/callback.
        val state = stateSigner.sign("join:$code")
        call.respondRedirect(oauthClient.buildAuthorizeUrl(state))
    }
}

/**
 * Kalles fra AuthRoutes sin callback når `state` starter med "join:" — dvs. når
 * innloggingen kom fra /join/start, ikke fra en allerede-tilkoblet forelder.
 * Validerer koden (FAMILY_CREATION_CODE eller en families invite_code, ALDRI logget)
 * og oppretter/kobler forelderen. familyId legges i sesjonen etterpå (se AuthRoutes).
 *
 * Returnerer familyId ved suksess, eller null ved ugyldig kode/full familie —
 * responsen skal IKKE avsløre hvilken av de to årsakene det var (unngå å lekke
 * hvorvidt en kode "nesten" var gyldig).
 */
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
        // Samme kode brukes av begge foreldre for å opprette/bli med i DIN familie
        // (hobbyprosjekt, kun én administrert familie er ventet normalt) — hvis en
        // familie med ledig plass allerede finnes, bli med i den i stedet for å
        // opprette en ny (ellers ender forelder #2 opp i en egen, tom familie).
        val existingFamily = familyRepository.findFamilyWithRoom()
        if (existingFamily != null) {
            val joined = familyRepository.addParentToFamily(existingFamily.id, googleSub, email, name)
            if (joined != null) return joined.familyId.toString()
        }

        // Ny familie. Delt kalender settes til en tom placeholder her — forelderen
        // må oppgi faktisk kalender-ID etterpå (ikke del av denne minimale flyten).
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

    // Admin-generert engangskode (se AdminRoutes/AdminRepository) — OPPRETTER alltid
    // en helt ny familie (i motsetning til FAMILY_CREATION_CODE, blir den ALDRI
    // gjenbrukt av forelder #2 for en "familie med ledig plass"), og markeres brukt
    // med det samme slik at den ikke kan gjenbrukes (engangsbruk, samme mønster som
    // families.invite_code).
    val adminCode = adminRepository?.findUnusedInviteCode(code)
    if (adminCode != null) {
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

private fun generateInviteCode(): String {
    val bytes = ByteArray(9)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
