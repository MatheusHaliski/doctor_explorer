import { describe, expect, it } from "vitest";
import { guessUnitAndRoute, itemErrors, newMedication, normalizeCode, sameContent, stepOf, tarjaLabel, type CatalogMedication } from "@/lib/documents";

const amoxi: CatalogMedication = {
  ggrem: "500200101",
  substance: "AMOXICILINA",
  product: "AMOXIL",
  presentation: "500 MG CAP DURA CT BL AL PLAS PVC X 21",
  laboratory: "GSK",
  tarja: "VERMELHA_RESTRICAO",
  specialControl: false,
  antimicrobial: true,
};

describe("código de validação", () => {
  it("aceita minúsculas, espaços e as trocas do Crockford", () => {
    expect(normalizeCode("abcd efgh jkmn pqrs")).toBe("ABCD-EFGH-JKMN-PQRS");
    expect(normalizeCode("0O1I-L234-5678-9ABC")).toBe("0011-1234-5678-9ABC");
  });
  it("recusa tamanho errado e U", () => {
    expect(normalizeCode("ABCD-EFGH")).toBeNull();
    expect(normalizeCode("UUUU-UUUU-UUUU-UUUU")).toBeNull();
  });
});

describe("posologia de partida", () => {
  it("deduz unidade e via da apresentação da CMED", () => {
    expect(guessUnitAndRoute("500 MG CAP DURA")).toEqual({ doseUnit: "cápsula", route: "ORAL" });
    expect(guessUnitAndRoute("500 MG COM CT BL")).toEqual({ doseUnit: "comprimido", route: "ORAL" });
    expect(guessUnitAndRoute("5 MG/ML COL OFT")).toEqual({ doseUnit: "gota", route: "OFTALMICA" });
    expect(guessUnitAndRoute("50 MG/G CREM DERM")).toEqual({ doseUnit: "aplicação", route: "TOPICA" });
  });
  it("antimicrobiano começa com duração definida", () => {
    const m = newMedication(amoxi);
    expect(m).toMatchObject({ ggrem: amoxi.ggrem, dose: "1", doseUnit: "cápsula", intervalHours: 8, timesPerDay: null, durationDays: 7, continuous: false });
    expect(tarjaLabel(amoxi)).toContain("Antimicrobiano");
    expect(tarjaLabel({ ...amoxi, antimicrobial: false, specialControl: true })).toContain("Controle especial");
  });
});

describe("fluxo", () => {
  it("passa por itens, revisão, assinatura e entrega", () => {
    expect(stepOf("RASCUNHO", false)).toBe("Itens");
    expect(stepOf("RASCUNHO", true)).toBe("Revisão");
    expect(stepOf("ASSINANDO", true)).toBe("Assinatura");
    expect(stepOf("ENVIADA", false)).toBe("Entrega");
    expect(stepOf("CANCELADA", false)).toBe("Entrega");
  });
  it("compara conteúdo e filtra erros por item", () => {
    const a = { medications: [newMedication(amoxi)], exams: [], indication: "", notes: "" };
    expect(sameContent(a, structuredClone(a))).toBe(true);
    expect(sameContent(a, { ...a, notes: "x" })).toBe(false);
    const errs = [
      { field: "medications[0].dose", message: "Dose numérica" },
      { field: "medications[1].route", message: "Via" },
      { field: "medications[10].dose", message: "outro" },
    ];
    expect(itemErrors(errs, "medications[1]")).toEqual(["Via"]);
    expect(itemErrors(errs, "medications[0]")).toEqual(["Dose numérica"]);
  });
});
