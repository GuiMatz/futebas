"""FUTÉBAS: transferências brasileiras de 2026 (Séries A e B) pelas páginas de temporada da Wikipedia.

O transfermarkt-datasets parou em 06/07/2026 e o Wikidata P54 cobre pouco a janela brasileira do meio do ano.
Fontes (só páginas públicas, /wiki/<título>?action=raw — o robots.txt da Wikipedia proíbe /w/):
  - en.wikipedia "2026 <clube> season": tabelas Transfers in/out, Loans in/out (+ elenco "Fs player");
  - pt.wikipedia "Temporada do <clube> de 2026": tabelas Entradas/Saídas/Empréstimos/Emprestados;
  - en.wikipedia "Template:<clube> squad": elenco atual dos clubes sem página de temporada
    (data = "última edição" do rodapé do HTML /wiki/Template:...);
  - Wikidata SPARQL: título do artigo -> QID, nascimento, Transfermarkt id, nacionalidade, posição.
Saída: coleta/intermediario/transferencias_br_2026.json (lido por montar_banco.py, passo "transferências BR 2026").
Cache em coleta/cache/<domínio>; User-Agent genérico; <= 1 req/s.
Uso: python coleta_transferencias_br.py
"""
from __future__ import annotations

import datetime as dt
import html
import json
import re
import sqlite3
from collections import Counter, defaultdict

import comum

comum.UA = "FUTEBAS-dados/1.0"
comum._sess.headers["User-Agent"] = comum.UA

TEMPORADAS = ("bra-1", "bra-2")
MESES_EN = {m: i + 1 for i, m in enumerate(["january", "february", "march", "april", "may", "june", "july", "august",
                                              "september", "october", "november", "december"])}
MESES_PT = {m: i + 1 for i, m in enumerate(["janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto",
                                              "setembro", "outubro", "novembro", "dezembro"])}
# títulos que não seguem o padrão "2026 <título en do clube> season"
EN_EXTRA = {"cruzeiro": ["2026 Cruzeiro Esporte Clube season"],
            "internacional": ["2026 Sport Club Internacional season"]}


# ------------------------------------------------------------------ wikitexto
def raw(lang, titulo, seguir=True):
    """(wikitexto, título final) seguindo redirecionamentos; (None, título) se não existe."""
    t = titulo
    for _ in range(4):
        x = comum.baixar(comum.wiki_url(lang, t) + "?action=raw")
        if x is None:
            return None, t
        m = re.match(r"\s*#(REDIRECT|REDIRECIONAMENTO)\s*\[\[([^\]|#]+)", x, re.I)
        if not m or not seguir:
            return x, t
        t = m.group(2).strip()
    return None, t


def ultima_edicao(lang, titulo):
    """Data da última edição (rodapé do HTML /wiki/)."""
    h = comum.wiki_html(lang, titulo) or ""
    m = re.search(r"(?:last edited on|editada pela última vez em)\s+(\d{1,2})\s+(?:de\s+)?([A-Za-zç]+)\s+(?:de\s+)?(\d{4})", h)
    if not m:
        return None
    mes = MESES_EN.get(m.group(2).lower()) or MESES_PT.get(m.group(2).lower())
    return f"{m.group(3)}-{mes:02d}-{int(m.group(1)):02d}" if mes else None


def sem_comentarios(x):
    return re.sub(r"<!--.*?-->", "", x, flags=re.S)


def dividir_topo(s, sep="|"):
    """Divide s por sep fora de [[ ]] e {{ }}."""
    out, prof_l, prof_t, cur, i = [], 0, 0, [], 0
    while i < len(s):
        if s.startswith("[[", i):
            prof_l += 1; cur.append("[["); i += 2; continue
        if s.startswith("]]", i) and prof_l:
            prof_l -= 1; cur.append("]]"); i += 2; continue
        if s.startswith("{{", i):
            prof_t += 1; cur.append("{{"); i += 2; continue
        if s.startswith("}}", i) and prof_t:
            prof_t -= 1; cur.append("}}"); i += 2; continue
        if s.startswith(sep, i) and not prof_l and not prof_t:
            out.append("".join(cur)); cur = []; i += len(sep); continue
        cur.append(s[i]); i += 1
    out.append("".join(cur))
    return out


def conteudo_celula(c):
    """Tira os atributos (style=..|conteúdo) de uma célula."""
    ps = dividir_topo(c, "|")
    if len(ps) >= 2 and re.search(r"(style|align|class|scope|colspan|rowspan|data-sort[-\w]*|width|bgcolor)\s*=", ps[0], re.I) \
            and not re.search(r"\[\[|\{\{", ps[0]):
        return "|".join(ps[1:]).strip()
    return c.strip()


def secoes(x):
    """[(nível, título, início, fim, caminho de títulos)]"""
    hs = [(len(m.group(1)), m.group(2).strip(), m.start(), m.end())
          for m in re.finditer(r"^(={2,6})\s*(.+?)\s*\1\s*$", x, re.M)]
    out, pilha = [], []
    for i, (nv, t, ini, fim_h) in enumerate(hs):
        fim = next((h[2] for h in hs[i + 1:] if h[0] <= nv), len(x))
        pilha = [p for p in pilha if p[0] < nv] + [(nv, t)]
        out.append((nv, t, fim_h, fim, [p[1] for p in pilha]))
    return out


def tabelas(x):
    """[(início, texto)] das wikitables de nível superior."""
    out, i = [], 0
    while True:
        a = x.find("{|", i)
        if a < 0:
            return out
        prof, j = 0, a
        while j < len(x):
            if x.startswith("{|", j):
                prof += 1; j += 2; continue
            if x.startswith("|}", j):
                prof -= 1; j += 2
                if prof == 0:
                    break
                continue
            j += 1
        out.append((a, x[a:j]))
        i = j


def linhas_tabela(t):
    """Lista de linhas; cada linha = lista de (é_cabeçalho, texto bruto da célula)."""
    corpo = t[2:-2] if t.endswith("|}") else t[2:]
    linhas = re.split(r"\n\|-[^\n]*", "\n" + corpo.split("\n", 1)[1] if "\n" in corpo else "")
    out = []
    for ln in linhas:
        cels = []
        for l in ln.split("\n"):
            if l.startswith("|+"):
                continue
            if l.startswith("!"):
                for c in re.split(r"!!|\|\|", l[1:]):
                    cels.append([True, c])
            elif l.startswith("|"):
                for c in dividir_topo(l[1:], "||"):
                    cels.append([False, c])
            elif cels:
                cels[-1][1] += "\n" + l
        if cels:
            out.append(cels)
    return out


RE_LINK = re.compile(r"\[\[([^\]|#]+)(?:#[^\]|]*)?(?:\|([^\]]+))?\]\]")


def links(s):
    return [(m.group(1).strip(), (m.group(2) or m.group(1)).strip()) for m in RE_LINK.finditer(s)]


def texto(s):
    s = re.sub(r"<ref[^>]*/>|<ref[^>]*>.*?</ref>", "", s, flags=re.S)
    s = re.sub(r"\{\{(?:sort|sortname|nts|ntsh)\|([^|}]*)\|([^}]*)\}\}", r"\2", s, flags=re.I)
    s = re.sub(r"\{\{(?:abbr|tooltip)\|([^|}]*)\|[^}]*\}\}", r"\1", s, flags=re.I)
    s = RE_LINK.sub(lambda m: m.group(2) or m.group(1), s)
    s = re.sub(r"\{\{[^{}]*\}\}", "", s)
    s = re.sub(r"<[^>]+>|'''?", "", s)
    return html.unescape(re.sub(r"\s+", " ", s)).strip()


def bandeira(s):
    m = re.search(r"\{\{\s*(?:flagicon|fbaicon|fbicon|flag icon|flagdeco|fb)\s*\|\s*([A-Z]{3})", s, re.I)
    if m:
        return m.group(1).upper()
    m = re.search(r"\{\{([A-Z]{3})b\}\}", s)
    return m.group(1) if m else None


def data_de(s, lang="en"):
    """AAAA-MM-DD de uma célula/ref (dts, '1 September 2026', 'September 1, 2026', 1/9/2026, ISO)."""
    if not s:
        return None
    m = re.search(r"\{\{\s*(?:dts|date|start date|dtsort)\s*\|\s*(\d{4})\s*\|\s*(\d{1,2})\s*\|\s*(\d{1,2})", s, re.I)
    if m:
        return f"{m.group(1)}-{int(m.group(2)):02d}-{int(m.group(3)):02d}"
    t = texto(re.sub(r"\{\{\s*(?:dts|date)\s*\|([^}]*)\}\}", r"\1", s, flags=re.I))
    m = re.search(r"(\d{1,2})(?:st|nd|rd|th)?\s+(?:de\s+)?([A-Za-zç]+)\s+(?:de\s+)?(\d{4})", t)
    if m and (MESES_EN.get(m.group(2).lower()) or MESES_PT.get(m.group(2).lower())):
        mes = MESES_EN.get(m.group(2).lower()) or MESES_PT.get(m.group(2).lower())
        return f"{m.group(3)}-{mes:02d}-{int(m.group(1)):02d}"
    m = re.search(r"([A-Za-z]+)\s+(\d{1,2}),?\s+(\d{4})", t)
    if m and MESES_EN.get(m.group(1).lower()):
        return f"{m.group(3)}-{MESES_EN[m.group(1).lower()]:02d}-{int(m.group(2)):02d}"
    m = re.search(r"(\d{4})-(\d{2})-(\d{2})", t)
    if m:
        return m.group(0)
    m = re.search(r"\b(\d{1,2})/(\d{1,2})/(\d{2,4})\b", t)
    if m:
        a = int(m.group(3)); a = a + 2000 if a < 100 else a
        d, me = int(m.group(1)), int(m.group(2))
        if lang == "en" and d <= 12 < me:
            d, me = me, d
        if 1 <= me <= 12 and 1 <= d <= 31:
            return f"{a}-{me:02d}-{d:02d}"
    return None


def data_ref(s):
    """Data pela citação: |date= da ref ou /AAAA/MM/DD/ da URL."""
    for m in re.finditer(r"\|\s*(?:date|data)\s*=\s*([^|}]+)", s):
        d = data_de(m.group(1), "pt" if "/" in m.group(1) else "en")
        if d:
            return d
    m = re.search(r"/(20\d\d)/(\d{1,2})/(\d{1,2})/", s)
    if m:
        return f"{m.group(1)}-{int(m.group(2)):02d}-{int(m.group(3)):02d}"
    return None


def url_ref(s):
    m = re.search(r"\|\s*url\s*=\s*(https?://[^\s|}]+)", s)
    return m.group(1) if m else None


# ------------------------------------------------------------------ classificação
RE_LIVRE = re.compile(r"free agent|released|unattached|without club|sem clube|livre|desempregad|contract terminated|"
                      r"rescis|terminated", re.I)
RE_APOS = re.compile(r"retire|aposentad|aposentou|encerrou a carreira", re.I)
RE_RETORNO = re.compile(r"loan return|return(ed)? from loan|end of loan|loan ended|retorno|fim de empr[eé]stimo|"
                        r"volta de empr|returned|\{\{empr[eé]stimo\}\}", re.I)
RE_EMPREST = re.compile(r"\bloan|empr[eé]stim|emprestad", re.I)
RE_BASE_TIME = re.compile(r"youth|academy|u-?\d\d|sub-?\d\d|under-\d\d|reserves|\bB team|aspirantes|base", re.I)
RE_PERMANENTE = re.compile(r"made permanent|permanent|definitiv|option exercised|compra", re.I)


def classe_cabecalho(h):
    h = texto(h).lower()
    if re.search(r"\bpos", h) or "posição" in h:
        return "pos"
    if re.search(r"player|name|jogador|nome", h):
        return "jogador"
    if re.search(r"loaned (from|to)|from club|to club|transferred|moving|signed from|^from$|^to$|clube|club|"
                 r"origem|destino|from|to\b", h):
        return "clube"
    if re.search(r"loan expires|until|^end|fim|término|period|período", h):
        return "fim"
    if re.search(r"date|start|data|início", h):
        return "data"
    if re.search(r"fee|type|notes|valor|tipo", h):
        return "tipo"
    if re.search(r"team|equipe|elenco", h):
        return "time"
    if re.search(r"source|ref", h):
        return "ref"
    return None


def direcao_de(caminho, cab_clube, lang):
    """'in' | 'out' | None, pela seção (mais próxima primeiro) e pelo cabeçalho da coluna do clube."""
    for t in reversed(caminho):
        tl = texto(t).lower()
        if re.search(r"\b(in|arrivals|incoming|signings)\b|entradas|chegadas|contrata|empréstimos$|^empréstimos", tl):
            if not re.search(r"\bout\b|saídas|emprestados", tl):
                return "in", ("loan" in tl or tl.startswith("empréstimos"))
        if re.search(r"\b(out|departures|outgoing)\b|saídas|emprestados|dispensa", tl):
            return "out", ("loan" in tl or "emprestados" in tl)
    h = texto(cab_clube or "").lower()
    if re.search(r"from|origem|anterior", h):
        return "in", "loan" in h
    if re.search(r"\bto\b|destino", h):
        return "out", "loan" in h
    return None, False


# ------------------------------------------------------------------ pt: predefinições {{Futebol X|país=antes}}
_pred_cache = {}


def clube_pt_predef(s):
    """Para {{Futebol Internacional-RS|país=antes}} devolve (título do artigo, nome, bandeira) lendo a predefinição."""
    m = re.search(r"\{\{\s*(Futebol [^|}]+)", s)
    if not m:
        return None
    nome = m.group(1).strip()
    if nome not in _pred_cache:
        x, _ = raw("pt", "Predefinição:" + nome)
        res = None
        if x:
            x2 = re.sub(r"<noinclude>.*?</noinclude>", "", x, flags=re.S)
            ls = [l for l in links(x2) if not re.match(r"(Ficheiro|Arquivo|File|Imagem|Image|Categoria):", l[0], re.I)]
            if ls:
                res = (ls[0][0], ls[0][1], bandeira(x2))
        _pred_cache[nome] = res
    return _pred_cache[nome]


# ------------------------------------------------------------------ extração
def extrair_movimentos(x, lang, clube, titulo):
    x = sem_comentarios(x)
    secs = secoes(x)
    url = comum.wiki_url(lang, titulo)
    out = []
    for ini, t in tabelas(x):
        sec = next((s for s in reversed(secs) if s[2] <= ini < s[3]), None)
        caminho = sec[4] if sec else []
        if not any(re.search(r"transfer|loan|entradas|saídas|empréstimos|emprestados|contrata|signings|departures|arrivals",
                             c, re.I) for c in caminho):
            continue
        cap = re.search(r"^\|\+\s*(.+)$", t, re.M)
        cam = caminho + ([cap.group(1)] if cap else [])
        rows = linhas_tabela(t)
        cab, col = None, {}
        for r in rows:
            if all(h for h, _ in r) and len(r) >= 3 and cab is None:
                cab = [conteudo_celula(c) for _, c in r]
                for i, c in enumerate(cab):
                    k = classe_cabecalho(c)
                    if k and k not in col:
                        col[k] = i
                continue
            if cab is None or "jogador" not in col:
                continue
            if any(h for h, _ in r) and len(r) <= 2:
                continue
            cels = [conteudo_celula(c) for _, c in r]
            if len(cels) < len(cab) - 1 or texto(" ".join(cels)).lower().startswith("total"):
                continue
            # tabelas lado a lado (Ceará, Fortaleza: in | out na mesma linha) – só a primeira metade aqui
            g = lambda k: cels[col[k]] if k in col and col[k] < len(cels) else ""
            dirc, emp_sec = direcao_de(cam, cab[col["clube"]] if "clube" in col else "", lang)
            if dirc is None:
                continue
            jc = g("jogador")
            lj = links(jc)
            nome_j = texto(re.sub(r"\{\{\s*[Tt]ooltip\|([^|}]*)\|[^}]*\}\}", r"\1", jc))
            nome_j = re.sub(r"\s*\((loan|empréstimo)[^)]*\)", "", nome_j, flags=re.I).strip()
            if not nome_j or len(nome_j) < 2:
                continue
            cc = g("clube")
            pred = clube_pt_predef(cc) if lang == "pt" else None
            lc = links(cc)
            if pred:
                ctit, cnome, cband = pred
            elif lc:
                ctit, cnome, cband = lc[0][0], lc[0][1], bandeira(cc)
            else:
                ctit, cnome, cband = None, texto(cc), bandeira(cc)
            tipo_txt = texto(g("tipo")) + " " + texto(cels[col["clube"]] if "clube" in col else "")
            tudo = " ".join(cels)
            tipo = "transfer"
            if re.search(r"\{\{\s*empr[eé]stimo\s*\}\}", jc, re.I) and dirc == "in":
                tipo = "retorno"
            elif RE_RETORNO.search(tipo_txt):
                tipo = "retorno"
            elif RE_PERMANENTE.search(tipo_txt):
                tipo = "transfer"
            elif emp_sec or RE_EMPREST.search(tipo_txt) or re.search(r"\{\{\s*emprestado\s*\}\}", jc, re.I):
                tipo = "emprestimo"
            if dirc == "out" and RE_APOS.search(tipo_txt + " " + (cnome or "")):
                tipo = "aposentou"
            elif dirc == "out" and tipo == "transfer" and (not cnome or RE_LIVRE.search(cnome or "")) and not lc and not pred:
                tipo = "livre"
            elif dirc == "out" and cnome and RE_LIVRE.search(cnome) and (not lc or RE_LIVRE.search(lc[0][0])):
                tipo = "livre"
            data = data_de(g("data"), lang) if "data" in col else None
            fim_txt = g("fim")
            fim = data_de(fim_txt, lang) if fim_txt else None
            encerrado = bool(re.search(r"<s>", fim_txt or ""))
            data = data or data_ref(tudo)
            time_ = texto(g("time"))
            base = bool(time_ and RE_BASE_TIME.search(time_) and not re.search(r"first|principal|profissional", time_, re.I))
            out.append({"clube": clube, "lang": lang, "artigo": titulo, "url": url, "secao": " > ".join(cam)[-120:],
                        "direcao": dirc, "tipo": tipo, "jogador": nome_j,
                        "jogador_titulo": lj[0][0] if lj else None, "jogador_pais": bandeira(jc),
                        "pos": texto(g("pos"))[:6] or None,
                        "outro_titulo": ctit, "outro_nome": cnome, "outro_pais": cband,
                        "data": data, "fim": fim, "emprestimo_encerrado": encerrado,
                        "base": base, "fonte_url": url_ref(tudo)})
    return out


def extrair_elenco(x, lang, clube, titulo, data):
    """Fs player / Football squad2 player: [{nome, titulo, pais, pos, emprestado_para}] fora das seções de base/estatística."""
    x = sem_comentarios(x)
    secs = secoes(x)
    out = []
    for m in re.finditer(r"\{\{\s*(?:fs player|football squad2? player|fs2 player)\s*\|", x, re.I):
        # fim do template
        prof, j = 0, m.start()
        while j < len(x):
            if x.startswith("{{", j):
                prof += 1; j += 2; continue
            if x.startswith("}}", j):
                prof -= 1; j += 2
                if prof == 0:
                    break
                continue
            j += 1
        tpl = x[m.start() + 2:j - 2]
        sec = next((s for s in reversed(secs) if s[2] <= m.start() < s[3]), None)
        cam = " > ".join(sec[4]) if sec else ""
        if re.search(r"youth|academy|statistic|appearances|goals|reserve|under-|u-?\d\d|base|former|left|departed|"
                     r"retired|number changes", cam, re.I):
            continue
        par = {}
        for p in dividir_topo(tpl, "|")[1:]:
            if "=" in p:
                k, v = p.split("=", 1)
                par[k.strip().lower()] = v.strip()
        nm = par.get("name", "")
        if re.search(r"<s>|<del>", nm):
            continue          # riscado = já saiu
        ln = links(nm)
        other = par.get("other", "")
        loan_out = bool(re.search(r"loan|^at\s", texto(other), re.I)) or bool(re.search(r"out on loan|loaned out", cam, re.I))
        lo = links(other)
        if re.search(r"on loan from|emprestado (pelo|por|do)", texto(other), re.I):
            # emprestado AO clube do elenco
            out.append({"nome": texto(nm), "titulo": ln[0][0] if ln else None, "pais": par.get("nat"),
                        "pos": par.get("pos") or par.get("no"), "emprestado_de_titulo": lo[0][0] if lo else None,
                        "emprestado_de_nome": lo[0][1] if lo else None})
            continue
        if loan_out:
            if not lo:
                continue
            out.append({"nome": texto(nm), "titulo": ln[0][0] if ln else None, "pais": par.get("nat"),
                        "pos": par.get("pos") or par.get("no"), "emprestado_para_titulo": lo[0][0],
                        "emprestado_para_nome": lo[0][1], "ate": data_de(other)})
            continue
        out.append({"nome": texto(nm), "titulo": ln[0][0] if ln else None, "pais": par.get("nat"),
                    "pos": par.get("pos") or par.get("no")})
    return out


# ------------------------------------------------------------------ Wikidata
def qids_por_titulo(lang, titulos):
    """título -> QID pelos sitelinks (com redirecionamentos resolvidos baixando o wikitexto)."""
    res = {}
    tits = sorted({t[0].upper() + t[1:] for t in titulos if t})
    for lote in comum.lotes(tits, 80):
        vals = " ".join(json.dumps(t, ensure_ascii=False) + f"@{lang}" for t in lote)
        q = f"""SELECT ?t ?i WHERE {{ VALUES ?t {{ {vals} }} ?a schema:name ?t ; schema:isPartOf <https://{lang}.wikipedia.org/> ;
                schema:about ?i . }}"""
        for r in comum.sparql(q):
            res[r["t"]] = comum.qid(r["i"])
    faltam = [t for t in tits if t not in res]
    redir = {}
    for t in faltam:
        x, final = raw(lang, t)
        if x is not None and final != t:
            redir[t] = final[0].upper() + final[1:]
    if redir:
        for lote in comum.lotes(sorted(set(redir.values())), 80):
            vals = " ".join(json.dumps(t, ensure_ascii=False) + f"@{lang}" for t in lote)
            q = f"""SELECT ?t ?i WHERE {{ VALUES ?t {{ {vals} }} ?a schema:name ?t ; schema:isPartOf <https://{lang}.wikipedia.org/> ;
                    schema:about ?i . }}"""
            for r in comum.sparql(q):
                for k, v in redir.items():
                    if v == r["t"]:
                        res[k] = comum.qid(r["i"])
    return res


def dados_jogadores(qids):
    out = {}
    for lote in comum.lotes(sorted(set(qids)), 150):
        vals = " ".join("wd:" + q for q in lote)
        q = f"""SELECT ?p ?nasc ?tm ?pais ?posL ?en ?pt ?ehjog WHERE {{ VALUES ?p {{ {vals} }}
          OPTIONAL {{ ?p wdt:P569 ?nasc }} OPTIONAL {{ ?p wdt:P2446 ?tm }}
          OPTIONAL {{ ?p wdt:P1532/wdt:P298 ?pais }} OPTIONAL {{ ?p wdt:P413 ?pos . ?pos rdfs:label ?posL FILTER(lang(?posL)='en') }}
          OPTIONAL {{ ?p rdfs:label ?en FILTER(lang(?en)='en') }} OPTIONAL {{ ?p rdfs:label ?pt FILTER(lang(?pt)='pt') }}
          OPTIONAL {{ ?p wdt:P106 ?ehjog FILTER(?ehjog = wd:Q937857) }} }}"""
        for r in comum.sparql(q):
            d = out.setdefault(comum.qid(r["p"]), {"nasc": None, "tm": None, "pais": [], "pos": [], "en": None, "pt": None,
                                                   "futebolista": False})
            if r.get("nasc") and not d["nasc"]:
                d["nasc"] = r["nasc"][:10]
            if r.get("tm") and not d["tm"]:
                d["tm"] = r["tm"]
            if r.get("pais") and r["pais"] not in d["pais"]:
                d["pais"].append(r["pais"])
            if r.get("posL") and r["posL"] not in d["pos"]:
                d["pos"].append(r["posL"])
            d["en"] = d["en"] or r.get("en")
            d["pt"] = d["pt"] or r.get("pt")
            d["futebolista"] = d["futebolista"] or bool(r.get("ehjog"))
        # P27 quando não há P1532 (nacionalidade esportiva)
        q2 = f"""SELECT ?p ?pais WHERE {{ VALUES ?p {{ {vals} }} ?p wdt:P27/wdt:P298 ?pais }}"""
        for r in comum.sparql(q2):
            d = out.get(comum.qid(r["p"]))
            if d is not None and not d["pais"]:
                d.setdefault("pais27", [])
                if r["pais"] not in d["pais27"]:
                    d["pais27"].append(r["pais"])
    for d in out.values():
        if not d["pais"] and d.get("pais27"):
            d["pais"] = d.pop("pais27")
        d.pop("pais27", None)
    return out


# ------------------------------------------------------------------ main
def main():
    import historia
    c = sqlite3.connect(comum.DADOS / "mundo.sqlite")
    ids = [r[0] for r in c.execute("SELECT DISTINCT clube_id FROM ligas_clubes WHERE competicao_id IN (?,?)", TEMPORADAS)]
    c.close()
    m = historia.mapa_clubes()
    inv = {v: k for k, v in m.items()}
    qs = {i: inv.get(i) for i in ids}
    vals = " ".join("wd:" + q for q in qs.values() if q)
    rs = comum.sparql("SELECT ?c ?pt ?en WHERE { VALUES ?c {" + vals + "} "
                      "OPTIONAL{?a schema:about ?c; schema:isPartOf <https://pt.wikipedia.org/>; schema:name ?pt} "
                      "OPTIONAL{?b schema:about ?c; schema:isPartOf <https://en.wikipedia.org/>; schema:name ?en} }")
    tit = {comum.qid(r["c"]): (r.get("pt"), r.get("en")) for r in rs}
    artigos, movs, elencos, sem_fonte = [], [], [], []
    for cid in ids:
        pt, en = tit.get(qs[cid], (None, None))
        achou = False
        # pt: página de temporada
        if pt:
            base = re.sub(r"\s*\(.*\)$", "", pt)
            art = "da" if re.match(r"(Associação|Sociedade|Agremiação)", pt) else "do"
            for t in [f"Temporada {art} {pt} de 2026", f"Temporada {art} {base} de 2026", f"{pt} em 2026"]:
                x, tt = raw("pt", t)
                if x:
                    mv = extrair_movimentos(x, "pt", cid, tt)
                    artigos.append({"clube": cid, "lang": "pt", "titulo": tt, "url": comum.wiki_url("pt", tt),
                                    "movimentos": len(mv)})
                    movs += mv
                    achou = True
                    break
        # en: página de temporada
        if en:
            eb = re.sub(r"\s*\(.*\)$", "", en)
            for t in EN_EXTRA.get(cid, []) + [f"2026 {en} season", f"2026 {eb} season"]:
                x, tt = raw("en", t)
                if x:
                    mv = extrair_movimentos(x, "en", cid, tt)
                    upd = re.search(r"\{\{\s*updated\s*\|([^}]*)\}\}", x, re.I)
                    d_el = data_de(upd.group(1)) if upd else None
                    d_el = d_el or ultima_edicao("en", tt)
                    el = extrair_elenco(x, "en", cid, tt, d_el)
                    artigos.append({"clube": cid, "lang": "en", "titulo": tt, "url": comum.wiki_url("en", tt),
                                    "movimentos": len(mv), "elenco": len(el), "elenco_data": d_el})
                    movs += mv
                    if el:
                        elencos.append({"clube": cid, "fonte": comum.wiki_url("en", tt), "data": d_el, "jogadores": el})
                    achou = True
                    break
            # elenco atual pela predefinição "<clube> squad" (para clubes sem elenco na página de temporada)
            if not any(e["clube"] == cid for e in elencos):
                for t in [f"Template:{en} squad", f"Template:{eb} squad"]:
                    x, tt = raw("en", t)
                    if x:
                        d_el = ultima_edicao("en", tt)
                        el = extrair_elenco(x, "en", cid, tt, d_el)
                        if el:
                            elencos.append({"clube": cid, "fonte": comum.wiki_url("en", tt), "data": d_el, "jogadores": el})
                            artigos.append({"clube": cid, "lang": "en", "titulo": tt, "url": comum.wiki_url("en", tt),
                                            "elenco": len(el), "elenco_data": d_el})
                            achou = True
                        break
        if not achou:
            sem_fonte.append(cid)
        print(f"  {cid}: {sum(1 for x in movs if x['clube'] == cid)} movimentos, "
              f"{sum(len(e['jogadores']) for e in elencos if e['clube'] == cid)} no elenco", flush=True)

    # títulos -> QID
    tit_lang = defaultdict(set)
    for x in movs:
        if x["jogador_titulo"]:
            tit_lang[x["lang"]].add(x["jogador_titulo"])
        if x["outro_titulo"]:
            tit_lang[x["lang"]].add(x["outro_titulo"])
    for e in elencos:
        for j in e["jogadores"]:
            for k in ("titulo", "emprestado_para_titulo", "emprestado_de_titulo"):
                if j.get(k):
                    tit_lang["en"].add(j[k])
    qmap = {lg: qids_por_titulo(lg, ts) for lg, ts in tit_lang.items()}

    def q_de(lg, t):
        return qmap.get(lg, {}).get(t[0].upper() + t[1:]) if t else None

    pqs = set()
    for x in movs:
        x["jogador_qid"] = q_de(x["lang"], x["jogador_titulo"])
        x["outro_qid"] = q_de(x["lang"], x["outro_titulo"])
        if x["jogador_qid"]:
            pqs.add(x["jogador_qid"])
    for e in elencos:
        for j in e["jogadores"]:
            j["qid"] = q_de("en", j.get("titulo"))
            for k in ("emprestado_para", "emprestado_de"):
                if j.get(k + "_titulo"):
                    j[k + "_qid"] = q_de("en", j[k + "_titulo"])
            if j["qid"]:
                pqs.add(j["qid"])
    wd = dados_jogadores(pqs)
    for x in movs:
        x["wd"] = wd.get(x["jogador_qid"]) if x["jogador_qid"] else None
    for e in elencos:
        for j in e["jogadores"]:
            j["wd"] = wd.get(j["qid"]) if j.get("qid") else None

    comum.salvar_json("transferencias_br_2026.json", {
        "consultado_em": comum.HOJE, "artigos": artigos, "sem_fonte": sem_fonte,
        "movimentos": movs, "elencos": elencos})
    cnt = Counter((x["direcao"], x["tipo"]) for x in movs)
    print("artigos:", len(artigos), "| movimentos:", len(movs), dict(cnt), "| elencos:", len(elencos),
          "| sem fonte:", sem_fonte)
    print("sem data:", sum(1 for x in movs if not x["data"]), "| jogador sem QID:",
          sum(1 for x in movs if not x["jogador_qid"]))


if __name__ == "__main__":
    main()
