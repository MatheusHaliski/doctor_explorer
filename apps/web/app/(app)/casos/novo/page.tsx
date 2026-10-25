"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { api, ApiError } from "@/lib/api";
import { KINDS, SPECIALTIES } from "@/lib/domain";
import { Button, Field, Notice, ProblemAlert } from "@/components/ui";
import { MediaAttach, type MediaView } from "@/components/media/MediaAttach";

type Group = { specialtyCode: string; name: string; membership: string };

const SECTIONS: Array<[keyof Sections, string, string]> = [
  ["complaint", "Queixa e contexto", "Idade em faixa (ex.: 60 anos), sexo, comorbidades relevantes. Sem nome, data completa ou número de documento."],
  ["history", "História", "Evolução, medicações em uso, antecedentes."],
  ["exam", "Exame físico", "Achados objetivos."],
  ["workup", "Exames complementares", "Resultados relevantes. Imagens vão no bloco abaixo, com anonimização."],
  ["question", "Pergunta para os colegas", "O que você quer discutir? Isso vira o resumo no feed."],
];
type Sections = { complaint: string; history: string; exam: string; workup: string; question: string };

/** RF04 · publicar caso: bloqueio por identificador devolve 422 com todos os trechos e a correção sugerida. RF05 · imagens anonimizadas e aprovadas. */
export default function NewCasePage() {
  const router = useRouter();
  const [groups, setGroups] = useState<Group[]>([]);
  const [form, setForm] = useState({ specialtyCode: "", kind: "CASE", audience: "GROUP", title: "", tags: "" });
  const [sections, setSections] = useState<Sections>({ complaint: "", history: "", exam: "", workup: "", question: "" });
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [media, setMedia] = useState<MediaView[]>([]);

  useEffect(() => {
    api
      .get<{ groups: Group[] }>("/api/v1/groups")
      .then((g) => {
        const mine = g.groups.filter((x) => x.membership === "ACTIVE");
        setGroups(mine);
        if (mine[0]) setForm((f) => ({ ...f, specialtyCode: f.specialtyCode || mine[0]!.specialtyCode }));
      })
      .catch(setError);
  }, []);

  const errs = (f: string) => (error instanceof ApiError ? error.field(f) : []);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const created = await api.post<{ id: string }>("/api/v1/posts", {
        ...form,
        ...sections,
        tags: form.tags.split(/[,\s]+/).map((t) => t.replace(/^#/, "")).filter(Boolean),
        mediaIds: media.map((m) => m.id),
      });
      router.push(`/casos/${created.id}`);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate className="max-w-3xl">
      <h1>Publicar caso</h1>
      <p className="lead">Tudo que identifique o paciente é bloqueado antes de publicar: nome, CPF, CNS, telefone, e-mail, data completa, número de prontuário.</p>
      {groups.length === 0 && (
        <Notice tone="warning">
          Você ainda não está em nenhum grupo ativo. <Link href="/grupos">Entre no grupo da sua especialidade</Link> para publicar.
        </Notice>
      )}
      <div className="grid" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))" }}>
        <Field id="specialtyCode" label="Grupo / especialidade" errors={errs("specialtyCode")}>
          {(p) => (
            <select {...p} value={form.specialtyCode} onChange={(e) => setForm({ ...form, specialtyCode: e.target.value })}>
              {groups.map((g) => (
                <option key={g.specialtyCode} value={g.specialtyCode}>
                  {SPECIALTIES[g.specialtyCode] ?? g.name}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field id="kind" label="Tipo" errors={errs("kind")}>
          {(p) => (
            <select {...p} value={form.kind} onChange={(e) => setForm({ ...form, kind: e.target.value })}>
              {["CASE", "OPINION_REQUEST", "EDUCATION"].map((k) => (
                <option key={k} value={k}>
                  {KINDS[k]}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field id="audience" label="Quem vê" errors={errs("audience")} help="Grupo: só membros. Rede: toda a rede verificada.">
          {(p) => (
            <select {...p} value={form.audience} onChange={(e) => setForm({ ...form, audience: e.target.value })}>
              <option value="GROUP">Só o grupo</option>
              <option value="NETWORK">Rede verificada</option>
            </select>
          )}
        </Field>
      </div>
      <Field id="title" label="Título" errors={errs("title")} help="Curto e sem identificar ninguém.">
        {(p) => <input {...p} type="text" maxLength={160} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />}
      </Field>
      {SECTIONS.map(([key, label, help]) => (
        <Field key={key} id={key} label={label} help={help} errors={errs(key)}>
          {(p) => <textarea {...p} value={sections[key]} onChange={(e) => setSections({ ...sections, [key]: e.target.value })} />}
        </Field>
      ))}
      <MediaAttach approved={media} onChange={setMedia} dermatologyGroup={form.specialtyCode === "DERM"} />
      {errs("mediaIds").map((m) => (
        <Notice key={m} tone="danger">
          {m}
        </Notice>
      ))}
      <Field id="tags" label="Tags" help="Separe por vírgula ou espaço. Ex.: sepse, vasopressor">
        {(p) => <input {...p} type="text" value={form.tags} onChange={(e) => setForm({ ...form, tags: e.target.value })} />}
      </Field>
      <ProblemAlert error={error} />
      <div className="toolbar">
        <Button type="submit" variant="primary" loading={busy} disabled={groups.length === 0}>
          Publicar no grupo
        </Button>
        <Link className="btn" href="/feed">
          Cancelar
        </Link>
      </div>
    </form>
  );
}
