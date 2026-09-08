"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

function NotRegisteredNotice() {
  const params = useSearchParams();
  if (params.get("error") !== "ikke_registrert") return null;
  return (
    <p role="alert">
      Fant ingen familie knyttet til Google-kontoen din ennå. Skriv inn koden du har fått under for å
      bli med i en familie.
    </p>
  );
}

/**
 * Forhåndsutfyller kode-feltet fra `?code=`-query-parameteren (se
 * AdminClient.tsx sin "Kopier lenke"-knapp, som lenker til `/join?code=...`)
 * — brukeren trenger da ikke å skrive inn koden selv. Egen komponent (i
 * stedet for å lese `useSearchParams()` direkte i `JoinPage`) fordi
 * `useSearchParams()` krever en `<Suspense>`-grense i Next.js, se
 * `NotRegisteredNotice` over for samme mønster.
 */
function CodeFromQuery({ onCode }: { onCode: (code: string) => void }) {
  const params = useSearchParams();
  const codeParam = params.get("code");
  useEffect(() => {
    if (codeParam) onCode(codeParam);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [codeParam]);
  return null;
}

export default function JoinPage() {
  const [code, setCode] = useState("");
  const [status, setStatus] = useState<"idle" | "loading">("idle");

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setStatus("loading");
    // /join/start er en GET-rute som (etter en rask kode-sjekk) redirigerer
    // videre til Googles innloggingsside — naviger nettleseren dit direkte,
    // ikke fetch() (det er ikke et JSON-API-kall). Relativ URL: Next.js
    // proxyer /join/* til backend (se rewrites() i next.config.mjs), slik at
    // sesjonscookien fra /auth/google/callback ender opp på riktig origin.
    window.location.href = `/join/start?code=${encodeURIComponent(code)}`;
  }

  return (
    <main>
      <h1>Bli med i en familie</h1>
      <Suspense fallback={null}>
        <NotRegisteredNotice />
      </Suspense>
      <Suspense fallback={null}>
        <CodeFromQuery onCode={setCode} />
      </Suspense>
      <p>Skriv inn koden du har fått for å opprette en ny familie, eller invitasjonskoden fra den andre forelderen.</p>
      <form onSubmit={handleSubmit}>
        <Label htmlFor="code">Kode</Label>
        <Input
          id="code"
          name="code"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          required
        />
        <Button type="submit" disabled={status === "loading"}>
          Fortsett
        </Button>
      </form>
    </main>
  );
}
