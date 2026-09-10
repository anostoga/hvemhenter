import { Assignment, AssignmentType, Parent } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { WeekBlockSkeleton } from "@/app/components/WeekCalendarSkeleton";

const UNASSIGNED = "__unassigned__";

export type CellAssignments = Partial<Record<AssignmentType, Assignment>>;

interface WeekCalendarProps {
  days: string[];
  assignmentsByDay: Map<string, CellAssignments>;
  parents: Parent[];
  loading: boolean;

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

  const d = new Date(`${date}T00:00:00`);
  return d.toLocaleDateString("nb-NO", { weekday: "short", day: "2-digit", month: "2-digit" });
}

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
          <SelectTrigger className="w-full min-w-0 min-h-[34px] text-[0.85rem]" aria-label={`${TYPE_LABEL[type]} ${dayLabel(date)}`}>
            {}
            <SelectValue className="min-w-0 truncate" />
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
        {}
        <p className="m-0 min-h-[1em] text-xs text-muted-foreground">{assignment ? (assignment.source === "AUTO" ? "auto" : "manuelt") : "\u00A0"}</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-5 overflow-x-auto">
      {weeks.map((week, i) => {
        const weekIsPast = week.every((date) => isPast(date));

        const weekFullyAssigned = week.every((date) => {
          const cell = assignmentsByDay.get(date);
          return Boolean(cell?.DROPOFF && cell?.PICKUP);
        });

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
                <div className="mb-2 flex flex-wrap gap-2">
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
