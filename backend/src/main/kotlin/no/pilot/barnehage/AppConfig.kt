package no.pilot.barnehage

import no.pilot.barnehage.domain.Parent

/**
 * Statisk konfigurasjon for de to foreldrene og delt kalender. Holdes enkelt via
 * miljøvariabler siden dette er et to-brukers hobbyprosjekt uten behov for en
 * egen "administrer brukere"-flate.
 */
data class AppConfig(
    val parents: List<Parent>,
    val sharedCalendarId: String,
    val tokenEncryptionKey: String,
    val stateSigningSecret: String,
) {
    companion object {
        fun fromEnv(): AppConfig = AppConfig(
            parents = listOf(
                Parent(
                    id = Env.get("PARENT_1_ID") ?: "parent1",
                    name = Env.get("PARENT_1_NAME") ?: "Forelder 1",
                ),
                Parent(
                    id = Env.get("PARENT_2_ID") ?: "parent2",
                    name = Env.get("PARENT_2_NAME") ?: "Forelder 2",
                ),
            ),
            sharedCalendarId = Env.get("SHARED_CALENDAR_ID") ?: error("SHARED_CALENDAR_ID mangler"),
            tokenEncryptionKey = Env.get("TOKEN_ENCRYPTION_KEY") ?: error("TOKEN_ENCRYPTION_KEY mangler (generer med TokenCipher.generateKey())"),
            stateSigningSecret = Env.get("STATE_SIGNING_SECRET") ?: error("STATE_SIGNING_SECRET mangler"),
        )
    }
}
