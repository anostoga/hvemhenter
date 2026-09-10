"use client";

import { useState } from "react";
import { api, AdminInviteCode, AdminStats } from "@/lib/api";
import { Button } from "@/components/ui/button";

interface AdminClientProps {
  initialStats: AdminStats;
  initialInviteCodes: AdminInviteCode[];
}

export default function AdminClient({ initialStats, initialInviteCodes }: AdminClientProps) {
  const [stats, setStats] = useState(initialStats);
  const [inviteCodes, setInviteCodes] = useState(initialInviteCodes);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copiedCode, setCopiedCode] = useState<string | null>(null);

  async function handleCreateCode() {
    setCreating(true);
    setError(null);
    try {
      const created = await api.createAdminInviteCode();
      setInviteCodes((prev) => [created, ...prev]);

    } catch {
      setError("Kunne ikke opprette invitasjonskode. Prøv igjen.");
    } finally {
      setCreating(false);
    }
  }

  async function handleCopy(code: string) {
    const joinUrl = `${window.location.origin}/join?code=${encodeURIComponent(code)}`;
    await navigator.clipboard.writeText(joinUrl);
    setCopiedCode(code);
    setTimeout(() => setCopiedCode(null), 2000);
  }

  async function refreshStats() {
    try {
      setStats(await api.getAdminStats());
    } catch {

    }
  }

  const unusedCodes = inviteCodes.filter((c) => !c.usedAt);
  const usedCodes = inviteCodes.filter((c) => c.usedAt);

  return (
    <div className="flex flex-col gap-8">
      <section className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2>Statistikk</h2>
          <Button variant="ghost" onClick={refreshStats}>Oppdater</Button>
        </div>
        <div className="flex flex-wrap gap-4">
          <StatCard label="Familier" value={stats.familyCount} />
          <StatCard label="Brukere" value={stats.userCount} />
          <StatCard label="Hjelpere" value={stats.helperCount} />
        </div>
      </section>

      <section className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2>Invitasjonskoder</h2>
          <Button onClick={handleCreateCode} disabled={creating}>
            {creating ? "Oppretter …" : "Opprett ny kode"}
          </Button>
        </div>
        <p className="text-sm text-muted-foreground">
          En invitasjonskode brukes til å opprette (ikke bli med i) en helt ny familie — engangsbruk,
          send lenken videre til personen som skal opprette familien sin.
        </p>
        {error && <p className="text-sm text-destructive">{error}</p>}

        {unusedCodes.length > 0 && (
          <div className="flex flex-col gap-2">
            <h3 className="text-sm font-semibold">Ubrukte koder</h3>
            <ul className="flex flex-col gap-2">
              {unusedCodes.map((c) => (
                <li key={c.code} className="flex items-center justify-between gap-3 rounded-md border p-2">
                  <code className="text-sm">{c.code}</code>
                  <Button variant="outline" size="sm" onClick={() => handleCopy(c.code)}>
                    {copiedCode === c.code ? "Kopiert!" : "Kopier lenke"}
                  </Button>
                </li>
              ))}
            </ul>
          </div>
        )}

        {usedCodes.length > 0 && (
          <div className="flex flex-col gap-2">
            <h3 className="text-sm font-semibold">Brukte koder</h3>
            <ul className="flex flex-col gap-2">
              {usedCodes.map((c) => (
                <li key={c.code} className="flex items-center justify-between gap-3 rounded-md border p-2 opacity-60">
                  <code className="text-sm">{c.code}</code>
                  <span className="text-xs">brukt {new Date(c.usedAt!).toLocaleDateString("nb-NO")}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {inviteCodes.length === 0 && <p className="text-sm text-muted-foreground">Ingen invitasjonskoder opprettet ennå.</p>}
      </section>
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex min-w-32 flex-col gap-1 rounded-md border p-4">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className="text-2xl font-semibold">{value}</span>
    </div>
  );
}
