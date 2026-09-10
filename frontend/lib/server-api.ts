import { cookies } from "next/headers";
import type { Assignment, AdminInviteCode, AdminStats, AvailableCalendar, Family, MyCalendar, Parent, Profile, WhoAmI } from "./api";

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

export async function getServerWhoAmI(): Promise<WhoAmI> {
  try {
    const header = await cookieHeader();

    const response = await fetch(`${BACKEND_URL}/auth/whoami`, {
      headers: header ? { Cookie: header } : {},

      cache: "no-store",
    });

    if (!response.ok) return { loggedIn: false };
    return (await response.json()) as WhoAmI;
  } catch {
    return { loggedIn: false };
  }
}

export class UnauthorizedError extends Error {}

export class ForbiddenError extends Error {}

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

    cache: "no-store",
  });

  if (response.status === 401) throw new UnauthorizedError();
  if (response.status === 403) throw new ForbiddenError();
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`API-kall feilet (${response.status}): ${body}`);
  }
  return response.json() as Promise<T>;
}

export const getServerAdminStats = () => serverFetch<AdminStats>("/api/admin/stats");
export const getServerAdminInviteCodes = () => serverFetch<AdminInviteCode[]>("/api/admin/invite-codes");

export const getServerParents = () => serverFetch<Parent[]>("/api/parents");
export const getServerAssignments = () => serverFetch<Assignment[]>("/api/assignments");

export const getServerFamily = () => serverFetch<Family>("/api/family");

export const getServerProfile = () => serverFetch<Profile>("/api/profile");

export const getServerMyCalendar = () => serverFetch<MyCalendar>("/api/calendars/mine");

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
