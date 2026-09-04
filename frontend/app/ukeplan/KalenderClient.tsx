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

const ASSIGNMENT_TYPES: AssignmentType[] = ["DROPOFF", "PICKUP"];

/**
 * Fordelingslogikken (kalender, forslag og endring) — flyttet hit fra
 * dashboard for å skille "sett opp familie/kalender"-siden fra den daglige
 * bruken. Viser en 2-ukers kalender med Levering/Henting inline per dag.
 * Forslag genereres nå PER UKE (én knapp fyller alle ubesatte slots i den
 * uken automatisk, uten bekreftelse per dag) — endring (inkl. å fjerne en
 * tildeling ved å velge "Ikke tildelt") skjer via en nedtrekksliste i selve
 * dag-boksen (se WeekCalendar), det finnes ingen egen slett-knapp per dag.
 * En egen "Nullstill uken"-knapp sletter i stedet ALLE tildelinger i uken
 * samlet, som en rask måte å starte fordelingen for uken på nytt.
 *
 * `initialParents`/`initialAssignments` hentes server-side (se
 * app/ukeplan/page.tsx og lib/server-api.ts) og seeder state-et her direkte
 * — ingen egen "hent ved mount"-effekt lenger, siden Server Component-en +
 * Suspense-fallbacken (skjelett-UI) allerede har dekket førstelastingen.
 * Etterfølgende handlinger (generer/nullstill/endre) bruker fortsatt
 * client-side `lib/api.ts`-kall og `refreshAssignments()`, akkurat som før.
 */
interface KalenderClientProps {
  initialParents: Parent[];
  initialAssignments: Assignment[];
}

export default function KalenderClient({ initialParents, initialAssignments }: KalenderClientProps) {
  // Foreldrelisten endres ikke fra denne siden (administreres på /familie),
  // så den trenger ikke egen state — kun tildelingene endres her.
  const parents = initialParents;
  const [assignments, setAssignments] = useState<Assignment[]>(initialAssignments);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  // Index (0 eller 1) for uken en hel-uke-handling (generer forslag/nullstill
  // uken) pågår for, eller `null` når ingen slik handling pågår. Sendes til
  // `<WeekCalendar>` (se `loadingWeekIndex`-prop-en der), som kun
  // skjelettifiserer DEN ene uken — den andre uken forblir synlig og
  // brukbar. Enkelt-endring av én tildeling (`changeAssignment`) bruker
  // fortsatt kun `loading` (deaktiverer kontrollene) — å skjelettifisere en
  // hel uke for én dropdown-endring ville vært unødvendig forstyrrende.
  const [loadingWeekIndex, setLoadingWeekIndex] = useState<number | null>(null);
  // Uken (datoer) det er bedt om å nullstille, mens vi venter på at brukeren
  // bekrefter/avbryter i modalen — selve slettingen skjer først i
  // `confirmResetWeek`, se under.
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

  async function refreshAssignments(): Promise<void> {
    try {
      setAssignments(await api.getAssignments());
    } catch (e) {
      setError(String(e));
    }
  }

  // `days` er de 10 datoene (uke 1 + uke 2) — uke 2 starter på index 5.
  // `weekDays` som sendes inn til generateWeekPlan/resetWeekPlan er ALLTID
  // enten `days.slice(0, 5)` eller `days.slice(5, 10)` (se WeekCalendar sin
  // egen `weeks`-oppdeling), så det er nok å sjekke første dato i den mot
  // uke 2 sin første dato for å avgjøre hvilken uke det gjelder.
  function weekIndexOf(weekDays: string[]): number {
    return weekDays[0] === days[5] ? 1 : 0;
  }

  // Fyller alle UBESATTE Levering/Henting-slots i den gitte uken med et
  // automatisk forslag hver — ingen bekreftelse per dag (se brukerens
  // ønske om å fjerne inline foreslå-knappen per dag). Eksisterende
  // tildelinger (manuelle eller tidligere auto-genererte) overskrives ikke;
  // bruk nedtrekkslisten i dag-boksen for å endre en allerede satt tildeling.
  // Passerte dager (før i dag) hoppes også over — de er låst i UI-et
  // (WeekCalendar deaktiverer nedtrekkslisten der), og skal derfor heller
  // ikke kunne fylles/endres av denne handlingen (symmetrisk med
  // `resetWeekPlan`, som av samme grunn heller aldri rører passerte dager).
  async function generateWeekPlan(weekDays: string[]) {
    setError(null);
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

  // Sletter alle eksisterende tildelinger (manuelle og auto-genererte) i den
  // gitte uken, slik at brukeren kan starte fordelingen på nytt. Passerte
  // dager (før i dag) røres ikke — disse er uansett låst i UI-et og skal
  // ikke kunne endres i ettertid. Kalles først etter at brukeren har
  // bekreftet i modalen (se `requestResetWeek`/`confirmResetWeek` under) —
  // ingen `confirm()`-alert her lenger.
  async function resetWeekPlan(weekDays: string[]) {
    setError(null);
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

  // Åpner bekreftelsesmodalen i stedet for å slette med en gang — selve
  // slettingen skjer i `confirmResetWeek` når brukeren klikker "Nullstill".
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

  // Endrer (eller fjerner) en tildeling via nedtrekkslisten. Tom parentId
  // betyr at brukeren valgte "Ikke tildelt" — da slettes en ev. eksisterende
  // tildeling i stedet for å opprette en ny (backend har ikke noe konsept om
  // en "tom" tildeling, kun fravær av rad).
  async function changeAssignment(date: string, type: AssignmentType, parentId: string) {
    setError(null);
    setLoading(true);
    try {
      if (!parentId) {
        const existing = assignmentsByDay.get(date)?.[type];
        if (existing?.id) {
          await api.deleteAssignment(existing.id);
        }
      } else {
        // Sender samme dato/type på nytt — backend gjenkjenner at det allerede
        // finnes en tildeling for den kombinasjonen og erstatter den (oppdaterer
        // forelder, bytter ev. kalenderhendelse).
        await api.assign({ date, type, parentId, source: "MANUAL" });
      }
      await refreshAssignments();
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      {error && <p className="text-destructive">{error}</p>}

      <WeekCalendar
        days={days}
        assignmentsByDay={assignmentsByDay}
        parents={parents}
        loading={loading}
        loadingWeekIndex={loadingWeekIndex}
        onGenerateWeekPlan={generateWeekPlan}
        onResetWeekPlan={requestResetWeek}
        onChangeAssignment={changeAssignment}
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
