"""Escudos, logos de liga e troféus da Kings League (site oficial kingsleague.pro + CDN kama.football).

- Páginas /en/<liga> do kingsleague.pro (robots.txt respeitado, 1 req/s, cache em coleta/cache/kings/).
- O site é Nuxt: os dados ficam no JSON __NUXT_DATA__ (formato "devalue": índices num array). Dele saem,
  para cada temporada de competição, o logo da competição, a imagem da temporada e metaInformation
  (season_trophy, alternative_logo).
- Escudos: URL do CDN já gravada em kings_times.escudo_png (pesquisa de elencos 2026).
- Saída: assets/kings/escudos/<time_id>.png, assets/kings/ligas/<liga_id>.png, assets/kings/trofeus/<liga_id>.png,
  assets/kings/kings-league.png (PNG transparente, <=256 px) e coleta/intermediario/kings_imagens.json
  (caminhos + URL de origem), lido por montar_banco.py.

Uso: python coleta_kings_imagens.py
"""
from __future__ import annotations

import io
import json
import os
import re
import sqlite3
import sys

import comum

os.environ.setdefault("U2NET_HOME", str(comum.CACHE / "modelos"))
from bs4 import BeautifulSoup  # noqa: E402
from PIL import Image  # noqa: E402

DEST = comum.RAIZ / "assets" / "kings"
CACHE_K = comum.CACHE / "kings"
PAGINA = {"kings-espanha": "espana", "kings-americas": "americas", "kings-italia": "italia",
          "kings-brasil": "brazil", "kings-franca": "france", "kings-alemanha": "germany", "kings-mena": "mena",
          "kings-world-cup-clubs": "world-cup", "kings-world-cup-nations": "kwc-nations"}


def baixar_bin(url):
    """Download binário com cache próprio em coleta/cache/kings/ (cópia do cache geral)."""
    dados = comum.baixar(url, binario=True)
    if dados:
        CACHE_K.mkdir(parents=True, exist_ok=True)
        nome = re.sub(r"[^\w.\-]", "_", url.split("://", 1)[-1])[-150:]
        p = CACHE_K / nome
        if not p.exists():
            p.write_bytes(dados)
    return dados


def nuxt(url):
    h = comum.baixar(url)
    if not h:
        return []
    CACHE_K.mkdir(parents=True, exist_ok=True)
    (CACHE_K / (re.sub(r"[^\w]", "_", url.split("://", 1)[-1]) + ".html")).write_text(h, "utf-8")
    s = BeautifulSoup(h, "lxml").find("script", id="__NUXT_DATA__")
    if not s or not s.string:
        return []
    arr = json.loads(s.string)

    def res(i, prof=0):
        if prof > 6 or not isinstance(i, int) or not (0 <= i < len(arr)):
            return i
        v = arr[i]
        if isinstance(v, dict):
            return {k: res(x, prof + 1) for k, x in v.items()}
        return v

    temporadas = []
    for o in arr:
        if isinstance(o, dict) and "competitionId" in o and "metaInformation" in o:
            t = {k: res(v) for k, v in o.items()}
            temporadas.append(t)
    return temporadas


def png_transparente(dados, destino, fundo_solido_remove=True):
    """Converte para PNG RGBA <=256 px; remove o fundo com rembg só se a imagem não tiver transparência
    e as bordas forem de uma cor só (fundo sólido)."""
    if dados[:5] == b"<?xml" or b"<svg" in dados[:300]:
        return False   # SVG: sem conversor local confiável; usa outra imagem
    img = Image.open(io.BytesIO(dados)).convert("RGBA")
    a = img.getchannel("A")
    if a.getextrema()[0] == 255 and fundo_solido_remove:
        w, h = img.size
        borda = [img.getpixel((x, y))[:3] for x in (0, w - 1) for y in range(0, h, max(1, h // 20))] + \
                [img.getpixel((x, y))[:3] for y in (0, h - 1) for x in range(0, w, max(1, w // 20))]
        ref = borda[0]
        if all(sum(abs(p[i] - ref[i]) for i in range(3)) < 30 for p in borda):
            from rembg import new_session, remove
            global _SESSAO
            if "_SESSAO" not in globals():
                _SESSAO = new_session("u2net")
            img = remove(img, session=_SESSAO)
    bbox = img.getchannel("A").point(lambda v: 255 if v > 10 else 0).getbbox()
    if bbox:
        img = img.crop(bbox)
    img.thumbnail((256, 256), Image.LANCZOS)
    destino.parent.mkdir(parents=True, exist_ok=True)
    img.save(destino, optimize=True)
    return True


def main():
    con = sqlite3.connect(f"file:{comum.DADOS / 'mundo.sqlite'}?mode=ro", uri=True)
    times = con.execute("SELECT id, liga_id, escudo_png FROM kings_times").fetchall()
    con.close()
    # depois da 1ª montagem o banco guarda o caminho local: as URLs antigas ficam no JSON anterior
    out = comum.ler_json("kings_imagens.json", None) or {"times": {}, "ligas": {}, "trofeus": {}, "geral": None}
    for tid, _liga, url in times:
        if not url or not url.startswith("http"):
            url = (out["times"].get(tid) or {}).get("url")
        if not url:
            continue
        d = baixar_bin(url)
        if d and png_transparente(d, DEST / "escudos" / f"{tid}.png"):
            out["times"][tid] = {"png": f"assets/kings/escudos/{tid}.png", "url": url}
    print("escudos:", len(out["times"]), "de", len(times), flush=True)
    for liga, slug in PAGINA.items():
        pag = f"https://kingsleague.pro/en/{slug}"
        temps = nuxt(pag)
        # liga principal: nome sem 'Cup'/'Queens'; temporada atual primeiro
        def peso(t):
            nome = ((t.get("competition") or {}).get("name") or t.get("name") or "")
            ruim = bool(re.search(r"cup|queens|copa", nome, re.I)) and "world" not in slug and "kwc" not in slug
            return (ruim, not t.get("isCurrent"))
        temps.sort(key=peso)
        logo = troféu = None
        for t in temps:
            comp = t.get("competition") or {}
            mi = t.get("metaInformation") or {}
            cands_logo = [(comp.get("image") or {}).get("url") if isinstance(comp.get("image"), dict) else None,
                          (t.get("image") or {}).get("url") if isinstance(t.get("image"), dict) else None,
                          mi.get("alternative_logo") if isinstance(mi, dict) else None]
            for u in cands_logo:
                if not logo and isinstance(u, str) and u.startswith("http") and not u.endswith(".svg"):
                    d = baixar_bin(u)
                    if d and png_transparente(d, DEST / "ligas" / f"{liga}.png"):
                        logo = u
            u = mi.get("season_trophy") if isinstance(mi, dict) else None
            if isinstance(u, str) and "kings-cup-trophy" in u and "copa" not in out["trofeus"]:
                d = baixar_bin(u)
                if d and png_transparente(d, DEST / "trofeus" / "kings-cup.png"):
                    out["trofeus"]["copa"] = {"png": "assets/kings/trofeus/kings-cup.png", "url": u, "pagina": pag,
                                              "obs": "troféu da Kings Cup (copa de cada país)"}
            if isinstance(u, str) and "cup-trophy" in u and "world" not in slug and "kwc" not in slug:
                continue   # troféu da copa, não da liga
            if not troféu and isinstance(u, str) and u.startswith("http"):
                d = baixar_bin(u)
                if d and png_transparente(d, DEST / "trofeus" / f"{liga}.png"):
                    troféu = u
        if logo:
            out["ligas"][liga] = {"png": f"assets/kings/ligas/{liga}.png", "url": logo, "pagina": pag}
        if troféu:
            out["trofeus"][liga] = {"png": f"assets/kings/trofeus/{liga}.png", "url": troféu, "pagina": pag}
        print(f"{liga}: {len(temps)} temporadas no payload; logo={'ok' if logo else '-'} troféu={'ok' if troféu else '-'}", flush=True)
    # logo geral: logo da competição da Kings League Spain (a original), senão o primeiro logo
    ger = out["ligas"].get("kings-espanha") or next(iter(out["ligas"].values()), None)
    if ger:
        d = baixar_bin(ger["url"])
        if d and png_transparente(d, DEST / "kings-league.png"):
            out["geral"] = {"png": "assets/kings/kings-league.png", "url": ger["url"]}
    comum.salvar_json("kings_imagens.json", out)
    print("ligas com logo:", len(out["ligas"]), "troféus:", len(out["trofeus"]))


if __name__ == "__main__":
    sys.exit(main())
