# Barnehage-planlegger — frontend

Enkel Next.js-app for at to foreldre skal se og bekrefte forslag om hvem som
leverer/henter i barnehagen. Ingen egen innlogging i appen (kun to kjente
brukere) — hver forelder kobler sin Google-kalender direkte mot backend via
`/auth/google/{parentId}/start`.

## Utvikling

```bash
npm install
cp env.example .env.local
npm run dev
```

## Miljøvariabler

| Variabel | Beskrivelse |
|---|---|
| `NEXT_PUBLIC_API_URL` | URL til backend-API-et |

## Deploy

Vercel (gratis tier), koblet til GitHub-repoet. Sett `NEXT_PUBLIC_API_URL` til
backend sin Fly.io-URL i Vercel sine miljøvariabler.
