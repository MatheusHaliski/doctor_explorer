import { randomUUID } from "node:crypto";
import { createServer, type IncomingMessage, type Server } from "node:http";
import type { types } from "mediasoup";
import { WebSocketServer, type WebSocket } from "ws";
import { config } from "./config.ts";
import { Peer, Sfu, type Room } from "./sfu.ts";
import { TokenVerifier, type RtcClaims } from "./token.ts";

/**
 * RF07 · sinalização das chamadas. O navegador abre o WebSocket, manda o token de admissão da core-api como
 * primeira mensagem e então negocia transportes, produtores e consumidores do mediasoup. A mídia que chega já vem
 * cifrada quadro a quadro pelo aparelho (chave do grupo MLS); o SFU só repassa.
 */
type Request = { id: number; method: string; data?: Record<string, unknown> };

/** Por participante: envio + recebimento (com folga para reconexão) e áudio, vídeo e tela. */
const MAX_TRANSPORTS = 4;
const MAX_PRODUCERS = 3;

export interface RtcServer {
  server: Server;
  port: number;
  sfu: Sfu;
  close(): Promise<void>;
}

export async function startRtc(verifier: TokenVerifier, port = config.port): Promise<RtcServer> {
  const sfu = await Sfu.start();
  const server = createServer((req, res) => {
    if (req.url === "/health") {
      res.writeHead(200, { "content-type": "application/json" }).end(JSON.stringify({ status: "UP", rooms: sfu.roomCount() }));
      return;
    }
    res.writeHead(404).end();
  });
  const wss = new WebSocketServer({ server, path: "/ws", maxPayload: 64 * 1024 });

  wss.on("connection", (ws: WebSocket, req: IncomingMessage) => {
    const origin = req.headers.origin ?? "";
    if (!config.allowedOrigins.includes(origin)) {
      ws.close(4003, "origem não permitida");
      return;
    }
    let claims: RtcClaims | null = null;
    let room: Room | null = null;
    let peer: Peer | null = null;
    const send = (msg: unknown) => ws.readyState === ws.OPEN && ws.send(JSON.stringify(msg));
    const timer = setTimeout(() => !claims && ws.close(4001, "sem autenticação"), config.authTimeoutMs);

    ws.on("message", async (raw) => {
      let msg: Request;
      try {
        msg = JSON.parse(String(raw)) as Request;
      } catch {
        ws.close(4000, "mensagem inválida");
        return;
      }
      const reply = (data: unknown) => send({ id: msg.id, ok: true, data });
      const fail = (error: string) => send({ id: msg.id, ok: false, error });
      try {
        if (!claims) {
          if (msg.method !== "auth") return ws.close(4001, "autentique primeiro");
          claims = verifier.verify(msg.data?.token);
          if (!claims) return ws.close(4001, "token inválido ou já usado");
          clearTimeout(timer);
          room = await sfu.room(claims.room);
          if (room.peers.size >= config.maxPeers) return ws.close(4009, "chamada cheia");
          const c = claims;
          peer = new Peer({ id: randomUUID(), userId: c.sub, name: c.name, deviceId: c.device }, (event, data) => send({ event, data }));
          return reply({ peerId: peer.info.id, routerRtpCapabilities: room.router.rtpCapabilities });
        }
        const r = room!;
        const p = peer!;
        const d = msg.data ?? {};
        switch (msg.method) {
          case "join": {
            if (r.peers.has(p.info.id)) return fail("já entrou");
            if (r.peers.size >= config.maxPeers) return ws.close(4009, "chamada cheia");
            p.rtpCapabilities = d.rtpCapabilities as types.RtpCapabilities;
            const others = r.snapshot();
            r.peers.set(p.info.id, p);
            r.broadcast(p.info.id, "peerJoined", { ...p.info, producers: [] });
            return reply({ peers: others });
          }
          case "createTransport": {
            if (!r.peers.has(p.info.id)) return fail("entre na sala primeiro");
            if (p.transports.size >= MAX_TRANSPORTS) return fail("transportes demais");
            const t = await sfu.createTransport(r);
            t.appData = { direction: d.direction === "recv" ? "recv" : "send" };
            p.transports.set(t.id, t);
            return reply({ id: t.id, iceParameters: t.iceParameters, iceCandidates: t.iceCandidates, dtlsParameters: t.dtlsParameters });
          }
          case "connectTransport": {
            const t = p.transports.get(String(d.transportId));
            if (!t) return fail("transporte desconhecido");
            await t.connect({ dtlsParameters: d.dtlsParameters as types.DtlsParameters });
            return reply({});
          }
          case "produce": {
            const t = p.transports.get(String(d.transportId));
            if (!t || t.appData.direction !== "send") return fail("transporte de envio desconhecido");
            if (p.producers.size >= MAX_PRODUCERS) return fail("fluxos demais");
            const kind = d.kind === "audio" ? "audio" : "video";
            const share = Boolean((d.appData as { share?: unknown } | undefined)?.share) && kind === "video";
            const producer = await t.produce({ kind, rtpParameters: d.rtpParameters as types.RtpParameters, appData: { share, peerId: p.info.id } });
            p.producers.set(producer.id, producer);
            producer.on("transportclose", () => p.producers.delete(producer.id));
            r.broadcast(p.info.id, "newProducer", { peerId: p.info.id, producerId: producer.id, kind, appData: { share } });
            return reply({ id: producer.id });
          }
          case "closeProducer": {
            const producer = p.producers.get(String(d.producerId));
            if (!producer) return fail("produtor desconhecido");
            producer.close();
            p.producers.delete(producer.id);
            r.broadcast(p.info.id, "producerClosed", { peerId: p.info.id, producerId: producer.id });
            return reply({});
          }
          case "consume": {
            const t = p.transports.get(String(d.transportId));
            if (!t || t.appData.direction !== "recv") return fail("transporte de recebimento desconhecido");
            const producerId = String(d.producerId);
            if (!p.rtpCapabilities || !r.router.canConsume({ producerId, rtpCapabilities: p.rtpCapabilities })) return fail("não dá para receber este fluxo");
            const consumer = await t.consume({ producerId, rtpCapabilities: p.rtpCapabilities, paused: true });
            p.consumers.set(consumer.id, consumer);
            consumer.on("transportclose", () => p.consumers.delete(consumer.id));
            consumer.on("producerclose", () => {
              p.consumers.delete(consumer.id);
              p.notify("consumerClosed", { consumerId: consumer.id });
            });
            return reply({ id: consumer.id, producerId, kind: consumer.kind, rtpParameters: consumer.rtpParameters, appData: consumer.appData });
          }
          case "resumeConsumer": {
            const consumer = p.consumers.get(String(d.consumerId));
            if (!consumer) return fail("consumidor desconhecido");
            await consumer.resume();
            return reply({});
          }
          case "leave":
            ws.close(1000, "saiu");
            return;
          default:
            return fail("método desconhecido");
        }
      } catch (e) {
        fail(e instanceof Error ? e.message : "erro");
      }
    });

    ws.on("close", () => {
      clearTimeout(timer);
      if (room) sfu.leave(room, peer?.info.id ?? "");
    });
  });

  await new Promise<void>((resolve) => server.listen(port, resolve));
  const address = server.address();
  return {
    server,
    port: typeof address === "object" && address ? address.port : port,
    sfu,
    close: async () => {
      for (const c of wss.clients) c.terminate();
      await new Promise<void>((resolve) => server.close(() => resolve()));
      sfu.close();
    },
  };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  // a core-api pode subir depois: tenta a chave pública por até 2 min
  let verifier: TokenVerifier | null = null;
  for (let i = 0; i < 60 && !verifier; i++) {
    try {
      verifier = await TokenVerifier.fetch(config.coreUrl);
    } catch (e) {
      console.warn("aguardando a core-api:", e instanceof Error ? e.message : e);
      await new Promise((r) => setTimeout(r, 2000));
    }
  }
  if (!verifier) throw new Error("sem a chave pública da core-api");
  const rtc = await startRtc(verifier);
  console.log(`rtc ouvindo em :${rtc.port} (mídia ${config.listenIp} → ${config.announcedAddress}, portas ${config.minPort}-${config.maxPort})`);
  const stop = () => void rtc.close().then(() => process.exit(0));
  process.on("SIGTERM", stop);
  process.on("SIGINT", stop);
}
