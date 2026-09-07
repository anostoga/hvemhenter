-- Eksplisitt "ikke sjekk tilgjengelighet i det hele tatt"-tilstand, atskilt
-- fra availability_calendar_id = null (som betyr "samme som calendar_id",
-- se V4). Default false bevarer eksisterende oppførsel for alle rader.
alter table parents add column availability_disabled boolean not null default false;
