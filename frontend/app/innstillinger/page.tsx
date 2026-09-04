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
  // Kalenderen tilgjengelighet (opptatte tider) hentes fra. Kun i bruk når
  // `sameCalendarForBoth` er false — se checkboxen under.
  const [availabilityCalendarInput, setAvailabilityCalendarInput] = useState("");
  // Avkrysningsboksen: bruk SAMME kalender (calendarInput) til både skriving
  // av tildelinger og henting av tilgjengelighet. Da sendes
  // availabilityCalendarId = null til backend, som faller tilbake til
  // calendarId (se CalendarRoutes.kt).
  const [sameCalendarForBoth, setSameCalendarForBoth] = useState(true);
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
        setAvailabilityCalendarInput(c.availabilityCalendarId ?? "");
        setSameCalendarForBoth(c.availabilityCalendarId == null);
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
      const availabilityCalendarId = sameCalendarForBoth ? null : availabilityCalendarInput || null;
      const c = await api.updateMyCalendar(calendarInput, availabilityCalendarId);
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

      {connected === false && (
        <section>
          <h2>Koble til Google Kalender</h2>
          <p>
            <a href={api.authStartUrl()}>Koble til/forny min kalendertilgang</a>
          </p>
          <p>Ikke tilkoblet ennå</p>
        </section>
      )}

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
            <label htmlFor="calendarSelect">Kalender som oppdateres med tildelinger</label>
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
            <br />
            <label style={{ display: "inline-flex", alignItems: "center" }}>
              <input
                type="checkbox"
                checked={sameCalendarForBoth}
                onChange={(e) => setSameCalendarForBoth(e.target.checked)}
              />
              <span style={{ marginLeft: "0.5rem" }}>
                Bruk samme kalender for tilgjengelighet og skriving av tildelinger
              </span>
            </label>
            <br />
            {!sameCalendarForBoth && (
              <div style={{ marginTop: "1rem" }}>
                <label htmlFor="availabilityCalendarSelect">Kalender for tilgjengelighetsjekk</label>
                <br />
                <select
                  id="availabilityCalendarSelect"
                  value={availabilityCalendarInput}
                  onChange={(e) => setAvailabilityCalendarInput(e.target.value)}
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
              </div>
            )}
            <br />
            <button type="submit" disabled={!calendarInput || (!sameCalendarForBoth && !availabilityCalendarInput)}>
              Lagre
            </button>
            {saved && <span> ✅ Lagret</span>}
          </form>
        ) : (
          <p>
            {connected
              ? "Fant ingen kalendere å velge mellom."
              : "Koble til Google-kalenderen din ovenfor for å velge kalender fra en liste."}
          </p>
        )}
      </section>
    </main>
  );
}
