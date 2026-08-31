import { Assignment, AssignmentSource, AssignmentType, Parent, Suggestion } from "@/lib/api";

export type CellAssignments = Partial<Record<AssignmentType, Assignment>>;

export interface ActiveSuggestion {
  date: string;
  type: AssignmentType;
  suggestion: Suggestion;
}

interface WeekCalendarProps {
  days: string[]; // 10 datoer (yyyy-MM-dd), mandag-fredag for denne og neste uke
  assignmentsByDay: Map<string, CellAssignments>;
  parents: Parent[];
  activeSuggestion: ActiveSuggestion | null;
  loading: boolean;
  onRequestSuggestion: (date: string, type: AssignmentType) => void;
  onConfirmSuggestion: (date: string, type: AssignmentType, parentId: string, source: AssignmentSource) => void;
  onChangeAssignment: (date: string, type: AssignmentType, parentId: string) => void;
  onDeleteAssignment: (id: string) => void;
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
 * props når brukeren klikker noe.
 */
export function WeekCalendar({
  days,
  assignmentsByDay,
  parents,
  activeSuggestion,
  loading,
  onRequestSuggestion,
  onConfirmSuggestion,
  onChangeAssignment,
  onDeleteAssignment,
}: WeekCalendarProps) {
  const today = todayIso();
  const weeks = [days.slice(0, 5), days.slice(5, 10)];

  function parentName(id: string): string {
    return parents.find((p) => p.id === id)?.name ?? id;
  }

  function renderSlot(date: string, type: AssignmentType) {
    const assignment = assignmentsByDay.get(date)?.[type];

    if (assignment) {
      return (
        <div className="week-slot week-slot-filled">
          <p className="week-slot-label">{TYPE_LABEL[type]}</p>
          <p>
            {parentName(assignment.parentId)} <small>({assignment.source === "AUTO" ? "auto" : "manuelt"})</small>
          </p>
          {parents
            .filter((p) => p.id !== assignment.parentId)
            .map((p) => (
              <button
                key={p.id}
                disabled={loading}
                onClick={() => onChangeAssignment(date, type, p.id)}
              >
                Endre til {p.name}
              </button>
            ))}
          {assignment.id && (
            <button disabled={loading} onClick={() => onDeleteAssignment(assignment.id!)}>
              Slett
            </button>
          )}
        </div>
      );
    }

    const isActive = activeSuggestion?.date === date && activeSuggestion?.type === type;
    if (isActive) {
      const { suggestion } = activeSuggestion;
      return (
        <div className="week-slot week-slot-active">
          <p className="week-slot-label">{TYPE_LABEL[type]}</p>
          <p>
            Forslag: <strong>{parentName(suggestion.suggestedParentId)}</strong> — {suggestion.reason}
            {suggestion.conflict && " ⚠️ bekreft manuelt"}
          </p>
          {parents.map((p) => (
            <button
              key={p.id}
              disabled={loading}
              onClick={() => onConfirmSuggestion(date, type, p.id, p.id === suggestion.suggestedParentId ? "AUTO" : "MANUAL")}
            >
              Bekreft {p.name}
            </button>
          ))}
        </div>
      );
    }

    return (
      <div className="week-slot week-slot-empty">
        <p className="week-slot-label">{TYPE_LABEL[type]}</p>
        <button disabled={loading} onClick={() => onRequestSuggestion(date, type)}>
          Foreslå
        </button>
      </div>
    );
  }

  return (
    <div className="week-calendar">
      {weeks.map((week, i) => (
        <div className="week-calendar-week" key={i}>
          {week.map((date) => (
            <div className={`week-calendar-day${date === today ? " week-calendar-day-today" : ""}`} key={date}>
              <p className="week-calendar-day-header">{dayLabel(date)}</p>
              {renderSlot(date, "DROPOFF")}
              {renderSlot(date, "PICKUP")}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
