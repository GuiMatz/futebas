"""Copia coleta/cache/fotos_tm/<tm_id>.png para assets/fotos/<jogador_id>.png (só as que faltam).
Sem refazer o banco: o jogo procura assets/fotos/<id>.png quando foto_png está vazio. Não toca em assets/fotos/lendas."""
import shutil, sqlite3, sys
from pathlib import Path
RAIZ = Path(__file__).resolve().parent.parent
cache = RAIZ / "coleta" / "cache" / "fotos_tm"
dest = RAIZ / "assets" / "fotos"
db = sqlite3.connect(f"file:{(RAIZ / 'dados' / 'mundo.sqlite').as_posix()}?mode=ro", uri=True)
n = 0
for jid, tm in db.execute("select id, tm_id from jogadores where tm_id is not null"):
    src, dst = cache / f"{tm}.png", dest / f"{jid}.png"
    if src.exists() and not dst.exists():
        shutil.copyfile(src, dst); n += 1
print("fotos novas copiadas:", n, " total em assets/fotos:", len(list(dest.glob('*.png'))))
