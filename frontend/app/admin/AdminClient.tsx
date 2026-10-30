"use client";

import { useState } from "react";
import { api, AdminStats } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

interface AdminClientProps {
  initialStats: AdminStats;
}

export default function AdminClient({ initialStats }: AdminClientProps) {
  const [stats, setStats] = useState(initialStats);
  const [sortOrder, setSortOrder] = useState("newest");
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const families = [...stats.families].sort((a, b) => {
    if (a.lastActiveAt === null && b.lastActiveAt === null) return a.id.localeCompare(b.id);
    if (a.lastActiveAt === null) return sortOrder === "oldest" ? -1 : 1;
    if (b.lastActiveAt === null) return sortOrder === "oldest" ? 1 : -1;
    const difference = Date.parse(a.lastActiveAt) - Date.parse(b.lastActiveAt);
    return (sortOrder === "oldest" ? difference : -difference) || a.id.localeCompare(b.id);
  });

  async function refreshStats() {
    setRefreshing(true);
    setError(null);
    try {
      setStats(await api.getAdminStats());
    } catch (e) {
      setError(`Kunne ikke oppdatere statistikken: ${String(e)}`);
    } finally {
      setRefreshing(false);
    }
  }

  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2>Statistikk</h2>
        <Button variant="ghost" onClick={refreshStats} disabled={refreshing}>
          {refreshing ? "Oppdaterer …" : "Oppdater"}
        </Button>
      </div>
      {error && <p role="alert" className="text-destructive">{error}</p>}
      <div className="flex flex-wrap gap-4">
        <StatCard label="Familier" value={stats.familyCount} />
        <StatCard label="Brukere" value={stats.userCount} />
        <StatCard label="Hjelpere" value={stats.helperCount} />
      </div>

      <h3 className="mt-4 font-semibold">Aktive familier</h3>
      <div className="flex flex-wrap gap-4">
        <StatCard label="Siste 24 timer" value={stats.activeFamiliesLast24Hours} />
        <StatCard label="Siste 7 dager" value={stats.activeFamiliesLast7Days} />
        <StatCard label="Siste 30 dager" value={stats.activeFamiliesLast30Days} />
      </div>

      <h3 className="mt-4 font-semibold">Familier som har endret planen</h3>
      <div className="flex flex-wrap gap-4">
        <StatCard label="Siste 7 dager" value={stats.updatedFamiliesLast7Days} />
        <StatCard label="Siste 30 dager" value={stats.updatedFamiliesLast30Days} />
      </div>

      <h3 className="mt-4 font-semibold">Aktivitet per familie</h3>
      <p className="m-0 text-sm text-muted-foreground" id="family-activity-description">
        Aktivitet er visning eller endring av ukeplanen. Visninger registreres høyst én gang per
        15 minutter per familie, så visningstidene er omtrentlige. Tidspunkter vises i norsk tid.
        Eldre aktivitet er ikke etterregistrert.
      </p>
      <div className="flex flex-wrap items-center gap-2">
        <label htmlFor="family-activity-sort">Sorter etter aktivitet</label>
        <Select value={sortOrder} onValueChange={setSortOrder}>
          <SelectTrigger id="family-activity-sort">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="newest">Nyeste først</SelectItem>
            <SelectItem value="oldest">Eldste / ingen aktivitet først</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-left text-sm" aria-describedby="family-activity-description">
          <caption className="sr-only">Siste registrerte aktivitet for hver familie</caption>
          <thead>
            <tr className="border-b">
              <th scope="col" className="p-3">Familie-ID</th>
              <th scope="col" className="p-3">Sist aktiv</th>
              <th scope="col" className="p-3">Sist vist (ca.)</th>
              <th scope="col" className="p-3">Sist endret</th>
            </tr>
          </thead>
          <tbody>
            {families.map((family) => (
              <tr key={family.id} className="border-b">
                <th scope="row" className="p-3 font-normal"><code>{family.id}</code></th>
                <td className="p-3"><ActivityTime value={family.lastActiveAt} /></td>
                <td className="p-3"><ActivityTime value={family.lastCalendarViewedAt} /></td>
                <td className="p-3"><ActivityTime value={family.lastCalendarUpdatedAt} /></td>
              </tr>
            ))}
            {families.length === 0 && (
              <tr><td colSpan={4} className="p-3">Ingen familier registrert.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function ActivityTime({ value }: { value: string | null }) {
  if (value === null) return <span className="text-muted-foreground">Ingen aktivitet registrert</span>;
  return (
    <time dateTime={value}>
      {new Date(value).toLocaleString("nb-NO", {
        timeZone: "Europe/Oslo",
        dateStyle: "short",
        timeStyle: "short",
      })}
    </time>
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
