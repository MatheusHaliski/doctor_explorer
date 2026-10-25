import { describe, expect, it } from "vitest";
import { activeHref, NAV, visibleNav } from "@/lib/nav";

const at = (url: string, roles = ["ADMIN"]) => {
  const u = new URL(url, "http://x");
  return activeHref(visibleNav(roles), u.pathname, u.searchParams);
};

describe("navegação lateral em grupos", () => {
  it("cada subpágina tem endereço próprio e único", () => {
    const hrefs = NAV.flatMap((g) => g.items.map((i) => i.href));
    expect(new Set(hrefs).size).toBe(hrefs.length);
  });

  it("acende o item mais específico da página atual", () => {
    expect(at("/feed")).toBe("/feed");
    expect(at("/feed?kind=CASE&unanswered=true")).toBe("/feed?kind=CASE");
    expect(at("/feed?kind=XYZ")).toBe("/feed");
    expect(at("/casos/novo")).toBe("/casos/novo");
    expect(at("/prontuario/pacientes/1")).toBe("/prontuario");
    expect(at("/prontuario/nova?post=1")).toBe("/prontuario/nova");
    expect(at("/prontuario/documentos/1")).toBe("/prescricoes");
    expect(at("/mensagens/aparelhos")).toBe("/mensagens/aparelhos");
    expect(at("/chamadas/1")).toBe("/mensagens");
    expect(at("/admin")).toBe("/admin");
    expect(at("/admin?aba=auditoria")).toBe("/admin?aba=auditoria");
    expect(at("/perfil")).toBe("/perfil");
    expect(at("/perfil/outra-pessoa")).toBeNull();
    expect(at("/feedback")).toBeNull();
  });

  it("moderação e painel só para quem tem o papel", () => {
    const ids = (roles: string[]) => visibleNav(roles).map((g) => g.id);
    expect(ids([])).not.toContain("moderacao");
    expect(ids([])).not.toContain("painel");
    expect(ids(["DPO"])).toContain("painel");
    expect(ids(["DPO"])).not.toContain("moderacao");
    expect(ids(["MODERATOR"])).toEqual(expect.arrayContaining(["moderacao", "painel"]));
  });
});
