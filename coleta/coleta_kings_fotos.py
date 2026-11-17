"""Fotos oficiais dos atletas da Kings League (site oficial kingsleague.pro + CDN kingsleague-cdn.kama.football).

Fonte: páginas públicas de time do site oficial (/en/teams/<id>-<slug>), cujo HTML já traz os cards do
elenco atual (nome, link /en/players/..., imagem do CDN) e da comissão (presidente, técnico). As páginas de
partida (/matches/) são proibidas pelo robots.txt e NÃO são acessadas. 1 req/s por domínio, cache em
coleta/cache/ (via comum.baixar) e cópia das páginas em coleta/cache/kings/fotos/.

Para cada nome de kings_elencos (todas as ligas + KWC Clubs/Nations) procura a foto do mesmo time
(nome normalizado: sem acento, aspas e apelidos tratados); se não achar, procura pelo nome em qualquer time
(só se o nome for único). Placeholders do site (kl-player-placeholder) são descartados.

Saída:
- assets/kings/fotos/<slug>.png — 197x256 RGBA transparente, cabeça e ombros (formato de assets/fotos/);
- assets/kings/fotos/indice.json — {"por_time": {"<time_id>|<nome_fonte>": caminho}, "por_nome": {nome_norm: caminho}};
- coleta/cache/futae-kings-novas/kings_<Nome>.png — cópias 122x180 para o projeto FUT.AE (só nomes que já
  existem lá; a pasta do FUT.AE é apenas listada, nunca escrita).

Uso: python coleta_kings_fotos.py [--sem-futae]
"""
from __future__ import annotations

import io
import json
import os
import re
import sqlite3
import sys
from pathlib import Path

import comum

comum.UA = "FUTEBAS-dados/1.0"
comum._sess.headers["User-Agent"] = comum.UA
os.environ.setdefault("U2NET_HOME", str(comum.CACHE / "modelos"))

from bs4 import BeautifulSoup  # noqa: E402
from PIL import Image  # noqa: E402

SITE = "https://kingsleague.pro"
CDN = "https://kingsleague-cdn.kama.football/account/"
DEST = comum.RAIZ / "assets" / "kings" / "fotos"
CACHE_PAG = comum.CACHE / "kings" / "fotos"
FUTAE_ORIG = Path(os.environ.get("FUTAE_DIR", "FUT.AE/data")) / "photos" / "players"   # só leitura (listar)
FUTAE_OUT = comum.CACHE / "futae-kings-novas"
LIGAS = {"kings-espanha": "espana", "kings-americas": "americas", "kings-italia": "italia",
         "kings-brasil": "brazil", "kings-franca": "france", "kings-alemanha": "germany", "kings-mena": "mena",
         "kings-world-cup-nations": "kwc-nations", "kings-world-cup-clubs": "world-cup"}
POSICOES = {"Goalkeeper": "GOL", "Defender": "DEF", "Midfielder": "MEI", "Forward": "ATA"}
W, H = 197, 256           # formato de assets/fotos/*.png
FW, FH = 122, 180         # formato do FUT.AE


def chave(s: str) -> str:
    """Nome normalizado para casar: sem acento/aspas/pontuação, minúsculo."""
    return comum.norm((s or "").replace("'", " ").replace('"', " ").replace("´", " ").replace("’", " "))


def toks(s: str) -> list[str]:
    return chave(s).split()


def pagina(url: str) -> BeautifulSoup | None:
    h = comum.baixar(url)
    if not h:
        return None
    CACHE_PAG.mkdir(parents=True, exist_ok=True)
    (CACHE_PAG / (re.sub(r"[^\w]", "_", url.split("://", 1)[-1]) + ".html")).write_text(h, "utf-8")
    return BeautifulSoup(h, "lxml")


def url_cdn(img) -> str | None:
    """URL original do CDN a partir do src/srcset do <img> (/_ipx/<mods>/<url ou kama/...>)."""
    cands = [img.get("src") or ""] + [p.strip().split(" ")[0] for p in (img.get("srcset") or "").split(",")]
    for c in cands:
        if "placeholder" in c:
            return None
        m = re.search(r"(https://kingsleague-cdn\.kama\.football/\S+?\.(?:png|jpe?g|webp))", c)
        if m:
            return m.group(1)
        m = re.search(r"/kama/(production/\S+?\.(?:png|jpe?g|webp))", c)
        if m:
            return CDN + m.group(1)
    return None


def times_da_liga(slug: str) -> dict[str, str]:
    """{url_time: nome} da página /en/<liga>/teams."""
    s = pagina(f"{SITE}/en/{slug}/teams")
    out = {}
    if not s:
        return out
    for sc in s.find_all("script", type="application/ld+json"):
        try:
            js = json.loads(sc.string or "")
        except (json.JSONDecodeError, TypeError):
            continue
        for it in (js.get("itemListElement") or []) if isinstance(js, dict) else []:
            item = it.get("item") or {}
            if "/teams/" in (item.get("url") or ""):
                out[item["url"]] = item.get("name") or ""
    for a in s.find_all("a", href=re.compile(r"^/en/teams/\d+-")):
        u = SITE + a["href"]
        out.setdefault(u, a.get_text(" ", strip=True))
    return out


def elenco_site(url: str) -> list[dict]:
    """Cards de atletas e comissão da página do time: [{nome, img, papel, link}]."""
    s = pagina(url)
    if not s:
        return []
    out = []
    for c in s.select(".player-card-content"):
        a = c.find_parent("a")
        partes = c.get_text("|", strip=True).split("|")
        nome = next((p for p in partes if not p.startswith("#")), "")
        im = c.find("img")
        pos = next((p for p in partes if p in POSICOES), None)
        out.append({"nome": nome, "img": url_cdn(im) if im else None, "papel": "atleta",
                    "link": a.get("href") if a else None, "pos": pos})
    for im in s.find_all("img"):
        ss = (im.get("srcset") or "") + (im.get("src") or "")
        if "seasonTeamStaff" not in ss and "/staff/" not in ss:
            continue
        p, txt = im, ""
        for _ in range(6):
            p = p.parent
            if p is None:
                break
            txt = p.get_text("|", strip=True)
            if txt:
                break
        partes = [x for x in txt.split("|") if x]
        nome = im.get("alt") or (partes[0] if partes else "")
        papel = (partes[1] if len(partes) > 1 else "").lower()
        # foto de grupo ("Neymar + Cris Guedes"): não serve como retrato de uma pessoa só
        grupo = bool(re.search(r"\+|&|Hnos", nome))
        out.append({"nome": nome, "img": None if grupo else url_cdn(im), "papel": "staff:" + papel, "link": None})
    return out


def sem_sufixo(nome: str) -> str:
    """Nome de time sem 'FC', 'CF', 'F.C.' ('Capim' = 'Capim FC')."""
    return " ".join(t for t in chave(nome).split() if t not in ("fc", "cf", "f", "c", "ff", "the"))


def global_ok(nome_db: str, nome_site: str, nivel: int) -> bool:
    """Casamento fora do time (busca só pelo nome): nome igual, ou todas as palavras do nome da fonte
    no nome do site (o site tem o nome mais completo). Nunca o contrário ('João Pedro Pajé' != 'João Pedro')."""
    if nivel >= 3:
        return casa(nome_db, nome_site) >= 3
    a, b = toks(nome_db), toks(nome_site)
    return len(a) >= 2 and all(t in b for t in a)


def casa(nome_db: str, nome_site: str) -> int:
    """0 = não casa; maior = melhor."""
    a, b = chave(nome_db), chave(nome_site)
    if not a or not b:
        return 0
    if a == b:
        return 3
    if "?" in nome_db:   # acento perdido na fonte ("LC Pared?o"): '?' vale um caractere qualquer
        sem = lambda x: comum.norm(x.replace("?", "QQQ")).replace(" ", "")
        padrao = re.escape(sem(nome_db)).replace("qqq", ".{1,2}")
        if re.fullmatch(padrao, chave(nome_site).replace(" ", "")):
            return 3
    ta, tb = a.split(), b.split()
    if a.replace(" ", "") == b.replace(" ", ""):
        return 3
    menor, maior = (ta, tb) if len(ta) <= len(tb) else (tb, ta)
    if len(menor) >= 2 and all(t in maior for t in menor):
        return 2
    # mesmo primeiro e último nome (o meio/apelido difere)
    if len(ta) >= 2 and len(tb) >= 2 and ta[0] == tb[0] and ta[-1] == tb[-1]:
        return 1
    return 0


_SESSAO = None


def silhueta(img: Image.Image) -> bool:
    """Placeholder do site em forma de silhueta preta (sem rosto): pixels opacos quase todos escuros e lisos."""
    from PIL import ImageStat
    peq = img.copy()
    peq.thumbnail((128, 128))
    masc = peq.getchannel("A").point(lambda v: 255 if v > 128 else 0)
    if not masc.getbbox():
        return True
    st = ImageStat.Stat(peq.convert("L"), mask=masc)
    return st.mean[0] < 40 and st.stddev[0] < 26


def recorta(dados: bytes, w: int, h: int, larg_rel: float) -> Image.Image | None:
    """Recorte cabeça-e-ombros transparente w x h. larg_rel = largura do recorte / largura do corpo."""
    global _SESSAO
    try:
        img = Image.open(io.BytesIO(dados)).convert("RGBA")
    except Exception:
        return None
    a = img.getchannel("A")
    if a.getextrema()[0] == 255:       # fundo opaco: recorta a pessoa com rembg
        from rembg import new_session, remove
        if _SESSAO is None:
            _SESSAO = new_session("u2net")
        img = remove(img, session=_SESSAO)
        a = img.getchannel("A")
    bbox = a.point(lambda v: 255 if v > 40 else 0).getbbox()
    if not bbox or silhueta(img):
        return None
    x0, y0, x1, y1 = bbox
    bw = x1 - x0
    # centro da cabeça: média das colunas opacas na faixa de cima (8% da altura do corpo)
    faixa = a.crop((0, y0, img.width, y0 + max(4, (y1 - y0) // 12))).point(lambda v: 255 if v > 40 else 0)
    hb = faixa.getbbox()
    cx = (hb[0] + hb[2]) / 2 if hb else (x0 + x1) / 2
    cw = bw * larg_rel
    ch = cw * h / w
    topo = y0 - ch * 0.05
    caixa = (int(cx - cw / 2), int(topo), int(cx + cw / 2), int(topo + ch))
    out = Image.new("RGBA", (caixa[2] - caixa[0], caixa[3] - caixa[1]), (0, 0, 0, 0))
    out.alpha_composite(img.crop((max(0, caixa[0]), max(0, caixa[1]), min(img.width, caixa[2]), min(img.height, caixa[3]))),
                        (max(0, -caixa[0]), max(0, -caixa[1])))
    return out.resize((w, h), Image.LANCZOS)


def main():
    sem_futae = "--sem-futae" in sys.argv
    con = sqlite3.connect(f"file:{comum.DADOS / 'mundo.sqlite'}?mode=ro", uri=True)
    times = {r[0]: (r[1], r[2]) for r in con.execute("SELECT id, liga_id, nome FROM kings_times")}
    elenco = con.execute("SELECT DISTINCT time_id, nome_fonte, tipo FROM kings_elencos").fetchall()
    con.close()

    # 1) páginas de time do site → kings_times
    url_do_time: dict[str, str] = {}
    paginas: dict[str, str] = {}
    for liga, slug in LIGAS.items():
        for u, nome in times_da_liga(slug).items():
            paginas[u] = nome
    for tid, (liga, nome) in times.items():
        melhor = None
        for u, n in paginas.items():
            if sem_sufixo(n) == sem_sufixo(nome) or u.rsplit("/", 1)[-1].split("-", 1)[-1] == comum.slug(nome):
                melhor = u
                break
        if melhor:
            url_do_time[tid] = melhor
    paginas_selecao = {u for t, u in url_do_time.items() if times[t][0] == "kings-world-cup-nations"}
    print(f"times no site: {len(paginas)}; times do banco ligados: {len(url_do_time)} de {len(times)}", flush=True)
    for tid in sorted(set(times) - set(url_do_time)):
        print("  sem página:", tid, times[tid][1])

    # 2) elencos do site
    site: dict[str, list[dict]] = {}
    for u in sorted(paginas):
        site[u] = [dict(p, time=u) for p in elenco_site(u)]
    todos = [p for ps in site.values() for p in ps if p["img"]]
    print("cards com foto no site:", len(todos), "(sem foto/placeholder:",
          sum(1 for ps in site.values() for p in ps if not p["img"]), ")", flush=True)

    # 3) casamento
    escolha: dict[tuple[str, str], dict] = {}
    for tid, nome, tipo in elenco:
        cands = site.get(url_do_time.get(tid), [])
        cands = [p for p in cands if p["img"]]
        if tipo in ("presidente", "tecnico"):
            cands = [p for p in cands if p["papel"].startswith("staff")] + [p for p in cands if p["papel"] == "atleta"]
        melhor = max(cands, key=lambda p: casa(nome, p["nome"]), default=None)
        if melhor and casa(nome, melhor["nome"]) > 0:
            escolha[(tid, nome)] = melhor
            continue
        # fallback: nome igual (ou contido, >=2 palavras) em qualquer time do site, se único
        for nivel in (3, 2):
            achados = [p for p in todos if global_ok(nome, p["nome"], nivel)]
            if len({p["link"] or p["img"] for p in achados}) == 1:   # uma pessoa só (pode ter 2 fotos: clube e seleção)
                achados.sort(key=lambda p: p["time"] in paginas_selecao)
                escolha[(tid, nome)] = achados[0]
                break

    # 4) download + recorte
    DEST.mkdir(parents=True, exist_ok=True)
    feito: dict[str, str] = {}      # url img -> caminho relativo
    ruins = set(comum.ler_json("kings_fotos_rejeitadas.json", []) or [])   # URLs reprovadas na revisão visual
    for (tid, nome), p in sorted(escolha.items()):
        u = p["img"]
        if u in feito or u in ruins:
            continue
        if p["link"]:
            arq = re.sub(r"[^\w-]", "", p["link"].rsplit("/", 1)[-1])
        else:
            arq = "staff-" + comum.slug(times.get(tid, ("", tid))[1]) + "-" + comum.slug(p["nome"])
        if f"assets/kings/fotos/{arq}.png" in feito.values():   # mesmo atleta, outra foto (ex.: seleção na KWC Nations)
            arq += "-" + re.sub(r"^\d+-", "", (p.get("time") or "").rsplit("/", 1)[-1])
        d = comum.baixar(u, binario=True)
        if not d:
            continue
        img = recorta(d, W, H, 0.62)
        if img is None:
            continue
        img.save(DEST / f"{arq}.png", optimize=True)
        feito[u] = f"assets/kings/fotos/{arq}.png"
        p["arq"] = arq
    # remove sobras de execuções anteriores (fotos reprovadas, placeholders)
    validos = {Path(v).name for v in feito.values()}
    for f in DEST.glob("*.png"):
        if f.name not in validos:
            f.unlink()
    por_time = {}
    for (tid, nome), p in escolha.items():
        if p["img"] in feito:
            por_time[f"{tid}|{nome}"] = feito[p["img"]]
    # fallback só-nome: um nome → uma foto (descarta nomes ambíguos)
    por_nome: dict[str, set] = {}
    for (tid, nome), p in escolha.items():
        if p["img"] in feito:
            por_nome.setdefault(chave(nome), set()).add(feito[p["img"]])
    for p in todos:
        if p["img"] in feito:
            por_nome.setdefault(chave(p["nome"]), set()).add(feito[p["img"]])
    por_nome_ok = {k: next(iter(v)) for k, v in sorted(por_nome.items()) if len(v) == 1 and k}
    (DEST / "indice.json").write_text(json.dumps(
        {"fonte": "kingsleague.pro (páginas de time) + kingsleague-cdn.kama.football",
         "formato": f"{W}x{H} RGBA", "por_time": dict(sorted(por_time.items())), "por_nome": por_nome_ok},
        ensure_ascii=False, indent=1), "utf-8")
    # cópia das URLs para auditoria
    comum.salvar_json("kings_fotos.json", {f"{t}|{n}": {"img": p["img"], "nome_site": p["nome"], "time_site": p.get("time")}
                                          for (t, n), p in sorted(escolha.items()) if p["img"] in feito})

    # 4b) posição oficial (card do elenco no site; vale também para quem não tem foto)
    atletas = [p for ps in site.values() for p in ps if p["papel"] == "atleta" and p.get("pos")]
    posicoes = {}
    for tid, nome, tipo in elenco:
        if tipo in ("tecnico", "staff"):
            continue
        cands = [p for p in site.get(url_do_time.get(tid), []) if p["papel"] == "atleta" and p.get("pos")]
        melhor = max(cands, key=lambda p: casa(nome, p["nome"]), default=None)
        if not (melhor and casa(nome, melhor["nome"]) > 0):
            melhor = None
            for nivel in (3, 2):
                ach = {(p["link"] or p["nome"]): p for p in atletas if global_ok(nome, p["nome"], nivel)}
                if len({p["pos"] for p in ach.values()}) == 1:
                    melhor = next(iter(ach.values()))
                    break
        if melhor:
            posicoes[f"{tid}|{nome}"] = {"posicao": POSICOES[melhor["pos"]], "posicao_site": melhor["pos"],
                                         "nome_site": melhor["nome"], "time_site": melhor["time"], "tipo": tipo}
    comum.salvar_json("kings_posicoes.json", dict(sorted(posicoes.items())))
    print(f"posições oficiais: {len(posicoes)} de {sum(1 for e in elenco if e[2] not in ('tecnico', 'staff'))} atletas")

    # 5) relatório por liga
    print("\nliga                         nomes  com foto  sem")
    porliga: dict[str, list[int]] = {}
    for tid, nome, tipo in elenco:
        liga = times.get(tid, ("?",))[0]
        c = porliga.setdefault(liga, [0, 0])
        c[0] += 1
        c[1] += f"{tid}|{nome}" in por_time
    for liga, (n, f) in sorted(porliga.items()):
        print(f"{liga:28s} {n:5d}  {f:8d}  {n - f:4d}")
    tot = sum(v[0] for v in porliga.values()), sum(v[1] for v in porliga.values())
    print(f"{'TOTAL':28s} {tot[0]:5d}  {tot[1]:8d}  {tot[0] - tot[1]:4d}   arquivos: {len(feito)}")
    falt = sorted(f"{times.get(t, ('?',))[0]} | {t} | {n} | {tp}" for t, n, tp in elenco if f"{t}|{n}" not in por_time)
    (comum.INTERMED / "kings_fotos_faltantes.txt").write_text("\n".join(falt), "utf-8")

    if not sem_futae:
        exporta_futae(todos, feito, paginas_selecao)


def exporta_futae(todos, feito, paginas_selecao=frozenset()):
    """Cópias 122x180 com os nomes exatos dos arquivos kings_*.png do FUT.AE (pasta deles só é listada)."""
    if not FUTAE_ORIG.is_dir():
        return
    nomes = sorted(f.name for f in FUTAE_ORIG.iterdir() if f.name.startswith("kings_") and f.suffix == ".png")
    FUTAE_OUT.mkdir(parents=True, exist_ok=True)
    for f in FUTAE_OUT.glob("*.png"):
        f.unlink()   # refaz do zero (só arquivos desta pasta de staging)
    ok, falta = [], []
    for arq in nomes:
        nome = arq[len("kings_"):-4].replace("_", " ")
        achados = []
        for nivel in (3, 2):
            achados = [p for p in todos if p["img"] in feito and global_ok(nome, p["nome"], nivel)]
            if achados:
                break
        pessoas = {p["link"] or p["img"] for p in achados}
        if len(pessoas) != 1:
            falta.append(f"{arq} ({len(pessoas)} atletas com esse nome no site)" if pessoas else f"{arq} (não está nos elencos atuais do site)")
            continue
        achados.sort(key=lambda p: p["time"] in paginas_selecao)   # foto do clube antes da foto da seleção
        p = achados[0]
        img = recorta(comum.baixar(p["img"], binario=True), FW, FH, 0.95)
        if img is None:
            falta.append(arq)
            continue
        img.save(FUTAE_OUT / arq, optimize=True)
        ok.append(f"{arq} <- {p['nome']}")
    (FUTAE_OUT / "_relatorio.txt").write_text(
        f"substituídas: {len(ok)} de {len(nomes)}\n\n" + "\n".join(ok) + "\n\nsem foto oficial:\n" + "\n".join(falta), "utf-8")
    print(f"\nFUT.AE: {len(ok)} de {len(nomes)} substituídas em {FUTAE_OUT}")


if __name__ == "__main__":
    sys.exit(main())
