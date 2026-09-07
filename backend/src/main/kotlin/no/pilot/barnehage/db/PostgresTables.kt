package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

/**
 * Postgres-tabeller for multi-familie (V1__init.sql). Speiler Flyway-skjemaet nøyaktig —
 * hold disse to i sync manuelt (Exposed genererer ikke skjema fra migrasjonsfiler).
 */
object FamiliesTable : Table("families") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val sharedCalendarId = text("shared_calendar_id")
    val inviteCode = text("invite_code").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}

object ParentsTable : Table("parents") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val familyId = uuid("family_id").references(FamiliesTable.id)
    val googleSub = text("google_sub").uniqueIndex()
    val email = text("email")
    val name = text("name")
    val avatar = text("avatar").nullable()
    /** Google-kalenderen DENNE forelderen selv har valgt (se /api/calendars/mine)
     * — tildelinger der forelderen er satt opp skrives som hendelser hit, i
     * stedet for til én delt familiekalender (se V3-migrasjonen). */
    val calendarId = text("calendar_id").nullable()
    /** Kalenderen forelderen henter TILGJENGELIGHET (opptatte tider) fra, hvis
     * forskjellig fra `calendarId`. Null betyr "samme som calendarId" — se
     * V4-migrasjonen og checkboxen på /innstillinger. */
    val availabilityCalendarId = text("availability_calendar_id").nullable()
    /** Eksplisitt "ikke sjekk tilgjengelighet i det hele tatt"-tilstand, atskilt
     * fra `availabilityCalendarId = null` (som betyr "samme som calendarId") —
     * se V5-migrasjonen. */
    val availabilityDisabled = bool("availability_disabled").default(false)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}

object OauthTokensTable : Table("oauth_tokens") {
    val parentId = uuid("parent_id").references(ParentsTable.id)
    val accessTokenEnc = text("access_token_enc")
    val refreshTokenEnc = text("refresh_token_enc")
    val expiresAt = timestamp("expires_at")
    override val primaryKey = PrimaryKey(parentId)
}

object AssignmentsTable : Table("assignments") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val familyId = uuid("family_id").references(FamiliesTable.id)
    val date = date("date")
    val type = text("type")
    val parentId = uuid("parent_id").references(ParentsTable.id)
    val assignmentSource = text("source")
    val googleEventId = text("google_event_id").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}

