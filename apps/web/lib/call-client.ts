"use client";

/**
 * RF07 · cliente da chamada: sinalização com o SFU (apps/rtc), transportes mediasoup e E2EE por quadro num Worker.
 * A chave vem do grupo MLS da conversa (exportador RFC 9420 com o id da chamada); quando a época muda, todos trocam.
 */
import { CALL_EXPORTER_LABEL, type ChatDevice } from "@doctor-explorer/crypto";
import { Device } from "mediasoup-client";
import type { Consumer, Producer, Transport } from "mediasoup-client/types";

export type Admission = { token: string; url: string; iceServers: RTCIceServer[]; maxParticipants: number };
export type Quality = "BOA" | "REGULAR" | "RUIM";
export type RemotePeer = { peerId: string; userId: string; name: string; audio?: MediaStream; video?: MediaStream; share?: MediaStream };
export type E2eeStats = { encrypted: number; decrypted: number; failed: number; epoch: number };
export type CallState = { status: "conectando" | "em-andamento" | "encerrada" | "falhou"; error?: string; peers: RemotePeer[]; quality: Quality | null; e2ee: E2eeStats; sharing: boolean };

type Pending = { resolve: (v: unknown) => void; reject: (e: Error) => void };

export class CallSession {
  private ws: WebSocket | null = null;
  private seq = 0;
  private readonly pending = new Map<number, Pending>();
  private readonly worker: Worker;
  private device = new Device();
  private sendTransport: Transport | null = null;
  private recvTransport: Transport | null = null;
  private readonly producers = new Map<string, Producer>();
  private readonly consumers = new Map<string, Consumer>();
  private readonly peers = new Map<string, RemotePeer>();
  private shareProducer: Producer | null = null;
  private keyTimer: ReturnType<typeof setInterval> | null = null;
  private statsTimer: ReturnType<typeof setInterval> | null = null;
  private lastEpoch = -1;
  everShared = false;
  state: CallState = { status: "conectando", peers: [], quality: null, e2ee: { encrypted: 0, decrypted: 0, failed: 0, epoch: -1 }, sharing: false };

  constructor(
    private readonly args: { callId: string; conversationId: string; chat: ChatDevice; admission: Admission; onChange: (s: CallState) => void },
  ) {
    this.worker = new Worker(new URL("./e2ee-worker.ts", import.meta.url), { type: "module" });
    this.worker.onmessage = (e: MessageEvent<{ type: string } & E2eeStats>) => {
      if (e.data.type === "stats") this.update({ e2ee: { encrypted: e.data.encrypted, decrypted: e.data.decrypted, failed: e.data.failed, epoch: e.data.epoch } });
    };
  }

  private update(patch: Partial<CallState>) {
    this.state = { ...this.state, ...patch, peers: [...this.peers.values()] };
    this.args.onChange(this.state);
  }

  /** Chave do quadro = exportador MLS da época atual da conversa; recalcula quando a época muda. */
  private async refreshKey() {
    await this.args.chat.sync(this.args.conversationId);
    const { epoch, secret } = await this.args.chat.exportSecret(this.args.conversationId, CALL_EXPORTER_LABEL, new TextEncoder().encode(this.args.callId));
    if (epoch === this.lastEpoch) return;
    this.lastEpoch = epoch;
    const raw = secret.slice().buffer as ArrayBuffer;
    this.worker.postMessage({ type: "key", epoch, raw }, [raw]);
  }

  private request<T>(method: string, data?: unknown): Promise<T> {
    const ws = this.ws;
    if (!ws || ws.readyState !== WebSocket.OPEN) return Promise.reject(new Error("sem conexão com o servidor de chamadas"));
    const id = ++this.seq;
    return new Promise<T>((resolve, reject) => {
      this.pending.set(id, { resolve: resolve as (v: unknown) => void, reject });
      ws.send(JSON.stringify({ id, method, data }));
    });
  }

  async start(local: MediaStream) {
    await this.refreshKey();
    this.keyTimer = setInterval(() => void this.refreshKey().catch(() => {}), 3000);
    const ws = new WebSocket(this.args.admission.url);
    this.ws = ws;
    ws.onmessage = (e) => {
      const m = JSON.parse(String(e.data)) as { id?: number; ok?: boolean; data?: unknown; error?: string; event?: string };
      if (m.id !== undefined) {
        const p = this.pending.get(m.id);
        this.pending.delete(m.id);
        if (m.ok) p?.resolve(m.data);
        else p?.reject(new Error(m.error ?? "erro"));
      } else if (m.event) void this.onEvent(m.event, m.data as Record<string, string>);
    };
    ws.onclose = (e) => {
      for (const p of this.pending.values()) p.reject(new Error("conexão encerrada"));
      this.pending.clear();
      if (this.state.status !== "encerrada") this.update({ status: "falhou", error: closeReason(e.code) });
    };
    await new Promise<void>((resolve, reject) => {
      ws.onopen = () => resolve();
      ws.onerror = () => reject(new Error("servidor de chamadas indisponível"));
    });

    const authed = await this.request<{ peerId: string; routerRtpCapabilities: Parameters<Device["load"]>[0]["routerRtpCapabilities"] }>("auth", {
      token: this.args.admission.token,
    });
    await this.device.load({ routerRtpCapabilities: authed.routerRtpCapabilities });
    const joined = await this.request<{ peers: { id: string; userId: string; name: string; producers: { id: string; kind: string; appData: { share?: boolean } }[] }[] }>("join", {
      rtpCapabilities: this.device.rtpCapabilities,
    });
    this.sendTransport = await this.transport("send");
    this.recvTransport = await this.transport("recv");
    for (const track of local.getTracks()) await this.produce(track, false);
    for (const p of joined.peers) {
      this.peers.set(p.id, { peerId: p.id, userId: p.userId, name: p.name });
      for (const pr of p.producers) await this.consume(p.id, pr.id, Boolean(pr.appData?.share));
    }
    this.statsTimer = setInterval(() => void this.measure(), 2000);
    this.update({ status: "em-andamento" });
  }

  private async transport(direction: "send" | "recv"): Promise<Transport> {
    const info = await this.request<{ id: string; iceParameters: never; iceCandidates: never; dtlsParameters: never }>("createTransport", { direction });
    const t =
      direction === "send"
        ? this.device.createSendTransport({ ...info, iceServers: this.args.admission.iceServers })
        : this.device.createRecvTransport({ ...info, iceServers: this.args.admission.iceServers });
    t.on("connect", ({ dtlsParameters }, ok, fail) => {
      this.request("connectTransport", { transportId: t.id, dtlsParameters }).then(() => ok(), fail);
    });
    if (direction === "send") {
      t.on("produce", ({ kind, rtpParameters, appData }, ok, fail) => {
        this.request<{ id: string }>("produce", { transportId: t.id, kind, rtpParameters, appData }).then(ok, fail);
      });
    }
    t.on("connectionstatechange", (s) => {
      if (s === "failed") this.update({ status: "falhou", error: "A rede não deixou a chamada conectar. Tente outra rede ou desligue a VPN." });
    });
    return t;
  }

  private async produce(track: MediaStreamTrack, share: boolean): Promise<Producer> {
    const kind = track.kind as "audio" | "video";
    const producer = await this.sendTransport!.produce({
      track,
      appData: { share },
      // a cifra entra antes do primeiro quadro sair
      onRtpSender: (sender) => {
        sender.transform = new RTCRtpScriptTransform(this.worker, { operation: "encrypt", kind });
      },
    });
    this.producers.set(producer.id, producer);
    return producer;
  }

  private async consume(peerId: string, producerId: string, share: boolean) {
    const info = await this.request<{ id: string; producerId: string; kind: "audio" | "video"; rtpParameters: never }>("consume", {
      transportId: this.recvTransport!.id,
      producerId,
    });
    const consumer = await this.recvTransport!.consume({
      ...info,
      onRtpReceiver: (receiver) => {
        receiver.transform = new RTCRtpScriptTransform(this.worker, { operation: "decrypt", kind: info.kind });
      },
    });
    this.consumers.set(consumer.id, consumer);
    const peer = this.peers.get(peerId);
    if (peer) {
      const stream = new MediaStream([consumer.track]);
      if (info.kind === "audio") peer.audio = stream;
      else if (share) peer.share = stream;
      else peer.video = stream;
    }
    await this.request("resumeConsumer", { consumerId: consumer.id });
    this.update({});
  }

  private async onEvent(event: string, d: Record<string, string>) {
    if (event === "peerJoined") {
      this.peers.set(d.id!, { peerId: d.id!, userId: d.userId!, name: d.name! });
      this.update({});
    } else if (event === "peerLeft") {
      this.peers.delete(d.peerId!);
      this.update({});
    } else if (event === "newProducer") {
      if (!this.peers.has(d.peerId!)) this.peers.set(d.peerId!, { peerId: d.peerId!, userId: "", name: "" });
      await this.consume(d.peerId!, d.producerId!, Boolean((d.appData as unknown as { share?: boolean })?.share));
    } else if (event === "producerClosed") {
      const peer = this.peers.get(d.peerId!);
      if (peer) peer.share = undefined;
      this.update({});
    }
  }

  async shareScreen() {
    const display = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false });
    const track = display.getVideoTracks()[0]!;
    this.shareProducer = await this.produce(track, true);
    this.everShared = true;
    track.onended = () => void this.stopShare();
    this.update({ sharing: true });
  }

  async stopShare() {
    const p = this.shareProducer;
    if (!p) return;
    this.shareProducer = null;
    p.close();
    p.track?.stop();
    await this.request("closeProducer", { producerId: p.id }).catch(() => {});
    this.update({ sharing: false });
  }

  /** Perda de pacotes e RTT dos transportes viram BOA / REGULAR / RUIM (heurística 1). */
  private async measure() {
    let lost = 0;
    let received = 0;
    let rtt = 0;
    for (const t of [this.sendTransport, this.recvTransport]) {
      if (!t) continue;
      (await t.getStats()).forEach((r: { type: string; packetsLost?: number; packetsReceived?: number; currentRoundTripTime?: number; state?: string }) => {
        if (r.type === "inbound-rtp") {
          lost += r.packetsLost ?? 0;
          received += r.packetsReceived ?? 0;
        }
        if (r.type === "candidate-pair" && r.state === "succeeded" && r.currentRoundTripTime) rtt = Math.max(rtt, r.currentRoundTripTime);
      });
    }
    const loss = received + lost > 0 ? lost / (received + lost) : 0;
    this.update({ quality: loss > 0.08 || rtt > 0.6 ? "RUIM" : loss > 0.02 || rtt > 0.3 ? "REGULAR" : "BOA" });
  }

  /** Só para o teste de ponta a ponta: troca a chave deste aparelho por uma aleatória. */
  breakKeyForTest() {
    this.worker.postMessage({ type: "wrong-key" });
  }

  leave() {
    this.state = { ...this.state, status: "encerrada" };
    if (this.keyTimer) clearInterval(this.keyTimer);
    if (this.statsTimer) clearInterval(this.statsTimer);
    for (const p of this.producers.values()) {
      p.close();
      p.track?.stop();
    }
    this.sendTransport?.close();
    this.recvTransport?.close();
    try {
      this.ws?.send(JSON.stringify({ id: ++this.seq, method: "leave" }));
    } catch {
      /* já fechado */
    }
    this.ws?.close();
    this.worker.terminate();
    this.args.onChange(this.state);
  }
}

function closeReason(code: number): string {
  if (code === 4001) return "A entrada na chamada expirou. Saia e entre de novo.";
  if (code === 4009) return "A chamada está cheia (até 8 pessoas).";
  if (code === 4003) return "Este endereço não pode entrar nas chamadas.";
  return "A conexão com o servidor de chamadas caiu.";
}
