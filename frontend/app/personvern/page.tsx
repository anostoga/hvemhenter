export const metadata = {
  title: "Personvernerklæring — HvemHenter.no",
};

/**
 * Enkel, statisk personvernside — ingen SSR-datahenting, ingen
 * innloggingskrav (lenkes fra Footer og er synlig for alle besøkende).
 * Innholdet er basert på hva backend faktisk lagrer (se
 * backend/src/main/kotlin/no/pilot/barnehage/db/PostgresTables.kt og
 * db/migration/*.sql) — hold denne siden oppdatert hvis datamodellen endres.
 *
 * Hver `<h2>` pakkes inn i en egen `<section>` — globals.css sin
 * `section { @apply mb-8; }` gir dermed luft mellom overskrift-blokkene uten
 * at vi må style hver enkelt overskrift/paragraf manuelt.
 */
export default function PersonvernPage() {
  return (
    <main>
      <h1>Personvernerklæring</h1>
      <section>
        <p>
          HvemHenter.no er et hobbyprosjekt (ikke et kommersielt selskap) som hjelper foreldre med å fordele
          levering og henting i barnehagen. Denne siden forklarer hvilke personopplysninger appen lagrer, hvorfor,
          og hvordan du kan få innsyn i eller be om sletting av dine opplysninger.
        </p>
      </section>

      <section>
        <h2>Hvilke opplysninger vi lagrer</h2>
        <p className="mb-4">Når du logger inn med Google, og bruker appen, lagrer vi:</p>
        <ul className="mb-4 list-disc space-y-2 pl-5">
          <li>Navn, e-postadresse og en stabil Google-konto-ID, hentet fra Google ved innlogging.</li>
          <li>En valgfri emoji-avatar du selv velger i profilen — ikke bildet fra Google-kontoen din.</li>
          <li>
            Hvilken familie du tilhører, og hvilken Google-kalender familien bruker for
            levering/henting-hendelser.
          </li>
          <li>
            Tildelinger (hvem som henter/leverer hvilken dag), inkludert om tildelingen ble foreslått
            automatisk eller satt manuelt, og ID-en til den tilhørende Google Kalender-hendelsen.
          </li>
          <li>
            Krypterte Google OAuth-tilgangs- og fornyelsestokener, slik at appen kan lese ledig tid i og
            opprette hendelser i kalenderen din på dine vegne.
          </li>
        </ul>
        <p>
          Familien kan i tillegg registrere &quot;hjelpere&quot; (f.eks. besteforeldre) med kun navn og valgfri
          avatar — hjelpere har ingen Google-konto, e-postadresse eller kalendertilgang knyttet til seg i appen.
        </p>
      </section>

      <section>
        <h2>Hvorfor vi lagrer dette</h2>
        <p>
          Opplysningene brukes utelukkende til å drifte appens kjernefunksjon: vise hvem som henter/leverer når,
          foreslå en rettferdig fordeling basert på historikk og ledig tid, og synkronisere dette med familiens
          delte Google-kalender. Behandlingen er basert på samtykke — du logger aktivt inn med Google og gir
          derved appen tilgang til de kalender-scopene som er nødvendige for dette.
        </p>
      </section>

      <section>
        <h2>Informasjonskapsler (cookies)</h2>
        <p>
          Appen setter én sesjonscookie (<code>bhg_session</code>). Den er
          signert, kun lesbar av serveren (HttpOnly) og inneholder ingen navn eller e-postadresse — kun
          interne ID-er for deg og familien din. Den slettes når du logger ut, eller utløper automatisk etter
          30 dager.
        </p>
      </section>

      <section>
        <h2>Hvem vi deler opplysninger med</h2>
        <p className="mb-4">
          Vi selger eller deler aldri opplysninger dine til reklame- eller markedsføringsformål. Følgende
          tjenester brukes for å drifte appen:
        </p>
        <ul className="list-disc space-y-2 pl-5">
          <li>
            <strong>Google</strong> — for innlogging (OAuth) og for å lese/skrive kalenderhendelser i din
            Google-kalender.
          </li>
          <li>
            <strong>Supabase</strong> — hoster PostgreSQL-databasen der opplysningene lagres.
          </li>
          <li>
            <strong>Fly.io</strong> — hoster backend-tjenesten (API-et).
          </li>
          <li>
            <strong>Vercel</strong> — hoster frontend (denne nettsiden).
          </li>
        </ul>
      </section>

      <section>
        <h2>Hvor lenge vi lagrer opplysningene</h2>
        <p>
          Opplysningene lagres så lenge familien er aktiv i appen. Enkelttildelinger og hjelpere kan slettes
          fortløpende av familien selv i appen. Det finnes foreløpig ingen selvbetjent knapp for å slette hele
          brukerkontoen/familien — ta kontakt (se under) hvis du ønsker at alle dine opplysninger skal slettes,
          så gjør vi det manuelt.
        </p>
      </section>

      <section>
        <h2>Dine rettigheter</h2>
        <p>
          Du kan når som helst be om innsyn i, retting av, eller sletting av opplysningene vi har lagret om
          deg. Du kan selv endre navn og avatar under Profil, og fjerne enkelttildelinger og hjelpere fra
          Familie-siden. For alt annet — inkludert full sletting av konto/familie — ta kontakt via GitHub, se
          under.
        </p>
      </section>

      <section>
        <h2>Sikkerhet</h2>
        <p>
          Google-tokenene dine lagres kryptert i databasen, aldri i klartekst. Sesjonscookien er signert og
          HttpOnly, slik at den ikke kan leses eller endres av JavaScript i nettleseren. All trafikk mellom
          nettleseren din og appen går over HTTPS.
        </p>
      </section>

      <section>
        <h2>Kontakt</h2>
        <p>
          HvemHenter.no driftes som et open source-hobbyprosjekt uten et eget organisasjonsnummer. Spørsmål om
          personvern, innsynskrav eller sletting kan meldes som en{" "}
          <a href="https://github.com/anostoga/barnehage/issues" target="_blank" rel="noopener noreferrer">
            sak på GitHub
          </a>{" "}
          (unngå å inkludere personopplysninger direkte i en offentlig sak — vi tar heller kontakt for å
          avtale hvordan).
        </p>
      </section>
    </main>
  );
}
