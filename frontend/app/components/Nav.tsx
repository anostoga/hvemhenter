"use client";

import { useEffect, useRef, useState } from "react";
import { api, WhoAmI } from "@/lib/api";
import Link from "next/link";
import { Logo } from "./Logo";

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
  const [showAccountMenu, setShowAccountMenu] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  async function handleLogout() {
    await api.logout();
    window.location.href = "/";
  }

  // Lukk ved Escape eller klikk utenfor mens menyen er åpen — samme mønster
  // som den tidligere kontomodalen, bare tilpasset en nedtrekksmeny i stedet.
  useEffect(() => {
    if (!showAccountMenu) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        setShowAccountMenu(false);
        buttonRef.current?.focus();
      }
    }
    function onClickOutside(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node) && e.target !== buttonRef.current) {
        setShowAccountMenu(false);
      }
    }
    document.addEventListener("keydown", onKeyDown);
    document.addEventListener("mousedown", onClickOutside);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.removeEventListener("mousedown", onClickOutside);
    };
  }, [showAccountMenu]);

  return (
    <nav className="topnav">
      <div className="topnav-top-row">
        <Link href="/" className="topnav-brand">
          <Logo size={32} />
          Hvem henter?
        </Link>
        {who.loggedIn ? (
          <div className="account-menu">
            <button
              type="button"
              className="topnav-username"
              onClick={() => setShowAccountMenu((v) => !v)}
              aria-haspopup="menu"
              aria-expanded={showAccountMenu}
              ref={buttonRef}
            >
              {who.avatar && <span aria-hidden="true">{who.avatar}</span>} {who.name ?? "deg"}
            </button>
            {showAccountMenu && (
              <div className="account-dropdown" role="menu" ref={menuRef}>
                <Link href="/profil" role="menuitem" onClick={() => setShowAccountMenu(false)}>
                  Profil
                </Link>
                <button type="button" role="menuitem" onClick={handleLogout}>
                  Logg ut
                </button>
              </div>
            )}
          </div>
        ) : (
          <a href={api.loginUrl()}>Logg inn</a>
        )}
      </div>
      <span className="topnav-links">
        {who.loggedIn ? (
          <>
            <Link href="/innstillinger">Innstillinger</Link>
            <Link href="/kalender">Kalender</Link>
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
