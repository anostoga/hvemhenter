-- Erstatter den familie-delte kalenderen (`families.shared_calendar_id`) med én
-- kalender PER FORELDER: hver forelder velger nå sin egen Google-kalender som
-- deres tildelinger (levering/henting de selv er satt opp med) skrives til.
-- `families.shared_calendar_id`-kolonnen fjernes ikke (unngår en irreversibel
-- data-tapende migrasjon i et hobbyprosjekt), den er bare ikke lenger i bruk.
alter table parents add column calendar_id text;
