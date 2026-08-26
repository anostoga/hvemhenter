# Endringsdokument — Multi-familie-støtte

## Bakgrunn / beslutning
Appen fungerer i dag for nøyaktig to hardkodede foreldre (env-variabler). Endringen
gjør appen flerbrukbar: flere familier kan opprette konto og bruke appen uavhengig
av hverandre. Se Fase 1–3 i planen for full kontekst (Supabase Postgres, kombinert
Google-innlogging, invitasjonskode).

## Status i denne leveransen (Fase 4 — nå ferdigimplementert)
Rødsone-delene er nå implementert (ikke lenger stubs) og koblet inn — appen kjører
i **dobbel-database-modus**: eksisterende SQLite-basert to-forelder-flyt fungerer
uendret, og Postgres/multi-familie-flyten aktiveres kun når `DATABASE_URL` og
`SESSION_SIGNING_SECRET` er satt i miljøet.

| Del | Status |
|---|---|
| Karakteriseringstester (lås dagens forslags-/matching-logikk) | ✅ Ferdig, grønn build |
| Flyway-migrasjon (`V1__init.sql`) for nytt Postgres-skjema | ✅ Kjørt og verifisert mot lokal Docker Postgres |
| `build.gradle.kts` — Postgres/HikariCP/Flyway-avhengigheter lagt til | ✅ Ferdig, sameksisterer med dagens SQLite |
| `fly.toml` — dokumentert hva som fjernes ved cutover | ✅ Ikke endret funksjonelt ennå |
| Sesjonsautentisering (`SessionAuth.kt`) | ✅ Implementert — HMAC-signert cookie, `SESSION_COOKIE_SECURE` styrer `Secure`/`SameSite` for lokal vs. prod |
| Familie-scopet repository (`FamilyScopedAssignmentRepository.kt`) | ✅ Implementert, familie-isolasjon verifisert med egne integrasjonstester mot lokal Postgres |
| `/join`-rute (invitasjonskode) | ✅ Implementert — rate-limiting (5/time/IP), engangsbruk av invite_code, maks 2 foreldre/familie |
| Frontend `/join`-side | ✅ Ferdig, poster til den nå fungerende backend-ruten |

**Viktig:** koblingen i `Application.kt`/`Routing.kt` er additiv og betinget —
uten `DATABASE_URL`/`SESSION_SIGNING_SECRET` satt, oppfører appen seg akkurat som
før (kun SQLite, kun de to hardkodede foreldrene). Eksisterende funksjonalitet er
derfor fortsatt upåvirket med mindre man eksplisitt slår på Postgres-miljøet.

## Rollback-plan
- Fjern `DATABASE_URL`/`SESSION_SIGNING_SECRET` fra miljøet → appen faller
  automatisk tilbake til ren SQLite-modus, ingen kodeendring nødvendig.
- Behold `data/barnehage.db` som lokal backup (ikke slett før Postgres-versjonen
  har kjørt stabilt i produksjon i minst en uke).
  Fly-appen kan rulles tilbake til forrige release (`fly releases` + `fly deploy --image <forrige>`)
  så lenge SQLite-volumet (`barnehage_data`) ikke er slettet.

## Neste steg (ikke del av denne leveransen)
1. Implementer `SessionAuth.kt` (sesjon, cookie-attributter, `SESSION_SIGNING_SECRET`)
2. Implementer `/join`-ruten og koble familie-opprettelse til Google OAuth-flyten
3. Implementer `FamilyScopedAssignmentRepository` (og tilsvarende for `parents`/`oauth_tokens`)
4. Kjør `./gradlew flywayMigrate` mot ekte Supabase `DATABASE_URL`
5. Fjern SQLite (`sqlite-jdbc`, `[[mounts]]` i `fly.toml`, `Migrations.kt`, `Tables.kt`)
6. Oppdater `AppConfig.kt` til å hente foreldre fra DB i stedet for env-variabler
