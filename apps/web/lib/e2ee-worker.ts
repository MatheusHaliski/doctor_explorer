/// <reference lib="webworker" />
/**
 * RF07 · Worker das insertable streams (RTCRtpScriptTransform). Cifra cada quadro que sai e decifra cada quadro que
 * chega com a chave do grupo MLS da conversa; quadro que não abre é descartado (nunca vai para o decodificador).
 */
import { clearPrefix, FrameKeyRing } from "@doctor-explorer/crypto/frames";

declare const self: DedicatedWorkerGlobalScope & { onrtctransform: ((e: RTCTransformEvent) => void) | null };

const ring = new FrameKeyRing();
const stats = { encrypted: 0, decrypted: 0, failed: 0, epoch: -1 };

self.onmessage = async (e: MessageEvent<{ type: "key"; epoch: number; raw: ArrayBuffer } | { type: "wrong-key" }>) => {
  if (e.data.type === "key") {
    await ring.setKey(e.data.epoch, new Uint8Array(e.data.raw));
    stats.epoch = ring.current ?? -1;
  } else if (e.data.type === "wrong-key") {
    // só para o teste de ponta a ponta: com outra chave nada do que chega abre, e o que sai não abre do outro lado
    await ring.setKey(ring.current ?? 0, crypto.getRandomValues(new Uint8Array(32)));
  }
};

self.onrtctransform = (event: RTCTransformEvent) => {
  const t = event.transformer;
  const { operation, kind } = t.options as { operation: "encrypt" | "decrypt"; kind: "audio" | "video" };
  t.readable
    .pipeThrough(
      new TransformStream<RTCEncodedVideoFrame | RTCEncodedAudioFrame, RTCEncodedVideoFrame | RTCEncodedAudioFrame>({
        async transform(frame, controller) {
          if (operation === "encrypt") {
            if (ring.current === null) return; // sem chave, nada sai
            const keyFrame = "type" in frame && frame.type === "key";
            frame.data = await ring.encrypt(frame.data, clearPrefix(kind, keyFrame));
            stats.encrypted++;
            controller.enqueue(frame);
          } else {
            const plain = await ring.decrypt(frame.data);
            if (!plain) {
              stats.failed++;
              return;
            }
            frame.data = plain;
            stats.decrypted++;
            controller.enqueue(frame);
          }
        },
      }),
    )
    .pipeTo(t.writable)
    .catch(() => {});
};

setInterval(() => self.postMessage({ type: "stats", ...stats }), 1000);
