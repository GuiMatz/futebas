"""Divisões inferiores dos estaduais (2026): participantes pela Wikipedia em português.

Para cada UF tenta as páginas de temporada "Campeonato X de Futebol de 2026 - <divisão>" com
vários nomes de divisão (Série A2, Módulo II, Segunda Divisão, Série B1...). Se a página da
temporada não existir, tenta a página da competição ("Campeonato X de Futebol - <divisão>"),
que costuma listar os participantes da edição atual. Só /wiki/ (permitido pelo robots.txt),
cache em coleta/cache/, 1 req/s.

A ordem das divisões de cada estado sai da posição do sufixo em ORDEM (ex.: em SP a
"Segunda Divisão" vem depois da A3; no RS a "Série B" vem depois da A2).

Saída: coleta/intermediario/wiki_estaduais_inf.json
  {"est-sp-2": {"url", "lang", "nome", "uf", "divisao", "sufixo", "origem_lista": "2026"|"competicao"|"2025", "clubes": [...]}}
"""
from __future__ import annotations

import comum
import re

from bs4 import BeautifulSoup

from coleta_participantes_v2 import CABECALHOS, _clubes_da_pagina, _links_tabela


def _clubes_secao_atual(html: str) -> list[dict]:
    """Página da competição (sem ano): só aceita tabela dentro de seção cujo título fala da edição de 2026
    ou dos participantes atuais; listas históricas (campeões, todos os participantes) são ignoradas."""
    soup = BeautifulSoup(html, "lxml")
    titulo, out = "", []
    for el in soup.find_all(["h2", "h3", "table"]):
        if el.name in ("h2", "h3"):
            titulo = el.get_text(" ", strip=True).lower()
            continue
        if "wikitable" not in (el.get("class") or []):
            continue
        if not ("2026" in titulo or re.search(r"participantes|clubes (de|em) 20|equipes (de|em) 20|atua", titulo)):
            continue
        if re.search(r"campe|hist|títul|titul|artilh|final", titulo):
            continue
        linhas = el.find_all("tr")
        cab = [c.get_text(" ", strip=True) for c in linhas[0].find_all(["th", "td"])] if linhas else []
        col = next((i for i, h in enumerate(cab) if CABECALHOS.match(h)), None)
        if col is not None:
            out.extend(_links_tabela(el, col))
    vistos, res = set(), []
    for x in out:
        if x["titulo"] not in vistos:
            vistos.add(x["titulo"])
            res.append(x)
    return res if 3 <= len(res) <= 24 else []

BASE = {  # nome da competição na Wikipedia em português
    "AC": "Campeonato Acriano", "AL": "Campeonato Alagoano", "AM": "Campeonato Amazonense",
    "AP": "Campeonato Amapaense", "BA": "Campeonato Baiano", "CE": "Campeonato Cearense",
    "DF": "Campeonato Brasiliense", "ES": "Campeonato Capixaba", "GO": "Campeonato Goiano",
    "MA": "Campeonato Maranhense", "MG": "Campeonato Mineiro", "MS": "Campeonato Sul-Mato-Grossense",
    "MT": "Campeonato Mato-Grossense", "PA": "Campeonato Paraense", "PB": "Campeonato Paraibano",
    "PE": "Campeonato Pernambucano", "PI": "Campeonato Piauiense", "PR": "Campeonato Paranaense",
    "RJ": "Campeonato Carioca", "RN": "Campeonato Potiguar", "RO": "Campeonato Rondoniense",
    "RR": "Campeonato Roraimense", "RS": "Campeonato Gaúcho", "SC": "Campeonato Catarinense",
    "SE": "Campeonato Sergipano", "SP": "Campeonato Paulista", "TO": "Campeonato Tocantinense",
}
# sufixos de divisões abaixo da elite, na ordem de nível (o primeiro encontrado vira divisão 2...)
ORDEM = ["Série A2", "Série A-2", "Módulo II", "Divisão de Acesso", "Série B1", "Série A3", "Série B",
         "Segunda Divisão", "Série B2", "Terceira Divisão", "Série C", "Quarta Divisão"]
# o que é a elite em cada estado (para não confundir "Série B" com a 1ª divisão)
ELITE = {"SP": "Série A1", "RJ": "Série A", "MG": "Módulo I", "RS": "Série A", "SC": "Série A",
         "PE": "Série A1", "CE": "Série A", "PA": "Série A", "SE": "Série A1", "ES": "Série A"}


def main():
    res = {}
    elite = comum.ler_json("wiki_participantes.json", {}) or {}
    for uf, base in BASE.items():
        achados = []
        for suf in ORDEM:
            if uf == "SP" and suf == "Série B":
                continue
            pag = None
            for tit, temporada in ((f"{base} de Futebol de 2026 - {suf}", "2026"),
                                   (f"{base} de Futebol de 2026 – {suf}", "2026"),
                                   (f"{base} de Futebol - {suf}", "competicao"),
                                   (f"{base} de Futebol – {suf}", "competicao"),
                                   (f"{base} de Futebol de 2025 - {suf}", "2025"),
                                   (f"{base} de Futebol de 2025 – {suf}", "2025")):
                html = comum.wiki_html("pt", tit)
                if not html:
                    continue
                cl = _clubes_da_pagina(html) if temporada != "competicao" else _clubes_secao_atual(html)
                if len(cl) >= 3:
                    pag = (tit, temporada, cl)
                    break
            if pag:
                achados.append((suf, *pag))
        # mesma lista em dois sufixos (redirecionamento, ex. "Série A-2" -> "Série A2"): fica o primeiro
        # (lista de 2025 que se sobrepõe a outra já achada = mesma divisão com nome antigo)
        topo = {x["titulo"] for x in (elite.get(f"est-{uf.lower()}") or {}).get("clubes", [])}
        vistos, unicos, ja = [], [], set(topo)
        for suf, tit, temporada, cl in achados:
            chave = frozenset(x["titulo"] for x in cl)
            lim = 0.4 if temporada == "2025" else 0.8
            if any(len(chave & v) >= lim * len(chave) for v in vistos):
                continue
            vistos.append(chave)
            cl = [x for x in cl if x["titulo"] not in ja]   # clube já numa divisão acima em 2026
            ja |= {x["titulo"] for x in cl}
            if len(cl) >= 4:
                unicos.append((suf, tit, temporada, cl))
        for n, (suf, tit, temporada, cl) in enumerate(unicos, start=2):
            comp = f"est-{uf.lower()}-{n}"
            res[comp] = {"url": comum.wiki_url("pt", tit), "lang": "pt", "nome": f"{base} {suf}", "uf": uf,
                         "divisao": n, "sufixo": suf, "origem_lista": temporada, "clubes": cl}
            if temporada == "2025":
                res[comp]["observacoes"] = "sem página de 2026: participantes da edição de 2025 (aproximação)"
            print(f"{comp}: {len(cl)} clubes ({tit}) [{temporada}]", flush=True)
        if not unicos:
            print(f"{uf}: nenhuma divisão inferior encontrada", flush=True)
    comum.salvar_json("wiki_estaduais_inf.json", res)
    print("divisões inferiores:", len(res))


if __name__ == "__main__":
    main()
