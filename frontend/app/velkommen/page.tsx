import VelkommenClient from "./VelkommenClient";

export const metadata = {
  title: "Velkommen — Barnehage-planlegger",
};

export default function VelkommenPage() {
  return (
    <main className="flex flex-col gap-6">
      <VelkommenClient />
    </main>
  );
}
