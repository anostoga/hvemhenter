import { Suspense } from "react";
import { redirect } from "next/navigation";
import { getServerAdminInviteCodes, getServerAdminStats, ForbiddenError, UnauthorizedError } from "@/lib/server-api";
import type { AdminInviteCode, AdminStats } from "@/lib/api";
import AdminClient from "./AdminClient";

export const metadata = {
  title: "Admin — Barnehage-planlegger",
};

/**
 * Egen async komponent for SSR-hentingen — se app/innstillinger/page.tsx sin
 * `InnstillingerData` for hvorfor dette må ligge i en egen komponent for at
 * `<Suspense>` skal ha noe å vente på.
 *
 * `ForbiddenError` (403 — innlogget, men ikke admin) og `UnauthorizedError`
 * (401 — ikke innlogget i det hele tatt) håndteres likt her (begge sender til
 * forsiden): siden skal uansett ikke vise noe admin-innhold til en
 * ikke-admin, og en 403 avslører uansett ingenting mer enn "du har ikke
 * tilgang" (se backend AdminRoutes.kt).
 */
async function AdminData() {
  let stats: AdminStats;
  let inviteCodes: AdminInviteCode[];
  try {
    [stats, inviteCodes] = await Promise.all([getServerAdminStats(), getServerAdminInviteCodes()]);
  } catch (e) {
    if (e instanceof UnauthorizedError || e instanceof ForbiddenError) {
      redirect("/");
    }
    throw e;
  }
  return <AdminClient initialStats={stats} initialInviteCodes={inviteCodes} />;
}

/**
 * Server Component hvis eneste jobb er å hente admin-statistikk +
 * invitasjonskoder FØR HTML-en sendes (samme mønster som /innstillinger) —
 * selve "opprett ny kode"-mutasjonen skjer client-side i `AdminClient`.
 */
export default function AdminPage() {
  return (
    <main className="flex flex-col gap-6">
      <h1>Admin</h1>
      <Suspense fallback={<p>Laster …</p>}>
        <AdminData />
      </Suspense>
    </main>
  );
}
