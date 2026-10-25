"use client";

import Link from "next/link";
import { useState, type FormEvent, type ReactNode } from "react";
import type { ChatDevice } from "@doctor-explorer/crypto";
import { useSession } from "@/components/session-context";
import { Button, Field, Loading, Notice, ProblemAlert } from "@/components/ui";
import { useChatDevice } from "@/lib/use-chat-device";

function defaultName(): string {
  if (typeof navigator === "undefined") return "Navegador";
  const ua = navigator.userAgent;
  const browser = /Firefox/.test(ua) ? "Firefox" : /Edg/.test(ua) ? "Edge" : /Chrome/.test(ua) ? "Chrome" : /Safari/.test(ua) ? "Safari" : "Navegador";
  const os = /Android/.test(ua) ? "Android" : /iPhone|iPad/.test(ua) ? "iOS" : /Mac/.test(ua) ? "Mac" : /Windows/.test(ua) ? "Windows" : /Linux/.test(ua) ? "Linux" : "";
  return os ? `${browser} no ${os}` : browser;
}

/**
 * RF06 critério 3 · cada aparelho tem as próprias chaves. Antes de conversar, este navegador vira um aparelho: o
 * primeiro da conta entra direto e mostra a frase de recuperação; os seguintes esperam aprovação de um aparelho
 * confiável ou a frase.
 */
export function DeviceGate({ children }: { children: (device: ChatDevice, refresh: () => Promise<void>) => ReactNode }) {
  const session = useSession();
  const { status, register, recover, forget, refresh } = useChatDevice(session.userId);
  const [name, setName] = useState(defaultName);
  const [phrase, setPhrase] = useState<string | null>(null);
  const [noted, setNoted] = useState(false);
  const [typed, setTyped] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function run(op: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await op();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  if (phrase && !noted) {
    return (
      <section className="card max-w-2xl" aria-labelledby="phrase-title">
        <h2 id="phrase-title" className="mt-0">
          Sua frase de recuperação
        </h2>
        <p>Anote em papel ou no seu gerenciador de senhas. Ela aprova um aparelho novo quando você não tiver outro à mão. Não mostramos de novo e não temos cópia.</p>
        <p className="doc-code" aria-label="Frase de recuperação">
          {phrase}
        </p>
        <Notice tone="warning">Ela não recupera conversas antigas: num aparelho novo, as mensagens começam a partir da entrada dele.</Notice>
        <Button type="button" variant="primary" onClick={() => setNoted(true)}>
          Anotei a frase
        </Button>
      </section>
    );
  }

  switch (status.kind) {
    case "loading":
      return <Loading what="Abrindo as chaves deste aparelho" />;
    case "unsupported":
      return <Notice tone="danger">Este navegador não guarda chaves com segurança (IndexedDB e WebCrypto). Use um navegador atual.</Notice>;
    case "error":
      return <ProblemAlert error={status.error} />;
    case "none":
      return (
        <form
          className="card max-w-2xl"
          aria-labelledby="enable-title"
          noValidate
          onSubmit={(e: FormEvent) => {
            e.preventDefault();
            void run(async () => setPhrase(await register(name.trim(), status.firstDevice)));
          }}
        >
          <h2 id="enable-title" className="mt-0">
            Ativar mensagens neste aparelho
          </h2>
          <p className="text-sm text-text-2">
            As conversas são criptografadas ponta a ponta (MLS, RFC 9420). As chaves ficam só neste navegador; o servidor guarda apenas texto cifrado e não consegue ler.
            {status.firstDevice ? " Este é seu primeiro aparelho: vamos gerar uma frase de recuperação." : " Depois, aprove este aparelho em outro já confiável ou com a frase de recuperação."}
          </p>
          <Field id="device-name" label="Nome do aparelho" help="Para você reconhecer na lista (ex.: Notebook do consultório).">
            {(p) => <input {...p} className="input" maxLength={60} value={name} onChange={(e) => setName(e.target.value)} />}
          </Field>
          <ProblemAlert error={error} />
          <Button type="submit" variant="primary" loading={busy} disabled={!name.trim()}>
            Ativar neste aparelho
          </Button>
        </form>
      );
    case "pending":
      return (
        <section className="card max-w-2xl" aria-labelledby="pending-title">
          <h2 id="pending-title" className="mt-0">
            Aprove este aparelho
          </h2>
          <p>
            Abra <strong>Mensagens → Aparelhos</strong> num aparelho já confiável e toque em “Aprovar” ao lado de <strong>{status.device.record.name}</strong>. Ou use a
            frase de recuperação:
          </p>
          <form
            noValidate
            onSubmit={(e: FormEvent) => {
              e.preventDefault();
              void run(() => recover(status.device, typed));
            }}
          >
            <Field id="recovery" label="Frase de recuperação" help="8 grupos de 4 letras e números.">
              {(p) => <input {...p} className="input mono" autoComplete="off" spellCheck={false} value={typed} onChange={(e) => setTyped(e.target.value)} />}
            </Field>
            <ProblemAlert error={error} />
            <div className="toolbar">
              <Button type="submit" variant="primary" loading={busy} disabled={typed.replace(/[\s-]/g, "").length < 32}>
                Aprovar com a frase
              </Button>
              <Button type="button" onClick={() => void run(refresh)}>
                Já aprovei no outro aparelho
              </Button>
            </div>
          </form>
        </section>
      );
    case "revoked":
      return (
        <section className="card max-w-2xl">
          <Notice tone="danger">Este aparelho foi desconectado das mensagens. As chaves dele não servem mais.</Notice>
          <ProblemAlert error={error} />
          <Button type="button" loading={busy} onClick={() => void run(() => forget(status.device))}>
            Apagar as chaves daqui e ativar de novo
          </Button>
        </section>
      );
    case "ready":
      return (
        <>
          {status.others.some((d) => d.state === "PENDING") && (
            <Notice tone="info">
              Há aparelho seu esperando aprovação. <Link href="/mensagens/aparelhos">Revisar aparelhos</Link>
            </Notice>
          )}
          {children(status.device, refresh)}
        </>
      );
  }
}
