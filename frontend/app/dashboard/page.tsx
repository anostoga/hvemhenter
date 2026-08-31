"use client";

import { useEffect, useState } from "react";
import { api, Family, Parent } from "@/lib/api";

export default function DashboardPage() {
  const [parents, setParents] = useState<Parent[]>([]);
  const [family, setFamily] = useState<Family | null>(null);
  const [calendarInput, setCalendarInput] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.getParents().then(setParents).catch((e) => setError(String(e)));
    api
      .getFamily()
      .then((f) => {
        setFamily(f);
        setCalendarInput(f.sharedCalendarId);
      })
      .catch((e) => setError(String(e)));
  }, []);

  async function saveSharedCalendar(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const f = await api.updateSharedCalendar(calendarInput);
      setFamily(f);
    } catch (e) {
      setError(String(e));
    }
  }

  return (
    <main>
      <h1>Innstillinger</h1>
      {error && <p className="error">{error}</p>}

      <section>
        <h2>Koble til Google Kalender</h2>
        <p>
          <a href={api.authStartUrl()}>Koble til/forny min kalendertilgang</a>
        </p>
        <ul>
          {parents.map((p) => (
            <li key={p.id}>
              {p.name}: {p.connected ? "✅ tilkoblet" : "ikke tilkoblet ennå"}
            </li>
          ))}
        </ul>
      </section>

      {family && (
        <section>
          <h2>Delt kalender</h2>
          {!family.sharedCalendarId && (
            <p role="alert">
              ⚠️ Ingen delt kalender er satt opp ennå — bekreftede tildelinger opprettes IKKE som
              kalenderhendelser før du har lagret en kalender-ID her.
            </p>
          )}
          <form onSubmit={saveSharedCalendar}>
            <label htmlFor="calendarId">Google-kalender-ID for den delte familiekalenderen</label>
            <input
              id="calendarId"
              value={calendarInput}
              onChange={(e) => setCalendarInput(e.target.value)}
            />
            <button type="submit">Lagre</button>
          </form>
        </section>
      )}
    </main>
  );
}
