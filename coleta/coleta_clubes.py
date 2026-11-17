"""Resolve os clubes citados nas tabelas de classificação (participantes_brutos.json)
para itens do Wikidata e baixa os dados de cada clube.

Saídas (coleta/intermediario/):
  titulos_qid.json      {"en|Título": "Q123" | null}
  clubes_wikidata.json  {qid: {...dados do clube...}}
  participantes.json    [{competicao, ano, url, qids:[...], sem_wikidata:[texto,...]}]
"""
from __future__ import annotations

import re
import urllib.parse
from collections import Counter, defaultdict

import comum

CLUBE_TIPOS = "wd:Q476028 wd:Q847017 wd:Q103229495 wd:Q12973014"
# Q476028 clube de futebol, Q847017 clube esportivo, Q103229495 time de futebol, Q12973014 time esportivo
# (alguns itens de clubes brasileiros estão tipados só como "time de futebol" ou até "time feminino").


def lang_de_url(url: str | None) -> str:
    m = re.match(r"https://([a-z]+)\.wikipedia", url or "")
    return m.group(1) if m else "en"


def resolver_titulos(pares: set[tuple[str, str]]) -> dict[str, str | None]:
    mapa: dict[str, str | None] = comum.ler_json("titulos_qid.json", {})
    faltam = [p for p in pares if f"{p[0]}|{p[1]}" not in mapa]
    porlang = defaultdict(list)
    for lang, t in faltam:
        porlang[lang].append(t)
    for lang, ts in porlang.items():
        for lote in comum.lotes(sorted(ts), 120):
            vals = " ".join('"%s"@%s' % (t.replace("\\", "\\\\").replace('"', '\\"'), lang) for t in lote)
            q = f"""SELECT ?t ?item WHERE {{ VALUES ?t {{ {vals} }}
              ?a schema:name ?t; schema:isPartOf <https://{lang}.wikipedia.org/>; schema:about ?item }}"""
            achados = {r["t"]: comum.qid(r["item"]) for r in comum.sparql(q)}
            for t in lote:
                mapa[f"{lang}|{t}"] = achados.get(t)
    # títulos que são redirecionamentos: abrir a página (/wiki/) e ler wgWikibaseItemId
    pend = [k for k, v in mapa.items() if v is None and not k.endswith("|#")]
    print("títulos sem sitelink direto (redirecionamentos?):", len(pend))
    for i, k in enumerate(pend):
        lang, t = k.split("|", 1)
        h = comum.wiki_html(lang, t)
        if h:
            m = re.search(r'"wgWikibaseItemId":"(Q\d+)"', h)
            mapa[k] = m.group(1) if m else None
        if i % 50 == 0:
            print(f"  {i}/{len(pend)}", flush=True)
            comum.salvar_json("titulos_qid.json", mapa)
    comum.salvar_json("titulos_qid.json", mapa)
    return mapa


def classificar(qids: set[str]) -> dict[str, dict]:
    """Quais QIDs são clubes; país (ISO-3)."""
    out = {}
    for lote in comum.lotes(sorted(qids), 200):
        vals = " ".join("wd:" + q for q in lote)
        q = f"""SELECT ?item ?iso WHERE {{ VALUES ?item {{ {vals} }}
          VALUES ?tipo {{ {CLUBE_TIPOS} }}
          ?item wdt:P31/wdt:P279* ?tipo .
          FILTER NOT EXISTS {{ ?item wdt:P31 wd:Q6979593 }}
          FILTER NOT EXISTS {{ ?item wdt:P31 wd:Q1478437 }}
          OPTIONAL {{ ?item wdt:P17 ?pais . ?pais wdt:P298 ?iso }} }}"""
        for r in comum.sparql(q):
            out.setdefault(comum.qid(r["item"]), {"pais": r.get("iso")})
            if r.get("iso") and not out[comum.qid(r["item"])]["pais"]:
                out[comum.qid(r["item"])]["pais"] = r["iso"]
    return out


def detalhes(qids: list[str]) -> dict[str, dict]:
    cache = comum.ler_json("clubes_wikidata.json", {})
    faltam = [q for q in qids if q not in cache]
    for lote in comum.lotes(faltam, 60):
        vals = " ".join("wd:" + q for q in lote)
        q1 = f"""SELECT ?item ?pt ?en ?curto ?fund ?ext ?links ?tm ?cidL ?ufc ?isoP WHERE {{
          VALUES ?item {{ {vals} }}
          OPTIONAL {{ ?item rdfs:label ?pt FILTER(lang(?pt)='pt') }}
          OPTIONAL {{ ?item rdfs:label ?en FILTER(lang(?en)='en') }}
          OPTIONAL {{ ?item wdt:P1813 ?curto FILTER(lang(?curto) IN ('pt','en','es','mul')) }}
          OPTIONAL {{ ?item wdt:P571 ?fund }}
          OPTIONAL {{ ?item wdt:P576 ?ext }}
          OPTIONAL {{ ?item wikibase:sitelinks ?links }}
          OPTIONAL {{ ?item wdt:P7223 ?tm }}
          OPTIONAL {{ ?item wdt:P17 ?p . ?p wdt:P298 ?isoP }}
          OPTIONAL {{ ?item wdt:P159|wdt:P131 ?cid . ?cid rdfs:label ?cidL FILTER(lang(?cidL) IN ('pt'))
                     OPTIONAL {{ ?cid wdt:P131* ?est . ?est wdt:P300 ?ufc FILTER(STRSTARTS(?ufc,'BR-')) }} }}
        }}"""
        for r in comum.sparql(q1):
            k = comum.qid(r["item"])
            d = cache.setdefault(k, {"qid": k, "cores": [], "estadios": []})
            for c in ("pt", "en", "curto", "fund", "ext", "links", "tm", "cidL", "ufc", "isoP"):
                if r.get(c) and not d.get(c):
                    d[c] = r[c]
        q2 = f"""SELECT ?item ?cor ?corL ?hex ?est ?estL ?cap WHERE {{
          VALUES ?item {{ {vals} }}
          {{ ?item p:P6364|p:P462 ?st . ?st ps:P6364|ps:P462 ?cor . OPTIONAL {{ ?cor wdt:P465 ?hex }}
             OPTIONAL {{ ?cor rdfs:label ?corL FILTER(lang(?corL)='en') }} }}
          UNION
          {{ ?item wdt:P115 ?est . OPTIONAL {{ ?est rdfs:label ?estL FILTER(lang(?estL) IN ('pt')) }}
             OPTIONAL {{ ?est wdt:P1083 ?cap }} }}
        }}"""
        for r in comum.sparql(q2):
            k = comum.qid(r["item"])
            d = cache.setdefault(k, {"qid": k, "cores": [], "estadios": []})
            if r.get("cor"):
                c = [comum.qid(r["cor"]), r.get("corL"), r.get("hex")]
                if c not in d["cores"]:
                    d["cores"].append(c)
            if r.get("est"):
                e = [comum.qid(r["est"]), r.get("estL"), r.get("cap")]
                if e not in d["estadios"]:
                    d["estadios"].append(e)
        for q in lote:
            cache.setdefault(q, {"qid": q, "cores": [], "estadios": []})
        comum.salvar_json("clubes_wikidata.json", cache)
        print(f"  detalhes: {len(cache)}", flush=True)
    return cache


def atualizar_cores():
    """Refaz as cores de todos os clubes com P6364 (cor oficial) + P462 (cor)."""
    cache = comum.ler_json("clubes_wikidata.json", {})
    for lote in comum.lotes(sorted(cache), 100):
        vals = " ".join("wd:" + q for q in lote)
        q = f"""SELECT ?item ?cor ?corL ?hex ?ord WHERE {{ VALUES ?item {{ {vals} }}
          ?item p:P6364|p:P462 ?st . ?st ps:P6364|ps:P462 ?cor .
          OPTIONAL {{ ?cor wdt:P465 ?hex }} OPTIONAL {{ ?st pq:P1545 ?ord }}
          OPTIONAL {{ ?cor rdfs:label ?corL FILTER(lang(?corL)='en') }} }}"""
        novas = {}
        for r in comum.sparql(q):
            c = [comum.qid(r["cor"]), r.get("corL"), r.get("hex")]
            lst = novas.setdefault(comum.qid(r["item"]), [])
            if c not in lst:
                lst.append(c)
        for k in lote:
            if novas.get(k):
                cache[k]["cores"] = novas[k]
    comum.salvar_json("clubes_wikidata.json", cache)
    print("clubes com cores:", sum(1 for v in cache.values() if v.get("cores")), "de", len(cache))


def cores_do_uniforme():
    """Reserva para clubes sem cor no Wikidata: cores do uniforme titular no infobox do artigo
    (predefinição de uniforme: braço, camisa, braço, calção, meias). Guarda em 'cores_kit' + URL."""
    from bs4 import BeautifulSoup
    cache = comum.ler_json("clubes_wikidata.json", {})
    mapa = comum.ler_json("titulos_qid.json", {})
    titulo = {}
    for k, q in sorted(mapa.items(), key=lambda kv: not kv[0].startswith("en|")):
        if q and q not in titulo:
            titulo[q] = k.split("|", 1)
    feitos = 0
    for q, d in cache.items():
        if d.get("cores") or "cores_kit" in d or q not in titulo:
            continue
        lang, t = titulo[q]
        h = comum.wiki_html(lang, t)
        d["cores_kit"] = []
        if h:
            ib = BeautifulSoup(h, "html.parser").select_one("table.infobox")
            if ib:
                cs = []
                for dv in ib.find_all("div"):
                    m = re.search(r"background-color:\s*(#[0-9A-Fa-f]{6})", dv.get("style") or "")
                    if m:
                        cs.append(m.group(1).upper())
                if len(cs) >= 5:
                    casa = cs[:5]
                    cor1 = casa[1]
                    cor2 = next((c for c in [casa[3], casa[4], casa[0]] + cs[5:10] if c != cor1), None)
                    d["cores_kit"] = [cor1] + ([cor2] if cor2 else [])
                    d["cores_kit_url"] = comum.wiki_url(lang, t)
        feitos += 1
        if feitos % 50 == 0:
            comum.salvar_json("clubes_wikidata.json", cache)
            print("  uniformes lidos:", feitos, flush=True)
    comum.salvar_json("clubes_wikidata.json", cache)
    print("clubes com cor de uniforme:", sum(1 for v in cache.values() if v.get("cores_kit")))


def main():
    brutos = comum.ler_json("participantes_brutos.json", [])
    pares = set()
    for r in brutos:
        lang = lang_de_url(r["url"])
        for c in r["clubes"]:
            if c["titulo"]:
                pares.add((lang, c["titulo"]))
    camp = comum.ler_json("campeoes_brutos.json", [])
    for r in camp:
        for ls in [r.get("linha") or []] + ([r["infobox"]["campeao"] or []] + [r["infobox"]["vice"] or []] if r.get("infobox") else []):
            for c in ls:
                pares.add(("en", c["titulo"]))
    print("títulos distintos:", len(pares))
    mapa = resolver_titulos(pares)
    qids = {v for v in mapa.values() if v}
    tipos = comum.ler_json("clubes_tipos.json", {})
    novos = {q for q in qids if tipos.get(q) is None}   # inclui os rejeitados antes (tipos mudam)
    if novos:
        tipos.update({q: None for q in novos})
        cl = classificar(novos)
        for q in novos:
            tipos[q] = cl.get(q)
        comum.salvar_json("clubes_tipos.json", tipos)
    clubes = {q for q, v in tipos.items() if v}
    print("QIDs que são clubes:", len(clubes))
    det = detalhes(sorted(clubes))

    # textos de âncora por clube (nome popular) e participantes resolvidos
    ancoras = defaultdict(Counter)
    part = []
    for r in brutos:
        lang = lang_de_url(r["url"])
        qs, sem = [], []
        for c in r["clubes"]:
            if c["titulo"]:
                q = mapa.get(f"{lang}|{c['titulo']}")
                if q in clubes:
                    if q not in qs:
                        qs.append(q)
                    ancoras[q][c["texto"]] += 1
                elif q is None and c["texto"] and not re.match(r"^\d", c["texto"]):
                    sem.append(c["texto"])   # link vermelho (artigo inexistente): casa pelo nome
            else:
                sem.append(c["texto"])
        part.append({"competicao": r["competicao"], "ano": r["ano"], "url": r["url"],
                     "qids": qs, "sem_link": sem, "obs": r.get("obs"),
                     "so_brasileiros": r.get("so_brasileiros", False)})
    comum.salvar_json("participantes.json", part)

    # campeões: infobox da temporada > linha da lista de campeões
    def clubes_de(ls):
        out = []
        for c in ls or []:
            q = mapa.get(f"en|{c['titulo']}")
            if q in clubes and q not in out:
                out.append(q)
        return out
    campeoes = []
    for r in camp:
        lin = clubes_de(r.get("linha"))
        ib = r.get("infobox") or {}
        c_ib, v_ib = clubes_de(ib.get("campeao")), clubes_de(ib.get("vice"))
        campeao = vice = url = None
        conflito = None
        if c_ib:
            campeao, url = c_ib[0], ib["url"]
            vice = v_ib[0] if v_ib else None
            if lin and lin[0] != campeao:
                conflito = {"lista": lin[:2], "lista_url": r["url"]}
        if lin:
            if not campeao:
                campeao, url = lin[0], r["url"]
            if not vice and len(lin) > 1 and lin[1] != campeao:
                vice = lin[1]
                if url != r["url"]:
                    url = url  # campeão do infobox; vice da lista (as duas fontes ficam registradas)
        if campeao:
            campeoes.append({"competicao": r["competicao"], "ano": r["ano"], "campeao": campeao,
                             "vice": vice, "url": url, "url_lista": r["url"], "conflito": conflito})
    comum.salvar_json("campeoes.json", campeoes)
    print("campeões resolvidos:", len(campeoes), "conflitos lista×infobox:",
          sum(1 for c in campeoes if c["conflito"]))
    comum.salvar_json("clubes_ancoras.json", {q: dict(c) for q, c in ancoras.items()})
    print("participações:", sum(len(p["qids"]) for p in part),
          "sem link:", sum(len(p["sem_link"]) for p in part))


if __name__ == "__main__":
    import sys
    main()
    if "--sem-cores" not in sys.argv:
        atualizar_cores()
        cores_do_uniforme()
