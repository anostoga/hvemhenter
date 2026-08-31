"use client";

import { useEffect, useState } from "react";
import { api, WhoAmI } from "@/lib/api";

/**
 * Offentlig forside — vises for ALLE besøkende, innlogget eller ikke, uten å
 * gjøre noen familie-scopede API-kall (de ville 401'et for uinnloggede og
 * tvunget frem en redirect, som var akkurat problemet denne siden fikser).
 * Login-status hentes kun via /auth/whoami, som alltid svarer 200.
 */
export default function LandingPage() {
  const [who, setWho] = useState<WhoAmI | null>(null);

  useEffect(() => {
    api.whoAmI().then(setWho).catch(() => setWho({ loggedIn: false }));
  }, []);

  return (
    <main>
      <h1>Barnehage-planlegger</h1>
      <p>
        Fordel levering og henting i barnehagen rettferdig mellom to foreldre — foreslå automatisk,
        bekreft manuelt, og hold styr på historikken. Bekreftede tildelinger legges inn i en delt
        Google-kalender.
      </p>

      {who?.loggedIn ? (
        <p>
          Du er innlogget som <strong>{who.name ?? "deg"}</strong>.{" "}
          <a href="/innstillinger">Gå til innstillinger</a>
        </p>
      ) : (
        <section>
          <h2>Kom i gang</h2>
          <p>
            Allerede registrert i en familie? <a href={api.loginUrl()}>Logg inn med Google</a>
          </p>
          <p>
            Har du en invitasjonskode, eller vil du opprette en ny familie?{" "}
            <a href="/join">Bli med i en familie</a>
          </p>
        </section>
      )}
    </main>
  );
}
