import type { Metadata } from "next";
import { PatientRecord } from "./PatientRecord";

export const metadata: Metadata = { title: "Paciente" };

export default async function PatientPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <PatientRecord id={id} />;
}
