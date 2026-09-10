"use client";

import { useState } from "react";
import { api, AvailableCalendar, MyCalendar } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

const NO_CALENDAR = "__none__";

interface InnstillingerClientProps {
  initialMyCalendar: MyCalendar;
  initialAvailableCalendars: AvailableCalendar[] | null;
}

type AvailabilityMode = "same" | "custom" | "disabled";

function modeFor(myCalendar: MyCalendar): AvailabilityMode {
  if (myCalendar.availabilityDisabled) return "disabled";
  if (myCalendar.availabilityCalendarId) return "custom";
  return "same";
}

export default function InnstillingerClient({ initialMyCalendar, initialAvailableCalendars }: InnstillingerClientProps) {

  const connected = initialAvailableCalendars !== null;
  const [myCalendar, setMyCalendar] = useState<MyCalendar | null>(initialMyCalendar);
  const [calendarInput, setCalendarInput] = useState(initialMyCalendar.calendarId ?? "");

  const [availabilityCalendarInput, setAvailabilityCalendarInput] = useState(
    initialMyCalendar.availabilityCalendarId ?? "",
  );

  const [availabilityMode, setAvailabilityMode] = useState<AvailabilityMode>(modeFor(initialMyCalendar));

  const [availableCalendars, setAvailableCalendars] = useState<AvailableCalendar[] | null>(initialAvailableCalendars);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  async function saveMyCalendar(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSaved(false);
    try {
      const availabilityDisabled = availabilityMode === "disabled";
      const availabilityCalendarId = availabilityMode === "custom" ? availabilityCalendarInput || null : null;
      const c = await api.updateMyCalendar(calendarInput || null, availabilityCalendarId, availabilityDisabled);
      setMyCalendar(c);
      setSaved(true);
    } catch (e) {
      setError(String(e));
    }
  }

  const hasCalendars = availableCalendars !== null && availableCalendars.length > 0;

  return (
    <>
      {error && <p className="text-destructive">{error}</p>}

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
            ⚠️ Ingen kalender er valgt — dine tildelinger opprettes ikke som kalenderhendelser.
          </p>
        )}

        {hasCalendars ? (
          <form onSubmit={saveMyCalendar} className="flex flex-col gap-6">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="calendarSelect">Kalender som oppdateres med tildelinger</Label>
              <Select
                value={calendarInput || NO_CALENDAR}
                onValueChange={(value) => setCalendarInput(value === NO_CALENDAR ? "" : value)}
              >
                <SelectTrigger id="calendarSelect">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_CALENDAR}>Skriv ikke til noen kalender</SelectItem>
                  {availableCalendars.map((c) => (
                    <SelectItem key={c.id} value={c.id}>
                      {c.summary}
                      {c.primary ? " (hoved)" : ""}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="availabilityModeSelect">Tilgjengelighetssjekk</Label>
              <Select value={availabilityMode} onValueChange={(value) => setAvailabilityMode(value as AvailabilityMode)}>
                <SelectTrigger id="availabilityModeSelect">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="same">Bruk samme kalender som for skriving av tildelinger</SelectItem>
                  <SelectItem value="custom">Bruk en annen kalender for tilgjengelighet</SelectItem>
                  <SelectItem value="disabled">Ikke sjekk tilgjengelighet automatisk</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {availabilityMode === "custom" && (
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="availabilityCalendarSelect">Kalender for tilgjengelighetsjekk</Label>
                <Select value={availabilityCalendarInput || undefined} onValueChange={setAvailabilityCalendarInput}>
                  <SelectTrigger id="availabilityCalendarSelect">
                    <SelectValue placeholder="Velg en kalender …" />
                  </SelectTrigger>
                  <SelectContent>
                    {availableCalendars.map((c) => (
                      <SelectItem key={c.id} value={c.id}>
                        {c.summary}
                        {c.primary ? " (hoved)" : ""}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            <div className="flex items-center gap-2">
              <Button type="submit" disabled={availabilityMode === "custom" && !availabilityCalendarInput}>
                Lagre
              </Button>
              {saved && <span>✅ Lagret</span>}
            </div>
          </form>
        ) : (
          <p>
            {connected
              ? "Fant ingen kalendere å velge mellom."
              : "Koble til Google-kalenderen din ovenfor for å velge kalender fra en liste."}
          </p>
        )}
      </section>
    </>
  );
}
