"""Importa jogadores do FUT.AE (fonte Transfermarkt) — SOMENTE LEITURA.

- Banco: memi.sqlite aberto com file:...?mode=ro (nunca escreve na pasta do FUT.AE).
- Arquivo bruto players.csv.gz (somente leitura) completa pé, altura, local de nascimento,
  jogos pela seleção e maior valor de mercado da carreira.
- Só entram jogadores com fonte 'transfermarkt' e data de nascimento (lendas sem data ficam fora).

Saída: coleta/intermediario/futae.json  {tm_id: {...}}
"""
from __future__ import annotations
import os

import csv
import gzip
import sqlite3
from pathlib import Path

import comum
from paises import iso3

FUTAE = Path(os.environ.get("FUTAE_DIR", "FUT.AE/data"))

POS_TM = {
    "Goalkeeper": "GOL", "Centre-Back": "ZAG", "Right-Back": "LD", "Left-Back": "LE",
    "Defensive Midfield": "VOL", "Central Midfield": "MC", "Attacking Midfield": "MEI",
    "Right Winger": "PD", "Right Midfield": "PD", "Left Winger": "PE", "Left Midfield": "PE",
    "Centre-Forward": "CA", "Second Striker": "CA",
}
POS_GERAL = {"Attack": "CA", "Defender": "ZAG", "Midfield": "MC", "Goalkeeper": "GOL"}
PE = {"right": "D", "left": "E", "both": "A"}


def main():
    con = sqlite3.connect(f"file:{(FUTAE / 'memi.sqlite').as_posix()}?mode=ro", uri=True)
    db = {}
    q = """SELECT p.tm_player_id, p.name, p.birth_date, p.nationality, p.club_name, p.overall,
                  p.overall_source, p.market_value_eur, p.season_last_active, p.photo_path, p.photo_url
           FROM players p JOIN player_sources s ON s.player_id = p.id AND s.source = 'transfermarkt'
           WHERE p.tm_player_id IS NOT NULL"""
    for r in con.execute(q):
        db[int(r[0])] = r
    con.close()
    out = {}
    with gzip.open(FUTAE / "raw" / "players.csv.gz", "rt", encoding="utf-8") as f:
        for x in csv.DictReader(f):
            tm = int(x["player_id"])
            r = db.get(tm)
            nasc = (x["date_of_birth"] or (r[2] if r else "") or "")[:10]
            if not nasc:
                continue
            sub, geral = x["sub_position"], x["position"]
            pos = POS_TM.get(sub) or POS_GERAL.get(geral)
            foto = None
            if r and r[9]:
                base = FUTAE / ("photos" if not r[9].startswith("cartola") else "assets") / r[9]
                foto = str(base)
            out[tm] = {
                "tm_id": tm, "nome": (r[1] if r else None) or x["name"],
                "prenome": x["first_name"], "sobrenome": x["last_name"],
                "nasc": nasc, "pais": iso3(x["country_of_citizenship"]),
                "pais_nome": x["country_of_citizenship"],
                "pais_nasc": iso3(x["country_of_birth"]), "cidade_nasc": (x["city_of_birth"] or "").strip() or None,
                "posicao": pos, "pos_incerta": sub == "" or pos is None,
                "pe": PE.get(x["foot"]), "altura": int(x["height_in_cm"]) if x["height_in_cm"] else None,
                "caps": int(x["international_caps"]) if x["international_caps"] else None,
                "valor": int(float(x["market_value_in_eur"])) if x["market_value_in_eur"] else None,
                "valor_max": int(float(x["highest_market_value_in_eur"])) if x["highest_market_value_in_eur"] else None,
                "overall": r[5] if r else None, "overall_fonte": r[6] if r else None,
                "clube": x["current_club_name"] or (r[4] if r else None),
                "clube_tm": int(x["current_club_id"]) if x["current_club_id"] else None,
                "liga": x["current_club_domestic_competition_id"], "ultima_temp": int(x["last_season"]) if x["last_season"] else None,
                "url": x["url"].replace("transfermarkt.co.uk", "transfermarkt.com"),
                "foto": foto, "foto_url": (r[10] if r else None) or x["image_url"],
            }
    comum.salvar_json("futae.json", out)
    br = sum(1 for v in out.values() if v["pais"] == "BRA")
    bra1 = sum(1 for v in out.values() if v["liga"] == "BRA1")
    print(f"FUT.AE/Transfermarkt com nascimento: {len(out)}; brasileiros {br}; em clubes BRA1 {bra1}")


if __name__ == "__main__":
    main()
