import "./globals.css";
import { Nav } from "./components/Nav";
import { Footer } from "./components/Footer";
import { getServerWhoAmI } from "@/lib/server-api";
import { Inter, Geist } from "next/font/google";
import { cn } from "@/lib/utils";
import Script from "next/script";

const geist = Geist({subsets:['latin'],variable:'--font-sans'});

export const metadata = {
  title: "HvemHenter.no",
  description: "Fordel levering og henting i barnehagen",
};

export default async function RootLayout({ children }: { children: React.ReactNode }) {

  const who = await getServerWhoAmI();

  return (
    <html lang="no" className={cn("font-sans", geist.variable)}>
      <head>
        <Script id="google-tag-manager" strategy="beforeInteractive">
          {`(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':
new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],
j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src=
'https://www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f);
})(window,document,'script','dataLayer','GTM-PTSNBFC3');`}
        </Script>
      </head>
      <body className="flex min-h-screen flex-col font-sans">
        <noscript>
          <iframe
            src="https://www.googletagmanager.com/ns.html?id=GTM-PTSNBFC3"
            height="0"
            width="0"
            style={{ display: "none", visibility: "hidden" }}
            title="Google Tag Manager"
          />
        </noscript>
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
