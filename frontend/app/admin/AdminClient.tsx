"use client";

import { useState } from "react";
import { api, AdminStats } from "@/lib/api";
import { Button } from "@/components/ui/button";

interface AdminClientProps {
  initialStats: AdminStats;
}

export default function AdminClient({ initialStats }: AdminClientProps) {
  const [stats, setStats] = useState(initialStats);

  async function refreshStats() {
    try {
      setStats(await api.getAdminStats());
    } catch {
      // ignorer — brukeren kan prøve igjen
    }
  }

  return (
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
