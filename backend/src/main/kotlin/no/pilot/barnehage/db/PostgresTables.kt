package no.pilot.barnehage.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

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

    val googleSub = text("google_sub").uniqueIndex().nullable()

    val email = text("email").nullable()
    val name = text("name")
    val avatar = text("avatar").nullable()

    val calendarId = text("calendar_id").nullable()

    val availabilityCalendarId = text("availability_calendar_id").nullable()

    val availabilityDisabled = bool("availability_disabled").default(false)

    val isHelper = bool("is_helper").default(false)

    val isAdmin = bool("is_admin").default(false)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(id)
}

object InviteCodesTable : Table("invite_codes") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val code = text("code").uniqueIndex()
    val createdBy = uuid("created_by").references(ParentsTable.id)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val usedAt = timestamp("used_at").nullable()

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
