"use client";

import { useState } from "react";
import { api, AVATARS, Parent } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

interface HelpersManagerProps {
  initialParents: Parent[];
}

/**
 * Kombinert visning av "Medlemmer" (innloggede foreldre, ren visning — som
 * før) og "Hjelpere" (personer som kan tildeles levering/henting, typisk
 * slektninger, men som ALDRI logger inn selv — se backend FamilyRoutes/
 * FamilyRepository.addHelper). Flyttet hit fra `app/familie/page.tsx` (som nå
 * er en ren Server Component) fordi legg til/fjern-hjelper krever
 * client-side mutasjon, samme mønster som ProfilClient/InnstillingerClient.
 *
 * `initialParents` (BEGGE typer, se /api/parents) hentes server-side FØR
 * HTML-en sendes, og eies deretter lokalt her — nye/fjernede hjelpere
 * oppdaterer kun denne komponentens state, ingen full sideinnlasting eller
 * `router.refresh()` nødvendig siden ingenting annet på siden (nav,
 * invitasjonskode) avhenger av listen over hjelpere.
 */
export default function HelpersManager({ initialParents }: HelpersManagerProps) {
  const [parents, setParents] = useState<Parent[]>(initialParents);
  const [name, setName] = useState("");
  const [avatar, setAvatar] = useState<string | null>(null);
  const [showAvatarPicker, setShowAvatarPicker] = useState(false);
  const [adding, setAdding] = useState(false);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const members = parents.filter((p) => !p.isHelper);
  const helpers = parents.filter((p) => p.isHelper);

  async function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setAdding(true);
    try {
      const helper = await api.addHelper({ name: name.trim(), avatar });
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
      setName("");
      setAvatar(null);
    } catch (e) {
      setError(String(e));
    } finally {
      setAdding(false);
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
          <ul className="mb-4">
            {helpers.map((h) => (
              <li key={h.id} className="flex items-center gap-2">
                {h.avatar && <span aria-hidden="true">{h.avatar}</span>} {h.name}
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  disabled={removingId === h.id}
                  onClick={() => handleRemove(h.id)}
                >
                  Fjern
                </Button>
              </li>
            ))}
          </ul>
        )}

        <form onSubmit={handleAdd} className="flex max-w-xs flex-col gap-3">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="helperName">Navn</Label>
            <Input
              id="helperName"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="F.eks. Bestemor"
              required
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <span id="helperAvatarLabel">Avatar (valgfritt)</span>
            <div className="flex items-center gap-3">
              <span className="text-2xl leading-none" aria-labelledby="helperAvatarLabel">
                {avatar ?? "—"}
              </span>
              <Button type="button" variant="outline" size="sm" onClick={() => setShowAvatarPicker(true)}>
                Velg avatar
              </Button>
            </div>
          </div>
          <Button type="submit" disabled={adding || !name.trim()} className="self-start">
            Legg til hjelper
          </Button>
        </form>
      </section>

      <Dialog open={showAvatarPicker} onOpenChange={setShowAvatarPicker}>
        <DialogContent aria-label="Velg avatar">
          <DialogHeader>
            <DialogTitle>Velg avatar</DialogTitle>
          </DialogHeader>
          <div className="grid grid-cols-4 gap-2">
            {AVATARS.map((a) => (
              <button
                key={a}
                type="button"
                aria-pressed={avatar === a}
                aria-label={`Velg avatar ${a}`}
                onClick={() => {
                  setAvatar(a);
                  setShowAvatarPicker(false);
                }}
                className="cursor-pointer rounded-lg border-2 border-transparent bg-muted p-2.5 text-2xl leading-none hover:bg-border aria-pressed:border-primary aria-pressed:bg-accent"
              >
                {a}
              </button>
            ))}
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
