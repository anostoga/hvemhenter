import Link from "next/link";
import { api } from "@/lib/api";
import { getServerWhoAmI } from "@/lib/server-api";

/**
 * Offentlig forside — vises for ALLE besøkende, innlogget eller ikke, uten å
 * gjøre noen familie-scopede API-kall (de ville 401'et for uinnloggede og
 * tvunget frem en redirect, som var akkurat problemet denne siden fikser).
 * Server Component: login-status hentes SERVER-SIDE via getServerWhoAmI()
 * (se lib/server-api.ts), samme mønster som RootLayout/Nav.tsx — ingen
 * client-side fetch, ingen "blink".
 */
export default async function LandingPage() {
  const who = await getServerWhoAmI();

  return (
    <main>
      <h1>Barnehage-planlegger</h1>
      <p>
        Fordel levering og henting i barnehagen rettferdig mellom to foreldre — foreslå automatisk,
        bekreft manuelt, og hold styr på historikken. Bekreftede tildelinger legges inn i en delt
        Google-kalender.
      </p>

      {who.loggedIn ? (
        <p>
          Du er innlogget som <strong>{who.name ?? "deg"}</strong>.{" "}
          <Link href="/innstillinger">Gå til innstillinger</Link>
        </p>
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
