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
    return pathname === href ? "underline underline-offset-2" : "no-underline";
  }

  async function handleLogout() {
    await api.logout();
    window.location.href = "/";
  }

  return (
    <nav className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-3">
        <Link href="/" className="flex items-center gap-2 font-semibold text-inherit no-underline">
          <Logo height={64} />
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
          <Button variant="ghost" asChild>
            <a className="no-underline" href={api.loginUrl()}>Logg inn</a>
          </Button>
        )}
      </div>
      <span className="flex flex-wrap items-center gap-4">
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
          <>
            <Link href="/join" className="no-underline">Bli med i en familie</Link>
            {process.env.NODE_ENV !== "production" && (

              <a className="no-underline" href="/auth/mock-login">Mock-innlogging (dev)</a>
            )}
          </>
        )}
      </span>
    </nav>
  );
}
