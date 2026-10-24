import { describe, expect, it } from "vitest";
import { ApiError, describeError, readCookie, toProblem, withQuery } from "@/lib/api";

describe("cliente da API", () => {
  it("lê o XSRF-TOKEN do cookie", () => {
    expect(readCookie("XSRF-TOKEN", "a=1; XSRF-TOKEN=abc%3D; b=2")).toBe("abc=");
    expect(readCookie("XSRF-TOKEN", "a=1")).toBeNull();
  });

  it("monta query ignorando vazios e falsos", () => {
    expect(withQuery("/api/v1/feed", { kind: "", specialty: "UTI,DERM", unanswered: false, state: undefined })).toBe("/api/v1/feed?specialty=UTI%2CDERM");
    expect(withQuery("/api/v1/feed", { unanswered: true })).toBe("/api/v1/feed?unanswered=true");
  });

  it("converte Problem Details 422 com errors[] e 429 com Retry-After", async () => {
    const res422 = new Response(JSON.stringify({ title: "Dados inválidos", detail: "Há 2 campos para corrigir.", errors: [{ field: "email", message: "Informe um e-mail válido." }, { field: "password", message: "Curta demais." }] }), { status: 422 });
    const p = await toProblem(res422);
    const e = new ApiError(p);
    expect(e.status).toBe(422);
    expect(e.field("email")).toEqual(["Informe um e-mail válido."]);
    expect(e.field("nada")).toEqual([]);

    const res429 = new Response(JSON.stringify({ title: "Muitas tentativas", detail: "Aguarde." }), { status: 429, headers: { "Retry-After": "90" } });
    const p429 = await toProblem(res429);
    expect(p429.retryAfter).toBe(90);
    expect(describeError(new ApiError(p429))).toContain("90 s");
  });

  it("tolera corpo vazio ou não JSON com texto neutro em pt-BR", async () => {
    const p = await toProblem(new Response("<html>", { status: 401 }));
    expect(p.title).toBe("Sessão necessária");
    expect(describeError(new ApiError(p))).toBe("Entre de novo para continuar.");
  });
});
