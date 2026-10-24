import { describe, expect, it } from "vitest";
import { EMPTY_FEED, feedSearch, parseFeedParams, toggleSpecialty } from "@/lib/feed-params";

describe("eixos do feed na URL", () => {
  it("vai e volta sem perder nada", () => {
    const p = parseFeedParams(new URLSearchParams("kind=case&specialty=uti,derm&origin=Grupo&state=em_discussao&unanswered=true"));
    expect(p).toEqual({ kind: "CASE", specialty: ["UTI", "DERM"], origin: "grupo", state: "EM_DISCUSSAO", unanswered: true });
    expect(feedSearch(p)).toBe("?kind=CASE&specialty=UTI%2CDERM&origin=grupo&state=EM_DISCUSSAO&unanswered=true");
    expect(feedSearch(EMPTY_FEED)).toBe("");
  });

  it("alterna especialidades como conjunto", () => {
    const a = toggleSpecialty(EMPTY_FEED, "UTI");
    expect(a.specialty).toEqual(["UTI"]);
    expect(toggleSpecialty(a, "UTI").specialty).toEqual([]);
    expect(toggleSpecialty(a, "DERM").specialty).toEqual(["UTI", "DERM"]);
  });

  it("aceita searchParams de Server Component", () => {
    expect(parseFeedParams({ kind: ["education"], specialty: undefined }).kind).toBe("EDUCATION");
  });
});
