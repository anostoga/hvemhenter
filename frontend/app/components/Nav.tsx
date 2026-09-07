"use client";

import { api, WhoAmI } from "@/lib/api";
import Link from "next/link";
import { Logo } from "./Logo";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

/**
 * Vises på alle sider (se layout.tsx). `initialWho` hentes SERVER-SIDE (se
 * RootLayout og lib/server-api.ts sin getServerWhoAmI()) FØR HTML-en sendes
 * til nettleseren, slik at riktig meny og visningsnavn er der ved første
 * maling — ingen client-side /auth/whoami-kall, ingen "blink"-tilstand, og
 * ingen egen cookie trengs for å bære navnet (i motsetning til den tidligere
 * `bhg_logged_in`-cookien, som ble fjernet igjen nettopp for å unngå
 * personopplysninger i en klientlesbar cookie — se plan.md i sesjonsmappen
 * for sammenligningen som begrunnet dette valget).
 *
 * Selve autorisasjonen er fortsatt utelukkende den signerte, HttpOnly
 * `bhg_session`-cookien (se auth/SessionAuth.kt) — uendret av dette. Endrer
 * seg sesjonen (utlogging i en annen fane e.l.) mens denne fanen står åpen,
 * oppdages det ikke før neste fulle sideinnlasting eller et 401-svar fra et
 * API-kall (se handle() i lib/api.ts, som da sender brukeren til "/").
 */
export function Nav({ initialWho }: { initialWho: WhoAmI }) {
  const who = initialWho;

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
                <Link href="/profil">Profil</Link>
              </DropdownMenuItem>
              <DropdownMenuItem asChild>
                <Link href="/innstillinger">Innstillinger</Link>
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={handleLogout}>Logg ut</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        ) : (
          <Button variant="outline" asChild>
            <a href={api.loginUrl()}>Logg inn</a>
          </Button>
        )}
      </div>
      <span className="flex flex-wrap items-center gap-4">
        {who.loggedIn ? (
          <>
            <Link href="/">Forside</Link>
            <Link href="/ukeplan">Ukeplan</Link>
            <Link href="/familie">Familie</Link>
          </>
        ) : (
          <>
            <Link href="/join">Bli med i en familie</Link>
            {process.env.NODE_ENV !== "production" && (
              // Kun synlig i lokal dev (npm run dev) — lar deg teste innlogging uten en
              // ekte Google-klient. Krever i tillegg MOCK_GOOGLE_AUTH=true i backend/.env,
              // se README "Mock Google-innlogging (lokal dev)". Denne lenken vises alltid
              // i dev uavhengig av det flagget — /auth/mock-login svarer selv 404 hvis
              // MOCK_GOOGLE_AUTH ikke er satt på backend-siden. Vanlig <a> (ikke Link),
              // siden dette er en ekte backend-rute, ikke en Next.js-side.
              <a href="/auth/mock-login">Mock-innlogging (dev)</a>
            )}
          </>
        )}
      </span>
    </nav>
  );
}
