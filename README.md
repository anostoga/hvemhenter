# Barnehage-planlegger

Hobbyprosjekt for å fordele levering/henting i barnehagen mellom foreldre,
med automatiske forslag basert på tidligere fordeling og ledig tid i Google
Kalender. Familien bruker én delt kalender for de faktiske
levering/henting-hendelsene. Flere familier kan bruke appen samtidig — hver
familie ser kun sine egne data (maks 2 foreldre per familie).

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

### Lokal database (Postgres i Docker)

```bash
cd backend
docker compose up -d   # starter lokal Postgres på localhost:5432
```

Appen migrerer databasen selv ved oppstart (`PostgresDatabase.connect()`), så et
manuelt `./gradlew flywayMigrate`-steg er ikke nødvendig lenger for å kjøre
appen lokalt eller i prod. Tasken finnes fortsatt og brukes av CI-testjobben,
siden enkelte tester kobler til databasen direkte og trenger skjemaet
migrert på forhånd — kjør `./gradlew flywayMigrate` manuelt hvis du kun skal
kjøre tester uten å starte appen selv.

### Backend

```bash
cd backend
cp env.example .env   # se under for påkrevde variabler
./gradlew run
```

Påkrevde miljøvariabler (se `backend/plugins/PostgresDatabase.kt`,
`google/GoogleOAuthClient.kt` og `auth/SessionAuth.kt`):

| Variabel | Beskrivelse |
|---|---|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Fra Google Cloud Console (OAuth-klient) |
| `GOOGLE_REDIRECT_URI` | Peker til **frontend** (som proxyer videre til backend, se under) — f.eks. `https://<frontend>/auth/google/callback`. Må også registreres i Google Cloud Console. |
| `TOKEN_ENCRYPTION_KEY` | Base64 AES-256-nøkkel — generer med `TokenCipher.generateKey()` |
| `STATE_SIGNING_SECRET` | Vilkårlig hemmelig streng for CSRF-beskyttelse av OAuth-state |
| `SESSION_SIGNING_SECRET` | Egen hemmelighet for signering av sesjonscookien (ikke samme som over) |
| `SESSION_COOKIE_SECURE` | `false` lokalt (http), `true` i prod (Fly.io/Vercel, ulike domener) |
| `FAMILY_CREATION_CODE` | Kode som lar en ny familie opprettes (delt kun med deg selv/betrodde) |
| `FRONTEND_URL` | For redirect etter vellykket innlogging/OAuth-tilkobling |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | Postgres-tilkobling |
| `MOCK_GOOGLE_AUTH` | KUN lokal dev — `true` slår på `/auth/mock-login` (se under), lar deg teste uten en ekte Google-klient. ALDRI `true` i prod. |

### Frontend

```bash
cd frontend
cp env.example .env.local
npm install
npm run dev
```

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
`http://localhost:8080/auth/mock-login` (eller lenk dit fra frontend) og fyll
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

**`MOCK_GOOGLE_AUTH` må ALDRI settes i Fly.io/Vercel-hemmeligheter** — den
finnes kun som en lokal `.env`-bekvemmelighet (se `Env.kt`), og selve
endepunktet (`/auth/mock-login`) registreres ikke i det hele tatt med mindre
flagget er satt til `true`.

## Deploy (billigst mulig)

- **Backend**: Fly.io, `shared-cpu-1x`/256MB (`backend/fly.toml`). Skalerer til
  null maskiner når appen er inaktiv. `DATABASE_URL` og andre hemmeligheter
  settes med `fly secrets set`, ikke i `fly.toml`.
- **Database**: Supabase (Postgres) gratis tier.
- **Frontend**: Vercel gratis tier, koblet direkte til GitHub-repoet.
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
- Ingen hemmeligheter i repoet — sett dem som secrets i Fly.io/GitHub Actions/Vercel.

## Testing

```bash
cd backend
docker compose up -d && ./gradlew flywayMigrate
./gradlew test
```

`AssignmentServiceTest` dekker rettferdig fordeling, round-robin-alternering
ved uavgjort, og forslag basert på ledig tid/kalenderkonflikt.
`FamilyScopedAssignmentRepositoryTest`/`JoinRoutesTest`/`SessionAuthTest`
kjører integrasjonstester mot lokal Postgres og verifiserer familie-isolasjon.
