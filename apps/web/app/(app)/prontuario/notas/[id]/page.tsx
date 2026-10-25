import type { Metadata } from "next";
import { NoteEditor } from "@/components/ehr/NoteEditor";

export const metadata: Metadata = { title: "Nota SOAP" };

export default async function NotePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <NoteEditor id={id} />;
}
