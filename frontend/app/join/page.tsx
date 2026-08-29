"use client";

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";

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
      <p>Skriv inn koden du har fått for å opprette en ny familie, eller invitasjonskoden fra den andre forelderen.</p>
      <form onSubmit={handleSubmit}>
        <label htmlFor="code">Kode</label>
        <input
          id="code"
          name="code"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          required
        />
        <button type="submit" disabled={status === "loading"}>
          Fortsett
        </button>
      </form>
    </main>
  );
}
