import Link from "next/link";

/**
 * Vises på alle sider (se layout.tsx), også for uinnloggede besøkende —
 * derfor en ren server-komponent uten avhengighet til `who`/sesjon, i
 * motsetning til `Nav`. Bredden speiler header/innhold-wrapperen i
 * layout.tsx (samme `max-w`/padding), slik at lenkene visuelt står på linje
 * med resten av siden.
 */
export function Footer() {
  return (
    <footer className="w-full border-t border-border">
      <div className="mx-auto flex max-w-[640px] flex-wrap items-center gap-x-4 gap-y-1 px-4 py-4 text-sm text-muted-foreground sm:max-w-[clamp(630px,calc(100vw-4rem),900px)] sm:px-8">
        <Link href="/om" className="no-underline hover:underline">
          Om HvemHenter.no
        </Link>
        <Link href="/personvern" className="no-underline hover:underline">
          Personvernerklæring
        </Link>
        <span>© {new Date().getFullYear()} HvemHenter.no</span>
      </div>
    </footer>
  );
}
