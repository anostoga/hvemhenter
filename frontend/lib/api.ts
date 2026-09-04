// Speiler modellene i backend/src/main/kotlin/no/pilot/barnehage/domain/Models.kt

export type AssignmentType = "DROPOFF" | "PICKUP";
export type AssignmentSource = "AUTO" | "MANUAL";

export interface Parent {
  id: string;
  name: string;
  avatar: string | null;
  connected: boolean;
  calendarId: string | null;
  availabilityCalendarId: string | null;
}

export interface Assignment {
  id: string | null;
  date: string;
  type: AssignmentType;
  parentId: string;
  source: AssignmentSource;
  googleEventId: string | null;
}

export interface Suggestion {
  date: string;
  type: AssignmentType;
  suggestedParentId: string;
  reason: string;
  conflict: boolean;
}

export interface Family {
  id: string;
  // null når familien allerede har 2 foreldre (koden er engangsbruk og
  // invalideres server-side når forelder #2 blir med, se backend FamilyRepository).
  inviteCode: string | null;
}

export interface MyCalendar {
  calendarId: string | null;
  availabilityCalendarId: string | null;
}

export interface AvailableCalendar {
  id: string;
  summary: string;
  primary: boolean;
}

export interface WhoAmI {
  loggedIn: boolean;
  name?: string;
  avatar?: string | null;
}

export interface Profile {
  name: string;
  avatar: string | null;
}

// Samme faste sett som backend sin validering (se routes/ProfileRoutes.kt
// ALLOWED_AVATARS) — holdt i sync manuelt.
export const AVATARS = ["🐻", "🦊", "🐰", "🐼", "🐨", "🐯", "🦁", "🐵", "🐶", "🐱", "🐸", "🦄"];

// Relativ URL — Next.js proxyer /api/* og /auth/* videre til backend (se
// rewrites() i next.config.mjs), så nettleseren snakker kun med Next.js sitt
// eget origin. Ingen CORS-håndtering nødvendig lenger.
const API_BASE_URL = "";

async function handle<T>(response: Response): Promise<T> {
  if (response.status === 401) {
    // Sesjonen mangler/er utløpt — send brukeren til forsiden i stedet for en
    // kryptisk feilmelding. Forsiden viser selv lenker til innlogging/join,
    // så dette er ikke en blindvei slik /join alene var (den krevde en kode).
    window.location.href = "/";
    throw new Error("ikke innlogget");
  }
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`API-kall feilet (${response.status}): ${body}`);
  }
  return response.json() as Promise<T>;
}

// Alle /api/*-kall sender med `credentials: "include"` — sesjonscookien
// (satt av backend etter innlogging) er det eneste som knytter kallet til
// riktig familie, se auth/SessionAuth.kt.
export const api = {
  authStartUrl: () => `${API_BASE_URL}/auth/google/start`,
  loginUrl: () => `${API_BASE_URL}/auth/login`,

  // Svarer alltid 200 (også uinnlogget) — trygt å kalle fra en offentlig side
  // uten at det trigger 401-redirecten i handle().
  whoAmI: () =>
    fetch(`${API_BASE_URL}/auth/whoami`, { credentials: "include" }).then((r) => r.json() as Promise<WhoAmI>),

  logout: () =>
    fetch(`${API_BASE_URL}/auth/logout`, { method: "POST", credentials: "include" }),

  getParents: () => fetch(`${API_BASE_URL}/api/parents`, { credentials: "include" }).then((r) => handle<Parent[]>(r)),

  getAssignments: () =>
    fetch(`${API_BASE_URL}/api/assignments`, { credentials: "include" }).then((r) => handle<Assignment[]>(r)),

  getSuggestion: (date: string, type: AssignmentType) =>
    fetch(`${API_BASE_URL}/api/suggest?date=${date}&type=${type}`, { credentials: "include" }).then((r) =>
      handle<Suggestion>(r),
    ),

  assign: (input: { date: string; type: AssignmentType; parentId: string; source?: AssignmentSource }) =>
    fetch(`${API_BASE_URL}/api/assign`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }).then((r) => handle<Assignment>(r)),

  deleteAssignment: async (id: string) => {
    const response = await fetch(`${API_BASE_URL}/api/assignments/${id}`, {
      method: "DELETE",
      credentials: "include",
    });
    if (response.status === 401) {
      window.location.href = "/";
      throw new Error("ikke innlogget");
    }
    if (!response.ok) {
      const body = await response.text();
      throw new Error(`API-kall feilet (${response.status}): ${body}`);
    }
    // 204 No Content — ingen body å parse
  },

  getFamily: () => fetch(`${API_BASE_URL}/api/family`, { credentials: "include" }).then((r) => handle<Family>(r)),

  // Kalenderen er nå knyttet til DEN INNLOGGEDE BRUKEREN selv, ikke hele
  // familien — tildelinger denne brukeren er satt opp med skrives dit.
  getMyCalendar: () =>
    fetch(`${API_BASE_URL}/api/calendars/mine`, { credentials: "include" }).then((r) => handle<MyCalendar>(r)),

  updateMyCalendar: (calendarId: string, availabilityCalendarId: string | null) =>
    fetch(`${API_BASE_URL}/api/calendars/mine`, {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ calendarId, availabilityCalendarId }),
    }).then((r) => handle<MyCalendar>(r)),

  // Returnerer `null` (i stedet for å kaste) når brukeren ikke har koblet til
  // Google ennå (409) — det er en forventet tilstand UI-et skal falle tilbake
  // fra til fritekst-input, ikke en feil å vise som en generell feilmelding.
  getAvailableCalendars: async (): Promise<AvailableCalendar[] | null> => {
    const response = await fetch(`${API_BASE_URL}/api/calendars/available`, { credentials: "include" });
    if (response.status === 401) {
      window.location.href = "/";
      throw new Error("ikke innlogget");
    }
    if (response.status === 409) {
      return null;
    }
    if (!response.ok) {
      const body = await response.text();
      throw new Error(`API-kall feilet (${response.status}): ${body}`);
    }
    return response.json() as Promise<AvailableCalendar[]>;
  },

  getProfile: () => fetch(`${API_BASE_URL}/api/profile`, { credentials: "include" }).then((r) => handle<Profile>(r)),

  updateProfile: (input: { name: string; avatar: string | null }) =>
    fetch(`${API_BASE_URL}/api/profile`, {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }).then((r) => handle<Profile>(r)),
};
