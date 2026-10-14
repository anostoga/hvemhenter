package no.pilot.barnehage.notification

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import no.pilot.barnehage.Env
import kotlinx.coroutines.CancellationException

interface HelperAssignmentNotifier {
    val isConfigured: Boolean

    suspend fun sendConfirmationRequest(
        email: String,
        helperName: String,
        date: String,
        assignmentType: String,
        token: String,
    )
}

class HelperNotificationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ResendHelperAssignmentNotifier(
    private val httpClient: HttpClient,
    private val apiKey: String?,
    private val fromAddress: String?,
    private val frontendUrl: String?,
) : HelperAssignmentNotifier {
    override val isConfigured: Boolean
        get() = !apiKey.isNullOrBlank() &&
            !fromAddress.isNullOrBlank() &&
            frontendUrl?.let { it.startsWith("https://") || it.startsWith("http://localhost") } == true

    override suspend fun sendConfirmationRequest(
        email: String,
        helperName: String,
        date: String,
        assignmentType: String,
        token: String,
    ) {
        val apiKey = apiKey?.takeIf(String::isNotBlank)
            ?: throw HelperNotificationException("Varsling av hjelpere er ikke konfigurert")
        val fromAddress = fromAddress?.takeIf(String::isNotBlank)
            ?: throw HelperNotificationException("Varsling av hjelpere er ikke konfigurert")
        val frontendUrl = frontendUrl
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://localhost") }
            ?: throw HelperNotificationException("FRONTEND_URL mangler eller er ugyldig")

        val responseUrl = "${frontendUrl.trimEnd('/')}/bekreft/$token"
        try {
            val response = httpClient.post("https://api.resend.com/emails") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(
                    ResendEmailRequest(
                        from = fromAddress,
                        to = listOf(email),
                        subject = "$assignmentType $date – bekreft oppgaven",
                        html = emailHtml(helperName, assignmentType, date, responseUrl),
                        text = "Hei $helperName! Du er satt opp på $assignmentType $date. " +
                            "Svar her: $responseUrl",
                    ),
                )
            }
            if (!response.status.isSuccess()) {
                throw HelperNotificationException("E-postleverandøren avviste forespørselen (${response.status.value})")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HelperNotificationException) {
            throw e
        } catch (e: Exception) {
            throw HelperNotificationException("Kunne ikke kontakte e-postleverandøren", e)
        }
    }

    private fun emailHtml(helperName: String, assignmentType: String, date: String, responseUrl: String): String {
        val safeName = helperName.htmlEscape()
        val safeType = assignmentType.htmlEscape()
        val safeDate = date.htmlEscape()
        val safeUrl = responseUrl.htmlEscape()
        return """
            <p>Hei $safeName!</p>
            <p>Du er satt opp på $safeType $safeDate.</p>
            <p><a href="$safeUrl">Se oppgaven og svar</a></p>
        """.trimIndent()
    }

    private fun String.htmlEscape(): String = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    companion object {
        fun fromEnvironment(httpClient: HttpClient) = ResendHelperAssignmentNotifier(
            httpClient = httpClient,
            apiKey = Env.get("RESEND_API_KEY"),
            fromAddress = Env.get("HELPER_EMAIL_FROM"),
            frontendUrl = Env.get("FRONTEND_URL")?.trimEnd('/'),
        )
    }
}

@Serializable
private data class ResendEmailRequest(
    val from: String,
    val to: List<String>,
    val subject: String,
    val html: String,
    val text: String,
)
