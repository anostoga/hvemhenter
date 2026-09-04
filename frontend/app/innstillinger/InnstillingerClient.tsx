"use client";

import { useState } from "react";
import { api, AvailableCalendar, MyCalendar } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

interface InnstillingerClientProps {
  initialMyCalendar: MyCalendar;
  initialAvailableCalendars: AvailableCalendar[] | null;
}

/**
 * Skjema-/mutasjonslogikken for innstillingssiden — flyttet hit fra
 * `app/innstillinger/page.tsx` (nå en Server Component, se den filen) for at
 * `initialMyCalendar`/`initialAvailableCalendars` skal kunne hentes
 * server-side FØR HTML-en sendes. `initialAvailableCalendars` er `null` når
 * brukeren ikke har koblet til Google ennå — en legitim tilstand, ikke en
 * feil (se `getServerAvailableCalendars` i lib/server-api.ts).
 */
export default function InnstillingerClient({ initialMyCalendar, initialAvailableCalendars }: InnstillingerClientProps) {
  // Kun den innloggede brukerens EGEN tilkoblingsstatus/kalender vises her —
  // ikke lenger en familie-delt kalender (se AssignmentRoutes.kt for hvorfor:
  // hver forelder skriver nå sine egne tildelinger til sin egen kalender).
  const connected = initialAvailableCalendars !== null;
  const [myCalendar, setMyCalendar] = useState<MyCalendar | null>(initialMyCalendar);
  const [calendarInput, setCalendarInput] = useState(initialMyCalendar.calendarId ?? "");
  // Kalenderen tilgjengelighet (opptatte tider) hentes fra. Kun i bruk når
  // `sameCalendarForBoth` er false — se checkboxen under.
  const [availabilityCalendarInput, setAvailabilityCalendarInput] = useState(
    initialMyCalendar.availabilityCalendarId ?? "",
  );
  // Avkrysningsboksen: bruk SAMME kalender (calendarInput) til både skriving
  // av tildelinger og henting av tilgjengelighet. Da sendes
  // availabilityCalendarId = null til backend, som faller tilbake til
  // calendarId (se CalendarRoutes.kt).
  const [sameCalendarForBoth, setSameCalendarForBoth] = useState(initialMyCalendar.availabilityCalendarId == null);
  // null = ikke tilkoblet Google ennå (eller henting feilet) — da kan ingen
  // kalender velges i det hele tatt (se meldingen i JSX under). Tom liste =
  // tilkoblet, men ingen kalendere funnet (uvanlig, samme fallback som null).
  const [availableCalendars, setAvailableCalendars] = useState<AvailableCalendar[] | null>(initialAvailableCalendars);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

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
            ⚠️ Ingen kalender er valgt ennå — dine tildelinger opprettes IKKE som kalenderhendelser
            før du har lagret en kalender her.
          </p>
        )}

        {hasCalendars ? (
          <form onSubmit={saveMyCalendar}>
            <Label htmlFor="calendarSelect">Kalender som oppdateres med tildelinger</Label>
            <br />
            <Select value={calendarInput || undefined} onValueChange={setCalendarInput}>
              <SelectTrigger id="calendarSelect">
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
            <br />
            <br />
            <Label className="inline-flex items-center">
              <Checkbox
                checked={sameCalendarForBoth}
                onCheckedChange={(checked) => setSameCalendarForBoth(checked === true)}
              />
              <span className="ml-2">
                Bruk samme kalender for tilgjengelighet og skriving av tildelinger
              </span>
            </Label>
            <br />
            {!sameCalendarForBoth && (
              <div className="mt-4">
                <Label htmlFor="availabilityCalendarSelect">Kalender for tilgjengelighetsjekk</Label>
                <br />
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
                <br />
              </div>
            )}
            <br />
            <Button type="submit" disabled={!calendarInput || (!sameCalendarForBoth && !availabilityCalendarInput)}>
              Lagre
            </Button>
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
    </>
  );
}
