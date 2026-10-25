import { describe, expect, it } from "vitest";
import { ageFrom, groupByLineage, sameDraft, type CompositionSummary } from "@/lib/ehr";

describe("ageFrom", () => {
  it("conta anos completos", () => {
    expect(ageFrom("1964-03-02", new Date(2026, 2, 1))).toBe(61);
    expect(ageFrom("1964-03-02", new Date(2026, 2, 2))).toBe(62);
  });
});

describe("groupByLineage", () => {
  const s = (lineageId: string, version: number, updatedAt: string): CompositionSummary => ({
    id: `${lineageId}-${version}`, lineageId, version, state: "ASSINADA", context: "CONSULTA", occurredAt: updatedAt, icd10: [], signedAt: updatedAt, updatedAt, supersededBy: null,
  });
  it("junta as versões da mesma nota, a mais nova primeiro", () => {
    const g = groupByLineage([s("a", 1, "2026-01-01"), s("b", 1, "2026-02-01"), s("a", 2, "2026-03-01")]);
    expect(g.map((x) => x.map((y) => y.id))).toEqual([["a-2", "a-1"], ["b-1"]]);
  });
});

describe("sameDraft", () => {
  it("compara o conteúdo do rascunho", () => {
    const d = { subjective: "a", objective: "", assessment: { text: "", icd10: ["J18.9"], snomed: [] }, plan: "" };
    expect(sameDraft(d, { ...d })).toBe(true);
    expect(sameDraft(d, { ...d, plan: "x" })).toBe(false);
  });
});
