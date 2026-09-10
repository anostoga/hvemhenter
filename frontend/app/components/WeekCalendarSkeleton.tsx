import { Skeleton } from "@/components/ui/skeleton";

const DAYS = [0, 1, 2, 3, 4];
const SLOTS = [0, 1];

export function WeekBlockSkeleton() {
  return (
    <div>
      <div className="mb-1.5 flex flex-wrap gap-2">
        <Skeleton className="h-9 w-[190px]" />
        <Skeleton className="h-9 w-[150px]" />
      </div>
      <div className="flex flex-col items-stretch gap-2 sm:flex-row">
        {DAYS.map((day) => (
          <div className="flex flex-1 flex-col gap-1.5 rounded-md border border-border p-2 sm:min-w-[110px]" key={day}>
            <Skeleton className="h-5 w-20" />
            {SLOTS.map((slot) => (
              <div className="flex flex-col gap-1 rounded bg-muted p-2" key={slot}>
                <Skeleton className="h-4 w-16" />
                <Skeleton className="h-[34px] w-full" />
                <Skeleton className="h-3 w-10" />
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}

export function WeekCalendarSkeleton() {
  return (
    <div className="flex flex-col gap-5">
      <WeekBlockSkeleton />
      <WeekBlockSkeleton />
    </div>
  );
}
