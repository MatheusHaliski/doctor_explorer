#!/usr/bin/env python3
"""Regenera apps/<app>/src/main/resources/schema.sql como a concatenação exata das migrações Flyway.

Uso: python3 scripts/gen_schema.py [core-api]
Regra (verificada por SchemaConsistencyTest): schema.sql = cabeçalho fixo + migrações V1..Vn em ordem
numérica, cada arquivo verbatim. Como toda migração começa com linha em branco, a concatenação lê bem.
"""
import re
import sys
from pathlib import Path

HEADER = "-- GERADO por scripts/gen_schema.py a partir de db/migration/V*.sql. Não edite à mão.\n"


def version(p: Path) -> tuple[int, ...]:
    m = re.match(r"V(\d+(?:_\d+)*)__", p.name)
    return tuple(int(x) for x in m.group(1).split("_"))


def main() -> int:
    app = sys.argv[1] if len(sys.argv) > 1 else "core-api"
    res = Path(__file__).resolve().parent.parent / "apps" / app / "src" / "main" / "resources"
    migrations = sorted((res / "db" / "migration").glob("V*__*.sql"), key=version)
    out = HEADER + "".join(p.read_text(encoding="utf-8") for p in migrations)
    (res / "schema.sql").write_text(out, encoding="utf-8")
    print(f"{len(migrations)} migrações → {res / 'schema.sql'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
