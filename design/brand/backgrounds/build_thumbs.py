#!/usr/bin/env python3
"""Copia as artes de fundo para o web e gera as miniaturas da tela de Aparência.

Uso: python3 design/brand/backgrounds/build_thumbs.py
Entrada: design/brand/backgrounds/*.webp (originais). Saída: apps/web/public/backgrounds/<nome>.webp (cópia
fiel) e apps/web/public/backgrounds/thumbs/<nome>.webp (360×240, recorte central, qualidade 80).
"""
import shutil
from pathlib import Path

from PIL import Image, ImageOps

SRC = Path(__file__).resolve().parent
ROOT = SRC.parent.parent.parent
OUT = ROOT / "apps" / "web" / "public" / "backgrounds"


def main() -> None:
    (OUT / "thumbs").mkdir(parents=True, exist_ok=True)
    for art in sorted(SRC.glob("*.webp")):
        shutil.copyfile(art, OUT / art.name)
        thumb = ImageOps.fit(Image.open(art).convert("RGB"), (360, 240), Image.LANCZOS)
        thumb.save(OUT / "thumbs" / art.name, "WEBP", quality=80, method=6)
        print(f"{art.name}: cópia + miniatura")


if __name__ == "__main__":
    main()
