"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

type Step =
  | { kind: "loading" }
  | { kind: "expired" }
  | { kind: "choose"; name: string; csrfToken: string }
  | { kind: "join"; name: string; csrfToken: string }
  | { kind: "done" };

export default function VelkommenClient() {
  const [step, setStep] = useState<Step>({ kind: "loading" });
  const [code, setCode] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.onboardingStatus().then((result) => {
      if (cancelled) return;
      if (!result.ok) {
        setStep({ kind: "expired" });
        return;
      }
      setStep({ kind: "choose", name: result.data.name, csrfToken: result.data.csrfToken });
    });
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleCreateFamily() {
    if (step.kind !== "choose") return;
    setSubmitting(true);
    setError(null);
    const result = await api.onboardingCreateFamily(step.csrfToken);
    setSubmitting(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setStep({ kind: "done" });
    window.location.href = "/";
  }

  async function handleJoinFamily(e: React.FormEvent) {
    e.preventDefault();
    if (step.kind !== "join" && step.kind !== "choose") return;
    const csrfToken = step.csrfToken;
    setSubmitting(true);
    setError(null);
    const result = await api.onboardingJoinFamily(code, csrfToken);
    setSubmitting(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setStep({ kind: "done" });
    window.location.href = "/";
  }

  if (step.kind === "loading") {
    return <p>Laster …</p>;
  }

  if (step.kind === "expired") {
    return (
      <>
        <h1>Økten er utløpt</h1>
        <p>Det tok for lang tid å velge — logg inn med Google på nytt for å fortsette.</p>
        <Button asChild className="self-start">
          <a href={api.loginUrl()}>Logg inn med Google</a>
        </Button>
      </>
    );
  }

  if (step.kind === "done") {
    return <p>Fullført, sender deg videre …</p>;
  }

  return (
    <>
      <h1>Velkommen, {step.name}!</h1>
      <p>Du er logget inn med Google for første gang. Vil du opprette en ny familie, eller bli med i en du allerede har fått en kode til?</p>
      {error && (
        <p role="alert" className="text-destructive">
          {error}
        </p>
      )}

      {step.kind === "choose" && (
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2 rounded-md border p-4">
            <h2 className="text-base font-semibold">Opprett ny familie</h2>
            <p className="text-sm text-muted-foreground">
              Du blir forelder 1. Du får en invitasjonskode du kan dele med den andre forelderen etterpå.
            </p>
            <Button onClick={handleCreateFamily} disabled={submitting} className="self-start">
              {submitting ? "Oppretter …" : "Opprett ny familie"}
            </Button>
          </div>

          <div className="flex flex-col gap-2 rounded-md border p-4">
            <h2 className="text-base font-semibold">Bli med i en familie</h2>
            <p className="text-sm text-muted-foreground">
              Skriv inn invitasjonskoden du har fått fra den andre forelderen.
            </p>
            <form onSubmit={handleJoinFamily} className="flex flex-col gap-3">
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
              <Button type="submit" variant="outline" disabled={submitting} className="self-start">
                {submitting ? "Blir med …" : "Bli med"}
              </Button>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
