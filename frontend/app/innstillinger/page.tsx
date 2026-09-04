import { Suspense } from "react";
import { redirect } from "next/navigation";
import { getServerAvailableCalendars, getServerMyCalendar, UnauthorizedError } from "@/lib/server-api";
import type { AvailableCalendar, MyCalendar } from "@/lib/api";
import { InnstillingerSkeleton } from "@/app/components/InnstillingerSkeleton";
import InnstillingerClient from "./InnstillingerClient";

export const metadata = {
  title: "Innstillinger — Barnehage-planlegger",
};

/**
 * Egen async komponent for SSR-hentingen — se app/ukeplan/page.tsx sin
 * `KalenderData` for hvorfor dette må ligge i en egen komponent for at
 * `<Suspense>` skal ha noe å vente på. `getServerAvailableCalendars` kaster
 * ikke ved 409 (ikke tilkoblet Google ennå), kun ved 401/andre feil — se
 * lib/server-api.ts.
 */
async function InnstillingerData() {
  let myCalendar: MyCalendar;
  let availableCalendars: AvailableCalendar[] | null;
  try {
    [myCalendar, availableCalendars] = await Promise.all([getServerMyCalendar(), getServerAvailableCalendars()]);
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      redirect("/");
    }
    // Backend nede/annen feil under SSR: fall tilbake til tomt skjema i
    // stedet for å la hele siden feile (samme fail-soft-filosofi som
    // getServerWhoAmI/de andre SSR-sidene).
    myCalendar = { calendarId: null, availabilityCalendarId: null };
    availableCalendars = null;
  }
  return <InnstillingerClient initialMyCalendar={myCalendar} initialAvailableCalendars={availableCalendars} />;
}

/**
 * Skjema-/mutasjonslogikken ligger nå i `InnstillingerClient`
 * (client-komponent) — denne siden er en Server Component hvis eneste jobb
 * er å hente brukerens kalendertilkobling + tilgjengelige kalendere FØR
 * HTML-en sendes (se lib/server-api.ts), og strømme dette inn via en ekte
 * `<Suspense>`-grense mens `<InnstillingerSkeleton>` vises som fallback.
 * Selve lagringen skjer fortsatt client-side i `InnstillingerClient`,
 * akkurat som før.
 */
export default function InnstillingerPage() {
  return (
    <main>
      <h1>Innstillinger</h1>
      <Suspense fallback={<InnstillingerSkeleton />}>
        <InnstillingerData />
      </Suspense>
    </main>
  );
}
