"""FUTÉBAS: transferências 2026/27 e técnicos atuais pelo Wikidata (complemento do transfermarkt-datasets,
cujas atualizações pararam em 06/07/2026 — README oficial do projeto).

Saída: coleta/intermediario/transferencias_wd_2026.json
  {"transferencias": [{jogador_qid, nome, nasc, tm, clube_qid, clube_tm, clube_nome, inicio, emprestimo}],
   "tecnicos": [{clube_qid, clube_tm, tecnico_qid, nome, pais, inicio}]}
Lido por montar_banco.py (passo "transferências 2026/27"). Cache em coleta/cache/query.wikidata.org.
Uso: python coleta_transferencias.py
"""
from __future__ import annotations

import datetime as dt

import comum

INI = dt.date(2026, 5, 1)
FIM = dt.date(2026, 11, 12)

# consulta por lote de clubes do banco (VALUES ?c): usa o índice do valor de P54/P286, bem mais leve que
# varrer todas as declarações P54 por data (que dava timeout no endpoint)
Q_TRANSF = lambda vals: f"""SELECT ?p ?pL ?nasc ?tm ?c ?ctm ?ini ?how WHERE {{
  VALUES ?c {{ {vals} }}
  ?st ps:P54 ?c ; pq:P580 ?ini . ?p p:P54 ?st .
  FILTER(?ini >= "{INI.isoformat()}T00:00:00Z"^^xsd:dateTime && ?ini < "{FIM.isoformat()}T00:00:00Z"^^xsd:dateTime)
  OPTIONAL {{ ?st pq:P1642 ?how }}
  OPTIONAL {{ ?p wdt:P569 ?nasc }} OPTIONAL {{ ?p wdt:P2446 ?tm }} OPTIONAL {{ ?c wdt:P7223 ?ctm }}
  OPTIONAL {{ ?p rdfs:label ?pL FILTER(lang(?pL) IN ('en','mul')) }}
}}"""

Q_TEC = lambda vals: f"""SELECT ?c ?ctm ?t ?tL ?pais ?ini WHERE {{
  VALUES ?c {{ {vals} }}
  ?c p:P286 ?st . ?st ps:P286 ?t ; pq:P580 ?ini .
  FILTER(?ini >= "2025-01-01T00:00:00Z"^^xsd:dateTime)
  FILTER NOT EXISTS {{ ?st pq:P582 ?fim }}
  OPTIONAL {{ ?c wdt:P7223 ?ctm }}
  OPTIONAL {{ ?t wdt:P27/wdt:P298 ?pais }}
  OPTIONAL {{ ?t rdfs:label ?tL FILTER(lang(?tL) IN ('en','mul')) }}
}}"""


def main():
    import historia
    qids = sorted(historia.mapa_clubes())          # clubes do banco atual com QID
    print("clubes com QID:", len(qids), flush=True)
    tr, vistos = [], set()
    for n, lote in enumerate(comum.lotes(qids, 150)):
        for r in historia.consulta_lote(Q_TRANSF, lote):
            k = (r["p"], r["c"], r["ini"])
            if k in vistos:
                continue
            vistos.add(k)
            tr.append({"jogador_qid": comum.qid(r["p"]), "nome": r.get("pL"), "nasc": (r.get("nasc") or "")[:10] or None,
                       "tm": r.get("tm"), "clube_qid": comum.qid(r["c"]), "clube_tm": r.get("ctm"),
                       "inicio": r["ini"][:10], "emprestimo": comum.qid(r.get("how")) == "Q2914547"})
        print(f"  lote {n}: {len(tr)} movimentos", flush=True)
    tec, vt = [], set()
    for lote in comum.lotes(qids, 300):
        for r in historia.consulta_lote(Q_TEC, lote):
            k = (r["c"], r["t"])
            if k in vt:
                continue
            vt.add(k)
            tec.append({"clube_qid": comum.qid(r["c"]), "clube_tm": r.get("ctm"), "tecnico_qid": comum.qid(r["t"]),
                        "nome": r.get("tL"), "pais": r.get("pais"), "inicio": r["ini"][:10]})
    comum.salvar_json("transferencias_wd_2026.json", {"transferencias": tr, "tecnicos": tec,
                                                      "consultado_em": comum.HOJE})
    print("transferências Wikidata 2026:", len(tr), "| técnicos com início 2025+:", len(tec))


if __name__ == "__main__":
    main()
