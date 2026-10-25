import * as mediasoup from "mediasoup";
import type { types } from "mediasoup";
import { config } from "./config.ts";

/** Só Opus e VP8: o cabeçalho VP8 fica fora da cifra E2EE para o SFU achar quadros-chave sem ver a imagem. */
export const MEDIA_CODECS: types.RouterRtpCodecCapability[] = [
  { kind: "audio", mimeType: "audio/opus", clockRate: 48000, channels: 2 },
  { kind: "video", mimeType: "video/VP8", clockRate: 90000, parameters: {} },
];

export interface PeerInfo {
  id: string;
  userId: string;
  name: string;
  deviceId: string;
}

export class Peer {
  readonly transports = new Map<string, types.WebRtcTransport>();
  readonly producers = new Map<string, types.Producer>();
  readonly consumers = new Map<string, types.Consumer>();
  rtpCapabilities: types.RtpCapabilities | null = null;

  readonly info: PeerInfo;
  readonly notify: (event: string, data: unknown) => void;

  constructor(info: PeerInfo, notify: (event: string, data: unknown) => void) {
    this.info = info;
    this.notify = notify;
  }

  close() {
    for (const t of this.transports.values()) t.close();
  }
}

export class Room {
  readonly peers = new Map<string, Peer>();

  readonly id: string;
  readonly router: types.Router;

  constructor(id: string, router: types.Router) {
    this.id = id;
    this.router = router;
  }

  broadcast(from: string, event: string, data: unknown) {
    for (const p of this.peers.values()) if (p.info.id !== from) p.notify(event, data);
  }

  /** Quem está na sala e o que cada um publica (sem mídia: só ids). */
  snapshot() {
    return [...this.peers.values()].map((p) => ({
      ...p.info,
      producers: [...p.producers.values()].map((pr) => ({ id: pr.id, kind: pr.kind, appData: pr.appData })),
    }));
  }
}

export class Sfu {
  private readonly rooms = new Map<string, Room>();

  private readonly worker: types.Worker;

  private constructor(worker: types.Worker) {
    this.worker = worker;
  }

  static async start(): Promise<Sfu> {
    const worker = await mediasoup.createWorker({ logLevel: "warn" });
    worker.on("died", () => {
      console.error("mediasoup-worker morreu; encerrando para o orquestrador reiniciar");
      process.exit(1);
    });
    return new Sfu(worker);
  }

  async room(id: string): Promise<Room> {
    let r = this.rooms.get(id);
    if (!r) {
      r = new Room(id, await this.worker.createRouter({ mediaCodecs: MEDIA_CODECS }));
      this.rooms.set(id, r);
    }
    return r;
  }

  leave(room: Room, peerId: string) {
    const peer = room.peers.get(peerId);
    if (peer) {
      peer.close();
      room.peers.delete(peerId);
      room.broadcast(peerId, "peerLeft", { peerId });
    }
    // também quem autenticou e não chegou a entrar: sala vazia não fica com router aberto
    if (room.peers.size === 0 && this.rooms.get(room.id) === room) {
      room.router.close();
      this.rooms.delete(room.id);
    }
  }

  async createTransport(room: Room) {
    const listen = { ip: config.listenIp, announcedAddress: config.announcedAddress, portRange: { min: config.minPort, max: config.maxPort } };
    return room.router.createWebRtcTransport({
      listenInfos: [
        { protocol: "udp", ...listen },
        { protocol: "tcp", ...listen },
      ],
      enableUdp: true,
      enableTcp: true,
      preferUdp: true,
      initialAvailableOutgoingBitrate: 800_000,
    });
  }

  roomCount() {
    return this.rooms.size;
  }

  close() {
    this.worker.close();
  }
}
