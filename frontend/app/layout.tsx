import "./globals.css";
import { Nav } from "./components/Nav";
import { getServerWhoAmI } from "@/lib/server-api";

export const metadata = {
  title: "HentMeg.no",
  description: "Fordel levering og henting i barnehagen mellom to foreldre",
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  // Hentet server-side (se lib/server-api.ts) FØR HTML-en sendes til
  // nettleseren, slik at Nav aldri viser en tom/plassholder-meny mens
  // /auth/whoami laster client-side.
  const who = await getServerWhoAmI();

  return (
    <html lang="no">
      <body>
        <Nav initialWho={who} />
        {children}
      </body>
    </html>
  );
}
