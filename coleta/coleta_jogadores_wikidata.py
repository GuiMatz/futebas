"""Jogadores do Wikidata: futebolistas (P106 Q937857) brasileiros (P27 Q155) OU que jogaram
(P54) por um clube brasileiro do banco, nascidos de 1955 a 2010.

Etapas (cada uma com cache e retomável):
  python coleta_jogadores_wikidata.py lista      -> jogadores_lista.json   {qid: {nasc, links}}
  python coleta_jogadores_wikidata.py detalhes   -> jogadores_wd.json      {qid: {...}}
A lista de estrangeiros depende de clubes_wikidata.json (coleta_clubes.py).
"""
from __future__ import annotations

import sys

import comum

ANO_MIN, ANO_MAX = 1955, 2010


def lista():
    js = comum.ler_json("jogadores_lista.json", {})
    import datetime as dt

    def faixa(ini: dt.date, fim: dt.date, prof=0):
        q = f"""SELECT ?p ?nasc ?links WHERE {{
          ?p wdt:P27 wd:Q155; wdt:P106 wd:Q937857; wdt:P569 ?nasc .
          FILTER(?nasc >= "{ini.isoformat()}T00:00:00Z"^^xsd:dateTime && ?nasc < "{fim.isoformat()}T00:00:00Z"^^xsd:dateTime)
          ?p wikibase:sitelinks ?links }}"""
        try:
            return comum.sparql(q)
        except RuntimeError:
            if prof > 3:
                raise
            meio = ini + (fim - ini) / 2
            print("   dividindo", ini, fim, flush=True)
            return faixa(ini, meio, prof + 1) + faixa(meio, fim, prof + 1)

    for ano in range(ANO_MIN, ANO_MAX + 1):
        rs = faixa(dt.date(ano, 1, 1), dt.date(ano + 1, 1, 1))
        for r in rs:
            js.setdefault(comum.qid(r["p"]), {"nasc": r["nasc"][:10], "links": int(r["links"]), "via": "BRA"})
        print(f"brasileiros nascidos em {ano}: {len(rs)}", flush=True)
    # estrangeiros (ou sem cidadania) que jogaram em clubes brasileiros do banco
    clubes = comum.ler_json("clubes_wikidata.json", {})
    br = [q for q, d in clubes.items() if (d.get("isoP") == "BRA")]
    print("clubes brasileiros para a busca de estrangeiros:", len(br))
    for lote in comum.lotes(sorted(br), 40):
        vals = " ".join("wd:" + c for c in lote)
        q = f"""SELECT DISTINCT ?p ?nasc ?links WHERE {{
          VALUES ?c {{ {vals} }}
          ?p wdt:P54 ?c; wdt:P106 wd:Q937857; wdt:P569 ?nasc .
          FILTER(?nasc >= "{ANO_MIN}-01-01T00:00:00Z"^^xsd:dateTime && ?nasc < "{ANO_MAX + 1}-01-01T00:00:00Z"^^xsd:dateTime)
          FILTER NOT EXISTS {{ ?p wdt:P27 wd:Q155 }}
          ?p wikibase:sitelinks ?links }}"""
        rs = comum.sparql(q)
        for r in rs:
            js.setdefault(comum.qid(r["p"]), {"nasc": r["nasc"][:10], "links": int(r["links"]), "via": "clube_BR"})
        print(f"  estrangeiros lote: {len(rs)}", flush=True)
    comum.salvar_json("jogadores_lista.json", js)
    print("total na lista:", len(js))


PREC = {"11": "dia", "10": "mes", "9": "ano"}


def consulta_lote(montar, lote, minimo=4):
    """Roda a consulta montar(vals) para o lote; se der timeout, divide o lote ao meio."""
    vals = " ".join("wd:" + q for q in lote)
    try:
        return comum.sparql(montar(vals))
    except RuntimeError:
        if len(lote) <= minimo:
            print("   lote pulado (timeout):", lote, flush=True)
            return []
        m = len(lote) // 2
        print(f"   dividindo lote de {len(lote)}", flush=True)
        return consulta_lote(montar, lote[:m], minimo) + consulta_lote(montar, lote[m:], minimo)


def sexo():
    """Completa P21 (sexo) de quem já foi detalhado antes desse campo existir."""
    out = comum.ler_json("jogadores_wd.json", {})
    faltam = sorted(q for q, x in out.items() if "sexo" not in x)
    print("jogadores sem P21 consultado:", len(faltam))
    for lote in comum.lotes(faltam, 300):
        rs = consulta_lote(lambda vals: f"""SELECT ?p ?s WHERE {{ VALUES ?p {{ {vals} }} ?p wdt:P21 ?s }}""", lote)
        achou = {}
        for r in rs:
            achou.setdefault(comum.qid(r["p"]), comum.qid(r["s"]))
        for q in lote:
            out[q]["sexo"] = achou.get(q)
    comum.salvar_json("jogadores_wd.json", out)
    from collections import Counter
    print("sexo:", Counter(x.get("sexo") for x in out.values()))


def detalhes():
    lst = comum.ler_json("jogadores_lista.json", {})
    out = comum.ler_json("jogadores_wd.json", {})
    faltam = sorted(q for q in lst if q not in out)
    print("jogadores para detalhar:", len(faltam))
    for n, lote in enumerate(comum.lotes(faltam, 100)):
        vals = " ".join("wd:" + q for q in lote)
        d = {q: {"qid": q, "cid": [], "pos": [], "pe": [], "apelido": [], "car": [], "sexo": None} for q in lote}
        qa = lambda vals: f"""SELECT ?p ?pt ?en ?nomeNasc ?nasc ?prec ?links ?tm ?img ?alt ?locL ?locUF ?locPais ?ptw ?enw WHERE {{
          VALUES ?p {{ {vals} }}
          OPTIONAL {{ ?p rdfs:label ?pt FILTER(lang(?pt)='pt') }}
          OPTIONAL {{ ?p rdfs:label ?en FILTER(lang(?en)='en') }}
          OPTIONAL {{ ?p wdt:P1477 ?nomeNasc }}
          OPTIONAL {{ ?p p:P569/psv:P569 ?nv . ?nv wikibase:timeValue ?nasc; wikibase:timePrecision ?prec }}
          OPTIONAL {{ ?p wikibase:sitelinks ?links }}
          OPTIONAL {{ ?p wdt:P2446 ?tm }}
          OPTIONAL {{ ?p wdt:P18 ?img }}
          OPTIONAL {{ ?p p:P2048/psn:P2048/wikibase:quantityAmount ?alt }}
          OPTIONAL {{ ?p wdt:P19 ?loc . OPTIONAL {{ ?loc rdfs:label ?locL FILTER(lang(?locL)='pt') }}
                     OPTIONAL {{ ?loc wdt:P17/wdt:P298 ?locPais }}
                     OPTIONAL {{ ?loc wdt:P131* ?e . ?e wdt:P300 ?locUF FILTER(STRSTARTS(?locUF,'BR-')) }} }}
          OPTIONAL {{ ?ptw schema:about ?p; schema:isPartOf <https://pt.wikipedia.org/> }}
          OPTIONAL {{ ?enw schema:about ?p; schema:isPartOf <https://en.wikipedia.org/> }}
        }}"""
        for r in consulta_lote(qa, lote):
            x = d[comum.qid(r["p"])]
            for c in ("pt", "en", "nomeNasc", "links", "tm", "img", "alt", "locL", "locUF", "locPais", "ptw", "enw"):
                if r.get(c) and not x.get(c):
                    x[c] = r[c]
            if r.get("nasc"):
                p = PREC.get(r.get("prec"), "ano")
                rank = {"dia": 3, "mes": 2, "ano": 1}[p]
                if rank > x.get("_rk", 0):
                    x["nasc"], x["prec"], x["_rk"] = r["nasc"][:10], p, rank
        qb = lambda vals: f"""SELECT ?p ?cid ?pos ?posL ?pe ?peL ?ap ?sx WHERE {{
          VALUES ?p {{ {vals} }}
          {{ ?p wdt:P27/wdt:P298 ?cid }} UNION
          {{ ?p wdt:P413 ?pos . OPTIONAL {{ ?pos rdfs:label ?posL FILTER(lang(?posL)='en') }} }} UNION
          {{ ?p wdt:P552 ?pe . OPTIONAL {{ ?pe rdfs:label ?peL FILTER(lang(?peL)='en') }} }} UNION
          {{ ?p wdt:P1449 ?ap FILTER(lang(?ap) IN ('pt','pt-br','en','mul')) }} UNION
          {{ ?p wdt:P21 ?sx }}
        }}"""
        for r in consulta_lote(qb, lote):
            x = d[comum.qid(r["p"])]
            if r.get("cid") and r["cid"] not in x["cid"]:
                x["cid"].append(r["cid"])
            if r.get("pos"):
                v = [comum.qid(r["pos"]), r.get("posL")]
                if v not in x["pos"]:
                    x["pos"].append(v)
            if r.get("pe"):
                v = r.get("peL") or comum.qid(r["pe"])
                if v not in x["pe"]:
                    x["pe"].append(v)
            if r.get("sx"):
                x["sexo"] = comum.qid(r["sx"])
            if r.get("ap") and r["ap"] not in x["apelido"]:
                x["apelido"].append(r["ap"])
        qc = lambda vals: f"""SELECT ?p ?st ?c ?cL ?cEn ?cPais ?ini ?fim ?jogos ?gols ?trans ?ord ?nt WHERE {{
          VALUES ?p {{ {vals} }}
          ?p p:P54 ?st . ?st ps:P54 ?c .
          OPTIONAL {{ ?c rdfs:label ?cL FILTER(lang(?cL)='pt') }}
          OPTIONAL {{ ?c rdfs:label ?cEn FILTER(lang(?cEn)='en') }}
          OPTIONAL {{ ?c wdt:P17/wdt:P298 ?cPais }}
          OPTIONAL {{ ?c wdt:P1532/wdt:P298 ?cPais }}
          OPTIONAL {{ ?st pq:P580 ?ini }} OPTIONAL {{ ?st pq:P582 ?fim }}
          OPTIONAL {{ ?st pq:P1350 ?jogos }} OPTIONAL {{ ?st pq:P1351 ?gols }}
          OPTIONAL {{ ?st pq:P1642 ?trans }} OPTIONAL {{ ?st pq:P1545 ?ord }}
          BIND(EXISTS {{ ?c wdt:P31/wdt:P279? wd:Q6979593 }} AS ?nt)
        }}"""
        vistos = set()
        for r in consulta_lote(qc, lote):
            st = r["st"]
            if st in vistos:
                continue
            vistos.add(st)
            x = d[comum.qid(r["p"])]
            x["car"].append({
                "clube": comum.qid(r["c"]), "nome": r.get("cL") or r.get("cEn"), "pais": r.get("cPais"),
                "ini": r.get("ini", "")[:4] or None, "fim": r.get("fim", "")[:4] or None,
                "jogos": r.get("jogos"), "gols": r.get("gols"),
                "emp": comum.qid(r.get("trans")) == "Q2914547" if r.get("trans") else False,
                "ord": r.get("ord"), "selecao": r.get("nt") == "true"})
        for q, x in d.items():
            x.pop("_rk", None)
            if not x.get("nasc"):
                x["nasc"], x["prec"] = lst[q]["nasc"], "dia"
            x["via"] = lst[q]["via"]
            out[q] = x
        if n % 10 == 0:
            comum.salvar_json("jogadores_wd.json", out)
            print(f"  detalhados: {len(out)}/{len(lst)}", flush=True)
    comum.salvar_json("jogadores_wd.json", out)
    print("total detalhados:", len(out))


if __name__ == "__main__":
    for etapa in sys.argv[1:] or ["lista", "detalhes", "sexo"]:
        globals()[etapa]()
