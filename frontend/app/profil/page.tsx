import { Suspense } from "react";
import { redirect } from "next/navigation";
import { getServerProfile, UnauthorizedError } from "@/lib/server-api";
import { ProfilSkeleton } from "@/app/components/ProfilSkeleton";
import ProfilClient from "./ProfilClient";

export const metadata = {
  title: "Profil — Barnehage-planlegger",
};

/**
 * Egen async komponent for SSR-hentingen — se app/ukeplan/page.tsx sin
 * `KalenderData` for hvorfor dette må ligge i en egen komponent for at
 * `<Suspense>` skal ha noe å vente på.
 */
async function ProfilData() {
  try {
    const profile = await getServerProfile();
    return <ProfilClient initialProfile={profile} />;
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      redirect("/");
    }
    // Backend nede/annen feil under SSR: fall tilbake til tomt skjema i
    // stedet for å la hele siden feile (samme fail-soft-filosofi som
    // getServerWhoAmI/de andre SSR-sidene).
    return <ProfilClient initialProfile={{ name: "", avatar: null }} />;
  }
}

/**
 * Skjema-/mutasjonslogikken ligger nå i `ProfilClient` (client-komponent) —
 * denne siden er en Server Component hvis eneste jobb er å hente det
 * innloggede navnet/avataren FØR HTML-en sendes (se lib/server-api.ts), og
 * strømme dette inn via en ekte `<Suspense>`-grense mens `<ProfilSkeleton>`
 * vises som fallback. Selve lagringen skjer fortsatt client-side i
 * `ProfilClient`, akkurat som før.
 */
export default function ProfilPage() {
  return (
    <main>
      <h1>Profil</h1>
      <Suspense fallback={<ProfilSkeleton />}>
        <ProfilData />
      </Suspense>
    </main>
  );
}
