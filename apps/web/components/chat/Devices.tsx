"use client";

import { useState } from "react";
import type { ChatDevice, DeviceInfo } from "@doctor-explorer/crypto";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/domain";
import { useAsyncEffect } from "@/lib/use-async";
import { Button, Loading, Notice, ProblemAlert, useToast } from "@/components/ui";

const STATES: Record<DeviceInfo["state"], string> = { PENDING: "Aguardando aprovação", APPROVED: "Confiável", REVOKED: "Desconectado" };

/** RF06 critério 3 · aparelhos da conta: aprovar um novo (assinado por este) e desconectar um perdido. */
export function Devices({ device, onChange }: { device: ChatDevice; onChange: () => Promise<void> }) {
  const toast = useToast();
  const [items, setItems] = useState<(DeviceInfo & { lastSeenAt?: string | null; createdAt?: string })[] | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);

  const load = async () => setItems((await api.get<{ items: (DeviceInfo & { lastSeenAt?: string | null; createdAt?: string })[] }>("/api/v1/messaging/devices")).items);
  useAsyncEffect(load, setError, [device]);

  async function act(id: string, op: () => Promise<void>) {
    setBusy(id);
    setError(null);
    try {
      await op();
      await load();
      await onChange();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(null);
    }
  }

  if (!items) return error ? <ProblemAlert error={error} /> : <Loading what="Carregando aparelhos" />;
  return (
    <div className="max-w-3xl">
      <h1>Aparelhos</h1>
      <p className="lead">Cada aparelho tem as próprias chaves. Aprove só o que você reconhece: ele passa a receber as mensagens novas das suas conversas.</p>
      <ProblemAlert error={error} />
      <ul className="feed" aria-label="Seus aparelhos">
        {items.map((d) => (
          <li key={d.id} className="card">
            <div className="toolbar">
              <strong className="mr-auto">
                {d.name}
                {d.id === device.deviceId ? " (este)" : ""}
              </strong>
              <span className="text-sm text-text-2">{STATES[d.state]}</span>
            </div>
            <div className="text-xs text-text-3">
              {d.createdAt ? `ativado em ${formatDateTime(d.createdAt)}` : ""}
              {d.lastSeenAt ? ` · visto ${formatDateTime(d.lastSeenAt)}` : ""}
              <span className="mono"> · chave {d.signatureKey.slice(0, 12)}…</span>
            </div>
            <div className="toolbar mt-2">
              {d.state === "PENDING" && (
                <Button
                  type="button"
                  size="sm"
                  variant="primary"
                  loading={busy === d.id}
                  onClick={() =>
                    void act(d.id, async () => {
                      const signature = await device.approvalSignature(d.id, d.signatureKey);
                      await api.post(`/api/v1/messaging/devices/${d.id}/approve`, { approverDeviceId: device.deviceId, signature });
                      toast.show(`${d.name} aprovado. Ele entra nas conversas assim que alguém as abrir.`);
                    })
                  }
                >
                  Aprovar
                </Button>
              )}
              {d.state !== "REVOKED" && (
                <Button
                  type="button"
                  size="sm"
                  variant="danger"
                  loading={busy === "x" + d.id}
                  onClick={() =>
                    confirm(`Desconectar ${d.name}? Ele sai de todas as conversas e as chaves dele deixam de valer.`) &&
                    void act("x" + d.id, async () => {
                      await api.delete(`/api/v1/messaging/devices/${d.id}`);
                    })
                  }
                >
                  Desconectar
                </Button>
              )}
            </div>
          </li>
        ))}
      </ul>
      <Notice tone="info">Perdeu um aparelho? Desconecte aqui. Os outros membros o retiram dos grupos automaticamente na próxima abertura de cada conversa.</Notice>
      {toast.el}
    </div>
  );
}
