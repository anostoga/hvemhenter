-- Støtter kodefri opprettelse av familie: når en ukjent Google-bruker logger
-- inn blir ikke familie/forelder opprettet med en gang. I stedet lagres
-- Google-identiteten og OAuth-tokenene kortvarig her mens brukeren velger
-- "opprett ny familie" eller "bli med i en eksisterende familie" (kode) på
-- /velkommen (se OnboardingRepository/OnboardingRoutes.kt).
--
-- Lagret i databasen (ikke i minnet i backend-prosessen) slik at valget
-- overlever en omstart/ny instans av backend — Fly.io kan rotere/legge til
-- maskiner uten varsel. token_hash er en SHA-256-hash av den tilfeldige
-- referansen i brukerens cookie; selve referansen lagres aldri i databasen,
-- slik at en lekkasje av databasen ikke alene gir noen tilgang til en økt.
create table onboarding_sessions (
    id uuid primary key default gen_random_uuid(),
    token_hash text unique not null,
    google_sub text not null,
    email text not null,
    name text not null,
    access_token_enc text not null,
    refresh_token_enc text not null,
    token_expires_at timestamptz not null,
    csrf_token text not null,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);

create index onboarding_sessions_expires_at_idx on onboarding_sessions (expires_at);
create index onboarding_sessions_google_sub_idx on onboarding_sessions (google_sub);
