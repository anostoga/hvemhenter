"use client";

import { useEffect, useState } from "react";
import { api, AvailableCalendar, Family, Parent } from "@/lib/api";

export default function InnstillingerPage() {
  const [parents, setParents] = useState<Parent[]>([]);
  const [family, setFamily] = useState<Family | null>(null);
  const [calendarInput, setCalendarInput] = useState("");
  // null = ikke tilkoblet Google ennå (eller henting feilet) — da kan ingen
  // kalender velges i det hele tatt (se meldingen i JSX under). Tom liste =
  // tilkoblet, men ingen kalendere funnet (uvanlig, samme fallback som null).
  const [availableCalendars, setAvailableCalendars] = useState<AvailableCalendar[] | null>(null);
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
    // Egen catch (ikke satt til den globale `error`) — 409 (ikke tilkoblet ennå)
    // er en forventet tilstand, ikke en feil å vise brukeren som en rød advarsel.
    api
      .getAvailableCalendars()
      .then(setAvailableCalendars)
      .catch(() => setAvailableCalendars(null));
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

  // Kun mulig å velge delt kalender fra en liste hentet fra Google — ALDRI ved
  // å skrive inn en vilkårlig kalender-ID manuelt. Det krever at brukeren har
  // koblet til Google-kalenderen sin (se seksjonen over), og at det finnes
  // minst én kalender å velge mellom.
  const hasCalendars = availableCalendars !== null && availableCalendars.length > 0;

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
              kalenderhendelser før du har lagret en kalender her.
            </p>
          )}

          {hasCalendars ? (
            <form onSubmit={saveSharedCalendar}>
              <label htmlFor="calendarSelect">Velg delt kalender</label>
              <br />
              <select
                id="calendarSelect"
                value={calendarInput}
                onChange={(e) => setCalendarInput(e.target.value)}
              >
                <option value="" disabled>
                  Velg en kalender …
                </option>
                {availableCalendars.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.summary}
                    {c.primary ? " (hoved)" : ""}
                  </option>
                ))}
              </select>
              <br />
              <button type="submit" disabled={!calendarInput}>
                Lagre
              </button>
            </form>
          ) : (
            <p>Koble til Google-kalenderen din ovenfor for å velge en delt kalender fra en liste.</p>
          )}
        </section>
      )}
    </main>
  );
}
