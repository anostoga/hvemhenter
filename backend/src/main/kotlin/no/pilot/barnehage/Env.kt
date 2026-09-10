package no.pilot.barnehage

import io.github.cdimascio.dotenv.dotenv

object Env {
    private val dotenv = dotenv {
        ignoreIfMissing = true
        ignoreIfMalformed = true
    }

    private val testOverrides = mutableMapOf<String, String>()

    fun get(key: String): String? = testOverrides[key] ?: dotenv[key]

    fun overrideForTests(key: String, value: String) {
        testOverrides[key] = value
    }
}
