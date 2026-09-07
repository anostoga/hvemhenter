import { Skeleton } from "@/components/ui/skeleton";

/**
 * Suspense-fallback for /profil — speiler skjemaets struktur (label + input,
 * avatar + knapp, lagre-knapp) med `Skeleton`-plassholdere, samme
 * begrunnelse som `WeekCalendarSkeleton`/`FamilieSkeleton`.
 */
export function ProfilSkeleton() {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1.5">
        <Skeleton className="h-4 w-32" />
        <Skeleton className="h-9 w-full max-w-xs" />
      </div>

      <div className="flex flex-col gap-1.5">
        <Skeleton className="h-4 w-16" />
        <div className="flex items-center gap-3">
          <Skeleton className="h-9 w-9 rounded-full" />
          <Skeleton className="h-9 w-28" />
        </div>
      </div>

      <Skeleton className="h-9 w-24" />
    </div>
  );
}
