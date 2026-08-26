import Link from "next/link";

export const metadata = {
  title: "Tilkoblet — Barnehage-planlegger",
};

export default function TilkobletPage() {
  return (
    <main>
      <h1>Kalender tilkoblet ✅</h1>
      <p>Google-kalenderen din er nå koblet til. Du kan lukke denne siden eller gå tilbake.</p>
      <p>
        <Link href="/">Tilbake til forsiden</Link>
      </p>
    </main>
  );
}
