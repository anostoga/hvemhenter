# Observerbarhetsplan — Multi-familie-cutover

Gjelder når rødsone-delene er implementert og appen faktisk kjører mot Postgres/sesjoner.

## Hva skal måles
- **Innloggingssuksess**: andel `/join`- og OAuth-callback-forsøk som lykkes vs. feiler
- **Familie-antall**: totalt antall familier og aktive brukere (enkel teller, ikke PII)
- **Databasetilkoblinger**: HikariCP pool-utnyttelse (bør aldri nå `maximumPoolSize=3` konstant)
- **Flyway**: migrasjonsstatus ved oppstart (feiler appen å starte hvis migrasjon feiler? Bør den det?)

## Logging (påminnelse fra sikkerhetssjekklisten)
- Logg `family_id`/`parent_id` ved feil, **aldri** `google_sub`, e-post eller navn
- Logg antall hendelser hentet per kalenderoppslag (allerede implementert) — behold, ufarlig
- Logg **aldri** `FAMILY_CREATION_CODE` eller `invite_code`-verdier, selv ved feil

## Alarmer (minimum, gitt at dette er et hobbyprosjekt uten Prometheus/Grafana)
- Fly.io sin innebygde helsesjekk (`/health`) er allerede konfigurert — behold
- Vurder enkel varsling (f.eks. e-post via Fly/Supabase sine egne varslinger) ved:
  - Gjentatte migrasjonsfeil ved deploy
  - Databasetilkobling feiler ved oppstart

## Suksesskriterier for cutover
- Minst én reell familie (utover din egen) har fullført `/join` → Google-innlogging → sett kalenderforslag, uten manuell inngripen fra deg
