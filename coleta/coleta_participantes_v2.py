"""Participantes reais das competições da temporada inicial (2026 ou 2025/26), pela Wikipedia.

Para cada competição tenta uma lista de títulos de artigo (en/pt/es). Lê o HTML de /wiki/
(permitido pelo robots.txt), procura tabelas 'wikitable' com coluna Time/Clube/Team/Equipo
e guarda o texto e o alvo do link de cada clube. O casamento com os clubes do banco é feito
em montar_banco.py (por nome, dentro do país).

Saída: coleta/intermediario/wiki_participantes.json
  {competicao_id: {"url": ..., "clubes": [{"texto":..., "titulo":...}, ...]}}
"""
from __future__ import annotations

import re
import urllib.parse

from bs4 import BeautifulSoup

import comum

S = "2025–26"
PAGINAS: dict[str, list[tuple[str, str]]] = {
    # Brasil
    "bra-2": [("en", "2026 Campeonato Brasileiro Série B"), ("pt", "Campeonato Brasileiro de Futebol de 2026 – Série B")],
    "bra-3": [("en", "2026 Campeonato Brasileiro Série C"), ("pt", "Campeonato Brasileiro de Futebol de 2026 – Série C")],
    "bra-4": [("en", "2026 Campeonato Brasileiro Série D"), ("pt", "Campeonato Brasileiro de Futebol de 2026 – Série D")],
    "bra-1": [("en", "2026 Campeonato Brasileiro Série A")],
    "bra-copa": [("en", "2026 Copa do Brasil"), ("pt", "Copa do Brasil de Futebol de 2026")],
    "conmebol-libertadores": [("en", "2026 Copa Libertadores group stage"), ("en", "2026 Copa Libertadores")],
    "conmebol-sul-americana": [("en", "2026 Copa Sudamericana group stage"), ("en", "2026 Copa Sudamericana")],
    # estaduais (1ª divisão 2026)
    "est-sp": [("en", "2026 Campeonato Paulista"), ("pt", "Campeonato Paulista de Futebol de 2026")],
    "est-rj": [("en", "2026 Campeonato Carioca"), ("pt", "Campeonato Carioca de Futebol de 2026")],
    "est-mg": [("en", "2026 Campeonato Mineiro"), ("pt", "Campeonato Mineiro de Futebol de 2026")],
    "est-rs": [("en", "2026 Campeonato Gaúcho"), ("pt", "Campeonato Gaúcho de Futebol de 2026")],
    "est-pr": [("en", "2026 Campeonato Paranaense"), ("pt", "Campeonato Paranaense de Futebol de 2026")],
    "est-sc": [("en", "2026 Campeonato Catarinense"), ("pt", "Campeonato Catarinense de Futebol de 2026")],
    "est-ba": [("en", "2026 Campeonato Baiano"), ("pt", "Campeonato Baiano de Futebol de 2026")],
    "est-pe": [("en", "2026 Campeonato Pernambucano"), ("pt", "Campeonato Pernambucano de Futebol de 2026")],
    "est-ce": [("en", "2026 Campeonato Cearense"), ("pt", "Campeonato Cearense de Futebol de 2026")],
    "est-go": [("en", "2026 Campeonato Goiano"), ("pt", "Campeonato Goiano de Futebol de 2026")],
    "est-pa": [("en", "2026 Campeonato Paraense"), ("pt", "Campeonato Paraense de Futebol de 2026")],
    "est-al": [("en", "2026 Campeonato Alagoano"), ("pt", "Campeonato Alagoano de Futebol de 2026")],
    "est-am": [("en", "2026 Campeonato Amazonense"), ("pt", "Campeonato Amazonense de Futebol de 2026")],
    "est-ap": [("en", "2026 Campeonato Amapaense"), ("pt", "Campeonato Amapaense de Futebol de 2026")],
    "est-ac": [("en", "2026 Campeonato Acreano"), ("pt", "Campeonato Acriano de Futebol de 2026")],
    "est-df": [("en", "2026 Campeonato Brasiliense"), ("pt", "Campeonato Brasiliense de Futebol de 2026")],
    "est-es": [("en", "2026 Campeonato Capixaba"), ("pt", "Campeonato Capixaba de Futebol de 2026")],
    "est-ma": [("en", "2026 Campeonato Maranhense"), ("pt", "Campeonato Maranhense de Futebol de 2026")],
    "est-mt": [("en", "2026 Campeonato Mato-Grossense"), ("pt", "Campeonato Mato-Grossense de Futebol de 2026")],
    "est-ms": [("en", "2026 Campeonato Sul-Mato-Grossense"), ("pt", "Campeonato Sul-Mato-Grossense de Futebol de 2026")],
    "est-pb": [("en", "2026 Campeonato Paraibano"), ("pt", "Campeonato Paraibano de Futebol de 2026")],
    "est-pi": [("en", "2026 Campeonato Piauiense"), ("pt", "Campeonato Piauiense de Futebol de 2026")],
    "est-rn": [("en", "2026 Campeonato Potiguar"), ("pt", "Campeonato Potiguar de Futebol de 2026")],
    "est-ro": [("en", "2026 Campeonato Rondoniense"), ("pt", "Campeonato Rondoniense de Futebol de 2026")],
    "est-rr": [("en", "2026 Campeonato Roraimense"), ("pt", "Campeonato Roraimense de Futebol de 2026")],
    "est-se": [("en", "2026 Campeonato Sergipano"), ("pt", "Campeonato Sergipano de Futebol de 2026")],
    "est-to": [("en", "2026 Campeonato Tocantinense"), ("pt", "Campeonato Tocantinense de Futebol de 2026")],
    # Europa: divisões de baixo (as 1ªs vêm do Transfermarkt)
    "eng-2": [("en", f"{S} EFL Championship")],
    "eng-3": [("en", f"{S} EFL League One")],
    "eng-4": [("en", f"{S} EFL League Two")],
    "eng-5": [("en", f"{S} National League")],
    "esp-2": [("en", f"{S} Segunda División")],
    "ita-2": [("en", f"{S} Serie B")],
    "deu-2": [("en", f"{S} 2. Bundesliga")],
    "deu-3": [("en", f"{S} 3. Liga")],
    "fra-2": [("en", f"{S} Ligue 2")],
    "prt-2": [("en", f"{S} Liga Portugal 2")],
    "nld-2": [("en", f"{S} Eerste Divisie")],
    "bel-2": [("en", f"{S} Challenger Pro League")],
    "sco-2": [("en", f"{S} Scottish Championship")],
    "tur-2": [("en", f"{S} TFF 1. Lig")],
    "aut-1": [("en", f"{S} Austrian Football Bundesliga")],
    "che-1": [("en", f"{S} Swiss Super League")],
    "irl-1": [("en", "2026 League of Ireland Premier Division")],
    "cyp-1": [("en", f"{S} Cypriot First Division")],
    "bgr-1": [("en", f"{S} First Professional Football League (Bulgaria)")],
    "hun-1": [("en", f"{S} Nemzeti Bajnokság I")],
    "svk-1": [("en", f"{S} Slovak First Football League")],
    "svn-1": [("en", f"{S} Slovenian PrvaLiga")],
    "isr-1": [("en", f"{S} Israeli Premier League")],
    "fin-1": [("en", "2026 Veikkausliiga")],
    "isl-1": [("en", "2026 Besta deild karla")],
    "kaz-1": [("en", "2026 Kazakhstan Premier League")],
    "bih-1": [("en", f"{S} Premier League of Bosnia and Herzegovina")],
    "geo-1": [("en", "2026 Erovnuli Liga")],
    # América do Sul / Norte / Ásia / África
    "chl-1": [("en", "2026 Chilean Primera División"), ("en", "2026 Liga de Primera")],
    "ury-1": [("en", "2026 Uruguayan Primera División season"), ("en", "2026 Uruguayan Primera División")],
    "pry-1": [("en", "2026 Paraguayan Primera División season"), ("en", "2026 Paraguayan Primera División")],
    "per-1": [("en", "2026 Liga 1 (Peru)")],
    "ecu-1": [("en", "2026 Ecuadorian Serie A"), ("en", "2026 LigaPro Serie A")],
    "bol-1": [("en", "2026 Bolivian Primera División season"), ("en", "2026 Bolivian Primera División")],
    "ven-1": [("en", "2026 Venezuelan Primera División season"), ("en", "2026 Venezuelan Primera División")],
    "arg-2": [("en", "2026 Primera Nacional")],
    "col-2": [("en", "2026 Categoría Primera B season"), ("en", "2026 Categoría Primera B")],
    "usa-2": [("en", "2026 USL Championship season")],
    # Japão: o jogo começa em jan/2026 já com a estrutura 2026/27 (outono–primavera); o torneio especial
    # de fev–jun/2026 (100 Year Vision League) juntava J2+J3 em 40 clubes e não serve de divisão
    "jpn-1": [("en", "2026–27 J1 League")],
    "jpn-2": [("en", "2026–27 J2 League")],
    "jpn-3": [("en", "2026–27 J3 League")],
    "jpn-4": [("en", "2026–27 Japan Football League")],
    "per-2": [("en", "2026 Liga 2 (Peru)")],
    "chn-1": [("en", "2026 Chinese Super League")],
    "kor-1": [("en", "2026 K League 1")],
    "qat-1": [("en", f"{S} Qatar Stars League")],
    "are-1": [("en", f"{S} UAE Pro League")],
    "egy-1": [("en", f"{S} Egyptian Premier League")],
    "mar-1": [("en", f"{S} Botola Pro")],
    "zaf-1": [("en", f"{S} South African Premiership")],
    "tun-1": [("en", f"{S} Tunisian Ligue Professionnelle 1")],
    "dza-1": [("en", f"{S} Algerian Ligue Professionnelle 1")],
    "ind-1": [("en", f"{S} Indian Super League")],
    "aus-1": [("en", f"{S} A-League Men")],
    "ury-1": [("es", "Campeonato Uruguayo de Primera División 2026"), ("es", "Campeonato Uruguayo 2026")],
    "pry-1": [("es", "Campeonato Paraguayo de Primera División 2026"), ("es", "Primera División de Paraguay 2026")],
    "bol-1": [("es", "División Profesional de Bolivia 2026"), ("es", "Campeonato de Primera División de Bolivia 2026")],
    "ven-1": [("es", "Liga FUTVE 2026"), ("es", "Primera División de Venezuela 2026")],
    "col-2": [("es", "Primera B de Colombia 2026"), ("es", "Torneo BetPlay Dimayor 2026")],
    "est-df": [("pt", "Campeonato Brasiliense de Futebol de 2026 – Primeira Divisão")],
    "est-rn": [("pt", "Campeonato Potiguar de Futebol de 2026 – Primeira Divisão")],
    "est-mt": [("pt", "Campeonato Mato-Grossense de Futebol de 2026 – Primeira Divisão")],
    "est-ms": [("pt", "Campeonato Sul-Mato-Grossense de Futebol de 2026 – Primeira Divisão")],
    "est-pi": [("pt", "Campeonato Piauiense de Futebol de 2026 – Primeira Divisão")],
    "est-ma": [("pt", "Campeonato Maranhense de Futebol de 2026 – Primeira Divisão")],
    "sau-2": [("en", f"{S} Saudi First Division League")],
}

CABECALHOS = re.compile(r"^(team|club|clube|equipe|time|equipo|club name|teams|clubs|participantes?)\b", re.I)


def _links_tabela(tab, col) -> list[dict]:
    out = []
    for tr in tab.find_all("tr")[1:]:
        cels = tr.find_all(["td", "th"])
        if len(cels) <= col:
            continue
        for c in cels[col:col + 2]:
            a = next((a for a in c.find_all("a") if "/wiki/" in a.get("href", "")
                      and ":" not in a.get("href", "").split("/wiki/", 1)[1]
                      and a.get_text(strip=True)), None)
            if a:
                tit = urllib.parse.unquote(a["href"].split("/wiki/", 1)[1]).split("#")[0].replace("_", " ")
                fl = tr.find("span", class_="flagicon")
                pais = None
                if fl:
                    fa = fl.find("a") or fl.find("img")
                    pais = (fa.get("title") or fa.get("alt")) if fa else None
                out.append({"texto": a.get_text(" ", strip=True), "titulo": tit, "pais": pais})
                break
    return out


def _clubes_da_pagina(html: str) -> list[dict]:
    """União das tabelas de classificação (Pos/Time/J/Pts); se não houver, a 1ª tabela com coluna Time."""
    soup = BeautifulSoup(html, "lxml")
    classif, primeira = [], None
    for tab in soup.select("table.wikitable"):
        linhas = tab.find_all("tr")
        if not linhas:
            continue
        cab = [c.get_text(" ", strip=True) for c in linhas[0].find_all(["th", "td"])]
        col = next((i for i, h in enumerate(cab) if CABECALHOS.match(h)), None)
        if col is None:
            continue
        resto = " ".join(cab[col + 1:]).lower().split()
        if any(x in resto for x in ("pld", "pts", "j", "pj", "jogos", "pontos", "gp", "p")) and len(cab) >= 6:
            classif.extend(_links_tabela(tab, col))
        elif primeira is None:
            primeira = _links_tabela(tab, col)
    lista = classif or primeira or []
    out, vistos = [], set()
    for x in lista:
        if x["titulo"] not in vistos:
            vistos.add(x["titulo"])
            out.append(x)
    return out


def main():
    res = comum.ler_json("wiki_participantes.json", {}) or {}
    for cid, cands in PAGINAS.items():
        urls = [comum.wiki_url(l, t) for l, t in cands]
        if res.get(cid, {}).get("clubes") and res[cid].get("url") in urls:
            continue   # (página trocada na lista acima: busca de novo)
        for lang, tit in cands:
            html = comum.wiki_html(lang, tit)
            if not html:
                continue
            cl = _clubes_da_pagina(html)
            if len(cl) >= 4:
                res[cid] = {"url": comum.wiki_url(lang, tit), "lang": lang, "clubes": cl}
                print(f"{cid}: {len(cl)} clubes ({lang}:{tit})")
                break
        else:
            print(f"{cid}: NADA")
    comum.salvar_json("wiki_participantes.json", res)


if __name__ == "__main__":
    main()
