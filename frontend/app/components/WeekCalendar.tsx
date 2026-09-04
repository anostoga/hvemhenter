import { Assignment, AssignmentType, Parent } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { WeekBlockSkeleton } from "@/app/components/WeekCalendarSkeleton";

const UNASSIGNED = "__unassigned__";

export type CellAssignments = Partial<Record<AssignmentType, Assignment>>;

interface WeekCalendarProps {
  days: string[]; // 10 datoer (yyyy-MM-dd), mandag-fredag for denne og neste uke
  assignmentsByDay: Map<string, CellAssignments>;
  parents: Parent[];
  loading: boolean;
  // Index (0 eller 1) for uken en hel-uke-handling (generer forslag/nullstill
  // uken) pågår for, eller `null` når ingen slik handling pågår — se
  // KalenderClient.tsx. Kun DENNE uken vises som skjelett (`WeekBlockSkeleton`)
  // mens den andre uken fortsatt vises normalt, i motsetning til den
  // opprinnelige Suspense-fallbacken (`WeekCalendarSkeleton`) som
  // skjelettifiserer begge ukene samtidig ved førstelasting.
  loadingWeekIndex?: number | null;
  onGenerateWeekPlan: (weekDays: string[]) => void;
  onResetWeekPlan: (weekDays: string[]) => void;
  onChangeAssignment: (date: string, type: AssignmentType, parentId: string) => void;
}

const TYPE_LABEL: Record<AssignmentType, string> = {
  DROPOFF: "Levering",
  PICKUP: "Henting",
};

function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function dayLabel(date: string): string {
  // Parses som lokal dato (ikke UTC) — unngår at "T00:00:00Z" havner på forrige
  // dag i tidssoner vest for UTC.
  const d = new Date(`${date}T00:00:00`);
  return d.toLocaleDateString("nb-NO", { weekday: "short", day: "2-digit", month: "2-digit" });
}

/**
 * Presentasjonell komponent: viser 10 hverdager (denne + neste uke) med
 * Levering/Henting inline per dag. Eier ingen state selv — all API-logikk og
 * feilhåndtering ligger i `KalenderPage`, komponenten kaller kun tilbake via
 * props når brukeren gjør noe.
 *
 * Hver dag-boks har FAST størrelse uansett tilstand (tildelt/ikke tildelt) —
 * samme struktur rendres alltid (se renderSlot), kun innholdet i
 * nedtrekkslisten/meta-teksten endrer seg. Dette unngår at boksene "hopper"
 * i størrelse når man genererer et forslag eller endrer en tildeling, i
 * motsetning til den tidligere varianten som viste helt ulikt innhold
 * (forslagstekst + bekreft-knapper per forelder) for en aktiv forslags-tilstand.
 */
export function WeekCalendar({
  days,
  assignmentsByDay,
  parents,
  loading,
  loadingWeekIndex = null,
  onGenerateWeekPlan,
  onResetWeekPlan,
  onChangeAssignment,
}: WeekCalendarProps) {
  const today = todayIso();
  const weeks = [days.slice(0, 5), days.slice(5, 10)];

  // Datoer før i dag er historikk — ingen tildelinger skal kunne endres der.
  function isPast(date: string): boolean {
    return date < today;
  }

  function renderSlot(date: string, type: AssignmentType) {
    const assignment = assignmentsByDay.get(date)?.[type];
    const past = isPast(date);

    return (
      <div className="flex flex-col gap-1 rounded bg-muted p-2 text-sm">
        <p className="m-0 font-semibold text-foreground">{TYPE_LABEL[type]}</p>
        <Select
          value={assignment?.parentId ?? UNASSIGNED}
          disabled={loading || past}
          onValueChange={(value) => onChangeAssignment(date, type, value === UNASSIGNED ? "" : value)}
        >
          <SelectTrigger className="w-full min-h-[34px] text-[0.85rem]" aria-label={`${TYPE_LABEL[type]} ${dayLabel(date)}`}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={UNASSIGNED}>Ikke tildelt</SelectItem>
            {parents.map((p) => (
              <SelectItem key={p.id} value={p.id}>
                {p.avatar ? `${p.avatar} ` : ""}
                {p.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {/* Fast plass for kilde-teksten (ikke-brytende mellomrom når ingen tildeling) —
            unngår at boksens høyde endrer seg avhengig av om noe er tildelt. */}
        <p className="m-0 min-h-[1em] text-xs text-muted-foreground">{assignment ? (assignment.source === "AUTO" ? "auto" : "manuelt") : "\u00A0"}</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-5 overflow-x-auto">
      {weeks.map((week, i) => {
        const weekIsPast = week.every((date) => isPast(date));
        // "Fullt generert" = alle 10 slots (5 dager × Levering/Henting) i uken har
        // en tildeling. Da har "Generer forslag" ingenting igjen å fylle (funksjonen
        // hopper uansett over allerede tildelte slots), så knappen deaktiveres for å
        // gjøre det tydelig — den blir aktiv igjen så snart "Nullstill uken" (eller en
        // manuell endring til "Ikke tildelt") åpner opp minst én tom slot.
        const weekFullyAssigned = week.every((date) => {
          const cell = assignmentsByDay.get(date);
          return Boolean(cell?.DROPOFF && cell?.PICKUP);
        });
        // "Nullstill uken" gir ingen mening (og er forvirrende å tilby) hvis uken
        // ikke har noen tildelinger å slette i det hele tatt.
        const weekHasNoAssignments = week.every((date) => {
          const cell = assignmentsByDay.get(date);
          return !cell?.DROPOFF && !cell?.PICKUP;
        });
        return (
          <div className={weekIsPast ? "hidden sm:block" : undefined} key={i}>
            {i === loadingWeekIndex ? (
              <WeekBlockSkeleton />
            ) : (
              <>
                <div className="mb-1.5 flex flex-wrap gap-2">
                  <Button
                    disabled={loading || weekIsPast || weekFullyAssigned}
                    onClick={() => onGenerateWeekPlan(week)}
                  >
                    Generer forslag for uken
                  </Button>
                  <Button
                    variant="outline"
                    className="border-destructive text-destructive hover:bg-destructive/10"
                    disabled={loading || weekIsPast || weekHasNoAssignments}
                    onClick={() => onResetWeekPlan(week)}
                  >
                    Nullstill uken
                  </Button>
                </div>
                <div className="flex flex-col items-stretch gap-2 sm:flex-row">
                  {week.map((date) => (
                    <div
                      className={`flex flex-1 flex-col gap-1.5 rounded-md border p-2 ${date === today ? "border-primary bg-accent" : "border-border"} ${isPast(date) ? "hidden sm:flex sm:min-w-0" : "sm:min-w-[110px]"}`}
                      key={date}
                    >
                      <p className="m-0 font-semibold capitalize">{dayLabel(date)}</p>
                      {renderSlot(date, "DROPOFF")}
                      {renderSlot(date, "PICKUP")}
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}
