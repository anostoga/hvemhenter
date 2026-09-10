# Barnehage-planleggjar

Hobbyprosjekt for å fordele levering/henting i barnehagen mellom foreldre,
med automatiske forslag basert på tidlegare fordeling og ledig tid i Google
Kalender om ein ynskjer det.
Ein familie kan i tillegg legge til "hjelparar" (typisk besteforeldre/andre
slektningar) som kan tildelast levering/henting utan å logge inn i appen sjølv
(sjå `/familie`).

## Arkitektur

- **backend/** — Kotlin/Ktor API. Google-innlogging (OAuth) per forelder,
  Postgres (Exposed) for familiar/foreldre/krypterte tokens/tildelingshistorikk,
  `AssignmentService` foreslår kven som bør ta ei oppgåve (rettferdig
  fordeling + ledigheitssjekk). Sesjonsbasert autentisering (signert cookie)
  avgrensar alle `/api/*`-kall til familien til den innlogga brukaren.
- **frontend/** — Next.js-dashbord. Innlogging skjer via Google (håndtert av
  backend); nye familiar vert oppretta via ein invitasjons-/opprettingskode
  på `/join`.

## Kom i gang

### Køyr lokalt

Når `backend/.env` og `frontend/.env.local` er sette opp (sjå avsnitta under),
kan du starte Postgres, backend og frontend.

```bash
cd backend
docker-compose up/start
./gradlew run

cd..
cd frontend
npm run dev
```

Appen migrerer databasen sjølv ved oppstart (`PostgresDatabase.connect()`)

`next.config.mjs` proxyar `/api/*`, `/auth/*` og `/join/*` til `BACKEND_URL`
(server-only env-variabel), slik at nettlesaren berre snakkar med Next.js sin eige
origin — ingen CORS involvert. Sesjonscookien frå backend blir dermed
same-origin sett frå nettlesaren, sjølv om frontend (Vercel) og backend
(Fly.io) er ulike domene bak proxyen.

### Mock Google-innlogging (lokal dev)

Du treng ikkje ein ekte Google Cloud OAuth-klient for å teste appen lokalt.
Set i `backend/.env`:

```
MOCK_GOOGLE_AUTH=true
```

og start backend + frontend som vanleg (`GOOGLE_CLIENT_ID`/`SECRET`/`REDIRECT_URI`
kan stå tomme — dei blir uansett aldri brukte i mock-modus). Gå så til
`http://localhost:8080/auth/mock-login` og fyll
inn eit namn:

- **Kode fylt ut** — opprettar ein ny familie (`FAMILY_CREATION_CODE`) eller blir
  med i ein eksisterande familie via invitasjonskode, akkurat som `/join` gjer.
- **Kode tom** — loggar inn som ein allereie registrert mock-brukar (same
  namn/e-post → same forelder-rad vert gjenbrukt). Ukjend brukar blir sendt til
  `/join?error=ikke_registrert`, akkurat som ekte `/auth/login` ville gjort.

Mock-innlogginga set ein ekte, gyldig sesjonscookie (same mekanisme som
ekte Google-innlogging) og ein fiktiv OAuth-token-rad, slik at appen elles
oppfører seg identisk — bortsett frå at ekte Google Calendar-kall vil feile
(forventa, sidan ingen ekte Google-tilkopling er gjort).

## Deploy

- **Backend**: Fly.io.
- **Database**: Supabase (Postgres).
- **Frontend**: Vercel, kopla direkte til GitHub-repoet.
- CI: `.github/workflows/backend.yml` køyrer testar og deployar til Fly.io ved
  push til `main` (krev secret `FLY_API_TOKEN`).
  `.github/workflows/frontend.yml` validerer berre at frontend byggjer — sjølve
  deployen håndterast av Vercel sin GitHub-integrasjon.

## Tryggleik

- OAuth-tokens er AES-256-GCM-krypterte i Postgres (`crypto/TokenCipher.kt`).
- OAuth `state`-parameteren er HMAC-signert med utløpstid for å hindre CSRF
  (`crypto/StateSigner.kt`).
- Sesjonscookien er HMAC-signert (ikkje kryptert — inneheld ingen PII, berre
  `parentId`/`familyId`) og kan difor ikkje forfalskast til å gje seg ut for
  å tilhøyra ein annan familie (`auth/SessionAuth.kt`).
- Alle familie-avgrensa repositories (`FamilyScopedAssignmentRepository`,
  `FamilyRepository`) krev `familyId` som konstruktørparameter henta frå
  sesjonen — det finst ingen spørjing som kan hente/skriva på tvers av familiar.

## Testing

```bash
cd backend
docker compose up -d && ./gradlew flywayMigrate
./gradlew test
```
