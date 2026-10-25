import type { Metadata } from "next";
import { DocumentEditor } from "@/components/documents/DocumentEditor";

export const metadata: Metadata = { title: "Receita e pedido de exame" };

export default async function DocumentPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <DocumentEditor id={id} />;
}
