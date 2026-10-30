"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import type { Assignment, Parent } from "@/lib/api";
import type { CellAssignments } from "@/app/components/WeekCalendar";
import { frontpageWeek } from "@/lib/frontpage-week";
import { WeekOverview, WeekOverviewSkeleton } from "@/app/components/WeekOverview";
import { CalendarViewActivity } from "@/app/components/CalendarViewActivity";

export function FrontpageWeekOverview({ parents, assignments }: {
  parents: Parent[];
  assignments: Assignment[];
}) {
  const [week, setWeek] = useState<ReturnType<typeof frontpageWeek> | null>(null);

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    function update() {
      clearTimeout(timer);
      const now = new Date();
      setWeek(frontpageWeek(now));
      const nextUpdate = new Date(now);
      nextUpdate.setHours(now.getDay() === 5 && now.getHours() < 17 ? 17 : 24, 0, 0, 0);
      timer = setTimeout(update, nextUpdate.getTime() - now.getTime());
    }
    update();
    window.addEventListener("focus", update);
    return () => {
      clearTimeout(timer);
      window.removeEventListener("focus", update);
    };
  }, []);

  const assignmentsByDay = new Map<string, CellAssignments>();
  for (const assignment of assignments) {
    const cell = assignmentsByDay.get(assignment.date) ?? {};
    cell[assignment.type] = assignment;
    assignmentsByDay.set(assignment.date, cell);
  }

  return (
    <>
      {week && <CalendarViewActivity />}
      <h2>{week ? (week.isNextWeek ? "Neste uke" : "Denne uken") : "Ukeoversikt"}</h2>
      <p className="mb-2 sm:hidden">
        <Link href="/ukeplan">Gå til ukeplan for å endre</Link>
      </p>
      {week ? (
        <WeekOverview days={week.days} assignmentsByDay={assignmentsByDay} parents={parents} />
      ) : (
        <WeekOverviewSkeleton />
      )}
      <p className="mt-2 hidden sm:block">
        <Link href="/ukeplan">Gå til ukeplan for å endre</Link>
      </p>
    </>
  );
}
