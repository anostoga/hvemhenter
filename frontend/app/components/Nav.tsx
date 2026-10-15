"use client";

import { api, WhoAmI } from "@/lib/api";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Logo } from "./Logo";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

export function Nav({ initialWho }: { initialWho: WhoAmI }) {
  const who = initialWho;
  const pathname = usePathname();

  function navLinkClassName(href: string) {
    return pathname === href
      ? "rounded-full bg-primary-foreground/15 px-3 py-1 no-underline"
      : "rounded-full px-3 py-1 no-underline hover:bg-primary-foreground/10";
  }

  async function handleLogout() {
    await api.logout();
    window.location.href = "/";
  }

  return (
    <nav className="flex flex-col">
      <div className="flex items-center justify-between gap-3">
        <Link href="/" className="flex items-center gap-2 font-semibold text-inherit no-underline">
          <Logo height={76} />
        </Link>
        {who.loggedIn ? (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                className="h-auto min-h-0 -mx-2 rounded-md border-none bg-transparent px-2 py-1 font-semibold text-inherit hover:bg-primary-foreground/15 hover:text-inherit aria-expanded:bg-primary-foreground/20 aria-expanded:text-inherit"
              >
                {who.avatar && <span aria-hidden="true">{who.avatar}</span>} {who.name ?? "deg"}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem asChild>
                <Link href="/profil" className="no-underline">Profil</Link>
              </DropdownMenuItem>
              <DropdownMenuItem asChild>
                <Link href="/innstillinger" className="no-underline">Innstillinger</Link>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={handleLogout}>Logg ut</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        ) : (
          <div className="flex shrink-0 items-center gap-1.5">
            <Link
              href="/join"
              aria-label="Bli med i en familie"
              className="rounded-full border border-primary-foreground/50 px-3 py-2 text-sm font-semibold text-primary-foreground no-underline transition-colors hover:bg-primary-foreground/10"
            >
              <span className="sm:hidden">Bli med</span>
              <span className="hidden sm:inline">Bli med i familie</span>
            </Link>
            <a
              className="rounded-full bg-primary-foreground px-3 py-2 text-sm font-semibold text-primary no-underline transition-colors hover:bg-primary-foreground/90"
              href={api.loginUrl()}
            >
              Logg inn
            </a>
          </div>
        )}
      </div>
      {(who.loggedIn || process.env.NODE_ENV !== "production") && (
        <span className="flex flex-wrap items-center gap-2 border-t border-primary-foreground/20 pt-3">
          {who.loggedIn ? (
            <>
              <Link href="/" className={navLinkClassName("/")}>Forside</Link>
              <Link href="/ukeplan" className={navLinkClassName("/ukeplan")}>Ukeplan</Link>
              <Link href="/familie" className={navLinkClassName("/familie")}>Familie</Link>
              {who.isAdmin && (
                <Link href="/admin" className={navLinkClassName("/admin")}>Admin</Link>
              )}
            </>
          ) : (
            <a className="no-underline" href="/auth/mock-login">Mock-innlogging (dev)</a>
          )}
        </span>
      )}
    </nav>
  );
}
