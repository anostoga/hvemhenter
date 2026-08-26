import "./globals.css";

export const metadata = {
  title: "Barnehage-planlegger",
  description: "Fordel levering og henting i barnehagen mellom to foreldre",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="no">
      <body>{children}</body>
    </html>
  );
}
