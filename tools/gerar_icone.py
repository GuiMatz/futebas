"""Ícone do FUTÉBAS (janela e .ico do executável): agora vem do gerador da logo.

Uso: python tools/gerar_icone.py [--conceito N]   (o mesmo que tools/gerar_logo.py --sem-esbocos)
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gerar_logo  # noqa: E402

if __name__ == "__main__":
    if "--sem-esbocos" not in sys.argv:
        sys.argv.append("--sem-esbocos")
    gerar_logo.main()
