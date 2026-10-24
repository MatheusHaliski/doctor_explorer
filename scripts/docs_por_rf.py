#!/usr/bin/env python3
"""Docs por RF: exige doc atualizado quando o código de um RF muda e renderiza todo Mermaid.

Uso: python3 scripts/docs_por_rf.py --base <sha> [--head HEAD] [--mmdc <caminho>] [--skip-render]

1. Lê o cabeçalho YAML simples de cada docs/rf/RFnn-*.md (chaves `rf`, `paths`).
2. Para cada arquivo mudado entre base e head que case com algum glob de `paths`, exige que o doc
   do RF também esteja entre os arquivos mudados. Falha listando o que faltou.
3. Renderiza cada bloco ```mermaid dos .md em docs/ com mermaid-cli (falha se algum não compilar).
"""
import argparse
import fnmatch
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RF_DIR = ROOT / "docs" / "rf"


def front_matter(md: Path) -> dict:
    text = md.read_text(encoding="utf-8")
    m = re.match(r"---\n(.*?)\n---\n", text, re.S)
    if not m:
        return {}
    data, key = {}, None
    for line in m.group(1).splitlines():
        if re.match(r"^\s+-\s", line) and key:
            data.setdefault(key, []).append(line.split("-", 1)[1].strip())
        elif ":" in line:
            key, _, value = line.partition(":")
            key, value = key.strip(), value.strip()
            if value.startswith("[") and value.endswith("]"):
                data[key] = [v.strip() for v in value[1:-1].split(",") if v.strip()]
            elif value:
                data[key] = value
            else:
                data[key] = []
    return data


def glob_match(path: str, pattern: str) -> bool:
    """fnmatch com `**` cobrindo zero ou mais diretórios."""
    regex = re.escape(pattern).replace(r"\*\*/", "(?:.*/)?").replace(r"\*\*", ".*").replace(r"\*", "[^/]*")
    return re.fullmatch(regex, path) is not None


def changed_files(base: str, head: str) -> set[str]:
    out = subprocess.run(["git", "diff", "--name-only", f"{base}...{head}"], cwd=ROOT, capture_output=True, text=True)
    if out.returncode != 0:  # base sem merge-base (ex.: primeiro push): compara direto
        out = subprocess.run(["git", "diff", "--name-only", base, head], cwd=ROOT, capture_output=True, text=True, check=True)
    return {line.strip() for line in out.stdout.splitlines() if line.strip()}


def check_docs(base: str, head: str) -> list[str]:
    changed = changed_files(base, head)
    problems = []
    for md in sorted(RF_DIR.glob("RF*.md")):
        fm = front_matter(md)
        paths = fm.get("paths", [])
        if isinstance(paths, str):
            paths = [paths]
        hits = sorted(f for f in changed for p in paths if glob_match(f, p))
        if hits and str(md.relative_to(ROOT)) not in changed:
            problems.append(f"{fm.get('rf', md.stem)}: mudou {', '.join(hits[:5])}{' …' if len(hits) > 5 else ''} mas {md.relative_to(ROOT)} não foi atualizado no mesmo commit")
    return problems


def render_mermaid(mmdc: str) -> list[str]:
    cfg = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False)
    json.dump({"args": ["--no-sandbox"]}, cfg)
    cfg.close()
    problems, total = [], 0
    for md in sorted((ROOT / "docs").rglob("*.md")):
        for i, block in enumerate(re.findall(r"```mermaid\n(.*?)```", md.read_text(encoding="utf-8"), re.S), 1):
            total += 1
            src = Path(tempfile.mkdtemp()) / "d.mmd"
            src.write_text(block, encoding="utf-8")
            r = subprocess.run([mmdc, "-p", cfg.name, "-i", str(src), "-o", str(src.with_suffix(".svg")), "-q"], capture_output=True, text=True)
            if r.returncode != 0:
                err = next((l for l in (r.stderr or r.stdout).splitlines() if "Error" in l or "Expecting" in l), "erro de renderização")
                problems.append(f"{md.relative_to(ROOT)} bloco {i}: {err[:200]}")
    print(f"{total} diagramas Mermaid renderizados, {len(problems)} com erro")
    return problems


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True)
    ap.add_argument("--head", default="HEAD")
    ap.add_argument("--mmdc", default="mmdc")
    ap.add_argument("--skip-render", action="store_true")
    a = ap.parse_args()

    problems = check_docs(a.base, a.head)
    if not a.skip_render:
        problems += render_mermaid(a.mmdc)
    for p in problems:
        print("✗", p)
    print("Docs por RF:", "ok" if not problems else f"{len(problems)} problema(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
