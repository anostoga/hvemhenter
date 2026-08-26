package no.pilot.barnehage

import io.github.cdimascio.dotenv.dotenv

/**
 * Leser miljøvariabler fra en lokal `.env`-fil i tillegg til ekte OS-miljøvariabler.
 * Ekte miljøvariabler har alltid forrang over `.env`-verdier (standardoppførsel i
 * dotenv-kotlin), så produksjon/CI (uten `.env`-fil) er upåvirket av dette.
 * `.env` er kun en lokal utviklingsbekvemmelighet — commit den aldri (se .gitignore).
 */
object Env {
    private val dotenv = dotenv {
        ignoreIfMissing = true
        ignoreIfMalformed = true
    }

    fun get(key: String): String? = dotenv[key]
}
