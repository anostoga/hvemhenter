"use client";

import { useMemo, useState } from "react";
import { api, Assignment, AssignmentType, Parent } from "@/lib/api";
import { CellAssignments, WeekCalendar } from "@/app/components/WeekCalendar";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";

function toIsoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function mondayOf(d: Date): Date {
  const day = d.getDay();
  const diffToMonday = day === 0 ? -6 : 1 - day;
  const monday = new Date(d);
  monday.setDate(d.getDate() + diffToMonday);
  return monday;
}

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

const ASSIGNMENT_TYPES: AssignmentType[] = ["DROPOFF", "PICKUP"];

interface KalenderClientProps {
  initialParents: Parent[];
  initialAssignments: Assignment[];
}

export default function KalenderClient({ initialParents, initialAssignments }: KalenderClientProps) {

  const parents = initialParents;
  const [assignments, setAssignments] = useState<Assignment[]>(initialAssignments);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [resendingAssignmentId, setResendingAssignmentId] = useState<string | null>(null);

  const [loadingWeekIndex, setLoadingWeekIndex] = useState<number | null>(null);

  const [pendingResetWeek, setPendingResetWeek] = useState<string[] | null>(null);

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
  const declinedAssignments = assignments.filter((assignment) => assignment.confirmationStatus === "DECLINED");

  async function refreshAssignments(): Promise<void> {
    try {
      setAssignments(await api.getAssignments());
    } catch (e) {
      setError(String(e));
    }
  }

  function weekIndexOf(weekDays: string[]): number {
    return weekDays[0] === days[5] ? 1 : 0;
  }

  async function generateWeekPlan(weekDays: string[]) {
    setError(null);
    setNotice(null);
    setLoading(true);
    setLoadingWeekIndex(weekIndexOf(weekDays));
    const today = toIsoDate(new Date());
    try {
      for (const date of weekDays) {
        if (date < today) continue;
        for (const type of ASSIGNMENT_TYPES) {
          if (assignmentsByDay.get(date)?.[type]) continue;
          const suggestion = await api.getSuggestion(date, type);
          await api.assign({ date, type, parentId: suggestion.suggestedParentId, source: "AUTO" });
        }
      }
      await refreshAssignments();
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
      setLoadingWeekIndex(null);
    }
  }

  async function resetWeekPlan(weekDays: string[]) {
    setError(null);
    setNotice(null);
    setLoading(true);
    setLoadingWeekIndex(weekIndexOf(weekDays));
    const today = toIsoDate(new Date());
    try {
      for (const date of weekDays) {
        if (date < today) continue;
        for (const type of ASSIGNMENT_TYPES) {
          const existing = assignmentsByDay.get(date)?.[type];
          if (existing?.id) {
            await api.deleteAssignment(existing.id);
          }
        }
      }
      await refreshAssignments();
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
      setLoadingWeekIndex(null);
    }
  }

  function requestResetWeek(weekDays: string[]) {
    setPendingResetWeek(weekDays);
  }

  function confirmResetWeek() {
    if (pendingResetWeek) resetWeekPlan(pendingResetWeek);
    setPendingResetWeek(null);
  }

  function cancelResetWeek() {
    setPendingResetWeek(null);
  }

  async function changeAssignment(date: string, type: AssignmentType, parentId: string) {
    setError(null);
    setNotice(null);
    setLoading(true);
    try {
      if (!parentId) {
        const existing = assignmentsByDay.get(date)?.[type];
        if (existing?.id) {
          await api.deleteAssignment(existing.id);
        }
      } else {

        await api.assign({ date, type, parentId, source: "MANUAL" });
      }
      await refreshAssignments();
    } catch (e) {
      await refreshAssignments();
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }

  async function resendConfirmation(assignmentId: string) {
    setError(null);
    setNotice(null);
    setResendingAssignmentId(assignmentId);
    try {
      await api.resendAssignmentConfirmation(assignmentId);
      await refreshAssignments();
      setNotice("Bekreftelses-e-post sendt.");
    } catch (e) {
      setError(String(e));
    } finally {
      setResendingAssignmentId(null);
    }
  }

  return (
    <>
      {error && <p className="text-destructive">{error}</p>}
      {notice && <p className="text-sm text-muted-foreground" role="status">{notice}</p>}
      {declinedAssignments.length > 0 && (
        <aside className="mb-4 rounded-md border border-destructive p-3" role="status">
          <p className="m-0 font-semibold">En hjelper har avslått en oppgave.</p>
          <p className="m-0">Velg en ny ansvarlig i kalenderen for å sende en ny forespørsel.</p>
          <ul className="mb-0 mt-2 list-disc pl-5">
            {declinedAssignments.map((assignment) => (
              <li key={`${assignment.date}-${assignment.type}`}>
                {assignment.date} — {assignment.type === "DROPOFF" ? "levering" : "henting"} (
                {parents.find((parent) => parent.id === assignment.parentId)?.name ?? "hjelper"})
              </li>
            ))}
          </ul>
        </aside>
      )}

      <WeekCalendar
        days={days}
        assignmentsByDay={assignmentsByDay}
        parents={parents}
        loading={loading || resendingAssignmentId !== null}
        loadingWeekIndex={loadingWeekIndex}
        onGenerateWeekPlan={generateWeekPlan}
        onResetWeekPlan={requestResetWeek}
        onChangeAssignment={changeAssignment}
        onResendConfirmation={resendConfirmation}
        resendingAssignmentId={resendingAssignmentId}
      />

      <Dialog
        open={pendingResetWeek !== null}
        onOpenChange={(open) => {
          if (!open) cancelResetWeek();
        }}
      >
        <DialogContent aria-label="Bekreft nullstilling av uken">
          <DialogHeader>
            <DialogTitle>Nullstill uken?</DialogTitle>
            <DialogDescription>
              Nullstille all fordeling denne uken? Dette sletter alle tildelinger (og tilhørende kalenderhendelser) for
              uken.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={cancelResetWeek}>
              Avbryt
            </Button>
            <Button type="button" variant="destructive" onClick={confirmResetWeek}>
              Nullstill
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
