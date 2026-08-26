// Speiler modellene i backend/src/main/kotlin/no/pilot/barnehage/domain/Models.kt

export type AssignmentType = "DROPOFF" | "PICKUP";
export type AssignmentSource = "AUTO" | "MANUAL";

export interface Parent {
  id: string;
  name: string;
  googleCalendarId: string | null;
}

export interface Assignment {
  id: number | null;
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

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

async function handle<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`API-kall feilet (${response.status}): ${body}`);
  }
  return response.json() as Promise<T>;
}

export const api = {
  authStartUrl: (parentId: string) => `${API_BASE_URL}/auth/google/${parentId}/start`,

  getParents: () => fetch(`${API_BASE_URL}/api/parents`).then((r) => handle<Parent[]>(r)),

  getAssignments: () => fetch(`${API_BASE_URL}/api/assignments`).then((r) => handle<Assignment[]>(r)),

  getSuggestion: (date: string, type: AssignmentType) =>
    fetch(`${API_BASE_URL}/api/suggest?date=${date}&type=${type}`).then((r) => handle<Suggestion>(r)),

  assign: (input: { date: string; type: AssignmentType; parentId: string; source?: AssignmentSource }) =>
    fetch(`${API_BASE_URL}/api/assign`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }).then((r) => handle<Assignment>(r)),
};
