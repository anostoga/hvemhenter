import { useState } from "react";
import { CheckCircle2, XCircle } from "lucide-react";
import { Assignment, AssignmentType, Parent } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
  onResendConfirmation: (assignmentId: string, token?: string) => Promise<boolean>;
  onAnswerConfirmationOnBehalf: (assignmentId: string, answer: "ACCEPTED" | "DECLINED") => Promise<boolean>;
  onCreateConfirmationLink: (assignmentId: string) => void;
  onShareConfirmationLink: (path: string, title: string, text: string) => void;
  onCopyConfirmationLink: (path: string) => void;
  confirmationLinks: Record<string, string>;
  resendingAssignmentId?: string | null;
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
  onResendConfirmation,
  onAnswerConfirmationOnBehalf,
  onCreateConfirmationLink,
  onShareConfirmationLink,
  onCopyConfirmationLink,
  confirmationLinks,
  resendingAssignmentId = null,
}: WeekCalendarProps) {
  const today = todayIso();
  const weeks = [days.slice(0, 5), days.slice(5, 10)];
  const [shareAssignment, setShareAssignment] = useState<{
    id: string;
    date: string;
    type: AssignmentType;
    helperName: string;
    email: string | null;
    emailSent: boolean;
  } | null>(null);
  const [answeringOnBehalf, setAnsweringOnBehalf] = useState(false);

  function isPast(date: string): boolean {
    return date < today;
  }

  function renderSlot(date: string, type: AssignmentType) {
    const assignment = assignmentsByDay.get(date)?.[type];
    const assignedParent = parents.find((parent) => parent.id === assignment?.parentId);
    const canNotifyHelper = Boolean(assignedParent?.isHelper);
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
        <p className="m-0 min-h-[1em] text-xs">
          {assignment
            ? assignment.confirmationStatus !== "NOT_REQUIRED"
              ? canNotifyHelper
                ? assignment.confirmationStatus === "PENDING"
                  ? (
                    <span className="text-muted-foreground">
                      {assignment.confirmationLinkIssued || assignment.confirmationEmailSent
                        ? "Venter på svar"
                        : "Ikke sendt"}
                    </span>
                  )
                  : assignment.confirmationStatus === "ACCEPTED" ? (
                    <span className="inline-flex items-center gap-1 font-semibold text-green-700 dark:text-green-400">
                      <CheckCircle2 className="size-4" aria-hidden="true" />
                      Godtatt
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 font-semibold text-red-700 dark:text-red-400">
                      <XCircle className="size-4" aria-hidden="true" />
                      Avslått
                    </span>
                  )
                : "\u00A0"
              : assignment.source === "AUTO"
                ? "auto"
                : "manuelt"
            : "\u00A0"}
        </p>
        {canNotifyHelper && assignment?.confirmationStatus === "PENDING" && assignment.id && (
          <Button
            type="button"
            variant="outline"
            size="default"
            disabled={resendingAssignmentId === assignment.id}
            onClick={() => {
                const hasStoredLink = Boolean(confirmationLinks[assignment.id!]);
                const target = {
                  id: assignment.id!,
                  date,
                  type,
                  helperName: assignedParent?.name ?? "hjelper",
                  email: assignedParent?.email?.trim() || null,
                  emailSent: hasStoredLink && assignment.confirmationEmailSent,
                };
              setShareAssignment(target);
              if (!confirmationLinks[target.id]) onCreateConfirmationLink(target.id);
            }}
          >
            {resendingAssignmentId === assignment.id ? "Forbereder …" : "Del med hjelper"}
          </Button>
        )}
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
      <Dialog open={shareAssignment !== null} onOpenChange={(open) => !open && setShareAssignment(null)}>
        <DialogContent aria-label="Del med hjelper">
          <DialogHeader>
            <DialogTitle>Del med hjelper</DialogTitle>
            <DialogDescription>
              Send lenken til {shareAssignment?.helperName} for å bekrefte{" "}
              {shareAssignment ? TYPE_LABEL[shareAssignment.type].toLowerCase() : "oppgaven"}{" "}
              {shareAssignment ? dayLabel(shareAssignment.date) : ""}.
            </DialogDescription>
          </DialogHeader>
          {shareAssignment && (
            <div className="flex flex-col gap-3">
              {confirmationLinks[shareAssignment.id] ? (
                <>
                  <Button
                    type="button"
                    onClick={() =>
                      onShareConfirmationLink(
                        confirmationLinks[shareAssignment.id],
                        "HvemHenter – bekreft oppgave",
                        `Hei ${shareAssignment.helperName}! Kan du bekrefte ${TYPE_LABEL[shareAssignment.type].toLowerCase()} ${dayLabel(shareAssignment.date)}?`,
                      )
                    }
                  >
                    Del via Meldinger …
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => onCopyConfirmationLink(confirmationLinks[shareAssignment.id])}
                  >
                    Kopier svarlenke
                  </Button>
                  {shareAssignment.email && (
                    shareAssignment.emailSent ? (
                      <p className="m-0 text-sm text-muted-foreground" role="status">
                        E-post er allerede sendt til hjelperen.
                      </p>
                    ) : (
                      <Button
                        type="button"
                        variant="outline"
                        disabled={resendingAssignmentId === shareAssignment.id}
                        onClick={async () => {
                          const token = confirmationLinks[shareAssignment.id].split("/").at(-1);
                          if (!token) return;
                          const sent = await onResendConfirmation(shareAssignment.id, token);
                          if (sent) {
                            setShareAssignment((current) =>
                              current?.id === shareAssignment.id ? { ...current, emailSent: true } : current,
                            );
                          }
                        }}
                      >
                        {resendingAssignmentId === shareAssignment.id ? "Sender e-post …" : "Send også på e-post"}
                      </Button>
                    )
                  )}
                </>
              ) : resendingAssignmentId === shareAssignment.id ? (
                <p className="m-0 text-sm text-muted-foreground" role="status">
                  Oppretter svarlenke …
                </p>
              ) : (
                <p className="m-0 text-sm text-destructive" role="alert">
                  Kunne ikke opprette svarlenken. Lukk vinduet og prøv igjen.
                </p>
              )}
              <div className="border-t pt-3">
                <p className="mb-2 mt-0 text-sm font-medium">Registrer svar på vegne av hjelperen</p>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    disabled={answeringOnBehalf}
                    onClick={async () => {
                      setAnsweringOnBehalf(true);
                      try {
                        if (await onAnswerConfirmationOnBehalf(shareAssignment.id, "ACCEPTED")) {
                          setShareAssignment(null);
                        }
                      } finally {
                        setAnsweringOnBehalf(false);
                      }
                    }}
                  >
                    Godtatt
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    disabled={answeringOnBehalf}
                    onClick={async () => {
                      setAnsweringOnBehalf(true);
                      try {
                        if (await onAnswerConfirmationOnBehalf(shareAssignment.id, "DECLINED")) {
                          setShareAssignment(null);
                        }
                      } finally {
                        setAnsweringOnBehalf(false);
                      }
                    }}
                  >
                    Avslått
                  </Button>
                </div>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
