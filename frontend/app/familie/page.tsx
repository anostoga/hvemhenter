import { Suspense } from "react";
import { redirect } from "next/navigation";
import Link from "next/link";
import { getServerFamily, getServerParents, UnauthorizedError } from "@/lib/server-api";
import type { Family, Parent } from "@/lib/api";
import { Skeleton } from "@/components/ui/skeleton";

export const metadata = {
  title: "Familie — Barnehage-planlegger",
};

/**
 * Suspense-fallback som speiler den ekte seksjon-strukturen under (medlemsliste
 * + invitasjonskode) — se `FamilieData` for hvorfor SSR-hentingen må ligge i
 * en egen async komponent for at `<Suspense>` skal ha noe å vente på.
 */
function FamilieSkeleton() {
  return (
    <>
      <section>
        <h2>Medlemmer</h2>
        <ul className="list-none pl-0">
          <li className="mb-1">
            <Skeleton className="h-5 w-64" />
          </li>
          <li>
            <Skeleton className="h-5 w-64" />
          </li>
        </ul>
      </section>
      <section>
        <h2>Inviter den andre forelderen</h2>
        <Skeleton className="h-5 w-full max-w-md" />
        <div className="mt-2">
          <Skeleton className="h-6 w-32" />
        </div>
      </section>
    </>
  );
}

/**
 * Egen async komponent for selve SSR-hentingen (foreldre + invitasjonskode)
 * — siden har ingen mutasjoner/interaktivitet i det hele tatt (ren visning),
 * så det trengs ingen client-komponent-splitting slik som i /ukeplan og
 * /profil: denne komponenten rendrer det ekte innholdet direkte.
 */
async function FamilieData() {
  let parents: Parent[];
  let family: Family | null;
  try {
    [parents, family] = await Promise.all([getServerParents(), getServerFamily()]);
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      redirect("/");
    }
    // Backend nede/annen feil under SSR: vis siden med tomt innhold i stedet
    // for å la hele siden feile (samme fail-soft-filosofi som getServerWhoAmI).
    parents = [];
    family = null;
  }

  return (
    <>
      <section>
        <h2>Medlemmer</h2>
        <ul>
          {parents.map((p) => (
            <li key={p.id}>
              {p.name}: {p.connected ? "✅ tilkoblet Google Kalender" : "ikke tilkoblet Google Kalender ennå"}
            </li>
          ))}
        </ul>
      </section>

      <section>
        <h2>Inviter den andre forelderen</h2>
        {family === null ? null : family.inviteCode ? (
          <>
            <p>
              Gi denne koden til den andre forelderen — de skriver den inn på{" "}
              <Link href="/join">bli med i en familie</Link>-siden for å koble seg til familien din:
            </p>
            <p>
              <code>{family.inviteCode}</code>
            </p>
          </>
        ) : (
          <p>Familien har allerede to foreldre — det finnes ingen aktiv invitasjonskode.</p>
        )}
      </section>
    </>
  );
}

/**
 * Viser hvem som er med i familien (parents fra /api/parents) og
 * invitasjonskoden andre trenger for å bli med (family.inviteCode). Koden er
 * engangsbruk og blir `null` server-side så snart familien har fått forelder
 * #2 — da er det ingen kode igjen å vise.
 *
 * Ren visning, ingen mutasjoner — hele siden er derfor en Server Component
 * (ingen "use client" nødvendig), med data hentet server-side via
 * `getServerParents`/`getServerFamily` (se lib/server-api.ts) og strømmet inn
 * gjennom en ekte `<Suspense>`-grense mens `<FamilieSkeleton>` vises.
 */
export default function FamiliePage() {
  return (
    <main>
      <h1>Familie</h1>
      <Suspense fallback={<FamilieSkeleton />}>
        <FamilieData />
      </Suspense>
    </main>
  );
}
