import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DashboardView } from "@/components/dashboard/DashboardView";
import { serverGet } from "@/lib/server-api";

export const metadata: Metadata = { title: "Painel da instituição" };

/** RF13 · painel de uma instituição e das unidades abaixo: gestor do hospital/rede e auditor, com 2FA. */
export default async function InstitutionPanelPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const { data } = await serverGet<{ id: string; name: string }>(`/api/v1/institutions/${id}`);
  if (!data) notFound();
  return <DashboardView scope={{ kind: "institution", id, name: data.name }} />;
}
