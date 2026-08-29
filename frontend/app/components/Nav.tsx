"use client";

import { useEffect, useState } from "react";
import { api, WhoAmI } from "@/lib/api";

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
        Barnehage-planlegger
      </a>
      <span className="topnav-links">
        {who === null ? null : who.loggedIn ? (
          <>
            <span>Innlogget som {who.name ?? "deg"}</span>
            <a href="/dashboard">Dashbord</a>
            <button onClick={handleLogout}>Logg ut</button>
          </>
        ) : (
          <>
            <a href={api.loginUrl()}>Logg inn</a>
            <a href="/join">Bli med i en familie</a>
          </>
        )}
      </span>
    </nav>
  );
}
