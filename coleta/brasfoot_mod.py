"""Leitor do mod Brasfoot 2026 instalado em C:\\Brasfoot22-23 (SOMENTE LEITURA, uso pessoal).

- teams/*.ban  -> objetos Java serializados (classe e.t = time, e.g = jogador). Layout em
  docs/referencias/brasfoot.md, seções 2.1 a 2.3.
- conf_ligas_nacionais/*.cfg -> divisões por país (sem lista de times: o jogo distribui por nível).
- conf_estadual/*.ces -> estaduais.
- bf22-23.exe é lido como ZIP (nunca executado) só para a tabela de países (default_lang.xml)
  e as listas de nomes/sobrenomes por país.

Saída: coleta/intermediario/mod_times.json, mod_ligas.json, mod_estaduais.json, mod_paises.json
Nunca escreve em C:\\Brasfoot22-23.
"""
from __future__ import annotations
import os

import datetime as dt
import re
import sys
import zipfile
from pathlib import Path

import javaobj.v2 as jo
from javaobj.v2.beans import JavaInstance

import comum

MOD = Path(os.environ.get("BRASFOOT_DIR", r"C:\Brasfoot22-23"))

# código numérico do Brasfoot (default_lang.xml, P0..P223) -> id de país do FUTÉBAS (ISO-3; ENG/SCO/WAL/NIR p/ Reino Unido)
ISO = {
    0: "AFG", 1: "ZAF", 2: "ALB", 3: "DEU", 4: "AND", 5: "AGO", 6: "AIA", 7: "ATG", 8: "CUW", 9: "SAU",
    10: "DZA", 11: "ARG", 12: "ARM", 13: "ABW", 14: "AUS", 15: "AUT", 16: "AZE", 17: "BHS", 18: "BHR",
    19: "BGD", 20: "BRB", 21: "BEL", 22: "BLZ", 23: "BEN", 24: "BMU", 25: "BLR", 26: "BOL", 27: "BIH",
    28: "BWA", 29: "BRA", 30: "BRN", 31: "BGR", 32: "BFA", 33: "BDI", 34: "BTN", 35: "CPV", 36: "CMR",
    37: "KHM", 38: "CAN", 39: "QAT", 40: "KAZ", 41: "TCD", 42: "CHL", 43: "CHN", 44: "CYP", 45: "TLS",
    46: "COL", 47: "COG", 48: "PRK", 49: "KOR", 50: "CIV", 51: "CRI", 52: "HRV", 53: "CUB", 54: "DNK",
    55: "DJI", 56: "DMA", 57: "EGY", 58: "SLV", 59: "ARE", 60: "ECU", 61: "ERI", 62: "SCO", 63: "SVK",
    64: "SVN", 65: "ESP", 66: "EST", 67: "ETH", 68: "USA", 69: "FJI", 70: "FIN", 71: "PHL", 72: "FRA",
    73: "GAB", 74: "GMB", 75: "GHA", 76: "GEO", 77: "GRD", 78: "GRC", 79: "GTM", 80: "GUY", 81: "GIN",
    82: "GNB", 83: "GNQ", 84: "HTI", 85: "NLD", 86: "HND", 87: "HKG", 88: "HUN", 89: "YEM", 90: "CYM",
    91: "COK", 92: "FRO", 93: "SLB", 94: "VGB", 95: "IND", 96: "IDN", 97: "ENG", 98: "IRN", 99: "IRQ",
    100: "IRL", 101: "NIR", 102: "ISL", 103: "ISR", 104: "ITA", 105: "MNE", 106: "JAM", 107: "JPN",
    108: "JOR", 109: "KEN", 110: "XKX", 111: "KWT", 112: "LAO", 113: "LSO", 114: "LVA", 115: "LBN",
    116: "LBY", 117: "LBR", 118: "LIE", 119: "LTU", 120: "LUX", 121: "MAC", 122: "MKD", 123: "MDG",
    124: "MYS", 125: "MWI", 126: "MDV", 127: "MLI", 128: "MLT", 129: "MAR", 130: "MRT", 131: "MEX",
    132: "MMR", 133: "MOZ", 134: "MDA", 135: "MCO", 136: "MNG", 137: "NAM", 138: "NPL", 139: "NIC",
    140: "NER", 141: "NGA", 142: "NOR", 143: "NZL", 144: "OMN", 145: "WAL", 146: "PSE", 147: "PAN",
    148: "PNG", 149: "PAK", 150: "PRY", 151: "PER", 152: "POL", 153: "PRI", 154: "PRT", 155: "KGZ",
    156: "CAF", 157: "COD", 158: "DOM", 159: "CZE", 160: "ROU", 161: "RWA", 162: "RUS", 163: "WSM",
    164: "SMR", 165: "LCA", 166: "KNA", 167: "STP", 168: "VCT", 169: "SEN", 170: "SLE", 171: "SRB",
    172: "SYC", 173: "SGP", 174: "SYR", 175: "SOM", 176: "LKA", 177: "SWZ", 178: "SDN", 179: "SWE",
    180: "CHE", 181: "SUR", 182: "TJK", 183: "THA", 184: "PYF", 185: "TWN", 186: "TZA", 187: "TGO",
    188: "TON", 189: "TTO", 190: "TUN", 191: "TKM", 192: "TUR", 193: "UKR", 194: "UGA", 195: "URY",
    196: "UZB", 197: "VUT", 198: "VEN", 199: "VNM", 200: "ZMB", 201: "ZWE", 202: "COM", 203: "FSM",
    204: "MHL", 205: "MUS", 206: "NRU", 207: "PLW", 208: "KIR", 209: "SSD", 210: "TUV", 211: "VIR",
    212: "MSR", 213: "TCA", 214: "ASM", 215: "NCL", 216: "GIB", 217: "GLP", 218: "GUM", 219: "MTQ",
    220: "GUF", 221: "BES", 222: "MAF", 223: "SXM",
}
UFS = ["AC", "AL", "AM", "AP", "BA", "CE", "DF", "ES", "GO", "MA", "MG", "MS", "MT", "PA", "PB", "PE",
       "PI", "PR", "RJ", "RN", "RO", "RR", "RS", "SC", "SE", "SP", "TO"]


def _py(o):
    if type(o).__name__ in ("JavaList", "JavaArray") or isinstance(o, list):
        return [_py(x) for x in o]
    if isinstance(o, JavaInstance):
        return {k.name: _py(v) for _fd, vals in o.field_data.items() for k, v in vals.items()}
    if isinstance(o, (int, float, bool, type(None))):
        return o
    return str(o)


def ler(p: Path):
    with open(p, "rb") as f:
        return _py(jo.load(f))


def paises_nomes() -> dict[int, str]:
    z = zipfile.ZipFile(MOD / "bf22-23.exe")
    x = z.read("arquivos/default_lang.xml").decode("utf-8", "replace")
    return {int(k): v.strip() for k, v in re.findall(r'<entry key="P(\d+)">([^<]*)<', x)}


def nomes_mod() -> dict[str, dict[str, list[str]]]:
    """Listas de nomes/sobrenomes por país (código de 3 letras do Brasfoot)."""
    z = zipfile.ZipFile(MOD / "bf22-23.exe")
    out: dict[str, dict[str, list[str]]] = {}
    for n in z.namelist():
        m = re.match(r"arquivos/(names|surnames)/([A-Z]{3})\.txt$", n)
        if m:
            txt = z.read(n).decode("utf-8", "replace")
            out.setdefault(m.group(2), {})[m.group(1)] = [l.strip() for l in txt.splitlines() if l.strip()]
    return out


def main():
    times = []
    erros = 0
    arqs = sorted((MOD / "teams").glob("*.ban"))
    for i, p in enumerate(arqs):
        try:
            d = ler(p)
        except Exception as e:  # noqa: BLE001
            erros += 1
            print("  erro", p.name, e)
            continue
        mt = dt.datetime.fromtimestamp(p.stat().st_mtime).strftime("%Y-%m-%d")
        jog = []
        for x in d.get("l") or []:
            jog.append({"nome": x.get("a"), "pais": ISO.get(x.get("c")), "idade": x.get("d"),
                        "pos": x.get("e"), "lado": x.get("i"), "car": [x.get("g"), x.get("h")],
                        "titular": x.get("f"), "estrela": bool(x.get("b")), "hash": x.get("hash"),
                        "tid": x.get("tid") or None})
        b = d.get("b")
        uf = UFS[b] if d.get("a") == 29 and isinstance(b, int) and 0 <= b < 27 else None
        esc = MOD / "teams" / "escudos" / (p.stem + ".png")
        times.append({
            "arquivo": p.name, "base": p.stem, "data_arquivo": mt,
            "nome": d.get("e"), "pais": ISO.get(d.get("a")), "pais_cod": d.get("a"), "uf": uf,
            "nivel": d.get("c"), "reputacao": d.get("n"), "estadio": d.get("f"), "capacidade": d.get("g"),
            "tecnico": d.get("h"), "tecnico_pais": ISO.get(d.get("i")),
            "cor1": d.get("cor1"), "cor2": d.get("cor2"), "modelo_camisa": d.get("o"),
            "escudo": str(esc) if esc.exists() else None, "jogadores": jog,
            "juniores": len(d.get("m") or []),
        })
        if i % 1000 == 0:
            print(f"  {i}/{len(arqs)}")
    comum.salvar_json("mod_times.json", times)
    ligas = {}
    for p in sorted((MOD / "conf_ligas_nacionais").glob("*.cfg")):
        try:
            ligas[p.stem] = ler(p)["a"]
        except Exception as e:  # noqa: BLE001
            print("  erro cfg", p.name, e)
    comum.salvar_json("mod_ligas.json", ligas)
    est = {}
    for p in sorted((MOD / "conf_estadual").glob("*.ces")):
        try:
            est[p.stem] = ler(p)["a"]
        except Exception as e:  # noqa: BLE001
            print("  erro ces", p.name, e)
    comum.salvar_json("mod_estaduais.json", est)
    pn = paises_nomes()
    cod3 = {}
    for k, v in ligas.items():
        if v:
            cod3[v[0].get("pais")] = k
    comum.salvar_json("mod_paises.json", {str(k): {"nome": v, "iso": ISO.get(k), "cod3": cod3.get(k)} for k, v in pn.items()})
    comum.salvar_json("mod_nomes.json", nomes_mod())
    print(f"times: {len(times)} (erros {erros}); ligas: {len(ligas)}; estaduais: {len(est)}")


if __name__ == "__main__":
    sys.exit(main())
