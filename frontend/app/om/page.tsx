export const metadata = {
  title: "Om Hvem henter — Barnehage-planlegger",
};

export default function OmPage() {
  return (
    <main>
      <h1>Om HvemHenter.no</h1>
      <div className="space-y-4">
        <p>
          HvemHenter.no hjelper foreldre med å fordele levering og henting i
          barnehagen. Appen foreslår automatisk hvem som bør ta en gitt dag, basert på
          tidligere fordeling (og ledig tid i Google Kalender om man ønsker dette) — men alle forslag kan overstyres manuelt.
        </p>
        <p>
          Det er mulig å legge til &quot;hjelpere&quot; (for eksempel besteforeldre) som
          kan tildeles levering/henting uten selv å logge inn i appen.
        </p>
        <p>
          Innlogging skjer med Google-konto. Se{" "}
          <a href="/personvern">personvernerklæringen</a> for hvilke opplysninger som lagres og hvorfor.
        </p>
        <p>
          Prosjektet er open source. Kildekoden finnes på{" "}
          <a href="https://github.com/anostoga/barnehage" target="_blank" rel="noopener noreferrer">
            github.com/anostoga/barnehage
          </a>
          , der du også kan melde feil eller stille spørsmål.
        </p>
      </div>
    </main>
  );
}
