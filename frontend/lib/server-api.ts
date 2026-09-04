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
import type { Assignment, AvailableCalendar, Family, MyCalendar, Parent, Profile, WhoAmI } from "./api";

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
    const header = await cookieHeader();

    const response = await fetch(`${BACKEND_URL}/auth/whoami`, {
      headers: header ? { Cookie: header } : {},
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

/**
 * Kastes av `serverFetch` når backend svarer 401 — kalleren (Server
 * Component-siden, se app/ukeplan/page.tsx) fanger denne opp og gjør en
 * ekte server-side `redirect("/")`, i stedet for å rendre en tom
 * innlogget-side som først etterpå (client-side) oppdager at sesjonen
 * mangler (slik `lib/api.ts` sin `handle()` gjør for øvrige kall).
 */
export class UnauthorizedError extends Error {}

async function cookieHeader(): Promise<string> {
  const cookieStore = await cookies();
  return cookieStore
    .getAll()
    .map((c) => `${c.name}=${c.value}`)
    .join("; ");
}

async function serverFetch<T>(path: string): Promise<T> {
  const header = await cookieHeader();

  const response = await fetch(`${BACKEND_URL}${path}`, {
    headers: header ? { Cookie: header } : {},
    // Samme begrunnelse som i getServerWhoAmI: dataene er per-bruker og kan
    // endre seg mellom hver request, skal aldri caches.
    cache: "no-store",
  });

  if (response.status === 401) throw new UnauthorizedError();
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`API-kall feilet (${response.status}): ${body}`);
  }
  return response.json() as Promise<T>;
}

/**
 * Henter foreldre + tildelinger server-side for førstelasting av
 * kalender-siden (se app/ukeplan/page.tsx) — brukes til å seede
 * klientkomponenten med data FØR HTML-en sendes, slik at Suspense-fallbacken
 * (skjelett-UI) kun vises mens denne SSR-henting pågår, ikke i tillegg
 * client-side etter hydrering. I motsetning til `getServerWhoAmI` kaster
 * disse ved feil (inkl. `UnauthorizedError` ved 401) — kalleren bestemmer
 * selv om det skal redirecte eller vise en feilmelding.
 */
export const getServerParents = () => serverFetch<Parent[]>("/api/parents");
export const getServerAssignments = () => serverFetch<Assignment[]>("/api/assignments");

/**
 * Henter familiemedlemmer + invitasjonskode server-side for /familie — se
 * getServerParents over for begrunnelsen (samme mønster, kaster ved feil).
 */
export const getServerFamily = () => serverFetch<Family>("/api/family");

/** Henter innlogget brukers navn/avatar server-side for /profil. */
export const getServerProfile = () => serverFetch<Profile>("/api/profile");

/** Henter innlogget brukers egen kalendertilkobling server-side for /innstillinger. */
export const getServerMyCalendar = () => serverFetch<MyCalendar>("/api/calendars/mine");

/**
 * Henter listen over kalendere brukeren kan velge mellom server-side for
 * /innstillinger. I motsetning til de andre `getServer*`-funksjonene kaster
 * denne IKKE ved 409 — det betyr bare at brukeren ikke har koblet til Google
 * ennå, en forventet/legitim tilstand (se `api.getAvailableCalendars` i
 * lib/api.ts, som gjør det samme client-side), ikke en feil å redirecte
 * eller vise feilmelding for. 401 gir fortsatt `UnauthorizedError`.
 */
export async function getServerAvailableCalendars(): Promise<AvailableCalendar[] | null> {
  const header = await cookieHeader();
  const response = await fetch(`${BACKEND_URL}/api/calendars/available`, {
    headers: header ? { Cookie: header } : {},
    cache: "no-store",
  });

  if (response.status === 401) throw new UnauthorizedError();
  if (response.status === 409) return null;
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`API-kall feilet (${response.status}): ${body}`);
  }
  return response.json() as Promise<AvailableCalendar[]>;
}
