import type { Metadata } from "next";
import { PatientDownload } from "@/components/public/PatientDownload";

export const metadata: Metadata = { title: "Seu documento médico", robots: { index: false }, referrer: "no-referrer" };

export default async function PatientLinkPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return <PatientDownload token={token} />;
}
