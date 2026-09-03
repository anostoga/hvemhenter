#!/usr/bin/env bash
#
# Starter hele den lokale utviklingsstacken: Postgres (Docker), backend
# (Ktor/gradlew run) og frontend (Next.js). Kjør fra hvor som helst i
# repoet:
#
#   ./scripts/dev.sh
#
# Trykk Ctrl+C for å stoppe backend + frontend igjen. Postgres-containeren
# lar vi fortsette å kjøre (raskere neste oppstart) — stopp den selv med
# `cd backend && docker compose down` hvis du vil frigjøre ressursene helt.
#
# Forutsetter at backend/.env og frontend/.env.local finnes (se README.md
# "Kom i gang" for hvordan du oppretter dem fra env.example).
set -euo pipefail
set -m # gi hver bakgrunnsjobb (&) sin egen prosessgruppe, slik at vi kan
       # drepe hele undertreet (gradlew->java, npm->node) ved opprydding,
       # ikke bare det direkte barnet.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/.dev-logs"
mkdir -p "$LOG_DIR"

BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"

# Noen miljøer har kun det eldre frittstående `docker-compose`-programmet,
# andre kun det nyere `docker compose`-pluginet — støtt begge.
if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  echo "Feil: fant verken 'docker compose' eller 'docker-compose'. Installer Docker Desktop først." >&2
  exit 1
fi

if [ ! -f "$ROOT_DIR/backend/.env" ]; then
  echo "Feil: backend/.env finnes ikke. Kopier backend/env.example til backend/.env og fyll inn verdier først." >&2
  exit 1
fi
if [ ! -f "$ROOT_DIR/frontend/.env.local" ]; then
  echo "Feil: frontend/.env.local finnes ikke. Kopier frontend/env.example til frontend/.env.local og fyll inn verdier først." >&2
  exit 1
fi

BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
  echo ""
  echo "Stopper backend og frontend ..."
  # Drep hele prosessgruppen (negativ PID), ikke bare den direkte
  # bakgrunnsjobben — ellers overlever gradlew/npm sine barneprosesser
  # (java/node) som foreldreløse prosesser på portene.
  [ -n "$BACKEND_PID" ] && kill -- "-$BACKEND_PID" 2>/dev/null || true
  [ -n "$FRONTEND_PID" ] && kill -- "-$FRONTEND_PID" 2>/dev/null || true
  echo "(Postgres-containeren lar vi fortsette å kjøre — 'cd backend && ${COMPOSE[*]} down' for å stoppe den også.)"
}
trap cleanup EXIT INT TERM

echo "==> Starter Postgres (Docker) ..."
(cd "$ROOT_DIR/backend" && "${COMPOSE[@]}" up -d)

echo "==> Venter på at Postgres blir klar ..."
# Sjekk både at postgres selv er klar (pg_isready inni containeren) OG at
# den host-mappede TCP-porten faktisk tar imot tilkoblinger — de to kan bli
# klare på litt forskjellige tidspunkt rett etter containeren er opprettet.
until (cd "$ROOT_DIR/backend" && "${COMPOSE[@]}" exec -T postgres pg_isready -U postgres) >/dev/null 2>&1 \
  && (exec 3<>"/dev/tcp/localhost/5432") 2>/dev/null; do
  exec 3<&- 2>/dev/null || true
  sleep 1
done
exec 3<&- 2>/dev/null || true
echo "    Postgres er klar."

echo "==> Starter backend (PORT=$BACKEND_PORT) — logg: $BACKEND_LOG"
(cd "$ROOT_DIR/backend" && PORT="$BACKEND_PORT" ./gradlew run --console=plain) > "$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!

echo "==> Venter på at backend svarer på http://localhost:$BACKEND_PORT/auth/whoami ..."
until curl -s -o /dev/null "http://localhost:$BACKEND_PORT/auth/whoami"; do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "Backend-prosessen døde under oppstart — se $BACKEND_LOG for detaljer." >&2
    exit 1
  fi
  sleep 1
done
echo "    Backend svarer."

echo "==> Starter frontend (PORT=$FRONTEND_PORT) — logg: $FRONTEND_LOG"
(cd "$ROOT_DIR/frontend" && BACKEND_URL="http://localhost:$BACKEND_PORT" PORT="$FRONTEND_PORT" npm run dev) > "$FRONTEND_LOG" 2>&1 &
FRONTEND_PID=$!

echo ""
echo "Alt kjører:"
echo "  Frontend: http://localhost:$FRONTEND_PORT"
echo "  Backend:  http://localhost:$BACKEND_PORT"
echo "  Logger:   tail -f $BACKEND_LOG   /   tail -f $FRONTEND_LOG"
echo ""
echo "Trykk Ctrl+C for å stoppe backend og frontend."
echo ""

wait "$BACKEND_PID" "$FRONTEND_PID"
