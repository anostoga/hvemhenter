import { Suspense } from "react";
import { redirect } from "next/navigation";
import { getServerAvailableCalendars, getServerMyCalendar, UnauthorizedError } from "@/lib/server-api";
import type { AvailableCalendar, MyCalendar } from "@/lib/api";
import { InnstillingerSkeleton } from "@/app/components/InnstillingerSkeleton";
import InnstillingerClient from "./InnstillingerClient";

export const metadata = {
  title: "Innstillinger — Barnehage-planlegger",
};

async function InnstillingerData() {
  let myCalendar: MyCalendar;
  let availableCalendars: AvailableCalendar[] | null;
  try {
    [myCalendar, availableCalendars] = await Promise.all([getServerMyCalendar(), getServerAvailableCalendars()]);
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      redirect("/");
    }

    myCalendar = { calendarId: null, availabilityCalendarId: null, availabilityDisabled: false };
    availableCalendars = null;
  }
  return <InnstillingerClient initialMyCalendar={myCalendar} initialAvailableCalendars={availableCalendars} />;
}

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
