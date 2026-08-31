"use client";

import { useEffect, useState } from "react";
import { api, AvailableCalendar, Family, Parent } from "@/lib/api";

const MANUAL_OPTION = "__manual__";

export default function InnstillingerPage() {
  const [parents, setParents] = useState<Parent[]>([]);
  const [family, setFamily] = useState<Family | null>(null);
  const [calendarInput, setCalendarInput] = useState("");
  // null = ikke tilkoblet Google ennå (eller henting feilet) — da vises kun
  // fritekst-input. Tom liste = tilkoblet, men ingen kalendere funnet (uvanlig,
  // men samme fallback som null).
  const [availableCalendars, setAvailableCalendars] = useState<AvailableCalendar[] | null>(null);
  const [manualOverride, setManualOverride] = useState(false);
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

  const hasCalendars = availableCalendars !== null && availableCalendars.length > 0;
  const showManualInput = !hasCalendars || manualOverride;
  const selectValue =
    hasCalendars && availableCalendars.some((c) => c.id === calendarInput) && !manualOverride
      ? calendarInput
      : MANUAL_OPTION;

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
            {hasCalendars && (
              <>
                <label htmlFor="calendarSelect">Velg delt kalender</label>
                <br />
                <select
                  id="calendarSelect"
                  value={selectValue}
                  onChange={(e) => {
                    if (e.target.value === MANUAL_OPTION) {
                      setManualOverride(true);
                    } else {
                      setManualOverride(false);
                      setCalendarInput(e.target.value);
                    }
                  }}
                >
                  {availableCalendars.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.summary}
                      {c.primary ? " (hoved)" : ""}
                    </option>
                  ))}
                  <option value={MANUAL_OPTION}>Annet (skriv inn kalender-ID manuelt)</option>
                </select>
                <br />
              </>
            )}

            {!hasCalendars && (
              <p>
                Koble til Google-kalenderen din ovenfor for å velge fra en liste i stedet for å
                skrive inn en kalender-ID manuelt.
              </p>
            )}

            {showManualInput && (
              <>
                <label htmlFor="calendarId">Google-kalender-ID for den delte familiekalenderen</label>
                <input
                  id="calendarId"
                  value={calendarInput}
                  onChange={(e) => setCalendarInput(e.target.value)}
                />
              </>
            )}
            <br />
            <button type="submit">Lagre</button>
          </form>
        </section>
      )}
    </main>
  );
}
