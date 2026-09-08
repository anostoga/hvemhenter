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
    /** Null for hjelpere (`isHelper = true`) — de logger aldri inn, se V6-migrasjonen. */
    val googleSub = text("google_sub").uniqueIndex().nullable()
    /** Null for hjelpere, samme begrunnelse som `googleSub`. */
    val email = text("email").nullable()
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
    /** Sant for "hjelpere" — personer (typisk slektninger) som kan tildeles
     * levering/henting, men aldri logger inn selv (se V6-migrasjonen). Skiller
     * disse fra ekte innloggede foreldre der det trengs, bl.a.
     * maks-2-innloggede-foreldre-grensen (se FamilyRepository). */
    val isHelper = bool("is_helper").default(false)
    /** Sant for en admin — kan se aggregert statistikk og generere nye
     * invitasjonskoder (se AdminRoutes/AdminRepository, V7-migrasjonen).
     * Settes ikke via noe UI; kun automatisk ved innlogging hvis e-posten er
     * listet i ADMIN_EMAILS (se AuthRoutes), eller manuelt i databasen. */
    val isAdmin = bool("is_admin").default(false)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}

/** Engangskoder generert av en admin for å OPPRETTE en helt ny familie — se
 * V7-migrasjonen og routes/AdminRoutes.kt/JoinRoutes.kt. */
object InviteCodesTable : Table("invite_codes") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val code = text("code").uniqueIndex()
    val createdBy = uuid("created_by").references(ParentsTable.id)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val usedAt = timestamp("used_at").nullable()
    /** ON DELETE SET NULL, se V8-migrasjonen — hele familien kan slettes
     * (f.eks. siste forelder sletter kontoen sin) uten at denne (historiske)
     * invite_codes-raden blokkerer slettingen. */
    val usedByFamilyId = uuid("used_by_family_id")
        .references(FamiliesTable.id, onDelete = org.jetbrains.exposed.sql.ReferenceOption.SET_NULL)
        .nullable()
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

