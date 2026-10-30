alter table families
    add column last_calendar_viewed_at timestamptz,
    add column last_calendar_updated_at timestamptz;
