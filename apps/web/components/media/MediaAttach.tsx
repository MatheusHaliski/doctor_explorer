"use client";

import { useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { api, ApiError } from "@/lib/api";
import { Button, Notice, ProblemAlert } from "@/components/ui";
import { contentTypeFor, describeReport, type MediaReport } from "@/lib/media";

export type MediaView = {
  id: string;
  kind: "IMAGE" | "DICOM";
  state: string;
  previewVersion: number;
  width: number | null;
  height: number | null;
  dermatology: boolean;
  reason: string;
  report: MediaReport | null;
  previewUrl?: string;
  url?: string;
};

type Box = { x: number; y: number; w: number; h: number };
const WORKING = new Set(["QUARENTENA", "PROCESSANDO"]);

/**
 * RF05 · anexar imagem ao caso. O arquivo vai do navegador direto para a quarentena (URL pré-assinada, tipo e
 * tamanho fixos); a API anonimiza em segundo plano; o autor revisa a prévia, cobre o que sobrou e aprova. Só a
 * versão aprovada entra na publicação.
 */
export function MediaAttach({ approved, onChange, dermatologyGroup }: { approved: MediaView[]; onChange: (m: MediaView[]) => void; dermatologyGroup: boolean }) {
  const [reviewing, setReviewing] = useState<MediaView | null>(null);
  const [status, setStatus] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [dermatology, setDermatology] = useState(dermatologyGroup);
  const [blurAnyway, setBlurAnyway] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  async function upload(file: File) {
    setError(null);
    setReviewing(null);
    const type = contentTypeFor(file);
    try {
      setStatus("Pedindo envio seguro…");
      const u = await api.post<{ id: string; uploadUrl: string; headers: Record<string, string> }>("/api/v1/media/uploads", {
        contentType: type,
        size: file.size,
        dermatology,
        blurFaces: dermatology ? blurAnyway : null,
      });
      setStatus("Enviando para a quarentena…");
      const put = await fetch(u.uploadUrl, { method: "PUT", headers: u.headers, body: file });
      if (!put.ok) throw new ApiError({ status: put.status, title: "Envio não concluído", detail: "O armazenamento recusou o arquivo. Tente de novo." });
      await api.post(`/api/v1/media/${u.id}/complete`);
      setStatus("Anonimizando: removendo metadados, procurando texto e rostos…");
      setReviewing(await poll(u.id));
      setStatus("");
    } catch (err) {
      setStatus("");
      setError(err);
    } finally {
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  async function poll(id: string): Promise<MediaView> {
    for (let i = 0; i < 120; i++) {
      const m = await api.get<MediaView>(`/api/v1/media/${id}`);
      if (!WORKING.has(m.state)) return m;
      await new Promise((r) => setTimeout(r, 1000));
    }
    throw new ApiError({ status: 504, title: "Demorou demais", detail: "A anonimização não terminou a tempo. Nada foi publicado; tente de novo." });
  }

  async function discard(m: MediaView) {
    try {
      await api.delete(`/api/v1/media/${m.id}`);
    } catch (err) {
      setError(err);
      return;
    }
    if (reviewing?.id === m.id) setReviewing(null);
    onChange(approved.filter((x) => x.id !== m.id));
  }

  return (
    <section className="card mt-4" aria-labelledby="media-title">
      <h2 id="media-title" className="mt-0 text-md">
        Imagens do caso
      </h2>
      <p className="text-sm text-text-2">
        JPEG, PNG, HEIC ou DICOM. Antes de publicar, removemos metadados (inclusive localização), cobrimos texto impresso na imagem e borramos rostos. Você confere a
        prévia e aprova. O original é apagado.
      </p>
      <label className="switch">
        <input type="checkbox" checked={dermatology} onChange={(e) => setDermatology(e.target.checked)} />
        Imagem dermatológica (não borrar rostos automaticamente)
      </label>
      {dermatology && (
        <label className="switch">
          <input type="checkbox" checked={blurAnyway} onChange={(e) => setBlurAnyway(e.target.checked)} />
          Mesmo assim, borrar rostos detectados
        </label>
      )}
      <div className="toolbar">
        <input
          ref={fileRef}
          id="media-file"
          className="sr-only media-file"
          type="file"
          accept="image/jpeg,image/png,image/heic,image/heif,.heic,.heif,.dcm,application/dicom"
          disabled={Boolean(status) || approved.length >= 10}
          onChange={(e) => e.target.files?.[0] && upload(e.target.files[0])}
        />
        <label className="btn" htmlFor="media-file">
          Escolher imagem
        </label>
        {status && (
          <span role="status" className="text-sm text-text-2">
            {status}
          </span>
        )}
      </div>
      <ProblemAlert error={error} />
      {reviewing && (
        <Review
          key={`${reviewing.id}-${reviewing.previewVersion}`}
          media={reviewing}
          onUpdate={setReviewing}
          onApproved={(m) => {
            setReviewing(null);
            onChange([...approved, m]);
          }}
          onDiscard={() => discard(reviewing)}
        />
      )}
      {approved.length > 0 && (
        <ul className="media-grid" aria-label="Imagens aprovadas">
          {approved.map((m) => (
            <li key={m.id}>
              {/* eslint-disable-next-line @next/next/no-img-element -- URL pré-assinada e curta do S3 */}
              <img src={m.url} alt="Imagem anonimizada aprovada" />
              <Button type="button" size="sm" onClick={() => discard(m)}>
                Remover
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function Review({ media, onUpdate, onApproved, onDiscard }: { media: MediaView; onUpdate: (m: MediaView) => void; onApproved: (m: MediaView) => void; onDiscard: () => void }) {
  const [boxes, setBoxes] = useState<Box[]>([]);
  const [drag, setDrag] = useState<{ x0: number; y0: number; box: Box } | null>(null);
  const [reviewed, setReviewed] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const frame = useRef<HTMLDivElement>(null);

  if (media.state === "REJEITADO" || media.state === "FALHOU" || media.state === "EXPIRADO") {
    return (
      <Notice tone="danger" role="alert">
        {media.reason || "Não foi possível anonimizar esta imagem com segurança."} Nada foi publicado e o arquivo foi apagado.
      </Notice>
    );
  }

  const point = (e: ReactPointerEvent) => {
    const r = frame.current!.getBoundingClientRect();
    return { x: Math.min(1, Math.max(0, (e.clientX - r.left) / r.width)), y: Math.min(1, Math.max(0, (e.clientY - r.top) / r.height)) };
  };

  function down(e: ReactPointerEvent<HTMLDivElement>) {
    e.currentTarget.setPointerCapture(e.pointerId);
    const p = point(e);
    setDrag({ x0: p.x, y0: p.y, box: { x: p.x, y: p.y, w: 0, h: 0 } });
  }
  function move(e: ReactPointerEvent<HTMLDivElement>) {
    if (!drag) return;
    const p = point(e);
    setDrag({ ...drag, box: { x: Math.min(drag.x0, p.x), y: Math.min(drag.y0, p.y), w: Math.abs(p.x - drag.x0), h: Math.abs(p.y - drag.y0) } });
  }
  function up() {
    if (drag && drag.box.w > 0.01 && drag.box.h > 0.01) setBoxes([...boxes, drag.box]);
    setDrag(null);
  }

  async function applyBoxes() {
    setBusy("redact");
    setError(null);
    try {
      onUpdate(await api.post<MediaView>(`/api/v1/media/${media.id}/redactions`, { previewVersion: media.previewVersion, regions: boxes }));
    } catch (err) {
      setError(err);
      setBusy(null);
    }
  }

  async function approve() {
    setBusy("approve");
    setError(null);
    try {
      onApproved(await api.post<MediaView>(`/api/v1/media/${media.id}/approve`, { previewVersion: media.previewVersion, reviewed }));
    } catch (err) {
      setError(err);
      setBusy(null);
    }
  }

  const all = drag ? [...boxes, drag.box] : boxes;
  return (
    <div className="media-review mt-4">
      <h3 className="mt-0 text-md">Revise a prévia (versão {media.previewVersion})</h3>
      <p className="text-sm text-text-2">Arraste sobre a imagem para cobrir qualquer coisa que ainda identifique o paciente (pulseira, tatuagem, etiqueta).</p>
      <div
        ref={frame}
        className="media-frame"
        style={{ aspectRatio: media.width && media.height ? `${media.width} / ${media.height}` : undefined }}
        onPointerDown={down}
        onPointerMove={move}
        onPointerUp={up}
        onPointerCancel={() => setDrag(null)}
      >
        {/* eslint-disable-next-line @next/next/no-img-element -- prévia privada por URL pré-assinada de 5 min */}
        <img src={media.previewUrl} alt="Prévia anonimizada para revisão" draggable={false} />
        {all.map((b, i) => (
          <span key={i} className="media-box" style={{ left: `${b.x * 100}%`, top: `${b.y * 100}%`, width: `${b.w * 100}%`, height: `${b.h * 100}%` }} />
        ))}
      </div>
      {boxes.length > 0 && (
        <div className="toolbar">
          <span className="text-sm">
            {boxes.length} {boxes.length === 1 ? "área marcada" : "áreas marcadas"}
          </span>
          <Button type="button" variant="primary" size="sm" loading={busy === "redact"} onClick={applyBoxes}>
            Aplicar tarjas
          </Button>
          <Button type="button" size="sm" onClick={() => setBoxes([])}>
            Limpar marcações
          </Button>
        </div>
      )}
      <h4 className="mb-1">O que foi feito</h4>
      <ul className="text-sm text-text-2">
        {describeReport(media.report).map((line) => (
          <li key={line}>{line}</li>
        ))}
      </ul>
      <label className="switch">
        <input type="checkbox" checked={reviewed} onChange={(e) => setReviewed(e.target.checked)} disabled={boxes.length > 0} />
        Revisei a prévia e a lista do que foi removido
      </label>
      <ProblemAlert error={error} />
      <div className="toolbar">
        <Button type="button" variant="primary" disabled={!reviewed || boxes.length > 0} loading={busy === "approve"} onClick={approve}>
          Aprovar imagem
        </Button>
        <Button type="button" variant="danger" onClick={onDiscard}>
          Descartar
        </Button>
      </div>
    </div>
  );
}
