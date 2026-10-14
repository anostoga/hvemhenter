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
  backend). Ein heilt ny brukar treng ingen kode — fyrste innlogging sender
  deg til `/velkommen`, der du vel om du vil opprette ei ny familie (du blir
  forelder 1) eller bli med i ei eksisterande familie via invitasjonskoden du
  har fått frå den andre forelderen. Den andre forelderen brukar framleis
  invitasjonskoden på `/join`.

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

### Bekreftelse fra hjelpere

Hjelpere kan få e-post når de tildeles levering eller henting, og svare via en
tidsbegrenset lenke uten å logge inn. Tildeling fungerer uavhengig av e-posttjenesten:
e-post sendes først når en forelder trykker «Send e-post» på den aktuelle
hjelpertildelingen i ukeplanen. For å sende e-post må backend ha en
Resend API-nøkkel (`RESEND_API_KEY`) og en verifisert avsenderadresse
(`HELPER_EMAIL_FROM`). `FRONTEND_URL` må peke på appens offentlige adresse; lokalt
brukes `http://localhost:3000`. Uten disse verdiene kan familier fortsatt tildele
oppgaver til hjelpere; e-postknappen viser en feil hvis utsending ikke er konfigurert
eller feiler. Hjelperens e-postadresse
legges inn under Familie. Avslag vises i ukeplanen, der familien kan tildele
oppgaven på nytt. En mislykket utsending kan forsøkes på nytt med samme knapp.

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

- **Kode fylt ut** — hoppar over `/velkommen`-valet og blir med i ein
  eksisterande familie via invitasjonskoden direkte (dev-snarveg for det
  `/join` elles gjer).
- **Kode tom** — loggar inn som ein allereie registrert mock-brukar (same
  namn/e-post → same forelder-rad vert gjenbrukt). Ukjend brukar blir sendt til
  `/velkommen` for å velje "opprett ny familie" eller "bli med med kode",
  akkurat som ekte `/auth/login` ville gjort.

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
  (`crypto/StateSigner.kt`), og er i tillegg bunde til ein éingongs-nonce i ein
  eigen cookie (`auth/OAuthNonce.kt`) slik at ein angripar ikkje kan lure eit
  offer til å fullføre angriparen sin innlogging.
- Fyrste-gongs-innlogging (ukjend Google-brukar) mellomlagrar identiteten i
  ein kortlevd, databaselagra "onboarding-sesjon" (`db/OnboardingRepository.kt`,
  tabellen `onboarding_sessions`) — aldri i minne, slik at det fungerer sjølv
  med fleire backend-instansar. Val av "opprett familie"/"bli med" krev i
  tillegg eit CSRF-token henta frå `/auth/onboarding/status` og ein gyldig
  `Origin`-header (`routes/OnboardingRoutes.kt`).
- Familieoppretting og -join er atomisk (Postgres row-locking på
  invitasjonskoden) slik at to samtidige forsøk ikkje kan gje ein familie tre
  foreldre eller opprette duplikate familiar (`db/FamilyRepository.kt`).
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
