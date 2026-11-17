"""Camada histórica do FUTÉBAS (contrato v2.1 em docs/arquitetura-v2.md):
tabelas carreira, lendas, onze_historico e campeoes_historicos.

Fontes (todas com cache em coleta/cache/, User-Agent genérico, 1 req/s por domínio):
- Wikidata (SPARQL, API documentada): carreiras P54 com P580/P582/P1350/P1351, dados das lendas,
  vencedores (P1346) das temporadas (P3450) de cada competição;
- coleta/intermediario/jogadores_wd.json (v1: ~12 mil carreiras de jogadores ligados ao Brasil, reaproveitadas);
- coleta/intermediario/campeoes.json (v1: campeões e vices brasileiros 1995+ da Wikipedia);
- transfermarkt-datasets (transfers.csv + appearances.csv, CC0): carreira dos jogadores com tm_id que o
  Wikidata não cobre, e jogos/gols desde 2012;
- FUT.AE (data/memi.sqlite, só leitura): lendas 'lendas_br' e 'enciclopedia'.

Uso:
  python historia.py coletar [etapa...]   # rede (com cache); etapas: paises clubes jogadores carreiras
                                          # candidatos lendas competicoes campeoes fotos
  python historia.py aplicar              # grava as 4 tabelas no dados/mundo.sqlite existente
montar_banco.py chama aplicar(b) no fim, antes da validação.
"""
from __future__ import annotations

import datetime as dt
import io
import json
import math
import os
import re
import sqlite3
import sys
from collections import Counter, defaultdict

import comum
from comum import norm

UA_FONTE = "https://query.wikidata.org/"
WD = "https://www.wikidata.org/wiki/"
FUTAE_DB = os.path.join(os.environ.get("FUTAE_DIR", "FUT.AE/data"), "memi.sqlite")
TM_RAW = comum.CACHE / "transfermarkt-datasets" / "raw"
TM_URL = "https://github.com/dcaribou/transfermarkt-datasets"
FOTOS_LENDAS = comum.RAIZ / "assets" / "fotos" / "lendas"
ANO_ATUAL = 2026

PALAVRAS_VAZIAS = set("fc cf sc ec ac afc cd ca cs fk sk sv club clube clube de futebol futebol esporte esportiva "
                      "sociedade associacao associação de do da dos das the football calcio deportivo deportiva "
                      "1 2 u s a f c".split())


def chave_clube(s):
    t = [w for w in norm(s).split() if w not in PALAVRAS_VAZIAS]
    return " ".join(t) or norm(s)


def consulta_lote(montar, lote, minimo=4):
    vals = " ".join("wd:" + q for q in lote)
    try:
        return comum.sparql(montar(vals))
    except RuntimeError:
        if len(lote) <= minimo:
            print("   lote pulado (timeout):", lote[:3], flush=True)
            return []
        m = len(lote) // 2
        return consulta_lote(montar, lote[:m], minimo) + consulta_lote(montar, lote[m:], minimo)


def consulta_faixa(montar, ini: dt.date, fim: dt.date, prof=0):
    """montar(ini_iso, fim_iso) -> SPARQL; divide o intervalo de datas se der timeout."""
    try:
        return comum.sparql(montar(ini.isoformat(), fim.isoformat()))
    except RuntimeError:
        if prof > 4:
            print("   faixa pulada:", ini, fim, flush=True)
            return []
        meio = ini + (fim - ini) / 2
        return consulta_faixa(montar, ini, meio, prof + 1) + consulta_faixa(montar, meio, fim, prof + 1)


def db_ro():
    return sqlite3.connect(f"file:{comum.DADOS / 'mundo.sqlite'}?mode=ro", uri=True)


# =============================================================== coleta
def c_paises():
    rs = comum.sparql("""SELECT ?c ?iso WHERE { ?c wdt:P298 ?iso . ?c wdt:P31 ?t .
        VALUES ?t { wd:Q6256 wd:Q3624078 wd:Q15634554 wd:Q1763527 wd:Q46395 wd:Q161243 wd:Q3336843 } }""")
    m = {}
    for r in rs:
        m.setdefault(r["iso"], comum.qid(r["c"]))
    m.update({"ENG": "Q21", "SCO": "Q22", "WAL": "Q25", "NIR": "Q26", "GBR": "Q145", "NLD": "Q55"})
    comum.salvar_json("historia_paises.json", m)
    print("países com QID:", len(m))


def c_clubes():
    paises = comum.ler_json("historia_paises.json")
    con = db_ro()
    isos = sorted({r[0] for r in con.execute("SELECT DISTINCT pais FROM clubes")})
    con.close()
    out = comum.ler_json("historia_clubes_wd.json", {}) or {}
    for iso in isos:
        alvo = "GBR" if iso in ("ENG", "SCO", "WAL", "NIR") else iso
        if alvo in out or alvo not in paises:
            continue
        q = f"""SELECT ?c ?n ?tm (GROUP_CONCAT(DISTINCT ?l; separator="|") AS ?ls) WHERE {{
          ?c wdt:P31/wdt:P279? wd:Q476028; wdt:P17 wd:{paises[alvo]}; wikibase:sitelinks ?n .
          OPTIONAL {{ ?c wdt:P7223 ?tm }}
          OPTIONAL {{ {{ ?c rdfs:label ?l }} UNION {{ ?c skos:altLabel ?l }}
                     FILTER(lang(?l) IN ('en','pt','es','mul','de','fr','it')) }}
        }} GROUP BY ?c ?n ?tm"""
        try:
            rs = comum.sparql(q)
        except RuntimeError:
            print("  timeout clubes", iso, flush=True)
            continue
        lst = {}
        for r in rs:
            k = comum.qid(r["c"])
            x = lst.setdefault(k, {"q": k, "n": int(r["n"]), "tm": None, "ls": []})
            if r.get("tm"):
                x["tm"] = r["tm"]
            x["ls"] = sorted(set(x["ls"]) | set((r.get("ls") or "").split("|")) - {""})
        out[alvo] = list(lst.values())
        print(f"  clubes {alvo}: {len(lst)}", flush=True)
        comum.salvar_json("historia_clubes_wd.json", out)
    # UF dos clubes brasileiros (sede/localização -> estado)
    if "BRA_UF" not in out:
        rs = comum.sparql(f"""SELECT DISTINCT ?c ?uf WHERE {{ ?c wdt:P31/wdt:P279? wd:Q476028; wdt:P17 wd:Q155 .
            ?c wdt:P159|wdt:P131 ?loc . ?loc wdt:P131* ?e . ?e wdt:P300 ?uf FILTER(STRSTARTS(?uf,'BR-')) }}""")
        out["BRA_UF"] = {comum.qid(r["c"]): r["uf"][3:] for r in rs}
    comum.salvar_json("historia_clubes_wd.json", out)


def c_jogadores():
    """Futebolistas (homens) do Wikidata por data de nascimento 1975-2010: qid, nascimento, nome, tm, sitelinks."""
    out = comum.ler_json("historia_jogadores_lista.json", {}) or {}
    feitos = set(out.get("_anos", []))
    # (1975-1978 foram buscados por faixa de datas; o resto só nas datas de nascimento que existem no banco,
    #  com VALUES: bem mais rápido no endpoint)
    con = db_ro()
    datas = defaultdict(list)
    for (d,) in con.execute("SELECT DISTINCT substr(nascimento,1,10) FROM jogadores WHERE origem='real' AND nascimento IS NOT NULL"):
        if re.fullmatch(r"\d{4}-\d{2}-\d{2}", d or ""):
            datas[int(d[:4])].append(d)
    con.close()
    for ano in range(1975, 2011):
        if ano in feitos:
            continue
        def por_datas(lote):
            vals = " ".join(f'"{d}T00:00:00Z"^^xsd:dateTime' for d in lote)
            try:
                return comum.sparql(f"""SELECT ?p ?nasc ?l ?tm ?n WHERE {{
                  VALUES ?nasc {{ {vals} }}
                  ?p wdt:P569 ?nasc; wdt:P106 wd:Q937857; wikibase:sitelinks ?n .
                  FILTER NOT EXISTS {{ ?p wdt:P21 wd:Q6581072 }}
                  OPTIONAL {{ ?p wdt:P2446 ?tm }}
                  OPTIONAL {{ ?p rdfs:label ?l FILTER(lang(?l) IN ('en','mul')) }} }}""")
            except RuntimeError:
                if len(lote) <= 2:
                    print("   datas puladas:", lote, flush=True)
                    return []
                m = len(lote) // 2
                return por_datas(lote[:m]) + por_datas(lote[m:])
        rs = []
        for lote in comum.lotes(sorted(datas.get(ano, [])), 60):
            rs += por_datas(lote)
        for r in rs:
            q = comum.qid(r["p"])
            x = out.setdefault(q, {"nasc": r["nasc"][:10], "n": int(r["n"]), "tm": None, "l": []})
            if r.get("tm"):
                x["tm"] = r["tm"]
            if r.get("l") and r["l"] not in x["l"]:
                x["l"].append(r["l"])
        feitos.add(ano)
        out["_anos"] = sorted(feitos)
        comum.salvar_json("historia_jogadores_lista.json", out)
        print(f"  nascidos em {ano}: {len(rs)}", flush=True)


def casar_ativos(con=None):
    """jogador_id do banco -> qid (por tm_id; senão nascimento + nome)."""
    lst = comum.ler_json("historia_jogadores_lista.json", {}) or {}
    lst.pop("_anos", None)
    from rapidfuzz import fuzz
    por_tm, por_nasc = {}, defaultdict(list)
    for q, x in lst.items():
        if x.get("tm"):
            por_tm[str(x["tm"])] = q
        por_nasc[x["nasc"]].append(q)
    fechar = con is None
    con = con or db_ro()
    res = {}
    for jid, nome, nc, nasc, tm in con.execute(
            "SELECT id, nome, nome_conhecido, nascimento, tm_id FROM jogadores WHERE origem='real'"):
        if tm and str(tm) in por_tm:
            res[jid] = por_tm[str(tm)]
            continue
        if not nasc:
            continue
        melhor, ms = None, 0
        for q in por_nasc.get(nasc[:10], []):
            for l in lst[q]["l"]:
                for n in (nome, nc):
                    s = fuzz.token_set_ratio(norm(n), norm(l))
                    if s > ms:
                        melhor, ms = q, s
        if melhor and ms >= 85:
            res[jid] = melhor
    if fechar:
        con.close()
    return res


QC = lambda vals: f"""SELECT ?p ?st ?c ?cL ?cEn ?cPais ?ini ?fim ?jogos ?gols ?trans ?nt WHERE {{
  VALUES ?p {{ {vals} }}
  ?p p:P54 ?st . ?st ps:P54 ?c .
  OPTIONAL {{ ?c rdfs:label ?cL FILTER(lang(?cL)='pt') }}
  OPTIONAL {{ ?c rdfs:label ?cEn FILTER(lang(?cEn)='en') }}
  OPTIONAL {{ ?c wdt:P17/wdt:P298 ?cPais }}
  OPTIONAL {{ ?st pq:P580 ?ini }} OPTIONAL {{ ?st pq:P582 ?fim }}
  OPTIONAL {{ ?st pq:P1350 ?jogos }} OPTIONAL {{ ?st pq:P1351 ?gols }}
  OPTIONAL {{ ?st pq:P1642 ?trans }}
  BIND(EXISTS {{ ?c wdt:P31/wdt:P279? wd:Q6979593 }} AS ?nt)
}}"""


def _carreiras(qids, arquivo):
    out = comum.ler_json(arquivo, {}) or {}
    faltam = sorted(q for q in qids if q not in out)
    print(f"carreiras a buscar ({arquivo}):", len(faltam), flush=True)
    for n, lote in enumerate(comum.lotes(faltam, 120)):
        d = {q: [] for q in lote}
        vistos = set()
        for r in consulta_lote(QC, lote):
            if r["st"] in vistos:
                continue
            vistos.add(r["st"])
            d[comum.qid(r["p"])].append({
                "clube": comum.qid(r["c"]), "nome": r.get("cL") or r.get("cEn"), "pais": r.get("cPais"),
                "ini": (r.get("ini") or "")[:4] or None, "fim": (r.get("fim") or "")[:4] or None,
                "jogos": r.get("jogos"), "gols": r.get("gols"),
                "emp": comum.qid(r.get("trans")) == "Q2914547" if r.get("trans") else False,
                "selecao": r.get("nt") == "true"})
        out.update(d)
        if n % 20 == 0:
            comum.salvar_json(arquivo, out)
            print(f"  {len(out)} carreiras", flush=True)
    comum.salvar_json(arquivo, out)
    return out


def c_carreiras():
    casados = casar_ativos()
    comum.salvar_json("historia_ativos_qid.json", {str(k): v for k, v in casados.items()})
    print("jogadores ativos casados com o Wikidata:", len(casados), flush=True)
    v1 = comum.ler_json("jogadores_wd.json", {}) or {}
    _carreiras({q for q in casados.values() if q not in v1}, "historia_carreiras.json")


# ---------------------------------------------------------------- lendas
TOP_PAISES = ["BRA", "ARG", "ENG", "ESP", "ITA", "DEU", "FRA", "PRT", "NLD", "BEL", "URY", "MEX", "USA", "TUR",
              "SCO", "RUS", "UKR", "GRC", "COL", "CHL", "PRY", "JPN", "KOR", "SAU", "AUT", "CHE", "DNK", "SWE",
              "HRV", "SRB", "EGY", "MAR", "CZE", "POL", "ROU", "PER", "ECU", "NOR", "CHN", "AUS"][:30]


def mapa_clubes(con=None):
    """qid do Wikidata -> clubes.id, por tm_id e nome dentro do país (UF no Brasil)."""
    from rapidfuzz import fuzz
    wd = comum.ler_json("historia_clubes_wd.json", {}) or {}
    uf_wd = wd.get("BRA_UF", {})
    c2 = con or db_ro()
    db = c2.execute("SELECT id, nome, nome_completo, pais, uf, tm_id, IFNULL(reputacao,0) FROM clubes").fetchall()
    if con is None:
        c2.close()
    por_pais = defaultdict(list)
    for r in db:
        por_pais["GBR" if r[3] in ("ENG", "SCO", "WAL", "NIR") else r[3]].append(r)
    res, dono = {}, {}
    for pais, cls in por_pais.items():
        lst = wd.get(pais) or []
        por_tm = {str(x["tm"]): x for x in lst if x.get("tm")}
        idx = defaultdict(list)
        for x in lst:
            for l in x["ls"]:
                idx[chave_clube(l)].append(x)
        for cid, nome, nc, _p, uf, tm, rep in cls:
            esc = por_tm.get(str(tm)) if tm else None
            if not esc:
                cands = {}
                for n in (nome, nc):
                    if n:
                        for x in idx.get(chave_clube(n), []):
                            cands[x["q"]] = x
                if not cands and lst and len(chave_clube(nome)) > 3:
                    k = chave_clube(nc or nome)
                    melhores = sorted(((fuzz.token_sort_ratio(k, kk), kk) for kk in idx), reverse=True)[:2]
                    if melhores and melhores[0][0] >= 93 and (len(melhores) < 2 or melhores[1][0] < melhores[0][0]):
                        for x in idx[melhores[0][1]]:
                            cands[x["q"]] = x
                if pais == "BRA" and uf:
                    mesmo = {q: x for q, x in cands.items() if uf_wd.get(q) == uf}
                    if mesmo:
                        cands = mesmo
                    elif any(uf_wd.get(q) for q in cands):
                        cands = {q: x for q, x in cands.items() if not uf_wd.get(q)}
                esc = max(cands.values(), key=lambda x: x["n"]) if cands else None
            if esc:
                q = esc["q"]
                if q in dono and dono[q][1] >= rep:
                    continue
                if q in dono:
                    res.pop(dono[q][0], None)
                dono[q] = (cid, rep)
                res[cid] = q
    return {q: cid for cid, q in res.items()}


def c_candidatos():
    """Candidatos a lenda: (a) sitelinks >= 30, nascidos até 1992; (b) os 40 mais famosos de cada clube das
    1ªs divisões dos 30 maiores países; (c) Bola de Ouro; (d) lendas do FUT.AE (nome no Wikidata)."""
    cand = comum.ler_json("historia_candidatos.json", {}) or {}
    feito = set(cand.get("_etapas", []))
    if "a" not in feito:
        for ini in range(1880, 1993, 4):
            mont = lambda a, b: f"""SELECT ?p ?n WHERE {{ ?p wdt:P106 wd:Q937857; wdt:P569 ?d .
              FILTER(?d >= "{a}T00:00:00Z"^^xsd:dateTime && ?d < "{b}T00:00:00Z"^^xsd:dateTime)
              ?p wikibase:sitelinks ?n . FILTER(?n >= 30)
              FILTER NOT EXISTS {{ ?p wdt:P21 wd:Q6581072 }} }}"""
            rs = consulta_faixa(mont, dt.date(ini, 1, 1), dt.date(ini + 4, 1, 1))
            for r in rs:
                cand.setdefault(comum.qid(r["p"]), {"n": int(r["n"]), "via": []})["via"].append("fama")
            print(f"  famosos {ini}-{ini + 3}: {len(rs)}", flush=True)
        feito.add("a")
    if "c" not in feito:
        for prem in ("Q166177", "Q182529", "Q24896226"):   # Bola de Ouro, Melhor do Mundo FIFA, The Best
            for r in comum.sparql(f"""SELECT DISTINCT ?p ?n WHERE {{ ?p wdt:P166 wd:{prem}; wdt:P106 wd:Q937857;
                                     wikibase:sitelinks ?n . FILTER NOT EXISTS {{ ?p wdt:P21 wd:Q6581072 }} }}"""):
                x = cand.setdefault(comum.qid(r["p"]), {"n": int(r["n"]), "via": []})
                if "premio" not in x["via"]:
                    x["via"].append("premio")
        feito.add("c")
    if "b" not in feito:
        mp = mapa_clubes()
        con = db_ro()
        top = {r[0] for r in con.execute(
            f"""SELECT DISTINCT lc.clube_id FROM ligas_clubes lc JOIN competicoes c ON c.id=lc.competicao_id
                WHERE c.ambito='nacional' AND c.divisao=1 AND c.pais IN ({','.join('?' * len(TOP_PAISES))})""",
            TOP_PAISES)}
        top |= {r[0] for r in con.execute(
            "SELECT DISTINCT clube_id FROM ligas_clubes WHERE competicao_id IN ('est-sp','est-rj','est-mg','est-rs')")}
        con.close()
        alvo = sorted(q for q, cid in mp.items() if cid in top)
        print("clubes de elite com QID:", len(alvo), flush=True)
        for q in alvo:
            rs = comum.sparql(f"""SELECT ?p ?n WHERE {{ ?p wdt:P54 wd:{q}; wdt:P106 wd:Q937857; wikibase:sitelinks ?n .
                 ?p wdt:P569 ?d FILTER(YEAR(?d) < 1994) FILTER NOT EXISTS {{ ?p wdt:P21 wd:Q6581072 }} }}
                 ORDER BY DESC(?n) LIMIT 40""")
            for r in rs:
                x = cand.setdefault(comum.qid(r["p"]), {"n": int(r["n"]), "via": []})
                if "clube" not in x["via"]:
                    x["via"].append("clube")
        feito.add("b")
    if "d" not in feito:
        fut = lendas_futae()
        nomes = sorted({f["nome"] for f in fut})
        achou = {}
        for lote in comum.lotes(nomes, 60):
            vals = " ".join(json.dumps(n, ensure_ascii=False) + "@" + l for n in lote for l in ("en", "pt", "es"))
            rs = comum.sparql(f"""SELECT ?nome ?p ?n WHERE {{ VALUES ?nome {{ {vals} }}
                 {{ ?p rdfs:label ?nome }} UNION {{ ?p skos:altLabel ?nome }}
                 ?p wdt:P106 wd:Q937857; wikibase:sitelinks ?n . }}""")
            for r in rs:
                k = r["nome"]
                if int(r["n"]) > achou.get(k, ("", -1))[1]:
                    achou[k] = (comum.qid(r["p"]), int(r["n"]))
        for f in fut:
            if f["nome"] in achou:
                q, n = achou[f["nome"]]
                x = cand.setdefault(q, {"n": n, "via": []})
                if "futae" not in x["via"]:
                    x["via"].append("futae")
                x["futae"] = f["chave"]
        cand["_futae_qid"] = {f["chave"]: achou[f["nome"]][0] for f in fut if f["nome"] in achou}
        feito.add("d")
    cand["_etapas"] = sorted(feito)
    comum.salvar_json("historia_candidatos.json", cand)
    print("candidatos a lenda:", len([k for k in cand if not k.startswith("_")]))


def lendas_futae():
    try:
        con = sqlite3.connect(f"file:{FUTAE_DB}?mode=ro", uri=True)
    except sqlite3.Error:
        return []
    rs = con.execute("""SELECT p.id, p.name, p.display_name, p.birth_date, p.nationality, p.game_position, p.overall,
                               s.source, s.source_id
                        FROM players p JOIN player_sources s ON s.player_id = p.id
                        WHERE s.source IN ('lendas_br','enciclopedia')""").fetchall()
    con.close()
    out = []
    for pid, nome, disp, nasc, nac, pos, ov, src, sid in rs:
        out.append({"chave": f"{src}:{sid}", "futae_id": pid, "nome": nome, "conhecido": disp or nome, "nasc": nasc,
                    "nac": nac, "pos": pos, "overall": ov, "source": src})
    return out


QA = lambda vals: f"""SELECT ?p ?pt ?en ?nomeNasc ?nasc ?morte ?links ?tm ?img ?cid ?ptw ?enw WHERE {{
  VALUES ?p {{ {vals} }}
  OPTIONAL {{ ?p rdfs:label ?pt FILTER(lang(?pt)='pt') }}
  OPTIONAL {{ ?p rdfs:label ?en FILTER(lang(?en)='en') }}
  OPTIONAL {{ ?p wdt:P1477 ?nomeNasc }}
  OPTIONAL {{ ?p wdt:P569 ?nasc }} OPTIONAL {{ ?p wdt:P570 ?morte }}
  OPTIONAL {{ ?p wikibase:sitelinks ?links }}
  OPTIONAL {{ ?p wdt:P2446 ?tm }}
  OPTIONAL {{ ?p wdt:P18 ?img }}
  OPTIONAL {{ ?p wdt:P1532/wdt:P298 ?cid }}
  OPTIONAL {{ ?ptw schema:about ?p; schema:isPartOf <https://pt.wikipedia.org/> }}
  OPTIONAL {{ ?enw schema:about ?p; schema:isPartOf <https://en.wikipedia.org/> }}
}}"""
QB = lambda vals: f"""SELECT ?p ?cid ?pos ?posL ?ap ?pr WHERE {{
  VALUES ?p {{ {vals} }}
  {{ ?p wdt:P27/wdt:P298 ?cid }} UNION
  {{ ?p wdt:P413 ?pos . OPTIONAL {{ ?pos rdfs:label ?posL FILTER(lang(?posL)='en') }} }} UNION
  {{ ?p wdt:P1449 ?ap FILTER(lang(?ap) IN ('pt','en','mul')) }} UNION
  {{ ?p wdt:P166 ?pr VALUES ?pr {{ wd:Q166177 wd:Q182529 wd:Q24896226 }} }}
}}"""


def c_lendas():
    cand = comum.ler_json("historia_candidatos.json", {}) or {}
    v1 = comum.ler_json("jogadores_wd.json", {}) or {}
    ativos = set((comum.ler_json("historia_ativos_qid.json", {}) or {}).values())
    alvo = sorted(q for q in cand if not q.startswith("_") and q not in ativos)
    # v1: jogadores ligados ao Brasil já detalhados; os aposentados entram como candidatos (fama >= 6 sitelinks)
    out = comum.ler_json("historia_lendas_wd.json", {}) or {}
    faltam = [q for q in alvo if q not in out and q not in v1]
    print("lendas a detalhar:", len(faltam), flush=True)
    for n, lote in enumerate(comum.lotes(faltam, 100)):
        d = {q: {"qid": q, "cid": [], "pos": [], "apelido": [], "premios": []} for q in lote}
        for r in consulta_lote(QA, lote):
            x = d[comum.qid(r["p"])]
            for c in ("pt", "en", "nomeNasc", "links", "tm", "img", "ptw", "enw"):
                if r.get(c) and not x.get(c):
                    x[c] = r[c]
            if r.get("nasc") and not x.get("nasc"):
                x["nasc"] = r["nasc"][:10]
            if r.get("morte") and not x.get("morte"):
                x["morte"] = r["morte"][:10]
            if r.get("cid") and r["cid"] not in x["cid"]:
                x["cid"].insert(0, r["cid"])   # país esportivo (P1532) primeiro
        for r in consulta_lote(QB, lote):
            x = d[comum.qid(r["p"])]
            if r.get("cid") and r["cid"] not in x["cid"]:
                x["cid"].append(r["cid"])
            if r.get("pos"):
                v = [comum.qid(r["pos"]), r.get("posL")]
                if v not in x["pos"]:
                    x["pos"].append(v)
            if r.get("ap") and r["ap"] not in x["apelido"]:
                x["apelido"].append(r["ap"])
            if r.get("pr") and comum.qid(r["pr"]) not in x["premios"]:
                x["premios"].append(comum.qid(r["pr"]))
        out.update(d)
        if n % 10 == 0:
            comum.salvar_json("historia_lendas_wd.json", out)
            print(f"  detalhadas {len(out)}", flush=True)
    comum.salvar_json("historia_lendas_wd.json", out)
    # morte e prêmios dos jogadores v1 (não estavam no v1)
    v1q = [q for q in v1 if q not in ativos and q not in out]
    extra = comum.ler_json("historia_v1_extra.json", {}) or {}
    falt = [q for q in v1q if q not in extra]
    for lote in comum.lotes(falt, 300):
        d = {q: {} for q in lote}
        for r in consulta_lote(lambda vals: f"""SELECT ?p ?morte ?pr WHERE {{ VALUES ?p {{ {vals} }}
                 {{ ?p wdt:P570 ?morte }} UNION {{ ?p wdt:P166 ?pr VALUES ?pr {{ wd:Q166177 wd:Q182529 wd:Q24896226 }} }} }}""", lote):
            x = d[comum.qid(r["p"])]
            if r.get("morte"):
                x["morte"] = r["morte"][:10]
            if r.get("pr"):
                x.setdefault("premios", []).append(comum.qid(r["pr"]))
        extra.update(d)
    comum.salvar_json("historia_v1_extra.json", extra)
    _carreiras(faltam + [q for q in out if q not in v1], "historia_carreiras_lendas.json")


# ---------------------------------------------------------------- campeões
# competição do banco -> títulos de artigos da Wikipedia em inglês (o QID sai do sitelink)
COMPS = {
    "fifa-copa-do-mundo": ["FIFA World Cup"], "copa-america": ["Copa América"], "eurocopa": ["UEFA European Championship"],
    "caf-copa-africa": ["Africa Cup of Nations"], "afc-copa-asia": ["AFC Asian Cup"],
    "uefa-nations-league": ["UEFA Nations League"], "concacaf-nations-league": ["CONCACAF Nations League"],
    "finalissima": ["CONMEBOL–UEFA Cup of Champions"],
    "uefa-champions": ["UEFA Champions League"], "uefa-europa": ["UEFA Europa League"],
    "uefa-conference": ["UEFA Conference League"], "uefa-supercopa": ["UEFA Super Cup"],
    "conmebol-libertadores": ["Copa Libertadores"], "conmebol-sul-americana": ["Copa Sudamericana"],
    "conmebol-recopa": ["Recopa Sudamericana"], "concacaf-champions": ["CONCACAF Champions Cup"],
    "concacaf-leagues-cup": ["Leagues Cup"], "afc-champions-elite": ["AFC Champions League Elite"],
    "afc-champions-2": ["AFC Champions League Two"], "caf-champions": ["CAF Champions League"],
    "caf-confederacoes": ["CAF Confederation Cup"], "fifa-mundial-clubes": ["FIFA Club World Cup"],
    "intercontinental": ["Intercontinental Cup (football)"], "fifa-intercontinental": ["FIFA Intercontinental Cup"],
    "bra-1": ["Campeonato Brasileiro Série A"], "bra-2": ["Campeonato Brasileiro Série B"],
    "bra-3": ["Campeonato Brasileiro Série C"], "bra-4": ["Campeonato Brasileiro Série D"],
    "bra-copa": ["Copa do Brasil"], "bra-supercopa": ["Supercopa do Brasil"], "bra-copa-nordeste": ["Copa do Nordeste"],
    "bra-copa-verde": ["Copa Verde"], "bra-copa-sul-sudeste": ["Copa Sul-Sudeste"],
    "est-sp": ["Campeonato Paulista"], "est-rj": ["Campeonato Carioca"], "est-mg": ["Campeonato Mineiro"],
    "est-rs": ["Campeonato Gaúcho"], "est-pr": ["Campeonato Paranaense"], "est-sc": ["Campeonato Catarinense"],
    "est-ba": ["Campeonato Baiano"], "est-pe": ["Campeonato Pernambucano"], "est-ce": ["Campeonato Cearense"],
    "est-go": ["Campeonato Goiano"], "est-pa": ["Campeonato Paraense"], "est-al": ["Campeonato Alagoano"],
    "est-am": ["Campeonato Amazonense"], "est-ap": ["Campeonato Amapaense"], "est-ac": ["Campeonato Acreano"],
    "est-df": ["Campeonato Brasiliense"], "est-es": ["Campeonato Capixaba"], "est-ma": ["Campeonato Maranhense"],
    "est-mt": ["Campeonato Mato-Grossense"], "est-ms": ["Campeonato Sul-Mato-Grossense"],
    "est-pb": ["Campeonato Paraibano"], "est-pi": ["Campeonato Piauiense"], "est-rn": ["Campeonato Potiguar"],
    "est-ro": ["Campeonato Rondoniense"], "est-rr": ["Campeonato Roraimense"], "est-se": ["Campeonato Sergipano"],
    "est-to": ["Campeonato Tocantinense"],
    "eng-1": ["Premier League", "Football League First Division"], "eng-2": ["EFL Championship"],
    "eng-copa": ["FA Cup"], "eng-copa-liga": ["EFL Cup"], "eng-supercopa": ["FA Community Shield"],
    "esp-1": ["La Liga"], "esp-2": ["Segunda División"], "esp-copa": ["Copa del Rey"], "esp-supercopa": ["Supercopa de España"],
    "ita-1": ["Serie A"], "ita-2": ["Serie B"], "ita-copa": ["Coppa Italia"], "ita-supercopa": ["Supercoppa Italiana"],
    "deu-1": ["Bundesliga", "German football championship"], "deu-2": ["2. Bundesliga"], "deu-copa": ["DFB-Pokal"],
    "deu-supercopa": ["DFL-Supercup"],
    "fra-1": ["Ligue 1"], "fra-2": ["Ligue 2"], "fra-copa": ["Coupe de France"], "fra-supercopa": ["Trophée des Champions"],
    "prt-1": ["Primeira Liga"], "prt-copa": ["Taça de Portugal"], "prt-copa-liga": ["Taça da Liga"],
    "prt-supercopa": ["Supertaça Cândido de Oliveira"],
    "nld-1": ["Eredivisie"], "nld-copa": ["KNVB Cup"], "bel-1": ["Belgian Pro League"], "bel-copa": ["Belgian Cup"],
    "sco-1": ["Scottish Premiership", "Scottish Premier League", "Scottish Football League"], "sco-copa": ["Scottish Cup"],
    "tur-1": ["Süper Lig"], "tur-copa": ["Turkish Cup"], "grc-1": ["Super League Greece"], "grc-copa": ["Greek Football Cup"],
    "rus-1": ["Russian Premier League"], "rus-copa": ["Russian Cup (football)"],
    "ukr-1": ["Ukrainian Premier League"], "ukr-copa": ["Ukrainian Cup"],
    "dnk-1": ["Danish Superliga"], "dnk-copa": ["Danish Cup"], "che-1": ["Swiss Super League"], "che-copa": ["Swiss Cup"],
    "aut-1": ["Austrian Football Bundesliga"], "aut-copa": ["Austrian Cup"],
    "swe-1": ["Allsvenskan"], "nor-1": ["Eliteserien"], "pol-1": ["Ekstraklasa"], "cze-1": ["Czech First League"],
    "hrv-1": ["Croatian Football League"], "srb-1": ["Serbian SuperLiga"], "rou-1": ["Liga I"],
    "hun-1": ["Nemzeti Bajnokság I"], "isr-1": ["Israeli Premier League"], "bgr-1": ["First Professional Football League (Bulgaria)"],
    "arg-1": ["Argentine Primera División"], "arg-copa": ["Copa Argentina"], "ury-1": ["Uruguayan Primera División"],
    "pry-1": ["Paraguayan Primera División"], "chl-1": ["Chilean Primera División"], "chl-copa": ["Copa Chile"],
    "col-1": ["Categoría Primera A"], "col-copa": ["Copa Colombia"], "per-1": ["Liga 1 (Peru)"],
    "ecu-1": ["Ecuadorian Serie A"], "bol-1": ["Bolivian Primera División"], "bol-copa": ["Copa de la División Profesional"],
    "ven-1": ["Venezuelan Primera División"],
    "mex-1": ["Liga MX"], "usa-1": ["Major League Soccer", "MLS Cup"], "usa-copa": ["U.S. Open Cup"],
    "can-1": ["Canadian Premier League"], "cri-1": ["Liga FPD"],
    "jpn-1": ["J1 League", "Japan Soccer League"], "jpn-copa": ["Emperor's Cup"], "kor-1": ["K League 1"],
    "chn-1": ["Chinese Super League"], "sau-1": ["Saudi Pro League"], "sau-copa": ["King's Cup (Saudi Arabia)"],
    "aus-1": ["A-League Men"], "qat-1": ["Qatar Stars League"], "are-1": ["UAE Pro League"],
    "irn-1": ["Persian Gulf Pro League"], "egy-1": ["Egyptian Premier League"], "mar-1": ["Botola"],
    "zaf-1": ["South African Premiership"], "tun-1": ["Tunisian Ligue Professionnelle 1"],
    "dza-1": ["Algerian Ligue Professionnelle 1"], "ind-1": ["Indian Super League"], "idn-1": ["Super League (Indonesia)"],
    "fin-1": ["Veikkausliiga"], "irl-1": ["League of Ireland Premier Division"], "isl-1": ["Besta deild karla"],
    "svk-1": ["Slovak First Football League"], "svn-1": ["Slovenian PrvaLiga"], "cyp-1": ["Cypriot First Division"],
    "kaz-1": ["Kazakhstan Premier League"], "geo-1": ["Erovnuli Liga"], "bih-1": ["Premier League of Bosnia and Herzegovina"],
}


def c_competicoes():
    tits = sorted({t for v in COMPS.values() for t in v})
    tq = {}
    for lote in comum.lotes(tits, 80):
        vals = " ".join(json.dumps(t, ensure_ascii=False) + "@en" for t in lote)
        for r in comum.sparql(f"""SELECT ?t ?i WHERE {{ VALUES ?t {{ {vals} }} ?a schema:name ?t;
                                 schema:isPartOf <https://en.wikipedia.org/>; schema:about ?i }}"""):
            tq[r["t"]] = comum.qid(r["i"])
    comp_q = {k: [tq[t] for t in v if t in tq] for k, v in COMPS.items()}
    # títulos que não casaram com sitelink (artigo renomeado): QID achado pela busca de entidades do Wikidata
    for k, q in {"pry-1": "Q954911", "chl-1": "Q606832", "col-1": "Q1033349", "ecu-1": "Q2289847",
                 "ven-1": "Q1033339", "mar-1": "Q570246", "finalissima": "Q595275", "intercontinental": "Q182473",
                 "deu-supercopa": "Q156973"}.items():
        comp_q.setdefault(k, [])
        if q not in comp_q[k]:
            comp_q[k].append(q)
    # 1ªs divisões de outros países: ligas com nível 1 (P3983) no Wikidata
    paises = comum.ler_json("historia_paises.json")
    inv = {q: iso for iso, q in paises.items()}
    for r in comum.sparql("""SELECT ?l ?c ?n WHERE { ?l wdt:P3983 ?lev ; wdt:P17 ?c ; wdt:P641 wd:Q2736 ;
                              wikibase:sitelinks ?n . FILTER(?lev = 1) FILTER NOT EXISTS { ?l wdt:P2094 wd:Q1451788 } }"""):
        iso = inv.get(comum.qid(r["c"]))
        if iso and iso != "GBR":
            comp_q.setdefault(f"{iso.lower()}-1", []).append(comum.qid(r["l"]))
    sem = [k for k, v in comp_q.items() if not v]
    comum.salvar_json("historia_competicoes_qid.json", comp_q)
    print("competições com QID:", sum(1 for v in comp_q.values() if v), "sem:", sem)


def c_campeoes():
    comp_q = comum.ler_json("historia_competicoes_qid.json")
    todos = sorted({q for v in comp_q.values() for q in v})
    out = []
    for lote in comum.lotes(todos, 15):
        vals = " ".join("wd:" + q for q in lote)
        rs = comum.sparql(f"""SELECT ?c ?s ?sL ?ini ?pt ?w ?wL ?wPais ?hum WHERE {{ VALUES ?c {{ {vals} }}
          ?s wdt:P3450 ?c . ?s wdt:P1346 ?w .
          OPTIONAL {{ ?s rdfs:label ?sL FILTER(lang(?sL)='en') }}
          OPTIONAL {{ ?s wdt:P580 ?ini }} OPTIONAL {{ ?s wdt:P585 ?pt }}
          OPTIONAL {{ ?w rdfs:label ?wL FILTER(lang(?wL)='en') }}
          OPTIONAL {{ ?w wdt:P17/wdt:P298 ?wPais }} OPTIONAL {{ ?w wdt:P1532/wdt:P298 ?wPais }}
          BIND(EXISTS {{ ?w wdt:P31 wd:Q5 }} AS ?hum) }}""")
        for r in rs:
            if r.get("hum") == "true":
                continue
            out.append({"c": comum.qid(r["c"]), "s": comum.qid(r["s"]), "sL": r.get("sL"), "ini": r.get("ini"),
                        "pt": r.get("pt"), "w": comum.qid(r["w"]), "wL": r.get("wL"), "wPais": r.get("wPais")})
        print(f"  temporadas com vencedor: {len(out)}", flush=True)
    comum.salvar_json("historia_campeoes_wd.json", out)


def c_campeoes_tm():
    """id Transfermarkt (P7223) dos clubes campeões/vices das listas da Wikipedia, para ligar ao banco quando
    o QID do clube não está no mapa por país. Saída: intermediario/campeoes_wiki_tm.json {qid: tm}."""
    wl = comum.ler_json("campeoes_wiki.json", []) or []
    qs = sorted({r.get(k) for r in wl for k in ("campeao_qid", "vice_qid") if r.get(k)})
    out = {}
    for r in consulta_lote(lambda vals: f"SELECT ?p ?tm WHERE {{ VALUES ?p {{ {vals} }} ?p wdt:P7223 ?tm }}", qs):
        out[comum.qid(r["p"])] = r["tm"]
    for lote in comum.lotes(qs, 300):
        for r in consulta_lote(lambda vals: f"""SELECT ?p ?tm WHERE {{ VALUES ?p {{ {vals} }}
                 ?p (wdt:P361|wdt:P127|^wdt:P361|wdt:P1365|^wdt:P1366) ?o . ?o wdt:P7223 ?tm }}""", lote):
            out.setdefault(comum.qid(r["p"]), r["tm"])
    comum.salvar_json("campeoes_wiki_tm.json", out)
    print("campeões com id Transfermarkt:", len(out), "de", len(qs))


# ---------------------------------------------------------------- fotos
def c_fotos(qtd=500):
    os.environ.setdefault("U2NET_HOME", str(comum.CACHE / "modelos"))
    from PIL import Image
    from rembg import new_session, remove
    import fotos as F
    lendas = comum.ler_json("historia_lendas_final.json", [])   # gerado por aplicar()
    man = comum.ler_json("historia_fotos.json", {}) or {}
    FOTOS_LENDAS.mkdir(parents=True, exist_ok=True)
    sessao = new_session("u2net")
    alvo = [l for l in sorted(lendas, key=lambda l: -(l["fama"] or 0)) if l.get("img")][:qtd]
    feitos = 0
    for l in alvo:
        q = l["wikidata"]
        if q in man and (man[q].get("erro") or (man[q].get("png") and (comum.RAIZ / man[q]["png"]).exists())):
            continue
        arq = F.nome_arquivo(l["img"])
        reg = {"arquivo": arq, "lenda_id": l["id"]}
        info = F.info_commons(arq)
        if not info or not info["original"]:
            if info is not None:
                reg["erro"] = "sem original"
                man[q] = reg
            continue
        reg.update({"fonte_url": info["pagina"], "licenca": info["licenca"] or "ver página do Commons",
                    "autor": info["autor"]})
        mini = F.miniatura(info["original"])
        dados = comum.baixar(mini, binario=True, intervalo=3.0) if mini else None
        if dados is None:
            continue
        try:
            img = Image.open(io.BytesIO(dados)).convert("RGB")
            sem = remove(img, session=sessao)
            bbox = sem.getchannel("A").point(lambda a: 255 if a > 20 else 0).getbbox()
            if not bbox or (bbox[2] - bbox[0]) * (bbox[3] - bbox[1]) < 0.04 * sem.width * sem.height:
                reg["erro"] = "recorte vazio"
            else:
                sem = sem.crop(bbox)
                if sem.height > 256:
                    sem = sem.resize((max(1, round(sem.width * 256 / sem.height)), 256), Image.LANCZOS)
                sem.save(FOTOS_LENDAS / f"{l['id']}.png", optimize=True)
                reg["png"] = f"assets/fotos/lendas/{l['id']}.png"
                feitos += 1
        except Exception as e:
            reg["erro"] = f"processamento: {e.__class__.__name__}"
        man[q] = reg
        if feitos % 20 == 0:
            comum.salvar_json("historia_fotos.json", man)
            print(f"  fotos de lendas: {feitos}", flush=True)
    comum.salvar_json("historia_fotos.json", man)
    print("fotos de lendas:", sum(1 for v in man.values() if v.get("png")))


# ---------------------------------------------------------------- posições das lendas (infobox da Wikipedia)
RE_INFO_POS = {"en": re.compile(r"\|\s*positions?\s*=\s*([^\n]*)", re.I),
               "pt": re.compile(r"\|\s*posi[çc](?:[ãa]o|[õo]es)\s*=\s*([^\n]*)", re.I)}


def _texto_infobox_pos(wt, lang):
    """Texto do campo de posição dentro do infobox de futebolista (en: Infobox football biography;
    pt: Info/Futebolista / Info/Futebol / Info/Treinador de futebol)."""
    if not wt:
        return None
    ini = None
    for m in re.finditer(r"\{\{\s*(Infobox[ _]football[ _]biography|Infobox[ _]footballer|Info/Futebolista|"
                         r"Info/Futebol\b|Info/Treinador de futebol|Info/Jogador de futebol)", wt, re.I):
        ini = m.start()
        break
    if ini is None:
        return None
    trecho = wt[ini:ini + 12000]
    m = RE_INFO_POS[lang].search(trecho)
    if not m:
        return None
    v = m.group(1)
    if re.match(r"\s*\{\{\s*(plainlist|plain list|ubl|unbulleted list|hlist|flatlist)", v, re.I):
        # lista em várias linhas: vai até o fechamento do modelo
        resto = trecho[m.start(1):]
        prof, fim = 0, len(resto)
        for i in range(len(resto) - 1):
            if resto[i:i + 2] == "{{":
                prof += 1
            elif resto[i:i + 2] == "}}":
                prof -= 1
                if prof == 0:
                    fim = i + 2
                    break
        v = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", resto[:fim])
        v = re.sub(r"^\s*\{\{\s*[^|}]*\|?", "", v)[:-2]
        v = re.sub(r"\n\s*\*\s*", " / ", v).replace("|", " / ").replace("\n", " ")
    v = re.sub(r"<ref[^>]*/>|<ref.*?</ref>|<!--.*?-->", "", v)
    v = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", v)
    v = re.sub(r"\{\{[^{}]*\}\}", " ", v)
    v = re.sub(r"<br\s*/?>", " / ", v)
    return v.strip(" |") or None


def _alvo_posicoes():
    """Lendas que importam para o melhor 11: as que estão em algum onze_historico do banco atual (por fama)
    e depois as demais por fama, até 6500."""
    lendas = comum.ler_json("historia_lendas_final.json", [])
    fama = {l["wikidata"]: (l["fama"] or 0) for l in lendas if l.get("wikidata")}
    try:
        con = db_ro()
        no_onze = {f"Q{r[0]}" for r in con.execute("SELECT DISTINCT lenda_id FROM onze_historico WHERE lenda_id < 900000000")}
        con.close()
    except sqlite3.Error:
        no_onze = set()
    a = sorted((q for q in fama if q in no_onze), key=lambda q: -fama[q])
    b = sorted((q for q in fama if q not in no_onze), key=lambda q: -fama[q])
    return (a + b)[:6500]


def c_posicoes(lang=None):
    """Baixa (cache) o wikitext das lendas-alvo e grava o campo de posição do infobox.
    lang='en' ou 'pt' só baixa aquela língua (dá para rodar as duas em paralelo: domínios diferentes);
    sem lang, só monta intermediario/historia_posicoes_wiki.json {qid: {"en": texto, "pt": texto}} a partir do cache."""
    det = comum.ler_json("historia_lendas_wd.json", {}) or {}
    v1 = comum.ler_json("jogadores_wd.json", {}) or {}
    alvo = _alvo_posicoes()
    if not lang:   # composição só lê o cache: vale para toda lenda que já tenha artigo baixado
        alvo = alvo + sorted({l["wikidata"] for l in comum.ler_json("historia_lendas_final.json", [])
                              if l.get("wikidata")} - set(alvo))
    langs = [lang] if lang else ["en", "pt"]
    if not lang:
        os.environ["COLETA_OFFLINE"] = "1"
        comum.OFFLINE = True
    out = {} if lang else (comum.ler_json("historia_posicoes_wiki.json", {}) or {})
    for n, q in enumerate(alvo):
        d = det.get(q) or v1.get(q) or {}
        reg = out.setdefault(q, {})
        for lg in langs:
            url = d.get(f"{lg}w")
            if not url or reg.get(lg):
                continue
            t = _texto_infobox_pos(comum.baixar(url + "?action=raw"), lg)
            if t:
                reg[lg] = t
        if lang and n % 200 == 0:
            print(f"  {lang}: {n}/{len(alvo)}", flush=True)
    if not lang:
        out = {q: v for q, v in out.items() if v}
        comum.salvar_json("historia_posicoes_wiki.json", out)
        print("posições de lendas pela Wikipedia (cache):", len(out), "de", len(alvo))


# termos de posição (en/pt) -> código; a ordem importa (específico antes do genérico).
# 'g' = genérico (só a linha: defesa/meio/ataque), usado só se não houver termo específico.
TERMOS_POS = [
    (r"goal ?keeper|goaltender|guarda-redes|goleiro|arqueiro", "GOL", 0),
    (r"left[- ]?wing[- ]?back|left[- ]back|left full[- ]?back|lateral[- ]esquerd|ala[- ]esquerd|defesa[- ]esquerd", "LE", 0),
    (r"right[- ]?wing[- ]?back|right[- ]back|right full[- ]?back|lateral[- ]direit|ala[- ]direit|defesa[- ]direit", "LD", 0),
    (r"full[- ]?back|wing[- ]?back|\blateral\b|\bala\b", "LAT", 0),
    (r"centre[- ]?back|center[- ]?back|central defender|centre[- ]half|center[- ]half|centerhalf|sweeper|libero|líbero|"
     r"stopper|zagueiro|beque|defesa[- ]central", "ZAG", 0),
    (r"defensive[- ]midfield|holding[- ]midfield|volante|médio[- ]defensivo|medio[- ]defensivo|centromédio|trinco|"
     r"half[- ]?back|wing[- ]?half|médio[- ]volante", "VOL", 0),
    (r"attacking[- ]midfield|offensive[- ]midfield|playmaker|inside (?:forward|left|right)|meia[- ]atacante|"
     r"meia[- ]armador|meia[- ]ofensiv|\barmador\b|médio[- ]ofensivo|medio[- ]ofensivo|\bmeia\b|número 10|enganche", "MEI", 0),
    (r"left winger|outside left|left wing\b|ponta[- ]esquerd|extremo[- ]esquerd|ponteiro[- ]esquerd|left midfield", "PE", 0),
    (r"right winger|outside right|right wing\b|ponta[- ]direit|extremo[- ]direit|ponteiro[- ]direit|right midfield", "PD", 0),
    (r"second striker|segundo atacante|centre[- ]?forward|center[- ]?forward|striker|centroavante|centro-avante|"
     r"ponta[- ]de[- ]lança|avançado[- ]centro", "CA", 0),
    (r"\bwinger\b|\bponta\b|\bextremo\b|\bponteiro\b|wide midfield", "PONTA", 0),
    (r"central[- ]midfield|centre[- ]midfield|box[- ]to[- ]box|médio[- ]centro|meio[- ]campista central", "MC", 0),
    (r"midfield|meio[- ]?campista|meio[- ]campo|médio|\bmeio\b|centrocampista", "MC", 1),
    (r"defender|defensor|\bdefesa\b|zagueir", "ZAG", 1),
    (r"forward|attacker|atacante|avançado|delantero", "CA", 1),
]


def posicoes_de_texto(txt):
    """[(código, genérico?)] na ordem em que aparecem no texto."""
    t = (txt or "").lower()
    achados = []
    for rx, cod, g in TERMOS_POS:
        for m in re.finditer(rx, t):
            ini = m.start()
            if any(a <= ini < b for a, b, *_ in achados):
                continue
            achados.append((ini, m.end(), cod, g))
    achados.sort()
    return [(cod, g) for _a, _b, cod, g in achados]


def _generico(lst):
    return not lst or all(g for _c, g in lst)


# correções explícitas conferidas (verificadas na Wikipedia): qid -> posições
POS_FIXAS = {
    "Q12897": ["CA", "MEI"],     # Pelé
    "Q47526": ["MEI"],           # Zico
    "Q313741": ["MEI"],          # Andrés D'Alessandro
    "Q312454": ["VOL", "MC"],    # Falcão (Paulo Roberto)
    "Q311424": ["LE", "LD"],     # Júnior (Leovegildo)
    "Q180642": ["PD"],           # Garrincha
    "Q220283": ["MEI", "PE"],    # Rivelino
    "Q102331": ["MEI"],          # Sócrates
    "Q178649": ["CA"],            # Romário
    "Q39444": ["MEI", "PE"],     # Ronaldinho
    "Q531814": ["MEI"],           # Kaká
    "Q215890": ["VOL"],          # Dunga
    "Q372451": ["PE", "CA"],     # Sávio (Bortolini Pimentel), ponta/atacante — Wikidata diz "wing half"
}


def posicoes_lenda(q, wd_pos, wiki, pe=None):
    """Lista de posições (GOL ZAG LD LE VOL MC MEI PE PD CA) de uma lenda: correção fixa > infobox da
    Wikipedia (en/pt; termos específicos) > Wikidata P413 específico > genérico."""
    if q in POS_FIXAS:
        return list(POS_FIXAS[q])
    fontes = []
    for lang in ("en", "pt"):
        if wiki and wiki.get(lang):
            fontes.append(posicoes_de_texto(wiki[lang]))
    if not any(fontes):   # Wikidata P413 só quando o infobox não diz nada (costuma ser genérico/impreciso)
        fontes = [[x for rot in (wd_pos or []) for x in posicoes_de_texto(rot)]]
    esp, gen = [], []
    for lst in fontes:
        for cod, g in lst:
            (gen if g else esp).append(cod)
    canhoto = (pe or "").lower() in ("e", "left", "esquerdo")

    def fixo(cod):
        if cod == "LAT":
            return "LE" if canhoto else "LD"
        if cod == "PONTA":
            return "PE" if canhoto else "PD"
        return cod
    out = []
    for cod in map(fixo, esp):
        if cod not in out:
            out.append(cod)
    # um termo genérico ("midfielder", "defender", "forward") só entra se nenhum termo específico cobriu a linha
    lin_esp = {LINHA[c] for c in out}
    for cod in map(fixo, gen):
        if cod not in out and LINHA[cod] not in lin_esp:
            out.append(cod)
    return out[:3]


def nome_comum(d):
    """Nome pelo qual a lenda é conhecida: título do artigo da Wikipedia pt (sem o desambiguador entre
    parênteses), senão apelido (P1449) curto, senão rótulo pt/en; títulos/rótulos longos (nome completo)
    perdem para o título en ou para o apelido."""
    import urllib.parse
    def tit(url):
        if not url:
            return None
        t = urllib.parse.unquote(url.rsplit("/wiki/", 1)[-1]).replace("_", " ")
        return re.sub(r"\s*\(.*?\)\s*$", "", t).strip() or None
    cands = [tit(d.get("ptw")), d.get("pt"), tit(d.get("enw")), d.get("en")]
    cands = [c for c in cands if c]
    ap = [a for a in d.get("apelido") or [] if 2 < len(a) <= 20]
    if not cands:
        return ap[0] if ap else None
    nome = cands[0]
    if len(nome.split()) >= 3:   # parece nome completo: tenta algo mais curto entre as outras formas
        curtos = [c for c in cands[1:] + ap if len(c.split()) < len(nome.split()) and len(c) >= 3]
        if curtos:
            nome = curtos[0]
    return nome


LINHA = {"GOL": 0, "ZAG": 1, "LD": 1, "LE": 1, "VOL": 2, "MC": 2, "MEI": 2, "PE": 3, "PD": 3, "CA": 3}


# =============================================================== aplicar (montagem das tabelas)
ESQUEMA_HIST = """
DROP TABLE IF EXISTS carreira; DROP TABLE IF EXISTS lendas; DROP TABLE IF EXISTS onze_historico;
DROP TABLE IF EXISTS campeoes_historicos;
CREATE TABLE carreira (jogador_id INTEGER, lenda_id INTEGER, clube_id TEXT, clube_nome TEXT, clube_pais TEXT,
  ano_inicio INTEGER, ano_fim INTEGER, emprestimo INTEGER DEFAULT 0, jogos INTEGER, gols INTEGER, fonte_id INTEGER);
CREATE TABLE lendas (id INTEGER PRIMARY KEY, nome TEXT NOT NULL, nome_conhecido TEXT NOT NULL, nascimento TEXT, falecimento TEXT,
  pais TEXT, posicao TEXT, forca_auge INTEGER, fama REAL, foto_png TEXT, foto_fonte TEXT, foto_licenca TEXT,
  wikidata TEXT, tm_id INTEGER, resumo TEXT, fonte_id INTEGER);
CREATE TABLE onze_historico (clube_id TEXT, posicao_slot TEXT, ordem INTEGER, lenda_id INTEGER, jogador_id INTEGER, criterio TEXT);
CREATE TABLE campeoes_historicos (competicao_id TEXT, temporada TEXT, campeao_clube_id TEXT, campeao_nome TEXT,
  vice_clube_id TEXT, vice_nome TEXT, fonte_id INTEGER);
CREATE INDEX ix_carreira_jog ON carreira(jogador_id); CREATE INDEX ix_carreira_lenda ON carreira(lenda_id);
CREATE INDEX ix_carreira_clube ON carreira(clube_id); CREATE INDEX ix_onze_clube ON onze_historico(clube_id);
CREATE INDEX ix_campeoes_comp ON campeoes_historicos(competicao_id);
"""

POS_NOME = {"GOL": "goleiro", "ZAG": "zagueiro", "LD": "lateral-direito", "LE": "lateral-esquerdo", "VOL": "volante",
            "MC": "meio-campista", "MEI": "meia", "PE": "ponta-esquerda", "PD": "ponta-direita", "CA": "atacante"}


def pos_de_rotulo(rot):
    r = (rot or "").lower()
    regras = [("goalkeeper", "GOL"), ("left-back", "LE"), ("left back", "LE"), ("left wing-back", "LE"),
              ("right-back", "LD"), ("right back", "LD"), ("right wing-back", "LD"), ("full-back", "LD"),
              ("wing-back", "LD"), ("centre-back", "ZAG"), ("center-back", "ZAG"), ("central defender", "ZAG"),
              ("sweeper", "ZAG"), ("libero", "ZAG"), ("defender", "ZAG"), ("defensive midfielder", "VOL"),
              ("attacking midfielder", "MEI"), ("playmaker", "MEI"), ("left winger", "PE"), ("right winger", "PD"),
              ("inside forward", "MEI"), ("half-back", "VOL"), ("wing half", "VOL"),
              ("winger", "PE"), ("wide midfielder", "PE"), ("central midfielder", "MC"), ("midfielder", "MC"),
              ("second striker", "CA"), ("centre-forward", "CA"), ("center forward", "CA"), ("striker", "CA"),
              ("forward", "CA")]
    for k, v in regras:
        if k in r:
            return v
    return None


# compatibilidade posição do jogador -> vaga do 4-3-3 (ausente = não pode)
# Regras do jogo: cada um só joga na própria linha (GOL só GOL; ZAG só ZAG; laterais entre si; VOL/MC/MEI no
# meio; PE/PD/CA no ataque). Exceções de último recurso (nível 1, só se nenhum candidato da linha servir):
# VOL de zagueiro e MEI nas pontas. Nunca meio-campista na defesa nem atacante no meio/defesa.
COMPAT = {
    "GOL": {"GOL": 1},
    "ZAG": {"ZAG": 1},
    "LD": {"LD": 1, "LE": .85},
    "LE": {"LE": 1, "LD": .85},
    "VOL": {"VOL": 1, "MC": .9, "MEI": .75, "ZAG": .6},
    "MC": {"MC": 1, "VOL": .9, "MEI": .9},
    "MEI": {"MEI": 1, "MC": .9, "VOL": .75, "PE": .6, "PD": .6},
    "PE": {"PE": 1, "PD": .9, "CA": .8},
    "PD": {"PD": 1, "PE": .9, "CA": .8},
    "CA": {"CA": 1, "PE": .85, "PD": .85},
}
# títulos cassados que algumas listas ainda mostram
TITULOS_REVOGADOS = {("ita-1", "2004/05", "Juventus")}

# nível 2 (só quando a vaga ficaria vazia): zagueiro/lateral trocam entre si — continua na linha de defesa
DEFESA_TROCA = {("ZAG", "LD"): .5, ("ZAG", "LE"): .5, ("LD", "ZAG"): .5, ("LE", "ZAG"): .5}


def _cp(x, sl, troca_defesa=False):
    best = 0
    for j, p in enumerate(x["poss"]):
        v = COMPAT[p].get(sl, 0) or (DEFESA_TROCA.get((p, sl), 0) if troca_defesa else 0)
        best = max(best, v * (1 if j == 0 else 0.92))
    return best


def _completar_onze(cands, slot_ok, usado):
    """Vagas que o guloso deixou vazias: candidato livre compatível; senão troca de um escalado que serve na vaga
    por um livre que serve na vaga dele; por último, zagueiro<->lateral (mesma linha)."""
    for troca in (False, True):
        for si, sl in enumerate(SLOTS):
            if si in slot_ok:
                continue
            livres = [x for x in cands if (x["tipo"], x["id"]) not in usado]
            ok = sorted((x for x in livres if _cp(x, sl, troca) > 0), key=lambda x: -x["score"] * _cp(x, sl, troca))
            if ok:
                x = ok[0]
                slot_ok[si] = (x, x["score"] * _cp(x, sl, troca))
                usado.add((x["tipo"], x["id"]))
                continue
            for s2, (x2, _sc) in list(slot_ok.items()):
                if _cp(x2, sl, troca) <= 0:
                    continue
                subs = sorted((y for y in livres if _cp(y, SLOTS[s2], troca) > 0),
                              key=lambda y: -y["score"] * _cp(y, SLOTS[s2], troca))
                if subs:
                    y = subs[0]
                    slot_ok[si] = (x2, x2["score"] * _cp(x2, sl, troca))
                    slot_ok[s2] = (y, y["score"] * _cp(y, SLOTS[s2], troca))
                    usado.add((y["tipo"], y["id"]))
                    break


# nomes históricos de campeões (listas da Wikipedia) -> clube atual do banco (mesma instituição)
ALIAS_CAMPEAO = {
    (None, "Ambrosiana-Inter"): "internazionale", (None, "Ambrosiana"): "internazionale",
    (None, "Atlético Aviación"): "atletico-madri", (None, "West Bromwich Albion"): "west-bromwich",
    (None, "Steaua București"): "steaua-bucuresti", (None, "Steaua Bucharest"): "steaua-bucuresti",
    (None, "Independiente del Valle"): "indep-del-valle", ("bra-2", "Bragantino"): "rb-bragantino",
    ("bra-3", "Bragantino"): "rb-bragantino", ("bra-2", "Villa Nova"): "villa-nova-mg", (None, "Rapid Wien"): "rapid-viena", (None, "Karlsruher"): "karlsruhe",
    (None, "Ferro Carril Oeste"): "ferro-carril", ("conmebol-sul-americana", "Arsenal"): "arsenal-sarandi",
}
ULTIMO_RECURSO = {("VOL", "ZAG"), ("MEI", "PE"), ("MEI", "PD")}   # (posição do jogador, vaga)
SLOTS = ["GOL", "LD", "ZAG", "ZAG", "LE", "VOL", "MC", "MEI", "PE", "CA", "PD"]


def fama_de(links, caps=0):
    """fama 0..100: escala log dos sitelinks do Wikidata (~250 = 100) + até 5 pontos por jogos de seleção."""
    s = int(links or 0)
    return round(min(100.0, 100 * math.log1p(s) / math.log1p(250) + min(5.0, (caps or 0) / 30)), 1)


def forca_auge_de(links, caps=0, premios=0):
    """forca_auge (1..99): 58 + 7,2*ln(1+sitelinks) + min(3, caps/40) + 2 por prêmio de melhor do mundo
    (Bola de Ouro / Melhor do Mundo FIFA / The Best, máx. +4); limitado a 60..99."""
    s = int(links or 0)
    v = 58 + 7.2 * math.log1p(s) + min(3.0, (caps or 0) / 40) + min(4, 2 * premios)
    return int(max(60, min(99, round(v))))


def _int(x):
    try:
        return int(float(x))
    except (TypeError, ValueError):
        return None


class _BancoSolto:
    """Interface mínima de montar_banco.Banco para rodar aplicar() num mundo.sqlite já pronto."""
    def __init__(self, c):
        self.c = c
        self._f = {u: i for i, u in c.execute("SELECT id, url FROM fontes")}

    def fonte(self, url, tipo="base_dados", conf=3, titulo=None, licenca=None):
        if url in self._f:
            return self._f[url]
        cur = self.c.execute("INSERT INTO fontes (url,tipo,titulo,acessado_em,confiabilidade,licenca) VALUES (?,?,?,?,?,?)",
                             (url, tipo, titulo, comum.HOJE, conf, licenca))
        self._f[url] = cur.lastrowid
        return cur.lastrowid


def aplicar_standalone():
    c = sqlite3.connect(comum.DADOS / "mundo.sqlite")
    c.execute("DELETE FROM meta WHERE chave='historia'")
    aplicar(_BancoSolto(c))
    import normalizar
    normalizar.unificar_competicoes(c)
    normalizar.limpar_banco(c)
    c.commit()
    c.close()


def _carreira_tm(c):
    """Carreira pelo transfermarkt-datasets (transfers + appearances) para jogadores com tm_id: {tm: [linhas]}."""
    import pandas as pd
    tm_clube = {int(t): cid for cid, t in c.execute("SELECT id, tm_id FROM clubes WHERE tm_id IS NOT NULL")}
    tr = pd.read_csv(TM_RAW / "transfers.csv.gz", usecols=["player_id", "transfer_date", "to_club_id", "to_club_name"])
    tr = tr[tr.transfer_date <= "2026-01-01"].sort_values(["player_id", "transfer_date"])
    ap = pd.read_csv(TM_RAW / "appearances.csv.gz", usecols=["player_id", "player_club_id", "date", "goals"])
    ap = ap[ap.date < "2026-01-01"]
    jg = ap.groupby(["player_id", "player_club_id"]).agg(j=("goals", "size"), g=("goals", "sum")).reset_index()
    jg_map = {(int(r.player_id), int(r.player_club_id)): (int(r.j), int(r.g)) for r in jg.itertuples()}
    out = defaultdict(list)
    for pid, grp in tr.groupby("player_id"):
        linhas = list(grp.itertuples())
        for i, r in enumerate(linhas):
            if r.to_club_id != r.to_club_id:   # NaN
                continue
            nome = str(r.to_club_name)
            if re.search(r"retired|without club|unknown|career break|pause", nome, re.I):
                continue
            ini = int(str(r.transfer_date)[:4])
            fim = int(str(linhas[i + 1].transfer_date)[:4]) if i + 1 < len(linhas) else None
            tid = int(r.to_club_id)
            j, g = jg_map.get((int(pid), tid), (None, None))
            out[int(pid)].append({"clube_id": tm_clube.get(tid), "nome": nome, "ini": ini, "fim": fim,
                                  "jogos": j, "gols": g})
    return out


def aplicar(b):
    c = b.c
    t0 = dt.datetime.now()
    c.executescript(ESQUEMA_HIST)
    mp = mapa_clubes(c)
    clube_info = {cid: (nome, pais) for cid, nome, pais in c.execute("SELECT id, nome, pais FROM clubes")}
    pais_nome = dict(c.execute("SELECT id, nome FROM paises"))
    v1 = comum.ler_json("jogadores_wd.json", {}) or {}
    v1x = comum.ler_json("historia_v1_extra.json", {}) or {}
    car_at = comum.ler_json("historia_carreiras.json", {}) or {}
    car_le = comum.ler_json("historia_carreiras_lendas.json", {}) or {}
    lst = comum.ler_json("historia_jogadores_lista.json", {}) or {}
    lst.pop("_anos", None)
    ativos = casar_ativos(c)
    qid_ativos = set(ativos.values())
    est = Counter()
    est["clubes_com_qid"] = len(mp)
    est["ativos_casados_wikidata"] = len(ativos)

    def carreira_wd(q):
        if q in v1 and v1[q].get("car"):
            return v1[q]["car"]
        return car_at.get(q) or car_le.get(q) or []

    def linha_car(x):
        cid = mp.get(x.get("clube"))
        nome, pais = clube_info.get(cid, (x.get("nome"), x.get("pais")))
        if pais == "GBR":
            pais = "ENG"
        return cid, nome, pais

    def fonte_wd(q):
        return b.fonte(WD + q, "wikidata", 3, f"Wikidata {q} (carreira P54 e dados biográficos)", "CC0")

    def caps_de(car):
        return sum(_int(x.get("jogos")) or 0 for x in car if x.get("selecao"))

    # ------------------------------------------------ carreira dos jogadores ativos
    tm_de = dict(c.execute("SELECT id, tm_id FROM jogadores WHERE tm_id IS NOT NULL"))
    car_tm = _carreira_tm(c)
    fonte_tm = b.fonte(TM_URL, "base_dados", 4, "transfermarkt-datasets (dados do Transfermarkt, CC0)", "CC0")
    linhas = []
    fama_ativo = {}
    for (jid,) in c.execute("SELECT id FROM jogadores WHERE origem='real'").fetchall():
        q = ativos.get(jid)
        car = [x for x in carreira_wd(q) if not x.get("selecao")] if q else []
        if q:
            fama_ativo[jid] = fama_de(lst.get(q, {}).get("n") or (v1.get(q) or {}).get("links"),
                                      caps_de(carreira_wd(q)))
        if car:
            f = fonte_wd(q)
            for x in car:
                cid, nome, pais = linha_car(x)
                linhas.append((jid, None, cid, nome, pais, _int(x.get("ini")), _int(x.get("fim")),
                               1 if x.get("emp") else 0, _int(x.get("jogos")), _int(x.get("gols")), f))
            est["carreira_ativos_wikidata"] += 1
        elif jid in tm_de and car_tm.get(int(tm_de[jid])):
            for x in car_tm[int(tm_de[jid])]:
                nome, pais = clube_info.get(x["clube_id"], (x["nome"], None))
                linhas.append((jid, None, x["clube_id"], nome, pais, x["ini"], x["fim"], 0, x["jogos"], x["gols"], fonte_tm))
            est["carreira_ativos_tm"] += 1
    c.executemany("INSERT INTO carreira VALUES (?,?,?,?,?,?,?,?,?,?,?)", linhas)
    est["linhas_carreira_ativos"] = len(linhas)

    # ------------------------------------------------ lendas
    cand = comum.ler_json("historia_candidatos.json", {}) or {}
    futae_qid = cand.get("_futae_qid", {})
    det = comum.ler_json("historia_lendas_wd.json", {}) or {}
    fotos = comum.ler_json("historia_fotos.json", {}) or {}
    pos_wiki = comum.ler_json("historia_posicoes_wiki.json", {}) or {}   # coletar posicoes
    fut = {f["chave"]: f for f in lendas_futae()}
    fut_por_q = defaultdict(list)
    for k, q in futae_qid.items():
        if k in fut:
            fut_por_q[q].append(fut[k])
    qs = {q for q in cand if not q.startswith("_")}
    qs |= {q for q, x in v1.items() if int(x.get("links") or 0) >= 6}
    qs -= qid_ativos
    lendas, lin_car, final = [], [], []
    for q in sorted(qs):
        d = det.get(q) or v1.get(q)
        if not d or (d.get("sexo") or "") == "Q6581072":
            continue
        car = carreira_wd(q)
        nasc = d.get("nasc")
        ano_n = _int((nasc or "")[:4])
        clubes_car = [x for x in car if not x.get("selecao")]
        ft = fut_por_q.get(q)
        if not clubes_car and not ft:
            continue
        fims = [_int(x.get("fim")) for x in clubes_car]
        abertos = [x for x in clubes_car if not x.get("fim")]
        if ano_n and ano_n >= 1990 and (abertos or max([f for f in fims if f] or [0]) >= 2024):
            continue   # provavelmente ainda em atividade (fora do banco)
        if ano_n and ano_n >= 1983 and any((_int(x.get("ini")) or 0) >= 2021 for x in abertos):
            continue
        ex = v1x.get(q, {})
        morte = d.get("morte") or ex.get("morte")
        premios = len(set(d.get("premios") or ex.get("premios") or []))
        caps = caps_de(car)
        links = _int(d.get("links")) or 0
        fama = fama_de(links, caps)
        forca = forca_auge_de(links, caps, premios)
        if ft and ft[0].get("overall"):
            forca = int(ft[0]["overall"])   # nota curada à mão no FUT.AE tem prioridade
        cids = d.get("cid") or []
        pais = cids[0] if cids else None
        if pais == "GBR":
            sel = " ".join((x.get("nome") or "") for x in car if x.get("selecao")).lower()
            pais = "SCO" if ("scot" in sel or "escó" in sel) else "WAL" if ("wales" in sel or "gales" in sel) else \
                   "NIR" if ("northern" in sel or "do norte" in sel) else "ENG"
        pe_l = d.get("pe")
        pe_l = pe_l[0] if isinstance(pe_l, list) and pe_l else pe_l if isinstance(pe_l, str) else None
        poss = posicoes_lenda(q, [x[1] for x in d.get("pos") or [] if x and len(x) > 1], pos_wiki.get(q), pe_l)
        if q in pos_wiki and pos_wiki[q]:
            est["lendas_pos_wikipedia"] += 1
        if not poss and ft and ft[0].get("pos"):
            poss = [ft[0]["pos"]]
        conhecido = nome_comum(d) or (ft[0]["conhecido"] if ft else q)
        nome = d.get("nomeNasc") or d.get("en") or conhecido
        lid = int(q[1:])
        foto = fotos.get(q, {})
        ordem = sorted(clubes_car, key=lambda x: _int(x.get("ini")) or 9999)
        partes = []
        for x in ordem[:6]:
            _cid, nm, _p = linha_car(x)
            partes.append(f"{nm} ({x.get('ini') or '?'}–{x.get('fim') or '?'})")
        resumo = ((POS_NOME.get(poss[0], "jogador").capitalize() if poss else "Jogador")
                  + (f" ({pais_nome[pais]})" if pais in pais_nome else "")
                  + (f", {ano_n}–{morte[:4]}" if ano_n and morte else (f", nascido em {ano_n}" if ano_n else "")) + ". "
                  + ("Clubes: " + ", ".join(partes) + ("…" if len(ordem) > 6 else "") + ". " if partes else "")
                  + (f"{caps} jogos pela seleção. " if caps else "")
                  + ("Eleito melhor jogador do mundo. " if premios else ""))
        f = fonte_wd(q)
        lendas.append((lid, nome, conhecido, nasc, morte, pais, poss[0] if poss else None, forca, fama,
                       foto.get("png"), foto.get("fonte_url"), foto.get("licenca"), q, _int(d.get("tm")),
                       resumo.strip(), f))
        final.append({"id": lid, "wikidata": q, "fama": fama, "img": d.get("img"), "pos": poss})
        for x in clubes_car:
            cid, nm, pais_c = linha_car(x)
            lin_car.append((None, lid, cid, nm, pais_c, _int(x.get("ini")), _int(x.get("fim")),
                            1 if x.get("emp") else 0, _int(x.get("jogos")), _int(x.get("gols")), f))
        for fx in ft or []:
            fx["_usado"] = True
    # lendas do FUT.AE sem item no Wikidata (as que são jogadores ativos do banco ficam de fora)
    from paises import iso3
    fonte_fut = {}
    for k, fx in fut.items():
        if fx.get("_usado") or futae_qid.get(k) in qid_ativos:
            continue
        src = fx["source"]
        if src not in fonte_fut:
            fonte_fut[src] = b.fonte(f"futae:memi.sqlite#{src}",
                                     "base_dados", 2, f"FUT.AE (memi.sqlite, fonte '{src}': lendas curadas à mão)",
                                     "uso_pessoal")
        ov = int(fx.get("overall") or 80)
        lid = 900_000_000 + int(fx["futae_id"])
        p = iso3(fx["nac"]) if fx.get("nac") else None
        lendas.append((lid, fx["nome"], fx["conhecido"], fx.get("nasc"), None, p, fx.get("pos"), ov,
                       round(max(10.0, min(90.0, (ov - 70) * 3.0)), 1), None, None, None, None, None,
                       f"Lenda do FUT.AE ({src}); sem item correspondente no Wikidata.", fonte_fut[src]))
        final.append({"id": lid, "wikidata": None, "fama": None, "img": None, "pos": [fx.get("pos")]})
        est["lendas_so_futae"] += 1
    c.executemany("INSERT OR IGNORE INTO lendas VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", lendas)
    c.executemany("INSERT INTO carreira VALUES (?,?,?,?,?,?,?,?,?,?,?)", lin_car)
    comum.salvar_json("historia_lendas_final.json", final)
    est["lendas"] = len(lendas)
    est["lendas_com_foto"] = sum(1 for x in lendas if x[9])
    est["linhas_carreira_lendas"] = len(lin_car)

    # ------------------------------------------------ melhor 11 de todos os tempos (4-3-3)
    pos_lenda = {x["id"]: x["pos"] for x in final}
    fama_lenda = dict(c.execute("SELECT id, fama FROM lendas"))
    jog = {r[0]: r[1:] for r in c.execute("SELECT id, nome_conhecido, posicao, posicoes_sec, forca FROM jogadores WHERE origem='real'")}
    por_clube = defaultdict(dict)
    clube_atual = dict(c.execute("SELECT id, clube_id FROM jogadores"))
    inicios = defaultdict(list)
    for jid, lid, ini in c.execute("SELECT jogador_id, lenda_id, ano_inicio FROM carreira WHERE ano_inicio IS NOT NULL"):
        inicios[("L", lid) if lid else ("J", jid)].append(ini)
    for jid, lid, cid, ini, fim, emp, jogos in c.execute(
            "SELECT jogador_id, lenda_id, clube_id, ano_inicio, ano_fim, emprestimo, jogos FROM carreira WHERE clube_id IS NOT NULL"):
        if ini and not fim:
            # passagem "em aberto": termina quando começa a seguinte; se não houver seguinte, só vai até hoje
            # para o jogador ativo que continua no clube (lenda aposentada / ativo que saiu: +1 ano)
            prox = [x for x in inicios[("L", lid) if lid else ("J", jid)] if x > ini]
            if prox:
                fim = min(prox)
            elif not (jid and clube_atual.get(jid) == cid):
                fim = ini + 1
        anos = max(1, ((fim or ANO_ATUAL) - ini + 1)) if ini else 2
        w = min(1.0, max(anos / 8, (jogos or 0) / 200)) * (0.5 if emp else 1.0)
        k = ("L", lid) if lid else ("J", jid)
        ac = por_clube[cid].setdefault(k, [0.0, 0, 0])
        ac[0] = min(1.0, ac[0] + w)
        ac[1] += anos
        ac[2] += jogos or 0
    onze_linhas, n_onze = [], 0
    for cid, pool in por_clube.items():
        cands = []
        for (tipo, i), (w, anos, jogos) in pool.items():
            if tipo == "L":
                fama = fama_lenda.get(i)
                poss = [p for p in pos_lenda.get(i) or [] if p in COMPAT]
            else:
                if i not in jog:
                    continue
                _nome, p1, psec, forca = jog[i]
                fama = fama_ativo.get(i) or max(0.0, (forca - 55) * 1.6)
                sec = re.findall(r"[A-Z]{2,3}", psec or "")
                poss = [p for p in dict.fromkeys([p1] + sec) if p in COMPAT]
            if not poss or fama is None:
                continue
            cands.append({"tipo": tipo, "id": i, "poss": poss, "fama": fama, "anos": anos,
                          "jogos": jogos, "score": fama * (0.35 + 0.65 * w)})
        if len(cands) < 11 or not any("GOL" in x["poss"] for x in cands):
            continue
        pares = []
        for x in cands:
            for si, sl in enumerate(SLOTS):
                melhor = None
                for j, p in enumerate(x["poss"]):
                    cp = COMPAT[p].get(sl, 0) * (1 if j == 0 else 0.92)
                    if cp <= 0:
                        continue
                    nivel = 1 if (p, sl) in ULTIMO_RECURSO else 0
                    if melhor is None or (nivel, -cp) < (melhor[0], -melhor[1]):
                        melhor = (nivel, cp)
                if melhor:
                    pares.append((melhor[0], x["score"] * melhor[1], si, x))
        # nível 0 (mesma linha) é preenchido antes; último recurso só nas vagas que sobrarem
        pares.sort(key=lambda t: (t[0], -t[1]))
        usado, slot_ok = set(), {}
        for _nv, sc, si, x in pares:
            if si in slot_ok or (x["tipo"], x["id"]) in usado:
                continue
            slot_ok[si] = (x, sc)
            usado.add((x["tipo"], x["id"]))
        if len(slot_ok) < 11:
            _completar_onze(cands, slot_ok, usado)
        if len(slot_ok) < 11:
            for si in range(11):
                if si not in slot_ok:
                    est[f"onze_sem_{SLOTS[si]}"] += 1
            continue
        n_onze += 1
        for si in range(11):
            x, sc = slot_ok[si]
            crit = (f"fama {x['fama']:.0f}/100 ({'lenda; Wikidata' if x['tipo'] == 'L' else 'jogador em atividade'}); "
                    f"{x['anos']} temporada(s) no clube" + (f", {x['jogos']} jogos" if x["jogos"] else "") +
                    f"; posição de origem {x['poss'][0]}" + ("" if SLOTS[si] in x["poss"] else f" (adaptado a {SLOTS[si]})") +
                    f"; nota {sc:.1f} = fama x (0,35 + 0,65 x tempo de clube)")
            onze_linhas.append((cid, SLOTS[si], si + 1, x["id"] if x["tipo"] == "L" else None,
                                x["id"] if x["tipo"] == "J" else None, crit))
    c.executemany("INSERT INTO onze_historico VALUES (?,?,?,?,?,?)", onze_linhas)
    est["onze_clubes"] = n_onze

    # ------------------------------------------------ campeões
    comp_q = comum.ler_json("historia_competicoes_qid.json", {}) or {}
    comps_db = {r[0]: r[1] for r in c.execute("SELECT id, ambito FROM competicoes")}
    q_comp = defaultdict(list)
    for k, v in comp_q.items():
        if k in comps_db:
            for q in v:
                q_comp[q].append(k)
    camp = {}
    comp_pais_db = {k: v for k, v in c.execute("SELECT id, pais FROM competicoes") if v}
    GRUPOS_PAIS = [{"ENG", "SCO", "WAL", "NIR", "GBR"}, {"USA", "CAN"}, {"FRA", "MCO"}, {"CHE", "LIE"},
                   {"ESP", "AND"}, {"ITA", "SMR"}]

    def mesmo_pais(a, b_):
        return a == b_ or any(a in g and b_ in g for g in GRUPOS_PAIS)

    def temporada(lbl, ini, pt):
        m = re.search(r"\b(\d{4})\s*[–\-/]\s*(\d{2,4})\b", lbl or "")
        if m:
            a, bb = int(m.group(1)), m.group(2)
            bb = int(bb) if len(bb) == 4 else int(str(a)[:2] + bb)
            if bb == a + 1:
                return f"{a}/{str(bb)[-2:]}", bb
        m = re.search(r"\b(1[89]\d{2}|20\d{2})\b", lbl or "")
        if m:
            return m.group(1), int(m.group(1))
        for v in (pt, ini):
            if v:
                return v[:4], int(v[:4])
        return None, None

    # só temporadas encerradas antes de 1/jan/2026 (2025/26 e 2026 são disputadas no save)
    for r in comum.ler_json("historia_campeoes_wd.json", []) or []:
        temp, ano_fim = temporada(r.get("sL"), r.get("ini"), r.get("pt"))
        if not temp or ano_fim is None or ano_fim > 2025:
            continue
        for comp in q_comp.get(r["c"], []):
            if comps_db[comp] == "selecoes":
                cid = r.get("wPais")
                if cid == "GBR":
                    cid = "ENG"
                nome = pais_nome.get(cid) or r.get("wL")
            else:
                cid = mp.get(r["w"])
                if cid and comp_pais_db.get(comp) and not mesmo_pais(clube_info[cid][1], comp_pais_db[comp]):
                    cid = None   # clube de outro país (mapa por QID errado): fica só o nome
                nome = clube_info[cid][0] if cid in clube_info else r.get("wL")
            k = (comp, temp, cid or nome)
            if k not in camp:
                camp[k] = [comp, temp, cid, nome, None, None,
                           b.fonte(WD + r["s"], "wikidata", 3, f"Wikidata {r['s']} ({r.get('sL') or ''}): vencedor (P1346)", "CC0")]
    ren = {"bra-serie-a": "bra-1", "bra-serie-b": "bra-2", "bra-serie-c": "bra-3", "bra-serie-d": "bra-4",
           "bra-copa-do-brasil": "bra-copa"}
    for r in comum.ler_json("campeoes.json", []) or []:
        comp = ren.get(r["competicao"], r["competicao"])
        if comp not in comps_db or not r.get("campeao") or r["ano"] > 2025:
            continue
        temp = str(r["ano"])
        cid = mp.get(r["campeao"])
        vid = mp.get(r.get("vice")) if r.get("vice") else None
        vnome = clube_info[vid][0] if vid in clube_info else None
        alvo = [v for k, v in camp.items() if k[0] == comp and k[1] == temp]
        if alvo:
            for a in alvo:
                if a[2] == cid or a[2] is None:
                    a[2] = a[2] or cid
                    a[4], a[5] = vid, vnome
        elif cid:
            f = b.fonte(r["url"], "wikipedia", 3, "Wikipedia (temporada)", "CC BY-SA")
            camp[(comp, temp, cid)] = [comp, temp, cid, clube_info[cid][0], vid, vnome, f]
    # listas completas de campeões da Wikipedia (coleta_campeoes_wiki.py): substituem, para cada competição
    # que cobrem, o que veio do Wikidata/v1 (que tinha buracos, duplicatas e clubes homônimos)
    wl = comum.ler_json("campeoes_wiki.json", []) or []
    comps_wiki = {r["competicao_id"] for r in wl if r["competicao_id"] in comps_db}
    est["campeoes_wiki_sem_competicao"] = len({r["competicao_id"] for r in wl} - comps_wiki)
    if comps_wiki:
        camp = {k: v for k, v in camp.items() if k[0] not in comps_wiki}
        nome_idx = defaultdict(list)
        for cid_, (nm, ps) in clube_info.items():
            nome_idx[(ps, chave_clube(nm))].append(cid_)
        rep_cl = dict(c.execute("SELECT id, IFNULL(reputacao,0) FROM clubes"))
        uf_cl = dict(c.execute("SELECT id, uf FROM clubes"))

        qid_tm = comum.ler_json("campeoes_wiki_tm.json", {}) or {}
        tm_cl = {str(t): cid_ for cid_, t in c.execute("SELECT id, tm_id FROM clubes WHERE tm_id IS NOT NULL")}

        def clube_de(qid_, nome_, comp):
            pais_c = comp_pais_db.get(comp)
            cands = []
            if qid_ and qid_ in mp:
                cands.append(mp[qid_])
            if qid_ and str(qid_tm.get(qid_)) in tm_cl:
                cands.append(tm_cl[str(qid_tm[qid_])])
            for x in cands:
                if not pais_c or mesmo_pais(clube_info[x][1], pais_c):
                    return x
            if not nome_:
                return None
            al = ALIAS_CAMPEAO.get((comp, nome_)) or ALIAS_CAMPEAO.get((None, nome_))
            if al in clube_info:
                return al
            if comp.startswith("est-") or comp.startswith("bra-"):
                cs = nome_idx.get(("BRA", chave_clube(nome_)), [])
                if comp.startswith("est-"):
                    uf = comp.split("-")[1].upper()
                    cs = [x for x in cs if uf_cl.get(x) == uf] or [x for x in cs if not uf_cl.get(x)]
                return max(cs, key=lambda x: rep_cl.get(x, 0)) if cs else None
            if pais_c:
                cs = nome_idx.get((pais_c, chave_clube(nome_)), [])
                return max(cs, key=lambda x: rep_cl.get(x, 0)) if cs else None
            return None
        for r in wl:
            comp = r["competicao_id"]
            if comp not in comps_wiki:
                continue
            temp = str(r["temporada"])
            if comps_db[comp] == "selecoes":
                cid, vid = r.get("campeao_iso3"), r.get("vice_iso3")
                nome = pais_nome.get(cid) or r.get("campeao_nome")
                vnome = pais_nome.get(vid) or r.get("vice_nome")
            else:
                cid = clube_de(r.get("campeao_qid"), r.get("campeao_nome"), comp)
                vid = clube_de(r.get("vice_qid"), r.get("vice_nome"), comp)
                nome = clube_info[cid][0] if cid in clube_info else r.get("campeao_nome")
                vnome = clube_info[vid][0] if vid in clube_info else r.get("vice_nome")
            if not nome or re.fullmatch(r"(?i)\s*(unknown|desconhecido|n/?a|-+|—|not held|não realizado)?\s*", nome):
                continue
            if (comp, temp, r.get("campeao_nome")) in TITULOS_REVOGADOS:
                continue
            # mesmo clube 2x na mesma temporada só com vices diferentes (ex.: Palmeiras 1967, Taça Brasil + Robertão)
            k = (comp, temp, cid or nome, vid or vnome)
            if k in camp:
                continue
            camp[k] = [comp, temp, cid, nome, vid, vnome,
                       b.fonte(r["fonte_url"], "wikipedia", 3, "Wikipedia: lista de campeões", "CC BY-SA")]
        est["campeoes_competicoes_wikipedia"] = len(comps_wiki)
    c.executemany("INSERT INTO campeoes_historicos VALUES (?,?,?,?,?,?,?)", list(camp.values()))
    est["campeoes_linhas"] = len(camp)
    est["campeoes_competicoes"] = len({k[0] for k in camp})
    est["campeoes_com_vice"] = sum(1 for v in camp.values() if v[4] or v[5])
    est["campeoes_clube_ligado"] = sum(1 for v in camp.values() if v[2])
    c.execute("INSERT OR REPLACE INTO meta VALUES ('historia', ?)", (json.dumps(dict(est)),))
    print(f"  história: {dict(est)} ({dt.datetime.now() - t0})", flush=True)
    return dict(est)


ETAPAS = {"paises": c_paises, "clubes": c_clubes, "jogadores": c_jogadores, "carreiras": c_carreiras,
          "candidatos": c_candidatos, "lendas": c_lendas, "competicoes": c_competicoes, "campeoes": c_campeoes,
          "fotos": c_fotos, "posicoes": c_posicoes, "campeoes-tm": c_campeoes_tm,
          "posicoes-en": lambda: c_posicoes("en"), "posicoes-pt": lambda: c_posicoes("pt")}

if __name__ == "__main__":
    if sys.argv[1] == "coletar":
        for e in sys.argv[2:]:
            print("==", e, flush=True)
            ETAPAS[e]()
    elif sys.argv[1] == "aplicar":
        aplicar_standalone()
