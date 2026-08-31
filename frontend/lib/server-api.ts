// Server-only hjelpefunksjoner (kalles KUN fra Server Components/layout.tsx,
// aldri fra "use client"-filer — se next/headers-avhengigheten under, som
// krasjer hvis den importeres i en klientkomponent).
//
// Formålet er å hente innloggingsstatus + visningsnavn FØR HTML-en sendes til
// nettleseren (Server-Side Rendering), slik at Nav aldri trenger å vise en
// tom/plassholder-tilstand mens /auth/whoami laster client-side — og slik at
// vi IKKE lenger trenger en egen cookie for å bære navnet (se historikken:
// `bhg_logged_in`-cookien ble fjernet igjen til fordel for denne løsningen,
// nettopp for å unngå personopplysninger i en klientlesbar cookie).
import { cookies } from "next/headers";
import type { WhoAmI } from "./api";

// Samme fallback som next.config.mjs sin rewrite-konfigurasjon. Brukes her i
// stedet for en relativ URL/rewrite fordi denne kjører SERVER-til-server (i
// Next.js sin Node-prosess, ikke i nettleseren) — Next.js sine rewrites
// gjelder kun forespørsler som kommer FRA nettleseren til Next.js sitt eget
// origin, ikke interne fetch-kall gjort under rendering av en Server
// Component. Vi må derfor peke direkte på backend og videresende
// sesjonscookien manuelt (se cookieHeader under).
const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

/**
 * Henter innlogget forelders navn (eller `loggedIn: false`) server-side, ved
 * å videresende ALLE cookies fra den innkommende requesten til backend (kun
 * `bhg_session` er faktisk relevant, men det er enklere og mer robust å sende
 * hele cookie-headeren videre uendret enn å plukke ut én cookie ved navn).
 *
 * Svarer alltid med et gyldig `WhoAmI`-objekt (aldri kaster) — feiler kallet
 * (backend nede, nettverksfeil under SSR osv.), faller vi tilbake til
 * `loggedIn: false` slik at siden fortsatt kan rendres. Klientsiden sin egen
 * `/auth/whoami`-kall (se Nav.tsx) fanger opp og retter opp status i
 * bakgrunnen uansett.
 */
export async function getServerWhoAmI(): Promise<WhoAmI> {
  try {
    const cookieStore = await cookies();
    const cookieHeader = cookieStore
      .getAll()
      .map((c) => `${c.name}=${c.value}`)
      .join("; ");

    const response = await fetch(`${BACKEND_URL}/auth/whoami`, {
      headers: cookieHeader ? { Cookie: cookieHeader } : {},
      // Aldri cache dette — innloggingsstatus er per-bruker og kan endre seg
      // (utlogging, ny sesjon) mellom hver request.
      cache: "no-store",
    });

    if (!response.ok) return { loggedIn: false };
    return (await response.json()) as WhoAmI;
  } catch {
    return { loggedIn: false };
  }
}
