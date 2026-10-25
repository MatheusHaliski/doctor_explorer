import { b64, buf, concat, random, sha256, te, unb64 } from "./bytes";

const CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

/** Frase de recuperação: 160 bits em 8 grupos de 4 caracteres Crockford (sem I, L, O, U). Mostrada uma vez. */
export function newRecoveryPhrase(): string {
  const bytes = random(20);
  let bits = "";
  for (const b of bytes) bits += b.toString(2).padStart(8, "0");
  let out = "";
  for (let i = 0; i < 160; i += 5) out += CROCKFORD[parseInt(bits.slice(i, i + 5), 2)];
  return out.match(/.{4}/g)!.join("-");
}

export function normalizePhrase(raw: string): string | null {
  const c = raw
    .toUpperCase()
    .replace(/[\s-]/g, "")
    .replace(/O/g, "0")
    .replace(/[IL]/g, "1");
  return /^[0-9A-HJKMNP-TV-Z]{32}$/.test(c) ? c : null;
}

export const RECOVERY_ITERATIONS = 600_000;

/** O servidor recebe só este verificador (PBKDF2-SHA256, sal amarrado à conta) e guarda o Argon2id dele. */
export async function recoveryVerifier(phrase: string, userId: string, iterations = RECOVERY_ITERATIONS): Promise<string> {
  const p = normalizePhrase(phrase);
  if (!p) throw new Error("frase de recuperação inválida");
  const key = await crypto.subtle.importKey("raw", buf(te.encode(p)), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt: buf(te.encode("doctor-explorer/recovery/v1/" + userId)), iterations },
    key,
    256,
  );
  return b64(new Uint8Array(bits));
}

/**
 * Código de segurança da conversa 1:1 (critério 4): 60 dígitos derivados das chaves públicas de todos os aparelhos
 * aprovados dos dois lados. Os dois veem o mesmo número; se o servidor trocar ou injetar um aparelho, muda.
 */
export async function safetyCode(users: { userId: string; signatureKeys: string[] }[]): Promise<string> {
  const parts = [...users]
    .sort((a, b) => (a.userId < b.userId ? -1 : 1))
    .map((u) => concat(te.encode(u.userId + "\n"), ...[...u.signatureKeys].sort().map((k) => concat(unb64(k), te.encode("\n")))));
  // SHA-512: 64 bytes, 12 blocos de 5 bytes distintos (60 bytes) viram 12 grupos de 5 dígitos
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-512", buf(concat(te.encode("doctor-explorer/safety-code/v1\n"), ...parts))));
  const groups: string[] = [];
  for (let i = 0; i < 12; i++) {
    const chunk = digest.subarray(i * 5, i * 5 + 5);
    let n = 0;
    for (const b of chunk) n = (n * 256 + b) % 100000;
    groups.push(String(n).padStart(5, "0"));
  }
  return groups.join(" ");
}

export interface SealedFile {
  cipher: Uint8Array;
  key: string;
  iv: string;
  sha256: string;
}

/** Anexo (critério 5): cifrado no aparelho com chave própria; a chave viaja dentro da mensagem MLS. */
export async function sealFile(plain: Uint8Array): Promise<SealedFile> {
  const raw = random(32);
  const iv = random(12);
  const key = await crypto.subtle.importKey("raw", buf(raw), "AES-GCM", false, ["encrypt"]);
  const cipher = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv: buf(iv) }, key, buf(plain)));
  return { cipher, key: b64(raw), iv: b64(iv), sha256: b64(await sha256(cipher)) };
}

export async function openFile(cipher: Uint8Array, f: { key: string; iv: string; sha256: string }): Promise<Uint8Array> {
  if (b64(await sha256(cipher)) !== f.sha256) throw new Error("anexo alterado");
  const key = await crypto.subtle.importKey("raw", buf(unb64(f.key)), "AES-GCM", false, ["decrypt"]);
  return new Uint8Array(await crypto.subtle.decrypt({ name: "AES-GCM", iv: buf(unb64(f.iv)) }, key, buf(cipher)));
}
