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

/** Mandag i uken `d` tilhører (uavhengig av hvilken ukedag `d` selv er). */
function mondayOf(d: Date): Date {
  const day = d.getDay(); // 0=søn,1=man,...,6=lør
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = new Date(d);
  monday.setDate(d.getDate() + diffToMonday);
  return monday;
}

/** De 5 hverdagene (mandag-fredag) for inneværende kalenderuke. */
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

/**
 * Egen async komponent for SSR-hentingen av ukens tildelinger — se
 * `KalenderData`/`FamilieData` (app/ukeplan, app/familie) for hvorfor dette
 * må ligge i en egen komponent for at `<Suspense>` skal ha noe å vente på.
 * I motsetning til de sidene er denne KUN rendret når `who.loggedIn` er
 * true (se `LandingPage` under) — men henting kan likevel 401 hvis
 * sesjonen akkurat har utløpt, og siden forsiden skal forbli offentlig
 * (ALDRI redirecte bort herfra) faller vi da tilbake til tomt innhold i
 * stedet for å redirecte, i motsetning til /ukeplan og /familie.
 */
async function ThisWeekData() {
  let parents: Parent[] = [];
  let assignments: Assignment[] = [];
  try {
    [parents, assignments] = await Promise.all([getServerParents(), getServerAssignments()]);
  } catch {
    // 401 (utløpt sesjon) eller backend nede/annen feil: vis tomt overblikk i
    // stedet for å redirecte eller la hele forsiden feile — forsiden skal
    // ALDRI redirecte bort fra seg selv, i motsetning til /ukeplan og
    // /familie (samme fail-soft-filosofi som getServerWhoAmI).
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

/**
 * Offentlig forside — vises for ALLE besøkende, innlogget eller ikke, uten å
 * gjøre noen familie-scopede API-kall for uinnloggede (de ville 401'et og
 * tvunget frem en redirect, som var akkurat problemet denne siden fikser).
 * Server Component: login-status hentes SERVER-SIDE via getServerWhoAmI()
 * (se lib/server-api.ts), samme mønster som RootLayout/Nav.tsx — ingen
 * client-side fetch, ingen "blink".
 *
 * Innloggede brukere ser et skrivebeskyttet overblikk over inneværende ukes
 * fordeling (se `WeekOverview`/`ThisWeekData`) i stedet for markedsføringstekst
 * — selve redigeringen skjer fortsatt kun på /ukeplan.
 */
export default async function LandingPage() {
  const who = await getServerWhoAmI();

  return (
    <main>
      <h1>Barnehage-planlegger</h1>

      {who.loggedIn ? (
        <section>
          <h2>Denne uken</h2>
          <Suspense fallback={<WeekOverviewSkeleton />}>
            <ThisWeekData />
          </Suspense>
          <p className="mt-2">
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
