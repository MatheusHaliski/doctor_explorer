import type { Metadata } from "next";
import { Validator } from "@/components/public/Validator";

export const metadata: Metadata = { title: "Validar receita ou pedido de exame", robots: { index: false } };

export default async function ValidateCodePage({ params }: { params: Promise<{ codigo: string }> }) {
  const { codigo } = await params;
  return <Validator code={decodeURIComponent(codigo)} />;
}
