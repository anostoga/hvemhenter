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
  const [showAccountModal, setShowAccountModal] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);

  async function handleLogout() {
    await api.logout();
    window.location.href = "/";
  }

  // Lukk med Escape mens modalen er åpen.
  useEffect(() => {
    if (!showAccountModal) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") setShowAccountModal(false);
    }
    document.addEventListener("keydown", onKeyDown);
    dialogRef.current?.focus();
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [showAccountModal]);

  return (
    <nav className="topnav">
      <div className="topnav-top-row">
        <Link href="/" className="topnav-brand">
          <Logo size={32} />
          Hvem henter?
        </Link>
        {who.loggedIn ? (
          <button
            type="button"
            className="topnav-username"
            onClick={() => setShowAccountModal(true)}
            aria-haspopup="dialog"
          >
            {who.name ?? "deg"}
          </button>
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

      {showAccountModal && (
        <div
          className="modal-overlay"
          onClick={(e) => {
            // Lukk kun hvis klikket traff selve overlayet (bakgrunnen),
            // ikke innholdet i dialogboksen.
            if (e.target === e.currentTarget) setShowAccountModal(false);
          }}
        >
          <div
            className="modal-dialog"
            role="dialog"
            aria-modal="true"
            aria-label="Kontovalg"
            ref={dialogRef}
            tabIndex={-1}
          >
            <p className="modal-dialog-name">{who.name ?? "deg"}</p>
            <button onClick={handleLogout}>Logg ut</button>
            <button
              type="button"
              className="modal-dialog-close"
              onClick={() => setShowAccountModal(false)}
            >
              Avbryt
            </button>
          </div>
        </div>
      )}
    </nav>
  );
}
