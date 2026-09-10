import { Suspense } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import type { Assignment, Parent } from "@/lib/api";
import { getServerWhoAmI, getServerAssignments, getServerParents } from "@/lib/server-api";
import { CellAssignments } from "@/app/components/WeekCalendar";
import { WeekOverview, WeekOverviewSkeleton } from "@/app/components/WeekOverview";

function toIsoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function mondayOf(d: Date): Date {
  const day = d.getDay();
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = new Date(d);
  monday.setDate(d.getDate() + diffToMonday);
  return monday;
}

function currentWeekDays(): string[] {
  const monday = mondayOf(new Date());
  const days: string[] = [];
  for (let i = 0; i < 5; i++) {
    const d = new Date(monday);
    d.setDate(monday.getDate() + i);
    days.push(toIsoDate(d));
  }
  return days;
}

async function ThisWeekData() {
  let parents: Parent[] = [];
  let assignments: Assignment[] = [];
  try {
    [parents, assignments] = await Promise.all([getServerParents(), getServerAssignments()]);
  } catch {

    parents = [];
    assignments = [];
  }

  const days = currentWeekDays();
  const daySet = new Set(days);
  const assignmentsByDay = new Map<string, CellAssignments>();
  for (const a of assignments) {
    if (!daySet.has(a.date)) continue;
    const cell = assignmentsByDay.get(a.date) ?? {};
    cell[a.type] = a;
    assignmentsByDay.set(a.date, cell);
  }

  return <WeekOverview days={days} assignmentsByDay={assignmentsByDay} parents={parents} />;
}

export default async function LandingPage() {
  const who = await getServerWhoAmI();

  return (
    <main>

      {who.loggedIn ? (
        <section>
          <h2>Denne uken</h2>
          <p className="mb-2 sm:hidden">
            <Link href="/ukeplan">Gå til ukeplan for å endre</Link>
          </p>
          <Suspense fallback={<WeekOverviewSkeleton />}>
            <ThisWeekData />
          </Suspense>
          <p className="mt-2 hidden sm:block">
            <Link href="/ukeplan">Gå til ukeplan for å endre</Link>
          </p>
        </section>
      ) : (
        <section>
          <h2>Kom i gang</h2>
          <p>
            Allerede registrert i en familie? <a href={api.loginUrl()}>Logg inn med Google</a>
          </p>
          <p>
            Har du en invitasjonskode, eller vil du opprette en ny familie?{" "}
            <Link href="/join">Bli med i en familie</Link>
          </p>
        </section>
      )}
    </main>
  );
}
