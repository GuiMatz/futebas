"""FUTÉBAS: campeões (e vices) reais de todas as épocas, das listas da Wikipedia.

Saída: coleta/intermediario/campeoes_wiki.json — lista de
  {competicao_id, temporada, campeao_nome, campeao_titulo, campeao_lang, campeao_qid, [campeao_iso3],
   vice_nome, vice_titulo, vice_qid, [vice_iso3], fonte_url}
O historia.aplicar() usa esta lista (prioridade sobre os vencedores P1346 do Wikidata).

Fonte: tabelas "wikitable" dos artigos (Wikipedia em inglês; caminho /wiki/, permitido pelo robots.txt),
com cache em coleta/cache/ e 1 req/s. O link de cada campeão (alvo do <a>) vira QID pelo sitelink do
Wikidata (SPARQL); títulos que são redirecionamento são resolvidos abrindo a página (/wiki/, link canonical).
Só temporadas encerradas até 31/12/2025 (europeias até 2024/25).

Uso: python coleta_campeoes_wiki.py [competicao_id ...]
"""
from __future__ import annotations

import json
import re
import sys
import urllib.parse
from collections import Counter, defaultdict

from bs4 import BeautifulSoup

import comum
from comum import norm

# ---------------------------------------------------------------- configuração
# tabelas: "auto" = toda wikitable com coluna de temporada + campeão, antes da 1ª tabela-resumo (Club/Rank);
#          lista de índices = só essas; "anos" = tabela-resumo "Club | Years won | Years runner-up"
# cal: "ano" (temporada '2019') | "eur" ('2018/19' quando a fonte dá intervalo)
# sel: competição de seleções (gera iso3)
C = {
    "bra-1": dict(p="List of Brazilian football champions", t=[0], cal="ano"),
    "bra-2": dict(p="Campeonato Brasileiro Série B", t=[1, 2], cal="ano"),
    "bra-3": dict(p="Campeonato Brasileiro Série C", t=[1], cal="ano"),
    "bra-4": dict(p="Campeonato Brasileiro Série D", t=[0], cal="ano"),
    "bra-copa": dict(p="Copa do Brasil", t=[2], cal="ano"),
    "bra-supercopa": dict(p="Supercopa do Brasil", t=[0], cal="ano"),
    "bra-copa-nordeste": dict(p="Copa do Nordeste", t=[0], cal="ano"),
    "conmebol-libertadores": dict(p="List of Copa Libertadores finals", t=[1], cal="ano"),
    "conmebol-sul-americana": dict(p="List of Copa Sudamericana finals", t=[1], cal="ano"),
    "conmebol-recopa": dict(p="Recopa Sudamericana", t="anos", cal="ano"),
    "intercontinental": dict(p="List of Intercontinental Cup matches", t="anos", cal="ano"),
    "fifa-mundial-clubes": dict(p="List of FIFA Club World Cup finals", t=[1], cal="ano"),
    "fifa-intercontinental": dict(p="FIFA Intercontinental Cup", t=[0], cal="ano"),
    "uefa-champions": dict(p="List of European Cup and UEFA Champions League finals", t=[1], cal="eur"),
    "uefa-europa": dict(p="List of UEFA Cup and Europa League finals", t=[1], cal="eur"),
    "uefa-conference": dict(p="UEFA Conference League", t=[0], cal="eur"),
    "uefa-supercopa": dict(p="List of UEFA Super Cup matches", t=[1], cal="ano"),
    "eng-1": dict(p="List of English football champions", t=[0], cal="eur"),
    "eng-copa": dict(p="List of FA Cup finals", t=[1], cal="eur"),
    "esp-1": dict(p="List of Spanish football champions", t=[1], cal="eur"),
    "esp-copa": dict(p="List of Copa del Rey finals", t=[0], cal="eur"),
    "ita-1": dict(p="List of Italian football champions", t=[0, 1, 2, 3, 5], cal="eur"),
    "ita-copa": dict(p="List of Coppa Italia finals", t=[1], cal="eur"),
    "deu-1": dict(p="List of German football champions", t=[0, 1, 2, 3, 6], cal="eur"),
    "deu-copa": dict(p="List of DFB-Pokal finals", t=[0], cal="eur"),
    "fra-1": dict(p="List of French football champions", t=[1], cal="eur", desde=1933),   # era profissional (LFP)
    "fra-copa": dict(p="List of Coupe de France finals", t=[0], cal="eur"),
    "prt-1": dict(p="List of Portuguese football champions", t=[1], cal="eur"),
    "nld-1": dict(p="List of Dutch football champions", t=[0, 1], cal="eur"),
    "arg-1": dict(p="List of Argentine Primera División champions", t=[0], cal="ano"),
    "ury-1": dict(p="Uruguayan Primera División", t=[3], cal="ano"),
    "fifa-copa-do-mundo": dict(p="List of FIFA World Cup finals", t=[1], cal="ano", sel=True),
    "copa-america": dict(p="Copa América", t=[4], cal="ano", sel=True),
    "eurocopa": dict(p="List of UEFA European Championship finals", t=[1], cal="ano", sel=True),
}
UF_NOME = {"AC": "Acreano", "AL": "Alagoano", "AM": "Amazonense", "AP": "Amapaense", "BA": "Baiano", "CE": "Cearense",
           "DF": "Brasiliense", "ES": "Capixaba", "GO": "Goiano", "MA": "Maranhense", "MG": "Mineiro",
           "MS": "Sul-Mato-Grossense", "MT": "Mato-Grossense", "PA": "Paraense", "PB": "Paraibano", "PE": "Pernambucano",
           "PI": "Piauiense", "PR": "Paranaense", "RJ": "Carioca", "RN": "Potiguar", "RO": "Rondoniense",
           "RR": "Roraimense", "RS": "Gaúcho", "SC": "Catarinense", "SE": "Sergipano", "SP": "Paulista",
           "TO": "Tocantinense"}
for _uf, _n in UF_NOME.items():
    C[f"est-{_uf.lower()}"] = dict(p=f"Campeonato {_n}", t="auto", cal="ano")
C["est-sc"] = dict(p="Campeonato Catarinense de Futebol", lang="pt", t=[0], cal="ano", aninhada=True)   # en sem lista

ULTIMO_ANO = 2025
SEL_ISO = {"west germany": "DEU", "germany": "DEU", "soviet union": "RUS", "ussr": "RUS", "czechoslovakia": "CZE",
           "yugoslavia": "SRB", "england": "ENG", "scotland": "SCO", "wales": "WAL", "northern ireland": "NIR",
           "netherlands": "NLD", "republic of ireland": "IRL", "south korea": "KOR", "czech republic": "CZE",
           "russia": "RUS", "turkey": "TUR", "cis": "RUS", "ivory coast": "CIV"}


# ---------------------------------------------------------------- tabela -> grade
def grade(tb):
    """Expande rowspan/colspan: lista de linhas, cada uma lista de (id_célula, tag)."""
    linhas, pend = [], {}   # pend[col] = [restantes, cel]
    for ri, tr in enumerate(tb.find_all("tr")):
        if tr.find_parent("table") is not tb:
            continue
        row, col = [], 0
        cels = tr.find_all(["th", "td"], recursive=False)
        it = iter(cels)
        while True:
            if col in pend:
                rest, cel = pend[col]
                row.append(cel)
                pend[col][0] -= 1
                if pend[col][0] <= 0:
                    del pend[col]
                col += 1
                continue
            cel = next(it, None)
            if cel is None:
                break
            try:
                cs = int(re.sub(r"\D", "", cel.get("colspan", "1")) or 1)
                rs = int(re.sub(r"\D", "", cel.get("rowspan", "1")) or 1)
            except ValueError:
                cs = rs = 1
            for k in range(min(cs, 30)):
                row.append(cel)
                if rs > 1:
                    pend[col] = [rs - 1, cel]
                col += 1
        # colunas pendentes à direita
        while col in pend:
            rest, cel = pend[col]
            row.append(cel)
            pend[col][0] -= 1
            if pend[col][0] <= 0:
                del pend[col]
            col += 1
        linhas.append(row)
    return linhas


def txt(cel):
    if cel is None:
        return ""
    c = BeautifulSoup(str(cel), "lxml")
    for s in c.select("sup, .reference, style, .sortkey, span[style*='display:none']"):
        s.decompose()
    t = c.get_text(" ", strip=True)
    return re.sub(r"\s+", " ", t).strip()


def links(cel):
    out = []
    if cel is None:
        return out
    for a in cel.find_all("a", href=True):
        h = re.sub(r"^https?://[a-z]+\.wikipedia\.org", "", a["href"])
        if not h.startswith("/wiki/") or a.find_parent(class_="flagicon") or a.find_parent("sup"):
            continue
        if a.find("img") or not a.get_text(strip=True) or "new" in (a.get("class") or []):
            continue   # bandeira (imagem com link para o país/estado)
        t = urllib.parse.unquote(h[6:].split("#")[0].split("?")[0]).replace("_", " ")
        if re.match(r"(File|Ficheiro|Arquivo|Image|Help|Wikipedia|Template|Category|Special):", t):
            continue
        out.append((t, a.get_text(" ", strip=True)))
    return out


def limpar_nome(s):
    s = re.sub(r"\[[^\]]*\]", "", s)
    s = re.sub(r"\((\d+|TB|RB|shared|[A-Z]{2,5})\)", "", s)
    s = re.sub(r"\s*\([^)]*\)\s*", " ", s)
    s = re.sub(r"[†‡*♦#§¤°¹²³]+", "", s)
    s = re.sub(r"\s+[A-Z]{2}$", "", s.strip())   # 'Sport PE'
    return re.sub(r"\s+", " ", s).strip(" ,;-–")


RE_INT = re.compile(r"\b(18[6-9]\d|19\d{2}|20\d{2})\s*[–\-/]\s*(\d{4}|\d{2})\b")
RE_ANO = re.compile(r"\b(18[6-9]\d|19\d{2}|20\d{2})\b")


def temporada(s, cal):
    s = re.sub(r"\[[^\]]*\]", "", s or "")
    m = RE_INT.search(s)
    if m:
        a, b = int(m.group(1)), m.group(2)
        b = int(b) if len(b) == 4 else int(str(a)[:2] + b)
        if b == a + 1:
            return (f"{a}/{str(b)[-2:]}" if cal == "eur" else str(b)), b
    m = RE_ANO.search(s)
    if m:
        return m.group(1), int(m.group(1))
    return None, None


def cabecalhos(g):
    """linhas de cabeçalho (só <th>) no topo -> nome combinado por coluna, e índice da 1ª linha de dados."""
    n, hdr = 0, []
    for row in g:
        if row and all(c.name == "th" for c in row) and n < 4:
            n += 1
            if len({id(c) for c in row}) > 1:
                hdr.append(row)    # linha de grupo (uma célula na largura toda) não é cabeçalho
        else:
            break
    n = max(n, 1)
    hdr = hdr or g[:1]
    larg = max((len(r) for r in g), default=0)
    nomes = []
    for ci in range(larg):
        partes = []
        for r in hdr:
            if ci < len(r):
                t = txt(r[ci]).lower()
                if t and t not in partes:
                    partes.append(t)
        nomes.append(" ".join(partes))
    return nomes, n


K_TEMP = ("season", "year", "temporada", "ano", "date", "edition")
K_CAMP = ("champion", "winner", "campeão", "campeões", "campeao")
K_VICE = ("runner-up", "runners-up", "runner up", "runners up", "second place", "vice", "finalist")


def escolher(nomes, chaves, evitar=(), todas=False):
    cands = [i for i, n in enumerate(nomes) if any(k in n for k in chaves) and not any(e in n for e in evitar)
             and not re.match(r"titles?\b|title\(s\)", n)]
    if todas:
        return cands
    if not cands:
        return None
    pref = [i for i in cands if re.search(r"\bclub|\bteam", nomes[i])]
    return (pref or cands)[0]


def ler_tabela(tb, cal):
    g = grade(tb)
    nomes, h = cabecalhos(g)
    cts = escolher(nomes, K_TEMP, ("ed.", "top scorer", "manager", "seasons", "anos dos", "years won"), todas=True)
    # coluna de temporada = a candidata com mais anos legíveis nos dados (cabeçalho com colspan 2)
    ct = max(cts, key=lambda i: sum(1 for r in g[h:] if i < len(r) and RE_ANO.search(txt(r[i]))), default=None)
    cc = escolher(nomes, K_CAMP, ("country", "nation", "manager", "years won", "seasons won", "points", "anos dos"))
    cv = escolher(nomes, K_VICE, ("country", "nation", "manager", "years", "seasons", "points"))
    if ct is None or cc is None:
        return None
    out = []
    for row in g[h:]:
        if len(row) <= max(ct, cc):
            continue
        if row[ct] is row[cc]:
            continue   # linha de grupo ("Football League (1888–1992)")
        temp, fim = temporada(txt(row[ct]), cal)
        if not temp:
            continue
        cel = row[cc]
        if cv is not None and cv < len(row) and row[cv] is cel:
            continue   # nota que ocupa a linha ("Juventus won 5–2 on aggregate.")
        nome = limpar_nome(txt(cel))
        if re.search(r"\bwon\b|aggregate|penalt|replay", nome, re.I) or re.fullmatch(r"\d+", nome):
            continue
        if not nome or re.search(r"not (held|awarded|finished|contested)|cancel|suspend|abandon|no champion|"
                                 r"void|annulled|interrupted|^none$|^—$|^-$|^\?+$|world war|not played", nome, re.I):
            continue
        ls = links(cel)
        vs = links(row[cv]) if cv is not None and cv < len(row) and row[cv] is not cel else []
        vnome = limpar_nome(txt(row[cv])) if cv is not None and cv < len(row) and row[cv] is not cel else None
        if vnome and re.search(r"^(—|-|\?+|none|n/?a)$", vnome, re.I):
            vnome, vs = None, []
        # título dividido: "A and B" / "A / B" com 2+ links de clube
        campeoes = [(nome, ls[0][0] if ls else None)]
        if len(ls) >= 2 and re.search(r"\band\b|\s/\s|&|\be\b|shared|dividido", txt(cel), re.I):
            campeoes = [(limpar_nome(t2), t) for t, t2 in ls]
        for cn, ctit in campeoes:
            out.append({"temporada": temp, "fim": fim, "campeao_nome": cn, "campeao_titulo": ctit,
                        "vice_nome": vnome, "vice_titulo": vs[0][0] if vs else None})
    return out


def ler_anos(s, cal):
    """tabela-resumo 'Club | ... | Years won | Years runner-up' -> linhas por ano."""
    out = []
    for tb in s.select("table.wikitable"):
        g = grade(tb)
        nomes, h = cabecalhos(g)
        cc = escolher(nomes, ("club", "team"))
        cy = escolher(nomes, ("years won", "seasons won", "winning years", "winning seasons"))
        if cc is None or cy is None:
            continue
        for row in g[h:]:
            if len(row) <= max(cc, cy):
                continue
            ls = links(row[cc])
            nome = limpar_nome(txt(row[cc]))
            for m in re.finditer(r"\b(18[6-9]\d|19\d{2}|20\d{2})(?:\s*[–\-/]\s*(\d{2,4}))?", txt(row[cy])):
                temp, fim = temporada(m.group(0), cal)
                if temp:
                    out.append({"temporada": temp, "fim": fim, "campeao_nome": nome,
                                "campeao_titulo": ls[0][0] if ls else None, "vice_nome": None, "vice_titulo": None})
        if out:
            break
    return out


def e_resumo(nomes):
    j = " ".join(nomes)
    return ("club" in j or "team" in j or "rank" in j) and not any(k in j for k in K_TEMP)


def coletar(cid, cfg):
    lang = cfg.get("lang", "en")
    h = comum.wiki_html(lang, cfg["p"])
    if not h:
        print("  sem página:", cid, cfg["p"])
        return []
    s = BeautifulSoup(h, "lxml")
    can = s.find("link", rel="canonical")
    url = can["href"] if can else comum.wiki_url(lang, cfg["p"])
    tabs = s.select("table.wikitable")
    linhas = []
    if cfg["t"] == "anos":
        linhas = ler_anos(s, cfg["cal"])
    else:
        idx = cfg["t"]
        if idx == "auto":
            idx = []
            for i, tb in enumerate(tabs):
                nomes, _ = cabecalhos(grade(tb))
                if e_resumo(nomes) and idx:
                    break
                idx.append(i)
        for i in idx:
            if i < len(tabs):
                tb = (tabs[i].find("table") or tabs[i]) if cfg.get("aninhada") else tabs[i]
                linhas += ler_tabela(tb, cfg["cal"]) or []
    # a Wikipedia só linka o clube na 1ª aparição: completa o título pelo nome (dentro da competição)
    tit = defaultdict(set)
    for l in linhas:
        for a in ("campeao", "vice"):
            if l.get(f"{a}_titulo") and l.get(f"{a}_nome"):
                tit[norm(l[f"{a}_nome"])].add(l[f"{a}_titulo"])
    for l in linhas:
        for a in ("campeao", "vice"):
            ts = tit.get(norm(l.get(f"{a}_nome") or ""))
            if not l.get(f"{a}_titulo") and ts and len(ts) == 1:
                l[f"{a}_titulo"] = next(iter(ts))
    vis, out = set(), []
    for l in linhas:
        if l["fim"] > ULTIMO_ANO or l["fim"] < cfg.get("desde", 0):
            continue
        k = (l["temporada"], norm(l["campeao_nome"]))
        if k in vis:
            continue
        vis.add(k)
        l.pop("fim")
        out.append({"competicao_id": cid, **l, "campeao_lang": lang, "fonte_url": url})
    return out


# ---------------------------------------------------------------- títulos -> QID
def resolver_qids(titulos, lang="en"):
    res = comum.ler_json("campeoes_wiki_qids.json", {}) or {}
    falta = sorted({t for t in titulos if t and f"{lang}:{t}" not in res})

    def por_sitelink(ts):
        achou = {}
        for lote in comum.lotes(ts, 60):
            vals = " ".join(json.dumps(t, ensure_ascii=False) + f"@{lang}" for t in lote)
            for r in comum.sparql(f"""SELECT ?t ?i WHERE {{ VALUES ?t {{ {vals} }} ?a schema:name ?t;
                                     schema:isPartOf <https://{lang}.wikipedia.org/>; schema:about ?i }}"""):
                achou[r["t"]] = comum.qid(r["i"])
        return achou

    a = por_sitelink(falta)
    for t in falta:
        if t in a:
            res[f"{lang}:{t}"] = a[t]
    # redirecionamentos: abre a página e lê o link canonical
    sobra = [t for t in falta if t not in a]
    print(f"  títulos: {len(falta)} novos, {len(a)} por sitelink, {len(sobra)} a resolver por redirecionamento")
    canon = {}
    for t in sobra:
        h = comum.wiki_html(lang, t)
        m = re.search(r'<link rel="canonical" href="https://[a-z]+\.wikipedia\.org/wiki/([^"]+)"', h or "")
        if m:
            canon[t] = urllib.parse.unquote(m.group(1)).replace("_", " ")
    b = por_sitelink(sorted(set(canon.values())))
    for t in sobra:
        res[f"{lang}:{t}"] = b.get(canon.get(t))
    comum.salvar_json("campeoes_wiki_qids.json", res)
    return {k.split(":", 1)[1]: v for k, v in res.items() if k.startswith(lang + ":")}


def iso_sel(nome, titulo):
    from paises import iso3
    for s in (titulo or "", nome or ""):
        b = re.sub(r"\s*(men's )?national (football|soccer) team.*$", "", s, flags=re.I).strip()
        if norm(b) in SEL_ISO:
            return SEL_ISO[norm(b)]
        i = iso3(b)
        if i and i != "GBR":
            return i
    return None


def main(alvo):
    todos = comum.ler_json("campeoes_wiki.json", []) or []
    todos = [r for r in todos if alvo and r["competicao_id"] not in alvo]
    novos = []
    for cid, cfg in C.items():
        if alvo and cid not in alvo:
            continue
        rs = coletar(cid, cfg)
        if cfg.get("sel"):
            for r in rs:
                r["campeao_iso3"] = iso_sel(r["campeao_nome"], r["campeao_titulo"])
                r["vice_iso3"] = iso_sel(r["vice_nome"], r["vice_titulo"]) if r["vice_nome"] else None
        novos += rs
    # títulos ainda vazios: mesmo nome com link em outra competição do mesmo país (prefixo), se único
    tit = defaultdict(set)
    fam = lambda c: c if c.startswith("est-") else c.split("-")[0]
    for r in novos:
        for a in ("campeao", "vice"):
            if r.get(f"{a}_titulo") and r.get(f"{a}_nome"):
                tit[(fam(r["competicao_id"]), norm(r[f"{a}_nome"]))].add(r[f"{a}_titulo"])
    for r in novos:
        for a in ("campeao", "vice"):
            ts = tit.get((fam(r["competicao_id"]), norm(r.get(f"{a}_nome") or "")))
            if not r.get(f"{a}_titulo") and ts and len(ts) == 1:
                r[f"{a}_titulo"] = next(iter(ts))
    for lang in sorted({r["campeao_lang"] for r in novos}):
        rs = [r for r in novos if r["campeao_lang"] == lang]
        q = resolver_qids([r["campeao_titulo"] for r in rs] + [r["vice_titulo"] for r in rs], lang)
        for r in rs:
            r["campeao_qid"] = q.get(r["campeao_titulo"]) if r["campeao_titulo"] else None
            r["vice_qid"] = q.get(r["vice_titulo"]) if r["vice_titulo"] else None
    todos += novos
    ordem = list(C)
    todos.sort(key=lambda r: (ordem.index(r["competicao_id"]) if r["competicao_id"] in ordem else 999, r["temporada"]))
    comum.salvar_json("campeoes_wiki.json", todos)
    por = defaultdict(list)
    for r in todos:
        por[r["competicao_id"]].append(r)
    print(f"{'competição':26} linhas temps  primeira  última   vice  qid")
    for cid in ordem:
        rs = por.get(cid, [])
        if not rs:
            print(f"{cid:26} (nada)")
            continue
        ts = sorted({r["temporada"] for r in rs})
        print(f"{cid:26} {len(rs):5} {len(ts):5}  {ts[0]:9} {ts[-1]:8} {sum(1 for r in rs if r['vice_nome']):4} "
              f"{sum(1 for r in rs if r['campeao_qid'] or r.get('campeao_iso3')):4}")


if __name__ == "__main__":
    main(set(sys.argv[1:]))
