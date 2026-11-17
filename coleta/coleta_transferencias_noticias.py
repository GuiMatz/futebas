"""Transferências brasileiras 2026 pela imprensa (confiabilidade 3).

Fecha a lacuna das páginas de temporada da Wikipedia: contratações sem link/QID ("sem dados reais
para criar") e jogadores de elenco que não estão no banco. Cada registro foi conferido em notícia
de site oficial de clube ou da imprensa esportiva (ge, UOL, ESPN, Lance!, jornais regionais) e,
quando há, no perfil do Transfermarkt.

Uso (rodar com -I: lê páginas baixadas):
    .venv\\Scripts\\python.exe -I coleta_transferencias_noticias.py ler <url> [<url>...]   # baixa (cache) e mostra o texto
    .venv\\Scripts\\python.exe -I coleta_transferencias_noticias.py                         # baixa as fontes da lista e grava o JSON

Downloads: coleta/cache/noticias/<domínio>/ (1 req/s por domínio, robots.txt, User-Agent genérico).
Saída: intermediario/transferencias_br_noticias_2026.json, lido pelo montar_banco.py (`transferencias_noticias`).
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))   # -I não põe a pasta do script no sys.path
import comum  # noqa: E402

comum.CACHE = comum.CACHE / "noticias"      # _chave() lê comum.CACHE na hora: downloads isolados
comum.CACHE.mkdir(parents=True, exist_ok=True)

SAIDA = "transferencias_br_noticias_2026.json"
DADOS = Path(__file__).resolve().parent / "noticias_transferencias_br_2026.json"   # lista curada (entrada)
# Passes livres de 2026 (jogadores que o TM deixou "sem clube" em 01/07/2026), conferidos na imprensa em 13/11/2026.
# origem "sem_clube": casa com o registro sem clube do banco (tm_id; senão nome único entre os sem clube).
# status: confirmado (aplica) | livre (segue sem clube, nada muda) | nao_confirmado | destino_fora_do_banco.
# tipo "aposentou" = jogador sai do banco (mesma regra do TM e da Wikipedia).
DADOS_LIVRES = Path(__file__).resolve().parent / "noticias_livres_2026.json"


def texto(html: str) -> str:
    from bs4 import BeautifulSoup
    s = BeautifulSoup(html, "html.parser")
    partes = []
    if s.title:
        partes.append("TITLE: " + s.title.get_text(" ", strip=True))
    for m in s.find_all("meta"):
        k = (m.get("property") or m.get("name") or "").lower()
        if k in ("og:title", "og:description", "description", "article:published_time", "date"):
            partes.append(f"META {k}: {m.get('content')}")
    for sc in s.find_all("script", type="application/ld+json"):
        t = sc.get_text()
        for k in ("datePublished", "headline"):
            mm = re.search(rf'"{k}"\s*:\s*"([^"]+)"', t)
            if mm:
                partes.append(f"LD {k}: {mm.group(1)}")
    for tag in s(["script", "style", "nav", "footer", "header", "aside"]):
        tag.decompose()
    for p in s.find_all(["p", "h1", "h2", "li", "td", "th", "span"]):
        t = p.get_text(" ", strip=True)
        if 25 <= len(t) <= 1500:
            partes.append(t)
    vistos, out = set(), []
    for p in partes:
        if p not in vistos:
            vistos.add(p)
            out.append(p)
    return "\n".join(out)


def ler(url: str, tentativas: int = 5) -> str | None:
    html = comum.baixar(url, tentativas=tentativas)
    return texto(html) if html else None


TM = "https://www.transfermarkt.com.br"


def tm_busca(q: str) -> list[dict]:
    """Busca rápida do Transfermarkt: jogadores (nome, id, posição, clube, idade, nacionalidades)."""
    import urllib.parse
    from bs4 import BeautifulSoup
    h = comum.baixar(f"{TM}/schnellsuche/ergebnis/schnellsuche?query={urllib.parse.quote_plus(q)}", intervalo=4, tentativas=1)
    if not h:
        return []
    out = []
    for tr in BeautifulSoup(h, "html.parser").select("table.items > tbody > tr"):
        a = tr.select_one('a[href*="/profil/spieler/"]')
        if not a:
            continue
        tds = [td.get_text(" ", strip=True) for td in tr.find_all("td", recursive=False)]
        out.append({"tm": int(a["href"].rsplit("/", 1)[1]), "nome": a.get_text(" ", strip=True), "linha": tds[:6],
                    "nac": [i.get("title") for i in tr.select("img.flaggenrahmen")]})
    return out


def tm_perfil(tid: int) -> dict:
    """Perfil do Transfermarkt: nome completo, nascimento, posição, nacionalidade, clube, empréstimo."""
    from bs4 import BeautifulSoup
    url = f"{TM}/x/profil/spieler/{tid}"
    h = comum.baixar(url, intervalo=4, tentativas=1)
    if not h:
        return {}
    s = BeautifulSoup(h, "html.parser")
    d = {"tm": tid, "url": url}
    h1 = s.select_one("h1")
    d["nome_pagina"] = h1.get_text(" ", strip=True) if h1 else None
    lab = None
    for sp in s.select("span.info-table__content"):
        t = sp.get_text(" ", strip=True)
        if "info-table__content--regular" in sp.get("class", []):
            lab = t.rstrip(":")
        elif lab:
            d[lab] = t
            lab = None
    m = re.search(r'"birthDate"\s*:\s*"([^"]+)"', h)
    if m:
        d["birthDate"] = m.group(1)
    nasc = d.get("Nasc./Idade") or d.get("Data de nascimento/idade") or ""
    mm = re.search(r"(\d\d)/(\d\d)/(\d{4})", nasc)
    if mm:
        d["nascimento"] = f"{mm.group(3)}-{mm.group(2)}-{mm.group(1)}"
    return d


def principal():
    from comum import norm
    itens = json.loads(DADOS.read_text("utf-8"))
    if DADOS_LIVRES.exists():
        itens += json.loads(DADOS_LIVRES.read_text("utf-8"))
    out, faltou = [], []
    for it in itens:
        ok = []
        for u in it.get("fontes", []):
            if it.get("lista") == "livres_2026" and "wikipedia.org" in u:
                continue          # Wikipedia é só apoio; não baixa
            t = ler(u, 2)
            nomes = [it.get("nome_conhecido"), it.get("nome")] + it.get("nomes_na_fonte", [])
            achou = t is not None and any(n and norm(n) in norm(t) for n in nomes)
            ok.append({"url": u, "baixado": t is not None, "cita_jogador": achou})
        it["fontes_conferidas"] = ok
        if not any(x["cita_jogador"] for x in ok) and it.get("status") == "confirmado":
            faltou.append(it["nome_conhecido"])
        out.append(it)
    comum.salvar_json(SAIDA, {"consultado_em": comum.HOJE, "itens": out})
    conf = sum(1 for x in out if x.get("status") == "confirmado")
    print(f"{len(out)} registros, {conf} confirmados; sem página que cite o nome: {faltou}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    if len(sys.argv) > 2 and sys.argv[1] == "tm":
        for q in sys.argv[2:]:
            print("=" * 20, q)
            for r in tm_busca(q)[:8]:
                print("  ", r)
    elif len(sys.argv) > 2 and sys.argv[1] == "tmp":
        for q in sys.argv[2:]:
            print(json.dumps(tm_perfil(int(q)), ensure_ascii=False))
    elif len(sys.argv) > 2 and sys.argv[1] == "ler":
        for u in sys.argv[2:]:
            t = ler(u)
            print("=" * 20, u)
            print(t[:2200] if t else "(não baixou / proibido)")
    else:
        principal()
