"""Relatório de cobertura do banco v2 -> docs/cobertura.md

Por confederação, país e competição (temporada inicial): clubes, jogadores reais, gerados,
escudos e fotos. Também: base da força (EA FC 26 / valor de mercado / nível do mod / gerado)
e a data dos arquivos do mod usados nos elencos que não vêm do Transfermarkt.
"""
from __future__ import annotations

import json
import sqlite3
import time

import comum


def main():
    con = sqlite3.connect(comum.DADOS / "mundo.sqlite")
    c = con.cursor()
    q = lambda s, *a: c.execute(s, a).fetchall()  # noqa: E731
    one = lambda s, *a: c.execute(s, a).fetchone()[0]  # noqa: E731
    L = ["# Cobertura do banco FUTÉBAS (v2)", "",
         f"Gerado em {time.strftime('%Y-%m-%d %H:%M')} por `coleta/relatorio_cobertura.py` a partir de `dados/mundo.sqlite` "
         f"(versao_esquema={one('SELECT valor FROM meta WHERE chave=?', 'versao_esquema')}).", ""]
    tot = {k: one(s) for k, s in {
        "países": "SELECT COUNT(*) FROM paises", "clubes": "SELECT COUNT(*) FROM clubes",
        "clubes em alguma liga": "SELECT COUNT(DISTINCT clube_id) FROM ligas_clubes",
        "competições": "SELECT COUNT(*) FROM competicoes",
        "jogadores reais": "SELECT COUNT(*) FROM jogadores WHERE origem='real'",
        "jogadores gerados": "SELECT COUNT(*) FROM jogadores WHERE origem='gerado'",
        "reais com Transfermarkt": "SELECT COUNT(*) FROM jogadores WHERE tm_id IS NOT NULL",
        "reais com nota EA FC 26": "SELECT COUNT(*) FROM jogadores WHERE ea_id IS NOT NULL",
        "escudos": "SELECT COUNT(*) FROM clubes WHERE escudo_png IS NOT NULL",
        "fotos": "SELECT COUNT(*) FROM jogadores WHERE foto_png IS NOT NULL",
        "tabelas iniciais (linhas)": "SELECT COUNT(*) FROM tabelas_iniciais",
        "seleções na Copa 2026": "SELECT COUNT(*) FROM selecoes WHERE classificada_copa_2026=1",
        "Kings: ligas / times / elencos": "SELECT (SELECT COUNT(*) FROM kings_ligas)||' / '||(SELECT COUNT(*) FROM kings_times)||' / '||(SELECT COUNT(*) FROM kings_elencos)",
    }.items()}
    L += ["## Totais", "", "| Item | Total |", "|---|---:|"] + [f"| {k} | {v} |" for k, v in tot.items()] + [""]
    L += ["## Base da força", "", "| Base | Jogadores |", "|---|---:|"]
    for b, n in q("SELECT json_extract(conflito,'$.forca_base') b, COUNT(*) FROM jogadores GROUP BY b ORDER BY 2 DESC"):
        L.append(f"| {b} | {n} |")
    L += ["", "## Por confederação", "", "| Confederação | Países | Clubes | Reais | Gerados | Escudos | Fotos |", "|---|---:|---:|---:|---:|---:|---:|"]
    for r in q("""SELECT p.confederacao, COUNT(DISTINCT p.id), COUNT(DISTINCT cl.id),
                  (SELECT COUNT(*) FROM jogadores j JOIN clubes x ON x.id=j.clube_id JOIN paises y ON y.id=x.pais WHERE y.confederacao=p.confederacao AND j.origem='real'),
                  (SELECT COUNT(*) FROM jogadores j JOIN clubes x ON x.id=j.clube_id JOIN paises y ON y.id=x.pais WHERE y.confederacao=p.confederacao AND j.origem='gerado'),
                  SUM(cl.escudo_png IS NOT NULL),
                  (SELECT COUNT(*) FROM jogadores j JOIN clubes x ON x.id=j.clube_id JOIN paises y ON y.id=x.pais WHERE y.confederacao=p.confederacao AND j.foto_png IS NOT NULL)
                  FROM paises p JOIN clubes cl ON cl.pais=p.id GROUP BY p.confederacao ORDER BY 3 DESC"""):
        L.append("| " + " | ".join(str(x) for x in r) + " |")
    estim = set(comum.ler_json("ligas_estimadas.json", []) or [])
    L += ["", "## Por competição (temporada inicial)", "",
          "Origem dos participantes: **TM** = transfermarkt-datasets; **wiki** = artigo da temporada na Wikipedia; "
          "**mod** = estimado pelo nível/reputação do mod Brasfoot (estrutura de `conf_ligas_nacionais`/`conf_estadual`).", "",
          "| Competição | Nome | País | Origem | Clubes | Reais | Gerados | Escudos | Fotos |", "|---|---|---|---|---:|---:|---:|---:|---:|"]
    for r in q("""SELECT co.id, co.nome, COALESCE(co.pais, co.confederacao), COUNT(DISTINCT lc.clube_id),
                  SUM(j.origem='real'), SUM(j.origem='gerado'),
                  (SELECT COUNT(*) FROM ligas_clubes a JOIN clubes b ON b.id=a.clube_id WHERE a.competicao_id=co.id AND b.escudo_png IS NOT NULL),
                  SUM(j.foto_png IS NOT NULL)
                  FROM competicoes co JOIN ligas_clubes lc ON lc.competicao_id=co.id
                  LEFT JOIN jogadores j ON j.clube_id=lc.clube_id
                  GROUP BY co.id ORDER BY co.ambito='continental' DESC, co.pais, co.id"""):
        fonte = one("SELECT COUNT(*) FROM fontes WHERE 0") if False else None  # noqa: F841
        orig = "mod" if r[0] in estim else ("wiki" if r[0] in WIKI else "TM")
        L.append(f"| {r[0]} | {r[1]} | {r[2]} | {orig} | {r[3]} | {r[4] or 0} | {r[5] or 0} | {r[6]} | {r[7] or 0} |")
    L += ["", "## Por país (todos os clubes, inclusive sem liga)", "", "| País | Clubes | Sem liga | Reais | Gerados | Escudos | Fotos |", "|---|---:|---:|---:|---:|---:|---:|"]
    for r in q("""SELECT cl.pais, COUNT(*), SUM(cl.id NOT IN (SELECT clube_id FROM ligas_clubes)), SUM(cl.escudo_png IS NOT NULL)
                  FROM clubes cl GROUP BY cl.pais ORDER BY 2 DESC"""):
        jr = q("""SELECT SUM(origem='real'), SUM(origem='gerado'), SUM(foto_png IS NOT NULL) FROM jogadores j
                  JOIN clubes cl ON cl.id=j.clube_id WHERE cl.pais=?""", r[0])[0]
        L.append(f"| {r[0]} | {r[1]} | {r[2]} | {jr[0] or 0} | {jr[1] or 0} | {r[3]} | {jr[2] or 0} |")
    L += ["", "## Lacunas conhecidas", "",
          "- Elencos fora das 32 ligas do Transfermarkt vêm do mod Brasfoot; parte dos arquivos é antiga (ver data no `conflito.nascimento` e na fonte `mod Brasfoot 2026: <arquivo>`). Nascimento desses jogadores é estimado pela idade do arquivo (1º/jul).",
          "- Ligas marcadas **mod** têm participantes estimados por nível/reputação, não pela lista real.",
          "- Clubes reais citados na Wikipedia mas ausentes do mod e do Transfermarkt foram criados com elenco 100% gerado.",
          "- Fotos: só Série A + 5 grandes ligas europeias nesta rodada (`python coleta/fotos_tm.py`, retomável; `FOTOS_TODAS=1` para continuar pelas demais ligas)."]
    (comum.DOCS / "cobertura.md").write_text("\n".join(L) + "\n", "utf-8")
    print("docs/cobertura.md:", tot)


WIKI = set((comum.ler_json("wiki_participantes.json", {}) or {}).keys()) |     set((comum.ler_json("wiki_estaduais_inf.json", {}) or {}).keys())


def secao_estaduais(q):
    inf = comum.ler_json("wiki_estaduais_inf.json", {}) or {}
    estim = set(comum.ler_json("ligas_estimadas.json", []) or [])
    L = ["", "## Estaduais: todas as divisões (2026)", "",
         "Divisões inferiores coletadas por `coleta/coleta_estaduais_inferiores.py` (Wikipedia pt). Origem da lista: "
         "**2026** = artigo da temporada 2026; **competicao** = seção de participantes atuais do artigo da competição; "
         "**2025** = artigo de 2025 (aproximação, sem página de 2026); **mod (estimado)** = sem lista na Wikipedia: "
         "clubes restantes da UF pelo nível do mod Brasfoot. Formato padrão quando não há regulamento com fonte "
         "(ver `formatos.observacoes`).", "",
         "| UF | Competição | Nome | Divisão | Clubes | Criados sem elenco real | Origem da lista |", "|---|---|---|---:|---:|---:|---|"]
    for cid, nome, uf, div, n, novos in q("""SELECT co.id, co.nome, co.uf, IFNULL(co.divisao,1), COUNT(lc.clube_id),
                  SUM((SELECT COUNT(*) FROM jogadores j WHERE j.clube_id=lc.clube_id AND j.origem='real')=0)
                  FROM competicoes co LEFT JOIN ligas_clubes lc ON lc.competicao_id=co.id
                  WHERE co.ambito='estadual' GROUP BY co.id ORDER BY co.uf, IFNULL(co.divisao,1)"""):
        L.append(f"| {uf} | {cid} | {nome} | {div} | {n} | {novos or 0} | {(inf.get(cid) or {}).get('origem_lista') or ('mod (estimado)' if cid in estim else 'wiki/pesquisa')} |")
    sem = sorted({r[0] for r in q("SELECT uf FROM competicoes WHERE ambito='estadual'")} -
                 {r[0] for r in q("SELECT uf FROM competicoes WHERE ambito='estadual' AND divisao>1")})
    L += ["", f"UFs sem divisão inferior encontrada na Wikipedia: {', '.join(x for x in sem if x) or 'nenhuma'}."]
    return L


def secao_historia(q, one):
    try:
        one("SELECT COUNT(*) FROM lendas")
    except Exception:
        return []
    h = json.loads(one("SELECT valor FROM meta WHERE chave='historia'") or "{}")
    L = ["", "## Camada histórica (carreira, lendas, onze_historico, campeoes_historicos)", "",
         "Gerada por `coleta/historia.py` (chamado no fim do `montar_banco.py`). Fontes e fórmulas em `docs/fontes.md`.", "",
         "| Item | Total |", "|---|---:|"]
    itens = {
        "jogadores reais com carreira": "SELECT COUNT(DISTINCT jogador_id) FROM carreira WHERE jogador_id IS NOT NULL",
        "  via Wikidata (P54)": "SELECT COUNT(DISTINCT c.jogador_id) FROM carreira c JOIN fontes f ON f.id=c.fonte_id WHERE c.jogador_id IS NOT NULL AND f.tipo='wikidata'",
        "  via transfermarkt-datasets": "SELECT COUNT(DISTINCT c.jogador_id) FROM carreira c JOIN fontes f ON f.id=c.fonte_id WHERE c.jogador_id IS NOT NULL AND f.tipo='base_dados'",
        "linhas de carreira (ativos)": "SELECT COUNT(*) FROM carreira WHERE jogador_id IS NOT NULL",
        "linhas de carreira (lendas)": "SELECT COUNT(*) FROM carreira WHERE lenda_id IS NOT NULL",
        "linhas de carreira ligadas a clubes.id": "SELECT COUNT(*) FROM carreira WHERE clube_id IS NOT NULL",
        "lendas": "SELECT COUNT(*) FROM lendas",
        "lendas com foto sem fundo": "SELECT COUNT(*) FROM lendas WHERE foto_png IS NOT NULL",
        "lendas só do FUT.AE (sem Wikidata)": "SELECT COUNT(*) FROM lendas WHERE wikidata IS NULL",
        "clubes com melhor 11 histórico": "SELECT COUNT(DISTINCT clube_id) FROM onze_historico",
        "  dos quais com jogador em atividade no 11": "SELECT COUNT(DISTINCT clube_id) FROM onze_historico WHERE jogador_id IS NOT NULL",
        "campeões históricos (linhas)": "SELECT COUNT(*) FROM campeoes_historicos",
        "competições com campeões": "SELECT COUNT(DISTINCT competicao_id) FROM campeoes_historicos",
        "linhas com vice": "SELECT COUNT(*) FROM campeoes_historicos WHERE vice_nome IS NOT NULL",
        "linhas com campeão ligado a clubes.id/país": "SELECT COUNT(*) FROM campeoes_historicos WHERE campeao_clube_id IS NOT NULL",
    }
    for k, s in itens.items():
        L.append(f"| {k} | {one(s)} |")
    L += ["", f"Clubes do banco com QID do Wikidata (casamento por nome/tm_id): {h.get('clubes_com_qid', '?')}; "
          f"jogadores reais casados com o Wikidata: {h.get('ativos_casados_wikidata', '?')}.", "",
          "### Campeões por competição", "", "| Competição | Nome | Temporadas | 1ª | Última | Com vice |", "|---|---|---:|---|---|---:|"]
    for r in q("""SELECT ch.competicao_id, co.nome, COUNT(DISTINCT ch.temporada), MIN(ch.temporada), MAX(ch.temporada),
                  SUM(ch.vice_nome IS NOT NULL) FROM campeoes_historicos ch JOIN competicoes co ON co.id=ch.competicao_id
                  GROUP BY ch.competicao_id ORDER BY co.ambito, ch.competicao_id"""):
        L.append("| " + " | ".join(str(x) for x in r) + " |")
    sem = q("""SELECT co.id FROM competicoes co WHERE co.ambito IN ('nacional','copa_nacional','continental','selecoes','estadual')
               AND (co.divisao IS NULL OR co.divisao<=1)
               AND co.id NOT IN (SELECT competicao_id FROM campeoes_historicos) ORDER BY co.id""")
    L += ["", f"Competições de 1º nível/copas sem campeões históricos ({len(sem)}): " + ", ".join(r[0] for r in sem) + "."]
    L += ["", "### Lendas por país (top 20)", "", "| País | Lendas | Força de auge média |", "|---|---:|---:|"]
    for r in q("SELECT pais, COUNT(*), ROUND(AVG(forca_auge),1) FROM lendas GROUP BY pais ORDER BY 2 DESC LIMIT 20"):
        L.append("| " + " | ".join(str(x) for x in r) + " |")
    return L

if __name__ == "__main__":
    main()
