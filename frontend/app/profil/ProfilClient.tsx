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
  DialogDescription,
  DialogFooter,
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

  // Etternavnet i det LAGREDE navnet (ikke det ev. uendrede feltet over) —
  // brukeren må skrive dette for å bekrefte sletting, se `<Dialog>` under.
  // Kun en UX-sperre mot utilsiktet klikk (selve sikkerheten er sesjonen
  // alene, se backend AccountRoutes) — matches derfor ganske slapt (trim +
  // små bokstaver), ikke en reell valideringsregel.
  const lastName = initialProfile.name.trim().split(/\s+/).pop() ?? "";
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const canConfirmDelete = deleteConfirmText.trim().toLowerCase() === lastName.toLowerCase();

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

  function openDeleteDialog() {
    setDeleteError(null);
    setDeleteConfirmText("");
    setShowDeleteDialog(true);
  }

  async function handleDeleteAccount() {
    if (!canConfirmDelete) return;
    setDeleteError(null);
    setDeleting(true);
    try {
      await api.deleteAccount();
      // Sesjonen er allerede tømt server-side (se backend AccountRoutes) —
      // full sideinnlasting (ikke router.push) slik at Nav/layout garantert
      // laster det uinnloggede menyoppsettet på nytt, samme mønster som
      // utlogging i Nav.tsx.
      window.location.href = "/";
    } catch (e) {
      setDeleteError(String(e));
      setDeleting(false);
    }
  }

  return (
    <>
      {error && <p className="mb-4 text-destructive">{error}</p>}
      {saved && (
        <p role="status" className="mb-4">
          Lagret!
        </p>
      )}

      <form onSubmit={handleSave} className="flex flex-col gap-6">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="displayName">Visningsnavn</Label>
          <Input
            id="displayName"
            className="max-w-xs"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <span id="avatarLabel">Avatar</span>
          <div className="flex items-center gap-3">
            <span className="text-3xl leading-none" aria-labelledby="avatarLabel">
              {avatar ?? "—"}
            </span>
            <Button type="button" variant="outline" onClick={() => setShowAvatarPicker(true)}>
              Velg avatar
            </Button>
          </div>
        </div>

        <Button type="submit" disabled={saving || !name.trim()} className="self-start">
          Lagre
        </Button>
      </form>

      <section className="mt-10 border-t border-border pt-6">
        <h2>Slett konto</h2>
        <p className="mb-4">
          Dette sletter kontoen din permanent: fremtidige tildelinger fjernes, appens tilgang til Google-kontoen
          din tilbakekalles, og du logges ut. Historiske tildelinger blir stående som historikk for resten av
          familien.{" "}
          {lastName && (
            <>
              Er dette den eneste innloggede forelderen i familien, slettes i tillegg hele familien (inkl.
              hjelpere og invitasjonskode).
            </>
          )}
        </p>
        <Button type="button" variant="destructive" onClick={openDeleteDialog}>
          Slett konto
        </Button>
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

      <Dialog
        open={showDeleteDialog}
        onOpenChange={(open) => {
          if (!deleting) setShowDeleteDialog(open);
        }}
      >
        <DialogContent aria-label="Bekreft sletting av konto">
          <DialogHeader>
            <DialogTitle>Slette kontoen din?</DialogTitle>
            <DialogDescription>
              Dette kan ikke angres. Skriv etternavnet ditt (<strong>{lastName}</strong>) for å bekrefte.
            </DialogDescription>
          </DialogHeader>
          {deleteError && <p className="text-destructive">{deleteError}</p>}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="deleteConfirmName">Etternavn</Label>
            <Input
              id="deleteConfirmName"
              value={deleteConfirmText}
              onChange={(e) => setDeleteConfirmText(e.target.value)}
              autoFocus
              autoComplete="off"
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setShowDeleteDialog(false)} disabled={deleting}>
              Avbryt
            </Button>
            <Button type="button" variant="destructive" disabled={!canConfirmDelete || deleting} onClick={handleDeleteAccount}>
              Slett konto
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
