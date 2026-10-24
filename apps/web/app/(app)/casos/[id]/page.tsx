import type { Metadata } from "next";
import { CaseView } from "./CaseView";

export const metadata: Metadata = { title: "Caso" };

export default async function CasePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <CaseView id={id} />;
}
