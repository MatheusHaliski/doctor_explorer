"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import type { ChatDevice } from "@doctor-explorer/crypto";
import { api } from "@/lib/api";
import { CallSession, type Admission, type CallState } from "@/lib/call-client";
import { useAsyncEffect } from "@/lib/use-async";
import { useSession } from "@/components/session-context";
import { Button, Loading, Notice, ProblemAlert } from "@/components/ui";

type CallInfo = {
  id: string;
  conversationId: string;
  conversationKind: string;
  conversationTitle: string | null;
  state: string;
  startedBy: string;
  startedByName: string;
};

const QUALITY: Record<string, string> = { BOA: "Rede boa", REGULAR: "Rede instável", RUIM: "Rede ruim" };

function Video({ stream, muted, label }: { stream: MediaStream; muted?: boolean; label: string }) {
  const ref = useRef<HTMLVideoElement>(null);
  useEffect(() => {
    if (ref.current) ref.current.srcObject = stream;
  }, [stream]);
  return (
    <figure className="call-tile">
      <video ref={ref} autoPlay playsInline muted={muted} aria-label={label} />
      <figcaption>{label}</figcaption>
    </figure>
  );
}

function Audio({ stream }: { stream: MediaStream }) {
  const ref = useRef<HTMLAudioElement>(null);
  useEffect(() => {
    if (ref.current) ref.current.srcObject = stream;
  }, [stream]);
  return <audio ref={ref} autoPlay />;
}

/**
 * RF07 · chamada de vídeo. Antes de entrar, os avisos (sem gravação, não é teleconsulta); dentro, quem está, a
 * qualidade da rede e o indicador de E2EE, que só acende quando quadros cifrados com a chave MLS estão abrindo.
 */
export function CallView({ id, device }: { id: string; device: ChatDevice }) {
  const session_ = useSession();
  const [info, setInfo] = useState<CallInfo | null>(null);
  const [phase, setPhase] = useState<"lobby" | "joining" | "in-call" | "ended">("lobby");
  const [state, setState] = useState<CallState | null>(null);
  const [local, setLocal] = useState<MediaStream | null>(null);
  const [mic, setMic] = useState(true);
  const [cam, setCam] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const session = useRef<CallSession | null>(null);

  useAsyncEffect(async () => setInfo(await api.get<CallInfo>(`/api/v1/calls/${id}`)), setError, [id]);

  useEffect(() => {
    const leave = () => session.current?.leave();
    window.addEventListener("pagehide", leave);
    return () => {
      window.removeEventListener("pagehide", leave);
      leave();
    };
  }, []);

  async function enter() {
    setPhase("joining");
    setError(null);
    try {
      if (!(await device.isMember(info!.conversationId))) {
        await device.acceptWelcomes();
        if (!(await device.isMember(info!.conversationId))) throw new Error("Este aparelho ainda não entrou no grupo cifrado da conversa. Abra a conversa e tente de novo.");
      }
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: { width: 640, height: 360 } });
      setLocal(stream);
      const joined = await api.post<{ admission: Admission }>(`/api/v1/calls/${id}/join`, { deviceId: device.deviceId });
      const s = new CallSession({ callId: id, conversationId: info!.conversationId, chat: device, admission: joined.admission, onChange: setState });
      session.current = s;
      (window as unknown as { __deCall?: unknown }).__deCall = { state: () => s.state, breakKey: () => s.breakKeyForTest() };
      await s.start(stream);
      setPhase("in-call");
    } catch (err) {
      session.current?.leave();
      session.current = null;
      local?.getTracks().forEach((t) => t.stop());
      setError(err instanceof Error && !(err instanceof DOMException) ? { message: err.message } : err);
      setPhase("lobby");
    }
  }

  async function leave() {
    const s = session.current;
    session.current = null;
    s?.leave();
    local?.getTracks().forEach((t) => t.stop());
    await api.post(`/api/v1/calls/${id}/leave`, { quality: s?.state.quality ?? null, screenShared: s?.everShared ?? false }).catch(() => {});
    setPhase("ended");
  }

  async function share() {
    if (!confirm("A tela compartilhada pode mostrar dado de paciente (prontuário, exames). Feche o que não deve aparecer antes de continuar.")) return;
    try {
      await session.current?.shareScreen();
    } catch (err) {
      setError(err);
    }
  }

  if (!info) return error ? <ProblemAlert error={error} /> : <Loading what="Abrindo a chamada" />;
  const other = state?.peers.find((p) => p.name)?.name;
  const title =
    info.conversationKind === "GROUP"
      ? (info.conversationTitle ?? "Grupo")
      : info.startedBy === session_.userId
        ? other
          ? `Chamada com ${other}`
          : "Chamada de vídeo"
        : `Chamada com ${info.startedByName}`;
  const e2eeOn = state ? state.e2ee.decrypted > 0 || (state.peers.length === 0 && state.e2ee.encrypted > 0) : false;
  const errorText = error && typeof error === "object" && "message" in error && !("problem" in error) ? String((error as { message: string }).message) : null;

  if (phase === "ended") {
    return (
      <div className="max-w-2xl">
        <h1>Chamada encerrada</h1>
        <p className="lead">Nada da chamada foi gravado; ficaram só duração, participantes e a qualidade da rede.</p>
        <Notice tone="info">
          Falaram de paciente? Registre o que importa no <Link href="/prontuario/nova">prontuário</Link>: a chamada não é prontuário.
        </Notice>
        <div className="toolbar">
          <Link className="btn primary" href="/prontuario/nova">
            Registrar no prontuário
          </Link>
          <Link className="btn" href={`/mensagens/${info.conversationId}`}>
            Voltar à conversa
          </Link>
        </div>
      </div>
    );
  }

  if (phase !== "in-call") {
    return (
      <div className="max-w-2xl">
        <p className="result-line">
          <Link href={`/mensagens/${info.conversationId}`}>← Conversa</Link>
        </p>
        <h1>{title}</h1>
        <Notice tone="warning">
          <strong>Sem gravação.</strong> A plataforma não grava chamadas e o áudio e o vídeo são cifrados ponta a ponta. Não há como impedir que alguém filme a tela: combine
          isso com quem está na chamada.
        </Notice>
        <Notice tone="info">Chamada entre profissionais. Não use para atender paciente: teleconsulta fica fora desta versão.</Notice>
        {errorText ? <Notice tone="danger">{errorText}</Notice> : <ProblemAlert error={error} />}
        <Button type="button" variant="primary" loading={phase === "joining"} onClick={enter}>
          Entrar na chamada
        </Button>
      </div>
    );
  }

  return (
    <div className="call">
      <div className="toolbar">
        <h1 className="mr-auto mb-0">{title}</h1>
        {e2eeOn ? (
          <span className="pill chain-ok" role="status">
            Criptografada ponta a ponta
          </span>
        ) : (
          <span className="pill" role="status">
            Estabelecendo criptografia…
          </span>
        )}
        {state?.quality && <span className={`pill quality-${state.quality.toLowerCase()}`}>{QUALITY[state.quality]}</span>}
      </div>
      <p className="text-sm text-text-2" aria-live="polite">
        Na chamada: você{state?.peers.length ? ", " + state.peers.map((p) => p.name || "participante").join(", ") : " (aguardando os outros)"}
      </p>
      {state?.status === "falhou" && <Notice tone="danger">{state.error}</Notice>}
      {state?.sharing && <Notice tone="warning">Você está compartilhando a tela. Cuidado com dado de paciente.</Notice>}
      <div className="call-grid">
        {local && <Video stream={local} muted label="Você" />}
        {state?.peers.map((p) => (
          <div key={p.peerId} className="contents">
            {p.video && <Video stream={p.video} label={p.name || "Participante"} />}
            {p.share && <Video stream={p.share} label={`Tela de ${p.name || "participante"}`} />}
            {p.audio && <Audio stream={p.audio} />}
          </div>
        ))}
      </div>
      <div className="toolbar call-controls">
        <Button
          type="button"
          aria-pressed={!mic}
          onClick={() => {
            local?.getAudioTracks().forEach((t) => (t.enabled = !mic));
            setMic(!mic);
          }}
        >
          {mic ? "Silenciar" : "Ativar microfone"}
        </Button>
        <Button
          type="button"
          aria-pressed={!cam}
          onClick={() => {
            local?.getVideoTracks().forEach((t) => (t.enabled = !cam));
            setCam(!cam);
          }}
        >
          {cam ? "Desligar câmera" : "Ligar câmera"}
        </Button>
        {state?.sharing ? (
          <Button type="button" onClick={() => void session.current?.stopShare()}>
            Parar de compartilhar
          </Button>
        ) : (
          <Button type="button" onClick={share}>
            Compartilhar tela
          </Button>
        )}
        <Button type="button" variant="danger" onClick={leave}>
          Sair da chamada
        </Button>
      </div>
      <ProblemAlert error={error} />
    </div>
  );
}
