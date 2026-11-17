"""Bandeiras dos países: assets/bandeiras/<ISO3>.png a partir do flagcdn.com (domínio público).
Usa os países do mod Brasfoot (intermediario/mod_paises.json). Cache em coleta/cache/flagcdn.com.
"""
from __future__ import annotations

import io

import pycountry
from PIL import Image

import comum

SAIDA = comum.RAIZ / "assets" / "bandeiras"
SAIDA.mkdir(parents=True, exist_ok=True)
ESPECIAIS = {"ENG": "gb-eng", "SCO": "gb-sct", "WAL": "gb-wls", "NIR": "gb-nir", "XKX": "xk"}


def iso2(i3: str) -> str | None:
    if i3 in ESPECIAIS:
        return ESPECIAIS[i3]
    c = pycountry.countries.get(alpha_3=i3)
    return c.alpha_2.lower() if c else None


def main():
    paises = comum.ler_json("mod_paises.json", {})
    ok = 0
    for v in paises.values():
        i3 = v.get("iso")
        if not i3 or (SAIDA / f"{i3}.png").exists():
            ok += 1 if i3 else 0
            continue
        c2 = iso2(i3)
        if not c2:
            print("sem iso2", i3)
            continue
        b = comum.baixar(f"https://flagcdn.com/w320/{c2}.png", binario=True)
        if not b:
            print("falhou", i3, c2)
            continue
        Image.open(io.BytesIO(b)).convert("RGBA").save(SAIDA / f"{i3}.png")
        ok += 1
    print("bandeiras:", ok)


if __name__ == "__main__":
    main()
