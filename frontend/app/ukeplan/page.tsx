import { Suspense } from "react";
import { redirect } from "next/navigation";
import { getServerAssignments, getServerParents, UnauthorizedError } from "@/lib/server-api";
import type { Assignment, Parent } from "@/lib/api";
import { WeekCalendarSkeleton } from "@/app/components/WeekCalendarSkeleton";
import KalenderClient from "./KalenderClient";

export const metadata = {
  title: "Kalender — Barnehage-planlegger",
};

async function KalenderData() {
  let parents: Parent[];
  let assignments: Assignment[];
  try {
    [parents, assignments] = await Promise.all([getServerParents(), getServerAssignments()]);
  } catch (e) {
    if (e instanceof UnauthorizedError) {

      redirect("/");
    }

    parents = [];
    assignments = [];
  }
  return <KalenderClient initialParents={parents} initialAssignments={assignments} />;
}

export default function KalenderPage() {
  return (
    <main>
      <h1>Kalender</h1>
      <section>
        <Suspense fallback={<WeekCalendarSkeleton />}>
          <KalenderData />
        </Suspense>
      </section>
    </main>
  );
}
