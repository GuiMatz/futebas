"""Fotos sem fundo dos jogadores a partir do retrato do Transfermarkt (image_url do
transfermarkt-datasets), para USO PESSOAL. Fundo removido localmente com rembg (u2net).

- Prioridade: Série A do Brasil, as 5 grandes ligas europeias, depois por valor de mercado.
- Download pelo comum.baixar (cache em coleta/cache/<domínio>, 1 req/s, robots.txt, UA genérico).
- Saída: coleta/cache/fotos_tm/<tm_id>.png (máx. 256 px de altura). montar_banco.py copia para
  assets/fotos/<jogador_id>.png.
- Retomável: coleta/intermediario/fotos_tm.json guarda o estado de cada tm_id.

Uso: python fotos_tm.py [quantidade=5000]   (FOTOS_TODAS=1 para seguir com as demais ligas, por valor)
"""
from __future__ import annotations

import io
import json
import os
import sys

import comum

os.environ.setdefault("U2NET_HOME", str(comum.CACHE / "modelos"))

import pandas as pd  # noqa: E402
from PIL import Image  # noqa: E402

SAIDA = comum.CACHE / "fotos_tm"
SAIDA.mkdir(parents=True, exist_ok=True)
TM = comum.CACHE / "transfermarkt-datasets" / "raw"
ALTURA_MAX = 256
PRIORIDADE = ["BRA1", "GB1", "ES1", "IT1", "L1", "FR1"]


def fila() -> list[tuple[int, str]]:
    p = pd.read_csv(TM / "players.csv.gz", usecols=["player_id", "last_season", "image_url",
                                                    "current_club_domestic_competition_id", "market_value_in_eur"])
    p = p[(p.last_season >= 2025) & p.image_url.notna() & ~p.image_url.str.contains("default", na=True)]
    p = p.assign(pri=p.current_club_domestic_competition_id.map(lambda c: PRIORIDADE.index(c) if c in PRIORIDADE else 9),
                 v=p.market_value_in_eur.fillna(0))
    p = p.sort_values(["pri", "v"], ascending=[True, False])
    if os.environ.get("FOTOS_TODAS") != "1":   # padrão: só Série A + 5 grandes ligas europeias
        return list(zip(p[p.pri < 9].player_id.astype(int), p[p.pri < 9].image_url))
    extra = comum.ler_json("fotos_extra.json", []) or []   # [tm_id, url] vindos do montar_banco (mod)
    out = list(zip(p.player_id.astype(int), p.image_url))
    vistos = {t for t, _ in out}
    out += [(int(t), u) for t, u in extra if int(t) not in vistos]
    return out


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 5000
    from rembg import new_session, remove
    sess = new_session("u2net")
    est = comum.ler_json("fotos_tm.json", {}) or {}
    feitos = 0
    for tm, url in fila():
        k = str(tm)
        if k in est:
            continue
        if (SAIDA / f"{tm}.png").exists():
            est[k] = "ok"
            continue
        if feitos >= n:
            break
        b = comum.baixar(url.replace("/header/", "/big/"), binario=True) or comum.baixar(url, binario=True)
        if not b:
            est[k] = "sem"
            continue
        try:
            img = Image.open(io.BytesIO(b)).convert("RGBA")
            out = remove(img, session=sess)
            bbox = out.getbbox()
            if bbox:
                out = out.crop(bbox)
            if out.height > ALTURA_MAX:
                out = out.resize((max(1, round(out.width * ALTURA_MAX / out.height)), ALTURA_MAX), Image.LANCZOS)
            out.save(SAIDA / f"{tm}.png", optimize=True)
            est[k] = "ok"
            feitos += 1
        except Exception as e:  # noqa: BLE001
            est[k] = "erro"
            print("  erro", tm, e)
        if feitos % 50 == 0:
            comum.salvar_json("fotos_tm.json", est)
            print(f"  {feitos} fotos", flush=True)
    comum.salvar_json("fotos_tm.json", est)
    print("fotos ok no total:", sum(1 for v in est.values() if v == "ok"))


if __name__ == "__main__":
    main()
