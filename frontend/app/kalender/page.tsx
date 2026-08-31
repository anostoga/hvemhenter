"use client";

import { useEffect, useMemo, useState } from "react";
import { api, Assignment, AssignmentSource, AssignmentType, Parent } from "@/lib/api";
import { ActiveSuggestion, CellAssignments, WeekCalendar } from "@/app/components/WeekCalendar";

function toIsoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** Mandag i uken `d` tilhører (uavhengig av hvilken ukedag `d` selv er). */
function mondayOf(d: Date): Date {
  const day = d.getDay(); // 0=søn,1=man,...,6=lør
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = new Date(d);
  monday.setDate(d.getDate() + diffToMonday);
  return monday;
}

/** De 10 hverdagene (mandag-fredag) for inneværende og neste kalenderuke. */
function weekdays(): string[] {
  const monday = mondayOf(new Date());
  const days: string[] = [];
  for (let week = 0; week < 2; week++) {
    for (let i = 0; i < 5; i++) {
      const d = new Date(monday);
      d.setDate(monday.getDate() + week * 7 + i);
      days.push(toIsoDate(d));
    }
  }
  return days;
}

/**
 * Fordelingslogikken (kalender, forslag, bekreftelse, endring og sletting) —
 * flyttet hit fra dashboard for å skille "sett opp familie/kalender"-siden fra
 * den daglige bruken. Viser nå en 2-ukers kalender med Levering/Henting inline
 * per dag i stedet for et separat datovelger-skjema og en historikkliste.
 */
export default function KalenderPage() {
  const [parents, setParents] = useState<Parent[]>([]);
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [activeSuggestion, setActiveSuggestion] = useState<ActiveSuggestion | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const days = useMemo(() => weekdays(), []);

  const assignmentsByDay = useMemo(() => {
    const map = new Map<string, CellAssignments>();
    for (const a of assignments) {
      const cell = map.get(a.date) ?? {};
      cell[a.type] = a;
      map.set(a.date, cell);
    }
    return map;
  }, [assignments]);

  useEffect(() => {
    api.getParents().then(setParents).catch((e) => setError(String(e)));
    refreshAssignments();
  }, []);

  function refreshAssignments() {
    api.getAssignments().then(setAssignments).catch((e) => setError(String(e)));
  }

  async function requestSuggestion(date: string, type: AssignmentType) {
    setError(null);
    setLoading(true);
    try {
      const suggestion = await api.getSuggestion(date, type);
      setActiveSuggestion({ date, type, suggestion });
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }

  async function confirmSuggestion(date: string, type: AssignmentType, parentId: string, source: AssignmentSource) {
    setError(null);
    setLoading(true);
    try {
      await api.assign({ date, type, parentId, source });
      setActiveSuggestion(null);
      refreshAssignments();
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }

  async function changeAssignment(date: string, type: AssignmentType, parentId: string) {
    setError(null);
    setLoading(true);
    try {
      // Sender samme dato/type på nytt — backend gjenkjenner at det allerede
      // finnes en tildeling for den kombinasjonen og erstatter den (oppdaterer
      // forelder, bytter ev. kalenderhendelse).
      await api.assign({ date, type, parentId, source: "MANUAL" });
      refreshAssignments();
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }

  async function deleteAssignment(id: string) {
    if (!confirm("Slette denne bekreftede tildelingen (og en ev. kalenderhendelse)?")) return;
    setError(null);
    setLoading(true);
    try {
      await api.deleteAssignment(id);
      refreshAssignments();
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }

  return (
    <main>
      <h1>Kalender</h1>
      {error && <p className="error">{error}</p>}

      <section>
        <WeekCalendar
          days={days}
          assignmentsByDay={assignmentsByDay}
          parents={parents}
          activeSuggestion={activeSuggestion}
          loading={loading}
          onRequestSuggestion={requestSuggestion}
          onConfirmSuggestion={confirmSuggestion}
          onChangeAssignment={changeAssignment}
          onDeleteAssignment={deleteAssignment}
        />
      </section>
    </main>
  );
}
