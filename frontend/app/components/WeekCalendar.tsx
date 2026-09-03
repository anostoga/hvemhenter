import { Assignment, AssignmentType, Parent } from "@/lib/api";

export type CellAssignments = Partial<Record<AssignmentType, Assignment>>;

interface WeekCalendarProps {
  days: string[]; // 10 datoer (yyyy-MM-dd), mandag-fredag for denne og neste uke
  assignmentsByDay: Map<string, CellAssignments>;
  parents: Parent[];
  loading: boolean;
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
      <div className="week-slot">
        <p className="week-slot-label">{TYPE_LABEL[type]}</p>
        <select
          className="week-slot-select"
          value={assignment?.parentId ?? ""}
          disabled={loading || past}
          aria-label={`${TYPE_LABEL[type]} ${dayLabel(date)}`}
          onChange={(e) => onChangeAssignment(date, type, e.target.value)}
        >
          <option value="">Ikke tildelt</option>
          {parents.map((p) => (
            <option key={p.id} value={p.id}>
              {p.avatar ? `${p.avatar} ` : ""}
              {p.name}
            </option>
          ))}
        </select>
        {/* Fast plass for kilde-teksten (ikke-brytende mellomrom når ingen tildeling) —
            unngår at boksens høyde endrer seg avhengig av om noe er tildelt. */}
        <p className="week-slot-meta">{assignment ? (assignment.source === "AUTO" ? "auto" : "manuelt") : "\u00A0"}</p>
      </div>
    );
  }

  return (
    <div className="week-calendar">
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
        <div className={`week-calendar-week-block${weekIsPast ? " week-calendar-week-block-past" : ""}`} key={i}>
          <div className="week-calendar-week-header">
            <button disabled={loading || weekIsPast || weekFullyAssigned} onClick={() => onGenerateWeekPlan(week)}>
              Generer forslag for uken
            </button>
            <button
              className="week-calendar-reset-button"
              disabled={loading || weekIsPast || weekHasNoAssignments}
              onClick={() => onResetWeekPlan(week)}
            >
              Nullstill uken
            </button>
          </div>
          <div className="week-calendar-week">
            {week.map((date) => (
              <div
                className={`week-calendar-day${date === today ? " week-calendar-day-today" : ""}${isPast(date) ? " week-calendar-day-past" : ""}`}
                key={date}
              >
                <p className="week-calendar-day-header">{dayLabel(date)}</p>
                {renderSlot(date, "DROPOFF")}
                {renderSlot(date, "PICKUP")}
              </div>
            ))}
          </div>
        </div>
        );
      })}
    </div>
  );
}
