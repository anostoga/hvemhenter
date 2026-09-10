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

function CodeFromQuery({ onCode }: { onCode: (code: string) => void }) {
  const params = useSearchParams();
  const codeParam = params.get("code");
  useEffect(() => {
    if (codeParam) onCode(codeParam);

  }, [codeParam]);
  return null;
}

export default function JoinPage() {
  const [code, setCode] = useState("");
  const [status, setStatus] = useState<"idle" | "loading">("idle");

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setStatus("loading");

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
