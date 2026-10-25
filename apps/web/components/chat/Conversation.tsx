"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { openFile, safetyCode, sealFile, type ChatDevice, type ConversationInfo, type HistoryItem } from "@doctor-explorer/crypto";
import { api, ApiError } from "@/lib/api";
import { conversationName, deliveryApi, looksLikeIdentifiedPatient, RETENTIONS } from "@/lib/chat-api";
import { formatDateTime } from "@/lib/domain";
import { useSession } from "@/components/session-context";
import { Button, Field, Loading, Notice, ProblemAlert, useToast } from "@/components/ui";
import { saveFile } from "@/components/documents/SignedDocument";

const MAX_FILE = 25 * 1024 * 1024;

/**
 * RF06 · uma conversa. O texto é cifrado e decifrado aqui (MLS); o servidor só ordena e entrega. A cada poucos
 * segundos sincroniza e põe a árvore em dia (aparelho novo de um membro entra, aparelho revogado sai).
 */
export function Conversation({ id, device }: { id: string; device: ChatDevice }) {
  const session = useSession();
  const router = useRouter();
  const toast = useToast();
  const [info, setInfo] = useState<ConversationInfo | null>(null);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [member, setMember] = useState<boolean | null>(null);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [code, setCode] = useState<string | null>(null);
  const [reporting, setReporting] = useState(false);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [reason, setReason] = useState("");
  const fileRef = useRef<HTMLInputElement>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const lastRead = useRef(0);

  const refresh = useCallback(
    async (maintain: boolean) => {
      await device.acceptWelcomes();
      const isMember = await device.isMember(id);
      setMember(isMember);
      if (!isMember) {
        setInfo(await deliveryApi.conversation(id));
        return;
      }
      if (maintain) await device.maintain(id);
      await device.sync(id);
      const h = await device.history(id);
      setHistory(h);
      setInfo(await deliveryApi.conversation(id));
      const last = h.at(-1)?.seq ?? 0;
      if (last > lastRead.current) {
        lastRead.current = last;
        await api.post(`/api/v1/messaging/conversations/${id}/read`, { seq: last });
      }
    },
    [device, id],
  );

  useEffect(() => {
    let alive = true;
    let tick = 0;
    const run = () =>
      refresh(tick++ % 4 === 0).catch((e) => {
        if (alive) setError(e);
      });
    void run();
    const t = setInterval(run, 2500);
    return () => {
      alive = false;
      clearInterval(t);
    };
  }, [refresh]);

  // chaves: scrollIntoView devolve Promise nos Chromium recentes, e o React trataria isso como "limpeza" do efeito
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [history.length]);

  if (!info) return error ? <ProblemAlert error={error} /> : <Loading what="Abrindo a conversa" />;
  const name = conversationName(info, session.userId);
  const nameOf = (userId: string) => (userId === session.userId ? "Você" : (info.members.find((m) => m.userId === userId)?.name ?? "Ex-membro"));
  const me = info.members.find((m) => m.userId === session.userId);
  const patientHint = looksLikeIdentifiedPatient(text) || history.slice(-5).some((h) => h.content.type === "text" && looksLikeIdentifiedPatient(h.content.text));

  async function act(label: string, op: () => Promise<void>) {
    setBusy(label);
    setError(null);
    try {
      await op();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  async function send(e: FormEvent) {
    e.preventDefault();
    const t = text.trim();
    if (!t) return;
    await act("send", async () => {
      await device.send(id, { type: "text", text: t });
      setText("");
      setHistory(await device.history(id));
    });
  }

  async function attach(file: File) {
    if (file.size > MAX_FILE) {
      setError(new ApiError({ status: 422, title: "Anexo grande demais", detail: "Envie arquivos de até 25 MB." }));
      return;
    }
    await act("attach", async () => {
      const sealed = await sealFile(new Uint8Array(await file.arrayBuffer()));
      const up = await api.post<{ id: string; uploadUrl: string; headers: Record<string, string> }>(`/api/v1/messaging/conversations/${id}/attachments`, {
        size: sealed.cipher.length,
      });
      const put = await fetch(up.uploadUrl, { method: "PUT", headers: up.headers, body: sealed.cipher.slice().buffer as ArrayBuffer });
      if (!put.ok) throw new ApiError({ status: put.status, title: "Envio não concluído", detail: "O armazenamento recusou o anexo. Tente de novo." });
      await device.send(id, {
        type: "file",
        attachmentId: up.id,
        name: file.name.slice(0, 200),
        mime: file.type || "application/octet-stream",
        size: file.size,
        key: sealed.key,
        iv: sealed.iv,
        sha256: sealed.sha256,
      });
      setHistory(await device.history(id));
    });
    if (fileRef.current) fileRef.current.value = "";
  }

  async function download(item: HistoryItem) {
    if (item.content.type !== "file") return;
    const f = item.content;
    await act("download", async () => {
      const { url } = await api.get<{ url: string }>(`/api/v1/messaging/conversations/${id}/attachments/${f.attachmentId}`);
      const res = await fetch(url);
      if (!res.ok) throw new ApiError({ status: res.status, title: "Anexo indisponível", detail: "O anexo expirou com a retenção da conversa." });
      const plain = await openFile(new Uint8Array(await res.arrayBuffer()), f);
      saveFile(new Blob([plain.slice().buffer as ArrayBuffer], { type: f.mime }), f.name);
    });
  }

  async function showCode() {
    await act("code", async () => {
      const tree = await device.treeDevices(id);
      const byUser = new Map<string, string[]>();
      for (const d of tree) {
        const owner = d.deviceId === device.deviceId ? session.userId : (await deliveryApi.device(d.deviceId)).userId;
        byUser.set(owner, [...(byUser.get(owner) ?? []), d.signatureKey]);
      }
      setCode(await safetyCode([...byUser].map(([userId, signatureKeys]) => ({ userId, signatureKeys }))));
    });
  }

  async function report() {
    const picked = history.filter((h) => selected.has(h.seq));
    const excerpt = picked
      .map((h) => `[${formatDateTime(h.at)}] ${nameOf(h.senderUserId)}: ${h.content.type === "text" ? h.content.text : `(anexo ${h.content.name})`}`)
      .join("\n");
    const reported = picked.find((h) => !h.own)?.senderUserId;
    await act("report", async () => {
      await api.post(`/api/v1/messaging/conversations/${id}/reports`, { reportedUserId: reported, reason, excerpt });
      setReporting(false);
      setSelected(new Set());
      setReason("");
      toast.show("Denúncia enviada à moderação com as mensagens que você escolheu.");
    });
  }

  return (
    <div className="max-w-3xl chat">
      <p className="result-line">
        <Link href="/mensagens">← Mensagens</Link>
      </p>
      <div className="toolbar">
        <h1 className="mr-auto mb-0">{name}</h1>
        <span className="pill chain-ok" title="MLS (RFC 9420): só os aparelhos dos participantes têm as chaves">
          Criptografada ponta a ponta
        </span>
      </div>
      <p className="text-sm text-text-2">
        {info.kind === "GROUP" ? `${info.members.map((m) => m.name).join(", ")} · ` : ""}
        Época {info.epoch} · {info.devices.length} aparelhos no grupo
      </p>
      <div className="toolbar">
        {member && info.members.length <= 8 && (
          <Button
            type="button"
            size="sm"
            variant="primary"
            loading={busy === "call"}
            onClick={() =>
              void act("call", async () => {
                const c = await api.post<{ call: { id: string } }>("/api/v1/calls", { conversationId: id, deviceId: device.deviceId });
                router.push(`/chamadas/${c.call.id}`);
              })
            }
          >
            Chamada de vídeo
          </Button>
        )}
        {info.kind === "DIRECT" && (
          <Button type="button" size="sm" loading={busy === "code"} onClick={showCode}>
            Código de segurança
          </Button>
        )}
        <Field id="retention" label="Apagar mensagens em">
          {(p) => (
            <select
              {...p}
              value={info.retentionDays}
              onChange={(e) => void act("retention", async () => setInfo(await api.patch<ConversationInfo>(`/api/v1/messaging/conversations/${id}`, { retentionDays: Number(e.target.value) })))}
            >
              {Object.entries(RETENTIONS).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Button type="button" size="sm" aria-pressed={reporting} onClick={() => setReporting((v) => !v)}>
          Denunciar
        </Button>
        {info.kind === "GROUP" && me && (
          <Button
            type="button"
            size="sm"
            variant="danger"
            onClick={() =>
              confirm("Sair do grupo? Seus aparelhos deixam de receber as mensagens.") &&
              void act("leave", async () => {
                await api.delete(`/api/v1/messaging/conversations/${id}/members/${session.userId}`);
                router.replace("/mensagens");
              })
            }
          >
            Sair do grupo
          </Button>
        )}
      </div>
      {code && (
        <Notice tone="info" role="status">
          <strong>Código de segurança:</strong> <span className="mono safety-code">{code}</span>
          <br />
          Compare com o que aparece no aparelho do colega (pessoalmente ou por telefone). Se for igual, ninguém está no meio. Muda quando alguém adiciona ou remove aparelho.
        </Notice>
      )}
      {reporting && (
        <Notice tone="warning">
          Marque as mensagens e explique o motivo. <strong>Você vai encaminhar ao moderador o texto decifrado dessas mensagens</strong>; o servidor não consegue ler a conversa,
          então só você pode fazer isso.
        </Notice>
      )}
      <ProblemAlert error={error} />

      {member === false ? (
        <Notice tone="info" role="status">
          Este aparelho ainda não está no grupo. Outro participante o adiciona automaticamente assim que abrir a conversa; as mensagens começam a partir daí.
        </Notice>
      ) : (
        <ol className="chat-log" aria-label="Mensagens" aria-live="polite">
          {history.length === 0 && <li className="empty text-sm text-text-3">Nenhuma mensagem ainda. Só os aparelhos desta conversa conseguem ler o que for enviado.</li>}
          {history.map((h) => (
            <li key={h.seq} className={`bubble${h.own ? " own" : ""}`}>
              {reporting && (
                <input
                  type="checkbox"
                  aria-label={`Incluir mensagem de ${nameOf(h.senderUserId)} na denúncia`}
                  checked={selected.has(h.seq)}
                  onChange={(e) => {
                    const s = new Set(selected);
                    if (e.target.checked) s.add(h.seq);
                    else s.delete(h.seq);
                    setSelected(s);
                  }}
                />
              )}
              <div>
                <div className="text-xs text-text-3">
                  {nameOf(h.senderUserId)} · {formatDateTime(h.at)}
                </div>
                {h.content.type === "text" ? (
                  <p className="m-0 whitespace-pre-wrap">{h.content.text}</p>
                ) : (
                  <Button type="button" size="sm" loading={busy === "download"} onClick={() => void download(h)}>
                    📎 {h.content.name} ({Math.max(1, Math.round(h.content.size / 1024))} KB)
                  </Button>
                )}
              </div>
            </li>
          ))}
          <div ref={endRef} />
        </ol>
      )}

      {reporting && (
        <div className="card mt-4">
          <Field id="report-reason" label="Motivo" errors={error instanceof ApiError ? error.field("reason") : []}>
            {(p) => <textarea {...p} rows={2} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />}
          </Field>
          <Button type="button" variant="danger" loading={busy === "report"} disabled={selected.size === 0 || reason.trim().length < 10} onClick={report}>
            Encaminhar {selected.size} {selected.size === 1 ? "mensagem" : "mensagens"} à moderação
          </Button>
        </div>
      )}

      {patientHint && (
        <Notice tone="warning">
          Parece haver paciente identificável. O chat não é prontuário: registre o que importa no <Link href="/prontuario/nova">prontuário</Link>.
        </Notice>
      )}

      {member !== false && (
        <form className="composer" onSubmit={send}>
          <label className="sr-only" htmlFor="composer">
            Mensagem
          </label>
          <textarea
            id="composer"
            rows={2}
            maxLength={10000}
            value={text}
            placeholder="Mensagem criptografada"
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                e.currentTarget.form?.requestSubmit();
              }
            }}
          />
          <input ref={fileRef} id="chat-file" className="sr-only media-file" type="file" onChange={(e) => e.target.files?.[0] && void attach(e.target.files[0])} />
          <label className="btn" htmlFor="chat-file" aria-disabled={busy === "attach"}>
            Anexar
          </label>
          <Button type="submit" variant="primary" loading={busy === "send" || busy === "attach"} disabled={!text.trim()}>
            Enviar
          </Button>
        </form>
      )}
      {toast.el}
    </div>
  );
}
