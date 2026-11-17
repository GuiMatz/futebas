"""FUTÉBAS: monta dados/mundo.sqlite no esquema v2 (docs/arquitetura-v2.md).

Fontes (em ordem de prioridade):
1. Mod Brasfoot 2026 (C:\\Brasfoot22-23, SOMENTE LEITURA, uso pessoal): clubes do mundo inteiro, nível,
   reputação, cores, estádio, escudos e elencos (lidos por brasfoot_mod.py -> intermediario/mod_*.json).
2. transfermarkt-datasets (CC0, atual até 06/07/2026; coleta/cache/transfermarkt-datasets/raw): elencos
   2025/26 (e 2026 para ligas de ano-calendário) das 32 ligas cobertas, nascimento, pé, altura, valor,
   contrato, retrato; participantes da Champions/Liga Europa/Conference 2025/26; tabelas em 1/jan/2026.
3. EA SPORTS FC 26 (CSV público no GitHub, ismailoksuz/EAFC26-DataHub, raspado do SoFIFA): overall,
   potencial e atributos quando o jogador casa por data de nascimento + nome.
4. Wikipedia (intermediario/wiki_participantes.json, wiki_copa2026.json): participantes reais 2026 / 2025-26.
5. Pesquisa (dados/pesquisa/*.json, partes/*.json, kings-2026.json): formatos, calendários, regras, Kings.

O antigo montador v1 está em coleta/legado/montar_banco.py.
Uso: python montar_banco.py
"""
from __future__ import annotations
import os

import datetime as dt
import glob
import json
import math
import random
import re
import shutil
import sqlite3
import sys
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
import pandas as pd
from PIL import Image
from rapidfuzz import fuzz

import comum
import normalizar
from comum import norm, slug

RAIZ = comum.RAIZ
DB = Path(__import__("os").environ.get("FUTEBAS_DB") or (comum.DADOS / "mundo.sqlite"))  # FUTEBAS_DB: montagem de teste
TM = comum.CACHE / "transfermarkt-datasets" / "raw"
TM_URL = "https://github.com/dcaribou/transfermarkt-datasets"
EA_CSV = comum.CACHE / "eafc26" / "main_players.csv"
EA_URL = "https://github.com/ismailoksuz/EAFC26-DataHub/blob/main/data/players.csv"
MOD = Path(os.environ.get("BRASFOOT_DIR", r"C:\Brasfoot22-23"))
PESQ = comum.DADOS / "pesquisa"
ASSETS = RAIZ / "assets"
HOJE = dt.date.today().isoformat()
INICIO = dt.date(2026, 1, 1)
random.seed(2026)

ESQUEMA = """
CREATE TABLE fontes (id INTEGER PRIMARY KEY, url TEXT UNIQUE NOT NULL, tipo TEXT, titulo TEXT, acessado_em TEXT, confiabilidade INTEGER, licenca TEXT);
CREATE TABLE paises (id TEXT PRIMARY KEY, nome TEXT NOT NULL, confederacao TEXT, calendario TEXT NOT NULL, bandeira_png TEXT, ranking_fifa INTEGER, cor1 TEXT, cor2 TEXT);
CREATE TABLE clubes (id TEXT PRIMARY KEY, nome TEXT NOT NULL, nome_completo TEXT, sigla TEXT, cidade TEXT, uf TEXT, pais TEXT NOT NULL, fundacao TEXT, cor1 TEXT, cor2 TEXT, estadio TEXT, capacidade INTEGER, reputacao REAL, torcida REAL, orcamento_ref INTEGER, escudo_png TEXT, escudo_fonte TEXT, escudo_licenca TEXT, tm_id INTEGER, wikidata TEXT, rival_ids TEXT);
CREATE TABLE clube_fontes (clube_id TEXT, fonte_id INTEGER, PRIMARY KEY (clube_id, fonte_id));
CREATE TABLE jogadores (id INTEGER PRIMARY KEY, nome TEXT NOT NULL, nome_conhecido TEXT NOT NULL, nascimento TEXT, pais TEXT NOT NULL, pais2 TEXT, posicao TEXT NOT NULL, posicoes_sec TEXT, pe TEXT, altura INTEGER, clube_id TEXT, emprestado_de TEXT, contrato_ate TEXT, camisa INTEGER, valor_mercado INTEGER, forca INTEGER NOT NULL, potencial INTEGER NOT NULL, atributos TEXT, caracteristicas TEXT, origem TEXT NOT NULL, tm_id INTEGER, wikidata TEXT, ea_id INTEGER, foto_png TEXT, foto_fonte TEXT, foto_licenca TEXT, conflito TEXT);
CREATE TABLE jogador_fontes (jogador_id INTEGER, fonte_id INTEGER, PRIMARY KEY (jogador_id, fonte_id));
CREATE TABLE ligas_clubes (competicao_id TEXT, temporada TEXT, clube_id TEXT, PRIMARY KEY (competicao_id, temporada, clube_id));
CREATE TABLE tabelas_iniciais (competicao_id TEXT, temporada TEXT, fase TEXT, grupo TEXT, clube_id TEXT, j INT, v INT, e INT, d INT, gp INT, gc INT, pts INT, fonte TEXT);
CREATE TABLE competicoes (id TEXT PRIMARY KEY, nome TEXT NOT NULL, ambito TEXT NOT NULL, pais TEXT, uf TEXT, confederacao TEXT, divisao INTEGER, calendario TEXT, logo_png TEXT);
CREATE TABLE formatos (competicao_id TEXT, valido_de INTEGER, valido_ate INTEGER, formato TEXT NOT NULL, fontes TEXT NOT NULL);
CREATE TABLE calendarios (pais TEXT, competicao_id TEXT, temporada TEXT, de TEXT, ate TEXT, fontes TEXT);
CREATE TABLE regras (regra TEXT, valor TEXT, valido_de TEXT, ambito TEXT, fonte TEXT);
CREATE TABLE selecoes (pais TEXT PRIMARY KEY, tecnico TEXT, ranking_fifa INTEGER, classificada_copa_2026 INTEGER, grupo_copa_2026 TEXT, fonte TEXT);
CREATE TABLE kings_ligas (id TEXT PRIMARY KEY, nome TEXT, pais TEXT, inicio TEXT, regras TEXT, draft TEXT, fontes TEXT, logo_png TEXT, trofeu_png TEXT);
CREATE TABLE kings_times (id TEXT PRIMARY KEY, liga_id TEXT, nome TEXT, presidente TEXT, escudo_png TEXT, cor1 TEXT, cor2 TEXT, fonte TEXT);
CREATE TABLE kings_elencos (time_id TEXT, temporada TEXT, jogador_id INTEGER, nome_fonte TEXT, tipo TEXT, fonte TEXT);
CREATE TABLE tecnicos (id INTEGER PRIMARY KEY, nome TEXT NOT NULL, pais TEXT, clube_id TEXT, selecao TEXT, origem TEXT NOT NULL DEFAULT 'real', fonte_id INTEGER);
CREATE TABLE nomes_gerados (pais TEXT, tipo TEXT, nome TEXT, freq INTEGER);
CREATE TABLE meta (chave TEXT PRIMARY KEY, valor TEXT);
CREATE INDEX ix_jog_clube ON jogadores(clube_id);
CREATE INDEX ix_lc_clube ON ligas_clubes(clube_id);
"""

# ---------------------------------------------------------------- países
CONMEBOL = set("ARG BOL BRA CHL COL ECU PRY PER URY VEN".split())
CONCACAF = set("AIA ATG ABW BHS BRB BLZ BMU VGB CAN CYM CRI CUB CUW DMA DOM SLV GRD GTM GUY HTI HND JAM MEX MSR "
               "NIC PAN PRI KNA LCA VCT SUR TTO TCA USA VIR GLP MTQ GUF BES MAF SXM".split())
OFC = set("ASM COK FJI NCL NZL PNG WSM SLB PYF TON VUT KIR TUV".split())
AFC = set("AFG AUS BHR BGD BTN BRN KHM CHN TWN GUM HKG IND IDN IRN IRQ JPN JOR KWT KGZ LAO LBN MAC MYS MDV MNG "
          "MMR NPL PRK OMN PAK PSE PHL QAT SAU SGP KOR LKA SYR TJK THA TLS TKM ARE UZB VNM YEM FSM MHL PLW NRU".split())
UEFA = set("ALB AND ARM AUT AZE BLR BEL BIH BGR HRV CYP CZE DNK ENG EST FRO FIN FRA GEO DEU GIB GRC HUN ISL IRL "
           "ISR ITA KAZ XKX LVA LIE LTU LUX MLT MDA MCO MNE NLD MKD NIR NOR POL PRT ROU RUS SMR SCO SRB SVK SVN ESP "
           "SWE CHE TUR UKR WAL".split())
ANO_UEFA = set("IRL NOR SWE FIN ISL FRO LVA LTU EST BLR KAZ GEO".split())
ANO_AFC = set("JPN KOR CHN PRK MYS SGP VNM MMR LAO KHM TLS MNG BRN PHL BTN NPL TKM UZB TJK KGZ".split())
EUROPEU_CONCACAF = set("MEX CRI SLV GTM HND NIC PAN".split())


def confed(i3):
    for nome, s in (("CONMEBOL", CONMEBOL), ("CONCACAF", CONCACAF), ("OFC", OFC), ("AFC", AFC), ("UEFA", UEFA)):
        if i3 in s:
            return nome
    return "CAF"


def calendario_pais(i3):
    c = confed(i3)
    if c == "UEFA":
        return "ano" if i3 in ANO_UEFA else "europeu"
    if c == "AFC":
        return "ano" if i3 in ANO_AFC else "europeu"
    if c == "CAF":
        return "europeu"
    if c == "CONCACAF":
        return "europeu" if i3 in EUROPEU_CONCACAF else "ano"
    return "ano"


def temporada_de(pais):
    return "2026" if calendario_pais(pais) == "ano" else "2025/26"


TM_PAIS = {"Austria": "AUT", "Argentina": "ARG", "Australia": "AUS", "Belgium": "BEL", "Brazil": "BRA",
           "Switzerland": "CHE", "Germany": "DEU", "Denmark": "DNK", "Spain": "ESP", "France": "FRA",
           "England": "ENG", "Greece": "GRC", "Italy": "ITA", "Japan": "JPN", "Croatia": "HRV", "Mexico": "MEX",
           "United States": "USA", "Netherlands": "NLD", "Norway": "NOR", "Poland": "POL", "Portugal": "PRT",
           "Romania": "ROU", "Korea, South": "KOR", "Russia": "RUS", "Saudi Arabia": "SAU", "Scotland": "SCO",
           "Sweden": "SWE", "Serbia": "SRB", "Czech Republic": "CZE", "Ukraine": "UKR", "Colombia": "COL"}
TM_LIGA = {"GB1": "eng-1", "ES1": "esp-1", "IT1": "ita-1", "L1": "deu-1", "FR1": "fra-1", "PO1": "prt-1",
           "NL1": "nld-1", "BE1": "bel-1", "SC1": "sco-1", "TR1": "tur-1", "GR1": "grc-1", "RU1": "rus-1",
           "UKR1": "ukr-1", "DK1": "dnk-1", "C1": "che-1", "A1": "aut-1", "ARG1": "arg-1", "BRA1": "bra-1",
           "MLS1": "usa-1", "MEX1": "mex-1", "JAP1": "jpn-1", "SA1": "sau-1", "COL1": "col-1", "KR1": "hrv-1",
           "NO1": "nor-1", "PL1": "pol-1", "RO1": "rou-1", "SE1": "swe-1", "SER1": "srb-1", "TS1": "cze-1",
           "RSK1": "kor-1", "AUS1": "aus-1"}
NOMES_COMP = {
    "bra-1": "Brasileirão Série A", "bra-2": "Brasileirão Série B", "bra-3": "Brasileirão Série C",
    "bra-4": "Brasileirão Série D", "bra-copa": "Copa do Brasil", "eng-1": "Premier League",
    "eng-2": "EFL Championship", "eng-3": "EFL League One", "eng-4": "EFL League Two", "esp-1": "LaLiga",
    "esp-2": "LaLiga Hypermotion", "ita-1": "Serie A", "ita-2": "Serie B", "deu-1": "Bundesliga",
    "deu-2": "2. Bundesliga", "deu-3": "3. Liga", "fra-1": "Ligue 1", "fra-2": "Ligue 2", "prt-1": "Liga Portugal",
    "prt-2": "Liga Portugal 2", "nld-1": "Eredivisie", "nld-2": "Eerste Divisie", "bel-1": "Jupiler Pro League",
    "sco-1": "Scottish Premiership", "tur-1": "Süper Lig", "grc-1": "Super League Grécia", "rus-1": "Premier Liga Rússia",
    "ukr-1": "Premier Liga Ucrânia", "dnk-1": "Superliga Dinamarca", "che-1": "Super League Suíça",
    "aut-1": "Bundesliga Áustria", "arg-1": "Liga Profesional Argentina", "usa-1": "MLS", "mex-1": "Liga MX",
    "jpn-1": "J1 League", "sau-1": "Saudi Pro League", "col-1": "Liga BetPlay", "kor-1": "K League 1",
    "chl-1": "Liga de Primera (Chile)", "ury-1": "Campeonato Uruguaio", "pry-1": "Campeonato Paraguaio",
    "per-1": "Liga 1 (Peru)", "ecu-1": "LigaPro Serie A", "bol-1": "División Profesional (Bolívia)",
    "ven-1": "Liga FUTVE", "conmebol-libertadores": "Copa Libertadores", "conmebol-sul-americana": "Copa Sul-Americana",
    "uefa-champions": "UEFA Champions League", "uefa-europa": "UEFA Europa League",
    "uefa-conference": "UEFA Conference League",
    # ligas que saíam sem nome (o id cru, "zaf-1"); a mesma lista está em jogo/.../dados/NomesCompeticao.java
    "bel-2": "Challenger Pro League", "bgr-1": "efbet Liga", "bih-1": "Premijer Liga BiH", "cyp-1": "Cyprus League",
    "dza-1": "Ligue 1 Mobilis", "fin-1": "Veikkausliiga", "geo-1": "Erovnuli Liga", "hun-1": "NB I",
    "ind-1": "Indian Super League", "irl-1": "League of Ireland Premier Division", "isl-1": "Besta deild karla",
    "kaz-1": "Kazakhstan Premier League", "sau-2": "Saudi First Division", "sco-2": "Scottish Championship",
    "svk-1": "Niké liga", "svn-1": "Prva Liga", "tun-1": "Ligue Professionnelle 1", "tur-2": "TFF 1. Lig",
    "usa-2": "USL Championship", "zaf-1": "Premier Soccer League",
}
ORDINAL_DIV = {1: "Primeira", 2: "Segunda", 3: "Terceira", 4: "Quarta", 5: "Quinta", 6: "Sexta"}


def nome_divisao(n):
    """'1º Divisão' (mod do Brasfoot, gênero errado) -> 'Primeira Divisão'."""
    m = re.match(r"^(\d+)\s*[ºª°]\s*Divis[aã]o", n or "")
    if not m:
        return n
    k = int(m.group(1))
    return f"{ORDINAL_DIV.get(k, str(k) + 'ª')} Divisão{n[m.end():]}"
# Clubes que ficam sempre no jogo, mesmo quando a lista de participantes de 2026 não os traz.
CLUBES_OBRIGATORIOS = [
    ("est-ba-2", "colo-colo-ba"),   # Colo Colo de Ilhéus (BA)
]

UF_NOME = {"AC": "Acreano", "AL": "Alagoano", "AM": "Amazonense", "AP": "Amapaense", "BA": "Baiano", "CE": "Cearense",
           "DF": "Brasiliense", "ES": "Capixaba", "GO": "Goiano", "MA": "Maranhense", "MG": "Mineiro",
           "MS": "Sul-Mato-Grossense", "MT": "Mato-Grossense", "PA": "Paraense", "PB": "Paraibano", "PE": "Pernambucano",
           "PI": "Piauiense", "PR": "Paranaense", "RJ": "Carioca", "RN": "Potiguar", "RO": "Rondoniense",
           "RR": "Roraimense", "RS": "Gaúcho", "SC": "Catarinense", "SE": "Sergipano", "SP": "Paulista",
           "TO": "Tocantinense"}

POS_TM = {"Goalkeeper": "GOL", "Centre-Back": "ZAG", "Right-Back": "LD", "Left-Back": "LE",
          "Defensive Midfield": "VOL", "Central Midfield": "MC", "Attacking Midfield": "MEI",
          "Right Winger": "PD", "Right Midfield": "PD", "Left Winger": "PE", "Left Midfield": "PE",
          "Centre-Forward": "CA", "Second Striker": "CA"}
POS_GERAL = {"Attack": "CA", "Defender": "ZAG", "Midfield": "MC", "Goalkeeper": "GOL"}
POS_EA = {"GK": "GOL", "CB": "ZAG", "RB": "LD", "RWB": "LD", "LB": "LE", "LWB": "LE", "CDM": "VOL", "CM": "MC",
          "CAM": "MEI", "RM": "PD", "RW": "PD", "LM": "PE", "LW": "PE", "ST": "CA", "CF": "CA"}
CARAC = {0: "colocacao", 1: "saida_do_gol", 2: "reflexo", 3: "defesa_de_penalti", 4: "armacao", 5: "cabeceio",
         6: "cruzamento", 7: "desarme", 8: "drible", 9: "finalizacao", 10: "marcacao", 11: "passe",
         12: "resistencia", 13: "velocidade"}
PALAVRAS_VAZIAS = set("fc cf sc ec ac afc cd ca cs fk sk sv club clube clube de futebol futebol esporte esportiva "
                      "sociedade associacao associação de do da dos das the football calcio deportivo deportiva atletico "
                      "sporting real 1 2 u s a".split()) - {"atletico", "sporting", "real"}


def nome_chave(s):
    t = [w for w in norm(s).split() if w not in PALAVRAS_VAZIAS]
    return " ".join(t) or norm(s)


def tm_iso(nome):
    from paises import iso3
    esp = {"England": "ENG", "Scotland": "SCO", "Wales": "WAL", "Northern Ireland": "NIR", "Türkiye": "TUR",
           "Turkey": "TUR", "Kosovo": "XKX"}
    if not isinstance(nome, str) or not nome:
        return None
    return esp.get(nome) or iso3(nome)


# ---------------------------------------------------------------- banco
class Banco:
    def __init__(self):
        if DB.exists():
            DB.unlink()
        self.c = sqlite3.connect(DB)
        self.c.executescript(ESQUEMA)
        self._f: dict[str, int] = {}

    def fonte(self, url, tipo="base_dados", conf=3, titulo=None, licenca=None):
        if url in self._f:
            return self._f[url]
        cur = self.c.execute("INSERT INTO fontes (url,tipo,titulo,acessado_em,confiabilidade,licenca) VALUES (?,?,?,?,?,?)",
                             (url, tipo, titulo, HOJE, conf, licenca))
        self._f[url] = cur.lastrowid
        return cur.lastrowid


# ---------------------------------------------------------------- carga
def carregar_tm():
    p = pd.read_csv(TM / "players.csv.gz")
    cl = pd.read_csv(TM / "clubs.csv.gz")
    comp = pd.read_csv(TM / "competitions.csv.gz")
    comp_pais = {r.competition_id: TM_PAIS.get(r.country_name) or ("TUR" if r.competition_id == "TR1" else None)
                 for r in comp.itertuples()}
    # ligas que não vêm em competitions.csv (a COL1 falta no dataset): país pelo código da liga
    for cod, pais in (("COL1", "COL"),):
        comp_pais.setdefault(cod, pais)
    return p, cl, comp_pais


def carregar_ea():
    if not EA_CSV.exists():
        return {}
    ea = pd.read_csv(EA_CSV, low_memory=False)
    idx = defaultdict(list)
    for r in ea.itertuples():
        if isinstance(r.dob, str):
            idx[r.dob[:10]].append(r)
    return idx


def casar_ea(idx, nasc, *nomes):
    if not nasc or nasc not in idx:
        return None
    melhor, ms = None, 0
    for r in idx[nasc]:
        for n in nomes:
            if not n:
                continue
            s = max(fuzz.token_set_ratio(norm(n), norm(r.long_name)), fuzz.token_set_ratio(norm(n), norm(r.short_name)))
            ult = norm(n).split()[-1:] or [""]
            if ult[0] and ult[0] in norm(r.long_name).split():
                s = max(s, 85)
            if s > ms:
                melhor, ms = r, s
    return melhor if ms >= 80 else None


def ler_pesquisa_todos():
    arqs = [p for p in glob.glob(str(PESQ / "*.json")) + glob.glob(str(PESQ / "partes" / "*.json"))
            if Path(p).name not in ("fontes.json",)]
    out = {"competicoes": [], "calendarios": [], "regras": [], "selecoes": [], "kings": None, "fontes": [],
           "selecoes_2026": {}, "tabelas": []}
    for a in arqs:
        try:
            d = json.loads(Path(a).read_text("utf-8"))
        except Exception as e:  # noqa: BLE001
            print("  pesquisa ilegível", a, e)
            continue
        if Path(a).name.startswith("kings"):
            out["kings"] = d
            continue
        if Path(a).name.startswith("selecoes"):
            out["selecoes_2026"] = d
            continue
        if Path(a).name.startswith("tabelas"):
            out["tabelas"] = d.get("tabelas", []) if isinstance(d, dict) else d
            continue
        if isinstance(d, dict):
            for k in ("competicoes", "calendarios", "regras", "selecoes", "fontes"):
                v = d.get(k)
                if isinstance(v, list):
                    out[k].extend(v)
                elif isinstance(v, dict) and k == "selecoes":
                    out[k].extend([{"pais": kk, **vv} if isinstance(vv, dict) else {"pais": kk} for kk, vv in v.items()])
        elif isinstance(d, list) and Path(a).stem in out:
            out[Path(a).stem].extend(d)
    # pesquisa antiga do Brasil (legado): só os formatos válidos em 2026, com os ids do contrato v2
    leg = PESQ / "legado" / "competicoes.json"
    if leg.exists():
        mapa = {"bra-serie-a": "bra-1", "bra-serie-b": "bra-2", "bra-serie-c": "bra-3", "bra-serie-d": "bra-4",
                "bra-copa-do-brasil": "bra-copa"}
        ja = {x.get("id") for x in out["competicoes"]}
        for comp in json.loads(leg.read_text("utf-8")):
            fs = [f for f in comp.get("formatos", []) if (f.get("valido_ate") or 9999) >= 2026 and (f.get("valido_de") or 0) >= 2024]
            cid = mapa.get(comp["id"], comp["id"])
            if fs and cid not in ja:
                out["competicoes"].append({**comp, "id": cid, "formatos": fs})
    return out


# ---------------------------------------------------------------- transferências 2026/27
ELENCOS_ATE = "2026-11-11"      # elencos como estavam nesta data (temporada 2026/27 / 2º semestre de 2026)
JANELA_INI = "2026-05-01"       # movimentos a partir daqui contam como janela do meio de 2026
RE_SEM_CLUBE = re.compile(r"without club|vereinslos|unknown|career break|pause|sem clube", re.I)
RE_APOSENTOU = re.compile(r"retired|karriereende", re.I)
RE_BASE = re.compile(r"\b(B|II|III|U\s?1[5-9]|U\s?2[0-3]|Sub-?\d\d|Youth|Juniors?|Reserves?|Castilla|Atl[eè]tic|"
                     r"Next ?Gen|Juvenil|Primavera|Academy|Am\.|Amateure|Jong|Futures|Promesas|Fem)\b", re.I)


def mapa_qid_clubes(clubes):
    """qid do Wikidata -> clubes.id usando historia.mapa_clubes sobre uma cópia em memória da tabela clubes."""
    import historia
    m = sqlite3.connect(":memory:")
    m.execute("CREATE TABLE clubes (id, nome, nome_completo, pais, uf, tm_id, reputacao)")
    m.executemany("INSERT INTO clubes VALUES (?,?,?,?,?,?,?)",
                  [(k, v["nome"], v["nome_completo"], v["pais"], v["uf"], v["tm_id"],
                    (v["rep"] or 0) * 20 + (v["nivel"] or 0)) for k, v in clubes.items()])
    res = historia.mapa_clubes(m)
    m.close()
    return res


def transferencias_2627(tmp, tm_cid, qid_cid, clubes):
    """tm player_id -> {"clube": clubes.id|None, "emprestado_de", "data", "fonte": "TM"|"Wikidata", "destino",
    "aposentou", "so_emprestimo"} para quem mudou de clube na janela do meio de 2026 (ou está emprestado)."""
    tr = pd.read_csv(TM / "transfers.csv.gz").reset_index()
    tr = tr[tr.transfer_date.notna()]
    atual = {int(r.player_id): (None if pd.isna(r.current_club_id) else int(r.current_club_id))
             for r in tmp.itertuples()}
    nome_idx = defaultdict(list)
    for k, v in clubes.items():
        for n in {v["nome"], v["nome_completo"]} - {None}:
            nome_idx[nome_chave(n)].append(k)

    def clube_tm(tid, nome):
        """(clube_id, destino fora do banco)"""
        if tid is not None and not pd.isna(tid) and int(tid) in tm_cid:
            return tm_cid[int(tid)], None
        cs = nome_idx.get(nome_chave(str(nome or ""))) or []
        if len(cs) == 1 and len(nome_chave(str(nome))) > 4:
            return cs[0], None
        return None, str(nome or "?")

    out = {}
    for pid, g in tr.groupby("player_id"):
        pid = int(pid)
        rows = list(g.sort_values(["transfer_date", "index"]).itertuples())
        passado = [r for r in rows if r.transfer_date <= ELENCOS_ATE]
        futuro = [r for r in rows if r.transfer_date > ELENCOS_ATE]
        L = None
        if passado:
            ult = [r for r in passado if r.transfer_date == passado[-1].transfer_date]
            tos = {r.to_club_id for r in ult}
            # mesmo dia: fica a ponta da cadeia (a linha cuja origem não é destino de outra linha do dia)
            L = next((r for r in ult if r.from_club_id in tos and r.to_club_id not in {x.from_club_id for x in ult}), ult[-1])
        base_tm = L.to_club_id if L is not None and L.transfer_date >= JANELA_INI else atual.get(pid)
        dd = None
        if L is not None and L.transfer_date >= JANELA_INI:
            nome = str(L.to_club_name)
            if RE_APOSENTOU.search(nome):
                dd = {"clube": None, "aposentou": True}
            elif RE_SEM_CLUBE.search(nome) or pd.isna(L.to_club_id):
                dd = {"clube": None}
            else:
                cid, fora = clube_tm(L.to_club_id, nome)
                if cid is None and RE_BASE.search(nome):
                    cid, fora = clube_tm(L.from_club_id, L.from_club_name)   # foi para o time B/sub-23: fica no principal
                dd = {"clube": cid, "destino": fora}
            dd.update({"data": L.transfer_date, "fonte": "TM"})
        # empréstimo em curso: a próxima linha (futura) devolve o jogador do clube atual ao dono
        if futuro and base_tm is not None and futuro[0].from_club_id == base_tm and not pd.isna(futuro[0].to_club_id):
            dono = tm_cid.get(int(futuro[0].to_club_id))
            atual_cid = tm_cid.get(int(base_tm))
            if dono and atual_cid and dono != atual_cid:
                if dd is None:
                    dd = {"clube": atual_cid, "so_emprestimo": True, "data": futuro[0].transfer_date, "fonte": "TM"}
                if dd.get("clube") == atual_cid:
                    dd["emprestado_de"] = dono
        if dd is not None:
            dd["contrato_invalido"] = True
            out[pid] = dd
    n_tm = len(out)
    # Wikidata: movimentos depois do corte do TM (06/07/2026) — só entram quando o destino existe no banco
    wd = (comum.ler_json("transferencias_wd_2026.json", {}) or {}).get("transferencias", [])
    tm_nasc = defaultdict(list)
    for r in tmp.itertuples():
        if isinstance(r.date_of_birth, str):
            tm_nasc[(norm(r.name), r.date_of_birth[:10])].append(int(r.player_id))
    ultimo = {}
    sem_tm = 0
    for x in wd:
        if not (JANELA_INI <= x["inicio"] <= ELENCOS_ATE):
            continue
        pid = None
        if x.get("tm") and str(x["tm"]).isdigit() and int(x["tm"]) in atual:
            pid = int(x["tm"])
        elif x.get("nome") and x.get("nasc"):
            cs = tm_nasc.get((norm(x["nome"]), x["nasc"]), [])
            pid = cs[0] if len(cs) == 1 else None
        if pid is None:
            sem_tm += 1
            continue
        if pid not in ultimo or x["inicio"] > ultimo[pid]["inicio"]:
            ultimo[pid] = x
    n_wd = 0
    for pid, x in ultimo.items():
        prev = out.get(pid)
        if prev and not prev.get("so_emprestimo") and prev["data"] >= x["inicio"]:
            continue
        cid = None
        if x.get("clube_tm") and str(x["clube_tm"]).isdigit():
            cid = tm_cid.get(int(x["clube_tm"]))
        cid = cid or qid_cid.get(x["clube_qid"])
        if cid is None:
            continue
        antes = prev["clube"] if prev and not prev.get("so_emprestimo") else tm_cid.get(atual.get(pid) or -1)
        if prev and prev.get("so_emprestimo"):
            antes = prev.get("emprestado_de") or prev["clube"]
        if cid == antes:
            continue
        dd = {"clube": cid, "data": x["inicio"], "fonte": "Wikidata", "contrato_invalido": True}
        if x.get("emprestimo") and antes:
            dd["emprestado_de"] = antes
        out[pid] = dd
        n_wd += 1
    print(f"  transferências: TM {n_tm} jogadores (janela/empréstimos), Wikidata {n_wd} "
          f"(de {len(wd)} registros; {sem_tm} sem jogador no transfermarkt-datasets)")
    return out


# ---------------------------------------------------------------- transferências BR 2026 (Wikipedia)
# coleta_transferencias_br.py -> intermediario/transferencias_br_2026.json (páginas de temporada 2026 das Séries A/B).
BR_INI = "2026-01-01"          # movimentos do ano; só valem se forem mais novos que a decisão TM/Wikidata do jogador
BR_ELENCO_MIN = "2026-07-06"   # elencos (Fs player / Template squad) só contam se editados depois do corte do TM
POS_WIKI = [(r"goal|^gk$|^g$|goleiro", "GOL"), (r"right[- ]back|^rb$|^rwb$|^ld$|lateral[- ]direito", "LD"),
            (r"left[- ]back|^lb$|^lwb$|^le$|lateral[- ]esquerdo", "LE"),
            (r"cent(re|er)[- ]back|^cb$|^z$|zagueiro|^df$|defender", "ZAG"),
            (r"defensive mid|^dm$|^cdm$|^v$|volante", "VOL"), (r"attacking mid|^am$|^cam$|^mei$|meia", "MEI"),
            (r"left wing|^lw$|^lm$|^pe$|ponta[- ]esquerda", "PE"), (r"right wing|^rw$|^rm$|^pd$|ponta[- ]direita|winger", "PD"),
            (r"centre[- ]forward|striker|^cf$|^st$|^fw$|forward|^a$|atacante|centroavante", "CA"),
            (r"mid|^cm$|^mf$|^m$|meio", "MC")]
FIFA_ISO = {"URU": "URY", "CHI": "CHL", "POR": "PRT", "GER": "DEU", "NED": "NLD", "PAR": "PRY", "SUI": "CHE",
            "CRO": "HRV", "DEN": "DNK", "GRE": "GRC", "KSA": "SAU", "UAE": "ARE", "RSA": "ZAF", "ALG": "DZA",
            "NGR": "NGA", "CRC": "CRI", "HON": "HND", "GUA": "GTM", "ANG": "AGO", "BUL": "BGR", "HAI": "HTI",
            "IRI": "IRN", "ZAM": "ZMB", "TOG": "TGO", "GAM": "GMB", "GUI": "GIN", "CGO": "COG", "SUD": "SDN"}


def pos_wiki(*txts):
    for t in txts:
        t = norm(t or "")
        if not t:
            continue
        for rx, p in POS_WIKI:
            if re.search(rx, t):
                return p
    return None


def _nasc_estimado(j):
    return bool(j.get("nascimento")) and "nascimento" in (j.get("conflito") or {})


def _registro_real(j):
    """Registro com dados reais (TM ou EA) e data de nascimento verdadeira."""
    return bool(j.get("tm_id") or j.get("ea") is not None) and not _nasc_estimado(j)


def _nomes(j):
    return {norm(n) for n in (j.get("nome"), j.get("nome_conhecido")) if n} - {""}


def mesma_pessoa_mod(a, b):
    """a (TM/EA) e b (só do mod, nascimento estimado) são a mesma pessoa?
    Mesmo nome normalizado; ano de nascimento a até 1 ano (nome de uma palavra: Alisson, Douglas...)
    ou até 4 anos para nome composto (a idade do mod costuma estar atrasada: Ricardo Velho, Joe Aribo)."""
    if not (_registro_real(a) and not b.get("tm_id") and b.get("ea") is None and _nasc_estimado(b)):
        return False
    comuns = _nomes(a) & _nomes(b)
    if not comuns or not a.get("nascimento") or not b.get("nascimento"):
        return False
    dy = abs(int(a["nascimento"][:4]) - int(b["nascimento"][:4]))
    composto = any(len(n.split()) > 1 for n in comuns)
    return dy <= (4 if composto else 1)


def dedup_mesmo_clube(jogadores):
    """Mesma pessoa duas vezes no mesmo clube: registro do TM/EA + registro só do mod.
    Fica o do TM/EA, sai o do mod. Dois tm_id diferentes = pessoas diferentes (não mexe);
    dois registros só do mod também ficam (o mod lista homônimos no mesmo elenco).
    Roda antes de gravar: os ids ainda não existem, nada referencia o registro removido."""
    por = defaultdict(list)
    for j in jogadores:
        if j.get("clube_id") and j.get("origem") == "real" and not j.get("_remover"):
            for n in _nomes(j):
                por[(j["clube_id"], n)].append(j)
    fundidos = []
    for grupo in por.values():
        if len(grupo) < 2:
            continue
        for b in grupo:
            if b.get("_remover"):
                continue
            cands = [a for a in grupo if a is not b and not a.get("_remover") and mesma_pessoa_mod(a, b)]
            if not cands:
                continue
            a = min(cands, key=lambda x: abs(int(x["nascimento"][:4]) - int(b["nascimento"][:4])))
            b["_remover"] = True
            if a.get("mj") is None and b.get("mj") is not None:
                a["mj"] = b["mj"]          # características do mod
            a["conflito"]["duplicata_mod"] = (f"registro do mod fundido ({b['nome_conhecido']}, "
                                              f"nascimento estimado {b['nascimento'][:4]})")
            fundidos.append(f"{a['clube_id']}: {a['nome_conhecido']} {a['nascimento']} (tm {a.get('tm_id')}) "
                            f"<- mod {b['nascimento'][:4]}")
    jogadores[:] = [j for j in jogadores if not j.get("_remover")]
    return fundidos


# ---------------------------------------------------------------- Libertadores / Sul-Americana no formato real
# Formato da Sul-Americana/Libertadores (12/11/2026): campos novos de Formato.Fase já lidos pelo motor
# (competicao/Conmebol.java). Aplicado por cima do formato da pesquisa, para 2026 e 2027.
CONMEBOL_VAGAS_POR_PAIS = {
    "conmebol-libertadores": {
        **{p: {"grupos": 5, "segunda_fase": 1, "primeira_fase": 0} for p in ("ARG",)},
        "BRA": {"grupos": 5, "segunda_fase": 2, "primeira_fase": 0},
        **{p: {"grupos": 2, "segunda_fase": 2, "primeira_fase": 0} for p in ("CHL", "COL")},
        **{p: {"grupos": 2, "segunda_fase": 1, "primeira_fase": 1} for p in ("BOL", "ECU", "PRY", "PER", "URY", "VEN")},
        "_campeoes": "campeões da Libertadores e da Sul-Americana entram na fase de grupos, fora da cota"},
    "conmebol-sul-americana": {
        "ARG": {"grupos": 6}, "BRA": {"grupos": 6},
        **{p: {"primeira_fase": 4} for p in ("CHL", "COL", "BOL", "ECU", "PRY", "PER", "URY", "VEN")}},
}


def ajustar_conmebol(c):
    n = 0
    for rowid, comp, txt in list(c.execute(
            "SELECT rowid, competicao_id, formato FROM formatos WHERE competicao_id IN "
            "('conmebol-libertadores','conmebol-sul-americana')")):
        f = json.loads(txt)
        fases = {fa.get("nome"): fa for fa in f.get("fases", [])}
        if comp == "conmebol-sul-americana":
            f["participantes"] = 44
            pf = fases.get("Primeira fase")
            if pf:
                pf.update(chaveamento="pares", classificam=16, ida_volta=False)
            g = fases.get("Fase de grupos")
            if g:
                g.update(entram=12, entram_externos=4, origem_externos="conmebol-libertadores:eliminados_previa",
                         classificam=2, classificam_direto=1, separar_paises=True)
            po = fases.get("Playoffs (repescagem)") or fases.get("Playoffs das oitavas")
            if po:
                po.pop("entram", None)
                po.update(nome="Playoffs das oitavas", entram_externos=8,
                          origem_externos="conmebol-libertadores:terceiros_grupos", chaveamento="1x8", classificam=8)
        else:
            g = fases.get("Fase de grupos")
            if g:
                g["separar_paises"] = True
        if fases.get("Oitavas de final"):
            fases["Oitavas de final"]["chaveamento"] = "potes"
        for nm in ("Quartas de final", "Semifinal", "Final"):
            if fases.get(nm):
                fases[nm]["chaveamento"] = "chave"
        f["vagas_por_pais"] = CONMEBOL_VAGAS_POR_PAIS[comp]
        f["observacoes_motor"] = ("formato real pedido pelo motor (docs/PEDIDO-DADOS-SULA.md, 12/11/2026): "
                                  "chaveamento por fase, externos vindos da Libertadores, vagas por país 2026")
        c.execute("UPDATE formatos SET formato=? WHERE rowid=?", (json.dumps(f, ensure_ascii=False), rowid))
        n += 1
    print(f"  formatos CONMEBOL ajustados (pedido do motor): {n}")


POS_CODIGOS = {"GOL", "LD", "LE", "ZAG", "VOL", "MC", "MEI", "PE", "PD", "CA"}
LIVRES_PENDENTES = []   # (jogador, item) de passe livre cujo clube de destino só é criado depois


def aplicar_livres_pendentes(c, clubes, por_clube):
    """Passes livres (imprensa) para clubes criados a partir da Wikipedia depois da gravação dos jogadores."""
    n, fora = 0, []
    for j, it in LIVRES_PENDENTES:
        dest = it["destino"]
        if dest not in clubes or j.get("id") is None:
            fora.append(f"{it['nome_conhecido']} -> {dest}")
            continue
        j["clube_id"] = dest
        por_clube[dest].append(j)
        row = c.execute("SELECT conflito FROM jogadores WHERE id=?", (j["id"],)).fetchone()
        conf = json.loads(row[0]) if row and row[0] else {}
        conf["clube"] = (f"transferência 2026 (imprensa, {it.get('data')}, {it.get('tipo')}): sem_clube -> "
                         f"{clubes[dest]['nome']}; {it['fontes'][0]}")
        c.execute("UPDATE jogadores SET clube_id=?, emprestado_de=NULL, conflito=? WHERE id=?",
                  (dest, json.dumps(conf, ensure_ascii=False), j["id"]))
        n += 1
    print(f"  passes livres para clubes criados depois: {n}" + (f"; fora do banco: {fora}" if fora else ""))


def transferencias_noticias(jogadores, clubes, decis, criar_tm, criar_novo, tm_por_id):
    """Transferências brasileiras 2026 conferidas na imprensa (coleta_transferencias_noticias.py ->
    intermediario/transferencias_br_noticias_2026.json; confiabilidade 3). Fecha o que as páginas da Wikipedia
    deixaram de fora (contratações sem link/QID e jogadores de elenco que não estavam no banco).
    Casamento: tm_id; senão nome + nascimento exato; senão nome + clube (origem/destino/dono) com nascimento
    compatível (mesmo ano para registro real; ±1 ano, ou ±4 para nome composto, para registro só do mod).
    Só cria jogador com nome, nascimento (ou idade citada) e posição vindos da fonte; 'nao_confirmado' não entra."""
    dados = comum.ler_json("transferencias_br_noticias_2026.json", {}) or {}
    itens = dados.get("itens", [])
    por_tm = {int(j["tm_id"]): j for j in jogadores if j["tm_id"]}
    por_nome = defaultdict(list)
    for j in jogadores:
        for n in _nomes(j):
            por_nome[n].append(j)
    cont, exemplos, pulados, novos = Counter(), defaultdict(list), [], []

    def casar(it, ctx):
        if it.get("tm_id") and int(it["tm_id"]) in por_tm:
            return por_tm[int(it["tm_id"])], "tm_id"
        ns = {norm(n) for n in [it["nome_conhecido"], it.get("nome")] + it.get("nomes_na_fonte", []) if n} - {""}
        cands = [j for j in {id(j): j for n in ns for j in por_nome.get(n, [])}.values() if not j.get("_remover")]
        if it.get("origem") == "sem_clube":
            # passe livre (noticias_livres_2026.json): só vale o registro real sem clube, e tem de ser único
            # (os livres do mod ainda estão no pseudo-clube "Sem Contrato", desfeito no fim da montagem)
            livres = [j for j in cands if (j["clube_id"] is None or "sem contrato" in norm(
                          (clubes.get(j["clube_id"]) or {}).get("nome") or "")) and j.get("origem") == "real"
                      and (not it.get("tm_id") or not j["tm_id"] or int(j["tm_id"]) == int(it["tm_id"]))]
            return (livres[0], "nome+sem_clube") if len(livres) == 1 else (None, "ambiguo" if livres else "ausente")
        if it.get("tm_id"):
            cands = [j for j in cands if not j["tm_id"] or int(j["tm_id"]) == int(it["tm_id"])]
        nasc, est_ = it.get("nascimento"), it.get("nascimento_estimado")
        if nasc and not est_:
            ex = [j for j in cands if j["nascimento"] == nasc]
            if len(ex) == 1:
                return ex[0], "nome+nascimento"
        composto = any(len(n.split()) > 1 for n in ns)
        ok = []
        for j in cands:
            if not ({j["clube_id"], j.get("emprestado_de")} & ctx):
                continue
            if not nasc or not j["nascimento"]:
                ok.append(j)
                continue
            dy = abs(int(j["nascimento"][:4]) - int(nasc[:4]))
            mod = "nascimento" in j["conflito"]          # nascimento estimado (registro só do mod)
            if dy <= (1 if est_ else 0) or (mod and dy <= (4 if composto else 1)):
                ok.append(j)
        if len(ok) == 1:
            return ok[0], "nome+clube"
        return None, ("ambiguo" if len(ok) > 1 else "ausente")

    for it in itens:
        rot = f"{it['nome_conhecido']} -> {it.get('destino')}"
        if it.get("status") != "confirmado":
            st = it.get("status") or "nao_confirmado"
            cont[st] += 1
            pulados.append(f"{st.replace('_', ' ')}: {rot} ({it.get('obs', '')})")
            continue
        dest, dono = it.get("destino"), it.get("dono")
        if dest and dest not in clubes and it.get("origem") == "sem_clube":
            # clube que a montagem só cria depois (a partir da Wikipedia): aplicado em aplicar_livres_pendentes()
            j, how = casar(it, set())
            if j is not None:
                LIVRES_PENDENTES.append((j, it))
                cont["destino_criado_depois"] += 1
                continue
        if dest and dest not in clubes:
            cont["destino_fora_do_banco"] += 1
            pulados.append(f"destino fora do banco: {rot}")
            dest = None
        if dono and dono not in clubes:
            dono = None
        ctx = {it.get("origem"), it.get("destino"), it.get("dono"), *it.get("clubes_anteriores", [])} - {None}
        j, how = casar(it, ctx)
        fontes = [(u, "imprensa", 3, f"Imprensa: transferência 2026 ({it['nome_conhecido']})") for u in it["fontes"]]
        nota = (f"transferência 2026 (imprensa, {it.get('data')}, {it.get('tipo')}): "
                f"{clubes[it['origem']]['nome'] if it.get('origem') in clubes else (it.get('origem') or '?')} -> "
                f"{clubes[dest]['nome'] if dest else 'fora do banco/sem clube'}; {it['fontes'][0]}")
        if j is None:
            if how == "ambiguo":
                cont["ambiguo"] += 1
                pulados.append(f"ambíguo no banco: {rot}")
                continue
            if dest is None:
                cont["fora_do_banco_sem_registro"] += 1
                continue
            if not (it.get("nascimento") and it.get("posicao") in POS_CODIGOS and it.get("pais")):
                cont["sem_dados_para_criar"] += 1
                pulados.append(f"sem nascimento/posição/país para criar: {rot}")
                continue
            tid = int(it["tm_id"]) if it.get("tm_id") else None
            if tid and tid in tm_por_id:
                nj, como = criar_tm(tid, dest), "TM"
            else:
                nj, como = criar_novo(it, dest), "imprensa"
            nj["emprestado_de"] = dono
            nj["conflito"]["clube"] = "contratação 2026: jogador novo no banco (" + como + "); " + nota
            nj["fontes"] += fontes
            jogadores.append(nj)
            for n in _nomes(nj):
                por_nome[n].append(nj)
            if nj["tm_id"]:
                por_tm[int(nj["tm_id"])] = nj
            novos.append(nj)
            cont["criados_" + como] += 1
            exemplos[dest].append(f"+ {nj['nome_conhecido']} (novo, {it.get('data')}{', empréstimo' if dono else ''})")
            continue
        cont["casados_" + how] += 1
        # nascimento estimado do mod -> data real da fonte
        if it.get("nascimento") and not it.get("nascimento_estimado") and "nascimento" in j["conflito"]:
            j["conflito"].pop("nascimento", None)
            j["conflito"]["nascimento_fonte"] = f"imprensa ({j['nascimento']} estimado -> {it['nascimento']}): {it['fontes'][0]}"
            j["nascimento"] = it["nascimento"]
            cont["nascimento_corrigido"] += 1
        if it.get("tipo") == "aposentou":
            # aposentadoria confirmada na imprensa: sai do banco, como no TM e na Wikipedia
            j["_remover"] = True
            cont["aposentados_removidos"] += 1
            exemplos[j["clube_id"] or "sem_clube"].append(f"- {j['nome_conhecido']} (aposentou, {it.get('data')})")
            continue
        if it.get("tipo") == "ja_no_banco" or (j["clube_id"] == dest and (j.get("emprestado_de") or None) == dono):
            cont["ja_estava_certo"] += 1
            continue
        prev = decis.get(int(j["tm_id"])) if j["tm_id"] else None
        # decisão TM/Wikidata mais nova só manda se levou o jogador a um clube do banco
        # (saída para clube fora do banco no TM + clube encontrado pela imprensa = mesma mudança, mais completa)
        if (prev and not prev.get("so_emprestimo") and it.get("data") and str(prev["data"]) > it["data"]
                and prev.get("clube") is not None and prev.get("clube") != dest):
            cont["tm_wikidata_mais_novo"] += 1
            pulados.append(f"TM/Wikidata mais novo: {rot} ({prev['data']})")
            continue
        antes = j["clube_id"]
        j["clube_id"], j["emprestado_de"] = dest, dono
        j["conflito"]["clube"] = nota
        j["fontes"] += fontes
        if j.get("contrato_ate") and it.get("data") and j["contrato_ate"] < it["data"]:
            j["contrato_ate"] = None
        cont["emprestimos" if dono else ("sem_clube" if dest is None else "trocou_de_clube")] += 1
        if antes:
            exemplos[antes].append(f"- {j['nome_conhecido']} -> {clubes[dest]['nome'] if dest else 'fora do banco'} ({it.get('data')})")
        if dest:
            exemplos[dest].append(f"+ {j['nome_conhecido']} <- {clubes[antes]['nome'] if antes in clubes else 'sem clube'} ({it.get('data')}"
                                  f"{', empréstimo' if dono else ''})")
    return dict(cont), exemplos, pulados, novos


def transferencias_br(jogadores, clubes, qid_cid, decis, criar_tm, criar_wd, tm_por_id):
    """Aplica as transferências brasileiras de 2026 (Wikipedia) por cima do TM/Wikidata.
    Mesma semântica: clube_id = clube atual, emprestado_de = dono, NULL = sem clube/fora do banco, aposentado sai.
    Um movimento só vale se for mais novo (ou do mesmo dia) que a última decisão TM/Wikidata do jogador.
    Devolve (resumo, exemplos por clube, pulados)."""
    dados = comum.ler_json("transferencias_br_2026.json", {}) or {}
    movs, elencos = dados.get("movimentos", []), dados.get("elencos", [])
    nome_br = defaultdict(set)
    for k, v in clubes.items():
        if v["pais"] == "BRA":
            for n in {v["nome"], v["nome_completo"]} - {None}:
                nome_br[nome_chave(n)].add(k)
                nome_br[nome_chave(re.sub(r"[-\s]+[A-Z]{2}$", "", n))].add(k)

    def clube_de(q, nome, band):
        if q and q in qid_cid:
            return qid_cid[q]
        if nome and band in (None, "BRA") and not RE_BASE.search(nome):
            cs = nome_br.get(nome_chave(nome)) or set()
            if len(cs) == 1:
                return next(iter(cs))
        return None

    por_tm = {int(j["tm_id"]): j for j in jogadores if j["tm_id"]}
    por_nome = defaultdict(list)

    def indexar(j):
        for n in {j["nome"], j["nome_conhecido"]}:
            if n:
                por_nome[norm(n)].append(j)
    for j in jogadores:
        indexar(j)

    def casar(nome, wd, titulo, ctx):
        wd = wd or {}
        if wd.get("tm") and str(wd["tm"]).isdigit() and int(wd["tm"]) in por_tm:
            return por_tm[int(wd["tm"])], "tm_id"
        ns = {norm(nome)}
        if titulo:
            ns.add(norm(re.sub(r"\s*\(.*\)$", "", titulo)))
        ns |= {norm(wd.get("en")), norm(wd.get("pt"))}
        ns.discard("")
        cands = list({id(j): j for n in ns for j in por_nome.get(n, [])}.values())
        if wd.get("tm"):
            cands = [j for j in cands if not j["tm_id"]]     # tem TM id e não é ninguém do banco com TM id
        nasc = wd.get("nasc")
        no_ctx = [j for j in cands if j["clube_id"] in ctx or j.get("emprestado_de") in ctx]
        if nasc:
            ex = [j for j in cands if j["nascimento"] == nasc]
            if len(ex) == 1:
                return ex[0], "nome+nascimento"
            # ±1 ano só para nascimento estimado (registro só do mod); data real diferente = outra pessoa
            pr = [j for j in cands if j["nascimento"] and "nascimento" in j["conflito"]
                  and abs(int(j["nascimento"][:4]) - int(nasc[:4])) <= 1]
            pc = [j for j in pr if j["clube_id"] in ctx or j.get("emprestado_de") in ctx]
            if len(pc) == 1:
                return pc[0], "nome+ano+clube"
            mononimo = len(norm(nome).split()) == 1
            if len(pr) == 1 and not mononimo and not ex:
                return pr[0], "nome+ano"
            return None, ("ambiguo" if len(pr) > 1 or len(ex) > 1 else "ausente")
        if len(no_ctx) == 1:
            return no_ctx[0], "nome+clube"
        return None, ("ambiguo" if len(no_ctx) > 1 else "ausente")

    ev = defaultdict(list)
    alvo = {}
    pulados = []
    cont = Counter()
    for mv in movs:
        d = mv.get("data")
        if not d or not (BR_INI <= d <= ELENCOS_ATE):
            cont["fora_da_janela_ou_sem_data"] += 1
            continue
        if mv.get("base"):
            cont["base"] += 1
            continue
        art = mv["clube"]
        outro = clube_de(mv.get("outro_qid"), mv.get("outro_nome"), mv.get("outro_pais"))
        if outro == art:
            outro = None
        dirc, tipo = mv["direcao"], mv["tipo"]
        if dirc == "in":
            dest, dono = art, (outro if tipo == "emprestimo" else None)
        elif tipo in ("aposentou", "livre"):
            dest, dono = None, None
        elif tipo == "emprestimo":
            dest, dono = outro, (art if outro else None)
        else:
            dest, dono = outro, None
        if tipo == "emprestimo" and (mv.get("emprestimo_encerrado") or (mv.get("fim") and d <= mv["fim"] < ELENCOS_ATE)):
            # empréstimo já terminou: volta ao dono
            if dirc == "in" and outro is None:
                cont["emprestimo_encerrado_sem_dono"] += 1
                continue
            dest, dono = (outro, None) if dirc == "in" else (art, None)
            if mv.get("fim") and d <= mv["fim"] < ELENCOS_ATE and not mv.get("emprestimo_encerrado"):
                d = mv["fim"]          # venceu: volta ao dono no fim do empréstimo (riscado = encerrado antes, data desconhecida)
            tipo = "retorno"
        ctx = {art, outro, dono} - {None}
        j, how = casar(mv["jogador"], mv.get("wd"), mv.get("jogador_titulo"), ctx)
        key = id(j) if j is not None else (("q", mv["jogador_qid"]) if mv.get("jogador_qid") else ("n", norm(mv["jogador"]), art))
        if j is not None:
            alvo[key] = j
        ev[key].append({"dest": dest, "dono": dono, "data": d, "dir": dirc, "tipo": tipo, "art": art, "mv": mv,
                        # entrada (artigo do clube de destino) vale mais; pt cita a imprensa linha a linha (desempate)
                        "peso": (2 if dirc == "in" else 1) + (0.1 if mv["lang"] == "pt" else 0), "como": how})

    exemplos = defaultdict(list)
    tocados = set()
    novos = []

    def escolher(es):
        ult = max(e["data"] for e in es)
        es = [e for e in es if e["data"] == ult]
        if any(e["tipo"] != "retorno" for e in es):
            es = [e for e in es if e["tipo"] != "retorno"]   # volta de empréstimo e nova saída no mesmo dia
        votos = Counter()
        for e in es:
            votos[(e["dest"], e["dono"], e["tipo"] == "aposentou")] += e["peso"]
        (dest, dono, apos), _ = votos.most_common(1)[0]
        e = next(e for e in reversed(es) if (e["dest"], e["dono"], e["tipo"] == "aposentou") == (dest, dono, apos))
        return e, [x for x in es if (x["dest"], x["dono"]) != (dest, dono)]

    def nota(j, e, antes):
        mv = e["mv"]
        j["conflito"]["clube"] = (f"transferência 2026 (Wikipedia {mv['lang']}: {mv['artigo']}, {e['data']}, {e['tipo']}): "
                                  f"{clubes[antes]['nome'] if antes in clubes else (antes or 'sem clube')} -> "
                                  f"{clubes[e['dest']]['nome'] if e['dest'] else (mv.get('outro_nome') or 'sem clube')}")
        j["fontes"].append((mv["url"], "wikipedia", 3, f"Wikipedia ({mv['lang']}): {mv['artigo']}"))

    for key, es in ev.items():
        e, contra = escolher(es)
        mv = e["mv"]
        if contra:
            cont["conflito_mesmo_dia"] += 1
            pulados.append(f"conflito {mv['jogador']} {e['data']}: escolhido {e['dest']} (de {[x['dest'] for x in contra]})")
        j = alvo.get(key)
        if j is None:
            if e["dest"] is None or e["tipo"] == "aposentou" or e["dir"] != "in" and e["dest"] not in clubes:
                cont["sem_registro_saiu_do_banco"] += 1
                continue
            wd = mv.get("wd") or {}
            outro_n = mv.get("outro_nome") or ""
            if RE_BASE.search(outro_n) or re.search(r"youth|academy|base", outro_n, re.I):
                cont["promovido_da_base_sem_registro"] += 1
                pulados.append(f"base sem registro: {mv['jogador']} ({e['dest']})")
                continue
            tid = int(wd["tm"]) if wd.get("tm") and str(wd["tm"]).isdigit() else None
            nj = None
            if tid and tid in tm_por_id:
                nj = criar_tm(tid, e["dest"])
                how = "TM"
            elif (wd.get("nasc") and "1985" <= wd["nasc"] <= "2011" and wd.get("futebolista")
                  and (wd.get("pais") or mv.get("jogador_pais")) and pos_wiki(mv.get("pos"), *wd.get("pos", []))):
                # só futebolista (P106) em idade de jogar: link para homônimo antigo não vira contratação
                nj = criar_wd(mv, e["dest"])
                how = "Wikidata"
            if nj is None:
                cont["novo_sem_dados_reais"] += 1
                pulados.append(f"sem dados reais para criar: {mv['jogador']} -> {e['dest']} ({e['data']}, {mv['url']})")
                continue
            nj["emprestado_de"] = e["dono"]
            nj["conflito"]["clube"] = (f"contratação 2026 (Wikipedia {mv['lang']}: {mv['artigo']}, {e['data']}): jogador novo "
                                       f"no banco ({how})")
            nj["fontes"].append((mv["url"], "wikipedia", 3, f"Wikipedia ({mv['lang']}): {mv['artigo']}"))
            jogadores.append(nj)
            indexar(nj)
            if nj["tm_id"]:
                por_tm[int(nj["tm_id"])] = nj
            tocados.add(id(nj))
            novos.append(nj)
            cont["contratacoes_novas_" + how] += 1
            exemplos[e["dest"]].append(f"+ {nj['nome_conhecido']} (novo, {e['data']}{', empréstimo' if e['dono'] else ''})")
            continue
        tocados.add(id(j))
        prev = decis.get(int(j["tm_id"])) if j["tm_id"] else None
        if prev and not prev.get("so_emprestimo") and str(prev["data"]) > e["data"]:
            cont["tm_wikidata_mais_novo"] += 1
            continue
        antes = j["clube_id"]
        if e["tipo"] == "aposentou":
            j["_remover"] = True
            cont["aposentados_removidos"] += 1
            exemplos[antes].append(f"- {j['nome_conhecido']} (aposentou, {e['data']})")
            continue
        if antes == e["dest"] and (j.get("emprestado_de") or None) == e["dono"]:
            cont["ja_estava_certo"] += 1
            continue
        j["clube_id"], j["emprestado_de"] = e["dest"], e["dono"]
        nota(j, e, antes)
        if j.get("contrato_ate") and j["contrato_ate"] < e["data"]:
            j["contrato_ate"] = None
        cont["emprestimos" if e["dono"] else ("livre" if e["dest"] is None else "outro_clube")] += 1
        if antes != e["dest"]:
            if antes:
                exemplos[antes].append(f"- {j['nome_conhecido']} -> {clubes[e['dest']]['nome'] if e['dest'] else (mv.get('outro_nome') or 'sem clube')} ({e['data']}{', empréstimo' if e['dono'] else ''})")
            if e["dest"]:
                exemplos[e["dest"]].append(f"+ {j['nome_conhecido']} <- {clubes[antes]['nome'] if antes in clubes else 'sem clube/fora'} ({e['data']}{', empréstimo' if e['dono'] else ''})")

    # elencos atuais (Wikipedia) para quem não teve movimento: chegou ao clube do elenco / está emprestado
    # Guardas (só aqui, não nos movimentos datados): o link do elenco pode cair num homônimo famoso
    # ([[Alisson]] -> Alisson Becker no elenco do Criciúma).
    valor_max = defaultdict(int)
    for j in jogadores:
        if j["clube_id"] and j.get("valor"):
            valor_max[j["clube_id"]] = max(valor_max[j["clube_id"]], int(j["valor"]))

    def homonimo_em(j, clube):
        """Outro jogador do banco, com o mesmo nome e que não é a mesma pessoa, já no clube."""
        for n in _nomes(j):
            for o in por_nome.get(n, []):
                if o is j or o.get("_remover") or clube not in (o["clube_id"], o.get("emprestado_de")):
                    continue
                if not (mesma_pessoa_mod(j, o) or mesma_pessoa_mod(o, j)):
                    return o
        return None

    for el in elencos:
        if not el.get("data") or el["data"] < BR_ELENCO_MIN:
            cont["elencos_antigos_ignorados"] += 1
            continue
        cl = el["clube"]
        for p in el["jogadores"]:
            if not p.get("qid"):
                continue
            j, how = casar(p["nome"], p.get("wd"), p.get("titulo"), {cl})
            if j is None:
                if how == "ausente":
                    cont["elenco_fora_do_banco"] += 1
                    wdp = p.get("wd") or {}
                    pulados.append(f"elenco fora do banco: {p['nome']} ({cl}; titulo {p.get('titulo')}; qid {p.get('qid')}; "
                                   f"nasc {wdp.get('nasc')}; tm {wdp.get('tm')}; {el['fonte']})")
                continue
            if id(j) in tocados:
                continue
            if p.get("emprestado_para_titulo"):
                dest, dono = clube_de(p.get("emprestado_para_qid"), p.get("emprestado_para_nome"), None), cl
                if dest is None:
                    continue
            else:
                dest = cl
                dono = clube_de(p.get("emprestado_de_qid"), p.get("emprestado_de_nome"), None) if p.get("emprestado_de_titulo") else None
            if j["clube_id"] == dest and (j.get("emprestado_de") or None) == dono:
                continue
            prev = decis.get(int(j["tm_id"])) if j["tm_id"] else None
            if prev and not prev.get("so_emprestimo") and str(prev["data"]) > el["data"]:
                cont["elenco_tm_mais_novo"] += 1
                continue
            if j["clube_id"] == dest and dono is None and j.get("emprestado_de"):
                continue     # elenco não diz nada sobre o empréstimo
            if cl not in (j["clube_id"], j.get("emprestado_de")):
                o = homonimo_em(j, cl)
                if o is not None:
                    cont["homonimo_no_clube"] += 1
                    pulados.append(f"elenco {cl}: homônimo já no clube, {j['nome_conhecido']} (tm {j['tm_id']}, "
                                   f"{j['nascimento']}, {j['clube_id']}) não foi movido; fica {o['nome_conhecido']} "
                                   f"({o['nascimento']})")
                    print("    " + pulados[-1])
                    continue
            vmax = valor_max.get(dest, 0)
            if j["clube_id"] and j["clube_id"] != dest and j.get("valor") and vmax and int(j["valor"]) > 4 * vmax:
                cont["valor_incompativel"] += 1
                pulados.append(f"elenco {cl}: valor incompatível, {j['nome_conhecido']} (tm {j['tm_id']}, "
                               f"{int(j['valor']):,} em {j['clube_id']}) não foi para {dest} (maior valor {vmax:,})")
                print(f"    elenco: pulado {j['nome_conhecido']} ({j['clube_id']} -> {dest}): valor {int(j['valor']):,} > 4x {vmax:,}")
                continue
            antes = j["clube_id"]
            tocados.add(id(j))
            j["clube_id"], j["emprestado_de"] = dest, dono
            j["conflito"]["clube"] = (f"elenco atual 2026 (Wikipedia: {el['fonte']}, editado {el['data']}): "
                                      f"{clubes[antes]['nome'] if antes in clubes else (antes or 'sem clube')} -> {clubes[dest]['nome']}")
            j["fontes"].append((el["fonte"], "wikipedia", 3, "Wikipedia: elenco atual"))
            cont["elenco_mudou_clube"] += 1
            if antes != dest:
                if antes:
                    exemplos[antes].append(f"- {j['nome_conhecido']} -> {clubes[dest]['nome']} (elenco {el['data']}{', empréstimo' if dono else ''})")
                exemplos[dest].append(f"+ {j['nome_conhecido']} <- {clubes[antes]['nome'] if antes in clubes else 'sem clube'} (elenco {el['data']}{', empréstimo' if dono else ''})")
    resumo = {"movimentos_lidos": len(movs), "jogadores_com_movimento": len(ev), **cont}
    return resumo, exemplos, pulados, novos


# ---------------------------------------------------------------- força
# ---------------------------------------------------------------- estaduais 2026 (coleta/estaduais_2026.json)
ORDINAL = {2: "Segunda", 3: "Terceira", 4: "Quarta", 5: "Quinta", 6: "Sexta", 7: "Sétima"}


def nome_divisao_abaixo(nome_ultima, nivel, uf_nome):
    """Nome da divisão fictícia abaixo da última do estado: segue a série da de cima
    (Série B → Série C, A3 → A4, Segunda Divisão → Terceira Divisão); senão, o ordinal do nível."""
    n = nome_ultima or ""
    m = re.search(r"Série ([A-Z])(\d)?\b", n)
    if m and not m.group(2) and m.group(1) < "Z":
        return f"Campeonato {uf_nome} Série {chr(ord(m.group(1)) + 1)}"
    if m and m.group(1) == "B" and m.group(2) == "2":   # RJ: depois da B2 vem a Série C
        return f"Campeonato {uf_nome} Série C"
    if m and m.group(2):
        return f"Campeonato {uf_nome} Série {m.group(1)}{int(m.group(2)) + 1}"
    for k, v in ORDINAL.items():
        if re.search(rf"{v} Divisão|{k}ª Divisão", n) and k + 1 in ORDINAL:
            return f"Campeonato {uf_nome} {ORDINAL[k + 1]} Divisão"
    if "Terceirona" in n:
        return f"Campeonato {uf_nome} Quarta Divisão"
    return f"Campeonato {uf_nome} {ORDINAL.get(nivel, str(nivel) + 'ª')} Divisão"


def fases_estadual(n, cfg=None):
    """Formato padrão de uma divisão estadual sem regulamento com fonte: até 16 clubes, turno único e
    mata-mata entre os 4/8 primeiros; mais que isso, grupos de ~6 (turno e returno) e mata-mata de 8/16."""
    # o motor lê ida_volta como booleano (uma lista fazia o formato inteiro ser descartado na carga);
    # ida e volta se o mod diz ida e volta na maioria das fases finais
    iv = sum(x == 2 for x in (cfg or {}).get("finaisIdaVolta", [1, 1, 2])) >= 2
    if n <= 16:
        return [{"nome": "Primeira fase", "tipo": "pontos_corridos", "turnos": 1, "classificam": 8 if n >= 12 else 4},
                {"nome": "Mata-mata", "tipo": "mata_mata", "ida_volta": iv}]
    g, cl = (4, 2) if n <= 20 else (4, 4) if n <= 40 else (8, 2)   # 8 ou 16 no mata-mata
    tam = n // g
    return [{"nome": "Fase de grupos", "tipo": "grupos", "grupos": g, "turnos": 2 if tam <= 6 else 1, "classificam": cl},
            {"nome": "Mata-mata", "tipo": "mata_mata", "ida_volta": iv}]


def fundir_clubes(c, fusoes, fotos_dir):
    """Junta clubes duplicados no banco já gravado (estaduais_2026.json, 'fusoes'): fica um id só; os
    jogadores reais do removido vão para o que fica (o mesmo nome+ano que já está lá é descartado), os
    gerados do removido somem; competições, carreira, campeões, fontes e técnicos passam para o que fica."""
    feitos = []
    for fu in fusoes:
        velho, novo = fu["remover"], fu["fica"]
        if not c.execute("SELECT 1 FROM clubes WHERE id=?", (velho,)).fetchone():
            continue
        if not c.execute("SELECT 1 FROM clubes WHERE id=?", (novo,)).fetchone():
            print(f"  AVISO: fusão {velho} -> {novo}: {novo} não existe")
            continue
        ja = {(norm(n), (nasc or "")[:4]) for n, nasc in
              c.execute("SELECT nome_conhecido, nascimento FROM jogadores WHERE clube_id=?", (novo,))}
        movidos = apagados = 0
        for jid, n, nasc, orig in c.execute("SELECT id, nome_conhecido, nascimento, origem FROM jogadores WHERE clube_id=?",
                                            (velho,)).fetchall():
            ano = (nasc or "")[:4]
            dup = any(k == norm(n) and (not a.isdigit() or not ano.isdigit() or abs(int(a) - int(ano)) <= 1) for k, a in ja)
            if orig != "real" or dup:
                c.execute("DELETE FROM jogadores WHERE id=?", (jid,))
                c.execute("DELETE FROM jogador_fontes WHERE jogador_id=?", (jid,))
                c.execute("DELETE FROM kings_elencos WHERE jogador_id=?", (jid,))
                f_ = Path(fotos_dir) / f"{jid}.png"
                if f_.exists():
                    f_.unlink()
                apagados += 1
            else:
                c.execute("UPDATE jogadores SET clube_id=? WHERE id=?", (novo, jid))
                movidos += 1
        c.execute("UPDATE jogadores SET emprestado_de=? WHERE emprestado_de=?", (novo, velho))
        tem_tec = c.execute("SELECT 1 FROM tecnicos WHERE clube_id=?", (novo,)).fetchone()
        c.execute(f"UPDATE tecnicos SET clube_id={'NULL' if tem_tec else '?'} WHERE clube_id=?",
                  (velho,) if tem_tec else (novo, velho))
        for comp, temp in c.execute("SELECT competicao_id, temporada FROM ligas_clubes WHERE clube_id=?", (velho,)).fetchall():
            c.execute("INSERT OR IGNORE INTO ligas_clubes VALUES (?,?,?)", (comp, temp, novo))
        c.execute("DELETE FROM ligas_clubes WHERE clube_id=?", (velho,))
        c.execute("UPDATE tabelas_iniciais SET clube_id=? WHERE clube_id=?", (novo, velho))
        c.execute("UPDATE carreira SET clube_id=? WHERE clube_id=?", (novo, velho))
        c.execute("DELETE FROM onze_historico WHERE clube_id=?", (velho,))
        c.execute("UPDATE campeoes_historicos SET campeao_clube_id=? WHERE campeao_clube_id=?", (novo, velho))
        c.execute("UPDATE campeoes_historicos SET vice_clube_id=? WHERE vice_clube_id=?", (novo, velho))
        c.execute("INSERT OR IGNORE INTO clube_fontes SELECT ?, fonte_id FROM clube_fontes WHERE clube_id=?", (novo, velho))
        c.execute("DELETE FROM clube_fontes WHERE clube_id=?", (velho,))
        for u in fu.get("fontes") or []:
            fid = c.execute("SELECT id FROM fontes WHERE url=?", (u,)).fetchone()
            fid = fid[0] if fid else c.execute("INSERT INTO fontes (url,tipo,titulo,acessado_em,confiabilidade) VALUES (?,?,?,?,?)",
                                               (u, "imprensa", "clube duplicado (estaduais_2026.json)", HOJE, 3)).lastrowid
            c.execute("INSERT OR IGNORE INTO clube_fontes VALUES (?,?)", (novo, fid))
        for cid_, riv in c.execute("SELECT id, rival_ids FROM clubes WHERE rival_ids LIKE ?", (f'%"{velho}"%',)).fetchall():
            l = list(dict.fromkeys(novo if x == velho else x for x in json.loads(riv) if x != cid_))
            c.execute("UPDATE clubes SET rival_ids=? WHERE id=?", (json.dumps(l), cid_))
        c.execute("DELETE FROM clubes WHERE id=?", (velho,))
        feitos.append({"remover": velho, "fica": novo, "jogadores_movidos": movidos, "jogadores_descartados": apagados})
    return feitos


def ajustar_modelo_valor(amostra):
    """Regressão linear: overall EA ~ log10(valor) + juventude + veterania (ajustada nos casados com o EA)."""
    if len(amostra) < 500:
        return np.array([55 - 11 * 5, 11.0, -1.0, 0.8])
    X = np.array([[1, math.log10(v), max(0, 23 - a), max(0, a - 29)] for v, a, _ in amostra])
    y = np.array([o for _, _, o in amostra])
    coef, *_ = np.linalg.lstsq(X, y, rcond=None)
    return coef


def forca_por_valor(coef, valor, idade):
    f = coef[0] + coef[1] * math.log10(max(valor, 10_000)) + coef[2] * max(0, 23 - idade) + coef[3] * max(0, idade - 29)
    return int(round(min(94, max(38, f))))


def potencial_de(forca, idade):
    if idade <= 20:
        p = forca + (24 - idade) * 2.5
    elif idade <= 24:
        p = forca + (26 - idade) * 1.5
    else:
        p = forca
    return int(round(min(97, max(forca, p))))


def idade_em(nasc):
    try:
        d = dt.date.fromisoformat(nasc[:10])
    except (TypeError, ValueError):
        return None
    return INICIO.year - d.year - ((INICIO.month, INICIO.day) < (d.month, d.day))


def pos_mod(j):
    p, lado = j.get("pos"), j.get("lado")
    car = set(c for c in j.get("car") or [] if c is not None)
    if p == 0:
        return "GOL"
    if p == 1:
        return "LE" if lado == 1 else "LD"
    if p == 2:
        return "ZAG"
    if p == 3:
        if car & {7, 10} and not car & {4, 8, 9}:
            return "VOL"
        if car & {4, 8, 9}:
            return "MEI"
        return "MC"
    if p == 4:
        if car & {8, 13} and 5 not in car:
            return "PE" if lado == 1 else "PD"
        return "CA"
    return "MC"


def caracteristicas_ea(r):
    if r is None:
        return None
    if str(r.player_positions).startswith("GK"):
        v = {"reflexo": r.goalkeeping_reflexes, "colocacao": r.goalkeeping_positioning,
             "saida_do_gol": r.goalkeeping_speed, "defesa_de_penalti": r.goalkeeping_diving}
    else:
        v = {"velocidade": r.pace, "finalizacao": r.shooting, "passe": r.passing, "drible": r.dribbling,
             "desarme": r.defending, "resistencia": r.physic, "cabeceio": r.attacking_heading_accuracy,
             "cruzamento": r.attacking_crossing, "armacao": r.mentality_vision, "marcacao": r.defending_marking_awareness}
    v = {k: x for k, x in v.items() if isinstance(x, (int, float)) and not math.isnan(x)}
    return [k for k, _ in sorted(v.items(), key=lambda kv: -kv[1])[:2]] or None


def atributos_ea(r):
    if r is None:
        return None
    def g(x):
        return None if x is None or (isinstance(x, float) and math.isnan(x)) else int(x)
    gk = [r.goalkeeping_diving, r.goalkeeping_handling, r.goalkeeping_kicking, r.goalkeeping_positioning,
          r.goalkeeping_reflexes]
    gk = [x for x in gk if isinstance(x, (int, float)) and not math.isnan(x)]
    return {"ritmo": g(r.pace), "finalizacao": g(r.shooting), "passe": g(r.passing), "drible": g(r.dribbling),
            "defesa": g(r.defending), "fisico": g(r.physic), "goleiro": int(sum(gk) / len(gk)) if gk else None}


# ---------------------------------------------------------------- clubes
def sigla_de(nome, usadas):
    ws = [w for w in norm(nome).upper().split() if w.lower() not in PALAVRAS_VAZIAS or len(norm(nome).split()) == 1]
    base = ws[0] if ws else norm(nome).upper().replace(" ", "")
    cands = [base[:3]]
    if len(ws) >= 2:
        cands.append(ws[0][:2] + ws[1][0])
        cands.append(ws[0][0] + ws[1][:2])
    if len(base) > 3:
        cands += [base[0] + base[2:4], base[:2] + base[3]]
    for c in cands:
        if len(c) == 3 and c not in usadas:
            return c
    return cands[0][:3].ljust(3, "X")


def main():
    t0 = dt.datetime.now()
    b = Banco()
    c = b.c
    paises_mod = comum.ler_json("mod_paises.json")
    times = comum.ler_json("mod_times.json")
    ligas_mod = comum.ler_json("mod_ligas.json")
    # nomes do mod com digitação errada ('Atlético MIneiro', 'SIlvinho', 'Internazionale ', '1º DIvisão')
    for t in times:
        for k in ("nome", "estadio", "cidade"):
            if isinstance(t.get(k), str):
                t[k] = normalizar.limpar_nome(t[k])
        for j in t.get("jogadores") or []:
            if isinstance(j.get("nome"), str):
                j["nome"] = normalizar.limpar_nome(j["nome"])
    for divs_ in (ligas_mod or {}).values():
        for d_ in divs_ or []:
            if isinstance(d_.get("nomeDivisao"), str):
                d_["nomeDivisao"] = normalizar.limpar_nome(d_["nomeDivisao"])
    wiki = comum.ler_json("wiki_participantes.json", {}) or {}
    # divisões inferiores dos estaduais (coleta_estaduais_inferiores.py): est-<uf>-2, est-<uf>-3...
    est_inf = comum.ler_json("wiki_estaduais_inf.json", {}) or {}
    wiki.update(est_inf)
    copa =comum.ler_json("wiki_copa2026.json", {}) or {}
    pesq = ler_pesquisa_todos()
    print("carregando transfermarkt e EA...")
    tmp, tmc, comp_pais = carregar_tm()
    ea_idx = carregar_ea()
    print(f"  TM jogadores {len(tmp)}, clubes {len(tmc)}; EA datas {len(ea_idx)}")
    tm_fonte = b.fonte(TM_URL, "base_dados", 4, "transfermarkt-datasets (dados do Transfermarkt, CC0)", "CC0")
    ea_fonte = b.fonte(EA_URL, "base_dados", 3, "EA SPORTS FC 26 (dados do SoFIFA via EAFC26-DataHub)", "uso_pessoal")

    # ---------------- países
    ranking = {}
    nt = pd.read_csv(TM / "national_teams.csv.gz")
    for r in nt.itertuples():
        i3 = tm_iso(r.country_name) or tm_iso(r.name)
        if i3 and not pd.isna(r.fifa_ranking):
            ranking[i3] = int(r.fifa_ranking)
    for s in pesq["selecoes"]:
        if s.get("pais") and s.get("ranking_fifa"):
            ranking[s["pais"]] = int(s["ranking_fifa"])
    paises = {}
    for k, v in paises_mod.items():
        i3 = v["iso"]
        if not i3 or i3 in paises:
            continue
        band = ASSETS / "bandeiras" / f"{i3}.png"
        paises[i3] = v
        c.execute("INSERT INTO paises VALUES (?,?,?,?,?,?,?,?)",
                  (i3, v["nome"], confed(i3), calendario_pais(i3),
                   f"assets/bandeiras/{i3}.png" if band.exists() else None, ranking.get(i3), None, None))

    # ---------------- clubes do mod (dedup: mesmo nome+país -> arquivo mais novo)
    times.sort(key=lambda t: t["data_arquivo"], reverse=True)
    vistos, mod = set(), []
    for t in times:
        k = (norm(t["nome"]), t["pais"], t["uf"])
        if not t["pais"] or k in vistos:
            continue
        vistos.add(k)
        t["nivel"] = int(min(20, max(1, t["nivel"] or 8)))
        t["reputacao"] = int(min(5, max(0, t["reputacao"] or 0)))
        mod.append(t)
    # mesma equipe em 2 arquivos com nomes diferentes (>= 10 jogadores em comum) -> fica o mais novo
    por_pais = defaultdict(list)
    for i, t in enumerate(mod):
        por_pais[t["pais"]].append(i)
    descartar = set()
    for pais, ids in por_pais.items():
        inv = defaultdict(list)
        for i in ids:
            for j in mod[i]["jogadores"]:
                inv[norm(j["nome"])].append(i)
        par = Counter()
        for n, lst in inv.items():
            if len(lst) < 6:
                for a in range(len(lst)):
                    for bb in range(a + 1, len(lst)):
                        par[(lst[a], lst[bb])] += 1
        for (a, bb), n in par.items():
            if n >= 10 and a not in descartar and bb not in descartar:
                descartar.add(max(a, bb))   # lista ordenada por data desc: índice maior = mais velho
    mod = [t for i, t in enumerate(mod) if i not in descartar]
    print(f"  times do mod: {len(times)} arquivos -> {len(mod)} clubes ({len(descartar)} duplicados por elenco)")

    # ---------------- TM: elencos atuais
    tmp["pais_j"] = tmp.country_of_citizenship.map(tm_iso)
    atuais = tmp[(tmp.last_season >= 2025) & tmp.current_club_id.notna()]
    tm_por_clube = defaultdict(list)
    for r in atuais.itertuples():
        tm_por_clube[int(r.current_club_id)].append(r)
    tm_por_id = {int(r.player_id): r for r in tmp.itertuples()}
    tmc_atual = tmc[tmc.last_season >= 2025]
    print(f"  clubes TM 2025/26: {len(tmc_atual)}, jogadores atuais {len(atuais)}")

    # casamento TM <-> mod por sobreposição de elenco + nome
    mod_nomes = [set(norm(j["nome"]) for j in t["jogadores"]) for t in mod]
    mod_idx_pais = defaultdict(list)
    for i, t in enumerate(mod):
        mod_idx_pais[t["pais"]].append(i)
    cand = []
    for r in tmc_atual.itertuples():
        pais = comp_pais.get(r.domestic_competition_id)
        paises_c = ["USA", "CAN"] if r.domestic_competition_id == "MLS1" else [pais]
        elenco = tm_por_clube.get(int(r.club_id), [])
        nomes = set()
        for p in elenco:
            nomes.add(norm(p.name))
        for pc in paises_c:
            for i in mod_idx_pais.get(pc, []):
                ov = len(nomes & mod_nomes[i])
                ns = max(fuzz.token_set_ratio(nome_chave(r.name), nome_chave(mod[i]["nome"])),
                         fuzz.ratio(nome_chave(r.name), nome_chave(mod[i]["nome"])))
                if ov >= 3 or (ov >= 1 and ns >= 80) or ns >= 95:
                    cand.append((ov * 10 + ns / 10, int(r.club_id), i))
    cand.sort(reverse=True)
    tm2mod, mod2tm = {}, {}
    for s, tid, i in cand:
        if tid in tm2mod or i in mod2tm:
            continue
        tm2mod[tid], mod2tm[i] = i, tid
    print(f"  clubes TM casados com o mod: {len(tm2mod)} de {len(tmc_atual)}")

    # ---------------- registro de clubes
    clubes: dict[str, dict] = {}
    ids_usados = set()

    def novo_id(nome, pais, uf):
        base = slug(nome) or "clube"
        for cand_id in (base, f"{base}-{(uf or pais).lower()}", f"{base}-{pais.lower()}"):
            if cand_id not in ids_usados:
                ids_usados.add(cand_id)
                return cand_id
        n = 2
        while f"{base}-{n}" in ids_usados:
            n += 1
        ids_usados.add(f"{base}-{n}")
        return f"{base}-{n}"

    mod_cid = {}
    # ordem: maior nível primeiro, para o clube grande ganhar o slug "limpo"
    for i in sorted(range(len(mod)), key=lambda i: (-(mod[i]["nivel"] or 0), -(mod[i]["reputacao"] or 0))):
        t = mod[i]
        cid = novo_id(t["nome"], t["pais"], t["uf"])
        mod_cid[i] = cid
        tmid = mod2tm.get(i)
        r = tmc[tmc.club_id == tmid].iloc[0] if tmid else None
        clubes[cid] = {"id": cid, "nome": t["nome"], "nome_completo": (r["name"] if r is not None else None),
                       "pais": t["pais"], "uf": t["uf"], "cor1": t["cor1"], "cor2": t["cor2"],
                       "estadio": t["estadio"] or (r["stadium_name"] if r is not None else None),
                       "capacidade": t["capacidade"] or (int(r["stadium_seats"]) if r is not None and not pd.isna(r["stadium_seats"]) else None),
                       "nivel": t["nivel"], "rep": t["reputacao"], "tm_id": tmid, "mod": t,
                       "fonte_mod": f"file:///C:/Brasfoot22-23/teams/{t['arquivo']}"}
    tm_cid = {mod2tm[i]: mod_cid[i] for i in mod2tm}
    for r in tmc_atual.itertuples():
        if int(r.club_id) in tm_cid:
            continue
        pais = comp_pais.get(r.domestic_competition_id) or "USA"
        nome = re.sub(r"^(FC|CF|SC|AC|CD|CA|AS|SK|FK|SV|PFC|NK|KV|KRC|RSC|R\.)\s+|\s+(FC|CF|SC|SAD|S\.A\.D\.|AFC)$", "",
                      r.name).strip()
        cid = novo_id(nome, pais, None)
        tm_cid[int(r.club_id)] = cid
        clubes[cid] = {"id": cid, "nome": nome, "nome_completo": r.name, "pais": pais, "uf": None, "cor1": None,
                       "cor2": None, "estadio": r.stadium_name,
                       "capacidade": None if pd.isna(r.stadium_seats) else int(r.stadium_seats),
                       "nivel": None, "rep": None, "tm_id": int(r.club_id), "mod": None, "fonte_mod": None}
    print(f"  clubes: {len(clubes)}")

    # ---------------- jogadores
    jogadores: list[dict] = []
    tm_usados = set()

    def reg_tm(r, cid, mj=None, arquivo=None):
        nasc = r.date_of_birth[:10] if isinstance(r.date_of_birth, str) else None
        ea = casar_ea(ea_idx, nasc, r.name, mj["nome"] if mj else None)
        pos = POS_TM.get(r.sub_position) or POS_GERAL.get(r.position) or (pos_mod(mj) if mj else "MC")
        sec = None
        if ea is not None and isinstance(ea.player_positions, str):
            ps = [POS_EA.get(x.strip()) for x in ea.player_positions.split(",")]
            sec = [p for p in dict.fromkeys(ps) if p and p != pos][:2] or None
        valor = None if pd.isna(r.market_value_in_eur) else int(r.market_value_in_eur)
        j = {"nome": r.name, "nome_conhecido": (mj["nome"] if mj else r.name), "nascimento": nasc,
             "pais": r.pais_j, "pais2": None, "posicao": pos, "posicoes_sec": sec,
             "pe": {"right": "D", "left": "E", "both": "A"}.get(r.foot), "altura": None if pd.isna(r.height_in_cm) else int(r.height_in_cm),
             "clube_id": cid, "contrato_ate": r.contract_expiration_date[:10] if isinstance(r.contract_expiration_date, str) else None,
             "camisa": None, "valor": valor, "ea": ea, "tm_id": int(r.player_id), "mj": mj,
             "fontes": [(r.url, "base_dados", 4, "Transfermarkt (transfermarkt-datasets)")] if isinstance(r.url, str) else [],
             "foto_url": r.image_url if isinstance(r.image_url, str) and "default" not in r.image_url else None,
             "origem": "real", "conflito": {}}
        if ea is not None:
            if not pd.isna(ea.club_jersey_number):
                j["camisa"] = int(ea.club_jersey_number)
            j["fontes"].append(("https://sofifa.com" + ea.player_url if isinstance(ea.player_url, str) else EA_URL,
                                "base_dados", 3, "EA SPORTS FC 26 (SoFIFA)"))
        if arquivo:
            j["fontes"].append((f"file:///C:/Brasfoot22-23/teams/{arquivo}", "mod_brasfoot", 3, f"mod Brasfoot 2026: {arquivo}"))
        if not isinstance(j["pais"], str):
            j["pais"] = (mj or {}).get("pais") or clubes[cid]["pais"]
        tm_usados.add(int(r.player_id))
        return j

    # índices do TM por nome para casar jogadores do mod
    tm_nome_idx = defaultdict(list)
    for r in tmp.itertuples():
        tm_nome_idx[norm(r.name)].append(r)

    def casar_mod_tm(mj, ano_arq, elenco_tm=None):
        n = norm(mj["nome"])
        nasc_ano = ano_arq - (mj["idade"] or 25) if mj.get("idade") else None
        def ok_ano(r):
            if not nasc_ano or not isinstance(r.date_of_birth, str):
                return True
            return abs(int(r.date_of_birth[:4]) - nasc_ano) <= 1
        if elenco_tm:
            for r in elenco_tm:
                if norm(r.name) == n and ok_ano(r):
                    return r
        tid = mj.get("tid")
        if tid and tid in tm_por_id:
            r = tm_por_id[tid]
            rn = norm(r.name)
            if (n == rn or (n.split() and n.split()[-1] in rn.split())) and ok_ano(r):
                return r
        cs = [r for r in tm_nome_idx.get(n, []) if ok_ano(r) and (not mj.get("pais") or r.pais_j == mj["pais"])]
        return cs[0] if len(cs) == 1 else None

    # 1) clubes com elenco TM atual
    for tmid, cid in tm_cid.items():
        elenco = tm_por_clube.get(tmid, [])
        if not elenco:
            continue
        cl = clubes[cid]
        mjs = {}
        if cl["mod"]:
            ano = int(cl["mod"]["data_arquivo"][:4])
            for mj in cl["mod"]["jogadores"]:
                r = casar_mod_tm(mj, ano, elenco)
                if r is not None:
                    mjs[int(r.player_id)] = mj
        for r in elenco:
            jogadores.append(reg_tm(r, cid, mjs.get(int(r.player_id)),
                                    cl["mod"]["arquivo"] if int(r.player_id) in mjs else None))
        cl["fonte_elenco"] = "tm"
    # 2) clubes só do mod
    dedup_mod = {}
    extra_fotos = []
    for i, t in enumerate(mod):
        cid = mod_cid[i]
        if clubes[cid].get("fonte_elenco") == "tm":
            continue
        ano = int(t["data_arquivo"][:4])
        clubes[cid]["fonte_elenco"] = "mod"
        for mj in t["jogadores"]:
            if not mj.get("nome"):
                continue
            r = casar_mod_tm(mj, ano)
            if r is not None:
                if int(r.player_id) in tm_usados:
                    continue        # está em outro clube no Transfermarkt (mais recente)
                j = reg_tm(r, cid, mj, t["arquivo"])
                if r.last_season < 2024:
                    j["valor"] = None    # valor velho não vale
                if j["foto_url"] and r.last_season < 2025:
                    extra_fotos.append([int(r.player_id), j["foto_url"]])
                jogadores.append(j)
                continue
            idade = (mj.get("idade") or 25) + (2026 - ano)
            chave = (norm(mj["nome"]), mj.get("pais"), 2026 - idade)
            if chave in dedup_mod:
                continue
            dedup_mod[chave] = cid
            nasc_ano = 2026 - idade - 1
            jogadores.append({
                "nome": mj["nome"], "nome_conhecido": mj["nome"], "nascimento": f"{nasc_ano}-07-01",
                "pais": mj.get("pais") or t["pais"], "pais2": None, "posicao": pos_mod(mj), "posicoes_sec": None,
                "pe": {0: "D", 1: "E"}.get(mj.get("lado")), "altura": None, "clube_id": cid, "contrato_ate": None,
                "camisa": None, "valor": None, "ea": None, "tm_id": None, "mj": mj,
                "fontes": [(f"file:///C:/Brasfoot22-23/teams/{t['arquivo']}", "mod_brasfoot", 3, f"mod Brasfoot 2026: {t['arquivo']}")],
                "foto_url": None, "origem": "real",
                "conflito": {"nascimento": f"estimado pela idade ({mj.get('idade')}) no arquivo do mod de {t['data_arquivo']}"},
            })
    comum.salvar_json("fotos_extra.json", extra_fotos)
    print(f"  jogadores reais: {len(jogadores)} (TM {len(tm_usados)})")

    # ---------------- transferências 2026/27 (TM transfers.csv até 06/07/2026 + Wikidata P54 até ELENCOS_ATE)
    # clube atual do TM/Wikidata manda sobre o mod e sobre o clube do retrato de players.csv.
    tm_cid_todos = dict(tm_cid)
    for cid_, cl in clubes.items():
        if cl["tm_id"] and int(cl["tm_id"]) not in tm_cid_todos:
            tm_cid_todos[int(cl["tm_id"])] = cid_
    qid_cid = mapa_qid_clubes(clubes)
    decis = transferencias_2627(tmp, tm_cid_todos, qid_cid, clubes)
    tf_fonte = b.fonte(TM_URL + "#transfers", "base_dados", 4, "transfermarkt-datasets transfers.csv (CC0)", "CC0")
    wd_fonte_t = "https://query.wikidata.org/ (P54 com início em 2026: coleta_transferencias.py)"
    por_tm_j = {int(j["tm_id"]): j for j in jogadores if j["tm_id"]}
    novos_tm = 0
    # jogadores só do mod (sem tm_id), por nome + ano de nascimento: a mesma pessoa não pode ficar em dois clubes
    so_mod = defaultdict(list)
    for j in jogadores:
        if not j["tm_id"] and j["nascimento"]:
            so_mod[(norm(j["nome"]), int(j["nascimento"][:4]))].append(j)
    removidos_mod = 0
    for tid, dd in decis.items():
        if tid in por_tm_j or not dd["clube"] or tid not in tm_por_id or dd.get("so_emprestimo"):
            continue
        r = tm_por_id[tid]
        if isinstance(r.date_of_birth, str):
            ano_n = int(r.date_of_birth[:4])
            for a_ in (ano_n - 1, ano_n, ano_n + 1):
                for jm in so_mod.get((norm(r.name), a_), []):
                    jm["_remover"] = True      # vira o registro do TM no clube novo
                    removidos_mod += 1
        j = reg_tm(r, dd["clube"])
        if r.last_season < 2024:
            j["valor"] = None
        j["conflito"]["clube"] = f"contratação 2026/27 ({dd['fonte']}, {dd['data']}): jogador novo no banco"
        jogadores.append(j)
        por_tm_j[tid] = j
        dd["novo"] = True
        novos_tm += 1
    print(f"  transferências 2026/27: {len(decis)} decisões; {novos_tm} contratações novas no banco "
          f"({removidos_mod} registros antigos do mod com o mesmo nome/ano substituídos)")

    # ---------------- força / potencial
    amostra = []
    for j in jogadores:
        if j["ea"] is not None and j["valor"] and j["nascimento"]:
            a = idade_em(j["nascimento"])
            if a:
                amostra.append((j["valor"], a, int(j["ea"].overall)))
    coef = ajustar_modelo_valor(amostra)
    print("  modelo valor->força:", [round(x, 3) for x in coef], "amostra", len(amostra))
    for j in jogadores:
        a = idade_em(j["nascimento"]) or 25
        j["idade"] = a
        if j["ea"] is not None:
            j["forca"], j["potencial"] = int(j["ea"].overall), int(max(j["ea"].overall, j["ea"].potential))
            j["base_forca"] = "ea"
        elif j["valor"]:
            j["forca"] = forca_por_valor(coef, j["valor"], a)
            j["potencial"] = potencial_de(j["forca"], a)
            j["base_forca"] = "valor"
    # tabela nível(6-20) x titular -> força média (nos jogadores do mod que têm força por EA/valor)
    acc = defaultdict(list)
    for j in jogadores:
        mj, cl = j["mj"], clubes[j["clube_id"]]
        if mj and j.get("base_forca") and cl["nivel"]:
            acc[(cl["nivel"], mj.get("titular") or 0)].append(j["forca"])
    tabela_nivel = {}
    for nv in range(1, 21):
        for tt in (0, 1):
            v = acc.get((nv, tt))
            tabela_nivel[(nv, tt)] = (float(np.median(v)) if v and len(v) >= 30
                                      else 40 + 2.0 * nv + (3 if tt else -2))
    comum.salvar_json("tabela_nivel_forca.json", {f"{k[0]}_{k[1]}": round(v, 1) for k, v in tabela_nivel.items()})
    for j in jogadores:
        if "forca" in j:
            continue
        cl, mj = clubes[j["clube_id"]], j["mj"] or {}
        nv = cl["nivel"] or 10
        f = tabela_nivel[(nv, mj.get("titular") or 0)]
        a = j["idade"]
        f += 6 if mj.get("estrela") else 0
        f += -6 if a <= 18 else -3 if a <= 20 else -1 if a <= 22 else -2 if a >= 34 else 0
        h = mj.get("hash")
        h = h if isinstance(h, int) and 1 <= h <= 10 else 5   # no mod o hash às vezes vem fora de 1–10
        f += (h - 5) * 0.5
        j["forca"] = int(round(min(92, max(35, f))))
        j["potencial"] = potencial_de(j["forca"], a)
        j["base_forca"] = "nivel_mod"

    # ---------------- transferências 2026/27: aplica (depois da força, que usa o nível do clube de origem)
    mudou = Counter()
    exemplos_t = defaultdict(list)
    for tid, dd in decis.items():
        j = por_tm_j.get(tid)
        if j is None:
            mudou["sem_registro_no_banco"] += 1
            continue
        antes, depois = j["clube_id"], dd["clube"]
        if dd.get("so_emprestimo"):
            if antes == depois and dd.get("emprestado_de"):
                j["emprestado_de"] = dd["emprestado_de"]
                mudou["emprestimos"] += 1
            continue
        if dd.get("aposentou"):
            j["_remover"] = True
            mudou["aposentados_removidos"] += 1
            if antes:
                exemplos_t[antes].append(("-", j["nome"], {**dd, "fonte": "TM aposentou"}))
            continue
        if dd.get("emprestado_de") and dd["emprestado_de"] != depois:
            j["emprestado_de"] = dd["emprestado_de"]
            mudou["emprestimos"] += 1
        if dd.get("novo"):
            exemplos_t[depois].append(("+", j["nome"], dd))
            continue
        if antes == depois:
            continue
        j["clube_id"] = depois
        j["conflito"]["clube"] = (f"transferência 2026/27 ({dd['fonte']}, {dd['data']}): "
                                  f"{clubes[antes]['nome'] if antes in clubes else antes} -> "
                                  f"{clubes[depois]['nome'] if depois else (dd.get('destino') or 'sem clube')}")
        j["fontes"].append((TM_URL + "#transfers", "base_dados", 4, "transfermarkt-datasets transfers.csv (CC0)")
                           if dd["fonte"] == "TM" else (wd_fonte_t, "wikidata", 3, "Wikidata P54 (início 2026)"))
        if dd.get("contrato_invalido") and j.get("contrato_ate") and j["contrato_ate"] < dd["data"]:
            j["contrato_ate"] = None
        mudou["livre" if depois is None else "outro_clube"] += 1
        if depois is None and dd.get("destino"):
            mudou["livre_destino_fora_do_banco"] += 1
        if antes:
            exemplos_t[antes].append(("-", j["nome"], dd))
        if depois:
            exemplos_t[depois].append(("+", j["nome"], dd))
    jogadores[:] = [j for j in jogadores if not j.get("_remover")]
    resumo_transf = {"decisoes": len(decis), "contratacoes_novas": novos_tm, **mudou}
    print("  transferências aplicadas:", resumo_transf)
    comum.salvar_json("transferencias_2627_resumo.json", {
        "resumo": resumo_transf,
        "clubes": {k: [f"{s} {n} ({d['fonte']} {d['data']}{', empréstimo' if d.get('emprestado_de') else ''})"
                       for s, n, d in v] for k, v in sorted(exemplos_t.items())}})

    # ---------------- transferências BR 2026 (Wikipedia: páginas de temporada das Séries A/B) — por cima do TM/Wikidata
    def forca_nova(j, cid_):
        a = idade_em(j["nascimento"]) or 25
        j["idade"] = a
        if j["ea"] is not None:
            j["forca"], j["potencial"] = int(j["ea"].overall), int(max(j["ea"].overall, j["ea"].potential))
            j["base_forca"] = "ea"
        elif j["valor"]:
            j["forca"] = forca_por_valor(coef, j["valor"], a)
            j["potencial"] = potencial_de(j["forca"], a)
            j["base_forca"] = "valor"
        else:
            nv = clubes[cid_]["nivel"] or 10
            f = tabela_nivel[(nv, 0)] + (-6 if a <= 18 else -3 if a <= 20 else -1 if a <= 22 else -2 if a >= 34 else 0)
            j["forca"] = int(round(min(85, max(35, f))))
            j["potencial"] = potencial_de(j["forca"], a)
            j["base_forca"] = "estimada_nivel_clube"
            j["conflito"]["forca"] = (f"estimada pelo nível do clube de destino ({clubes[cid_]['nome']}, nível {nv}): "
                                      "sem EA FC 26 nem valor de mercado")
        return j

    def criar_tm_br(tid, cid_):
        r = tm_por_id[tid]
        j = reg_tm(r, cid_)
        if r.last_season < 2024:
            j["valor"] = None
        # registro antigo só do mod com o mesmo nome/ano vira este
        if isinstance(r.date_of_birth, str):
            ano_n = int(r.date_of_birth[:4])
            for a_ in (ano_n - 1, ano_n, ano_n + 1):
                for jm in so_mod.get((norm(r.name), a_), []):
                    jm["_remover"] = True
        return forca_nova(j, cid_)

    def criar_wd_br(mv, cid_):
        wd = mv["wd"]
        pais = next((p for p in wd.get("pais", []) if p != "GBR"), None)
        flag = FIFA_ISO.get(mv.get("jogador_pais") or "", mv.get("jogador_pais"))
        if not pais or (pais == "GBR" and flag in ("ENG", "SCO", "WAL", "NIR")):
            pais = flag
        if wd.get("pais") == ["GBR"] and flag in ("ENG", "SCO", "WAL", "NIR"):
            pais = flag
        if not pais:
            return None
        nome_c = mv["jogador"]
        nome = wd.get("pt") or wd.get("en") or nome_c
        ea = casar_ea(ea_idx, wd["nasc"], nome, nome_c)
        pos = pos_wiki(*wd.get("pos", []), mv.get("pos"))
        j = {"nome": nome, "nome_conhecido": nome_c, "nascimento": wd["nasc"], "pais": pais, "pais2": None,
             "posicao": pos, "posicoes_sec": None, "pe": None, "altura": None, "clube_id": cid_, "contrato_ate": None,
             "camisa": None, "valor": None, "ea": ea, "tm_id": None, "mj": None,
             "fontes": [("https://www.wikidata.org/wiki/" + mv["jogador_qid"], "wikidata", 3, f"Wikidata: {nome}")],
             "foto_url": None, "origem": "real", "conflito": {}}
        if ea is not None:
            j["fontes"].append(("https://sofifa.com" + ea.player_url if isinstance(ea.player_url, str) else EA_URL,
                                "base_dados", 3, "EA SPORTS FC 26 (SoFIFA)"))
            if isinstance(ea.player_positions, str):
                ps = [POS_EA.get(x.strip()) for x in ea.player_positions.split(",")]
                j["posicao"] = ps[0] or pos
        return forca_nova(j, cid_)

    resumo_br, exemplos_br, pulados_br, novos_br = transferencias_br(
        jogadores, clubes, qid_cid, decis, criar_tm_br, criar_wd_br, tm_por_id)
    jogadores[:] = [j for j in jogadores if not j.get("_remover")]
    print("  transferências BR 2026 (Wikipedia):", resumo_br)
    comum.salvar_json("transferencias_br_2026_resumo.json", {
        "resumo": resumo_br, "clubes": {k: v for k, v in sorted(exemplos_br.items(), key=lambda x: str(x[0]))},
        "pulados": pulados_br})

    # ---------------- transferências BR 2026 pela imprensa (confiabilidade 3) — por cima da Wikipedia
    def criar_noticia(it, cid_):
        est_ = bool(it.get("nascimento_estimado"))
        nome_c, nome = it["nome_conhecido"], it.get("nome") or it["nome_conhecido"]
        ea = None if est_ else casar_ea(ea_idx, it["nascimento"], nome, nome_c)
        j = {"nome": nome, "nome_conhecido": nome_c, "nascimento": it["nascimento"], "pais": it["pais"],
             "pais2": it.get("pais2"), "posicao": it["posicao"], "posicoes_sec": None, "pe": None, "altura": None,
             "clube_id": cid_, "contrato_ate": None, "camisa": None, "valor": None, "ea": ea, "tm_id": None, "mj": None,
             "fontes": [], "foto_url": None, "origem": "real", "conflito": {}}
        if est_:
            i_ = it["idade_fonte"]
            j["conflito"]["nascimento"] = (f"estimado pela idade citada na imprensa ({i_['idade']} anos em {i_['data']}): "
                                           f"{it['fontes'][0]}")
        if ea is not None:
            j["fontes"].append(("https://sofifa.com" + ea.player_url if isinstance(ea.player_url, str) else EA_URL,
                                "base_dados", 3, "EA SPORTS FC 26 (SoFIFA)"))
        return forca_nova(j, cid_)

    resumo_not, exemplos_not, pulados_not, novos_not = transferencias_noticias(
        jogadores, clubes, decis, criar_tm_br, criar_noticia, tm_por_id)
    jogadores[:] = [j for j in jogadores if not j.get("_remover")]
    print("  transferências BR 2026 (imprensa):", resumo_not)
    comum.salvar_json("transferencias_br_noticias_2026_resumo.json", {
        "resumo": resumo_not, "clubes": {k: v for k, v in sorted(exemplos_not.items(), key=lambda x: str(x[0]))},
        "pulados": pulados_not, "criados": [f"{j['nome_conhecido']} ({j['nascimento']}, {j['posicao']}, {j['clube_id']}, "
                                            f"força {j['forca']} por {j.get('base_forca')})" for j in novos_not]})

    # ---------------- mesma pessoa duas vezes no mesmo clube (TM/EA + mod): fica o TM/EA
    fundidos_mod = dedup_mesmo_clube(jogadores)
    print(f"  duplicatas TM/EA + mod no mesmo clube fundidas: {len(fundidos_mod)}")
    comum.salvar_json("duplicatas_mod_fundidas.json", fundidos_mod)

    # ---------------- clubes sem mod: nível estimado pela média de força
    por_clube = defaultdict(list)
    for j in jogadores:
        por_clube[j["clube_id"]].append(j)
    for cid, cl in clubes.items():
        if not cl["nivel"]:
            fs = sorted((j["forca"] for j in por_clube[cid]), reverse=True)[:14]
            m = sum(fs) / len(fs) if fs else 55
            cl["nivel"] = int(min(20, max(6, round((m - 44) / 2))))
            cl["rep"] = int(min(5, max(0, round((cl["nivel"] - 8) / 2.4))))

    # ---------------- nomes gerados (de jogadores reais do país)
    pren, sobr = defaultdict(Counter), defaultdict(Counter)
    for r in tmp.itertuples():
        if r.pais_j and isinstance(r.first_name, str) and isinstance(r.last_name, str):
            if " " not in r.first_name.strip():
                pren[r.pais_j][r.first_name.strip()] += 1
            sobr[r.pais_j][r.last_name.strip().split()[-1]] += 1
    for j in jogadores:
        if j["tm_id"] is None:
            ps = j["nome"].split()
            if len(ps) >= 2:
                pren[j["pais"]][ps[0]] += 1
                sobr[j["pais"]][ps[-1]] += 1
    nomes_mod = comum.ler_json("mod_nomes.json", {}) or {}
    cod3_iso = {v["cod3"]: v["iso"] for v in paises_mod.values() if v.get("cod3")}
    for cod, d in nomes_mod.items():
        i3 = cod3_iso.get(cod)
        if i3 and (len(pren[i3]) < 30 or len(sobr[i3]) < 30):
            for n in d.get("names", []):
                pren[i3][n] += 0
            for n in d.get("surnames", []):
                sobr[i3][n] += 0
    rows = []
    for p, cn in pren.items():
        rows += [(p, "prenome", n, f) for n, f in cn.items()]
    for p, cn in sobr.items():
        rows += [(p, "sobrenome", n, f) for n, f in cn.items()]
    c.executemany("INSERT INTO nomes_gerados VALUES (?,?,?,?)", rows)

    # ---------------- completar elencos (>= 18)
    MODELO = ["GOL", "GOL", "ZAG", "ZAG", "ZAG", "LD", "LE", "VOL", "MC", "MC", "MEI", "PE", "PD", "CA", "CA",
              "GOL", "ZAG", "VOL", "MEI", "CA", "LD", "LE"]
    gerados = 0
    for cid, cl in clubes.items():
        el = por_clube[cid]
        if len(el) >= 18:
            continue
        cont = Counter(j["posicao"] for j in el)
        precisa = []
        mc = Counter()
        for p in MODELO:
            mc[p] += 1
            if mc[p] > cont.get(p, 0):
                precisa.append(p)
        precisa = precisa[:18 - len(el)]
        pais = cl["pais"]
        lp = [n for n, _ in pren[pais].most_common(400)] or [n for n, _ in pren["ENG"].most_common(200)]
        ls = [n for n, _ in sobr[pais].most_common(800)] or [n for n, _ in sobr["ENG"].most_common(400)]
        base = tabela_nivel[(cl["nivel"] or 10, 0)] - 2
        for p in precisa:
            a = random.choice([18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32])
            f = int(round(min(85, max(35, base + random.gauss(0, 3) + (-4 if a <= 19 else 0)))))
            nome = f"{random.choice(lp)} {random.choice(ls)}"
            j = {"nome": nome, "nome_conhecido": nome, "nascimento": f"{2025 - a}-{random.randint(1, 12):02d}-{random.randint(1, 28):02d}",
                 "pais": pais, "pais2": None, "posicao": p, "posicoes_sec": None, "pe": random.choice("DDDE"),
                 "altura": None, "clube_id": cid, "contrato_ate": f"{2026 + random.randint(0, 2)}-12-31", "camisa": None,
                 "valor": None, "ea": None, "tm_id": None, "mj": None, "fontes": [], "foto_url": None,
                 "origem": "gerado", "conflito": {}, "forca": f, "potencial": potencial_de(f, a), "idade": a,
                 "base_forca": "gerado"}
            jogadores.append(j)
            por_clube[cid].append(j)
            gerados += 1
    print(f"  gerados: {gerados}")

    # ---------------- fotos
    # FUTEBAS_FOTOS: pasta de saída das fotos (montagem de teste fora de assets/; o swap copia depois).
    # Só os <id>.png da raiz são apagados: subpastas (fotos/lendas) ficam.
    fotos_dir = Path(__import__("os").environ.get("FUTEBAS_FOTOS") or (ASSETS / "fotos"))
    fotos_dir.mkdir(parents=True, exist_ok=True)
    for f_ in fotos_dir.glob("*.png"):
        f_.unlink()
    cache_fotos = comum.CACHE / "fotos_tm"

    # ---------------- gravar jogadores
    rows, jf = [], []
    nfotos = 0
    for jid, j in enumerate(jogadores, start=1):
        j["id"] = jid
        foto = foto_f = foto_l = None
        if j["tm_id"] and (cache_fotos / f"{j['tm_id']}.png").exists():
            shutil.copyfile(cache_fotos / f"{j['tm_id']}.png", fotos_dir / f"{jid}.png")
            foto, foto_f, foto_l = f"assets/fotos/{jid}.png", j["foto_url"], "uso_pessoal"
            nfotos += 1
        mj = j["mj"] or {}
        car = None
        if mj.get("car"):
            car = [CARAC[x] for x in mj["car"] if x in CARAC][:2] or None
        if not car and j["ea"] is not None:
            car = caracteristicas_ea(j["ea"])
        conf = dict(j["conflito"])
        conf["forca_base"] = j["base_forca"]
        pais_j = j["pais"] if isinstance(j["pais"], str) else (clubes[j["clube_id"]]["pais"] if j["clube_id"] else "BRA")
        rows.append((jid, j["nome"], j["nome_conhecido"] or j["nome"], j["nascimento"], pais_j, j["pais2"], j["posicao"],
                     json.dumps(j["posicoes_sec"]) if j["posicoes_sec"] else None, j["pe"], j["altura"], j["clube_id"],
                     j.get("emprestado_de"), j["contrato_ate"], j["camisa"], j["valor"], int(j["forca"]), int(j["potencial"]),
                     json.dumps(atributos_ea(j["ea"]), ensure_ascii=False) if j["ea"] is not None else None,
                     json.dumps(car, ensure_ascii=False) if car else None, j["origem"], j["tm_id"], None,
                     int(j["ea"].player_id) if j["ea"] is not None else None, foto, foto_f, foto_l,
                     json.dumps(conf, ensure_ascii=False)))
        for url, tipo, cf, tit in j["fontes"]:
            jf.append((jid, b.fonte(url, tipo, cf, tit)))
        if j["origem"] == "real" and j["tm_id"]:
            jf.append((jid, tm_fonte))
        if j["ea"] is not None:
            jf.append((jid, ea_fonte))
    c.executemany(f"INSERT INTO jogadores VALUES ({','.join('?' * 27)})", rows)
    c.executemany("INSERT OR IGNORE INTO jogador_fontes VALUES (?,?)", jf)
    print(f"  fotos copiadas: {nfotos}")

    # ---------------- competições e ligas_clubes
    lc: dict[str, set] = defaultdict(set)
    comp_meta: dict[str, dict] = {}
    comp_fonte: dict[str, str] = {}

    def add_comp(cid_, nome, ambito, pais=None, uf=None, div=None, cal=None):
        if cid_ not in comp_meta:
            comp_meta[cid_] = {"nome": nome, "ambito": ambito, "pais": pais, "uf": uf, "div": div,
                               "cal": cal or (calendario_pais(pais) if pais else "europeu"),
                               "conf": confed(pais) if pais else None}

    # 1ªs divisões do Transfermarkt
    for r in tmc_atual.itertuples():
        comp = TM_LIGA.get(r.domestic_competition_id)
        if comp:
            pais = comp_pais.get(r.domestic_competition_id)
            add_comp(comp, NOMES_COMP.get(comp, comp), "nacional", pais, None, 1)
            lc[comp].add(tm_cid[int(r.club_id)])
            comp_fonte[comp] = TM_URL

    # Wikipedia
    def casar_nome(item, candidatos, ja):
        txt, tit = item["texto"], re.sub(r"\s*\(.*?\)\s*", " ", item["titulo"])
        melhor, ms = None, 0
        for cid_ in candidatos:
            if cid_ in ja:
                continue
            cl = clubes[cid_]
            nomes_c = [cl["nome"]] + ([cl["nome_completo"]] if cl["nome_completo"] else [])
            s = 0
            for a_ in (txt, tit):
                for bn in nomes_c:
                    ka, kb = nome_chave(a_), nome_chave(bn)
                    if not ka or not kb:
                        continue
                    if ka == kb:
                        s = max(s, 120)
                    else:
                        s = max(s, 0.6 * fuzz.token_set_ratio(ka, kb) + 0.4 * fuzz.ratio(ka, kb))
            s += (cl["nivel"] or 0) * 0.05
            if s > ms:
                melhor, ms = cid_, s
        return melhor if ms >= 72 else None

    clubes_pais = defaultdict(list)
    for cid_, cl in clubes.items():
        clubes_pais[cl["pais"]].append(cid_)
    criados_wiki = 0
    FED = {"brazil": "BRA", "argentin": "ARG", "colombia": "COL", "peru": "PER", "chile": "CHL", "urugua": "URY",
           "paragua": "PRY", "ecuador": "ECU", "bolivia": "BOL", "venezuela": "VEN"}
    em_nacional = set()
    for comp_, s_ in lc.items():
        em_nacional |= s_
    def ordem(k):
        m_ = re.match(r"([a-z]+)-(\d+)$", k)
        return (0, k[:3], int(m_.group(2))) if m_ else (1, k, 0)
    for comp in sorted(wiki, key=ordem):
        d = wiki[comp]
        pais = comp.split("-")[0].upper()
        ambito, uf = "nacional", None
        if comp.startswith("est-"):
            uf, pais, ambito = comp.split("-")[1].upper(), "BRA", "estadual"
        elif comp == "bra-copa":
            pais, ambito = "BRA", "copa_nacional"
        elif comp.startswith("conmebol"):
            pais, ambito = None, "continental"
        else:
            pais = {"DEU": "DEU", "ENG": "ENG", "SCO": "SCO"}.get(pais, pais)
        if comp == "eng-5":
            continue
        if ambito == "continental":
            cands = [x for x in clubes if clubes[x]["pais"] in CONMEBOL]
        elif uf:
            cands = [x for x in clubes_pais["BRA"] if clubes[x]["uf"] in (uf, None)]
        else:
            cands = clubes_pais.get(pais, [])
        div = int(comp.split("-")[-1]) if comp.split("-")[-1].isdigit() else None
        if uf and div is None:
            div = 1
        nome = NOMES_COMP.get(comp) or d.get("nome") or (f"Campeonato {UF_NOME[uf]}" if uf else comp)
        add_comp(comp, nome, ambito, pais, uf, div, "ano" if ambito == "continental" else None)
        comp_fonte[comp] = d["url"]
        ja = set()
        if comp in ("bra-1",) and lc.get(comp):
            continue   # Série A já vem do Transfermarkt (mesmos 20)
        if lc.get(comp) and comp not in ("jpn-1",):
            continue   # TM tem prioridade para 1ªs divisões
        if comp == "jpn-1":
            em_nacional -= lc[comp]
            lc[comp] = set()
        for item in d["clubes"]:
            cands_i = cands
            if ambito == "continental" and item.get("pais"):
                pf = next((v for k, v in FED.items() if k in item["pais"].lower()), None)
                if pf:
                    cands_i = [x for x in cands if clubes[x]["pais"] == pf]
            if ambito == "nacional":
                cands_i = [x for x in cands_i if x not in em_nacional]
            mu = re.search(r"[\s\-(]([A-Z]{2})\)?$", item["texto"]) or re.search(r"\(([A-Z]{2})\)", item["titulo"])
            if pais == "BRA" and mu and mu.group(1) in UF_NOME:
                cu = [x for x in cands_i if clubes[x]["uf"] == mu.group(1)]
                cands_i = cu or cands_i
            m = casar_nome(item, cands_i, ja)
            if m is None and ambito in ("nacional", "estadual") and pais:
                # clube real que não está no mod nem no TM: cria com elenco gerado
                m = novo_id(item["texto"], pais, uf)
                clubes[m] = {"id": m, "nome": item["texto"], "nome_completo": item["titulo"], "pais": pais, "uf": uf,
                             "cor1": None, "cor2": None, "estadio": None, "capacidade": None, "nivel": None,
                             "rep": None, "tm_id": None, "mod": None, "fonte_mod": None, "fonte_elenco": "nenhuma",
                             "fonte_wiki": comum.wiki_url(d.get("lang", "en"), item["titulo"])}
                clubes_pais[pais].append(m)
                criados_wiki += 1
            if m:
                ja.add(m)
                lc[comp].add(m)
                if ambito == "nacional":
                    em_nacional.add(m)
    print(f"  clubes criados a partir da Wikipedia (sem elenco real): {criados_wiki}")
    aplicar_livres_pendentes(c, clubes, por_clube)

    # continentais UEFA 2025/26 (fase de liga) pelo Transfermarkt
    g = pd.read_csv(TM / "games.csv.gz", usecols=["competition_id", "season", "round", "date", "home_club_id",
                                                  "away_club_id", "home_club_goals", "away_club_goals", "home_club_name"])
    for tmcomp, comp in (("CL", "uefa-champions"), ("EL", "uefa-europa"), ("UCOL", "uefa-conference")):
        x = g[(g.competition_id == tmcomp) & (g.season == 2025) & (g["round"] == "Group Stage")]
        add_comp(comp, NOMES_COMP[comp], "continental", None, None, None, "europeu")
        comp_meta[comp]["conf"] = "UEFA"
        comp_fonte[comp] = TM_URL
        for tid in set(x.home_club_id) | set(x.away_club_id):
            if int(tid) in tm_cid:
                lc[comp].add(tm_cid[int(tid)])
            else:
                linha = x[x.home_club_id == tid]
                nome_tm = str(linha.iloc[0]["home_club_name"]) if len(linha) else None
                m = casar_nome({"texto": nome_tm or "", "titulo": nome_tm or ""},
                               [k for k in clubes if confed(clubes[k]["pais"]) == "UEFA"], lc[comp]) if nome_tm else None
                if m:
                    lc[comp].add(m)
                    tm_cid[int(tid)] = m
                    if not clubes[m]["tm_id"]:
                        clubes[m]["tm_id"] = int(tid)
                else:
                    print("   clube continental fora do banco:", tmcomp, tid, nome_tm)
    for comp in ("conmebol-libertadores", "conmebol-sul-americana"):
        if comp in comp_meta:
            comp_meta[comp]["conf"] = "CONMEBOL"

    # Copa do Brasil 2026: participantes da Wikipedia + Séries A, B e C + melhores dos estaduais (até 126)
    if "bra-copa" in comp_meta:
        alvo = lc["bra-copa"] | lc["bra-1"] | lc["bra-2"] | lc["bra-3"]
        est = sorted({x for k, v in lc.items() if re.fullmatch(r"est-[a-z]{2}", k) for x in v} - alvo,
                     key=lambda x: -(clubes[x]["nivel"] or 0))
        lc["bra-copa"] = set(list(alvo) + est[:max(0, 126 - len(alvo))])

    # divisões restantes: estrutura do mod (conf_ligas_nacionais) + clubes ordenados por nível/reputação
    cod3_por_iso = {v["iso"]: v["cod3"] for v in paises_mod.values() if v.get("cod3")}
    usados_pais = defaultdict(set)
    for comp, s in lc.items():
        if comp_meta[comp]["ambito"] == "nacional":
            for x in s:
                usados_pais[clubes[x]["pais"]].add(x)
    estimadas = []
    for pais, cod in cod3_por_iso.items():
        divs = ligas_mod.get(cod) or []
        if pais == "BRA":
            continue
        livres = sorted((x for x in clubes_pais.get(pais, []) if x not in usados_pais[pais]),
                        key=lambda x: (-(clubes[x]["nivel"] or 0), -(clubes[x]["rep"] or 0)))
        for d in sorted(divs, key=lambda d: d.get("divisao") or 9):
            comp = f"{pais.lower()}-{d.get('divisao')}"
            if lc.get(comp):
                continue
            n = int(d.get("nTimes") or 0)
            if n < 4 or len(livres) < 4:
                break
            pega, livres = livres[:n], livres[n:]
            nome = (NOMES_COMP.get(comp) or nome_divisao((d.get('nomeDivisao') or '').strip())
                    or f"{ORDINAL_DIV.get(d.get('divisao'), str(d.get('divisao')) + 'ª')} Divisão")
            if confed(pais) != "UEFA" or len(nome) < 4:
                nome = f"{nome} ({paises.get(pais, {}).get('nome', pais)})"
            add_comp(comp, nome, "nacional", pais, None, d.get("divisao"))
            lc[comp] = set(pega)
            comp_fonte[comp] = f"file:///C:/Brasfoot22-23/conf_ligas_nacionais/{cod}.cfg"
            estimadas.append(comp)
    # estaduais sem página: 10 melhores do estado pelo nível (mod)
    for uf in UF_NOME:
        comp = f"est-{uf.lower()}"
        if lc.get(comp):
            continue
        cand_uf = sorted((x for x in clubes_pais["BRA"] if clubes[x]["uf"] == uf),
                         key=lambda x: (-(clubes[x]["nivel"] or 0), -(clubes[x]["rep"] or 0)))
        if len(cand_uf) >= 4:
            add_comp(comp, f"Campeonato {UF_NOME[uf]}", "estadual", "BRA", uf, 1)
            lc[comp] = set(cand_uf[:10])
            comp_fonte[comp] = f"file:///C:/Brasfoot22-23/conf_estadual/{uf}.ces"
            estimadas.append(comp)
    # 2ª divisão estadual sem lista na Wikipedia: os clubes da UF que sobraram, pelo nível do mod (estimado)
    for uf in UF_NOME:
        if any(k.startswith(f"est-{uf.lower()}-") for k in lc if lc.get(k)) or not lc.get(f"est-{uf.lower()}"):
            continue
        em_est = {x for k, v in lc.items() if k.startswith(f"est-{uf.lower()}") for x in v}
        resto = sorted((x for x in clubes_pais["BRA"] if clubes[x]["uf"] == uf and x not in em_est and clubes[x].get("mod")),
                       key=lambda x: (-(clubes[x]["nivel"] or 0), -(clubes[x]["rep"] or 0)))
        if len(resto) >= 6:
            comp = f"est-{uf.lower()}-2"
            add_comp(comp, f"Campeonato {UF_NOME[uf]} – Segunda Divisão", "estadual", "BRA", uf, 2)
            lc[comp] = set(resto[:12])
            comp_fonte[comp] = f"file:///C:/Brasfoot22-23/conf_estadual/{uf}.ces"
            estimadas.append(comp)
    comum.salvar_json("ligas_estimadas.json", estimadas)

    # divisões estaduais de 2026 conferidas (coleta/estaduais_2026.json, curado à mão com fontes): substituem a
    # lista da Wikipedia/estimada; clubes duplicados apontam para o id que fica; cada clube numa divisão só
    est26 = json.loads((RAIZ / "coleta" / "estaduais_2026.json").read_text("utf-8"))
    fusao = {f["remover"]: f["fica"] for f in est26.get("fusoes", [])}
    nac_br = ("bra-1", "bra-2", "bra-3", "bra-4")
    for comp_, s_ in lc.items():
        for velho, novo in fusao.items():
            if velho in s_:
                s_.discard(velho)
                if comp_ in nac_br and any(novo in lc.get(k, ()) for k in nac_br):
                    continue   # o que fica já está noutra série nacional
                s_.add(novo)
    for r_ in est26.get("renomear", []):
        if r_["id"] in clubes:
            cl = clubes[r_["id"]]
            cl["nome"], cl["nome_completo"] = r_["nome"], r_.get("nome_completo") or cl["nome_completo"]
            cl["cidade"] = r_.get("cidade") or cl.get("cidade")
            cl.setdefault("fontes_extra", [])
            cl["fontes_extra"] = list(cl["fontes_extra"]) + (r_.get("fontes") or [])
    chaves_uf = {(nome_chave(cl["nome"]), cl["uf"]) for cl in clubes.values() if cl["pais"] == "BRA"}
    id_por_chave = {(nome_chave(cl["nome"]), cl["uf"]): k for k, cl in clubes.items() if cl["pais"] == "BRA"}
    est_corr = Counter()
    for comp, dv_ in est26.get("divisoes", {}).items():
        uf = comp.split("-")[1].upper()
        nivel_ = int(comp.split("-")[2]) if comp.count("-") == 2 else 1
        if comp not in comp_meta:
            add_comp(comp, dv_["nome"], "estadual", "BRA", uf, nivel_)
            est_corr["divisoes_novas"] += 1
        elif dv_.get("renomear"):
            comp_meta[comp]["nome"] = dv_["nome"]
        comp_fonte[comp] = (dv_.get("fontes") or [comp_fonte.get(comp)])[0]
        novo_s = set()
        for p in dv_["participantes"]:
            cid_ = fusao.get(p.get("clube_id"), p.get("clube_id"))
            if cid_ and cid_ not in clubes:
                print(f"  AVISO: {comp}: clube {cid_} não existe")
                continue
            if not cid_:
                k_ = (nome_chave(p["nome"]), uf)
                cid_ = id_por_chave.get(k_)
                if not cid_:
                    cid_ = novo_id(p["nome"], "BRA", uf)
                    clubes[cid_] = {"id": cid_, "nome": p["nome"], "nome_completo": p.get("nome_oficial"), "pais": "BRA",
                                    "uf": uf, "cidade": p.get("cidade"), "fundacao": None, "cor1": None, "cor2": None,
                                    "estadio": None, "capacidade": None, "nivel": None, "rep": None, "tm_id": None,
                                    "mod": None, "fonte_mod": None, "fonte_elenco": "nenhuma",
                                    "fontes_extra": dv_.get("fontes") or []}
                    clubes_pais["BRA"].append(cid_)
                    id_por_chave[k_] = cid_
                    chaves_uf.add(k_)
                    est_corr["clubes_criados"] += 1
            novo_s.add(cid_)
        antes = lc.get(comp, set())
        est_corr["entraram"] += len(novo_s - antes)
        est_corr["sairam"] += len(antes - novo_s)
        lc[comp] = novo_s
        # quem está numa divisão conferida sai das outras divisões do estado
        for k in list(lc):
            if k != comp and re.fullmatch(rf"est-{uf.lower()}(-\d)?", k) and k not in est26["divisoes"]:
                lc[k] -= novo_s
    for comp, cid_ob in CLUBES_OBRIGATORIOS:
        if comp in comp_meta and cid_ob in clubes:
            lc[comp].add(cid_ob)
    # um clube por estado numa divisão só: fica a mais alta
    for uf in UF_NOME:
        ks = sorted((k for k in lc if re.fullmatch(rf"est-{uf.lower()}(-\d)?", k)),
                    key=lambda k: int(k.rsplit("-", 1)[1]) if k.count("-") == 2 else 1)
        vistos_ = set()
        for k in ks:
            rep_ = lc[k] & vistos_
            if rep_:
                est_corr["em_duas_divisoes"] += len(rep_)
                lc[k] -= rep_
            vistos_ |= lc[k]
    print(f"  estaduais 2026 conferidos (estaduais_2026.json): {dict(est_corr)}")

    # clubes reais sem divisão em 2026 (licenciados/desistentes; coleta/clubes_inativos_2026.json, curado à mão):
    # entram no estado sem competição, com elenco gerado e nível da última divisão estadual da UF.
    # 'nao_confirmados_incluidos': clubes reais sem confirmação de atividade em 2026 (incluídos a requisito)
    inat = json.loads((RAIZ / "coleta" / "clubes_inativos_2026.json").read_text("utf-8"))
    n_inat = 0
    for it in inat.get("clubes", []) + inat.get("nao_confirmados_incluidos", []):
        uf = it["uf"]
        if (nome_chave(it["nome"]), uf) in chaves_uf:
            print(f"  AVISO: clube inativo já existe, ignorado: {it['nome']} ({uf})")
            continue
        ultima = max((k for k in lc if lc.get(k) and re.fullmatch(rf"est-{uf.lower()}(-\d)?", k)),
                     key=lambda k: int(k.rsplit("-", 1)[1]) if k.count("-") == 2 else 1, default=None)
        nv_uf = [clubes[x]["nivel"] for x in lc.get(ultima, ()) if clubes[x]["nivel"]]
        nv = max(1, int(np.median(nv_uf)) - 1) if nv_uf else 4
        m = novo_id(it["nome"], "BRA", uf)
        clubes[m] = {"id": m, "nome": it["nome"], "nome_completo": it.get("nome_oficial"), "pais": "BRA", "uf": uf,
                     "cidade": it.get("cidade"), "fundacao": str(it["fundacao"]) if it.get("fundacao") else None,
                     "cor1": it.get("cor1"), "cor2": it.get("cor2"), "estadio": it.get("estadio"), "capacidade": None,
                     "nivel": nv, "rep": 0, "tm_id": None, "mod": None, "fonte_mod": None, "fonte_elenco": "nenhuma",
                     "fontes_extra": it.get("fontes") or []}
        clubes_pais["BRA"].append(m)
        chaves_uf.add((nome_chave(it["nome"]), uf))
        n_inat += 1
    print(f"  clubes reais sem divisão em 2026 (clubes_inativos_2026.json): {n_inat}")

    # divisão estadual fictícia na base da pirâmide (requisito, 14/11/2026): no estado com 6 ou mais
    # clubes fora das divisões, eles jogam uma divisão a mais (acesso para a última real, que passa a ter
    # rebaixamento). Última divisão real com menos de 6 clubes (ex.: Série B rondoniense com 2) é completada
    # com esses clubes em vez de ganhar uma divisão abaixo.
    ficticias, completadas = {}, {}
    for uf in UF_NOME:
        ks = sorted((k for k in lc if lc.get(k) and re.fullmatch(rf"est-{uf.lower()}(-\d)?", k)),
                    key=lambda k: int(k.rsplit("-", 1)[1]) if k.count("-") == 2 else 1)
        if not ks:
            continue
        em_est = set().union(*(lc[k] for k in ks))
        fora = sorted((x for x in clubes_pais["BRA"] if clubes[x]["uf"] == uf and x not in em_est and x not in fusao),
                      key=lambda x: (-(clubes[x]["nivel"] or 0), x))
        if not fora:
            continue
        ult = ks[-1]
        nv_ult = int(ult.rsplit("-", 1)[1]) if ult.count("-") == 2 else 1
        if len(lc[ult]) < 6 and nv_ult > 1:
            # completa até 8 com os mais fortes de fora; se sobrarem menos de 6, entram todos aqui
            falta = max(0, 8 - len(lc[ult]))
            pega = fora if len(fora) - falta < 6 else fora[:falta]
            lc[ult] |= set(pega)
            completadas[ult] = len(pega)
            fora = [x for x in fora if x not in pega]
        if len(fora) >= 6:
            comp = f"est-{uf.lower()}-{nv_ult + 1}"
            add_comp(comp, nome_divisao_abaixo(comp_meta[ult]["nome"], nv_ult + 1, UF_NOME[uf]), "estadual", "BRA", uf, nv_ult + 1)
            comp_meta[comp]["ficticia"] = True
            lc[comp] = set(fora)
            comp_fonte[comp] = None
            ficticias[comp] = len(fora)
    print(f"  divisões estaduais fictícias: {ficticias}; últimas divisões completadas: {completadas}")

    # completa clubes criados a partir da Wikipedia (nível pela liga + elenco gerado)
    for comp, s in lc.items():
        nv = [clubes[x]["nivel"] for x in s if clubes[x]["nivel"]]
        med = int(np.median(nv)) if nv else 8
        for x in s:
            cl = clubes[x]
            if not cl["nivel"]:
                cl["nivel"], cl["rep"] = med, max(0, round((med - 8) / 2.4))
    for cid_, cl in clubes.items():
        if cl.get("fonte_elenco") == "nenhuma" and len(por_clube[cid_]) < 18:
            pais = cl["pais"]
            lp = [n for n, _ in pren[pais].most_common(400)] or ["Juan"]
            ls = [n for n, _ in sobr[pais].most_common(800)] or ["Silva"]
            base = tabela_nivel[(cl["nivel"] or 8, 0)] - 2
            novos = []
            for p in MODELO[:18]:
                a = random.choice(range(18, 33))
                f = int(round(min(85, max(35, base + random.gauss(0, 3)))))
                jid = len(jogadores) + 1
                nome = f"{random.choice(lp)} {random.choice(ls)}"
                j = {"id": jid, "nome": nome, "clube_id": cid_, "pais": pais, "posicao": p, "forca": f, "origem": "gerado"}
                jogadores.append(j)
                por_clube[cid_].append(j)
                novos.append((jid, nome, nome, f"{2025 - a}-{random.randint(1, 12):02d}-{random.randint(1, 28):02d}", pais,
                              None, p, None, random.choice("DDDE"), None, cid_, None, "2026-12-31", None, None, f,
                              potencial_de(f, a), None, None, "gerado", None, None, None, None, None, None,
                              json.dumps({"forca_base": "gerado"})))
            c.executemany(f"INSERT INTO jogadores VALUES ({','.join('?' * 27)})", novos)
            gerados += len(novos)

    # clubes obrigatórios (requisito), mesmo fora da lista da Wikipedia
    ids_clubes = set(clubes)   # (a tabela clubes só é gravada mais abaixo)
    for comp, cid_ob in CLUBES_OBRIGATORIOS:
        if comp in comp_meta and cid_ob in ids_clubes:
            lc[comp].add(cid_ob)
        else:
            print(f"  AVISO: clube obrigatório {cid_ob} não entrou em {comp}")

    for comp, m in comp_meta.items():
        logo = None
        c.execute("INSERT INTO competicoes VALUES (?,?,?,?,?,?,?,?,?)",
                  (comp, m["nome"], m["ambito"], m["pais"], m["uf"], m["conf"], m["div"], m["cal"], logo))
        temp = "2026" if m["cal"] == "ano" else "2025/26"
        if comp.startswith("conmebol"):
            temp = "2026"
        c.executemany("INSERT OR IGNORE INTO ligas_clubes VALUES (?,?,?)", [(comp, temp, x) for x in sorted(lc[comp])])

    # ---------------- tabelas iniciais (ligas agosto–maio em 1/jan/2026), pelos jogos do Transfermarkt
    ntab = 0
    com_tabela = set()
    for tb in pesq.get("tabelas", []):
        comp = tb.get("competicao_id")
        if comp not in comp_meta:
            continue
        cands = list(lc[comp])
        ja = set()
        linhas = []
        for l in tb.get("linhas", []):
            m = casar_nome({"texto": l.get("clube") or "", "titulo": l.get("clube") or ""}, cands, ja)
            if not m:
                print("   tabela: clube não casado", comp, l.get("clube"))
                continue
            ja.add(m)
            linhas.append((comp, tb.get("temporada") or "2025/26", tb.get("fase"), tb.get("grupo"), m,
                           l.get("j"), l.get("v"), l.get("e"), l.get("d"), l.get("gp"), l.get("gc"), l.get("pts"),
                           tb.get("fonte")))
        if len(linhas) >= len(tb.get("linhas", [])) - 1:
            c.executemany("INSERT INTO tabelas_iniciais VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", linhas)
            ntab += len(linhas)
            com_tabela.add(comp)
    for tmcomp, comp in TM_LIGA.items():
        if comp in com_tabela:
            continue
        if comp not in comp_meta or comp_meta[comp]["cal"] != "europeu":
            continue
        x = g[(g.competition_id == tmcomp) & (g.season == 2025) & (g.date < "2026-01-01")]
        if x.empty:
            continue
        tab = defaultdict(lambda: [0] * 7)
        for r in x.itertuples():
            if pd.isna(r.home_club_goals):
                continue
            for cl_, gp, gc in ((r.home_club_id, r.home_club_goals, r.away_club_goals),
                                (r.away_club_id, r.away_club_goals, r.home_club_goals)):
                t = tab[int(cl_)]
                t[0] += 1
                t[1 if gp > gc else 2 if gp == gc else 3] += 1
                t[4] += int(gp)
                t[5] += int(gc)
                t[6] += 3 if gp > gc else 1 if gp == gc else 0
        for tid, t in tab.items():
            if tid in tm_cid and tm_cid[tid] in lc[comp]:
                c.execute("INSERT INTO tabelas_iniciais VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                          (comp, "2025/26", "liga", None, tm_cid[tid], *t, TM_URL))
                ntab += 1

    # ---------------- escudos, uniformes, troféus
    esc_dir = ASSETS / "escudos"
    esc_dir.mkdir(parents=True, exist_ok=True)
    kit_dir = ASSETS / "brasfoot-mod"
    nesc = nkit = 0
    for cid_, cl in clubes.items():
        dst = esc_dir / f"{cid_}.png"
        src = Path(cl["mod"]["escudo"]) if cl["mod"] and cl["mod"].get("escudo") else None
        fonte_e = lic = None
        if src and src.exists():
            try:
                if not dst.exists() or dst.stat().st_mtime < src.stat().st_mtime:
                    im = Image.open(src).convert("RGBA")
                    if im.height > 256:
                        im = im.resize((round(im.width * 256 / im.height), 256), Image.LANCZOS)
                    im.save(dst)
                fonte_e, lic = f"mod Brasfoot 2026: teams/escudos/{src.name}", "uso_pessoal"
            except Exception as e:  # noqa: BLE001
                print("  escudo ruim", src, e)
        elif cl["tm_id"]:
            url = f"https://tmssl.akamaized.net/images/wappen/head/{cl['tm_id']}.png"
            bts = dst.read_bytes() if dst.exists() else comum.baixar(url, binario=True)
            if bts:
                if not dst.exists():
                    import io
                    im = Image.open(io.BytesIO(bts)).convert("RGBA")
                    if im.height > 256:
                        im = im.resize((round(im.width * 256 / im.height), 256), Image.LANCZOS)
                    im.save(dst)
                fonte_e, lic = url, "uso_pessoal"
        if fonte_e:
            cl["escudo"] = (f"assets/escudos/{cid_}.png", fonte_e, lic)
            nesc += 1
        if cl["mod"]:
            for pasta in ("camisas", "camisas2", "camisas3"):
                s = MOD / "teams" / pasta / f"{cl['mod']['base']}.png"
                if s.exists():
                    d_ = kit_dir / pasta / f"{cid_}.png"
                    d_.parent.mkdir(parents=True, exist_ok=True)
                    if not d_.exists():
                        shutil.copyfile(s, d_)
                    nkit += 1
    for sub in ("trofeus",):
        dd = kit_dir / sub
        dd.mkdir(parents=True, exist_ok=True)
        for s in (MOD / sub).glob("*.png"):
            if not (dd / s.name).exists():
                shutil.copyfile(s, dd / s.name)
    for sub in ("camisas", "camisas2", "escudos"):
        dd = kit_dir / "selecoes" / sub
        dd.mkdir(parents=True, exist_ok=True)
        for s in (MOD / "selecoes" / sub).glob("*.png"):
            if not (dd / s.name).exists():
                shutil.copyfile(s, dd / s.name)
    # logos (troféus do mod) das competições
    tro = kit_dir / "trofeus"
    cod3 = {v["iso"]: v["cod3"] for v in paises_mod.values() if v.get("cod3")}
    for comp, m in comp_meta.items():
        nomes = []
        if comp == "conmebol-libertadores":
            nomes = ["tr_libertadores.png"]
        elif comp == "conmebol-sul-americana":
            nomes = ["tr_sulamericana.png"]
        elif comp == "uefa-champions":
            nomes = ["tr_ligacampeoes.png"]
        elif comp == "uefa-europa":
            nomes = ["tr_ligaeuropa.png"]
        elif comp == "uefa-conference":
            nomes = ["tr_conference.png"]
        elif comp == "bra-copa":
            nomes = ["tr_copa_BRA.png"]
        elif m["ambito"] == "estadual":
            nomes = [f"tr_estadual_{m['uf']}.png", "tr_estadualgenerico.png"]
        elif m["ambito"] == "nacional" and m["pais"] in cod3:
            k = cod3[m["pais"]]
            nomes = [f"tr_nacional_{k}_d{m['div']}.png"] + ([f"tr_nacional_{k}.png"] if m["div"] == 1 else []) + ["tr_nacionalgenerico.png"]
        for n in nomes:
            if (tro / n).exists():
                c.execute("UPDATE competicoes SET logo_png=? WHERE id=?", (f"assets/brasfoot-mod/trofeus/{n}", comp))
                break

    # ---------------- gravar clubes
    siglas_pais = defaultdict(set)
    tot_valor = defaultdict(int)
    nval = Counter()
    for j in jogadores:
        if j.get("valor"):
            tot_valor[j["clube_id"]] += j["valor"]
            nval[j["clube_id"]] += 1
    for cid_, cl in clubes.items():
        nv, rp = cl["nivel"] or 8, cl["rep"] or 0
        cap = cl["capacidade"] or 0
        reput = round(min(100, max(0, (nv - 6) / 14 * 70 + rp * 6)), 1)
        torc = round(min(100, max(0, rp * 14 + (nv - 6) / 14 * 20 + min(10, cap / 8000))), 1)
        if nval[cid_] >= 10:
            orc = int(tot_valor[cid_] * 4.8)
        else:
            orc = int(10 ** (5.2 + 0.17 * nv))
        sg = sigla_de(cl["nome"], siglas_pais[cl["pais"]])
        siglas_pais[cl["pais"]].add(sg)
        esc = cl.get("escudo") or (None, None, None)
        c.execute("INSERT INTO clubes VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                  (cid_, cl["nome"], cl["nome_completo"], sg, cl.get("cidade"), cl["uf"], cl["pais"], cl.get("fundacao"), cl["cor1"], cl["cor2"],
                   cl["estadio"], cl["capacidade"], reput, torc, orc, esc[0], esc[1], esc[2], cl["tm_id"], None, None))
        mod_ = cl.get("mod") or {}
        if mod_.get("tecnico") and str(mod_["tecnico"]).strip():
            c.execute("INSERT INTO tecnicos (nome, pais, clube_id, origem, fonte_id) VALUES (?,?,?,?,?)",
                      (str(mod_["tecnico"]).strip(), mod_.get("tecnico_pais") or cl["pais"], cid_, "real",
                       b.fonte(cl["fonte_mod"], "mod_brasfoot", 3, f"mod Brasfoot 2026: {mod_.get('arquivo')}") if cl.get("fonte_mod") else None))
        if cl.get("fonte_mod"):
            c.execute("INSERT OR IGNORE INTO clube_fontes VALUES (?,?)",
                      (cid_, b.fonte(cl["fonte_mod"], "mod_brasfoot", 3, f"mod Brasfoot 2026: {cl['mod']['arquivo']}")))
        if cl["tm_id"]:
            c.execute("INSERT OR IGNORE INTO clube_fontes VALUES (?,?)", (cid_, tm_fonte))
        if cl.get("fonte_wiki"):
            c.execute("INSERT OR IGNORE INTO clube_fontes VALUES (?,?)", (cid_, b.fonte(cl["fonte_wiki"], "wikipedia", 3)))
        for u in cl.get("fontes_extra") or []:
            tp = "wikipedia" if "wikipedia.org" in u else "federacao" if re.search(r"federac|ff[a-z]{1,3}\.|fgf|fcf|fbf|fmf|ferj|fferj|fpf|futeboldealagoas|esumula", u) else "imprensa"
            c.execute("INSERT OR IGNORE INTO clube_fontes VALUES (?,?)", (cid_, b.fonte(u, tp, 3 if tp == "wikipedia" else 4)))

    # ---------------- técnicos atuais (Wikidata P286 sem data de fim, início depois do mod) mandam sobre o mod
    ntec = 0
    tw = (comum.ler_json("transferencias_wd_2026.json", {}) or {}).get("tecnicos", [])
    melhor_tec = {}
    for t in tw:
        cid_ = (tm_cid_todos.get(int(t["clube_tm"])) if str(t.get("clube_tm") or "").isdigit() else None) \
            or qid_cid.get(t["clube_qid"])
        if not cid_ or not t.get("nome") or t["inicio"] < "2026-03-01" or t["inicio"] > ELENCOS_ATE:
            continue
        if cid_ not in melhor_tec or t["inicio"] > melhor_tec[cid_]["inicio"]:
            melhor_tec[cid_] = t
    for cid_, t in melhor_tec.items():
        f_ = b.fonte("https://www.wikidata.org/wiki/" + t["tecnico_qid"], "wikidata", 3,
                     f"Wikidata {t['tecnico_qid']}: técnico (P286) desde {t['inicio']}", "CC0")
        pais_t = {"GBR": "ENG"}.get(t.get("pais"), t.get("pais"))
        c.execute("DELETE FROM tecnicos WHERE clube_id=?", (cid_,))
        c.execute("INSERT INTO tecnicos (nome, pais, clube_id, origem, fonte_id) VALUES (?,?,?,?,?)",
                  (t["nome"], pais_t, cid_, "real", f_))
        ntec += 1
    print(f"  técnicos atualizados pelo Wikidata (desde mar/2026): {ntec}")

    # ---------------- rivais (do banco v1, por id de clube igual)
    # (o v1 guardava rivais por Wikidata; ficam de fora até haver casamento por QID)

    # ---------------- formatos / calendários / regras (pesquisa)
    for comp in pesq["competicoes"]:
        cid_ = comp.get("id")
        if not cid_:
            continue
        if cid_ not in comp_meta:
            c.execute("INSERT OR IGNORE INTO competicoes VALUES (?,?,?,?,?,?,?,?,?)",
                      (cid_, comp.get("nome") or cid_, comp.get("ambito") or "nacional", comp.get("pais"), comp.get("uf"),
                       comp.get("confederacao"), comp.get("divisao"), comp.get("calendario"), None))
        else:
            c.execute("UPDATE competicoes SET nome=?, confederacao=COALESCE(?, confederacao), calendario=COALESCE(?, calendario) WHERE id=?",
                      (comp.get("nome") or comp_meta[cid_]["nome"], comp.get("confederacao"), comp.get("calendario"), cid_))
        for f in comp.get("formatos") or []:
            fmt = dict(f.get("formato") or {})
            for k in ("temporada", "observacoes"):
                if f.get(k) is not None:
                    fmt.setdefault(k, f[k])
            if comp.get("observacoes"):
                fmt.setdefault("observacoes_competicao", comp["observacoes"])
            fts = f.get("fontes") or fmt.get("fontes") or comp.get("fontes") or []
            c.execute("INSERT INTO formatos VALUES (?,?,?,?,?)",
                      (cid_, f.get("valido_de"), f.get("valido_ate"), json.dumps(fmt, ensure_ascii=False),
                       json.dumps(fts, ensure_ascii=False)))
    ajustar_conmebol(c)
    tem_fmt = {r[0] for r in c.execute("SELECT DISTINCT competicao_id FROM formatos")}
    for comp, m in comp_meta.items():
        if comp in tem_fmt or m["ambito"] != "nacional":
            continue
        n = len(lc[comp])
        cfg = next((d for d in ligas_mod.get(cod3.get(m["pais"]) or "", []) if d.get("divisao") == m["div"]), None)
        fmt = {"participantes": n, "pontos_vitoria": 3, "desempates": ["pontos", "vitorias", "saldo", "gols_pro"],
               "fases": [{"nome": "Pontos corridos", "tipo": "pontos_corridos", "turnos": 2}],
               "rebaixados": (cfg or {}).get("rebaixadosDireto", 2 if n >= 10 else 1),
               "observacoes": "formato provisório derivado do mod Brasfoot (conf_ligas_nacionais) e do nº real de participantes",
               "brasfoot_cfg": cfg}
        c.execute("INSERT INTO formatos VALUES (?,?,?,?,?)",
                  (comp, 2025, None, json.dumps(fmt, ensure_ascii=False), json.dumps([comp_fonte.get(comp)])))
    ces = comum.ler_json("mod_estaduais.json", {}) or {}
    for comp, m in comp_meta.items():
        if comp in tem_fmt or m["ambito"] != "estadual":
            continue
        dv = m.get("div") or 1
        cfg = next((d for d in ces.get(m["uf"], []) if d.get("divisao") == dv), None)
        n_est = len(lc[comp])
        niveis_uf = sorted(mm.get("div") or 1 for mm in comp_meta.values()
                           if mm["ambito"] == "estadual" and mm.get("uf") == m["uf"])
        ultimo = dv == max(niveis_uf)
        fmt = {"participantes": n_est, "pontos_vitoria": 3, "desempates": ["pontos", "vitorias", "saldo", "gols_pro"],
               "fases": fases_estadual(n_est, cfg),
               "rebaixados": 0 if ultimo else (cfg or {}).get("nRebaixados", 2),
               "observacoes": "formato provisório derivado do mod Brasfoot (conf_estadual)", "brasfoot_ces": cfg}
        fts = [f"file:///C:/Brasfoot22-23/conf_estadual/{m['uf']}.ces"]
        if dv > 1:
            sup = f"est-{m['uf'].lower()}" + ("" if dv == 2 else f"-{dv - 1}")
            fmt["divisao"] = dv
            fmt["sobe_para"] = sup
            fmt["promovidos"] = 2
            fmt["observacoes"] = ("divisão inferior do estadual: participantes 2026 da Wikipedia; formato padrão "
                                  f"({'turno único + mata-mata entre os 4/8 primeiros' if n_est <= 16 else 'grupos + mata-mata'}; os 2 finalistas sobem para "
                                  f"{sup}; {'sem rebaixamento (última divisão)' if ultimo else 'os 2 últimos caem'}) "
                                  "até haver regulamento com fonte")
            if (est_inf.get(comp) or {}).get("observacoes"):
                fmt["observacoes"] += "; " + est_inf[comp]["observacoes"]
            if comp in est26.get("divisoes", {}):
                fmt["observacoes"] = fmt["observacoes"].replace("participantes 2026 da Wikipedia",
                                                                "participantes 2026 conferidos (estaduais_2026.json)")
                if completadas.get(comp):
                    fmt["observacoes"] += f"; completada com {completadas[comp]} clubes do estado fora das divisões"
                if est26["divisoes"][comp].get("obs"):
                    fmt["observacoes_reais"] = est26["divisoes"][comp]["obs"]
                fts = (est26["divisoes"][comp].get("fontes") or []) + fts
            if m.get("ficticia"):
                fmt["ficticia"] = True
                fmt["observacoes"] = (f"divisão FICTÍCIA do FUTÉBAS (não existe em 2026): os clubes do estado fora das "
                                      f"divisões reais jogam aqui; os 2 finalistas sobem para {sup}"
                                      + ("" if ultimo else "; os 2 últimos caem") + ". Formato padrão (requisito, 14/11/2026)")
            fts.insert(0, comp_fonte.get(comp))
        c.execute("INSERT INTO formatos VALUES (?,?,?,?,?)",
                  (comp, 2026, None, json.dumps(fmt, ensure_ascii=False), json.dumps([f for f in fts if f])))
    # formatos da pesquisa (regulamentos) num estado que ganhou divisão abaixo: a de cima passa a ter queda
    ajuste_reb = []
    for comp, m in comp_meta.items():
        if m["ambito"] != "estadual" or comp not in tem_fmt:
            continue
        dv = m.get("div") or 1
        if not any(mm["ambito"] == "estadual" and mm.get("uf") == m["uf"] and (mm.get("div") or 1) > dv for mm in comp_meta.values()):
            continue
        for rid, ftxt in c.execute("SELECT rowid, formato FROM formatos WHERE competicao_id=?", (comp,)).fetchall():
            fm = json.loads(ftxt)
            if not fm.get("rebaixados"):
                fm["rebaixados"] = 2
                fm["observacoes"] = (fm.get("observacoes") or "") + "; 2 caem para a divisão de baixo (FUTÉBAS: estado com divisão abaixo)"
                c.execute("UPDATE formatos SET formato=? WHERE rowid=?", (json.dumps(fm, ensure_ascii=False), rid))
                ajuste_reb.append(comp)
    print(f"  rebaixamento acrescentado (divisão nova abaixo): {sorted(set(ajuste_reb))}")
    for k in pesq["calendarios"]:
        if isinstance(k, dict) and k.get("competicao_id"):
            c.execute("INSERT INTO calendarios VALUES (?,?,?,?,?,?)",
                      (k.get("pais"), k["competicao_id"], k.get("temporada"), k.get("de"), k.get("ate"),
                       json.dumps(k.get("fontes"), ensure_ascii=False)))
    for k in pesq["regras"]:
        if isinstance(k, dict) and k.get("regra"):
            c.execute("INSERT INTO regras VALUES (?,?,?,?,?)",
                      (k["regra"], k.get("valor") if isinstance(k.get("valor"), str) else json.dumps(k.get("valor"), ensure_ascii=False),
                       k.get("valido_de"), k.get("ambito"), k.get("fonte")))

    # ---------------- seleções
    grupos = {}
    nomes_sel = {}
    for gk, lst in (copa.get("grupos") or {}).items():
        for t in lst:
            i3 = tm_iso({"Ivory Coast": "Côte d'Ivoire", "DR Congo": "Congo, The Democratic Republic of the",
                         "Cape Verde": "Cabo Verde", "Turkey": "TUR", "South Korea": "Korea, Republic of",
                         "Czech Republic": "Czechia", "Curaçao": "Curaçao", "Iran": "Iran"}.get(t["texto"], t["texto"]))
            if t["texto"] == "Turkey":
                i3 = "TUR"
            if i3:
                grupos[i3] = gk
                nomes_sel[i3] = copa["fontes"][gk]
            else:
                print("   seleção sem ISO:", t["texto"])
    tecnicos = {}
    for r in nt.itertuples():
        i3 = tm_iso(r.country_name) or tm_iso(r.name)
        if i3 and isinstance(r.coach_name, str):
            tecnicos[i3] = r.coach_name
    pesq_sel = {s["pais"]: s for s in pesq["selecoes"] if s.get("pais")}
    s26 = pesq.get("selecoes_2026") or {}
    rk = s26.get("ranking_fifa_inicio") or {}
    for l in rk.get("linhas", []):
        if l.get("pais"):
            pesq_sel.setdefault(l["pais"], {})["ranking_fifa"] = l.get("pos")
            pesq_sel[l["pais"]]["fonte"] = rk.get("fonte")
    for g_ in (s26.get("copa_2026") or {}).get("grupos", []):
        for x in g_.get("selecoes", []):
            if x.get("pais"):
                pesq_sel.setdefault(x["pais"], {})["grupo_copa_2026"] = g_.get("grupo")
    for t in s26.get("tecnicos", []):
        if t.get("pais"):
            pesq_sel.setdefault(t["pais"], {})["tecnico"] = t.get("em_1_jan_2026") or t.get("tecnico")
    for i3, v in pesq_sel.items():
        if v.get("ranking_fifa"):
            c.execute("UPDATE paises SET ranking_fifa=? WHERE id=?", (v["ranking_fifa"], i3))
    for i3 in paises:
        ps = pesq_sel.get(i3, {})
        grupo = ps.get("grupo_copa_2026") or grupos.get(i3)
        fonte = ps.get("fonte") or nomes_sel.get(i3) or (TM_URL if i3 in ranking else None)
        c.execute("INSERT INTO selecoes VALUES (?,?,?,?,?,?)",
                  (i3, ps.get("tecnico") or tecnicos.get(i3), ps.get("ranking_fifa") or ranking.get(i3),
                   1 if grupo else 0, grupo, fonte if isinstance(fonte, str) else json.dumps(fonte, ensure_ascii=False)))
        tsel = ps.get("tecnico") or tecnicos.get(i3)
        if tsel:
            c.execute("INSERT INTO tecnicos (nome, pais, selecao, origem, fonte_id) VALUES (?,?,?,?,?)",
                      (tsel, None, i3, "real", b.fonte(fonte, "base_dados", 3) if isinstance(fonte, str) else None))

    # ---------------- Kings League
    kst = montar_kings(b, pesq.get("kings"))
    print(f"  índice de fotos da Kings: {normalizar.indice_fotos_kings(c)} chaves com o nome reparado")

    # ---------------- "Sem Contrato" do mod = jogadores livres (passe livre), não clubes
    falsos = [r[0] for r in c.execute("SELECT id, nome FROM clubes").fetchall()
              if "sem contrato" in norm(c.execute("SELECT nome FROM clubes WHERE id=?", (r[0],)).fetchone()[0] or "")]
    if falsos:
        q = ",".join("?" * len(falsos))
        c.execute(f"DELETE FROM jogadores WHERE origem='gerado' AND clube_id IN ({q})", falsos)
        # remove quem já está num clube de verdade (mesmo nome + nascimento)
        c.execute(f"""DELETE FROM jogadores WHERE clube_id IN ({q}) AND EXISTS (
                        SELECT 1 FROM jogadores o WHERE o.clube_id IS NOT NULL AND o.clube_id NOT IN ({q})
                        AND lower(o.nome)=lower(jogadores.nome) AND (IFNULL(o.nascimento,'')=IFNULL(jogadores.nascimento,'')
                             OR (o.tm_id IS NOT NULL AND jogadores.tm_id IS NULL AND instr(o.nome, ' ') > 0
                                 AND abs(CAST(substr(o.nascimento,1,4) AS INT) - CAST(substr(jogadores.nascimento,1,4) AS INT)) <= 5)))""",
                  falsos + falsos)
        c.execute(f"DELETE FROM jogador_fontes WHERE jogador_id NOT IN (SELECT id FROM jogadores)")
        livres = c.execute(f"UPDATE jogadores SET clube_id=NULL, emprestado_de=NULL, contrato_ate=NULL, camisa=NULL WHERE clube_id IN ({q})", falsos).rowcount
        for t in ("ligas_clubes", "tabelas_iniciais", "tecnicos"):
            c.execute(f"DELETE FROM {t} WHERE clube_id IN ({q})", falsos)
        c.execute(f"DELETE FROM clube_fontes WHERE clube_id IN ({q})", falsos)
        c.execute(f"DELETE FROM clubes WHERE id IN ({q})", falsos)
        print(f"  'Sem Contrato' do mod: {len(falsos)} pseudo-clubes removidos, {livres} jogadores em passe livre")

    # ---------------- formatos: 2026 válido em toda divisão, cadeia de acesso/queda, fases das copas
    rel_fmt = normalizar.normalizar_formatos(c)
    print("  formatos normalizados:\n" + normalizar.resumo(rel_fmt))

    # ---------------- camada histórica (carreira, lendas, onze_historico, campeoes_historicos)
    import historia
    historia.aplicar(b)

    # ---------------- clubes duplicados (estaduais_2026.json, 'fusoes'): um id só, jogadores reais no que fica
    fusoes_feitas = fundir_clubes(c, est26.get("fusoes", []), fotos_dir)
    print(f"  clubes duplicados fundidos: {fusoes_feitas}")
    for f_ in fusoes_feitas:
        clubes.pop(f_["remover"], None)

    # ---------------- normalização final: ids duplicados de competição e texto de todas as colunas de nome
    uni = normalizar.unificar_competicoes(c)
    txt = normalizar.limpar_banco(c)
    print(f"  competições unificadas: {uni}\n  texto normalizado: {txt}")
    print(f"  texto (sobras): {normalizar.problemas_texto(c)}")
    sem_f = c.execute("SELECT (SELECT COUNT(*) FROM carreira WHERE fonte_id IS NULL) + "
                      "(SELECT COUNT(*) FROM lendas WHERE fonte_id IS NULL)").fetchone()[0]
    assert sem_f == 0, f"{sem_f} linhas de carreira/lendas sem fonte"

    # ---------------- validação e meta
    sem = c.execute("""SELECT COUNT(*) FROM jogadores j WHERE origem='real'
                       AND NOT EXISTS (SELECT 1 FROM jogador_fontes f WHERE f.jogador_id=j.id)""").fetchone()[0]
    assert sem == 0, f"{sem} jogadores reais sem fonte"
    curtos = c.execute("SELECT COUNT(*) FROM (SELECT clube_id, COUNT(*) n FROM jogadores WHERE clube_id IS NOT NULL GROUP BY clube_id HAVING n < 18)").fetchone()[0]
    sem_jog = c.execute("SELECT COUNT(*) FROM clubes WHERE id NOT IN (SELECT DISTINCT clube_id FROM jogadores WHERE clube_id IS NOT NULL)").fetchone()[0]
    assert curtos == 0 and sem_jog == 0, f"clubes com menos de 18: {curtos}, sem jogadores: {sem_jog}"
    meta = {"versao_esquema": "2", "gerado_em": dt.datetime.now().isoformat(timespec="seconds"),
            "data_inicial": "2026-01-01", "gerador": "coleta/montar_banco.py",
            "modelo_forca_valor": json.dumps([round(float(x), 4) for x in coef]),
            "fontes_principais": json.dumps(["mod Brasfoot 2026 (C:/Brasfoot22-23)", TM_URL, EA_URL, "Wikipedia"]),
            "kings": json.dumps(kst), "elencos_ate": ELENCOS_ATE,
            "transferencias_2627": json.dumps(resumo_transf, ensure_ascii=False),
            "transferencias_br_2026": json.dumps(resumo_br, ensure_ascii=False),
            "transferencias_br_noticias_2026": json.dumps(resumo_not, ensure_ascii=False),
            "duplicatas_mod_fundidas": str(len(fundidos_mod)),
            "estaduais_2026": json.dumps({"conferidos": dict(est_corr), "ficticias": ficticias,
                                          "completadas": completadas, "rebaixamento_acrescentado": sorted(set(ajuste_reb)),
                                          "clubes_fundidos": fusoes_feitas}, ensure_ascii=False),
            "normalizacao": json.dumps({"formatos": {k: len(v) for k, v in rel_fmt.items()}, "competicoes": uni,
                                        "texto": txt}, ensure_ascii=False)}
    c.executemany("INSERT INTO meta VALUES (?,?)", list(meta.items()))
    c.commit()
    nreal = c.execute("SELECT COUNT(*) FROM jogadores WHERE origem='real'").fetchone()[0]
    ngen = c.execute("SELECT COUNT(*) FROM jogadores WHERE origem='gerado'").fetchone()[0]
    print(f"OK: {len(clubes)} clubes, {nreal} reais, {ngen} gerados, {nesc} escudos, {nkit} uniformes, "
          f"{nfotos} fotos, {ntab} linhas de tabela inicial, kings {kst}. ({dt.datetime.now() - t0})")
    c.close()


def montar_kings(b: Banco, k):
    if not k:
        return {}
    c = b.c
    est = Counter()

    def js(v):
        return None if v is None else (v if isinstance(v, str) else json.dumps(v, ensure_ascii=False))

    # imagens oficiais baixadas por coleta_kings_imagens.py (kingsleague.pro / CDN kama.football)
    kimg = comum.ler_json("kings_imagens.json", {}) or {}
    for grupo in ("times", "ligas", "trofeus"):
        for v in (kimg.get(grupo) or {}).values():
            b.fonte(v["url"], "imagem", 4, f"Kings League (site oficial): {grupo}", "uso_pessoal")
    if kimg.get("geral"):
        b.fonte(kimg["geral"]["url"], "imagem", 4, "Kings League (site oficial): logo geral -> assets/kings/kings-league.png",
                "uso_pessoal")
    for l in k.get("ligas", []):
        c.execute("INSERT OR REPLACE INTO kings_ligas VALUES (?,?,?,?,?,?,?,?,?)",
                  (l["id"], l.get("nome"), l.get("pais"), l.get("inicio"), js(l.get("regras")), js(l.get("draft")),
                   js(l.get("fontes")), (kimg.get("ligas", {}).get(l["id"]) or {}).get("png") or l.get("logo_png"),
                   (kimg.get("trofeus", {}).get(l["id"]) or {}).get("png")))
    for t in k.get("times", []):
        c.execute("INSERT OR REPLACE INTO kings_times VALUES (?,?,?,?,?,?,?,?)",
                  (t["id"], t.get("liga_id"), t.get("nome"), t.get("presidente"),
                   (kimg.get("times", {}).get(t["id"]) or {}).get("png") or t.get("escudo_png") or t.get("escudo_url"),
                   t.get("cor1"), t.get("cor2"), js(t.get("fonte"))))
    idx = defaultdict(list)
    for jid, n1, n2, nasc in c.execute("SELECT id, nome, nome_conhecido, nascimento FROM jogadores WHERE origem='real'"):
        for n in {norm(n1), norm(n2)}:
            idx[n].append((jid, nasc))
    elencos, est_q = normalizar.corrigir_elencos_kings(k.get("elencos", []))
    print(f"  kings: nomes com '?' (FUT.AE): {dict(est_q)}")
    est.update({f"interrogacao_{x}": v for x, v in est_q.items()})
    for e in elencos:
        nome = e.get("nome_fonte") or e.get("nome") or ""
        nasc = e.get("data_nascimento") or e.get("nascimento")
        cands = idx.get(norm(nome), [])
        if nasc:
            cands = [x for x in cands if x[1] == str(nasc)[:10]]
        jid = cands[0][0] if len(cands) == 1 else None
        est["ligados" if jid else "sem_ligacao"] += 1
        c.execute("INSERT INTO kings_elencos VALUES (?,?,?,?,?,?)",
                  (e.get("time_id"), e.get("temporada"), jid, nome, e.get("tipo"), js(e.get("fonte"))))
    return dict(est)


if __name__ == "__main__":
    sys.exit(main())
