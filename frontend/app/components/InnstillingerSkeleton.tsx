import { Skeleton } from "@/components/ui/skeleton";

/**
 * Suspense-fallback for /innstillinger — speiler "Min kalender"-skjemaet
 * (select + checkbox + lagre-knapp), samme begrunnelse som de andre
 * `*Skeleton`-komponentene.
 */
export function InnstillingerSkeleton() {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1.5">
        <Skeleton className="h-4 w-72" />
        <Skeleton className="h-9 w-full max-w-sm" />
      </div>

      <div className="flex items-center gap-2">
        <Skeleton className="h-4 w-4" />
        <Skeleton className="h-4 w-80" />
      </div>

      <Skeleton className="h-9 w-24" />
    </div>
  );
}
