"use client";

import { useState } from "react";
import { CheckIcon, CopyIcon } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * Viser invitasjonskoden (se app/familie/page.tsx) i en stor, lett synlig
 * hvit boks med en kopier-knapp ved siden av. Egen client-komponent
 * (clipboard-API + midlertidig "kopiert"-tilstand) selv om resten av
 * /familie er en ren Server Component uten interaktivitet — se
 * FamilieData/FamiliePage for begrunnelsen for at siden ellers ikke trenger
 * "use client".
 */
export function InviteCode({ code }: { code: string }) {
  const [copied, setCopied] = useState(false);

  async function handleCopy() {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  return (
    <div className="flex items-center gap-2">
      <code className="rounded-lg border border-input bg-white px-4 py-2 text-2xl font-semibold tracking-wide">
        {code}
      </code>
      <Button type="button" variant="outline" size="icon" aria-label="Kopier invitasjonskode" onClick={handleCopy}>
        {copied ? <CheckIcon /> : <CopyIcon />}
      </Button>
    </div>
  );
}
