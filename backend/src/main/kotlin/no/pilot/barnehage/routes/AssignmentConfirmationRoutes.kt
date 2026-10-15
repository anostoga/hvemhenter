package no.pilot.barnehage.routes

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import no.pilot.barnehage.auth.SESSION_AUTH_NAME
import no.pilot.barnehage.auth.userSession
import no.pilot.barnehage.crypto.AssignmentConfirmationTokens
import no.pilot.barnehage.db.AssignmentConfirmationRepository
import no.pilot.barnehage.db.ConfirmationAnswerResult
import no.pilot.barnehage.db.FamilyRepository
import no.pilot.barnehage.db.FamilyScopedAssignmentRepository
import no.pilot.barnehage.domain.ConfirmationStatus
import no.pilot.barnehage.notification.HelperAssignmentNotifier
import no.pilot.barnehage.notification.HelperNotificationException
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val logger = LoggerFactory.getLogger("no.pilot.barnehage.routes.AssignmentConfirmationRoutes")

@Serializable
data class AssignmentConfirmationResponse(
    val helperName: String,
    val date: String,
    val type: String,
    val status: ConfirmationStatus,
)

@Serializable
data class AssignmentConfirmationAnswerRequest(val answer: ConfirmationStatus)

@Serializable
data class AssignmentConfirmationLinkResponse(val path: String)

@Serializable
data class AssignmentConfirmationEmailRequest(val token: String? = null)

fun Route.assignmentConfirmationRoutes(
    familyRepository: FamilyRepository,
    database: Database,
    notifier: HelperAssignmentNotifier,
) {
    get("/api/assignment-confirmations/{token}") {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        val token = call.parameters["token"]?.takeIf(TOKEN_PATTERN::matches)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("bekreftelseslenken er ugyldig eller utløpt"))
        val confirmation = AssignmentConfirmationRepository(database).find(AssignmentConfirmationTokens.hash(token))
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("bekreftelseslenken er ugyldig eller utløpt"))
        call.respond(
            AssignmentConfirmationResponse(
                helperName = confirmation.helperName,
                date = confirmation.date.toString(),
                type = confirmation.type,
                status = confirmation.status,
            ),
        )
    }

    post("/api/assignment-confirmations/{token}") {
        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        val token = call.parameters["token"]?.takeIf(TOKEN_PATTERN::matches)
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("bekreftelseslenken er ugyldig eller utløpt"))
        val answer = call.receive<AssignmentConfirmationAnswerRequest>().answer
        if (answer != ConfirmationStatus.ACCEPTED && answer != ConfirmationStatus.DECLINED) {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("svaret må være godta eller avslå"))
        }

        val confirmationRepository = AssignmentConfirmationRepository(database)
        val tokenHash = AssignmentConfirmationTokens.hash(token)
        val confirmation = confirmationRepository.find(tokenHash)
        when (confirmationRepository.answer(tokenHash, answer)) {
            ConfirmationAnswerResult.UPDATED -> {
                if (confirmation != null) {
                    notifyFamilyOfAnswer(
                        familyRepository = familyRepository,
                        notifier = notifier,
                        familyId = confirmation.familyId,
                        helperName = confirmation.helperName,
                        date = confirmation.date.toString(),
                        type = confirmation.type,
                        answer = answer,
                    )
                }
                call.respond(HttpStatusCode.OK, mapOf("status" to answer.name))
            }
            ConfirmationAnswerResult.ALREADY_RECORDED ->
                call.respond(HttpStatusCode.OK, mapOf("status" to answer.name))
            ConfirmationAnswerResult.INVALID_OR_EXPIRED ->
                call.respond(HttpStatusCode.NotFound, ErrorResponse("bekreftelseslenken er ugyldig eller utløpt"))
            ConfirmationAnswerResult.CONFLICT ->
                call.respond(HttpStatusCode.Conflict, ErrorResponse("du har allerede svart på denne oppgaven"))
        }
    }

    authenticate(SESSION_AUTH_NAME) {
        post("/api/assignments/{id}/confirmation-answer") {
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val assignmentId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig id"))
            val answer = call.receive<AssignmentConfirmationAnswerRequest>().answer
            if (answer != ConfirmationStatus.ACCEPTED && answer != ConfirmationStatus.DECLINED) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("svaret må være godtatt eller avslått"))
            }

            val assignmentRepo = FamilyScopedAssignmentRepository(familyId, database)
            val assignment = assignmentRepo.findById(assignmentId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("tildeling ikke funnet"))
            if (assignment.confirmationStatus != ConfirmationStatus.PENDING) {
                return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("tildelingen venter ikke på svar"))
            }
            val helper = familyRepository.findParent(assignment.parentId)
                ?.takeIf { it.familyId == familyId && it.isHelper }
                ?: return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("tildelingen gjelder ikke en hjelper"))

            when (assignmentRepo.answerPendingConfirmation(assignmentId, assignment.parentId, answer)) {
                ConfirmationAnswerResult.UPDATED -> {
                    notifyFamilyOfAnswer(
                        familyRepository = familyRepository,
                        notifier = notifier,
                        familyId = familyId,
                        helperName = helper.name,
                        date = assignment.date.toString(),
                        type = assignment.type,
                        answer = answer,
                    )
                    call.respond(HttpStatusCode.OK, mapOf("status" to answer.name))
                }
                ConfirmationAnswerResult.ALREADY_RECORDED ->
                    call.respond(HttpStatusCode.OK, mapOf("status" to answer.name))
                else -> call.respond(HttpStatusCode.Conflict, ErrorResponse("tildelingen er allerede besvart"))
            }
        }
    }

    authenticate(SESSION_AUTH_NAME) {
        post("/api/assignments/{id}/confirmation-link") {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val assignmentId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig id"))
            val assignmentRepo = FamilyScopedAssignmentRepository(familyId, database)
            val assignment = assignmentRepo.findById(assignmentId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("tildeling ikke funnet"))
            if (assignment.confirmationStatus != ConfirmationStatus.PENDING) {
                return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("tildelingen venter ikke på svar"))
            }
            val helper = familyRepository.findParent(assignment.parentId)
                ?.takeIf { it.familyId == familyId && it.isHelper }
                ?: return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("tildelingen gjelder ikke en hjelper"))

            val token = AssignmentConfirmationTokens.generate()
            val expiresAt = Instant.now().plus(7, ChronoUnit.DAYS)
            if (!assignmentRepo.replaceConfirmationToken(assignmentId, token.hash, expiresAt)) {
                return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("tildelingen kan ikke varsles lenger"))
            }

            call.respond(
                HttpStatusCode.Created,
                AssignmentConfirmationLinkResponse(path = confirmationPath(token.value)),
            )
        }

        post("/api/assignments/{id}/confirmation") {
            val request = call.receive<AssignmentConfirmationEmailRequest>()
            val session = call.userSession()!!
            val familyId = UUID.fromString(session.familyId)
            val assignmentId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("ugyldig id"))
            val assignmentRepo = FamilyScopedAssignmentRepository(familyId, database)
            val assignment = assignmentRepo.findById(assignmentId)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("tildeling ikke funnet"))
            if (assignment.confirmationStatus != ConfirmationStatus.PENDING) {
                return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("tildelingen venter ikke på svar"))
            }
            if (assignment.confirmationEmailSent) {
                return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("bekreftelses-e-posten er allerede sendt"))
            }
            val helper = familyRepository.findParent(assignment.parentId)
                ?.takeIf { it.familyId == familyId && it.isHelper }
                ?: return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("tildelingen gjelder ikke en hjelper"))
            val email = helper.email
                ?: return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("hjelperen mangler e-postadresse"))
            if (!notifier.isConfigured) {
                return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("e-postvarsling er ikke konfigurert"))
            }

            val suppliedToken = request.token
            if (suppliedToken != null && !TOKEN_PATTERN.matches(suppliedToken)) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("bekreftelseslenken er ugyldig"))
            }
            val token = if (suppliedToken == null) {
                AssignmentConfirmationTokens.generate().also { generated ->
                    val expiresAt = Instant.now().plus(7, ChronoUnit.DAYS)
                    if (!assignmentRepo.updateConfirmationToken(assignmentId, generated.hash, expiresAt)) {
                        return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("tildelingen kan ikke varsles lenger"))
                    }
                }
            } else {
                val hash = AssignmentConfirmationTokens.hash(suppliedToken)
                if (!assignmentRepo.hasActiveConfirmationToken(assignmentId, hash)) {
                    return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("bekreftelseslenken er ikke lenger gyldig"))
                }
                no.pilot.barnehage.crypto.AssignmentConfirmationToken(suppliedToken, hash)
            }
            if (!assignmentRepo.markConfirmationEmailSent(assignmentId, token.hash)) {
                if (suppliedToken == null) {
                    assignmentRepo.clearConfirmationToken(assignmentId, token.hash)
                }
                return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("bekreftelses-e-posten er allerede sendt"))
            }
            try {
                notifier.sendConfirmationRequest(
                    email = email,
                    helperName = helper.name,
                    date = assignment.date.toString(),
                    assignmentType = assignmentLabel(assignment.type),
                    token = token.value,
                )
            } catch (e: HelperNotificationException) {
                if (suppliedToken == null) {
                    assignmentRepo.clearConfirmationToken(assignmentId, token.hash)
                } else {
                    assignmentRepo.clearConfirmationEmailSent(assignmentId, token.hash)
                }
                logger.error("Kunne ikke sende bekreftelsesforespørsel for tildeling $assignmentId", e)
                return@post call.respond(HttpStatusCode.BadGateway, ErrorResponse("kunne ikke sende e-post, prøv igjen"))
            }
            call.respond(HttpStatusCode.Accepted)
        }
    }
}

private val TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")

private suspend fun notifyFamilyOfAnswer(
    familyRepository: FamilyRepository,
    notifier: HelperAssignmentNotifier,
    familyId: UUID,
    helperName: String,
    date: String,
    type: String,
    answer: ConfirmationStatus,
) {
    val recipientEmails = familyRepository.findParents(familyId)
        .filter { !it.isHelper }
        .mapNotNull { it.email?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
    for (email in recipientEmails) {
        try {
            notifier.sendAnswerNotification(
                email = email,
                helperName = helperName,
                date = date,
                assignmentType = assignmentLabel(type),
                answer = if (answer == ConfirmationStatus.ACCEPTED) "godtatt" else "avslått",
            )
        } catch (e: HelperNotificationException) {
            logger.error("Kunne ikke varsle forelder om svar på tildeling", e)
        }
    }
}

internal fun confirmationPath(token: String) = "/bekreft/$token"

internal fun assignmentLabel(type: String): String = when (type) {
    "DROPOFF" -> "levering"
    "PICKUP" -> "henting"
    else -> "oppgave"
}
