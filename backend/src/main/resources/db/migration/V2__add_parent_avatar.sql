-- Legger til profilfelter for foreldre: valgfritt avatar-ikon (emoji), i tillegg
-- til det eksisterende `name`-feltet som nå også er redigerbart av brukeren selv
-- (opprinnelig kun satt fra Google sitt visningsnavn ved første innlogging).
alter table parents add column avatar text;
