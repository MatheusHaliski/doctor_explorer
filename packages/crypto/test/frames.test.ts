import { describe, expect, it } from "vitest";
import { clearPrefix, FrameKeyRing } from "../src/frames";
import { random } from "../src/bytes";

function frame(n = 300) {
  const f = random(n);
  f.set([0x90, 0x80, 0x01, 0x9d, 0x01, 0x2a, 0x40, 0x01, 0xf0, 0x00]); // cabeçalho VP8 de quadro-chave (fica em claro)
  return f;
}

describe("cifra de quadros das chamadas (RF07)", () => {
  it("ida e volta com a mesma chave; o cabeçalho do codec fica em claro e o resto não", async () => {
    const key = random(32);
    const a = new FrameKeyRing();
    const b = new FrameKeyRing();
    await a.setKey(3, key);
    await b.setKey(3, key);
    const f = frame();
    const enc = new Uint8Array(await a.encrypt(f.slice().buffer, clearPrefix("video", true)));
    expect(enc.subarray(0, 10)).toEqual(f.subarray(0, 10));
    expect(Buffer.from(enc).includes(Buffer.from(f.subarray(10, 40)))).toBe(false);
    expect(new Uint8Array((await b.decrypt(enc.buffer as ArrayBuffer))!)).toEqual(f);
  });

  it("sem a chave certa, adulterado ou sem cifra: o quadro é descartado", async () => {
    const a = new FrameKeyRing();
    await a.setKey(1, random(32));
    const intruder = new FrameKeyRing();
    await intruder.setKey(1, random(32));
    const enc = new Uint8Array(await a.encrypt(frame().slice().buffer as ArrayBuffer, clearPrefix("video", false)));
    expect(await intruder.decrypt(enc.buffer as ArrayBuffer)).toBeNull();
    const tampered = enc.slice();
    tampered[1] = tampered[1]! ^ 1; // até o prefixo em claro é autenticado
    expect(await a.decrypt(tampered.buffer as ArrayBuffer)).toBeNull();
    expect(await a.decrypt(frame().slice().buffer as ArrayBuffer)).toBeNull();
  });

  it("troca de época: quem ainda não sincronizou não abre; as épocas recentes continuam abrindo", async () => {
    const k1 = random(32);
    const k2 = random(32);
    const sender = new FrameKeyRing();
    const receiver = new FrameKeyRing();
    await sender.setKey(1, k1);
    await receiver.setKey(1, k1);
    const old = await sender.encrypt(random(80).slice().buffer as ArrayBuffer, clearPrefix("audio", false));
    await sender.setKey(2, k2);
    const fresh = await sender.encrypt(random(80).slice().buffer as ArrayBuffer, clearPrefix("audio", false));
    expect(await receiver.decrypt(fresh)).toBeNull();
    await receiver.setKey(2, k2);
    expect(await receiver.decrypt(fresh)).not.toBeNull();
    expect(await receiver.decrypt(old)).not.toBeNull();
    for (let e = 3; e <= 6; e++) await receiver.setKey(e, random(32));
    expect(receiver.has(1)).toBe(false);
    expect(await receiver.decrypt(old)).toBeNull();
  });
});
