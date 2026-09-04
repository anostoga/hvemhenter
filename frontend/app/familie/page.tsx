"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api, Family, Parent } from "@/lib/api";
import { FamilyIllustration } from "@/app/components/FamilyIllustration";

/**
 * Viser hvem som er med i familien (parents fra /api/parents) og
 * invitasjonskoden andre trenger for å bli med (family.inviteCode).
 * Koden er engangsbruk og blir `null` server-side så snart familien har
 * fått forelder #2 — da er det ingen kode igjen å vise.
 */
export default function FamiliePage() {
  const [parents, setParents] = useState<Parent[]>([]);
  const [family, setFamily] = useState<Family | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.getParents().then(setParents).catch((e) => setError(String(e)));
    api.getFamily().then(setFamily).catch((e) => setError(String(e)));
  }, []);

  return (
    <main>
      <h1>Familie</h1>
      {error && <p className="text-destructive">{error}</p>}

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
    </main>
  );
}
