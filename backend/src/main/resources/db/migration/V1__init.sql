-- Multi-familie skjema. Rad-basert multi-tenancy: alle familiedata scopes via family_id.
-- Erstatter den gamle hjemmesnekrede migrasjonsrunneren (Migrations.kt), som fantes
-- fordi SQLite manglet førsteklasses Flyway-støtte. Postgres/Supabase har det.

create table families (
    id uuid primary key default gen_random_uuid(),
    shared_calendar_id text not null,
    invite_code text unique, -- null når familien allerede har 2 foreldre
    created_at timestamptz not null default now()
);

create table parents (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null references families(id) on delete cascade,
    google_sub text not null unique, -- stabil Google-identitet, ikke e-post
    email text not null,
    name text not null,
    created_at timestamptz not null default now()
);

create index idx_parents_family_id on parents(family_id);

create table oauth_tokens (
    parent_id uuid primary key references parents(id) on delete cascade,
    access_token_enc text not null,
    refresh_token_enc text not null,
    expires_at timestamptz not null
);

create table assignments (
    id uuid primary key default gen_random_uuid(),
    family_id uuid not null references families(id) on delete cascade,
    date date not null,
    type text not null check (type in ('DROPOFF', 'PICKUP')),
    parent_id uuid not null references parents(id),
    source text not null check (source in ('AUTO', 'MANUAL')),
    google_event_id text,
    created_at timestamptz not null default now(),
    unique (family_id, date, type)
);

create index idx_assignments_family_id_date on assignments(family_id, date);
