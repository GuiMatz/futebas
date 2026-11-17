"""Coleta os participantes reais de cada competição por ano, a partir das tabelas de
classificação ("Pld ... Pts") das páginas de temporada da Wikipedia (inglês; português
como reserva).

Saída: coleta/intermediario/participantes_brutos.json
  [{competicao, ano, url, clubes: [{titulo, texto}]}]
Os títulos de artigo são resolvidos para clubes (Wikidata) em coleta_clubes.py.
"""
from __future__ import annotations

import re
import sys
import urllib.parse

from bs4 import BeautifulSoup

import comum

ANOS = range(1995, 2027)

ESTADUAIS = {  # uf: (nome em inglês, competição)
    "SP": "Campeonato Paulista", "RJ": "Campeonato Carioca", "MG": "Campeonato Mineiro",
    "RS": "Campeonato Gaúcho", "PR": "Campeonato Paranaense", "SC": "Campeonato Catarinense",
    "BA": "Campeonato Baiano", "PE": "Campeonato Pernambucano", "CE": "Campeonato Cearense",
    "GO": "Campeonato Goiano",
}


def titulo_de_href(href: str) -> str | None:
    m = re.match(r"^(?:https?://([a-z]+)\.wikipedia\.org)?/wiki/([^#?]+)", href or "")
    if not m:
        return None
    t = urllib.parse.unquote(m.group(2)).replace("_", " ")
    if ":" in t.split(" ")[0] and t.split(":")[0] in ("File", "Template", "Help", "Category",
                                                      "Special", "Wikipedia", "Arquivo",
                                                      "Predefinição", "Ficheiro", "Imagem"):
        return None
    return t


def _cab(tb) -> list[str]:
    linhas = tb.find_all("tr")
    if not linhas:
        return []
    return [c.get_text(" ", strip=True) for c in linhas[0].find_all(["th", "td"])]


def eh_classificacao(cab: list[str]) -> bool:
    s = {c.strip().rstrip(".").lower() for c in cab}
    tem_pts = bool(s & {"pts", "pg", "points", "pontos"})
    tem_jogos = bool(s & {"pld", "j", "p", "gp", "mp", "jogos"})
    return tem_pts and tem_jogos


def tabelas_classificacao(html: str, lang: str):
    """Rende (titulo_secao, [ (titulo_artigo, texto) ]) para cada tabela de classificação."""
    s = BeautifulSoup(html, "html.parser")
    corpo = s.select_one("#mw-content-text") or s
    secao = ""
    for el in corpo.find_all(["h2", "h3", "h4", "table"]):
        if el.name != "table":
            secao = el.get_text(" ", strip=True)
            continue
        if "wikitable" not in (el.get("class") or []):
            continue
        cab = _cab(el)
        if not eh_classificacao(cab):
            continue
        icol = None
        for i, c in enumerate(cab):
            if c.strip().rstrip(".").lower() in ("team", "club", "equipe", "time", "clube", "teams"):
                icol = i
                break
        clubes = []
        for tr in el.find_all("tr")[1:]:
            achou = False
            for a in tr.find_all("a"):
                if a.find_parent(class_="flagicon") or a.find("img"):
                    continue
                t = titulo_de_href(a.get("href", ""))
                if t and not re.match(r"^\d{4}", t):
                    clubes.append((t, a.get_text(" ", strip=True)))
                    achou = True
            if icol is not None:
                cels = tr.find_all(["th", "td"], recursive=False)
                if icol < len(cels):
                    cel = cels[icol]
                    if not [a for a in cel.find_all("a")
                            if not (a.find_parent(class_="flagicon") or a.find("img"))]:
                        txt = cel.get_text(" ", strip=True)
                        txt = re.sub(r"\s*\((?:[A-Z]|[a-z]{1,2})\)\s*$", "", txt).strip()
                        txt = re.sub(r"\[.*?\]", "", txt).strip()
                        if txt and not txt.isdigit():
                            clubes.append((None, txt))
        yield secao, clubes


def coletar_pagina(titulos: list[tuple[str, str]]):
    """Tenta cada (lang, titulo); devolve (url, lang, [(secao, clubes)]) da primeira com tabelas."""
    for lang, t in titulos:
        h = comum.wiki_html(lang, t)
        if not h:
            continue
        tabs = list(tabelas_classificacao(h, lang))
        if tabs:
            return comum.wiki_url(lang, t), lang, tabs
    return None, None, []


def nacionais():
    out = []
    for ano in ANOS:
        divs = "ABCD" if ano >= 2009 else "ABC"
        for d in divs:
            comp = f"bra-serie-{d.lower()}"
            if ano == 2000:
                continue
            titulos = [("en", f"{ano} Campeonato Brasileiro Série {d}"),
                       ("pt", f"Campeonato Brasileiro de Futebol de {ano} - Série {d}"),
                       ("pt", f"Campeonato Brasileiro de Futebol de {ano} – Série {d}")]
            url, lang, tabs = coletar_pagina(titulos)
            cl = _uniq([c for _, cs in tabs for c in cs])
            print(f"{comp} {ano}: {len(tabs)} tabelas, {len(cl)} links  [{lang}]", flush=True)
            out.append({"competicao": comp, "ano": ano, "url": url, "clubes": cl})
    # 2000: Copa João Havelange (módulos Azul ≈ A, Amarelo ≈ B, Verde/Branco ≈ C)
    url, lang, tabs = coletar_pagina([("en", "2000 Copa João Havelange"),
                                      ("en", "Copa João Havelange"),
                                      ("pt", "Copa João Havelange")])
    for sec, cs in tabs:
        print("   seção JH:", sec[:60], len(cs))
    mod = {"bra-serie-a": [], "bra-serie-b": [], "bra-serie-c": []}
    h = comum.wiki_html("pt", "Copa João Havelange")
    if h:
        url = comum.wiki_url("pt", "Copa João Havelange")
        sp = BeautifulSoup(h, "html.parser")
        for tb in sp.select("table.wikitable"):
            cab = [c.lower() for c in _cab(tb)]
            if "equipe" in cab and any(c.startswith("módulo") for c in cab):
                im = [i for i, c in enumerate(cab) if c.startswith("módulo")][0]
                for tr in tb.find_all("tr")[1:]:
                    cels = tr.find_all(["th", "td"], recursive=False)
                    if len(cels) <= im:
                        continue
                    links = [a for a in cels[0].find_all("a") if not a.find("img")]
                    if not links:
                        continue
                    t = titulo_de_href(links[0].get("href", ""))
                    m = cels[im].get_text(" ", strip=True).lower()
                    alvo = ("bra-serie-a" if "azul" in m else "bra-serie-b" if "amarel" in m
                            else "bra-serie-c" if ("verde" in m or "branco" in m) else None)
                    if alvo and t:
                        mod[alvo].append((t, links[0].get_text(" ", strip=True)))
        tabs = []
    for sec, cs in tabs:
        s = sec.lower()
        if "blue" in s or "azul" in s:
            mod["bra-serie-a"] += cs
        elif "yellow" in s or "amarel" in s:
            mod["bra-serie-b"] += cs
        elif any(k in s for k in ("green", "white", "verde", "branco")):
            mod["bra-serie-c"] += cs
    for comp, cs in mod.items():
        cl = _uniq(cs)
        print(f"{comp} 2000 (Copa JH): {len(cl)} links", flush=True)
        out.append({"competicao": comp, "ano": 2000, "url": url, "clubes": cl,
                    "obs": "Copa João Havelange (módulo equivalente)"})
    return out


def libertadores():
    out = []
    for ano in ANOS:
        url, lang, tabs = coletar_pagina([("en", f"{ano} Copa Libertadores")])
        cl = _uniq([c for _, cs in tabs for c in cs])
        print(f"libertadores {ano}: {len(tabs)} tabelas, {len(cl)} links", flush=True)
        out.append({"competicao": "conmebol-libertadores", "ano": ano, "url": url, "clubes": cl})
    return out


def copa():
    """Copa do Brasil: chaves de mata-mata (não há tabela de pontos). Junta os links de clubes de
    todas as tabelas do conteúdo (menos infobox/navbox); clubes não brasileiros são descartados depois."""
    out = []
    for ano in ANOS:
        t = f"{ano} Copa do Brasil"
        h = comum.wiki_html("en", t)
        if not h:
            print(f"copa {ano}: página ausente", flush=True)
            continue
        s = BeautifulSoup(h, "html.parser")
        corpo = s.select_one("#mw-content-text") or s
        cs = []
        for tb in corpo.find_all("table"):
            cls = " ".join(tb.get("class") or [])
            if any(k in cls for k in ("infobox", "navbox", "sidebar", "metadata", "ambox")):
                continue
            if tb.find_parent("table") is not None and any(
                    k in " ".join(tb.find_parent("table").get("class") or []) for k in ("infobox", "navbox")):
                continue
            for a in tb.find_all("a"):
                if a.find_parent(class_="flagicon") or a.find("img") or a.find_parent("sup"):
                    continue
                tt = titulo_de_href(a.get("href", ""))
                if tt and not re.match(r"^\d{4}", tt):
                    cs.append((tt, a.get_text(" ", strip=True)))
        cl = _uniq(cs)
        print(f"copa {ano}: {len(cl)} links", flush=True)
        out.append({"competicao": "bra-copa-do-brasil", "ano": ano, "url": comum.wiki_url("en", t), "clubes": cl,
                    "so_brasileiros": True})
    return out


def estaduais():
    out = []
    for uf, nome in ESTADUAIS.items():
        for ano in ANOS:
            titulos = [("en", f"{ano} {nome}"), ("en", f"{ano} {nome} Série A1"),
                       ("en", f"{ano} {nome} Série A")]
            base_pt = f"{nome} de Futebol de {ano}"
            titulos += [("pt", base_pt + s) for s in
                        ("", " - Série A1", " - Série A", " - Módulo I", " - Primeira Divisão", " - Série A-1")]
            url, lang, tabs = coletar_pagina(titulos)
            if not tabs:
                continue
            cl = _uniq([c for _, cs in tabs for c in cs])
            print(f"est-{uf.lower()} {ano}: {len(tabs)} tabelas, {len(cl)} links", flush=True)
            out.append({"competicao": f"est-{uf.lower()}", "ano": ano, "url": url, "clubes": cl})
    return out


def _uniq(cs):
    vistos, out = set(), []
    for t, txt in cs:
        k = t or ("#" + txt)
        if k not in vistos:
            vistos.add(k)
            out.append({"titulo": t, "texto": txt})
    return out


if __name__ == "__main__":
    partes = sys.argv[1:] or ["nacionais", "libertadores", "estaduais", "copa"]
    tudo = comum.ler_json("participantes_brutos.json", [])
    feitos = {p for p in partes}
    tudo = [r for r in tudo if not any(
        (k == "nacionais" and r["competicao"].startswith("bra-serie")) or
        (k == "libertadores" and r["competicao"] == "conmebol-libertadores") or
        (k == "estaduais" and r["competicao"].startswith("est-")) or
        (k == "copa" and r["competicao"] == "bra-copa-do-brasil") for k in feitos)]
    for p in partes:
        tudo += globals()[p]()
    comum.salvar_json("participantes_brutos.json", tudo)
    print("total de registros:", len(tudo))
