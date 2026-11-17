"""Escudos de clubes, logos de competições e troféus da Kings World Cup que ainda faltam em assets/.

O que é "faltante" vem do banco (aberto só para leitura):
- clube sem escudo: escudo_png ausente/inexistente E assets/escudos/<id>.png inexistente
  -> salva assets/escudos/<id>.png (o jogo já usa esse caminho como reserva);
- competição sem logo: logo_png ausente/inexistente E assets/competicoes/<id>.png inexistente
  -> salva assets/competicoes/<id>.png;
- troféus da Kings World Cup (clubes / nações) -> assets/kings/trofeus/kings-world-cup-*.png.

Fontes, nesta ordem: imagem da infobox do artigo na Wikipedia (escudo/logo atual), depois o
Wikidata (P154 "logo" -> arquivo do Wikimedia Commons), depois a infobox nos outros idiomas (pt/es/en).
Identificação:
- clubes: o artigo é o nome_completo do banco (pt para BRA, en para os demais), conferido no
  Wikidata (o item tem de ser clube/equipe de futebol do mesmo país). Tabela CURADO para exceções.
  Sem artigo: fora do Brasil, busca no Wikidata aceita só UM candidato do país com o mesmo nome;
  no Brasil (homônimos em vários estados) fica de fora. Na dúvida, melhor sem escudo que errado;
- competições: tabela COMPETICOES (artigo da Wikipedia conferido à mão); fotos/ilustrações de
  troféu e o logo genérico da confederação são recusados (RECUSADOS, RUIM, CONFEDERACAO);
- troféus da Kings World Cup: não há imagem na Wikipedia; vêm da página oficial do torneio.

Regras de rede (PLANO.md 6.2): User-Agent genérico sem dados pessoais, no máximo 1 requisição/s
por domínio, cache em coleta/cache/logos/ (nunca baixa duas vezes), robots.txt respeitado.
A Wikipedia proíbe /w/ (inclusive api.php) no robots.txt, então o script NÃO usa a api.php:
usa o endpoint SPARQL do Wikidata (API documentada, mesmo critério de comum.py), as páginas
/wiki/ (permitidas) e os arquivos em upload.wikimedia.org (permitidos).
SVG: usa a miniatura PNG renderizada pelo Commons (largura 330, a menor largura-padrão >= 256)
e reduz para no máximo 256 px no lado maior, preservando a transparência.

Saída: os PNGs acima + coleta/logos_faltantes.txt (relatório achados / não achados).
Não altera o banco nem o código do jogo.

Uso: python coleta/logos_faltantes.py   (COLETA_OFFLINE=1 -> só cache)
"""
from __future__ import annotations

import hashlib
import html as htmlmod
import io
import json
import os
import re
import sqlite3
import sys
import time
import unicodedata
import urllib.parse
import urllib.robotparser
from pathlib import Path

import requests
from PIL import Image

RAIZ = Path(__file__).resolve().parent.parent
CACHE = RAIZ / "coleta" / "cache" / "logos"
CACHE.mkdir(parents=True, exist_ok=True)
ESCUDOS = RAIZ / "assets" / "escudos"
COMPS = RAIZ / "assets" / "competicoes"
TROFEUS = RAIZ / "assets" / "kings" / "trofeus"
RELATORIO = RAIZ / "coleta" / "logos_faltantes.txt"
DB = RAIZ / "dados" / "mundo.sqlite"

UA = "FUTEBAS-logos/1.0"   # sem "fetch": o robots.txt da Wikipedia bloqueia o robô "Fetch" por substring
OFFLINE = os.environ.get("COLETA_OFFLINE") == "1"
LADO = 256
THUMB = 330          # largura-padrão de miniatura do Wikimedia (256 não é aceita: HTTP 400)
SPARQL = "https://query.wikidata.org/sparql"
APIS_DOCUMENTADAS = {"query.wikidata.org"}

_sess = requests.Session()
_sess.headers["User-Agent"] = UA
_ultimo: dict[str, float] = {}
_robots: dict[str, urllib.robotparser.RobotFileParser | None] = {}


# --------------------------------------------------------------------------- rede + cache
def _chave(url: str, data: dict | None) -> Path:
    base = url + ("|" + json.dumps(data, sort_keys=True) if data else "")
    h = hashlib.sha1(base.encode("utf-8")).hexdigest()
    dom = urllib.parse.urlsplit(url).netloc.replace(":", "_")
    d = CACHE / dom / h[:2]
    d.mkdir(parents=True, exist_ok=True)
    return d / h


def _espera(dom: str, intervalo: float = 1.05):
    dt = time.time() - _ultimo.get(dom, 0.0)
    if dt < intervalo:
        time.sleep(intervalo - dt)
    _ultimo[dom] = time.time()


def _permitido(url: str) -> bool:
    p = urllib.parse.urlsplit(url)
    dom = p.netloc
    if dom in APIS_DOCUMENTADAS:
        return True
    if dom not in _robots:
        robots_url = f"{p.scheme}://{dom}/robots.txt"
        arq = _chave(robots_url, None)
        txt = ""
        if arq.exists():
            txt = arq.read_text("utf-8", errors="replace")
        elif not OFFLINE:
            try:
                _espera(dom)
                r = _sess.get(robots_url, timeout=30)
                txt = r.text if r.status_code == 200 else ""
                arq.write_text(txt, "utf-8")
            except requests.RequestException:
                txt = ""
        if txt:
            rp = urllib.robotparser.RobotFileParser()
            rp.parse(txt.splitlines())
            _robots[dom] = rp
        else:
            _robots[dom] = None
    rp = _robots[dom]
    return True if rp is None else rp.can_fetch(UA, url)


def baixar(url: str, data: dict | None = None, binario: bool = False):
    """GET (POST se data) com cache em coleta/cache/logos/. None se falhar ou se proibido."""
    arq = _chave(url, data)
    if arq.exists():
        b = arq.read_bytes()
        return b if binario else b.decode("utf-8", errors="replace")
    if arq.with_suffix(".neg").exists() or OFFLINE:
        return None
    if not _permitido(url):
        print("  robots.txt proíbe:", url)
        return None
    dom = urllib.parse.urlsplit(url).netloc
    for i in range(6):
        _espera(dom, 2.0 if dom == "upload.wikimedia.org" else 1.1)
        try:
            r = _sess.post(url, data=data, timeout=90) if data is not None else _sess.get(url, timeout=90)
        except requests.RequestException as e:
            print("  erro de rede:", e)
            time.sleep(5 * (i + 1))
            continue
        if r.status_code == 200:
            arq.write_bytes(r.content)
            return r.content if binario else r.content.decode("utf-8", errors="replace")
        if r.status_code in (429, 500, 502, 503, 504):
            ra = r.headers.get("Retry-After")
            time.sleep(min(int(ra) if ra and ra.isdigit() else 0, 120) or 20 * (i + 1))
            continue
        arq.with_suffix(".neg").write_text(str(r.status_code))   # 4xx: não tenta de novo
        return None
    return None


def sparql(q: str) -> list[dict]:
    q = re.sub(r"[ \t]+", " ", q.strip())
    txt = baixar(SPARQL, data={"query": q, "format": "json"})
    if not txt:
        return []
    try:
        js = json.loads(txt)
    except json.JSONDecodeError:
        _chave(SPARQL, {"query": q, "format": "json"}).unlink(missing_ok=True)
        return []
    return [{k: v["value"] for k, v in b.items()} for b in js["results"]["bindings"]]


def norm(s: str | None) -> str:
    s = unicodedata.normalize("NFKD", s or "")
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", re.sub(r"[^a-zA-Z0-9 ]+", " ", s).lower()).strip()


# --------------------------------------------------------------------------- imagens
def url_commons(arquivo: str, repo: str = "wikipedia/commons") -> tuple[str, str]:
    """(url original, url miniatura PNG de THUMB px) de um arquivo do Commons/Wikipedia."""
    nome = arquivo.replace(" ", "_")
    md5 = hashlib.md5(nome.encode("utf-8")).hexdigest()
    q = urllib.parse.quote(nome, safe="()_.,-'!")
    orig = f"https://upload.wikimedia.org/{repo}/{md5[0]}/{md5[:2]}/{q}"
    thumb = f"https://upload.wikimedia.org/{repo}/thumb/{md5[0]}/{md5[:2]}/{q}/{THUMB}px-{q}"
    if nome.lower().endswith((".svg", ".tif", ".tiff", ".webp", ".gif")):
        thumb += ".png"
    return orig, thumb


def png_256(dados: bytes, destino: Path) -> bool:
    """Converte para PNG RGBA (transparência preservada), lado maior <= 256 px."""
    try:
        im = Image.open(io.BytesIO(dados))
        im.load()
    except Exception as e:  # noqa: BLE001
        print("  imagem inválida:", e)
        return False
    if im.mode != "RGBA":
        im = im.convert("RGBA")
    bb = im.getchannel("A").getbbox()     # corta margem totalmente transparente
    if bb:
        im = im.crop(bb)
    im.thumbnail((LADO, LADO), Image.LANCZOS)
    destino.parent.mkdir(parents=True, exist_ok=True)
    im.save(destino, "PNG", optimize=True)
    return True


def baixar_arquivo(arquivo: str, repo: str = "wikipedia/commons", largura: int | None = None) -> bytes | None:
    """Miniatura PNG (SVG ou imagem grande) ou o original (imagem pequena)."""
    orig, thumb = url_commons(arquivo, repo)
    vetor = arquivo.lower().endswith(".svg")
    if vetor or largura is None or largura > THUMB:
        d = baixar(thumb, binario=True)
        if d:
            return d
    if vetor:   # 2ª tentativa: largura-padrão 250 (miniatura menor que o SVG original)
        return baixar(thumb.replace(f"/{THUMB}px-", "/250px-"), binario=True)
    return baixar(orig, binario=True)


RUIM = re.compile(r"stadi|estadio|estádio|troph|trof[eé]|\bmaps?\b|mapa|flag|bandeira|bandera|\bkit\b|"
                  r"uniforme|jersey|team.photo|squad|celebrat|panoram|aerial|\bphoto|\bfoto|equipe|elenco", re.I)
ICONE = re.compile(r"soccerball|current event|ambox|question book|\bicon\b|symbol support|edit-clear|"
                   r"padlock|wiki letter|disambig", re.I)
CONFEDERACAO = re.compile(r"^(AFC|CAF|UEFA|CONMEBOL|CONCACAF|Concacaf|FIFA|OFC)( text)?( logo)?( \(\d+\))?\.\w+$", re.I)
# arquivos conferidos e recusados (ilustração de troféu no lugar do logo etc.)
RECUSADOS = {"Coppa del Portogallo.svg", "Coppa svizzera.png", "Finalissima.png"}
LOGO_EXPLICITO = re.compile(r"logo|crest|escudo|emblem|badge|wordmark", re.I)
BOM = re.compile(r"logo|crest|escudo|emblem|badge|bras[aã]o|shield|insignia|wappen|stemma|blason|\.svg$", re.I)


def infobox_imagem(lang: str, titulo: str, exigir_logo: bool = True) -> tuple[str, str, int | None] | None:
    """(repo, arquivo, largura original) da 1ª imagem da infobox do artigo /wiki/<titulo>."""
    h = baixar(url_artigo(lang, titulo))
    if not h:
        return None
    m = re.search(r'<table[^>]*class="[^"]*\b(infobox|infobox_v2|infobox_v3)\b', h)
    if not m:
        return None
    trecho = h[m.start(): m.start() + 60000]
    fim = trecho.find("</table>")
    for img in re.finditer(r"<img\b[^>]*>", trecho):
        if fim > 0 and img.start() > fim + 20000:
            break
        tag = img.group(0)
        src = re.search(r'src="([^"]+)"', tag)
        if not src:
            continue
        s = htmlmod.unescape(src.group(1))
        mm = re.search(r"(?:upload|thumb)\.wikimedia\.org/(wikipedia/[a-z]+)/(?:thumb/)?[0-9a-f]/[0-9a-f]{2}/([^/?]+)", s)
        if not mm:
            continue
        repo, arq = mm.group(1), urllib.parse.unquote(mm.group(2))
        w = re.search(r'data-file-width="(\d+)"', tag)
        larg = int(w.group(1)) if w else None
        chk = arq.replace("_", " ")
        if (larg is not None and larg < 60) or ICONE.search(chk):
            continue   # ícone/bandeirinha ("evento em andamento" etc.)
        if chk in RECUSADOS or (exigir_logo and CONFEDERACAO.search(chk)):
            return None   # recusado à mão / logo genérico da confederação, não da competição
        e_logo = bool(LOGO_EXPLICITO.search(chk))
        if RUIM.search(chk) and not e_logo:   # "Trophée des Champions logo.svg" é logo
            return None
        if exigir_logo and not (e_logo or BOM.search(chk) or chk.lower().endswith((".png", ".svg", ".gif"))):
            return None   # .jpg sem "logo" no nome costuma ser foto (troféu, festa do título)
        return repo, arq, larg
    return None


# --------------------------------------------------------------------------- Wikidata
def dados_item(qid: str) -> dict:
    r = sparql(f"""SELECT ?logo ?pt ?es ?en WHERE {{
      OPTIONAL {{ wd:{qid} wdt:P154 ?logo }}
      OPTIONAL {{ ?pt schema:about wd:{qid}; schema:isPartOf <https://pt.wikipedia.org/> }}
      OPTIONAL {{ ?es schema:about wd:{qid}; schema:isPartOf <https://es.wikipedia.org/> }}
      OPTIONAL {{ ?en schema:about wd:{qid}; schema:isPartOf <https://en.wikipedia.org/> }}
    }}""")
    out = {"logos": [], "pt": None, "es": None, "en": None}
    for b in r:
        if b.get("logo") and b["logo"] not in out["logos"]:
            out["logos"].append(b["logo"])
        for l in ("pt", "es", "en"):
            if b.get(l):
                out[l] = urllib.parse.unquote(b[l].split("/wiki/", 1)[1]).replace("_", " ")
    return out


def url_artigo(lang: str, titulo: str) -> str:
    return f"https://{lang}.wikipedia.org/wiki/" + urllib.parse.quote(titulo.replace(" ", "_"), safe="()_,'-.")


def qid_de_artigo(lang: str, titulo: str) -> str | None:
    """QID do artigo, lido do próprio HTML (wgWikibaseItemId; segue redirecionamentos)."""
    h = baixar(url_artigo(lang, titulo)) or ""
    m = re.search(r'"wgWikibaseItemId":"(Q\d+)"', h)
    return m.group(1) if m else None


def obter_logo(qid: str | None, artigos: list[tuple[str, str]], destino: Path, exigir_logo=True) -> str | None:
    """Infobox dos artigos conhecidos -> P154 (logo) do Wikidata -> infobox dos outros idiomas.
    A infobox vem primeiro porque mostra o escudo/logo atual; o P154 pode ter versões antigas."""
    def infobox(lang, tit):
        ib = infobox_imagem(lang, tit, exigir_logo)
        if ib:
            repo, arq, larg = ib
            dados = baixar_arquivo(arq, repo, larg)
            if dados and png_256(dados, destino):
                return f"{lang}.wikipedia:{tit} -> {repo}:{arq}"
        return None
    for lang, tit in artigos:
        f = infobox(lang, tit)
        if f:
            return f
    if not qid:
        return None
    d = dados_item(qid)
    for logo in d["logos"][:3]:
        arq = urllib.parse.unquote(logo.rsplit("/", 1)[1])
        if RUIM.search(arq) or arq in RECUSADOS or (exigir_logo and CONFEDERACAO.search(arq)):
            continue   # foto de troféu/estádio cadastrada como "logo", ou logo da confederação
        dados = baixar_arquivo(arq)
        if dados and png_256(dados, destino):
            return f"wikidata:{qid} P154 -> commons:{arq}"
    for lang in ("pt", "es", "en"):
        if d[lang] and (lang, d[lang]) not in artigos:
            f = infobox(lang, d[lang])
            if f:
                return f
    return None


# --------------------------------------------------------------------------- tabelas curadas
# clube -> (idioma, artigo) conferido à mão, quando o nome_completo do banco não serve. None = pular.
CURADO: dict[str, tuple[str, str] | None] = {
}

# competição -> artigos (lang, título) na ordem de preferência. None = não identificada.
COMPETICOES: dict[str, list[tuple[str, str]] | None] = {
    "col-1": [("en", "Categoría Primera A"), ("es", "Categoría Primera A")],
    "arg-copa": [("en", "Copa Argentina"), ("es", "Copa Argentina")],
    "bol-copa": [("es", "Copa Bolivia"), ("en", "Copa Bolivia")],
    "chl-copa": [("en", "Copa Chile"), ("es", "Copa Chile")],
    "chl-copa-liga": [("es", "Copa de la Liga (Chile)"), ("en", "Copa de la Liga (Chile)")],
    "col-copa": [("en", "Copa Colombia"), ("es", "Copa Colombia")],
    "usa-copa": [("en", "U.S. Open Cup")],
    "conmebol-recopa": [("en", "Recopa Sudamericana"), ("es", "Recopa Sudamericana")],
    "concacaf-champions": [("en", "CONCACAF Champions Cup")],
    "concacaf-leagues-cup": [("en", "Leagues Cup")],
    "bra-supercopa": [("pt", "Supercopa do Brasil"), ("en", "Supercopa do Brasil")],
    "bra-copa-nordeste": [("pt", "Copa do Nordeste"), ("en", "Copa do Nordeste")],
    "bra-copa-verde": [("pt", "Copa Verde"), ("en", "Copa Verde")],
    "bra-copa-sul-sudeste": [("pt", "Copa Sul-Sudeste"), ("en", "Copa Sul-Sudeste")],
    "uefa-supercopa": [("en", "UEFA Super Cup")],
    "afc-champions-elite": [("en", "AFC Champions League Elite")],
    "afc-champions-2": [("en", "AFC Champions League Two")],
    "caf-champions": [("en", "CAF Champions League")],
    "caf-confederacoes": [("en", "CAF Confederation Cup")],
    "fifa-mundial-clubes": [("en", "FIFA Club World Cup")],
    "fifa-intercontinental": [("en", "FIFA Intercontinental Cup")],
    "fifa-copa-do-mundo": [("en", "FIFA World Cup")],
    "eurocopa": [("en", "UEFA European Championship")],
    "uefa-nations-league": [("en", "UEFA Nations League")],
    "concacaf-nations-league": [("en", "CONCACAF Nations League")],
    "copa-america": [("en", "Copa América")],
    "caf-copa-africa": [("en", "Africa Cup of Nations")],
    "afc-copa-asia": [("en", "AFC Asian Cup")],
    "finalissima": [("en", "CONMEBOL–UEFA Cup of Champions")],
    "eliminatorias-concacaf": [("en", "2030 FIFA World Cup qualification (CONCACAF)")],
    "eliminatorias-uefa": [("en", "2030 FIFA World Cup qualification (UEFA)")],
    "eng-copa": [("en", "FA Cup")],
    "eng-copa-liga": [("en", "EFL Cup")],
    "eng-supercopa": [("en", "FA Community Shield")],
    "esp-copa": [("en", "Copa del Rey"), ("es", "Copa del Rey")],
    "esp-supercopa": [("en", "Supercopa de España"), ("es", "Supercopa de España")],
    "ita-copa": [("en", "Coppa Italia")],
    "ita-supercopa": [("en", "Supercoppa Italiana")],
    "deu-copa": [("en", "DFB-Pokal")],
    "deu-supercopa": [("en", "DFL-Supercup")],
    "fra-copa": [("en", "Coupe de France")],
    "fra-supercopa": [("en", "Trophée des Champions")],
    "prt-copa": [("en", "Taça de Portugal"), ("pt", "Taça de Portugal")],
    "prt-copa-liga": [("en", "Taça da Liga"), ("pt", "Taça da Liga")],
    "prt-supercopa": [("en", "Supertaça Cândido de Oliveira"), ("pt", "Supertaça Cândido de Oliveira")],
    "nld-copa": [("en", "KNVB Cup")],
    "bel-copa": [("en", "Belgian Cup")],
    "sco-copa": [("en", "Scottish Cup")],
    "tur-copa": [("en", "Turkish Cup")],
    "grc-copa": [("en", "Greek Football Cup")],
    "rus-copa": [("en", "Russian Cup (football)")],
    "ukr-copa": [("en", "Ukrainian Cup")],
    "dnk-copa": [("en", "Danish Cup")],
    "che-copa": [("en", "Swiss Cup")],
    "aut-copa": [("en", "Austrian Cup")],
    "sau-copa": [("en", "King's Cup (Saudi Arabia)")],
    "jpn-copa": [("en", "Emperor's Cup")],
    # "Copa Intercontinental" do banco = Copa Intercontinental da FIFA (mesmo logo)
    "intercontinental": [("en", "FIFA Intercontinental Cup")],
}

# observações da conferência visual, impressas no relatório
NOTAS = {
    "candango": "escudo antigo (CFZ de Brasília, nome anterior do clube); a Wikipedia não tem o atual",
    "jong-genk": "time B: usa o escudo do KRC Genk (o banco aponta para o artigo do clube principal)",
    "jong-kaa-gent": "time B: usa o escudo do KAA Gent (o banco aponta para o artigo do clube principal)",
    "eliminatorias-concacaf": "não há logo próprio das eliminatórias; usa a marca da Copa do Mundo da FIFA",
    "eliminatorias-uefa": "não há logo próprio das eliminatórias; usa a marca da Copa do Mundo da FIFA",
    "intercontinental": "tratada como a Copa Intercontinental da FIFA (mesmo logo de fifa-intercontinental)",
    "delhi": "SC Delhi é o antigo Hyderabad FC (mesmo item no Wikidata)",
    "durban-city": "Durban City (2024) é o antigo Maritzburg United (mesmo item no Wikidata)",
}

# troféus da Kings World Cup: a Wikipedia não tem imagem deles; vêm do site oficial (kingsleague.pro,
# robots.txt permite), da página do torneio: (página, regex da imagem do troféu, prefixo da URL final).
KINGS_TROFEUS = {
    "kings-world-cup-clubs": ("https://kingsleague.pro/en/world-cup",
                              r"/img/brackets/(trophy-kwc[\w-]*\.png)", "https://kingsleague.pro/img/brackets/"),
    "kings-world-cup-nations": ("https://kingsleague.pro/en/kwc-nations",
                                r"cms-es/(\d{4}/\d{2}/kwc-nations-trophy[\w-]*\.png)",
                                "https://cms-es.kingsleague.pro/wp-content/uploads/"),
}


# --------------------------------------------------------------------------- busca automática (clube fora de CURADO)
def pais_qids(iso3: str) -> set[str]:
    """Itens do Wikidata com esse código ISO alfa-3 (pode haver mais de um: Países Baixos/Reino...)."""
    esp = {"ENG": {"Q145", "Q21"}, "SCO": {"Q145", "Q22"}, "WAL": {"Q145", "Q25"}, "NIR": {"Q145", "Q26"},
           "NLD": {"Q55", "Q29999"}, "DNK": {"Q35", "Q756617"}}   # país constituinte x reino
    if iso3 in esp:
        return esp[iso3]
    return {b["c"].rsplit("/", 1)[1] for b in sparql(f'SELECT ?c WHERE {{ ?c wdt:P298 "{iso3}" }}')}


def busca_auto(nome: str, pais: str) -> str | None:
    cqs = pais_qids(pais)
    if not cqs:
        return None
    filtro = "haswbstatement:" + "|".join(f"P17={c}" for c in sorted(cqs))
    termo = re.sub(r"-[A-Z]{2}$", "", nome).replace('"', "")
    r = sparql(f"""SELECT DISTINCT ?item ?l WHERE {{
      SERVICE wikibase:mwapi {{ bd:serviceParam wikibase:endpoint "www.wikidata.org"; wikibase:api "Search";
        mwapi:srsearch "{termo} {filtro}"; mwapi:srlimit "25".
        ?item wikibase:apiOutputItem mwapi:title. }}
      ?item wdt:P31/wdt:P279* wd:Q476028 .
      ?item rdfs:label|skos:altLabel ?l .
      FILTER(LANG(?l) IN ("pt","en","es"))
    }}""")
    alvo = norm(termo)
    achados = {b["item"].rsplit("/", 1)[1] for b in r
               if re.sub(r"\b(fc|sc|cf|club|clube|esporte|futebol|football|soccer)\b", "", norm(b["l"])).strip()
               == re.sub(r"\b(fc|sc|cf|club|clube|esporte|futebol|football|soccer)\b", "", alvo).strip()}
    return achados.pop() if len(achados) == 1 else None


CLUBE_TIPOS = "wd:Q476028 wd:Q15944511 wd:Q12973014 wd:Q847017"   # clube de futebol / equipe / time / clube


def confere_item(qid: str, pais: str) -> bool:
    """Item é clube/equipe de futebol e o país (P17) bate com o do banco (ou não está cadastrado)."""
    r = sparql(f"""SELECT ?tipo ?pais WHERE {{
      OPTIONAL {{ wd:{qid} wdt:P31/wdt:P279* ?tipo . VALUES ?tipo {{ {CLUBE_TIPOS} }} }}
      OPTIONAL {{ wd:{qid} wdt:P17 ?pais }} }}""")
    tipos = {b["tipo"] for b in r if b.get("tipo")}
    paises = {b["pais"].rsplit("/", 1)[1] for b in r if b.get("pais")}
    return bool(tipos) and (not paises or bool(paises & pais_qids(pais)))


def identificar_clube(cid, nome, nome_completo, pais):
    """(qid, artigos, observação). Ordem: CURADO -> artigo nome_completo do banco -> busca única."""
    if cid in CURADO:
        v = CURADO[cid]
        if v is None:
            return None, [], "sem item confiável (curadoria)"
        lang, tit = v
        return qid_de_artigo(lang, tit), [(lang, tit)], "curadoria"
    tit = nome_completo or ""
    if tit and "redlink" not in tit and "action=edit" not in tit:
        for lang in (("pt", "en") if pais == "BRA" else ("en", "pt")):
            q = qid_de_artigo(lang, tit)
            if q and confere_item(q, pais):
                return q, [(lang, tit)], ""
    q = busca_auto(nome, pais) if pais != "BRA" else None   # BRA: homônimos em outros estados
    if q and confere_item(q, pais):
        return q, [], "busca no Wikidata (candidato único)"
    return None, [], "sem artigo na Wikipedia"


# --------------------------------------------------------------------------- principal
def existe(caminho: str | None) -> bool:
    return bool(caminho) and (RAIZ / caminho).exists()


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):
        pass
    con = sqlite3.connect(f"file:{DB.as_posix()}?mode=ro", uri=True)
    clubes = [r for r in con.execute("SELECT id, nome, nome_completo, pais, uf, escudo_png FROM clubes "
                                     "ORDER BY pais, id")
              if not existe(r[5]) and not (ESCUDOS / f"{r[0]}.png").exists()]
    comps = [r for r in con.execute("SELECT id, nome, logo_png FROM competicoes ORDER BY id")
             if not existe(r[2]) and not (COMPS / f"{r[0]}.png").exists()]
    con.close()
    rel = {"clubes": [], "competicoes": [], "kings": []}

    print(f"clubes sem escudo: {len(clubes)}", flush=True)
    for cid, nome, nome_completo, pais, uf, _ in clubes:
        qid, artigos, obs = identificar_clube(cid, nome, nome_completo, pais)
        fonte = obter_logo(qid, artigos, ESCUDOS / f"{cid}.png", exigir_logo=False) if (qid or artigos) else None
        rotulo = f"{nome} ({pais}{'/' + uf if uf else ''})" + (f" - {obs}" if obs else "")
        rel["clubes"].append((cid, rotulo, qid, fonte))
        print(f"  {cid}: {qid or '-'} {'OK' if fonte else 'nao achado'} {obs}", flush=True)

    print(f"competições sem logo: {len(comps)}", flush=True)
    for kid, nome, _ in comps:
        arts = COMPETICOES.get(kid)
        fonte = qid = None
        if arts:
            qid = qid_de_artigo(*arts[0])
            fonte = obter_logo(qid, arts, COMPS / f"{kid}.png")
        rel["competicoes"].append((kid, nome, qid, fonte))
        print(f"  {kid}: {qid or '-'} {'OK' if fonte else 'nao achado'}", flush=True)

    for tid, (pagina, rx, prefixo) in KINGS_TROFEUS.items():
        dest = TROFEUS / f"{tid}.png"
        fonte = None
        if dest.exists():
            print(f"  {tid}: já existe", flush=True)
            continue
        m = re.search(rx, baixar(pagina) or "")
        if m:
            url = prefixo + m.group(1)
            d = baixar(url, binario=True)
            if d and png_256(d, dest):
                fonte = f"{pagina} -> {url}"
        rel["kings"].append((tid, "troféu " + tid.replace("kings-", "Kings ").replace("-", " "), None, fonte))
        print(f"  {tid}: {'OK' if fonte else 'nao achado'}", flush=True)

    # relatório acumulado entre execuções (o que já foi baixado antes não volta a ser processado)
    hist_arq = CACHE / "resultado.json"
    hist = json.loads(hist_arq.read_text("utf-8")) if hist_arq.exists() else {}
    destino_de = {"clubes": ESCUDOS, "competicoes": COMPS, "kings": TROFEUS}
    for cat in rel:
        h = hist.setdefault(cat, {})
        for i in rel[cat]:
            h[i[0]] = list(i)
        for k in list(h):   # achado antes mas o arquivo sumiu: vale o resultado desta execução
            if h[k][3] and not (destino_de[cat] / f"{k}.png").exists():
                del h[k]
    hist_arq.write_text(json.dumps(hist, ensure_ascii=False, indent=1), "utf-8")
    rel = {cat: sorted(map(tuple, hist.get(cat, {}).values())) for cat in rel}

    linhas = [f"Relatório de logos faltantes - {time.strftime('%Y-%m-%d %H:%M')}",
              "Fontes: infobox da Wikipedia (pt, es, en) / Wikidata P154 -> Wikimedia Commons;",
              "troféus da Kings World Cup: site oficial kingsleague.pro.", ""]
    for cat, titulo in (("clubes", "ESCUDOS DE CLUBES (assets/escudos/<id>.png)"),
                        ("competicoes", "LOGOS DE COMPETIÇÕES (assets/competicoes/<id>.png)"),
                        ("kings", "TROFÉUS KINGS WORLD CUP (assets/kings/trofeus/<id>.png)")):
        itens = rel[cat]
        ok = [i for i in itens if i[3]]
        nok = [i for i in itens if not i[3]]
        linhas += [f"== {titulo}: {len(ok)} achados, {len(nok)} não achados", "", "-- achados:"]
        linhas += [f"  {i[0]:28s} {i[1]}  [{i[2] or ''}]  <- {i[3]}"
                   + (f"\n  {'':28s} obs.: {NOTAS[i[0]]}" if i[0] in NOTAS else "") for i in ok]
        linhas += ["", "-- não achados:"]
        linhas += [f"  {i[0]:28s} {i[1]}  [{i[2] or 'sem item confiável'}]" for i in nok]
        linhas.append("")
    RELATORIO.write_text("\n".join(linhas), "utf-8")
    for cat in rel:
        print(cat, sum(1 for i in rel[cat] if i[3]), "/", len(rel[cat]))


if __name__ == "__main__":
    sys.exit(main())
