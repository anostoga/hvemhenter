# Barnehage-planlegger

Hobbyprosjekt for å fordele levering/henting i barnehagen mellom foreldre,
med automatiske forslag basert på tidligere fordeling og ledig tid i Google
Kalender.
En familie kan i tillegg legge til "hjelpere" (typisk besteforeldre/andre
slektninger) som kan tildeles levering/henting uten å logge inn i appen selv
(se `/familie`).

## Arkitektur

- **backend/** — Kotlin/Ktor API. Google-innlogging (OAuth) per forelder,
  Postgres (Exposed) for familier/foreldre/krypterte tokens/tildelingshistorikk,
  `AssignmentService` foreslår hvem som bør ta en oppgave (rettferdig
  fordeling + ledighetssjekk). Sesjonsbasert autentisering (signert cookie)
  scoper alle `/api/*`-kall til innlogget brukers familie.
- **frontend/** — Next.js-dashboard. Innlogging skjer via Google (håndtert av
  backend); nye familier opprettes/bli-med-i via en invitasjons-/opprettelseskode
  på `/join`.

## Kom i gang

### Kjør lokalt

Når `backend/.env` og `frontend/.env.local` er satt opp (se seksjonene under),
kan du starte Postgres, backend og frontend.

```bash
cd backend
docker-compose up/start
./gradlew run

cd..
cd frontend
npm run dev
```

Appen migrerer databasen selv ved oppstart (`PostgresDatabase.connect()`)

`next.config.mjs` proxyer `/api/*`, `/auth/*` og `/join/*` til `BACKEND_URL`
(server-only env var), slik at nettleseren kun snakker med Next.js sitt eget
origin — ingen CORS involvert. Sesjonscookien fra backend blir dermed
samme-origin sett fra nettleseren, selv om frontend (Vercel) og backend
(Fly.io) er ulike domener bak proxyen.

### Mock Google-innlogging (lokal dev)

Du trenger ikke en ekte Google Cloud OAuth-klient for å teste appen lokalt.
Sett i `backend/.env`:

```
MOCK_GOOGLE_AUTH=true
```

og start backend + frontend som vanlig (`GOOGLE_CLIENT_ID`/`SECRET`/`REDIRECT_URI`
kan stå tomme — de brukes uansett aldri i mock-modus). Gå så til
`http://localhost:8080/auth/mock-login` og fyll
inn et navn:

- **Kode utfylt** — oppretter en ny familie (`FAMILY_CREATION_CODE`) eller blir
  med i en eksisterende familie via invitasjonskode, akkurat som `/join` gjør.
- **Kode tom** — logger inn som en allerede registrert mock-bruker (samme
  navn/e-post → samme forelder-rad gjenbrukes). Ukjent bruker sendes til
  `/join?error=ikke_registrert`, akkurat som ekte `/auth/login` ville gjort.

Mock-innloggingen setter en ekte, gyldig sesjonscookie (samme mekanisme som
ekte Google-innlogging) og en fiktiv OAuth-token-rad, slik at appen for øvrig
oppfører seg identisk — bortsett fra at ekte Google Calendar-kall vil feile
(forventet, siden ingen ekte Google-tilkobling er gjort).

## Deploy 

- **Backend**: Fly.io.
- **Database**: Supabase (Postgres).
- **Frontend**: Vercel, koblet direkte til GitHub-repoet.
- CI: `.github/workflows/backend.yml` kjører tester og deployer til Fly.io ved
  push til `main` (krever secret `FLY_API_TOKEN`).
  `.github/workflows/frontend.yml` validerer kun at frontend bygger — selve
  deploy håndteres av Vercel sin GitHub-integrasjon.

## Sikkerhet

- OAuth-tokens er AES-256-GCM-kryptert i Postgres (`crypto/TokenCipher.kt`).
- OAuth `state`-parameter er HMAC-signert med utløpstid for å hindre CSRF
  (`crypto/StateSigner.kt`).
- Sesjonscookien er HMAC-signert (ikke kryptert — inneholder ingen PII, kun
  `parentId`/`familyId`) og kan derfor ikke forfalskes til å late som man
  tilhører en annen familie (`auth/SessionAuth.kt`).
- Alle familie-scopede repositories (`FamilyScopedAssignmentRepository`,
  `FamilyRepository`) krever `familyId` som konstruktørparameter hentet fra
  sesjonen — det finnes ingen spørring som kan hente/skrive på tvers av familier.
- 
## Testing

```bash
cd backend
docker compose up -d && ./gradlew flywayMigrate
./gradlew test
```