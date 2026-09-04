import { Skeleton } from "@/components/ui/skeleton";

/**
 * Suspense-fallback for /profil — speiler skjemaets struktur (label + input,
 * avatar + knapp, lagre-knapp) med `Skeleton`-plassholdere, samme
 * begrunnelse som `WeekCalendarSkeleton`/`FamilieSkeleton`.
 */
export function ProfilSkeleton() {
  return (
    <>
      <Skeleton className="h-4 w-32" />
      <div className="mt-1 mb-4">
        <Skeleton className="h-9 w-full max-w-xs" />
      </div>

      <Skeleton className="h-4 w-16" />
      <div className="mt-1 flex items-center gap-2">
        <Skeleton className="h-9 w-9 rounded-full" />
        <Skeleton className="h-9 w-28" />
      </div>

      <div className="mt-4">
        <Skeleton className="h-9 w-24" />
      </div>
    </>
  );
}
