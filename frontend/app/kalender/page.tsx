"use client";

import { useEffect, useState } from "react";
import { api, Assignment, AssignmentType, Parent, Suggestion } from "@/lib/api";

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Fordelingslogikken (datovelger, forslag, bekreftelse og historikk) — flyttet
 * hit fra dashboard for å skille "sett opp familie/kalender"-siden fra den
 * daglige bruken (foreslå og bekrefte hvem som henter/leverer).
 */
export default function KalenderPage() {
  const [parents, setParents] = useState<Parent[]>([]);
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [date, setDate] = useState(today());
  const [type, setType] = useState<AssignmentType>("DROPOFF");
  const [suggestion, setSuggestion] = useState<Suggestion | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    api.getParents().then(setParents).catch((e) => setError(String(e)));
    refreshHistory();
  }, []);

  function refreshHistory() {
    api.getAssignments().then(setAssignments).catch((e) => setError(String(e)));
  }

  async function fetchSuggestion() {
    setError(null);
    setLoading(true);
    try {
      const result = await api.getSuggestion(date, type);
      setSuggestion(result);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }

  async function assignTo(parentId: string, source: "AUTO" | "MANUAL") {
    setError(null);
    setLoading(true);
    try {
      await api.assign({ date, type, parentId, source });
      setSuggestion(null);
      refreshHistory();
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }

  function parentName(id: string): string {
    return parents.find((p) => p.id === id)?.name ?? id;
  }

  return (
    <main>
      <h1>Kalender</h1>
      {error && <p className="error">{error}</p>}

      <section>
        <h2>Foreslå fordeling</h2>
        <label>
          Dato:{" "}
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <br />
        <label>
          Type:{" "}
          <select value={type} onChange={(e) => setType(e.target.value as AssignmentType)}>
            <option value="DROPOFF">Levering</option>
            <option value="PICKUP">Henting</option>
          </select>
        </label>
        <br />
        <button onClick={fetchSuggestion} disabled={loading}>
          Hent forslag
        </button>

        {suggestion && (
          <div>
            <p>
              Forslag: <strong>{parentName(suggestion.suggestedParentId)}</strong> — {suggestion.reason}
              {suggestion.conflict && " ⚠️ bekreft manuelt"}
            </p>
            {parents.map((p) => (
              <button key={p.id} onClick={() => assignTo(p.id, p.id === suggestion.suggestedParentId ? "AUTO" : "MANUAL")} disabled={loading}>
                Bekreft {p.name}
              </button>
            ))}
          </div>
        )}
      </section>

      <section>
        <h2>Historikk</h2>
        <ul>
          {assignments.slice(0, 20).map((a) => (
            <li key={a.id}>
              {a.date} — {a.type === "DROPOFF" ? "Levering" : "Henting"}: {parentName(a.parentId)} ({a.source === "AUTO" ? "auto" : "manuelt"})
              {" "}
              {parents
                .filter((p) => p.id !== a.parentId)
                .map((p) => (
                  <button
                    key={p.id}
                    disabled={loading}
                    onClick={async () => {
                      setError(null);
                      setLoading(true);
                      try {
                        // Sender samme dato/type på nytt — backend gjenkjenner at det
                        // allerede finnes en tildeling for den kombinasjonen og
                        // erstatter den (oppdaterer forelder, bytter ev. kalenderhendelse).
                        await api.assign({ date: a.date, type: a.type, parentId: p.id, source: "MANUAL" });
                        refreshHistory();
                      } catch (e) {
                        setError(String(e));
                      } finally {
                        setLoading(false);
                      }
                    }}
                  >
                    Endre til {p.name}
                  </button>
                ))}
              {" "}
              {a.id && (
                <button
                  disabled={loading}
                  onClick={async () => {
                    if (!confirm("Slette denne bekreftede tildelingen (og en ev. kalenderhendelse)?")) return;
                    setError(null);
                    setLoading(true);
                    try {
                      await api.deleteAssignment(a.id!);
                      refreshHistory();
                    } catch (e) {
                      setError(String(e));
                    } finally {
                      setLoading(false);
                    }
                  }}
                >
                  Slett
                </button>
              )}
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
