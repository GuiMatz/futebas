"""Fotos sem fundo para TODO jogador do mundo que tem tm_id (USO PESSOAL).

- A URL do retrato vem do memi.sqlite do FUT.AE (aberto só para leitura, mode=ro) e do
  transfermarkt-datasets. Só aceitamos URL cujo nome contém "/<tm_id>-", ou seja, a identidade é pelo ID.
- Os ARQUIVOS de foto do FUT.AE não são usados: há fotos trocadas lá (ex.: a do Messi é de outro jogador).
  A imagem é baixada da CDN do Transfermarkt (comum.baixar: cache, 1 req/s, robots.txt, UA genérico).
- Fundo removido com rembg (u2net) em processos paralelos; saída em coleta/cache/fotos_tm/<tm_id>.png,
  que o montar_banco.py copia para assets/fotos/<jogador_id>.png.
- Ordem: mais fortes primeiro. Retomável.

Uso: python -I fotos_futae.py [quantidade=999999] [processos=4]
"""
from __future__ import annotations

import io
import os
import sqlite3
import sys
import time
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import comum  # noqa: E402

os.environ.setdefault("U2NET_HOME", str(comum.CACHE / "modelos"))

FUTAE_DB = Path(os.environ.get("FUTAE_DIR", "FUT.AE/data")) / "memi.sqlite"   # só leitura
SAIDA = comum.CACHE / "fotos_tm"
SAIDA.mkdir(parents=True, exist_ok=True)
TM = comum.CACHE / "transfermarkt-datasets" / "raw"
ALTURA_MAX = 256


def urls() -> dict[int, str]:
    out: dict[int, str] = {}
    try:
        import pandas as pd
        p = pd.read_csv(TM / "players.csv.gz", usecols=["player_id", "image_url"])
        for t, u in zip(p.player_id, p.image_url):
            if isinstance(u, str) and "default" not in u:
                out[int(t)] = u
    except Exception as e:  # noqa: BLE001
        print("  datasets:", e)
    fa = sqlite3.connect(f"file:{FUTAE_DB.as_posix()}?mode=ro", uri=True)
    for t, u in fa.execute("select tm_player_id, photo_url from players where tm_player_id is not null and photo_url is not null"):
        try:
            t = int(t)
        except ValueError:
            continue
        if isinstance(u, str) and "default" not in u and "transfermarkt" in u:
            out[t] = u   # a do FUT.AE é mais recente
    fa.close()
    return {t: u for t, u in out.items() if f"/{t}-" in u}


def fila() -> list[tuple[int, str]]:
    u = urls()
    mundo = sqlite3.connect(f"file:{(comum.RAIZ / 'dados' / 'mundo.sqlite').as_posix()}?mode=ro", uri=True)
    rows = mundo.execute("select tm_id, max(forca) from jogadores where tm_id is not null "
                         "group by tm_id order by max(forca) desc").fetchall()
    mundo.close()
    return [(tm, u[tm]) for tm, _ in rows if tm in u and not (SAIDA / f"{tm}.png").exists()]


_sess = None


def recorta(args):
    tm, b = args
    global _sess
    from PIL import Image
    from rembg import new_session, remove
    if _sess is None:
        _sess = new_session("u2net")
    img = Image.open(io.BytesIO(b)).convert("RGBA")
    out = remove(img, session=_sess)
    bbox = out.getbbox()
    if bbox:
        out = out.crop(bbox)
    if out.height > ALTURA_MAX:
        out = out.resize((max(1, round(out.width * ALTURA_MAX / out.height)), ALTURA_MAX), Image.LANCZOS)
    tmp = SAIDA / f"{tm}.tmp.png"
    out.save(tmp, optimize=True)
    os.replace(tmp, SAIDA / f"{tm}.png")
    return tm


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 999999
    procs = int(sys.argv[2]) if len(sys.argv) > 2 else 4
    q = fila()[:n]
    print(f"a processar: {len(q)}", flush=True)
    t0 = time.time()
    feitos = erros = sem = 0
    pend = []
    with ProcessPoolExecutor(procs) as ex:
        for i, (tm, url) in enumerate(q):
            b = comum.baixar(url.replace("/header/", "/big/"), binario=True) or comum.baixar(url, binario=True)
            if not b:
                sem += 1
                continue
            pend.append(ex.submit(recorta, (tm, b)))
            while len(pend) > procs * 4 or (pend and pend[0].done()):
                f = pend.pop(0)
                try:
                    f.result()
                    feitos += 1
                except Exception as e:  # noqa: BLE001
                    erros += 1
                    print("  erro", e, flush=True)
            if i and i % 200 == 0:
                el = time.time() - t0
                print(f"  {i}/{len(q)}  ok {feitos}  sem {sem}  {el / i:.2f}s/foto  faltam ~{(len(q) - i) * el / i / 60:.0f} min", flush=True)
        for f in pend:
            try:
                f.result()
                feitos += 1
            except Exception as e:  # noqa: BLE001
                erros += 1
                print("  erro", e, flush=True)
    print(f"fotos novas: {feitos}, sem imagem: {sem}, erros: {erros}")


if __name__ == "__main__":
    main()
