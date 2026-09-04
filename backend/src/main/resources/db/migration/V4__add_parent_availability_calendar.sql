-- Lar hver forelder velge en ANNEN kalender å hente tilgjengelighet (opptatte
-- tider) fra enn den kalenderen tildelinger skrives til (`parents.calendar_id`,
-- se V3). Null her betyr "samme kalender som calendar_id" — det er
-- standardvalget, og det avkrysningsboksen "bruk samme kalender" i UI-et
-- tilsvarer (se CalendarRoutes/innstillinger-siden).
alter table parents add column availability_calendar_id text;
