"""Normalização final do banco (chamada por montar_banco.py; também roda sozinho sobre um banco pronto).

1. Texto: espaços (inclusive tab) nas pontas e duplicados, sufixo de link vermelho da Wikipedia
   ('?action=edit&redlink=1'), mojibake UTF-8 lido como cp1252/latin-1 ('AtlÃ©tico' -> 'Atlético') e
   maiúscula dupla no começo de palavra ('Atlético MIneiro' -> 'Atlético Mineiro'; 'IJ' holandês fica).
   `limpar_nome()` é usado na leitura das fontes (mod, Wikipedia) e `limpar_banco()` passa em todas as
   colunas de nome no fim da montagem.
2. Kings: nomes com '?' no lugar de letra acentuada (o FUT.AE gravou assim: '?lex Guti') são reparados
   pelos nomes do site oficial (cache de kingsleague.pro e coleta/intermediario/kings_fotos.json).
3. Competições: ids duplicados (`intercontinental` = `fifa-intercontinental`) viram um só, com o
   histórico de campeões levado para o id mantido.
4. Formatos (`normalizar_formatos`): toda divisão nacional tem formato válido em 2026; a cadeia de
   divisões de cada país fica coerente (rebaixados da N = promovidos da N+1); e as fases das copas são
   conferidas contra o que o motor sabe rodar (jogo/src/main/java/br/futebas/competicao/Torneio.java):
   pontos_corridos, grupos (+ triangular/quadrangular/hexagonal/octogonal), mata_mata, liga_suica e
   'especial' com `fallback` de um desses; copa nacional com grupo de mais de 8 vira mata-mata no motor.

Uso sozinho: python normalizar.py [banco.sqlite]   (só confere e mostra; --gravar aplica)
"""
from __future__ import annotations

import html
import json
import re
import sqlite3
import sys
from collections import Counter, defaultdict
from pathlib import Path

import comum

# ---------------------------------------------------------------------------------------------- texto
RE_ESPACOS = re.compile(r"\s+")
RE_REDLINK = re.compile(r"\?action=edit(&amp;|&)redlink=1$")
RE_MAIUSC = re.compile(r"\b([A-Z])([A-Z])([a-zß-öø-ÿ]{2,})")
MAIUSC_OK = {"IJ"}                      # holandês: IJsselmeervogels, IJzerman
NOMES_FIXOS = {"Shonam Bellmare": "Shonan Bellmare"}   # erros de digitação do mod achados na revisão
RE_MOJIBAKE = re.compile(r"[ÃÂ][\u0080-¿‘-›ŒœŠšŸŽžˆ˜]|â€")


def consertar_mojibake(s: str) -> str:
    """'AtlÃ©tico' -> 'Atlético' (UTF-8 decodificado como cp1252/latin-1); devolve s se não for o caso."""
    if not RE_MOJIBAKE.search(s):
        return s
    for cod in ("cp1252", "latin-1"):
        try:
            t = s.encode(cod).decode("utf-8")
        except (UnicodeEncodeError, UnicodeDecodeError):
            continue
        if not RE_MOJIBAKE.search(t):
            return t
    return s


def _maiusc(m: re.Match) -> str:
    a, b, resto = m.groups()
    if a + b in MAIUSC_OK or a == b:
        return m.group(0)
    return a + b.lower() + resto


def limpar_nome(s, maiusculas: bool = True):
    """Nome de clube/jogador/estádio/competição pronto para a tela (não muda nada além do descrito acima)."""
    if not isinstance(s, str):
        return s
    t = consertar_mojibake(s)
    t = RE_REDLINK.sub("", t)
    t = RE_ESPACOS.sub(" ", t).strip()
    if maiusculas:
        t = RE_MAIUSC.sub(_maiusc, t)
    return NOMES_FIXOS.get(t, t)


# (tabela, coluna, conserta maiúscula dupla?) — apelidos de streamer da Kings ('RDjavi') ficam como estão
COLUNAS_TEXTO = [
    ("paises", "nome", True), ("clubes", "nome", True), ("clubes", "nome_completo", True),
    ("clubes", "cidade", True), ("clubes", "estadio", True), ("jogadores", "nome", True),
    ("jogadores", "nome_conhecido", True), ("tecnicos", "nome", True), ("competicoes", "nome", True),
    ("selecoes", "tecnico", True), ("carreira", "clube_nome", True), ("lendas", "nome", True),
    ("lendas", "nome_conhecido", True), ("campeoes_historicos", "campeao_nome", True),
    ("campeoes_historicos", "vice_nome", True), ("kings_ligas", "nome", False), ("kings_times", "nome", False),
    ("kings_times", "presidente", False), ("kings_elencos", "nome_fonte", False),
]


def limpar_banco(c: sqlite3.Connection) -> dict:
    """Aplica limpar_nome em todas as colunas de COLUNAS_TEXTO. Retorna {tabela.coluna: nº de linhas mudadas}."""
    tabelas = {r[0] for r in c.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    mud = {}
    for t, col, mai in COLUNAS_TEXTO:
        if t not in tabelas or col not in {r[1] for r in c.execute(f"PRAGMA table_info({t})")}:
            continue
        troca = []
        for rid, v in c.execute(f"SELECT rowid, {col} FROM {t} WHERE {col} IS NOT NULL"):
            n = limpar_nome(v, mai)
            if n != v:
                troca.append((n, rid))
        if troca:
            c.executemany(f"UPDATE {t} SET {col}=? WHERE rowid=?", troca)
            mud[f"{t}.{col}"] = len(troca)
    if "nomes_gerados" in tabelas:   # (pais, tipo, nome) repetidos depois da limpeza: soma as frequências
        linhas = Counter()
        for p, tp, n, fq in c.execute("SELECT pais, tipo, nome, freq FROM nomes_gerados"):
            linhas[(p, tp, limpar_nome(n))] += fq or 1
        antes = c.execute("SELECT COUNT(*) FROM nomes_gerados").fetchone()[0]
        c.execute("DELETE FROM nomes_gerados")
        c.executemany("INSERT INTO nomes_gerados VALUES (?,?,?,?)", [(*k, v) for k, v in sorted(linhas.items(), key=lambda kv: tuple(str(x) for x in kv[0]))])
        if len(linhas) != antes:
            mud["nomes_gerados (fundidos)"] = antes - len(linhas)
    return mud


def problemas_texto(c: sqlite3.Connection) -> dict:
    """Contagem do que ainda sobra (para o relatório): '?' entre letras, U+FFFD, mojibake, espaços, maiúscula dupla."""
    pats = {"interrogacao": re.compile(r"\w\?|\?\w"), "u+fffd": re.compile("�"), "mojibake": RE_MOJIBAKE,
            "espacos": re.compile(r"^\s|\s$|\s{2}"), "maiuscula_dupla": RE_MAIUSC}
    out = Counter()
    for t, col, mai in COLUNAS_TEXTO:
        try:
            vals = [r[0] for r in c.execute(f"SELECT {col} FROM {t} WHERE {col} IS NOT NULL")]
        except sqlite3.OperationalError:
            continue
        for v in vals:
            if not isinstance(v, str):
                continue
            for k, p in pats.items():
                if k == "maiuscula_dupla":
                    if mai and any(_maiusc(m) != m.group(0) for m in p.finditer(v)):
                        out[f"{t}.{col}: {k}"] += 1
                elif p.search(v):
                    out[f"{t}.{col}: {k}"] += 1
    return dict(out)


# ---------------------------------------------------------------------------------------------- Kings
def nomes_oficiais_kings() -> dict:
    """{'por_time': {time_id|nome_errado: nome}, 'nomes': set(nomes do site oficial)}."""
    nomes = set()
    pastas = [comum.CACHE / "kingsleague.pro", comum.CACHE / "kings" / "fotos"]
    rx = [re.compile(r'"@type":\s*"Person",\s*"name"\s*:\s*"([^"]+)"'), re.compile(r'class="player-name"[^>]*>([^<]+)<'),
          re.compile(r'\balt="([^"]+)"')]
    for f in (f for pasta in pastas if pasta.exists() for f in pasta.rglob("*")):
        if True:
            if not f.is_file():
                continue
            try:
                s = f.read_text("utf-8", errors="replace")
            except OSError:
                continue
            if "kingsleague" not in s[:20000] and "kama.football" not in s:
                continue
            for r in rx:
                for m in r.finditer(s):
                    n = html.unescape(m.group(1)).strip()
                    if 2 < len(n) < 60 and "�" not in n and "?" not in n:
                        nomes.add(n)
    por_time = {}
    kf = comum.ler_json("kings_fotos.json", {}) or {}
    for k, v in kf.items():
        if isinstance(v, dict) and v.get("nome_site"):
            nomes.add(v["nome_site"])
            por_time[k] = v["nome_site"]
    return {"por_time": por_time, "nomes": nomes}


def _sem_apelido(s: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"['\"‘’“”]", "", s)).strip().lower()


def reparar_interrogacao(nome: str, time_id: str | None, ofic: dict) -> str:
    """'?lex Guti' -> 'Álex Guti': cada '?' vale uma letra não-ASCII; só troca se o candidato for único."""
    if not nome or ("?" not in nome and "�" not in nome):
        return nome
    padrao = re.compile("^" + "".join("[^\\x00-\\x7f]" if ch in "?�" else re.escape(ch) for ch in _sem_apelido(nome)) + "$")
    v = ofic["por_time"].get(f"{time_id}|{nome}")
    if v and padrao.match(_sem_apelido(v)):
        cands = {v}
    else:
        cands = {n for n in ofic["nomes"] if padrao.match(_sem_apelido(n))}
        if not cands:   # o site às vezes escreve sem acento ('Julio Carvalho')
            frouxo = re.compile("^" + "".join(r"\w" if ch in "?�" else re.escape(ch) for ch in _sem_apelido(nome)) + "$")
            cands = {n for n in ofic["nomes"] if frouxo.match(_sem_apelido(n))}
    if len(cands) != 1:
        return nome
    achado = next(iter(cands))
    if _sem_apelido(achado) == _sem_apelido(nome.replace("?", "x")):
        return nome
    # mantém as aspas/apelido do nome da fonte, só troca as letras
    letras = iter(ch for ch, cn in zip(_sem_apelido(achado), _sem_apelido(nome)) if cn in "?�")
    out = []
    for ch in nome:
        if ch in "?�":
            l = next(letras, ch)
            out.append(l.upper() if not out or out[-1] in " '\"" else l)
        else:
            out.append(ch)
    t = "".join(out)
    # a caixa certa vem do nome oficial quando dá para alinhar letra a letra (sem as aspas)
    if _sem_apelido(t) == _sem_apelido(achado):
        mapa = {i: ch for i, ch in enumerate(achado.replace("'", "").replace('"', ""))}
        j, res = 0, []
        for ch in t:
            if ch in "'\"":
                res.append(ch)
                continue
            o = mapa.get(j, ch)
            res.append(o if o.lower() == ch.lower() else ch)
            j += 1
        t = "".join(res)
    return t


def casa_interrogacao(errado: str, certo: str) -> bool:
    """'Lucas 'Caro?o'' casa com 'Lucas 'Caroço'' ('?' = uma letra qualquer não-ASCII)."""
    if "?" not in errado and "�" not in errado:
        return False
    rx = "^" + "".join("[^\x00-\x7f]" if ch in "?�" else re.escape(ch) for ch in _sem_apelido(errado)) + "$"
    return re.match(rx, _sem_apelido(certo)) is not None


def corrigir_elencos_kings(elencos: list[dict]) -> tuple[list[dict], Counter]:
    """Linhas com '?' no nome (FUT.AE gravou sem acento): some se já existe a certa no mesmo time e temporada;
    senão o nome é trocado pelo certo do mesmo time (outra temporada) ou do site oficial."""
    est = Counter()
    ofic = None
    certos_time = defaultdict(set)
    for e in elencos:
        n = e.get("nome_fonte") or e.get("nome") or ""
        if "?" not in n and "�" not in n:
            certos_time[e.get("time_id")].add(n)
    vistos = set()
    out = []
    for e in elencos:
        n = e.get("nome_fonte") or e.get("nome") or ""
        if "?" in n or "�" in n:
            tid, temp = e.get("time_id"), e.get("temporada")
            mesmo = [x for x in certos_time[tid] if casa_interrogacao(n, x)]
            if any((tid, temp, x) in vistos or any((y.get("nome_fonte") or y.get("nome")) == x and y.get("temporada") == temp
                                                    and y.get("time_id") == tid for y in elencos) for x in mesmo):
                est["duplicadas_removidas"] += 1
                continue
            if len(mesmo) == 1:
                novo = mesmo[0]
                est["reparadas_pelo_time"] += 1
            else:
                if ofic is None:
                    ofic = nomes_oficiais_kings()
                novo = reparar_interrogacao(n, tid, ofic)
                est["reparadas_pelo_site" if novo != n else "sem_reparo"] += 1
            e = {**e, "nome_fonte": novo}
        chave = (e.get("time_id"), e.get("temporada"), e.get("nome_fonte") or e.get("nome"))
        if chave in vistos:
            est["repetidas_identicas_removidas"] += 1
            continue
        vistos.add(chave)
        out.append(e)
    return out, est


# ---------------------------------------------------------------------------------------------- ids duplicados
ID_ALIAS = {"intercontinental": "fifa-intercontinental"}   # Copa Intercontinental (1960–2004) = mesma linhagem da FIFA (2024–)


def unificar_competicoes(c: sqlite3.Connection) -> dict:
    out = Counter()
    for velho, novo in ID_ALIAS.items():
        if not c.execute("SELECT 1 FROM competicoes WHERE id=?", (novo,)).fetchone():
            continue
        tem = {r[0] for r in c.execute("SELECT temporada FROM campeoes_historicos WHERE competicao_id=?", (novo,))}
        for (t,) in c.execute("SELECT temporada FROM campeoes_historicos WHERE competicao_id=?", (velho,)).fetchall():
            if t in tem:
                c.execute("DELETE FROM campeoes_historicos WHERE competicao_id=? AND temporada=?", (velho, t))
                out["campeoes_repetidos_apagados"] += 1
        out["campeoes_movidos"] += c.execute("UPDATE campeoes_historicos SET competicao_id=? WHERE competicao_id=?",
                                             (novo, velho)).rowcount
        for t in ("formatos", "ligas_clubes", "calendarios", "tabelas_iniciais"):
            out[f"{t}_apagados"] += c.execute(f"DELETE FROM {t} WHERE competicao_id=?", (velho,)).rowcount
        out["competicoes_apagadas"] += c.execute("DELETE FROM competicoes WHERE id=?", (velho,)).rowcount
        # vagas "para" o id velho (Libertadores/Champions -> Intercontinental)
        for rid, f in c.execute("SELECT rowid, formato FROM formatos WHERE formato LIKE ?", (f'%"para": "{velho}"%',)).fetchall():
            fm = json.loads(f)
            for v in fm.get("vagas") or []:
                if v.get("para") == velho:
                    v["para"] = novo
                    out["vagas_remapeadas"] += 1
            c.execute("UPDATE formatos SET formato=? WHERE rowid=?", (json.dumps(fm, ensure_ascii=False), rid))
    return {k: v for k, v in out.items() if v}


# ---------------------------------------------------------------------------------------------- formatos
SUPORTADOS = {"pontos_corridos", "mata_mata", "grupos", "liga_suica", "quadrangular", "triangular", "hexagonal", "octogonal"}
GRUPO_MAXIMO_COPA = 8   # Torneio.GRUPO_MAXIMO_COPA
RE_ESTREANTES = re.compile(r"\(\s*(\d+)\s*\+\s*(\d+)")


def _linhas(c):
    por = defaultdict(list)
    for rid, cid, de, ate, f in c.execute("SELECT rowid, competicao_id, valido_de, valido_ate, formato FROM formatos "
                                          "ORDER BY competicao_id, valido_de, rowid"):
        por[cid].append({"rowid": rid, "de": de, "ate": ate, "f": json.loads(f), "mudou": False})
    return por


def _vale(l, ano):
    return (l["de"] or 0) <= ano and (l["ate"] is None or ano <= l["ate"])


def escolher(linhas, ano):
    """Igual a BancoMundo.formato(): a última linha válida no ano; senão a última com valido_de <= ano."""
    achado = None
    for l in linhas:
        if _vale(l, ano):
            achado = l
    if achado is None and linhas and (linhas[-1]["de"] or 0) <= ano:
        achado = linhas[-1]
    return achado


def _provisorio(f):
    return str(f.get("observacoes") or "").startswith("formato provisório")


def _novos(fase):
    if fase.get("entram") is not None:
        return max(0, int(fase["entram"]))
    m = RE_ESTREANTES.search(fase.get("nome") or "")
    return int(m.group(2)) if m else 0


def _efetiva(fase):
    t = fase.get("tipo")
    if t in SUPORTADOS or t is None:
        return dict(fase), None
    e = dict(fase)
    fb = fase.get("fallback") or {}
    if fb.get("tipo") in SUPORTADOS:
        e.update({k: v for k, v in fb.items() if v is not None and k != "observacoes"})
        return e, None
    if (fase.get("grupos") or 0) > 1:
        e["tipo"] = "grupos"
        return e, None
    if any(fase.get(k) is not None for k in ("ida_volta", "gol_fora", "desempate", "chaveamento")):
        e["tipo"] = "mata_mata"
        return e, None
    if fase.get("turnos") is not None or fase.get("classificam") is not None:
        e["tipo"] = "pontos_corridos"
        return e, None
    nm = (fase.get("nome") or "").lower()
    if re.search("triangular|quadrangular|hexagonal|octogonal|grupo", nm) and "final" not in nm:
        e["tipo"] = "grupos"
    elif re.search("final|semi|quartas|oitavas|playoff|repescagem|decis|finais", nm):
        e["tipo"] = "mata_mata"
    else:
        e["tipo"] = "pontos_corridos"
    return e, f"fase '{fase.get('nome')}' tipo '{t}' sem fallback: o motor adivinha '{e['tipo']}' pelo nome"


def simular_fases(f: dict, n_total: int, ambito: str) -> list[str]:
    """Percorre as fases como o construtor de Torneio e devolve os problemas (lista vazia = roda bem)."""
    prob = []
    defs = f.get("fases") or []
    if not defs:
        return ["sem fases"]
    depois = sum(_novos(d) for d in defs[1:])
    escal = 0 < depois < n_total
    n = n_total - depois if escal else n_total
    somar_em, somar = -1, 0
    for k, d0 in enumerate(defs):
        d, aviso = _efetiva(d0)
        if aviso:
            prob.append(aviso)
        prox = _efetiva(defs[k + 1])[0] if k + 1 < len(defs) else None
        if escal and k > 0 and _novos(d0) > 0:
            n += _novos(d0)
        elif k > 0 and d.get("participantes"):
            n = min(n, d["participantes"])
        if k == somar_em:
            n += somar
        t = d.get("tipo") or "pontos_corridos"
        if t in ("quadrangular", "triangular", "hexagonal", "octogonal"):
            t = "grupos"
        if t not in SUPORTADOS:
            prob.append(f"fase '{d.get('nome')}' tipo '{t}' não suportado")
            t = "mata_mata" if n <= 16 else "pontos_corridos"
        if n < 2:
            if k + 1 < len(defs):
                prob.append(f"(info) fase '{d.get('nome')}' depois de já haver campeão (o motor pula: {n} clube)")
            continue
        if t == "pontos_corridos":
            saida = min(n, d["classificam"]) if d.get("classificam") else (n if prox is None else min(n, prox.get("participantes") or n))
        elif t == "liga_suica":
            saida = min(n, d["classificam"]) if d.get("classificam") else (n if prox is None else min(n, prox.get("participantes") or n))
            pl = d.get("playoff")
            if d.get("classificam_direto") is not None and isinstance(pl, list) and len(pl) == 2 and k + 2 < len(defs):
                dir_ = max(0, min(n, d["classificam_direto"]))
                saida = max(0, min(n, pl[1]) - dir_)
                somar_em, somar = k + 2, dir_
        elif t == "grupos":
            quad = d.get("tipo") == "quadrangular"
            g = d.get("grupos") or ((n + 3) // 4 if quad else max(1, n // 4))
            g = max(1, min(g, n // 2))
            maior = -(-n // g)
            cl = d.get("classificam") or (1 if prox is None else 2)
            cl = max(1, min(cl, n // g))
            terc = max(0, min(g, d.get("melhores_terceiros") or 0)) if cl < n // g else 0
            saida = min(n, cl * g + terc)
            if ambito == "copa_nacional" and maior > GRUPO_MAXIMO_COPA and prox is not None:
                prob.append(f"fase '{d.get('nome')}': grupos de {maior} clubes ({n} em {g}) numa copa — o motor troca por mata-mata")
        else:
            alvo = d.get("classificam") or (1 if prox is None else (prox.get("participantes") or max(1, n // 2)))
            if alvo >= n:
                if d0.get("tipo") == "especial":
                    prob.append(f"(info) fase especial '{d.get('nome')}' sem eliminação ({n} já vêm prontos)")
                elif prox is not None or n > 1:
                    prob.append(f"fase '{d.get('nome')}' de mata-mata sem eliminação ({n} entram, {alvo} saem)")
                saida = n
            else:
                saida = max(1, alvo)
        n = saida
    if n != 1 and (defs[-1].get("tipo") in ("mata_mata",) and n > 1):
        prob.append(f"termina com {n} clubes")
    return prob


def normalizar_formatos(c: sqlite3.Connection, gravar: bool = True) -> dict:
    """Formatos válidos em 2026 para toda divisão nacional, cadeia de acesso/queda coerente e auditoria das copas."""
    por = _linhas(c)
    comps = {r[0]: {"ambito": r[1], "pais": r[2], "divisao": r[3], "nome": r[4]}
             for r in c.execute("SELECT id, ambito, pais, divisao, nome FROM competicoes")}
    lc = Counter(r[0] for r in c.execute("SELECT competicao_id FROM ligas_clubes"))
    lc_temp = {r[0]: r[1] for r in c.execute("SELECT competicao_id, MAX(temporada) FROM ligas_clubes GROUP BY 1")}
    rel = defaultdict(list)
    novas = []   # linhas inseridas (cópias)

    def anota(l, txt):
        l["f"]["normalizado"] = ((l["f"].get("normalizado") or "") + "; " if l["f"].get("normalizado") else "") + txt
        l["mudou"] = True

    paises = defaultdict(list)
    for cid, m in comps.items():
        if m["ambito"] == "nacional" and m["divisao"] is not None and m["pais"]:
            paises[m["pais"]].append((m["divisao"], cid))
    for pais in sorted(paises):
        divs = sorted(paises[pais])
        # (a) temporada 2025/26 dos participantes = formato 2025/26 no 1º ano do jogo
        for _, cid in divs:
            ls = por.get(cid, [])
            r25 = next((l for l in reversed(ls) if _vale(l, 2025)), None)
            r26 = escolher(ls, 2026)
            n = lc[cid]
            if (r25 and r26 and r25 is not r26 and lc_temp.get(cid) == "2025/26" and n
                    and r25["f"].get("participantes") == n != r26["f"].get("participantes")):
                r25["ate"] = 2026
                r26["de"] = 2027
                if r26["ate"] is not None and r26["ate"] < 2027:
                    r26["ate"] = 2027
                r25["mudou"] = r26["mudou"] = True
                anota(r25, "vale também no 1º ano do jogo (2026), que começa com os participantes de 2025/26")
                rel["formato_2025_26_no_1o_ano"].append(f"{cid} ({n} clubes; {r26['f'].get('participantes')} a partir de 2027)")
        # (b) formato válido em 2026
        for _, cid in divs:
            ls = por.get(cid, [])
            if ls and not any(_vale(l, 2026) for l in ls):
                ult = ls[-1]
                nova = {"rowid": None, "de": 2026, "ate": None, "f": json.loads(json.dumps(ult["f"])), "mudou": True,
                        "fontes_de": ult["rowid"]}
                anota(nova, f"cópia do formato {ult['de']}–{ult['ate']} (sem formato pesquisado para 2026)")
                ls.append(nova)
                novas.append((cid, nova))
                rel["formato_2026_criado"].append(cid)
            elif not ls:
                rel["sem_formato"].append(cid)
        # (c) participantes e cadeia de acesso/queda
        efet = {cid: escolher(por.get(cid, []), 2026) for _, cid in divs}
        for i, (dv, cid) in enumerate(divs):
            l = efet[cid]
            if not l:
                continue
            f = l["f"]
            n = lc[cid]
            if n and (f.get("participantes") is None or (_provisorio(f) and f.get("participantes") != n)):
                anota(l, f"participantes {f.get('participantes')} -> {n} (ligas_clubes)")
                f["participantes"] = n
                rel["participantes_ajustados"].append(cid)
            elif n and f.get("participantes") != n:
                rel["participantes_diferentes_do_1o_ano"].append(f"{cid}: formato {f.get('participantes')}, ligas_clubes {n}")
            if i > 0 and divs[i - 1][0] != dv - 1:
                rel["buraco_na_cadeia"].append(f"{pais}: divisão {divs[i - 1][0]} -> {dv}")
            if i == 0 and f.get("promovidos") is None:
                f["promovidos"] = 0
                anota(l, "promovidos 0 (1ª divisão)")
        for i in range(len(divs) - 1):
            a, b = efet[divs[i][1]], efet[divs[i + 1][1]]
            if not a or not b:
                continue
            ra, pb = a["f"].get("rebaixados"), b["f"].get("promovidos")
            if ra is not None and pb is None:
                b["f"]["promovidos"] = ra
                anota(b, f"promovidos {ra} (= rebaixados de {divs[i][1]})")
                rel["promovidos_preenchidos"].append(divs[i + 1][1])
            elif ra is None and pb is not None:
                a["f"]["rebaixados"] = pb
                anota(a, f"rebaixados {pb} (= promovidos de {divs[i + 1][1]})")
                rel["rebaixados_preenchidos"].append(divs[i][1])
            elif ra is None and pb is None:
                k = 2 if (a["f"].get("participantes") or 0) >= 10 else 1
                a["f"]["rebaixados"] = b["f"]["promovidos"] = k
                anota(a, f"rebaixados {k} (padrão)")
                anota(b, f"promovidos {k} (padrão)")
                rel["acesso_padrao"].append(f"{divs[i][1]}/{divs[i + 1][1]}")
            elif ra != pb:
                if _provisorio(b["f"]) and not _provisorio(a["f"]):
                    b["f"]["promovidos"] = ra
                    anota(b, f"promovidos {pb} -> {ra} (= rebaixados de {divs[i][1]}, pesquisado)")
                    rel["promovidos_corrigidos"].append(divs[i + 1][1])
                elif _provisorio(a["f"]) and not _provisorio(b["f"]):
                    a["f"]["rebaixados"] = pb
                    anota(a, f"rebaixados {ra} -> {pb} (= promovidos de {divs[i + 1][1]}, pesquisado)")
                    rel["rebaixados_corrigidos"].append(divs[i][1])
                else:
                    rel["cadeia_diferente"].append(f"{divs[i][1]} cai {ra} / {divs[i + 1][1]} sobe {pb}")
        # (c2) divisão de cima muda de tamanho em 2027 (Bélgica 16→18, Egito 21→20, Sérvia 16→14): no 1º ano
        #      sobem da de baixo os rebaixados + a diferença; de 2027 em diante, os rebaixados do formato novo
        for i in range(len(divs) - 1):
            up, lo = divs[i][1], divs[i + 1][1]
            a26, a27 = efet[up], escolher(por.get(up, []), 2027)
            b26 = efet[lo]
            if not a26 or not a27 or a26 is a27 or not b26:
                continue
            p26, p27 = a26["f"].get("participantes"), a27["f"].get("participantes")
            if not p26 or not p27 or p26 == p27 or escolher(por.get(lo, []), 2027) is not b26:
                continue
            if not _provisorio(b26["f"]):   # acesso pesquisado (ex.: Série D sobe 6) manda
                continue
            r26 = a26["f"].get("rebaixados") or 0
            sobe26 = r26 + (p27 - p26)
            if sobe26 < 0:
                rel["crescimento_sem_solucao"].append(f"{up}: {p26}→{p27} com {r26} rebaixados")
                continue
            nova = {"rowid": None, "de": 2027, "ate": None if (b26["ate"] or 9999) < 2027 else b26["ate"], "f": json.loads(json.dumps(b26["f"])), "mudou": True,
                    "fontes_de": b26["rowid"] or b26.get("fontes_de")}
            nova["f"]["promovidos"] = a27["f"].get("rebaixados") if a27["f"].get("rebaixados") is not None else r26
            anota(nova, f"promovidos {nova['f']['promovidos']} a partir de 2027 (= rebaixados de {up} com {p27} clubes)")
            b26["ate"] = 2026
            b26["f"]["promovidos"] = sobe26
            anota(b26, f"promovidos {sobe26} em 2026 ({r26} rebaixados de {up} + {p27 - p26} pela mudança {p26}→{p27})")
            por[lo].append(nova)
            por[lo].sort(key=lambda l: (l["de"] or 0))
            novas.append((lo, nova))
            rel["acesso_ajustado_pela_mudanca_de_tamanho"].append(f"{lo}: sobem {sobe26} em 2026, {nova['f']['promovidos']} depois")
        # (d) tamanho de cada divisão no 2º ano pela cadeia de 2026 x formato de 2027
        for i, (dv, cid) in enumerate(divs):
            l = efet[cid]
            if not l or not l["f"].get("participantes"):
                continue
            f = l["f"]
            sai = (f.get("rebaixados") or 0) * (i + 1 < len(divs)) + (f.get("promovidos") or 0) * (i > 0)
            entra = 0
            if i > 0 and efet[divs[i - 1][1]]:
                entra += efet[divs[i - 1][1]]["f"].get("rebaixados") or 0
            if i + 1 < len(divs) and efet[divs[i + 1][1]]:
                entra += efet[divs[i + 1][1]]["f"].get("promovidos") or 0
            n27 = f["participantes"] - sai + entra
            l27 = escolher(por.get(cid, []), 2027)
            p27 = (l27 or l)["f"].get("participantes")
            if i + 1 < len(divs) and p27 and n27 != p27:
                rel["tamanho_2027_diferente"].append(f"{cid}: cadeia dá {n27}, formato 2027 tem {p27}")

    # (e) copas e continentais: fases que o motor não roda como estão
    for cid, ls in sorted(por.items()):
        m = comps.get(cid)
        if not m or m["ambito"] not in ("copa_nacional", "continental", "selecoes", "estadual", "nacional"):
            continue
        for l in ls:
            if l["ate"] is not None and l["ate"] < 2026:
                continue
            n = l["f"].get("participantes") or lc[cid] or 0
            if n < 2:
                if m["ambito"] in ("copa_nacional", "continental"):
                    rel["fases_sem_participantes"].append(f"{cid} {l['de']}")
                continue
            for p in simular_fases(l["f"], n, m["ambito"]):
                k = "fases_info" if p.startswith("(info)") else "fases_" + m["ambito"]
                rel[k].append(f"{cid} {l['de']}–{l['ate'] or ''}: {p.replace('(info) ', '')}")

    if gravar:
        for cid, ls in por.items():
            for l in ls:
                if not l["mudou"]:
                    continue
                if l["rowid"] is None:
                    fts = c.execute("SELECT fontes FROM formatos WHERE rowid=?", (l["fontes_de"],)).fetchone()[0]
                    c.execute("INSERT INTO formatos VALUES (?,?,?,?,?)",
                              (cid, l["de"], l["ate"], json.dumps(l["f"], ensure_ascii=False), fts))
                else:
                    c.execute("UPDATE formatos SET valido_de=?, valido_ate=?, formato=? WHERE rowid=?",
                              (l["de"], l["ate"], json.dumps(l["f"], ensure_ascii=False), l["rowid"]))
    return {k: v for k, v in rel.items()}


def resumo(rel: dict) -> str:
    out = []
    for k, v in rel.items():
        out.append(f"  {k}: {len(v)}")
        for x in v[:60]:
            out.append(f"     - {x}")
    return "\n".join(out)


def main():
    arq = next((a for a in sys.argv[1:] if not a.startswith("--")), str(comum.DADOS / "mundo.sqlite"))
    gravar = "--gravar" in sys.argv
    c = sqlite3.connect(arq)
    print("texto (problemas restantes):", json.dumps(problemas_texto(c), ensure_ascii=False, indent=1))
    rel = normalizar_formatos(c, gravar=gravar)
    print("formatos:\n" + resumo(rel))
    if gravar:
        c.commit()
    c.close()


if __name__ == "__main__":
    main()


def indice_fotos_kings(c: sqlite3.Connection) -> int:
    """assets/kings/fotos/indice.json foi feito com os nomes com '?': acrescenta a chave do nome reparado
    (mesmo arquivo de foto). Só acrescenta; nada é apagado."""
    arq = comum.RAIZ / "assets" / "kings" / "fotos" / "indice.json"
    if not arq.exists():
        return 0
    d = json.loads(arq.read_text("utf-8"))
    pt = d.get("por_time") or {}
    nomes = defaultdict(set)
    for t, n in c.execute("SELECT DISTINCT time_id, nome_fonte FROM kings_elencos"):
        nomes[t].add(n)
    novos = 0
    ofic = None
    for k, v in list(pt.items()):
        t, _, n = k.partition("|")
        if "?" not in n:
            continue
        ofic = ofic or nomes_oficiais_kings()
        for certo in nomes.get(t, ()):
            if f"{t}|{certo}" not in pt and (casa_interrogacao(n, certo) or reparar_interrogacao(n, t, ofic) == certo):
                pt[f"{t}|{certo}"] = v
                novos += 1
    if novos:
        arq.write_text(json.dumps(d, ensure_ascii=False, indent=1), "utf-8")
    return novos
