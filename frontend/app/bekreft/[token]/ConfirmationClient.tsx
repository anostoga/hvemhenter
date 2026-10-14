"use client";

import { useEffect, useState } from "react";
import { api, AssignmentConfirmation } from "@/lib/api";
import { Button } from "@/components/ui/button";

interface ConfirmationClientProps {
  token: string;
}

const TYPE_LABEL = {
  DROPOFF: "levering",
  PICKUP: "henting",
} as const;

export default function ConfirmationClient({ token }: ConfirmationClientProps) {
  const [confirmation, setConfirmation] = useState<AssignmentConfirmation | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.getAssignmentConfirmation(token)
      .then((result) => {
        if (!cancelled) setConfirmation(result);
      })
      .catch((reason: unknown) => {
        if (!cancelled) setError(String(reason));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  async function answer(answer: "ACCEPTED" | "DECLINED") {
    setSubmitting(true);
    setError(null);
    try {
      await api.answerAssignmentConfirmation(token, answer);
      setConfirmation((current) => current ? { ...current, status: answer } : current);
    } catch (reason) {
      setError(String(reason));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="mx-auto max-w-xl">
      <h1>Bekreft oppgaven</h1>
      {loading && <p role="status">Henter oppgaven …</p>}
      {!loading && error && <p className="text-destructive" role="alert">{error}</p>}
      {confirmation && (
        <section>
          <p>
            Hei {confirmation.helperName}! Du er satt opp på {TYPE_LABEL[confirmation.type]}{" "}
            {new Date(`${confirmation.date}T12:00:00`).toLocaleDateString("nb-NO", {
              weekday: "long",
              day: "numeric",
              month: "long",
            })}
            .
          </p>
          {confirmation.status === "PENDING" ? (
            <div className="flex flex-wrap gap-3">
              <Button type="button" disabled={submitting} onClick={() => answer("ACCEPTED")}>
                Godta
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={submitting}
                onClick={() => answer("DECLINED")}
              >
                Avslå
              </Button>
            </div>
          ) : (
            <p role="status">
              {confirmation.status === "ACCEPTED" ? "Du har godtatt oppgaven." : "Du har avslått oppgaven."}
            </p>
          )}
        </section>
      )}
    </main>
  );
}
