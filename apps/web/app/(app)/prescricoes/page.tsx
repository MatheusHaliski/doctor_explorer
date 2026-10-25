import type { Metadata } from "next";
import { MyDocuments } from "@/components/documents/MyDocuments";

export const metadata: Metadata = { title: "Prescrições" };

export default function PrescriptionsPage() {
  return <MyDocuments />;
}
