import { Suspense } from "react";
import { redirect } from "next/navigation";
import { getServerProfile, UnauthorizedError } from "@/lib/server-api";
import { ProfilSkeleton } from "@/app/components/ProfilSkeleton";
import ProfilClient from "./ProfilClient";

export const metadata = {
  title: "Profil — Barnehage-planlegger",
};

async function ProfilData() {
  try {
    const profile = await getServerProfile();
    return <ProfilClient initialProfile={profile} />;
  } catch (e) {
    if (e instanceof UnauthorizedError) {
      redirect("/");
    }

    return <ProfilClient initialProfile={{ name: "", avatar: null }} />;
  }
}

export default function ProfilPage() {
  return (
    <main>
      <h1>Profil</h1>
      <Suspense fallback={<ProfilSkeleton />}>
        <ProfilData />
      </Suspense>
    </main>
  );
}
