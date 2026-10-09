"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

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
    <main className="flex flex-col gap-6">
      <h1>Bli med i en familie</h1>
      <Suspense fallback={null}>
        <CodeFromQuery onCode={setCode} />
      </Suspense>
      <p>
        Skriv inn invitasjonskoden du har fått fra den andre forelderen for å bli med i familien
        hens. Skal du opprette en helt ny familie i stedet? Du trenger ingen kode for det — logg
        inn med Google fra forsiden.
      </p>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="code">Invitasjonskode</Label>
          <Input
            id="code"
            name="code"
            className="max-w-xs"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            required
          />
        </div>
        <Button type="submit" disabled={status === "loading"} className="self-start">
          Fortsett
        </Button>
      </form>
    </main>
  );
}
