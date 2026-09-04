import "./globals.css";
import { Nav } from "./components/Nav";
import { getServerWhoAmI } from "@/lib/server-api";
import { Inter, Geist } from "next/font/google";
import { cn } from "@/lib/utils";

const geist = Geist({subsets:['latin'],variable:'--font-sans'});

export const metadata = {
  title: "HvemHenter.no",
  description: "Fordel levering og henting i barnehagen",
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  // Hentet server-side (se lib/server-api.ts) FØR HTML-en sendes til
  // nettleseren, slik at Nav aldri viser en tom/plassholder-meny mens
  // /auth/whoami laster client-side.
  const who = await getServerWhoAmI();

  return (
    <html lang="no" className={cn("font-sans", geist.variable)}>
      <body className="font-sans">
        <div className="mx-auto max-w-[640px] px-4 pb-4 sm:max-w-[clamp(630px,calc(100vw-4rem),900px)] sm:px-8 sm:pb-8">
          <Nav initialWho={who} />
          {children}
        </div>
      </body>
    </html>
  );
}
