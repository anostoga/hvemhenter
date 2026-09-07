-- Lar familier legge til "hjelpere" (typisk besteforeldre/slektninger) som
-- kan tildeles levering/henting, men som ALDRI logger inn i appen selv (ingen
-- Google-konto). Gjenbruker `parents`-tabellen fremfor en egen tabell, siden
-- tildelinger allerede refererer generisk til `parent_id`, og all eksisterende
-- forelder-logikk (navn, avatar) fungerer identisk for disse. `google_sub` og
-- `email` var tidligere obligatoriske (kun satt ved ekte Google-innlogging) —
-- gjøres nå valgfrie, siden hjelpere aldri har noen av delene. `is_helper`
-- skiller de to typene fra hverandre der det trengs (bl.a. maks-2-innloggede-
-- foreldre-grensen, som ALDRI skal telle hjelpere, se FamilyRepository).
alter table parents alter column google_sub drop not null;
alter table parents alter column email drop not null;
alter table parents add column is_helper boolean not null default false;
