"""Roda a coleta inteira na ordem certa. Tudo tem cache: rodar de novo é rápido e não
baixa nada que já foi baixado.

  python coleta/rodar_tudo.py            # coleta + banco + fotos (300) + relatório
  python coleta/rodar_tudo.py --so-banco # só monta o banco e o relatório a partir do cache
  python coleta/rodar_tudo.py --fotos 1000

Para garantir que nada vai para a rede: defina COLETA_OFFLINE=1.
"""
import subprocess
import sys
from pathlib import Path

AQUI = Path(__file__).resolve().parent
PY = sys.executable


def rodar(*args):
    print(">>", " ".join(args), flush=True)
    subprocess.run([PY, str(AQUI / args[0]), *args[1:]], check=True, cwd=AQUI)


def main():
    fotos = "300"
    if "--fotos" in sys.argv:
        fotos = sys.argv[sys.argv.index("--fotos") + 1]
    if "--so-banco" not in sys.argv:
        rodar("coleta_participantes.py")          # Wikipedia: tabelas das temporadas
        rodar("coleta_campeoes.py")               # Wikipedia: campeões e vices
        rodar("coleta_clubes.py")                 # Wikidata: clubes, cores, participantes resolvidos
        rodar("coleta_rivais.py")                 # Wikidata + Wikipedia: clássicos
        rodar("importar_futae.py")                # FUT.AE (somente leitura)
        rodar("coleta_jogadores_wikidata.py")     # Wikidata: jogadores (lista, detalhes, sexo)
    rodar("montar_banco.py")
    if "--so-banco" not in sys.argv:
        rodar("fotos.py", fotos)                  # Commons + rembg (retomável)
        rodar("montar_banco.py")                  # grava as fotos no banco
    rodar("relatorio_cobertura.py")


if __name__ == "__main__":
    main()
