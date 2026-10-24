import type { Metadata } from "next";
import { ManageView } from "./ManageView";

export const metadata: Metadata = { title: "Gestão da instituição" };

export default async function ManagePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ManageView id={id} />;
}
