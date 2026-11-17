"""Fase 8: fotos sem fundo dos jogadores (PLANO.md seção 9).

- Só usa a imagem P18 do próprio item do Wikidata do jogador (identidade garantida pela
  entidade: não há busca por nome).
- Página do arquivo no Commons (/wiki/File:..., permitido pelo robots.txt) -> licença
  (licensetpl_short), autor e link do original.
- Baixa a miniatura de 500px (upload.wikimedia.org), remove o fundo localmente com rembg
  (modelo u2net, baixado do GitHub oficial do rembg na primeira execução, guardado em
  coleta/cache/modelos), recorta e salva assets/fotos/<jogador_id>.png (máx. 256 px de altura).
- Retomável: o manifesto coleta/intermediario/fotos.json guarda o que já foi feito.

Uso: python fotos.py [quantidade=300]
Depois rode de novo montar_banco.py para gravar foto_png/foto_fonte_url/foto_licenca.
"""
from __future__ import annotations

import io
import os
import re
import sqlite3
import sys
import urllib.parse

import comum

os.environ.setdefault("U2NET_HOME", str(comum.CACHE / "modelos"))

from bs4 import BeautifulSoup  # noqa: E402
from PIL import Image  # noqa: E402

FOTOS = comum.RAIZ / "assets" / "fotos"
FOTOS.mkdir(parents=True, exist_ok=True)
ALTURA_MAX = 256


def nome_arquivo(img_url: str) -> str:
    return urllib.parse.unquote(img_url.rsplit("/", 1)[-1]).replace("_", " ")


def info_commons(arquivo: str):
    url = "https://commons.wikimedia.org/wiki/File:" + urllib.parse.quote(arquivo.replace(" ", "_"))
    h = comum.baixar(url)
    if not h:
        return None
    s = BeautifulSoup(h, "html.parser")
    lic = s.select_one(".licensetpl_short")
    licenca = lic.get_text(" ", strip=True) if lic else None
    if not licenca:
        if s.select_one(".licensetpl") or "Public domain" in h:
            licenca = "Public domain" if "Public domain" in h else None
    aut = s.select_one(".licensetpl_attr") or s.select_one("#fileinfotpl_aut + td")
    autor = aut.get_text(" ", strip=True)[:200] if aut else None
    orig = s.select_one(".fullMedia a[href]") or s.select_one("a.internal[href]")
    original = orig["href"] if orig else None
    if original and original.startswith("//"):
        original = "https:" + original
    if original:
        original = original.split("?", 1)[0]
    return {"pagina": url, "licenca": licenca, "autor": autor, "original": original}


def miniatura(original: str, largura=500) -> str | None:
    m = re.match(r"https://upload\.wikimedia\.org/wikipedia/commons/(\w/\w\w)/(.+)$", original or "")
    if not m:
        return None
    nome = m.group(2)
    ext = nome.rsplit(".", 1)[-1].lower()
    sufixo = ".png" if ext in ("svg", "tif", "tiff") else ""
    return f"https://upload.wikimedia.org/wikipedia/commons/thumb/{m.group(1)}/{nome}/{largura}px-{nome}{sufixo}"


def main(qtd: int):
    from rembg import new_session, remove
    sessao = new_session("u2net")
    wd = comum.ler_json("jogadores_wd.json", {})
    man = comum.ler_json("fotos.json", {})
    con = sqlite3.connect(comum.DADOS / "mundo.sqlite")
    alvo = []
    for jid, q in con.execute("SELECT id, wikidata FROM jogadores WHERE wikidata IS NOT NULL ORDER BY fama_ref DESC"):
        if wd.get(q, {}).get("img"):
            alvo.append((jid, q))
        if len(alvo) >= qtd:
            break
    con.close()
    feitos = 0
    for i, (jid, q) in enumerate(alvo):
        if q in man and (man[q].get("png") or man[q].get("erro")):
            continue
        arq = nome_arquivo(wd[q]["img"])
        reg = {"arquivo": arq, "jogador_id": jid}
        info = info_commons(arq)
        if not info or not info["original"]:
            if info is not None:
                reg["erro"] = "página do Commons sem arquivo original"
                man[q] = reg
            continue
        reg.update({"fonte_url": info["pagina"], "licenca": info["licenca"] or "ver página do Commons",
                    "autor": info["autor"], "original": info["original"]})
        dados = None
        mini = miniatura(info["original"])
        if mini:
            dados = comum.baixar(mini, binario=True, intervalo=3.0)
        if dados is None and not info["original"].lower().endswith((".svg", ".tif", ".tiff")):
            dados = comum.baixar(info["original"], binario=True, intervalo=3.0)
        if dados is None:
            continue   # falha temporária (ex.: HTTP 429): não marca erro, tenta de novo na próxima execução
        try:
            img = Image.open(io.BytesIO(dados)).convert("RGB")
            if img.height > 1000:
                img.thumbnail((1000, 1000))
            sem = remove(img, session=sessao)
            bbox = sem.getchannel("A").point(lambda a: 255 if a > 20 else 0).getbbox()
            if not bbox or (bbox[2] - bbox[0]) * (bbox[3] - bbox[1]) < 0.04 * sem.width * sem.height:
                reg["erro"] = "recorte vazio"
                man[q] = reg
                continue
            sem = sem.crop(bbox)
            if sem.height > ALTURA_MAX:
                sem = sem.resize((max(1, round(sem.width * ALTURA_MAX / sem.height)), ALTURA_MAX), Image.LANCZOS)
            destino = FOTOS / f"{jid}.png"
            sem.save(destino, optimize=True)
            reg["png"] = f"assets/fotos/{jid}.png"
            feitos += 1
        except Exception as e:  # imagem corrompida/formato raro
            reg["erro"] = f"processamento: {e.__class__.__name__}"
        man[q] = reg
        if feitos % 20 == 0:
            comum.salvar_json("fotos.json", man)
            print(f"  {i + 1}/{len(alvo)} processados, {feitos} novas fotos", flush=True)
    comum.salvar_json("fotos.json", man)
    ok = sum(1 for v in man.values() if v.get("png"))
    print(f"fotos com fundo removido: {ok} (novas nesta execução: {feitos}); com erro: "
          f"{sum(1 for v in man.values() if v.get('erro'))}")


if __name__ == "__main__":
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 300)
