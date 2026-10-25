import { describe, expect, it } from "vitest";
import { conversationName, looksLikeIdentifiedPatient } from "@/lib/chat-api";

describe("chat (RF06)", () => {
  it("heurística local de paciente identificável sugere o prontuário", () => {
    expect(looksLikeIdentifiedPatient("Paciente João Silva, leito 4, piorou")).toBe(true);
    expect(looksLikeIdentifiedPatient("CPF 529.982.247-25")).toBe(true);
    expect(looksLikeIdentifiedPatient("prontuário nº 448812")).toBe(true);
    expect(looksLikeIdentifiedPatient("nascido em 02/03/1964")).toBe(true);
    expect(looksLikeIdentifiedPatient("Alguém tem experiência com ECMO em pneumonia grave?")).toBe(false);
    expect(looksLikeIdentifiedPatient("paciente sedado, sem resposta")).toBe(false);
  });

  it("nome da conversa: título do grupo ou o outro participante", () => {
    const members = [
      { userId: "a", name: "Dra. Ana" },
      { userId: "b", name: "Dr. Bruno" },
    ];
    expect(conversationName({ kind: "DIRECT", title: null, members }, "a")).toBe("Dr. Bruno");
    expect(conversationName({ kind: "GROUP", title: "Plantão", members }, "a")).toBe("Plantão");
  });
});
