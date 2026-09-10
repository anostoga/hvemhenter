export type AssignmentType = "DROPOFF" | "PICKUP";
export type AssignmentSource = "AUTO" | "MANUAL";

export interface Parent {
  id: string;
  name: string;
  avatar: string | null;
  connected: boolean;
  calendarId: string | null;
  availabilityCalendarId: string | null;

  isHelper: boolean;
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

  inviteCode: string | null;
}

export interface MyCalendar {
  calendarId: string | null;
  availabilityCalendarId: string | null;
  availabilityDisabled: boolean;
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
  isAdmin?: boolean;
}

export interface AdminStats {
  familyCount: number;
  userCount: number;
  helperCount: number;
}

export interface AdminInviteCode {
  code: string;
  createdAt: string;
  usedAt: string | null;
  usedByFamilyId: string | null;
}

export interface Profile {
  name: string;
  avatar: string | null;
}

export const AVATARS = ["🐻", "🦊", "🐰", "🐼", "🐨", "🐯", "🦁", "🐵", "🐶", "🐱", "🐸", "🦄"];

const API_BASE_URL = "";

async function handle<T>(response: Response): Promise<T> {
  if (response.status === 401) {

    window.location.href = "/";
    throw new Error("ikke innlogget");
  }
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`API-kall feilet (${response.status}): ${body}`);
  }
  return response.json() as Promise<T>;
}

export const api = {
  authStartUrl: () => `${API_BASE_URL}/auth/google/start`,
  loginUrl: () => `${API_BASE_URL}/auth/login`,

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

  },

  getFamily: () => fetch(`${API_BASE_URL}/api/family`, { credentials: "include" }).then((r) => handle<Family>(r)),

  addHelper: (input: { name: string; avatar?: string | null }) =>
    fetch(`${API_BASE_URL}/api/family/helpers`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }).then((r) => handle<{ id: string; name: string; avatar: string | null }>(r)),

  updateHelper: (id: string, input: { name: string; avatar?: string | null }) =>
    fetch(`${API_BASE_URL}/api/family/helpers/${id}`, {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    }).then((r) => handle<{ id: string; name: string; avatar: string | null }>(r)),

  removeHelper: async (id: string) => {
    const response = await fetch(`${API_BASE_URL}/api/family/helpers/${id}`, {
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

  },

  getMyCalendar: () =>
    fetch(`${API_BASE_URL}/api/calendars/mine`, { credentials: "include" }).then((r) => handle<MyCalendar>(r)),

  updateMyCalendar: (calendarId: string | null, availabilityCalendarId: string | null, availabilityDisabled: boolean = false) =>
    fetch(`${API_BASE_URL}/api/calendars/mine`, {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ calendarId, availabilityCalendarId, availabilityDisabled }),
    }).then((r) => handle<MyCalendar>(r)),

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

  deleteAccount: async () => {
    const response = await fetch(`${API_BASE_URL}/api/account`, {
      method: "DELETE",
      credentials: "include",
    });
    if (!response.ok) {
      const body = await response.text();
      throw new Error(`API-kall feilet (${response.status}): ${body}`);
    }

  },

  getAdminStats: () =>
    fetch(`${API_BASE_URL}/api/admin/stats`, { credentials: "include" }).then((r) => handle<AdminStats>(r)),

  getAdminInviteCodes: () =>
    fetch(`${API_BASE_URL}/api/admin/invite-codes`, { credentials: "include" }).then((r) =>
      handle<AdminInviteCode[]>(r),
    ),

  createAdminInviteCode: () =>
    fetch(`${API_BASE_URL}/api/admin/invite-codes`, {
      method: "POST",
      credentials: "include",
    }).then((r) => handle<AdminInviteCode>(r)),
};
