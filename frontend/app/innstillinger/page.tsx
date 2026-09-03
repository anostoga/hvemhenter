"use client";

import { useEffect, useState } from "react";
import { api, AvailableCalendar, MyCalendar } from "@/lib/api";

export default function InnstillingerPage() {
  // Kun den innloggede brukerens EGEN tilkoblingsstatus/kalender vises her —
  // ikke lenger en familie-delt kalender (se AssignmentRoutes.kt for hvorfor:
  // hver forelder skriver nå sine egne tildelinger til sin egen kalender).
  const [connected, setConnected] = useState<boolean | null>(null);
  const [myCalendar, setMyCalendar] = useState<MyCalendar | null>(null);
  const [calendarInput, setCalendarInput] = useState("");
  // null = ikke tilkoblet Google ennå (eller henting feilet) — da kan ingen
  // kalender velges i det hele tatt (se meldingen i JSX under). Tom liste =
  // tilkoblet, men ingen kalendere funnet (uvanlig, samme fallback som null).
  const [availableCalendars, setAvailableCalendars] = useState<AvailableCalendar[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    api
      .getMyCalendar()
      .then((c) => {
        setMyCalendar(c);
        setCalendarInput(c.calendarId ?? "");
      })
      .catch((e) => setError(String(e)));
    // Egen catch (ikke satt til den globale `error`) — 409 (ikke tilkoblet ennå)
    // er en forventet tilstand, ikke en feil å vise brukeren som en rød advarsel.
    api
      .getAvailableCalendars()
      .then((calendars) => {
        setAvailableCalendars(calendars);
        setConnected(calendars !== null);
      })
      .catch(() => {
        setAvailableCalendars(null);
        setConnected(false);
      });
  }, []);

  async function saveMyCalendar(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSaved(false);
    try {
      const c = await api.updateMyCalendar(calendarInput);
      setMyCalendar(c);
      setSaved(true);
    } catch (e) {
      setError(String(e));
    }
  }

  // Kun mulig å velge kalender fra en liste hentet fra Google — ALDRI ved å
  // skrive inn en vilkårlig kalender-ID manuelt. Det krever at brukeren har
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
        <p>{connected ? "✅ Du er tilkoblet" : connected === false ? "Ikke tilkoblet ennå" : "Sjekker …"}</p>
      </section>

      <section>
        <h2>Min kalender</h2>
        {myCalendar && !myCalendar.calendarId && (
          <p role="alert">
            ⚠️ Ingen kalender er valgt ennå — dine tildelinger opprettes IKKE som kalenderhendelser
            før du har lagret en kalender her.
          </p>
        )}

        {hasCalendars ? (
          <form onSubmit={saveMyCalendar}>
            <label htmlFor="calendarSelect">Velg din kalender</label>
            <br />
            <select id="calendarSelect" value={calendarInput} onChange={(e) => setCalendarInput(e.target.value)}>
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
            {saved && <span> ✅ Lagret</span>}
          </form>
        ) : (
          <p>Koble til Google-kalenderen din ovenfor for å velge kalender fra en liste.</p>
        )}
      </section>
    </main>
  );
}
