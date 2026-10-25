import { describe, expect, it } from "vitest";
import { ChatDevice, MemoryStore, newRecoveryPhrase, normalizePhrase, openFile, recoveryVerifier, safetyCode, sealFile } from "../src";
import { te } from "../src/bytes";
import { FakeServer } from "./fake-server";

const ANA = "aaaaaaaa-0000-4000-8000-000000000001";
const BRUNO = "bbbbbbbb-0000-4000-8000-000000000002";
const CARLA = "cccccccc-0000-4000-8000-000000000003";
const FAST = 1000; // iterações do PBKDF2 nos testes; o app usa 600 000

async function setup(server: FakeServer, userId: string, name = "Notebook") {
  const store = new MemoryStore();
  const api = server.api(userId);
  const first = !(await api.myDevices()).some((d) => d.state === "APPROVED");
  const r = await ChatDevice.register(store, api, userId, name, first, FAST);
  if (r.info.state === "APPROVED") await r.device.ensureKeyPackages(0);
  return { ...r, store, api };
}

function texts(h: { content: { type: string; text?: string } }[]) {
  return h.map((i) => (i.content.type === "text" ? i.content.text : `[${i.content.type}]`));
}

describe("chat MLS (RF06)", () => {
  it("1:1: cria o grupo, entrega o Welcome e as mensagens chegam decifradas só nos aparelhos", async () => {
    const server = new FakeServer();
    const ana = await setup(server, ANA);
    const bruno = await setup(server, BRUNO, "Celular");
    expect(ana.recoveryPhrase).toMatch(/^([0-9A-Z]{4}-){7}[0-9A-Z]{4}$/);

    const conv = await ana.device.createConversation({ kind: "DIRECT", memberIds: [BRUNO] });
    expect(conv.epoch).toBe(1);
    expect(conv.devices.sort()).toEqual([ana.device.deviceId, bruno.device.deviceId].sort());
    expect(await bruno.device.acceptWelcomes()).toEqual([conv.id]);

    await ana.device.send(conv.id, { type: "text", text: "Olá, Bruno. Paciente da UTI 3 com febre." });
    expect(await bruno.device.sync(conv.id)).toBe(1);
    await bruno.device.send(conv.id, { type: "text", text: "Vejo em 10 min." });
    await ana.device.sync(conv.id);
    expect(texts(await ana.device.history(conv.id))).toEqual(["Olá, Bruno. Paciente da UTI 3 com febre.", "Vejo em 10 min."]);
    expect(texts(await bruno.device.history(conv.id))).toEqual(["Olá, Bruno. Paciente da UTI 3 com febre.", "Vejo em 10 min."]);

    // o servidor só tem bytes cifrados
    const needle = te.encode("UTI 3");
    for (const bytes of server.storedBytes()) expect(Buffer.from(bytes).includes(Buffer.from(needle))).toBe(false);

    // código de segurança igual dos dois lados; chave de chamada (RF07) igual em todos os membros
    const keysOf = async (uid: string) => (await ana.api.myDevices()).length && [...server.devices.values()].filter((d) => d.userId === uid).map((d) => d.signatureKey);
    const code = await safetyCode([
      { userId: ANA, signatureKeys: (await keysOf(ANA)) || [] },
      { userId: BRUNO, signatureKeys: (await keysOf(BRUNO)) || [] },
    ]);
    const reversed = await safetyCode([
      { userId: BRUNO, signatureKeys: (await keysOf(BRUNO)) || [] },
      { userId: ANA, signatureKeys: (await keysOf(ANA)) || [] },
    ]);
    expect(code).toMatch(/^(\d{5} ){11}\d{5}$/);
    const groups = code.split(" ");
    expect(groups.slice(6)).not.toEqual(groups.slice(0, 6));
    expect(code).toBe(reversed);
    const a = await ana.device.exportSecret(conv.id, "doctor-explorer/call", te.encode("call-1"));
    const b = await bruno.device.exportSecret(conv.id, "doctor-explorer/call", te.encode("call-1"));
    expect(Buffer.from(a.secret).equals(Buffer.from(b.secret))).toBe(true);
    expect(a.epoch).toBe(b.epoch);
  });

  it("estado sobrevive a recarregar a página (o aparelho e os grupos ficam no armazenamento local)", async () => {
    const server = new FakeServer();
    const ana = await setup(server, ANA);
    const bruno = await setup(server, BRUNO);
    const conv = await ana.device.createConversation({ kind: "DIRECT", memberIds: [BRUNO] });
    await bruno.device.acceptWelcomes();
    const reloaded = (await ChatDevice.load(bruno.store, bruno.api, BRUNO))!;
    expect(reloaded.deviceId).toBe(bruno.device.deviceId);
    expect(await ChatDevice.load(bruno.store, bruno.api, ANA)).toBeNull();
    await ana.device.send(conv.id, { type: "text", text: "depois do recarregamento" });
    await reloaded.sync(conv.id);
    expect(texts(await reloaded.history(conv.id))).toEqual(["depois do recarregamento"]);
  });

  it("novo aparelho só entra aprovado por outro confiável, recebe o que vier depois e sai quando revogado", async () => {
    const server = new FakeServer();
    const ana = await setup(server, ANA);
    const bruno = await setup(server, BRUNO);
    const conv = await ana.device.createConversation({ kind: "DIRECT", memberIds: [BRUNO] });
    await bruno.device.acceptWelcomes();
    await ana.device.send(conv.id, { type: "text", text: "antes do celular" });

    const ana2 = await setup(server, ANA, "Celular da Ana");
    expect(ana2.info.state).toBe("PENDING");
    expect(await server.approveWithSignature(ana2.device.deviceId, ana2.device.deviceId, await ana2.device.approvalSignature(ana2.device.deviceId, ana2.device.signatureKey))).toBe(false);
    expect(await server.approveWithSignature(ana2.device.deviceId, ana.device.deviceId, await ana.device.approvalSignature(ana2.device.deviceId, ana2.device.signatureKey))).toBe(true);
    await ana2.device.ensureKeyPackages(0);

    // qualquer membro põe a árvore em dia
    expect(await bruno.device.maintain(conv.id)).toBe(true);
    expect(await ana2.device.acceptWelcomes()).toEqual([conv.id]);
    await ana.device.sync(conv.id);
    await bruno.device.send(conv.id, { type: "text", text: "para os dois aparelhos" });
    await ana.device.sync(conv.id);
    await ana2.device.sync(conv.id);
    expect(texts(await ana2.device.history(conv.id))).toEqual(["para os dois aparelhos"]);
    expect(texts(await ana.device.history(conv.id))).toEqual(["antes do celular", "para os dois aparelhos"]);

    // revogado: alguém remove da árvore e ele deixa de receber
    server.devices.get(ana2.device.deviceId)!.state = "REVOKED";
    expect(await ana.device.maintain(conv.id)).toBe(true);
    await ana2.device.sync(conv.id);
    expect(await ana2.device.isMember(conv.id)).toBe(false);
    await bruno.device.sync(conv.id);
    await bruno.device.send(conv.id, { type: "text", text: "sem o celular" });
    await ana.device.sync(conv.id);
    expect(texts(await ana.device.history(conv.id)).at(-1)).toBe("sem o celular");
    expect(texts(await ana2.device.history(conv.id))).toEqual(["para os dois aparelhos"]);
  });

  it("grupo: commits concorrentes na mesma época, um recebe 409, sincroniza e refaz", async () => {
    const server = new FakeServer();
    const ana = await setup(server, ANA);
    const bruno = await setup(server, BRUNO);
    const conv = await ana.device.createConversation({ kind: "GROUP", memberIds: [BRUNO], title: "Plantão" });
    await bruno.device.acceptWelcomes();
    const carla = await setup(server, CARLA);
    server.conversations.get(conv.id)!.members.add(CARLA);
    // Ana e Bruno tentam adicionar a Carla ao mesmo tempo
    const [x, y] = await Promise.all([ana.device.maintain(conv.id), bruno.device.maintain(conv.id)]);
    expect([x, y].filter(Boolean)).toHaveLength(1);
    expect(await carla.device.acceptWelcomes()).toEqual([conv.id]);
    await carla.device.send(conv.id, { type: "text", text: "cheguei" });
    await ana.device.sync(conv.id);
    await bruno.device.sync(conv.id);
    expect(texts(await ana.device.history(conv.id))).toEqual(["cheguei"]);
    expect(texts(await bruno.device.history(conv.id))).toEqual(["cheguei"]);
    // envio numa época velha também se recupera sozinho
    server.conversations.get(conv.id)!.members.delete(CARLA);
    await ana.device.maintain(conv.id);
    await bruno.device.send(conv.id, { type: "text", text: "Carla saiu" });
    await ana.device.sync(conv.id);
    expect(texts(await ana.device.history(conv.id)).at(-1)).toBe("Carla saiu");
  });

  it("Authentication Service: KeyPackage com a chave trocada pelo servidor não entra no grupo", async () => {
    const server = new FakeServer();
    const ana = await setup(server, ANA);
    const bruno = await setup(server, BRUNO);
    // servidor malicioso troca a chave do Bruno no diretório depois de publicado o KeyPackage
    const real = server.devices.get(bruno.device.deviceId)!.signatureKey;
    server.devices.get(bruno.device.deviceId)!.signatureKey = server.devices.get(ana.device.deviceId)!.signatureKey;
    const conv = await ana.device.createConversation({ kind: "DIRECT", memberIds: [BRUNO] });
    expect(conv.devices).toEqual([ana.device.deviceId]);
    server.devices.get(bruno.device.deviceId)!.signatureKey = real;
  });
});

describe("frase de recuperação e anexos", () => {
  it("frase normaliza e o verificador é determinístico por conta", async () => {
    const p = newRecoveryPhrase();
    expect(normalizePhrase(p.toLowerCase().replace(/-/g, " "))).toBe(p.replace(/-/g, ""));
    expect(normalizePhrase("curta")).toBeNull();
    const v1 = await recoveryVerifier(p, ANA, FAST);
    expect(await recoveryVerifier(p.toLowerCase(), ANA, FAST)).toBe(v1);
    expect(await recoveryVerifier(p, BRUNO, FAST)).not.toBe(v1);
  });

  it("anexo cifrado com chave própria; adulteração é detectada", async () => {
    const plain = te.encode("laudo.pdf conteúdo");
    const f = await sealFile(plain);
    expect(Buffer.from(f.cipher).includes(Buffer.from("laudo"))).toBe(false);
    expect(new TextDecoder().decode(await openFile(f.cipher, f))).toBe("laudo.pdf conteúdo");
    const bad = f.cipher.slice();
    bad[0] = bad[0]! ^ 1;
    await expect(openFile(bad, f)).rejects.toThrow();
  });
});
