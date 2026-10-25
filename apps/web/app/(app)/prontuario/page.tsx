"use client";

import { useRouter } from "next/navigation";
import { PatientPicker } from "@/components/ehr/PatientPicker";

/** RF08 · cofre clínico do médico: busca e cadastro de pacientes. */
export default function EhrHome() {
  const router = useRouter();
  return (
    <div className="max-w-3xl">
      <h1>Prontuário</h1>
      <p className="lead">Seu cofre clínico: pacientes e notas SOAP cifrados, guardados por 20 anos. Toda leitura e escrita fica registrada na auditoria.</p>
      <PatientPicker actionLabel="Abrir" onPick={(p) => router.push(`/prontuario/pacientes/${p.id}`)} />
    </div>
  );
}
