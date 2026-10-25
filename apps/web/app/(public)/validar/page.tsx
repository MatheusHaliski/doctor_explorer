import type { Metadata } from "next";
import { CodeForm } from "@/components/public/Validator";

export const metadata: Metadata = { title: "Validar receita ou pedido de exame", robots: { index: false } };

export default function ValidatePage() {
  return (
    <>
      <h1 className="text-lg">Validar documento médico</h1>
      <p className="text-sm text-text-2">Digite o código impresso no rodapé da receita ou do pedido de exame, ou leia o QR com a câmera.</p>
      <CodeForm />
    </>
  );
}
