"""Campeões e vices reais (1995-2026) das listas de campeões da Wikipedia (inglês), com
reserva no infobox da página de temporada ("Champions"/"Runners-up").

Saída: coleta/intermediario/campeoes_brutos.json
  [{competicao, ano, campeao:{titulo,texto}, vice:{...}|None, url}]
"""
from __future__ import annotations

import re

from bs4 import BeautifulSoup

import comum
from coleta_participantes import ESTADUAIS, titulo_de_href

LISTAS = {
    "bra-serie-a": ["Campeonato Brasileiro Série A", "List of Brazilian football champions"],
    "bra-serie-b": ["Campeonato Brasileiro Série B"],
    "bra-serie-c": ["Campeonato Brasileiro Série C"],
    "bra-serie-d": ["Campeonato Brasileiro Série D"],
    "bra-copa-do-brasil": ["Copa do Brasil", "List of Copa do Brasil finals"],
    "conmebol-libertadores": ["List of Copa Libertadores finals", "Copa Libertadores"],
}
for uf, nome in ESTADUAIS.items():
    LISTAS[f"est-{uf.lower()}"] = [nome, f"List of {nome} champions"]

TEMPORADA = {
    "bra-serie-a": "{a} Campeonato Brasileiro Série A", "bra-serie-b": "{a} Campeonato Brasileiro Série B",
    "bra-serie-c": "{a} Campeonato Brasileiro Série C", "bra-serie-d": "{a} Campeonato Brasileiro Série D",
    "bra-copa-do-brasil": "{a} Copa do Brasil", "conmebol-libertadores": "{a} Copa Libertadores",
}
for uf, nome in ESTADUAIS.items():
    TEMPORADA[f"est-{uf.lower()}"] = "{a} " + nome


def _links(cel):
    out = []
    for a in cel.find_all("a"):
        if a.find_parent(class_="flagicon") or a.find("img") or a.find_parent("sup"):
            continue
        t = titulo_de_href(a.get("href", ""))
        if t and not re.match(r"^\d{4}", t):
            out.append({"titulo": t, "texto": a.get_text(" ", strip=True)})
    return out


def de_lista(html: str):
    """{ano: [links da linha na ordem]} para as linhas cuja 1ª/2ª célula tem um ano."""
    s = BeautifulSoup(html, "html.parser")
    res = {}
    for tb in s.select("table.wikitable"):
        cab = [c.get_text(" ", strip=True).lower() for c in tb.find_all("tr")[0].find_all(["th", "td"])] if tb.find_all("tr") else []
        if not any(k in " ".join(cab) for k in ("champion", "winner", "runner", "final", "campe")):
            continue
        ano_rowspan = None
        for tr in tb.find_all("tr")[1:]:
            cels = tr.find_all(["th", "td"], recursive=False)
            if not cels:
                continue
            ano = None
            for c in cels[:2]:
                m = re.match(r"^\s*(\d{4})", c.get_text(" ", strip=True))
                if m:
                    ano = int(m.group(1))
                    break
            if ano is None or not (1995 <= ano <= 2026):
                continue
            ls = []
            for c in cels:
                ls += _links(c)
            if ls and ano not in res:
                res[ano] = ls
    return res


def de_infobox(html: str):
    s = BeautifulSoup(html, "html.parser")
    ib = s.select_one("table.infobox")
    if not ib:
        return None, None
    camp = vice = None
    for tr in ib.find_all("tr"):
        th, td = tr.find("th"), tr.find("td")
        if not th or not td:
            continue
        k = th.get_text(" ", strip=True).lower()
        ls = _links(td)
        if not ls:
            continue
        if k.startswith("champion") or k.startswith("winner") or k == "campeão":
            camp = camp or ls
        elif k.startswith("runner") or k.startswith("vice"):
            vice = vice or ls
    return camp, vice


def main():
    out = []
    for comp, titulos in LISTAS.items():
        por_ano, url_lista = {}, None
        for t in titulos:
            h = comum.wiki_html("en", t)
            if not h:
                continue
            r = de_lista(h)
            if len(r) > len(por_ano):
                por_ano, url_lista = r, comum.wiki_url("en", t)
        print(f"{comp}: {len(por_ano)} anos na lista", flush=True)
        for ano in range(1995, 2027):
            if comp == "bra-serie-d" and ano < 2009:
                continue
            reg = {"competicao": comp, "ano": ano, "linha": por_ano.get(ano), "url": url_lista}
            # infobox da temporada (ganha quando existe: mais específico)
            ts = TEMPORADA[comp].format(a=ano)
            h = comum.wiki_html("en", ts)
            if h:
                c, v = de_infobox(h)
                if c:
                    reg["infobox"] = {"campeao": c, "vice": v, "url": comum.wiki_url("en", ts)}
            out.append(reg)
    comum.salvar_json("campeoes_brutos.json", out)
    print("registros:", len(out))


if __name__ == "__main__":
    main()
