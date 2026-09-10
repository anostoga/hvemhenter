import { Suspense } from "react";
import { redirect } from "next/navigation";
import { getServerAdminInviteCodes, getServerAdminStats, ForbiddenError, UnauthorizedError } from "@/lib/server-api";
import type { AdminInviteCode, AdminStats } from "@/lib/api";
import AdminClient from "./AdminClient";

export const metadata = {
  title: "Admin — Barnehage-planlegger",
};

async function AdminData() {
  let stats: AdminStats;
  let inviteCodes: AdminInviteCode[];
  try {
    [stats, inviteCodes] = await Promise.all([getServerAdminStats(), getServerAdminInviteCodes()]);
  } catch (e) {
    if (e instanceof UnauthorizedError || e instanceof ForbiddenError) {
      redirect("/");
    }
    throw e;
  }
  return <AdminClient initialStats={stats} initialInviteCodes={inviteCodes} />;
}

export default function AdminPage() {
  return (
    <main className="flex flex-col gap-6">
      <h1>Admin</h1>
      <Suspense fallback={<p>Laster …</p>}>
        <AdminData />
      </Suspense>
    </main>
  );
}
