import ConfirmationClient from "./ConfirmationClient";

interface ConfirmationPageProps {
  params: Promise<{ token: string }>;
}

export const metadata = {
  title: "Bekreft oppgave — Barnehage-planlegger",
  robots: {
    index: false,
    follow: false,
    noarchive: true,
  },
  referrer: "no-referrer",
};
export const dynamic = "force-dynamic";
export const revalidate = 0;

export default async function ConfirmationPage({ params }: ConfirmationPageProps) {
  const { token } = await params;
  return <ConfirmationClient token={token} />;
}
