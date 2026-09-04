import { Suspense } from "react";
import { redirect } from "next/navigation";
import { getServerAssignments, getServerParents, UnauthorizedError } from "@/lib/server-api";
import type { Assignment, Parent } from "@/lib/api";
import { WeekCalendarSkeleton } from "@/app/components/WeekCalendarSkeleton";
import KalenderClient from "./KalenderClient";

export const metadata = {
  title: "Kalender — Barnehage-planlegger",
};

/**
 * Async Server Component som gjør selve SSR-hentingen — holdt adskilt fra
 * `KalenderPage` under slik at `<Suspense>` får noe å strømme inn når
 * `await`-kallene er ferdige (en synkron side har ingenting å vente på, og
 * ville aldri vist fallback-en).
 */
async function KalenderData() {
  let parents: Parent[];
  let assignments: Assignment[];
  try {
    [parents, assignments] = await Promise.all([getServerParents(), getServerAssignments()]);
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      // Ingen (gyldig) sesjon — send til forsiden i stedet for å rendre en
      // tom "innlogget" side som først client-side oppdager at den ikke er
      // det (se lib/api.ts sin handle(), som gjør det samme for øvrige kall).
      redirect("/");
    }
    // Backend nede/annen nettverksfeil under SSR: fall tilbake til tom
    // initial-state i stedet for å la hele siden feile — samme
    // fail-soft-filosofi som getServerWhoAmI. Brukeren kan fortsatt bruke
    // siden når backend er tilbake (mutasjoner henter på nytt via
    // refreshAssignments i KalenderClient).
    parents = [];
    assignments = [];
  }
  return <KalenderClient initialParents={parents} initialAssignments={assignments} />;
}

/**
 * Fordelingslogikken er flyttet til `KalenderClient` (client-komponent) —
 * denne siden er nå en Server Component hvis eneste jobb er å hente
 * foreldre + tildelinger FØR HTML-en sendes (se lib/server-api.ts), og
 * strømme dette inn via en ekte `<Suspense>`-grense mens
 * `<WeekCalendarSkeleton>` vises som fallback. Alle mutasjoner (generer
 * forslag, nullstill uken, endre tildeling) skjer fortsatt client-side i
 * `KalenderClient`, akkurat som før.
 */
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
