"use client";

import { useEffect, useState } from "react";
import { api, WhoAmI } from "@/lib/api";
import { Logo } from "./Logo";

/**
 * Vises på alle sider (se layout.tsx). Kaller /auth/whoami — den svarer alltid
 * 200 (også uinnlogget), så dette utløser aldri 401-redirecten i lib/api.ts sin
 * handle(). Uten denne meldte forsiden aldri fra om brukeren var innlogget eller
 * ikke, og det fantes ingen synlig vei til å logge inn eller ut.
 */
export function Nav() {
  const [who, setWho] = useState<WhoAmI | null>(null);

  useEffect(() => {
    api.whoAmI().then(setWho).catch(() => setWho({ loggedIn: false }));
  }, []);

  async function handleLogout() {
    await api.logout();
    window.location.href = "/";
  }

  return (
    <nav className="topnav">
      <a href="/" className="topnav-brand">
        <Logo size={32} />
        Hvem henter?
      </a>
      <span className="topnav-links">
        {who === null ? null : who.loggedIn ? (
          <>
            <span>Innlogget som {who.name ?? "deg"}</span>
            <a href="/dashboard">Innstillinger</a>
            <a href="/kalender">Kalender</a>
            <a href="/familie">Familie</a>
            <button onClick={handleLogout}>Logg ut</button>
          </>
        ) : (
          <>
            <a href={api.loginUrl()}>Logg inn</a>
            <a href="/join">Bli med i en familie</a>
            {process.env.NODE_ENV !== "production" && (
              // Kun synlig i lokal dev (npm run dev) — lar deg teste innlogging uten en
              // ekte Google-klient. Krever i tillegg MOCK_GOOGLE_AUTH=true i backend/.env,
              // se README "Mock Google-innlogging (lokal dev)". Denne lenken vises alltid
              // i dev uavhengig av det flagget — /auth/mock-login svarer selv 404 hvis
              // MOCK_GOOGLE_AUTH ikke er satt på backend-siden.
              <a href="/auth/mock-login">Mock-innlogging (dev)</a>
            )}
          </>
        )}
      </span>
    </nav>
  );
}
