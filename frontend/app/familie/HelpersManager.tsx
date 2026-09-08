"use client";

import { useState } from "react";
import { api, AVATARS, Parent } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

interface HelpersManagerProps {
  initialParents: Parent[];
}

/** Felles state for både "legg til"- og "rediger"-modalen (samme skjema,
 * kun `mode`/`id` skiller dem — se `openAddDialog`/`openEditDialog` under). */
interface HelperFormState {
  mode: "add" | "edit";
  id: string | null; // kun satt i "edit"-modus
  name: string;
  avatar: string | null;
}

/**
 * Kombinert visning av "Medlemmer" (innloggede foreldre, ren visning — som
 * før) og "Hjelpere" (personer som kan tildeles levering/henting, typisk
 * slektninger, men som ALDRI logger inn selv — se backend FamilyRoutes/
 * FamilyRepository.addHelper/updateHelper). Flyttet hit fra
 * `app/familie/page.tsx` (som nå er en ren Server Component) fordi
 * legg til/rediger/fjern-hjelper krever client-side mutasjon, samme mønster
 * som ProfilClient/InnstillingerClient.
 *
 * `initialParents` (BEGGE typer, se /api/parents) hentes server-side FØR
 * HTML-en sendes, og eies deretter lokalt her — nye/endrede/fjernede
 * hjelpere oppdaterer kun denne komponentens state, ingen full
 * sideinnlasting eller `router.refresh()` nødvendig siden ingenting annet på
 * siden (nav, invitasjonskode) avhenger av listen over hjelpere.
 *
 * Legg til og rediger deler samme modal/skjema (`<Dialog>` under) — kun
 * `formState.mode` skiller om innsending kaller `api.addHelper` eller
 * `api.updateHelper`. Avatar-valget er en enkel grid inline i modalen (ikke
 * en egen nestet dialog, i motsetning til ProfilClient sin avatar-velger)
 * for å unngå kompleksiteten med nestede Radix-dialoger.
 */
export default function HelpersManager({ initialParents }: HelpersManagerProps) {
  const [parents, setParents] = useState<Parent[]>(initialParents);
  const [formState, setFormState] = useState<HelperFormState | null>(null);
  const [saving, setSaving] = useState(false);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Hjelperen brukeren har bedt om å fjerne, mens vi venter på bekreftelse i
  // modalen (se `requestRemove`/`confirmRemove` under) — selve slettingen
  // (`handleRemove`) skjer først når brukeren bekrefter, samme mønster som
  // "Nullstill uken" i KalenderClient.tsx.
  const [pendingRemove, setPendingRemove] = useState<Parent | null>(null);

  const members = parents.filter((p) => !p.isHelper);
  const helpers = parents.filter((p) => p.isHelper);

  function openAddDialog() {
    setError(null);
    setFormState({ mode: "add", id: null, name: "", avatar: null });
  }

  function openEditDialog(helper: Parent) {
    setError(null);
    setFormState({ mode: "edit", id: helper.id, name: helper.name, avatar: helper.avatar });
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!formState) return;
    setError(null);
    setSaving(true);
    try {
      const trimmedName = formState.name.trim();
      if (formState.mode === "add") {
        const helper = await api.addHelper({ name: trimmedName, avatar: formState.avatar });
        // Resten av Parent-feltene (connected/calendarId/osv.) er alltid
        // tomme/false for en fersk hjelper — samme tilstand som backend selv
        // returnerer fra findParents() rett etter oppretting.
        setParents((prev) => [
          ...prev,
          {
            id: helper.id,
            name: helper.name,
            avatar: helper.avatar,
            connected: false,
            calendarId: null,
            availabilityCalendarId: null,
            isHelper: true,
          },
        ]);
      } else {
        const updated = await api.updateHelper(formState.id!, { name: trimmedName, avatar: formState.avatar });
        setParents((prev) =>
          prev.map((p) => (p.id === updated.id ? { ...p, name: updated.name, avatar: updated.avatar } : p)),
        );
      }
      setFormState(null);
    } catch (e) {
      setError(String(e));
    } finally {
      setSaving(false);
    }
  }

  async function handleRemove(id: string) {
    setError(null);
    setRemovingId(id);
    try {
      await api.removeHelper(id);
      setParents((prev) => prev.filter((p) => p.id !== id));
    } catch (e) {
      setError(String(e));
    } finally {
      setRemovingId(null);
    }
  }

  // Åpner bekreftelsesmodalen i stedet for å slette med en gang.
  function requestRemove(helper: Parent) {
    setPendingRemove(helper);
  }

  function confirmRemove() {
    if (pendingRemove) handleRemove(pendingRemove.id);
    setPendingRemove(null);
  }

  function cancelRemove() {
    setPendingRemove(null);
  }

  return (
    <>
      <section>
        <h2>Medlemmer</h2>
        <ul>
          {members.map((p) => (
            <li key={p.id}>
              {p.name}: {p.connected ? "✅ tilkoblet Google Kalender" : "ikke tilkoblet Google Kalender ennå"}
            </li>
          ))}
        </ul>
      </section>

      <section>
        <h2>Hjelpere</h2>
        <p>
          Personer (f.eks. besteforeldre eller andre slektninger) som kan tildeles levering/henting, men som ikke
          logger inn i appen selv.
        </p>

        {error && <p className="text-destructive">{error}</p>}

        {helpers.length > 0 && (
          <ul className="m-2">
            {helpers.map((h) => (
              <li key={h.id} className="flex items-center gap-2">
                {h.avatar && <span aria-hidden="true">{h.avatar}</span>} {h.name}
                <Button type="button" variant="default" size="lg" onClick={() => openEditDialog(h)}>
                  Rediger
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="lg"
                  disabled={removingId === h.id}
                  onClick={() => requestRemove(h)}
                >
                  Fjern
                </Button>
              </li>
            ))}
          </ul>
        )}

        <Button type="button" variant="outline" onClick={openAddDialog}>
          Legg til hjelper
        </Button>
      </section>

      <Dialog open={formState !== null} onOpenChange={(open) => !open && setFormState(null)}>
        <DialogContent aria-label={formState?.mode === "edit" ? "Rediger hjelper" : "Legg til hjelper"}>
          <DialogHeader>
            <DialogTitle>{formState?.mode === "edit" ? "Rediger hjelper" : "Legg til hjelper"}</DialogTitle>
          </DialogHeader>
          {formState && (
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="helperName">Navn</Label>
                <Input
                  id="helperName"
                  value={formState.name}
                  onChange={(e) => setFormState({ ...formState, name: e.target.value })}
                  placeholder="F.eks. Bestemor"
                  required
                  autoFocus
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <span id="helperAvatarLabel">Avatar (valgfritt)</span>
                <div className="grid grid-cols-6 gap-2" aria-labelledby="helperAvatarLabel">
                  <button
                    type="button"
                    aria-pressed={formState.avatar === null}
                    aria-label="Ingen avatar"
                    onClick={() => setFormState({ ...formState, avatar: null })}
                    className="cursor-pointer rounded-lg border-2 border-transparent bg-muted p-2 text-sm leading-none text-muted-foreground hover:bg-border aria-pressed:border-primary aria-pressed:bg-accent"
                  >
                    Ingen
                  </button>
                  {AVATARS.map((a) => (
                    <button
                      key={a}
                      type="button"
                      aria-pressed={formState.avatar === a}
                      aria-label={`Velg avatar ${a}`}
                      onClick={() => setFormState({ ...formState, avatar: a })}
                      className="cursor-pointer rounded-lg border-2 border-transparent bg-muted p-2 text-2xl leading-none hover:bg-border aria-pressed:border-primary aria-pressed:bg-accent"
                    >
                      {a}
                    </button>
                  ))}
                </div>
              </div>
              <Button type="submit" disabled={saving || !formState.name.trim()} className="self-start">
                {formState.mode === "edit" ? "Lagre" : "Legg til"}
              </Button>
            </form>
          )}
        </DialogContent>
      </Dialog>

      <Dialog
        open={pendingRemove !== null}
        onOpenChange={(open) => {
          if (!open) cancelRemove();
        }}
      >
        <DialogContent aria-label="Bekreft fjerning av hjelper">
          <DialogHeader>
            <DialogTitle>Fjerne {pendingRemove?.name}?</DialogTitle>
            <DialogDescription>
              Dette fjerner hjelperen fra familien. Hjelperen kan ikke lenger tildeles levering/henting.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={cancelRemove}>
              Avbryt
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={removingId === pendingRemove?.id}
              onClick={confirmRemove}
            >
              Fjern
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
