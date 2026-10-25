import { createPublicKey, verify, type KeyObject } from "node:crypto";

/**
 * Token interno da core-api (ADR-008, não é JWT): "v1.<base64url(json)>.<base64url(Ed25519)>", 60 s, público
 * "rtc", com a sala (chamada) e o aparelho. Cada token entra uma vez (jti).
 */
export interface RtcClaims {
  sub: string;
  name: string;
  room: string;
  device: string;
  exp: number;
  jti: string;
}

export class TokenVerifier {
  private readonly seen = new Map<string, number>();

  private readonly key: KeyObject;

  constructor(key: KeyObject) {
    this.key = key;
  }

  static fromSpki(spkiBase64: string): TokenVerifier {
    return new TokenVerifier(createPublicKey({ key: Buffer.from(spkiBase64, "base64"), format: "der", type: "spki" }));
  }

  /** Busca a chave pública da core-api (GET /api/v1/internal/public-key). */
  static async fetch(coreUrl: string): Promise<TokenVerifier> {
    const res = await fetch(`${coreUrl}/api/v1/internal/public-key`);
    if (!res.ok) throw new Error(`chave pública da core-api indisponível (${res.status})`);
    const body = (await res.json()) as { alg: string; spki: string };
    if (body.alg !== "Ed25519") throw new Error("algoritmo inesperado");
    return TokenVerifier.fromSpki(body.spki);
  }

  verify(token: unknown, now = Date.now()): RtcClaims | null {
    if (typeof token !== "string" || token.length > 4096) return null;
    const parts = token.split(".");
    if (parts.length !== 3 || parts[0] !== "v1") return null;
    let ok = false;
    try {
      ok = verify(null, Buffer.from(`${parts[0]}.${parts[1]}`, "ascii"), this.key, Buffer.from(parts[2]!, "base64url"));
    } catch {
      return null;
    }
    if (!ok) return null;
    let c: Record<string, unknown>;
    try {
      c = JSON.parse(Buffer.from(parts[1]!, "base64url").toString("utf8")) as Record<string, unknown>;
    } catch {
      return null;
    }
    const uuid = /^[0-9a-f-]{36}$/;
    if (c.iss !== "core-api" || c.aud !== "rtc" || c.seal !== "VERIFIED") return null;
    if (typeof c.exp !== "number" || typeof c.iat !== "number" || c.exp < now || c.iat > now + 5_000) return null;
    for (const k of ["sub", "room", "device", "jti"]) if (typeof c[k] !== "string" || !uuid.test(c[k] as string)) return null;
    this.prune(now);
    if (this.seen.has(c.jti as string)) return null;
    this.seen.set(c.jti as string, c.exp);
    return { sub: c.sub as string, name: String(c.name ?? ""), room: c.room as string, device: c.device as string, exp: c.exp, jti: c.jti as string };
  }

  private prune(now: number) {
    for (const [jti, exp] of this.seen) if (exp < now) this.seen.delete(jti);
  }
}
