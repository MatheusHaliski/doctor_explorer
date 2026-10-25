/**
 * RF07 · cifra quadro a quadro das chamadas (no espírito do SFrame, RFC 9605), aplicada por insertable streams
 * antes do SRTP. A chave vem do grupo MLS da conversa (RFC 9420 §8.5, exportador com o id da chamada) e muda a
 * cada época; o anel guarda as últimas para quem ainda não sincronizou.
 *
 * Quadro cifrado: [prefixo em claro][AES-256-GCM(resto)][IV 12][época 4][tamanho do prefixo 1][0xDE]
 * O prefixo em claro é só o cabeçalho do codec (VP8: 10 bytes no quadro-chave, 3 nos demais; Opus: 1), para o SFU
 * achar quadros-chave sem ver imagem nem som. O prefixo e o trailer entram como dado autenticado.
 */
import { buf } from "./bytes";

export const CALL_EXPORTER_LABEL = "doctor-explorer/call/v1";
const MAGIC = 0xde;
const TRAILER = 12 + 4 + 1 + 1;
const KEEP_EPOCHS = 4;

export function clearPrefix(kind: "audio" | "video", keyFrame: boolean): number {
  if (kind === "audio") return 1;
  return keyFrame ? 10 : 3;
}

export class FrameKeyRing {
  private readonly keys = new Map<number, CryptoKey>();
  current: number | null = null;

  async setKey(epoch: number, raw: Uint8Array): Promise<void> {
    if (raw.length !== 32) throw new Error("chave de quadro precisa de 32 bytes");
    const key = await crypto.subtle.importKey("raw", buf(raw), "AES-GCM", false, ["encrypt", "decrypt"]);
    this.keys.set(epoch, key);
    if (this.current === null || epoch >= this.current) this.current = epoch;
    for (const e of [...this.keys.keys()].sort((a, b) => a - b).slice(0, -KEEP_EPOCHS)) this.keys.delete(e);
  }

  has(epoch: number) {
    return this.keys.has(epoch);
  }

  async encrypt(data: ArrayBuffer, prefix: number): Promise<ArrayBuffer> {
    if (this.current === null) throw new Error("sem chave");
    const key = this.keys.get(this.current)!;
    const bytes = new Uint8Array(data);
    const n = Math.min(prefix, bytes.length);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const meta = new Uint8Array(6);
    new DataView(meta.buffer).setUint32(0, this.current >>> 0);
    meta[4] = n;
    meta[5] = MAGIC;
    const aad = new Uint8Array(n + 12 + 6);
    aad.set(bytes.subarray(0, n));
    aad.set(iv, n);
    aad.set(meta, n + 12);
    const sealed = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv: buf(iv), additionalData: buf(aad) }, key, buf(bytes.subarray(n))));
    const out = new Uint8Array(n + sealed.length + TRAILER);
    out.set(bytes.subarray(0, n));
    out.set(sealed, n);
    out.set(iv, n + sealed.length);
    out.set(meta, n + sealed.length + 12);
    return out.buffer;
  }

  /** null = não decifra (sem a chave da época, quadro sem cifra ou adulterado): o quadro é descartado. */
  async decrypt(data: ArrayBuffer): Promise<ArrayBuffer | null> {
    const bytes = new Uint8Array(data);
    if (bytes.length < TRAILER + 16 || bytes[bytes.length - 1] !== MAGIC) return null;
    const n = bytes[bytes.length - 2]!;
    const epoch = new DataView(bytes.buffer, bytes.byteOffset + bytes.length - 6, 4).getUint32(0);
    const key = this.keys.get(epoch);
    if (!key || n + TRAILER + 16 > bytes.length) return null;
    const iv = bytes.subarray(bytes.length - TRAILER, bytes.length - 6);
    const aad = new Uint8Array(n + 12 + 6);
    aad.set(bytes.subarray(0, n));
    aad.set(iv, n);
    aad.set(bytes.subarray(bytes.length - 6), n + 12);
    try {
      const plain = new Uint8Array(
        await crypto.subtle.decrypt({ name: "AES-GCM", iv: buf(iv), additionalData: buf(aad) }, key, buf(bytes.subarray(n, bytes.length - TRAILER))),
      );
      const out = new Uint8Array(n + plain.length);
      out.set(bytes.subarray(0, n));
      out.set(plain, n);
      return out.buffer;
    } catch {
      return null;
    }
  }
}
