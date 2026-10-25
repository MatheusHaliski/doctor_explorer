import {
  createApplicationMessage,
  createCommit,
  createGroup,
  decodeGroupState,
  decodeMlsMessage,
  defaultCapabilities,
  defaultLifetime,
  emptyPskIndex,
  encodeGroupState,
  encodeMlsMessage,
  generateKeyPackageWithKey,
  getCiphersuiteFromName,
  getCiphersuiteImpl,
  joinGroup,
  mlsExporter,
  processPrivateMessage,
  zeroOutUint8Array,
  type CiphersuiteImpl,
  type ClientState,
  type KeyPackage,
  type PrivateKeyPackage,
  type Proposal,
} from "ts-mls";
import { defaultClientConfig } from "ts-mls/clientConfig.js";
import type { ClientConfig } from "ts-mls/clientConfig.js";
import { makeKeyPackageRef } from "ts-mls/keyPackage.js";
import { NotInGroupError, StaleEpochError, type ConversationInfo, type DeliveryApi, type DeviceInfo, type Envelope } from "./api";
import { b64, equal, te, td, unb64 } from "./bytes";
import { recoveryVerifier, newRecoveryPhrase } from "./extras";
import type { KeyStore } from "./store";

/** RFC 9420, suíte 1: X25519 + AES-128-GCM + SHA-256 + Ed25519 (obrigatória em toda implementação MLS). */
export const CIPHERSUITE = "MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519";
export const APPROVAL_CONTEXT = "doctor-explorer/device-approval/v1|";
const KEY_PACKAGE_TARGET = 10;
const HISTORY_LIMIT = 2000;

export type MessageContent =
  | { type: "text"; text: string }
  | { type: "file"; attachmentId: string; name: string; mime: string; size: number; key: string; iv: string; sha256: string };

export interface HistoryItem {
  seq: number;
  senderUserId: string;
  senderDeviceId: string;
  at: string;
  own: boolean;
  content: MessageContent;
}

interface DeviceRecord {
  deviceId: string;
  userId: string;
  name: string;
  sigPub: string;
  sigPriv: string;
}

interface Cursor {
  seq: number;
  joinedEpoch: number;
  removed?: boolean;
}

let suite: Promise<CiphersuiteImpl> | null = null;
export function ciphersuite(): Promise<CiphersuiteImpl> {
  suite ??= getCiphersuiteImpl(getCiphersuiteFromName(CIPHERSUITE));
  return suite;
}

/**
 * Um aparelho no chat (RF06): par de chaves Ed25519 próprio, KeyPackages publicados, grupos MLS por conversa e
 * histórico decifrado guardado só aqui. Operações são serializadas (uma de cada vez) para o estado MLS não divergir.
 */
export class ChatDevice {
  private chain: Promise<unknown> = Promise.resolve();
  private readonly directory = new Map<string, { at: number; info: Promise<DeviceInfo> }>();
  private readonly config: ClientConfig;

  private constructor(
    private readonly store: KeyStore,
    private readonly api: DeliveryApi,
    private readonly impl: CiphersuiteImpl,
    readonly record: DeviceRecord,
  ) {
    this.config = {
      ...defaultClientConfig,
      authService: { validateCredential: (credential, key) => this.validateCredential(credential.credentialType === "basic" ? credential.identity : undefined, key) },
    };
  }

  get deviceId() {
    return this.record.deviceId;
  }

  get userId() {
    return this.record.userId;
  }

  get signatureKey() {
    return this.record.sigPub;
  }

  /** Aparelho já registrado neste navegador para esta conta, se houver. */
  static async load(store: KeyStore, api: DeliveryApi, userId: string): Promise<ChatDevice | null> {
    const raw = await store.get("device");
    if (!raw) return null;
    const rec = JSON.parse(td.decode(raw)) as DeviceRecord;
    if (rec.userId !== userId) return null;
    return new ChatDevice(store, api, await ciphersuite(), rec);
  }

  /**
   * Registra este navegador como aparelho. O primeiro aparelho da conta gera a frase de recuperação (devolvida para
   * mostrar uma vez); os seguintes ficam pendentes até aprovação.
   */
  static async register(store: KeyStore, api: DeliveryApi, userId: string, name: string, firstDevice: boolean, iterations?: number) {
    const impl = await ciphersuite();
    const sig = await impl.signature.keygen();
    const phrase = firstDevice ? newRecoveryPhrase() : null;
    const verifier = phrase ? await recoveryVerifier(phrase, userId, iterations) : undefined;
    const info = await api.registerDevice(name, b64(sig.publicKey), verifier);
    const rec: DeviceRecord = { deviceId: info.id, userId, name, sigPub: b64(sig.publicKey), sigPriv: b64(sig.signKey) };
    await store.put("device", te.encode(JSON.stringify(rec)));
    return { device: new ChatDevice(store, api, impl, rec), info, recoveryPhrase: phrase };
  }

  /** Assinatura que um aparelho confiável dá para aprovar outro (o servidor confere com a chave do aprovador). */
  async approvalSignature(newDeviceId: string, newSignatureKey: string): Promise<string> {
    return b64(await this.impl.signature.sign(unb64(this.record.sigPriv), te.encode(APPROVAL_CONTEXT + newDeviceId + "|" + newSignatureKey)));
  }

  private lock<T>(op: () => Promise<T>): Promise<T> {
    const next = this.chain.then(op, op);
    this.chain = next.catch(() => undefined);
    return next;
  }

  // ---------- Authentication Service ----------

  private lookup(id: string): Promise<DeviceInfo> {
    const hit = this.directory.get(id);
    if (hit && Date.now() - hit.at < 60_000) return hit.info;
    const info = this.api.device(id);
    this.directory.set(id, { at: Date.now(), info });
    info.catch(() => this.directory.delete(id));
    return info;
  }

  /** Credencial = id do aparelho; a chave de assinatura da folha precisa ser a que o diretório tem para ele. */
  private async validateCredential(identity: Uint8Array | undefined, signatureKey: Uint8Array): Promise<boolean> {
    if (!identity) return false;
    const id = td.decode(identity);
    if (!/^[0-9a-f-]{36}$/.test(id)) return false;
    if (id === this.deviceId) return b64(signatureKey) === this.record.sigPub;
    try {
      const d = await this.lookup(id);
      return d.state !== "PENDING" && equal(unb64(d.signatureKey), signatureKey);
    } catch {
      return false;
    }
  }

  // ---------- KeyPackages ----------

  /** Mantém KeyPackages livres no servidor para que outros possam adicionar este aparelho. */
  ensureKeyPackages(free: number): Promise<number> {
    return this.lock(async () => {
      if (free >= KEY_PACKAGE_TARGET / 2) return free;
      const out: string[] = [];
      for (let i = free; i < KEY_PACKAGE_TARGET; i++) {
        const kp = await generateKeyPackageWithKey(
          { credentialType: "basic", identity: te.encode(this.deviceId) },
          defaultCapabilities(),
          defaultLifetime,
          [],
          { signKey: unb64(this.record.sigPriv), publicKey: unb64(this.record.sigPub) },
          this.impl,
        );
        const encoded = encodeMlsMessage({ keyPackage: kp.publicPackage, wireformat: "mls_key_package", version: "mls10" });
        const ref = await makeKeyPackageRef(kp.publicPackage, this.impl.hash);
        await this.store.put(
          "kp:" + b64(ref),
          te.encode(
            JSON.stringify({
              pub: b64(encoded),
              init: b64(kp.privatePackage.initPrivateKey),
              hpke: b64(kp.privatePackage.hpkePrivateKey),
              sig: b64(kp.privatePackage.signaturePrivateKey),
            }),
          ),
        );
        out.push(b64(encoded));
      }
      return this.api.publishKeyPackages(this.deviceId, out);
    });
  }

  private async keyPackageFor(ref: Uint8Array): Promise<{ pub: KeyPackage; priv: PrivateKeyPackage } | null> {
    const raw = await this.store.get("kp:" + b64(ref));
    if (!raw) return null;
    const j = JSON.parse(td.decode(raw)) as { pub: string; init: string; hpke: string; sig: string };
    const msg = decodeMlsMessage(unb64(j.pub), 0)?.[0];
    if (!msg || msg.wireformat !== "mls_key_package") return null;
    return { pub: msg.keyPackage, priv: { initPrivateKey: unb64(j.init), hpkePrivateKey: unb64(j.hpke), signaturePrivateKey: unb64(j.sig) } };
  }

  // ---------- estado local ----------

  private async state(conversationId: string): Promise<ClientState | null> {
    const raw = await this.store.get("group:" + conversationId);
    if (!raw) return null;
    const decoded = decodeGroupState(raw, 0);
    if (!decoded) throw new Error("estado MLS corrompido");
    return { ...decoded[0], clientConfig: this.config };
  }

  private async saveState(conversationId: string, s: ClientState) {
    await this.store.put("group:" + conversationId, encodeGroupState(s));
  }

  private async cursor(conversationId: string): Promise<Cursor | null> {
    const raw = await this.store.get("cursor:" + conversationId);
    return raw ? (JSON.parse(td.decode(raw)) as Cursor) : null;
  }

  private async saveCursor(conversationId: string, c: Cursor) {
    await this.store.put("cursor:" + conversationId, te.encode(JSON.stringify(c)));
  }

  async history(conversationId: string): Promise<HistoryItem[]> {
    const raw = await this.store.get("history:" + conversationId);
    return raw ? (JSON.parse(td.decode(raw)) as HistoryItem[]) : [];
  }

  private async appendHistory(conversationId: string, item: HistoryItem) {
    const h = await this.history(conversationId);
    h.push(item);
    h.sort((a, b) => a.seq - b.seq);
    await this.store.put("history:" + conversationId, te.encode(JSON.stringify(h.slice(-HISTORY_LIMIT))));
  }

  /** Conversas em que este aparelho já está no grupo MLS. */
  async joined(): Promise<string[]> {
    return (await this.store.keys("group:")).map((k) => k.slice("group:".length));
  }

  async isMember(conversationId: string): Promise<boolean> {
    const c = await this.cursor(conversationId);
    return Boolean(c && !c.removed && (await this.store.get("group:" + conversationId)));
  }

  /** Aparelhos (id da credencial) na árvore do grupo, como este aparelho a vê. */
  async treeDevices(conversationId: string): Promise<{ deviceId: string; leafIndex: number; signatureKey: string }[]> {
    const s = await this.state(conversationId);
    return s ? leaves(s) : [];
  }

  // ---------- conversas ----------

  /** Cria a conversa no servidor, o grupo MLS (época 0) aqui e já adiciona os aparelhos dos outros. */
  async createConversation(input: { kind: "DIRECT" | "GROUP"; memberIds: string[]; title?: string; retentionDays?: number }): Promise<ConversationInfo> {
    const info = await this.api.createConversation({ ...input, deviceId: this.deviceId });
    await this.lock(async () => {
      const keys = await this.keyPackageForSelf();
      const s = await createGroup(te.encode(info.id), keys.pub, keys.priv, [], this.impl, this.config);
      await this.saveState(info.id, s);
      await this.saveCursor(info.id, { seq: 0, joinedEpoch: 0 });
    });
    await this.maintain(info.id);
    return this.api.conversation(info.id);
  }

  private async keyPackageForSelf() {
    const kp = await generateKeyPackageWithKey(
      { credentialType: "basic", identity: te.encode(this.deviceId) },
      defaultCapabilities(),
      defaultLifetime,
      [],
      { signKey: unb64(this.record.sigPriv), publicKey: unb64(this.record.sigPub) },
      this.impl,
    );
    return { pub: kp.publicPackage, priv: kp.privatePackage };
  }

  /** Entra nos grupos para os quais recebeu Welcome. */
  acceptWelcomes(): Promise<string[]> {
    return this.lock(async () => {
      const joined: string[] = [];
      for (const w of await this.api.welcomes(this.deviceId)) {
        try {
          if (await this.isMemberUnlocked(w.conversationId)) {
            await this.api.ackWelcome(this.deviceId, w.id);
            continue;
          }
          const msg = decodeMlsMessage(unb64(w.payload), 0)?.[0];
          if (!msg || msg.wireformat !== "mls_welcome") throw new Error("Welcome inválido");
          let kp: { pub: KeyPackage; priv: PrivateKeyPackage; ref: Uint8Array } | null = null;
          for (const secret of msg.welcome.secrets) {
            const found = await this.keyPackageFor(secret.newMember);
            if (found) {
              kp = { ...found, ref: secret.newMember };
              break;
            }
          }
          if (!kp) throw new Error("Welcome para um KeyPackage que este aparelho não tem");
          const s = await joinGroup(msg.welcome, kp.pub, kp.priv, emptyPskIndex, this.impl, undefined, undefined, this.config);
          if (td.decode(s.groupContext.groupId) !== w.conversationId) throw new Error("Welcome de outro grupo");
          await this.saveState(w.conversationId, s);
          await this.saveCursor(w.conversationId, { seq: 0, joinedEpoch: Number(s.groupContext.epoch) });
          await this.store.delete("kp:" + b64(kp.ref));
          joined.push(w.conversationId);
        } catch (e) {
          // Welcome que não abre (KeyPackage perdido, credencial que não confere) é descartado: outro membro pode
          // adicionar este aparelho de novo com um KeyPackage novo.
          console.warn("Welcome descartado", e instanceof Error ? e.message : e);
        }
        await this.api.ackWelcome(this.deviceId, w.id);
      }
      return joined;
    });
  }

  private async isMemberUnlocked(conversationId: string) {
    return Boolean(await this.store.get("group:" + conversationId));
  }

  /** Processa, na ordem do servidor, commits e mensagens ainda não vistos. Devolve quantas mensagens novas chegaram. */
  sync(conversationId: string): Promise<number> {
    return this.lock(() => this.syncUnlocked(conversationId));
  }

  private async syncUnlocked(conversationId: string): Promise<number> {
    let s = await this.state(conversationId);
    const c = await this.cursor(conversationId);
    if (!s || !c || c.removed) return 0;
    let fresh = 0;
    for (;;) {
      const batch = await this.api.messages(conversationId, c.seq);
      if (batch.length === 0) break;
      for (const e of batch) {
        c.seq = e.seq;
        if (e.epoch < c.joinedEpoch) continue;
        const pending = await this.store.get("pending:" + conversationId);
        if (e.senderDeviceId === this.deviceId) {
          if (e.kind === "COMMIT" && pending) {
            const p = JSON.parse(td.decode(pending)) as { epoch: number; state: string };
            if (p.epoch === e.epoch) {
              const decoded = decodeGroupState(unb64(p.state), 0)!;
              s = { ...decoded[0], clientConfig: this.config };
              await this.store.delete("pending:" + conversationId);
            }
          }
          continue;
        }
        const msg = decodeMlsMessage(unb64(e.payload), 0)?.[0];
        if (!msg || msg.wireformat !== "mls_private_message") continue;
        try {
          const r = await processPrivateMessage(s, msg.privateMessage, emptyPskIndex, this.impl);
          s = r.newState;
          if (r.kind === "applicationMessage") {
            r.consumed.forEach(zeroOutUint8Array);
            const content = parseContent(r.message);
            if (content) {
              await this.appendHistory(conversationId, { seq: e.seq, senderUserId: e.senderUserId, senderDeviceId: e.senderDeviceId, at: e.createdAt, own: false, content });
              fresh++;
            }
          } else if (s.groupActiveState.kind === "removedFromGroup") {
            c.removed = true;
          }
        } catch (err) {
          // mensagem que não decifra (época antiga já descartada, ou adulterada) é ignorada; o resto segue
          console.warn("Mensagem MLS ignorada", e.seq, err instanceof Error ? err.message : err);
        }
        if (c.removed) break;
      }
      await this.saveState(conversationId, s);
      await this.saveCursor(conversationId, c);
      if (c.removed || batch.length < 200) break;
    }
    return fresh;
  }

  /**
   * Põe a árvore em dia com o servidor: adiciona aparelhos aprovados dos membros (com KeyPackage) e remove os
   * revogados ou de quem saiu. Quem chegar atrasado recebe 409, sincroniza e tenta de novo.
   */
  maintain(conversationId: string): Promise<boolean> {
    return this.lock(async () => {
      for (let attempt = 0; attempt < 3; attempt++) {
        await this.syncUnlocked(conversationId);
        const s = await this.state(conversationId);
        const c = await this.cursor(conversationId);
        if (!s || !c || c.removed) return false;
        const info = await this.api.conversation(conversationId);
        const tree = new Map(leaves(s).map((l) => [l.deviceId, l.leafIndex]));
        const removals = info.devicesToRemove.filter((d) => tree.has(d) && d !== this.deviceId);
        const adds = info.devicesToAdd.filter((d) => d.hasKeyPackage && !tree.has(d.deviceId)).map((d) => d.deviceId);
        if (removals.length === 0 && adds.length === 0) return false;
        const claimed = adds.length ? await this.api.claimKeyPackages(conversationId, adds) : [];
        const proposals: Proposal[] = [];
        const added: string[] = [];
        for (const k of claimed) {
          const msg = decodeMlsMessage(unb64(k.keyPackage), 0)?.[0];
          if (!msg || msg.wireformat !== "mls_key_package") continue;
          const leaf = msg.keyPackage.leafNode;
          if (leaf.credential.credentialType !== "basic" || td.decode(leaf.credential.identity) !== k.deviceId) continue;
          if (!(await this.validateCredential(leaf.credential.identity, leaf.signaturePublicKey))) continue;
          proposals.push({ proposalType: "add", add: { keyPackage: msg.keyPackage } });
          added.push(k.deviceId);
        }
        for (const d of removals) proposals.push({ proposalType: "remove", remove: { removed: tree.get(d)! } });
        if (proposals.length === 0) return false;
        const epoch = Number(s.groupContext.epoch);
        const result = await createCommit({ state: s, cipherSuite: this.impl }, { extraProposals: proposals, ratchetTreeExtension: true });
        await this.store.put("pending:" + conversationId, te.encode(JSON.stringify({ epoch, state: b64(encodeGroupState(result.newState)) })));
        try {
          await this.api.commit(conversationId, {
            epoch,
            senderDeviceId: this.deviceId,
            commit: b64(encodeMlsMessage(result.commit)),
            welcome: result.welcome ? b64(encodeMlsMessage({ welcome: result.welcome, wireformat: "mls_welcome", version: "mls10" })) : undefined,
            addedDeviceIds: added,
            removedDeviceIds: removals,
          });
        } catch (err) {
          await this.store.delete("pending:" + conversationId);
          if (err instanceof StaleEpochError) continue;
          throw err;
        }
        result.consumed.forEach(zeroOutUint8Array);
        await this.saveState(conversationId, result.newState);
        await this.store.delete("pending:" + conversationId);
        return true;
      }
      return false;
    });
  }

  /** Cifra e envia; se o grupo mudou no meio, sincroniza e cifra de novo na época nova. */
  send(conversationId: string, content: MessageContent): Promise<HistoryItem> {
    return this.lock(async () => {
      for (let attempt = 0; attempt < 4; attempt++) {
        await this.syncUnlocked(conversationId);
        const s = await this.state(conversationId);
        const c = await this.cursor(conversationId);
        if (!s || !c || c.removed) throw new NotInGroupError();
        const r = await createApplicationMessage(s, te.encode(JSON.stringify({ v: 1, ...content })), this.impl);
        try {
          const sent = await this.api.send(conversationId, {
            epoch: Number(s.groupContext.epoch),
            senderDeviceId: this.deviceId,
            payload: b64(encodeMlsMessage({ privateMessage: r.privateMessage, wireformat: "mls_private_message", version: "mls10" })),
          });
          r.consumed.forEach(zeroOutUint8Array);
          await this.saveState(conversationId, r.newState);
          const item: HistoryItem = { seq: sent.seq, senderUserId: this.userId, senderDeviceId: this.deviceId, at: new Date().toISOString(), own: true, content };
          await this.appendHistory(conversationId, item);
          return item;
        } catch (err) {
          if (err instanceof StaleEpochError) continue;
          throw err;
        }
      }
      throw new StaleEpochError();
    });
  }

  /** Segredo exportado do grupo (RFC 9420 §8.5): chave E2EE das chamadas do RF07, igual em todos os membros. */
  async exportSecret(conversationId: string, label: string, context: Uint8Array, length = 32): Promise<{ epoch: number; secret: Uint8Array }> {
    const s = await this.state(conversationId);
    if (!s) throw new NotInGroupError();
    return { epoch: Number(s.groupContext.epoch), secret: await mlsExporter(s.keySchedule.exporterSecret, label, context, length, this.impl) };
  }

  async epoch(conversationId: string): Promise<number | null> {
    const s = await this.state(conversationId);
    return s ? Number(s.groupContext.epoch) : null;
  }

  /** Esquece este aparelho neste navegador (depois de revogado no servidor). */
  async forget(): Promise<void> {
    for (const prefix of ["group:", "cursor:", "history:", "kp:", "pending:"]) for (const k of await this.store.keys(prefix)) await this.store.delete(k);
    await this.store.delete("device");
  }
}

function leaves(s: ClientState): { deviceId: string; leafIndex: number; signatureKey: string }[] {
  const out: { deviceId: string; leafIndex: number; signatureKey: string }[] = [];
  s.ratchetTree.forEach((n, i) => {
    if (i % 2 !== 0 || !n || n.nodeType !== "leaf") return;
    const cred = n.leaf.credential;
    if (cred.credentialType !== "basic") return;
    out.push({ deviceId: td.decode(cred.identity), leafIndex: i / 2, signatureKey: b64(n.leaf.signaturePublicKey) });
  });
  return out;
}

function parseContent(bytes: Uint8Array): MessageContent | null {
  try {
    const j = JSON.parse(td.decode(bytes)) as { v?: number } & Record<string, unknown>;
    if (j.v !== 1) return null;
    if (j.type === "text" && typeof j.text === "string") return { type: "text", text: j.text.slice(0, 10_000) };
    if (j.type === "file" && typeof j.attachmentId === "string" && typeof j.key === "string" && typeof j.iv === "string" && typeof j.sha256 === "string") {
      return {
        type: "file",
        attachmentId: j.attachmentId,
        name: String(j.name ?? "anexo").slice(0, 200),
        mime: String(j.mime ?? "application/octet-stream").slice(0, 100),
        size: Number(j.size ?? 0),
        key: j.key,
        iv: j.iv,
        sha256: j.sha256,
      };
    }
    return null;
  } catch {
    return null;
  }
}
