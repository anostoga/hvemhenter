import "./globals.css";
import { Nav } from "./components/Nav";
import { Footer } from "./components/Footer";
import { getServerWhoAmI } from "@/lib/server-api";
import { Inter, Geist } from "next/font/google";
import { cn } from "@/lib/utils";

const geist = Geist({subsets:['latin'],variable:'--font-sans'});

export const metadata = {
  title: "HvemHenter.no",
  description: "Fordel levering og henting i barnehagen",
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {

  const who = await getServerWhoAmI();

  return (
    <html lang="no" className={cn("font-sans", geist.variable)}>
      <body className="flex min-h-screen flex-col font-sans">
        {}
        <header className="w-full bg-primary text-primary-foreground">
          <div className="mx-auto max-w-[640px] px-4 py-3 sm:max-w-[clamp(630px,calc(100vw-4rem),900px)] sm:px-8">
            <Nav initialWho={who} />
          </div>
        </header>
        {}
        <div className="mx-auto w-full max-w-[640px] flex-1 px-4 pt-4 pb-4 sm:max-w-[clamp(630px,calc(100vw-4rem),900px)] sm:px-8 sm:pt-6 sm:pb-8">
          {children}
        </div>
        <Footer />
      </body>
    </html>
  );
}
