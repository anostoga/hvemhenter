"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { api, AVATARS, Profile } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

interface ProfilClientProps {
  initialProfile: Profile;
}

/**
 * Skjema-/mutasjonslogikken for profilsiden — flyttet hit fra
 * `app/profil/page.tsx` (nå en Server Component, se den filen) for at
 * `initialProfile` skal kunne hentes server-side FØR HTML-en sendes, mens
 * selve redigeringen (navn/avatar-valg + lagring) fortsatt skjer client-side
 * akkurat som før.
 */
export default function ProfilClient({ initialProfile }: ProfilClientProps) {
  const router = useRouter();
  const [name, setName] = useState(initialProfile.name);
  const [avatar, setAvatar] = useState<string | null>(initialProfile.avatar);
  const [showAvatarPicker, setShowAvatarPicker] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  async function handleSave(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSaved(false);
    setSaving(true);
    try {
      await api.updateProfile({ name, avatar });
      setSaved(true);
      // Nav-navnet/avataren hentes server-side i layout.tsx (se getServerWhoAmI) —
      // router.refresh() kjører serverkomponentene på nytt uten en full
      // sideinnlasting, slik at toppmenyen viser det nye navnet/avataren med en gang.
      router.refresh();
    } catch (e) {
      setError(String(e));
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      {error && <p className="text-destructive">{error}</p>}
      {saved && <p role="status">Lagret!</p>}

      <form onSubmit={handleSave}>
        <Label htmlFor="displayName">Visningsnavn</Label>
        <br />
        <Input
          id="displayName"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />
        <br />
        <br />

        <span id="avatarLabel">Avatar</span>
        <br />
        <span className="text-3xl leading-none" aria-labelledby="avatarLabel">
          {avatar ?? "—"}
        </span>{" "}
        <Button type="button" variant="outline" onClick={() => setShowAvatarPicker(true)}>
          Velg avatar
        </Button>
        <br />
        <br />

        <Button type="submit" disabled={saving || !name.trim()}>
          Lagre
        </Button>
      </form>

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
