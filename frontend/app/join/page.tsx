"use client";

import { useState } from "react";

export default function JoinPage() {
  const [code, setCode] = useState("");
  const [status, setStatus] = useState<"idle" | "loading">("idle");

  const backendUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setStatus("loading");
    // /join/start er en GET-rute som (etter en rask kode-sjekk) redirigerer
    // videre til Googles innloggingsside — naviger nettleseren dit direkte,
    // ikke fetch() (det er ikke et JSON-API-kall).
    window.location.href = `${backendUrl}/join/start?code=${encodeURIComponent(code)}`;
  }

  return (
    <main>
      <h1>Bli med i en familie</h1>
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
