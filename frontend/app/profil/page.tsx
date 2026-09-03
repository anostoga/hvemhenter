"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, AVATARS } from "@/lib/api";

export default function ProfilPage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [avatar, setAvatar] = useState<string | null>(null);
  const [showAvatarPicker, setShowAvatarPicker] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    api
      .getProfile()
      .then((p) => {
        setName(p.name);
        setAvatar(p.avatar);
      })
      .catch((e) => setError(String(e)));
  }, []);

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
    <main>
      <h1>Profil</h1>
      {error && <p className="error">{error}</p>}
      {saved && <p role="status">Lagret!</p>}

      <form onSubmit={handleSave}>
        <label htmlFor="displayName">Visningsnavn</label>
        <br />
        <input
          id="displayName"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />
        <br />
        <br />

        <span id="avatarLabel">Avatar</span>
        <br />
        <span className="profile-avatar-preview" aria-labelledby="avatarLabel">
          {avatar ?? "—"}
        </span>{" "}
        <button type="button" onClick={() => setShowAvatarPicker(true)}>
          Velg avatar
        </button>
        <br />
        <br />

        <button type="submit" disabled={saving || !name.trim()}>
          Lagre
        </button>
      </form>

      {showAvatarPicker && (
        <div
          className="modal-overlay"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowAvatarPicker(false);
          }}
        >
          <div className="modal-dialog" role="dialog" aria-modal="true" aria-label="Velg avatar">
            <button
              type="button"
              className="modal-dialog-close"
              onClick={() => setShowAvatarPicker(false)}
            >
              Lukk
            </button>
            <div className="avatar-grid">
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
                >
                  {a}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
