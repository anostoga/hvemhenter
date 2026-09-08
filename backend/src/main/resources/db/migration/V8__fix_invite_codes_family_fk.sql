-- Retter en manglende ON DELETE-regel på invite_codes.used_by_family_id fra
-- V7-migrasjonen: uten denne blokkerte Postgres sletting av en familie
-- (families-raden) så snart familien var opprettet via en admin-generert
-- invitasjonskode (se AccountRoutes.kt/FamilyRepository.deleteFamily — siste
-- innloggede forelder sletter kontoen sin, som kaskaderer til hele familien).
--
-- SET NULL (ikke CASCADE) — selve invite_codes-raden er en revisjonslogg over
-- at koden ER brukt (usedAt er fortsatt satt), den skal bestå selv om
-- familien den en gang opprettet siden er slettet. Kun koblingen til den nå
-- ikke-eksisterende familien fjernes.
alter table invite_codes drop constraint invite_codes_used_by_family_id_fkey;
alter table invite_codes
    add constraint invite_codes_used_by_family_id_fkey
    foreign key (used_by_family_id) references families(id) on delete set null;
