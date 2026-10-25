import { generateKeyPairSync, randomUUID, sign } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { WebSocket } from "ws";
import { startRtc, type RtcServer } from "../src/server.ts";
import { TokenVerifier } from "../src/token.ts";

const keys = generateKeyPairSync("ed25519");
const ORIGIN = "http://localhost:3000";

/** Mesmo formato do InternalTokens.issueRtc da core-api. */
function token(claims: Record<string, unknown> = {}, key = keys.privateKey) {
  const now = Date.now();
  const body = {
    v: 1,
    iss: "core-api",
    aud: "rtc",
    sub: randomUUID(),
    name: "Dra. Ana",
    seal: "VERIFIED",
    iat: now,
    exp: now + 60_000,
    jti: randomUUID(),
    room: ROOM,
    device: randomUUID(),
    ...claims,
  };
  const head = "v1." + Buffer.from(JSON.stringify(body)).toString("base64url");
  return head + "." + sign(null, Buffer.from(head, "ascii"), key).toString("base64url");
}

const ROOM = randomUUID();
let rtc: RtcServer;

beforeAll(async () => {
  rtc = await startRtc(new TokenVerifier(keys.publicKey), 0);
});
afterAll(async () => {
  await rtc.close();
});

type Conn = { ws: WebSocket; closed: Promise<{ code: number; reason: string }>; call: (method: string, data?: unknown) => Promise<{ ok: boolean; data?: any; error?: string }> };

function connect(origin = ORIGIN): Promise<Conn> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://127.0.0.1:${rtc.port}/ws`, { headers: { origin } });
    let n = 0;
    const pending = new Map<number, (v: any) => void>();
    const closed = new Promise<{ code: number; reason: string }>((r) => ws.on("close", (code, reason) => r({ code, reason: String(reason) })));
    ws.on("message", (raw) => {
      const m = JSON.parse(String(raw));
      if (m.id !== undefined) pending.get(m.id)?.(m);
    });
    ws.on("open", () =>
      resolve({
        ws,
        closed,
        call: (method, data) =>
          new Promise((r) => {
            const id = ++n;
            pending.set(id, r);
            ws.send(JSON.stringify({ id, method, data }));
          }),
      }),
    );
    ws.on("error", reject);
  });
}

describe("rtc · admissão no SFU (RF07)", () => {
  it("só entra com token da core-api, uma vez, do público rtc e dentro da validade", async () => {
    const before = await connect();
    void before.call("createTransport", { direction: "send" });
    expect((await before.closed).code).toBe(4001);

    for (const bad of [token({}, generateKeyPairSync("ed25519").privateKey), token({ aud: "clinical-api" }), token({ exp: Date.now() - 1 }), token({ seal: "PENDING" }), "v1.x.y"]) {
      const c = await connect();
      void c.call("auth", { token: bad });
      expect((await c.closed).code).toBe(4001);
    }

    const t = token();
    const ok = await connect();
    const authed = await ok.call("auth", { token: t });
    expect(authed.ok).toBe(true);
    expect(authed.data.routerRtpCapabilities.codecs.map((c: { mimeType: string }) => c.mimeType)).toEqual(expect.arrayContaining(["audio/opus", "video/VP8"]));
    const replay = await connect();
    void replay.call("auth", { token: t });
    expect((await replay.closed).code).toBe(4001);
    ok.ws.close();
  });

  it("recusa origem desconhecida", async () => {
    const c = await connect("https://evil.example");
    expect((await c.closed).code).toBe(4003);
  });

  it("negocia transporte e avisa quem está na sala; no máximo 8 pessoas", async () => {
    const room = randomUUID();
    const peers: Conn[] = [];
    for (let i = 0; i < 8; i++) {
      const c = await connect();
      expect((await c.call("auth", { token: token({ room }) })).ok).toBe(true);
      const joined = await c.call("join", { rtpCapabilities: { codecs: [], headerExtensions: [] } });
      expect(joined.data.peers).toHaveLength(i);
      peers.push(c);
    }
    const t = await peers[0]!.call("createTransport", { direction: "send" });
    expect(t.ok).toBe(true);
    expect(t.data.iceCandidates.length).toBeGreaterThan(0);
    expect(t.data.dtlsParameters.fingerprints.length).toBeGreaterThan(0);
    expect((await peers[0]!.call("produce", { transportId: "x", kind: "video", rtpParameters: {} })).ok).toBe(false);
    for (let i = 0; i < 4; i++) expect((await peers[1]!.call("createTransport", { direction: "recv" })).ok).toBe(true);
    expect((await peers[1]!.call("createTransport", { direction: "recv" })).error).toBe("transportes demais");

    const ninth = await connect();
    void ninth.call("auth", { token: token({ room }) });
    expect((await ninth.closed).code).toBe(4009);
    for (const p of peers) p.ws.close();
    await Promise.all(peers.map((p) => p.closed));
    await new Promise((r) => setTimeout(r, 50));
    expect(rtc.sfu.roomCount()).toBe(0);
  });
});
