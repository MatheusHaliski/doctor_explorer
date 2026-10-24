import type { Metadata } from "next";
import { InstitutionView } from "./InstitutionView";

export const metadata: Metadata = { title: "Instituição" };

export default async function InstitutionPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <InstitutionView id={id} />;
}
