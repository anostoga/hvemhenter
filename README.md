# Barnehage-planlegger

Hobbyprosjekt for å fordele levering/henting i barnehagen mellom to foreldre,
med automatiske forslag basert på tidligere fordeling og ledig tid i Google
Kalender. Begge foreldre bruker én delt kalender for de faktiske
levering/henting-hendelsene.

## Arkitektur

- **backend/** — Kotlin/Ktor API. Google OAuth per forelder, SQLite (Exposed)
  for krypterte tokens og tildelingshistorikk, `AssignmentService` foreslår
  hvem som bør ta en oppgave (rettferdig fordeling + ledighetssjekk).
- **frontend/** — Next.js-dashboard. Ingen egen innlogging (kun to kjente
  brukere); hver forelder kobler til sin Google-kalender via lenke til
  backend.

## Kom i gang

### Backend

```bash
cd backend
cp env.example .env   # se under for påkrevde variabler
./gradlew run
```

Påkrevde miljøvariabler (se `backend/src/main/kotlin/no/pilot/barnehage/AppConfig.kt`
og `google/GoogleOAuthClient.kt`):

| Variabel | Beskrivelse |
|---|---|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Fra Google Cloud Console (OAuth-klient) |
| `GOOGLE_REDIRECT_URI` | F.eks. `https://<backend>/auth/google/callback` |
| `SHARED_CALENDAR_ID` | Google-kalender-ID for den delte kalenderen |
| `TOKEN_ENCRYPTION_KEY` | Base64 AES-256-nøkkel — generer med `TokenCipher.generateKey()` |
| `STATE_SIGNING_SECRET` | Vilkårlig hemmelig streng for CSRF-beskyttelse av OAuth-state |
| `PARENT_1_ID` / `PARENT_1_NAME` | Identifikator og visningsnavn for forelder 1 |
| `PARENT_2_ID` / `PARENT_2_NAME` | Identifikator og visningsnavn for forelder 2 |
| `FRONTEND_URL` | For redirect etter vellykket OAuth-tilkobling |
| `DB_PATH` | Sti til SQLite-fil (default `./data/barnehage.db`) |

### Frontend

```bash
cd frontend
cp env.example .env.local
npm install
npm run dev
```

## Deploy (billigst mulig)

- **Backend**: Fly.io, `shared-cpu-1x`/256MB, ett persistent volum for SQLite
  (`backend/fly.toml`). Skalerer til null maskiner når appen er inaktiv.
- **Frontend**: Vercel gratis tier, koblet direkte til GitHub-repoet.
- CI: `.github/workflows/backend.yml` kjører tester og deployer til Fly.io ved
  push til `main` (krever secret `FLY_API_TOKEN`).
  `.github/workflows/frontend.yml` validerer kun at frontend bygger — selve
  deploy håndteres av Vercel sin GitHub-integrasjon.

## Sikkerhet

- OAuth-tokens er AES-256-GCM-kryptert i SQLite (`crypto/TokenCipher.kt`).
- OAuth `state`-parameter er HMAC-signert med utløpstid for å hindre CSRF
  (`crypto/StateSigner.kt`).
- Ingen hemmeligheter i repoet — sett dem som secrets i Fly.io/GitHub Actions/Vercel.

## Testing

```bash
cd backend
./gradlew test
```

`AssignmentServiceTest` dekker rettferdig fordeling, round-robin-alternering
ved uavgjort, og forslag basert på ledig tid/kalenderkonflikt.
