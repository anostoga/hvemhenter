package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/** Krypterte OAuth-tokens per forelder. access/refresh-felt er AES-GCM-kryptert (se TokenCipher). */
object Tokens : Table("tokens") {
    val parentId = varchar("parent_id", 64)
    val accessTokenEnc = text("access_token_enc")
    val refreshTokenEnc = text("refresh_token_enc")
    val expiresAt = datetime("expires_at")
    val googleCalendarId = varchar("google_calendar_id", 128).nullable()
    override val primaryKey = PrimaryKey(parentId)
}

/** Historikk over tildelinger, brukt til rettferdighetsberegning og revisjonsspor. */
object Assignments : Table("assignments") {
    val id = long("id").autoIncrement()
    val date = varchar("date", 10) // ISO-8601 yyyy-MM-dd
    val type = varchar("type", 16) // DROPOFF | PICKUP
    val parentId = varchar("parent_id", 64)
    val assignmentSource = varchar("assignment_source", 16) // AUTO | MANUAL
    val googleEventId = varchar("google_event_id", 128).nullable()
    val createdAt = datetime("created_at")
    override val primaryKey = PrimaryKey(id)
}

/** Enkel nøkkel/verdi-konfigurasjon, bl.a. delt kalender-ID. */
object Config : Table("config") {
    val key = varchar("key", 64)
    val value = text("value")
    override val primaryKey = PrimaryKey(key)
}
