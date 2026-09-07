import { Assignment, AssignmentType, Parent } from "@/lib/api";
import { CellAssignments } from "@/app/components/WeekCalendar";
import { Skeleton } from "@/components/ui/skeleton";

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

function parentLabel(assignment: Assignment | undefined, parents: Parent[]): string {
  if (!assignment) return "Ikke tildelt";
  const parent = parents.find((p) => p.id === assignment.parentId);
  if (!parent) return "Ikke tildelt";
  return parent.avatar ? `${parent.avatar} ${parent.name}` : parent.name;
}

interface WeekOverviewProps {
  days: string[]; // 5 hverdager (mandag-fredag) for inneværende uke
  assignmentsByDay: Map<string, CellAssignments>;
  parents: Parent[];
}

/**
 * Skrivebeskyttet variant av `<WeekCalendar>` (se app/components/WeekCalendar.tsx)
 * — vises på forsiden som et raskt overblikk over ukens fordeling, uten
 * nedtrekkslister/knapper for å endre noe (det gjøres fortsatt kun på
 * /ukeplan). Viser derfor bare navn/avatar (eller "Ikke tildelt") som ren
 * tekst per Levering/Henting-slot, samme dag-boks-struktur som originalen.
 */
export function WeekOverview({ days, assignmentsByDay, parents }: WeekOverviewProps) {
  const today = todayIso();

  return (
    <div className="flex flex-col items-stretch gap-2 sm:flex-row">
      {days.map((date) => (
        <div
          className={`flex flex-1 flex-col gap-1.5 rounded-md border p-2 ${date === today ? "border-primary bg-accent" : "border-border"}`}
          key={date}
        >
          <p className="m-0 font-semibold capitalize">{dayLabel(date)}</p>
          {(Object.keys(TYPE_LABEL) as AssignmentType[]).map((type) => (
            <div className="flex flex-col gap-1 rounded bg-muted p-2 text-sm" key={type}>
              <p className="m-0 font-semibold text-foreground">{TYPE_LABEL[type]}</p>
              <p className="m-0">{parentLabel(assignmentsByDay.get(date)?.[type], parents)}</p>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}

const DAYS = [0, 1, 2, 3, 4];
const SLOTS = [0, 1];

/** Suspense-fallback for `<WeekOverview>` — speiler strukturen uten knapperad. */
export function WeekOverviewSkeleton() {
  return (
    <div className="flex flex-col items-stretch gap-2 sm:flex-row">
      {DAYS.map((day) => (
        <div className="flex flex-1 flex-col gap-1.5 rounded-md border border-border p-2" key={day}>
          <Skeleton className="h-5 w-20" />
          {SLOTS.map((slot) => (
            <div className="flex flex-col gap-1 rounded bg-muted p-2" key={slot}>
              <Skeleton className="h-4 w-16" />
              <Skeleton className="h-4 w-24" />
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
