"""Copa do Mundo 2026: as 48 seleções e seus grupos, pela Wikipedia (artigos 'Group A'..'Group L').

Saída: coleta/intermediario/wiki_copa2026.json  {"grupos": {"A": [{"texto","titulo"}...]}, "fontes": {...}}
Ranking FIFA: vem do transfermarkt-datasets (national_teams.fifa_ranking), lido no montar_banco.
"""
from __future__ import annotations

import comum
from coleta_participantes_v2 import _clubes_da_pagina


def main():
    res = {"grupos": {}, "fontes": {}}
    for g in "ABCDEFGHIJKL":
        tit = f"2026 FIFA World Cup Group {g}"
        html = comum.wiki_html("en", tit)
        if not html:
            print("sem página", tit)
            continue
        times = _clubes_da_pagina(html)[:4]
        res["grupos"][g] = times
        res["fontes"][g] = comum.wiki_url("en", tit)
        print(g, [t["texto"] for t in times])
    comum.salvar_json("wiki_copa2026.json", res)


if __name__ == "__main__":
    main()
