import { Suspense } from "react";
import { redirect } from "next/navigation";
import Link from "next/link";
import { getServerFamily, getServerParents, UnauthorizedError } from "@/lib/server-api";
import type { Family, Parent } from "@/lib/api";
import { Skeleton } from "@/components/ui/skeleton";
import { InviteCode } from "@/app/components/InviteCode";
import HelpersManager from "@/app/familie/HelpersManager";

export const metadata = {
  title: "Familie — Barnehage-planlegger",
};

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
        <h2>Hjelpere</h2>
        <Skeleton className="h-5 w-full max-w-md" />
      </section>
      <section>
        <h2>Inviter den andre forelderen</h2>
        <Skeleton className="h-5 w-full max-w-md" />
        <div className="mt-2">
          <Skeleton className="h-12 w-48" />
        </div>
      </section>
    </>
  );
}

async function FamilieData() {
  let parents: Parent[];
  let family: Family | null;
  try {
    [parents, family] = await Promise.all([getServerParents(), getServerFamily()]);
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      redirect("/");
    }

    parents = [];
    family = null;
  }

  return (
    <>
      <HelpersManager initialParents={parents} />

      <section>
        <h2>Inviter den andre forelderen</h2>
        {family === null ? null : family.inviteCode ? (
          <>
            <p>
              Gi denne koden til den andre forelderen — de skriver den inn på{" "}
              <Link href="/join">bli med i en familie</Link>-siden for å koble seg til familien din:
            </p>
            <div className="mt-2">
              <InviteCode code={family.inviteCode} />
            </div>
          </>
        ) : (
          <p>Familien har allerede to foreldre — det finnes ingen aktiv invitasjonskode.</p>
        )}
      </section>
    </>
  );
}

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
