-- Adminstøtte: utvalgte foreldre kan se aggregerte statistikk (antall familier/
-- brukere) og generere nye invitasjonskoder for å opprette (ikke bli med i) en
-- familie — et alternativ til den ene statiske FAMILY_CREATION_CODE-miljøvariabelen,
-- se routes/JoinRoutes.kt og routes/AdminRoutes.kt.
alter table parents add column is_admin boolean not null default false;

-- Engangskoder generert av en admin. Adskilt fra families.invite_code (som kun
-- lar forelder 2 BLI MED i en allerede opprettet familie) — disse kodene
-- OPPRETTER en helt ny familie, akkurat som FAMILY_CREATION_CODE gjør i dag,
-- men er individuelt sporbare (hvem opprettet koden, når/av hvem ble den brukt).
create table invite_codes (
    id uuid primary key default gen_random_uuid(),
    code text unique not null,
    created_by uuid not null references parents(id),
    created_at timestamptz not null default now(),
    used_at timestamptz,
    used_by_family_id uuid references families(id)
);
