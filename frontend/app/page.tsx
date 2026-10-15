import { Suspense } from "react";
import Link from "next/link";
import { api } from "@/lib/api";
import type { Assignment, Parent } from "@/lib/api";
import { getServerWhoAmI, getServerAssignments, getServerParents } from "@/lib/server-api";
import { CellAssignments } from "@/app/components/WeekCalendar";
import { WeekOverview, WeekOverviewSkeleton } from "@/app/components/WeekOverview";
import { ArrowRight, CalendarDays, Handshake, UsersRound } from "lucide-react";

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
        <div className="space-y-12 sm:space-y-16">
          <section className="grid overflow-hidden rounded-3xl border border-border bg-card shadow-sm sm:grid-cols-[1.05fr_0.95fr] sm:items-center">
            <div className="px-6 py-8 sm:px-9 sm:py-10">
              <p className="mb-3 text-sm font-semibold uppercase tracking-[0.12em] text-primary">
                En enklere barnehagehverdag
              </p>
              <h1 className="mb-4 text-3xl font-bold leading-tight sm:text-4xl">
                Hvem henter i dag?
                <span className="block text-primary">En plan hele familien ser.</span>
              </h1>
              <p className="mb-6 max-w-prose text-muted-foreground">
                Samle familiens levering og henting på ett sted. HvemHenter hjelper dere med å
                fordele dagene og holde ukeplanen oppdatert.
              </p>
              <div className="flex flex-wrap items-center gap-3">
                <a
                  href={api.loginUrl()}
                  className="inline-flex items-center gap-2 rounded-full bg-primary px-5 py-3 font-semibold text-primary-foreground no-underline transition-colors hover:bg-primary/90"
                >
                  Kom i gang med Google
                  <ArrowRight aria-hidden="true" className="size-4" />
                </a>
                <Link
                  href="/join"
                  className="rounded-full px-4 py-3 font-semibold text-foreground no-underline hover:bg-muted"
                >
                  Jeg har en invitasjonskode
                </Link>
              </div>
            </div>

            <div className="bg-muted/70 p-5 sm:p-8">
              <div className="mx-auto max-w-sm rotate-1 rounded-2xl border border-border bg-card p-4 shadow-sm sm:p-5">
                <div className="mb-4 flex items-center justify-between">
                  <div>
                    <p className="m-0 text-sm font-semibold">Eksempel på ukeplan</p>
                    <p className="m-0 mt-1 text-xs text-muted-foreground">Levering og henting samlet</p>
                  </div>
                  <CalendarDays aria-hidden="true" className="size-5 text-primary" />
                </div>
                <div className="space-y-2">
                  {[
                    { day: "Mandag", name: "Mamma", color: "bg-accent" },
                    { day: "Tirsdag", name: "Pappa", color: "bg-secondary" },
                    { day: "Onsdag", name: "Bestefar", color: "bg-[#f8edcf]" },
                  ].map(({ day, name, color }) => (
                    <div className={`flex items-center justify-between rounded-xl ${color} px-3 py-2.5`} key={day}>
                      <span className="text-sm font-medium">{day}</span>
                      <span className="text-sm">{name} henter</span>
                    </div>
                  ))}
                </div>
                <p className="m-0 mt-3 text-xs text-muted-foreground">
                  Et eksempel – planen bestemmer dere selv.
                </p>
              </div>
            </div>
          </section>

          <section aria-labelledby="features-heading">
            <div className="mb-6 text-center">
              <h2 id="features-heading" className="mb-2 text-2xl font-bold sm:text-3xl">
                Mer oversikt. Mindre styr.
              </h2>
              <p className="m-0 text-muted-foreground">
                Dette hjelper familien med å få hente- og leveringskabalen til å gå opp.
              </p>
            </div>
            <div className="grid gap-4 sm:grid-cols-3">
              <article className="rounded-2xl border border-border bg-card p-5">
                <div className="mb-4 inline-flex rounded-xl bg-accent p-3 text-accent-foreground">
                  <Handshake aria-hidden="true" className="size-5" />
                </div>
                <h3 className="mb-2 font-semibold">En jevnere fordeling</h3>
                <p className="m-0 text-sm leading-relaxed text-muted-foreground">
                  Få forslag basert på hvem som har tatt vaktene tidligere. Juster planen når
                  hverdagen krever det.
                </p>
              </article>
              <article className="rounded-2xl border border-border bg-card p-5">
                <div className="mb-4 inline-flex rounded-xl bg-secondary p-3 text-secondary-foreground">
                  <CalendarDays aria-hidden="true" className="size-5" />
                </div>
                <h3 className="mb-2 font-semibold">En plan alle ser</h3>
                <p className="m-0 text-sm leading-relaxed text-muted-foreground">
                  Se hvem som leverer og henter hver dag i en felles ukeplan. Koble til Google
                  Kalender for å ta hensyn til når dere har tid.
                </p>
              </article>
              <article className="rounded-2xl border border-border bg-card p-5">
                <div className="mb-4 inline-flex rounded-xl bg-[#f8edcf] p-3 text-[#72531b]">
                  <UsersRound aria-hidden="true" className="size-5" />
                </div>
                <h3 className="mb-2 font-semibold">Hele familien kan bidra</h3>
                <p className="m-0 text-sm leading-relaxed text-muted-foreground">
                  Besteforeldre og andre hjelpere kan også settes opp på hente- og
                  leveringsdagene.
                </p>
              </article>
            </div>
          </section>
        </div>
      )}
    </main>
  );
}
