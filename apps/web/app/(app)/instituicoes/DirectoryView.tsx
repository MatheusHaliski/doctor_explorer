"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { api } from "@/lib/api";
import { useAsyncEffect } from "@/lib/use-async";
import { UFS } from "@/lib/domain";
import { Button, Field, Loading, Notice, ProblemAlert } from "@/components/ui";

type Institution = { id: string; name: string; type: string; city: string; uf: string; cnes: string; verified: boolean; distanceKm?: number };
type Result = { items: Institution[]; count: number; locationUsed: boolean };
const TYPES: Record<string, string> = { NETWORK: "Rede", HOSPITAL: "Hospital", UNIT: "Unidade", CLINIC: "Clínica" };

/** RF11 · diretório: texto, UF e raio (o raio só vale com o consentimento de localização ligado). */
export function DirectoryView() {
  const router = useRouter();
  const pathname = usePathname();
  const sp = useSearchParams();
  const [q, setQ] = useState(sp.get("q") ?? "");
  const [uf, setUf] = useState(sp.get("uf") ?? "");
  const [radius, setRadius] = useState(sp.get("radiusKm") ?? "");
  const [pos, setPos] = useState<{ lat: number; lng: number } | null>(null);
  const [res, setRes] = useState<Result | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [geoError, setGeoError] = useState<string | null>(null);

  useAsyncEffect(
    async () => {
      const r = await api.get<Result>("/api/v1/institutions", { query: { q: sp.get("q"), uf: sp.get("uf"), radiusKm: sp.get("radiusKm"), lat: sp.get("lat"), lng: sp.get("lng") } });
      setError(null);
      setRes(r);
    },
    setError,
    [sp],
  );

  function search(e: FormEvent) {
    e.preventDefault();
    const qs = new URLSearchParams();
    if (q.trim()) qs.set("q", q.trim());
    if (uf) qs.set("uf", uf);
    if (radius && pos) {
      qs.set("radiusKm", radius);
      qs.set("lat", String(pos.lat));
      qs.set("lng", String(pos.lng));
    }
    router.replace(`${pathname}${qs.toString() ? `?${qs}` : ""}`);
  }

  function locate() {
    setGeoError(null);
    if (!navigator.geolocation) return setGeoError("Este navegador não informa localização.");
    navigator.geolocation.getCurrentPosition(
      (p) => setPos({ lat: p.coords.latitude, lng: p.coords.longitude }),
      () => setGeoError("Não foi possível obter sua localização. Você pode buscar por cidade ou UF."),
      { maximumAge: 300000, timeout: 8000 },
    );
  }

  return (
    <>
      <h1>Instituições</h1>
      <p className="lead">Hospitais, redes e unidades com CNPJ e CNES conferidos. Médicos verificados pedem vínculo; gestores aceitam.</p>
      <form onSubmit={search} noValidate className="card">
        <div className="grid" style={{ gridTemplateColumns: "2fr 1fr 1fr" }}>
          <Field id="q" label="Nome ou cidade">
            {(p) => <input {...p} type="search" value={q} onChange={(e) => setQ(e.target.value)} />}
          </Field>
          <Field id="uf" label="UF">
            {(p) => (
              <select {...p} value={uf} onChange={(e) => setUf(e.target.value)}>
                <option value="">Todas</option>
                {UFS.map((u) => (
                  <option key={u}>{u}</option>
                ))}
              </select>
            )}
          </Field>
          <Field id="radius" label="Raio (km)" help={pos ? "Usando sua localização atual." : "Precisa da sua localização e do consentimento “Localização” ligado."}>
            {(p) => <input {...p} type="number" min={1} max={500} value={radius} onChange={(e) => setRadius(e.target.value)} />}
          </Field>
        </div>
        {geoError && <Notice tone="warning">{geoError}</Notice>}
        {res && !res.locationUsed && sp.get("radiusKm") && (
          <Notice tone="info">
            O raio foi ignorado: ligue o consentimento de <Link href="/privacidade">Localização</Link> para ordenar por distância.
          </Notice>
        )}
        <div className="toolbar">
          <Button type="submit" variant="primary">
            Buscar
          </Button>
          <Button type="button" onClick={locate}>
            {pos ? "Localização obtida" : "Usar minha localização"}
          </Button>
        </div>
      </form>

      <ProblemAlert error={error} />
      {!res ? (
        <Loading />
      ) : (
        <>
          <p className="result-line" aria-live="polite">
            {res.count === 0 ? "Nenhuma instituição encontrada." : `${res.count} ${res.count === 1 ? "instituição" : "instituições"}`}
          </p>
          <div className="grid">
            {res.items.map((i) => (
              <article key={i.id} className="card">
                <h3 className="m-0 text-lg">
                  <Link href={`/instituicoes/${i.id}`} className="no-underline hover:underline" style={{ color: "inherit" }}>
                    {i.name}
                  </Link>
                </h3>
                <p className="m-0 text-sm text-text-3">
                  {TYPES[i.type] ?? i.type} · {i.city}/{i.uf}
                  {i.cnes && ` · CNES ${i.cnes}`}
                  {i.distanceKm !== undefined && ` · ${i.distanceKm} km`}
                </p>
                <p className="m-0 mt-2 text-sm font-bold" style={{ color: i.verified ? "var(--success)" : "var(--warning)" }}>
                  {i.verified ? "✓ CNPJ e CNES conferidos" : "Em análise"}
                </p>
              </article>
            ))}
          </div>
        </>
      )}
    </>
  );
}
