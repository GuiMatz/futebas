"""Clássicos/rivalidades entre clubes do banco. Duas fontes:

1. Wikidata: itens "rivalidade entre times" (Q40434727 e subclasses) com exatamente dois
   participantes (P1923/P710), ambos clubes do banco.
2. Wikipedia (português): seção "Clássicos estaduais" da "Lista de clássicos de futebol do
   Brasil"; cada item com dois links que resolvem para clubes do banco vira um par.

Saída: coleta/intermediario/rivais.json  [[qid_a, qid_b, url_fonte], ...]
"""
import re

from bs4 import BeautifulSoup

import comum
from coleta_clubes import resolver_titulos
from coleta_participantes import titulo_de_href

PAGINA = ("pt", "Lista de clássicos de futebol do Brasil")   # seção "Clássicos estaduais"


def de_wikidata(clubes):
    pares = {}
    for lote in comum.lotes(sorted(clubes), 150):
        vals = " ".join("wd:" + q for q in lote)
        q = f"""SELECT ?d (SAMPLE(?a) AS ?x) (SAMPLE(?b) AS ?y) (COUNT(DISTINCT ?c) AS ?n) WHERE {{
          VALUES ?a {{ {vals} }}
          ?d wdt:P1923|wdt:P710 ?a . ?d wdt:P1923|wdt:P710 ?b . FILTER(?a != ?b)
          ?d wdt:P1923|wdt:P710 ?c .
          ?d wdt:P31/wdt:P279* wd:Q40434727 . }} GROUP BY ?d HAVING (COUNT(DISTINCT ?c) = 2)"""
        for r in comum.sparql(q):
            a, b = comum.qid(r["x"]), comum.qid(r["y"])
            if a in clubes and b in clubes:
                pares[tuple(sorted((a, b)))] = "https://www.wikidata.org/wiki/" + comum.qid(r["d"])
    return pares


def de_wikipedia(clubes):
    """Seção 'Clássicos estaduais' da lista de clássicos (pt): cada item tem os dois clubes como
    os dois primeiros links."""
    lang, t = PAGINA
    pares = {}
    h = comum.wiki_html(lang, t)
    if not h:
        return pares
    s = BeautifulSoup(h, "html.parser")
    corpo = s.select_one("#mw-content-text") or s
    dentro = False
    grupos = []
    for el in corpo.find_all(["h2", "h3", "li"]):
        if el.name == "h2":
            dentro = el.get_text(" ", strip=True).startswith("Clássicos estaduais")
            continue
        if not dentro or el.name != "li":
            continue
        ts = [titulo_de_href(a.get("href", "")) for a in el.find_all("a")
              if not a.find("img") and not a.find_parent("sup")]
        ts = [x for x in ts if x][:6]
        if len(ts) >= 2:
            grupos.append(ts)
    mapa = resolver_titulos({(lang, x) for g in grupos for x in g})
    for g in grupos:
        qs = list(dict.fromkeys(q for x in g if (q := mapa.get(f"{lang}|{x}")) in clubes))[:2]
        if len(qs) == 2:
            pares.setdefault(tuple(sorted(qs)), comum.wiki_url(lang, t))
    print(f"{t}: {len(grupos)} clássicos estaduais, {len(pares)} entre clubes do banco")
    return pares


def de_artigos_de_classico(clubes):
    """Clássicos que a lista só cita como link para o artigo do clássico (ex.: Fla-Flu): resolve
    o artigo no Wikidata e usa os participantes (P1923/P710) quando são exatamente dois clubes do banco."""
    lang, t = PAGINA
    h = comum.wiki_html(lang, t)
    if not h:
        return {}
    corpo = BeautifulSoup(h, "html.parser").select_one("#mw-content-text")
    titulos = set()
    for a in corpo.find_all("a"):
        x = titulo_de_href(a.get("href", ""))
        if x and not a.find("img"):
            titulos.add(x)
    mapa = resolver_titulos({(lang, x) for x in titulos})
    itens = {q: x for x in titulos if (q := mapa.get(f"{lang}|{x}")) and q not in clubes}
    pares = {}
    for lote in comum.lotes(sorted(itens), 150):
        vals = " ".join("wd:" + q for q in lote)
        q = f"""SELECT ?d ?c WHERE {{ VALUES ?d {{ {vals} }} ?d wdt:P1923|wdt:P710 ?c . }}"""
        part = {}
        for r in comum.sparql(q):
            part.setdefault(comum.qid(r["d"]), set()).add(comum.qid(r["c"]))
        for d, cs in part.items():
            cs = sorted(c for c in cs if c in clubes)
            if len(cs) == 2 and len(part[d]) == 2:
                pares.setdefault(tuple(cs), comum.wiki_url(lang, itens[d]))
    print(f"artigos de clássico com 2 clubes do banco no Wikidata: {len(pares)}")
    # reserva: infobox do próprio artigo do clássico (os dois primeiros clubes linkados)
    cand = [x for x in titulos if re.match(r"(Cl[aá]ssico|D[eé]rbi|Fla-|Choque|Majestoso|San-S|Gre-Nal|Atletiba|Ba-Vi|"
                                           r"Re-Pa|Rei do|Vovô|Clássico)", x)]
    grupos = {}
    for x in sorted(cand):
        hh = comum.wiki_html(lang, x)
        if not hh:
            continue
        ib = BeautifulSoup(hh, "html.parser").select_one("table.infobox")
        if not ib:
            continue
        ts = [titulo_de_href(a.get("href", "")) for a in ib.find_all("a") if not a.find("img")]
        grupos[x] = [y for y in ts if y][:12]
    mapa2 = resolver_titulos({(lang, y) for g in grupos.values() for y in g})
    novos = 0
    for x, g in grupos.items():
        qs = list(dict.fromkeys(q for y in g if (q := mapa2.get(f"{lang}|{y}")) in clubes))[:2]
        if len(qs) == 2 and tuple(sorted(qs)) not in pares:
            pares[tuple(sorted(qs))] = comum.wiki_url(lang, x)
            novos += 1
    print(f"artigos de clássico lidos: {len(grupos)}; pares novos pelo infobox: {novos}")
    return pares


# Clássicos tradicionais conferidos pelo próprio artigo (pt) de cada clássico; clubes pelo rótulo pt do Wikidata.
MANUAIS = [
    ("Fla-Flu", "Clube de Regatas do Flamengo", "Fluminense Football Club"),
    ("Clássico dos Milhões", "Clube de Regatas do Flamengo", "Club de Regatas Vasco da Gama"),
    ("Clássico da Rivalidade", "Clube de Regatas do Flamengo", "Botafogo de Futebol e Regatas"),
    ("Clássico dos Gigantes", "Club de Regatas Vasco da Gama", "Fluminense Football Club"),
    ("Clássico da Amizade", "Club de Regatas Vasco da Gama", "Botafogo de Futebol e Regatas"),
    ("Clássico Vovô", "Botafogo de Futebol e Regatas", "Fluminense Football Club"),
    ("Derby Paulista", "Sport Club Corinthians Paulista", "Sociedade Esportiva Palmeiras"),
    ("Majestoso", "Sport Club Corinthians Paulista", "São Paulo Futebol Clube"),
    ("Choque-Rei", "Sociedade Esportiva Palmeiras", "São Paulo Futebol Clube"),
    ("Clássico Alvinegro", "Sport Club Corinthians Paulista", "Santos Futebol Clube"),
    ("San-São", "Santos Futebol Clube", "São Paulo Futebol Clube"),
    ("Clássico da Saudade", "Sociedade Esportiva Palmeiras", "Santos Futebol Clube"),
    ("Gre-Nal", "Grêmio Foot-Ball Porto Alegrense", "Sport Club Internacional"),
    ("Clássico Mineiro", "Clube Atlético Mineiro", "Cruzeiro Esporte Clube"),
    ("Atletiba", "Club Athletico Paranaense", "Coritiba Foot Ball Club"),
    ("Clássico das Multidões", "Sport Club do Recife", "Santa Cruz Futebol Clube"),
    ("Clássico dos Clássicos", "Sport Club do Recife", "Clube Náutico Capibaribe"),
    ("Ba-Vi", "Esporte Clube Bahia", "Esporte Clube Vitória"),
    ("Clássico-Rei", "Ceará Sporting Club", "Fortaleza Esporte Clube"),
    ("Re-Pa", "Clube do Remo", "Paysandu Sport Club"),
]


def manuais(clubes):
    por_rotulo = {}
    for q, d in clubes.items():
        for k in ("pt", "en"):
            if d.get(k):
                por_rotulo.setdefault(d[k], q)
    pares = {}
    for artigo, a, b in MANUAIS:
        qa, qb = por_rotulo.get(a), por_rotulo.get(b)
        if not (qa and qb):
            print("  clássico manual sem clube no banco:", artigo, a if not qa else b)
            continue
        if not comum.wiki_html("pt", artigo):
            print("  artigo do clássico não encontrado:", artigo)
            continue
        pares[tuple(sorted((qa, qb)))] = comum.wiki_url("pt", artigo)
    return pares


def main():
    clubes = comum.ler_json("clubes_wikidata.json", {})
    pares = de_wikidata(clubes)
    print("pares do Wikidata:", len(pares))
    for k, v in de_wikipedia(clubes).items():
        pares.setdefault(k, v)
    for k, v in de_artigos_de_classico(clubes).items():
        pares.setdefault(k, v)
    for k, v in manuais(clubes).items():
        pares.setdefault(k, v)
    comum.salvar_json("rivais.json", [[a, b, u] for (a, b), u in sorted(pares.items())])
    print("pares de rivais (total):", len(pares))


if __name__ == "__main__":
    main()
