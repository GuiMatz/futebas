"""Utilidades comuns da coleta: HTTP com cache em disco, limite de 1 req/s por domínio,
robots.txt, consultas SPARQL ao Wikidata e à API da Wikipedia.

Regras (PLANO.md 6.2):
- toda resposta baixada fica em coleta/cache/ (nunca baixa duas vezes);
- no máximo 1 requisição por segundo por domínio;
- respeita robots.txt;
- User-Agent genérico, sem dados pessoais.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import time
import unicodedata
import urllib.parse
import urllib.robotparser
from pathlib import Path

import requests

RAIZ = Path(__file__).resolve().parent.parent
COLETA = RAIZ / "coleta"
CACHE = COLETA / "cache"
INTERMED = COLETA / "intermediario"   # saídas das etapas de coleta (JSON), lidas pelo montar_banco
DADOS = RAIZ / "dados"
DOCS = RAIZ / "docs"
CACHE.mkdir(parents=True, exist_ok=True)
INTERMED.mkdir(parents=True, exist_ok=True)

UA = "FUTEBAS-coleta/0.1"
HOJE = time.strftime("%Y-%m-%d")

_sess = requests.Session()
_sess.headers["User-Agent"] = UA
_ultimo: dict[str, float] = {}
_robots: dict[str, urllib.robotparser.RobotFileParser | None] = {}
OFFLINE = os.environ.get("COLETA_OFFLINE") == "1"   # só cache, nenhuma rede


def _chave(url: str, data: dict | None) -> Path:
    base = url + ("|" + json.dumps(data, sort_keys=True) if data else "")
    h = hashlib.sha1(base.encode("utf-8")).hexdigest()
    dom = urllib.parse.urlsplit(url).netloc.replace(":", "_")
    d = CACHE / dom / h[:2]
    d.mkdir(parents=True, exist_ok=True)
    return d / h


def _espera(dom: str, intervalo: float = 1.05):
    t = _ultimo.get(dom, 0.0)
    dt = time.time() - t
    if dt < intervalo:
        time.sleep(intervalo - dt)
    _ultimo[dom] = time.time()


# APIs documentadas cujo uso programático é permitido pela política de uso da Wikimedia
# (User-Agent identificado, requisições em série, sem paralelismo). O robots.txt desses
# endpoints existe para impedir indexação por buscadores, não uso de API. Só o endpoint
# SPARQL do Wikidata entra aqui; páginas da Wikipedia/Commons seguem o robots.txt (/wiki/).
APIS_DOCUMENTADAS = {"query.wikidata.org"}


def _permitido(url: str) -> bool:
    p = urllib.parse.urlsplit(url)
    dom = p.netloc
    if dom in APIS_DOCUMENTADAS:
        return True
    if dom not in _robots:
        rp = urllib.robotparser.RobotFileParser()
        robots_url = f"{p.scheme}://{dom}/robots.txt"
        cache = _chave(robots_url, None)
        txt = None
        if cache.exists():
            txt = cache.read_text("utf-8", errors="replace")
        elif not OFFLINE:
            try:
                _espera(dom)
                r = _sess.get(robots_url, timeout=30)
                txt = r.text if r.status_code == 200 else ""
                cache.write_text(txt, "utf-8")
            except requests.RequestException:
                txt = ""
        if txt:
            rp.parse(txt.splitlines())
            _robots[dom] = rp
        else:
            _robots[dom] = None
    rp = _robots[dom]
    return True if rp is None else rp.can_fetch(UA, url)


def baixar(url: str, params: dict | None = None, data: dict | None = None,
           binario: bool = False, tentativas: int = 5, intervalo: float = 1.05):
    """GET (ou POST se data) com cache. Retorna texto (ou bytes) ou None se falhar/proibido."""
    if params:
        url = url + ("&" if "?" in url else "?") + urllib.parse.urlencode(params)
    arq = _chave(url, data)
    if arq.exists():
        b = arq.read_bytes()
        return b if binario else b.decode("utf-8", errors="replace")
    neg = arq.with_suffix(".404")
    if neg.exists():
        return None
    if OFFLINE:
        return None
    if not _permitido(url):
        print("  robots.txt proíbe:", url)
        return None
    dom = urllib.parse.urlsplit(url).netloc
    for i in range(tentativas):
        _espera(dom, intervalo)
        try:
            if data is not None:
                r = _sess.post(url, data=data, timeout=120)
            else:
                r = _sess.get(url, timeout=120)
        except requests.RequestException as e:
            print("  erro de rede:", e, "tentativa", i + 1)
            time.sleep(5 * (i + 1))
            continue
        if r.status_code == 200:
            arq.write_bytes(r.content)
            return r.content if binario else r.content.decode("utf-8", errors="replace")
        if r.status_code in (429, 500, 502, 503, 504):
            ra = r.headers.get("Retry-After")
            pausa = int(ra) if ra and ra.isdigit() else 10 * (i + 1)
            print(f"  HTTP {r.status_code}, aguardando {pausa}s")
            time.sleep(min(pausa, 120))
            continue
        if r.status_code in (404, 410):
            neg.write_text(str(r.status_code))   # cache negativo: não tenta de novo
        else:
            print(f"  HTTP {r.status_code} em {url[:150]}")
        return None
    return None


SPARQL = "https://query.wikidata.org/sparql"


def sparql(q: str) -> list[dict]:
    """Executa SPARQL no Wikidata (POST, com cache). Retorna lista de dicts {var: valor}."""
    q = re.sub(r"[ \t]+", " ", q.strip())
    txt = baixar(SPARQL, data={"query": q, "format": "json"}, intervalo=1.2, tentativas=2)
    if txt is None:
        raise RuntimeError("SPARQL falhou: " + q[:300])
    try:
        js = json.loads(txt)
    except json.JSONDecodeError:
        # resposta truncada/timeout gravada? apaga para refazer depois
        _chave(SPARQL, {"query": q, "format": "json"}).unlink(missing_ok=True)
        raise RuntimeError("SPARQL devolveu algo que não é JSON (timeout?): " + q[:200])
    out = []
    for b in js["results"]["bindings"]:
        out.append({k: v["value"] for k, v in b.items()})
    return out


def qid(uri: str | None) -> str | None:
    if not uri:
        return None
    m = re.search(r"(Q\d+)$", uri)
    return m.group(1) if m else None


def wiki_url(lang: str, titulo: str) -> str:
    return f"https://{lang}.wikipedia.org/wiki/" + urllib.parse.quote(titulo.replace(" ", "_"), safe="")


def wiki_html(lang: str, titulo: str) -> str | None:
    """HTML do artigo (caminho /wiki/, permitido pelo robots.txt)."""
    return baixar(wiki_url(lang, titulo))


def norm(s: str | None) -> str:
    """Nome normalizado: sem acento, minúsculo, só letras/números e espaço."""
    if not s:
        return ""
    s = unicodedata.normalize("NFKD", s)
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = re.sub(r"[^a-zA-Z0-9 ]+", " ", s).lower()
    return re.sub(r"\s+", " ", s).strip()


def slug(s: str) -> str:
    return norm(s).replace(" ", "-")


def salvar_json(nome: str, obj):
    (INTERMED / nome).write_text(json.dumps(obj, ensure_ascii=False, indent=1), "utf-8")


def ler_json(nome: str, padrao=None):
    p = INTERMED / nome
    if not p.exists():
        return padrao
    return json.loads(p.read_text("utf-8"))


def lotes(seq, n):
    seq = list(seq)
    for i in range(0, len(seq), n):
        yield seq[i:i + n]
