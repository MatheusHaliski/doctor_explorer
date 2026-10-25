import { ciphersuite } from "../src/client";
import { APPROVAL_CONTEXT } from "../src/client";
import { StaleEpochError, NotInGroupError, type ConversationInfo, type DeliveryApi, type DeviceInfo, type Envelope, type WelcomeItem } from "../src/api";
import { te, unb64 } from "../src/bytes";

/**
 * Delivery Service em memória com as mesmas regras do MessagingService da core-api: aparelho pendente até aprovação,
 * KeyPackage de uso único, commit só na época atual, Welcome por aparelho, quem pode entrar e quem precisa sair.
 */
export class FakeServer {
  devices = new Map<string, DeviceInfo>();
  keyPackages = new Map<string, string[]>();
  welcomes = new Map<string, WelcomeItem[]>();
  conversations = new Map<string, { info: Omit<ConversationInfo, "devices" | "devicesToAdd" | "devicesToRemove">; inTree: Set<string>; log: Envelope[]; members: Set<string> }>();
  verified = new Set<string>();
  private n = 0;

  id(prefix: string) {
    this.n++;
    return `${prefix.padEnd(8, "0").slice(0, 8)}-0000-4000-8000-${String(this.n).padStart(12, "0")}`;
  }

  /** Tudo que o servidor guardou, para conferir que não há texto claro. */
  storedBytes(): Uint8Array[] {
    const out: Uint8Array[] = [];
    for (const c of this.conversations.values()) for (const e of c.log) out.push(unb64(e.payload));
    for (const w of this.welcomes.values()) for (const i of w) out.push(unb64(i.payload));
    for (const k of this.keyPackages.values()) for (const p of k) out.push(unb64(p));
    return out;
  }

  view(id: string): ConversationInfo {
    const c = this.conversations.get(id)!;
    const devicesToAdd = [...this.devices.values()]
      .filter((d) => d.state === "APPROVED" && c.members.has(d.userId) && this.verified.has(d.userId) && !c.inTree.has(d.id))
      .map((d) => ({ deviceId: d.id, userId: d.userId, hasKeyPackage: (this.keyPackages.get(d.id) ?? []).length > 0 }));
    const devicesToRemove = [...c.inTree].filter((d) => {
      const dev = this.devices.get(d)!;
      return dev.state !== "APPROVED" || !c.members.has(dev.userId);
    });
    return { ...c.info, members: c.info.members.filter((m) => c.members.has(m.userId)), devices: [...c.inTree], devicesToAdd, devicesToRemove };
  }

  approve(deviceId: string) {
    this.devices.get(deviceId)!.state = "APPROVED";
  }

  api(userId: string, name = "Médico"): DeliveryApi {
    this.verified.add(userId);
    const own = (deviceId: string) => {
      const d = this.devices.get(deviceId);
      if (!d || d.userId !== userId) throw new Error("404 aparelho");
      return d;
    };
    const member = (cid: string) => {
      const c = this.conversations.get(cid);
      if (!c || !c.members.has(userId)) throw new Error("404 conversa");
      return c;
    };
    return {
      registerDevice: async (devName, signatureKey) => {
        const first = ![...this.devices.values()].some((d) => d.userId === userId && d.state === "APPROVED");
        const d: DeviceInfo = { id: this.id("d"), userId, userName: name, name: devName, signatureKey, state: first ? "APPROVED" : "PENDING", freeKeyPackages: 0 };
        this.devices.set(d.id, d);
        return { ...d };
      },
      device: async (id) => {
        const d = this.devices.get(id);
        if (!d) throw new Error("404");
        return { ...d, freeKeyPackages: (this.keyPackages.get(id) ?? []).length };
      },
      myDevices: async () => [...this.devices.values()].filter((d) => d.userId === userId).map((d) => ({ ...d, freeKeyPackages: (this.keyPackages.get(d.id) ?? []).length })),
      publishKeyPackages: async (deviceId, kps) => {
        if (own(deviceId).state !== "APPROVED") throw new Error("403");
        const list = this.keyPackages.get(deviceId) ?? [];
        list.push(...kps);
        this.keyPackages.set(deviceId, list);
        return list.length;
      },
      welcomes: async (deviceId) => [...(this.welcomes.get(own(deviceId).id) ?? [])],
      ackWelcome: async (deviceId, wid) => {
        this.welcomes.set(deviceId, (this.welcomes.get(own(deviceId).id) ?? []).filter((w) => w.id !== wid));
      },
      createConversation: async (input) => {
        if (own(input.deviceId).state !== "APPROVED") throw new Error("403");
        const id = this.id("c");
        const members = new Set([userId, ...input.memberIds]);
        this.conversations.set(id, {
          info: {
            id,
            kind: input.kind,
            title: input.title ?? null,
            epoch: 0,
            retentionDays: input.retentionDays ?? 90,
            members: [...members].map((m) => ({ userId: m, name: m, role: m === userId ? "OWNER" : "MEMBER", verified: true })),
          },
          inTree: new Set([input.deviceId]),
          log: [],
          members,
        });
        return this.view(id);
      },
      conversation: async (id) => {
        member(id);
        return this.view(id);
      },
      claimKeyPackages: async (cid, deviceIds) => {
        member(cid);
        const wanted = new Set(this.view(cid).devicesToAdd.map((d) => d.deviceId));
        const out: { deviceId: string; userId: string; keyPackage: string }[] = [];
        for (const d of deviceIds) {
          if (!wanted.has(d)) continue;
          const kp = (this.keyPackages.get(d) ?? []).shift();
          if (kp) out.push({ deviceId: d, userId: this.devices.get(d)!.userId, keyPackage: kp });
        }
        return out;
      },
      commit: async (cid, body) => {
        const c = member(cid);
        if (!c.inTree.has(body.senderDeviceId) || own(body.senderDeviceId).state !== "APPROVED") throw new NotInGroupError();
        if (body.epoch !== c.info.epoch) throw new StaleEpochError();
        const seq = c.log.length + 1;
        c.log.push({ seq, kind: "COMMIT", epoch: c.info.epoch, senderDeviceId: body.senderDeviceId, senderUserId: userId, payload: body.commit, createdAt: new Date().toISOString() });
        c.info.epoch++;
        for (const d of body.addedDeviceIds ?? []) {
          c.inTree.add(d);
          const list = this.welcomes.get(d) ?? [];
          list.push({ id: this.id("w"), conversationId: cid, epoch: c.info.epoch, payload: body.welcome! });
          this.welcomes.set(d, list);
        }
        for (const d of body.removedDeviceIds ?? []) c.inTree.delete(d);
        return { seq, epoch: c.info.epoch };
      },
      send: async (cid, body) => {
        const c = member(cid);
        if (!c.inTree.has(body.senderDeviceId) || own(body.senderDeviceId).state !== "APPROVED") throw new NotInGroupError();
        if (body.epoch !== c.info.epoch) throw new StaleEpochError();
        const seq = c.log.length + 1;
        c.log.push({ seq, kind: "APP", epoch: body.epoch, senderDeviceId: body.senderDeviceId, senderUserId: userId, payload: body.payload, createdAt: new Date().toISOString() });
        return { seq, epoch: c.info.epoch };
      },
      messages: async (cid, after) => member(cid).log.filter((e) => e.seq > after).slice(0, 200),
    };
  }

  /** Aprovação como a core-api faz: assinatura Ed25519 do aprovador sobre o contexto + id + chave do novo. */
  async approveWithSignature(newDeviceId: string, approverDeviceId: string, signature: string): Promise<boolean> {
    const impl = await ciphersuite();
    const target = this.devices.get(newDeviceId)!;
    const approver = this.devices.get(approverDeviceId)!;
    if (approver.state !== "APPROVED" || approver.userId !== target.userId) return false;
    const ok = await impl.signature.verify(unb64(approver.signatureKey), te.encode(APPROVAL_CONTEXT + newDeviceId + "|" + target.signatureKey), unb64(signature));
    if (ok) target.state = "APPROVED";
    return ok;
  }
}
