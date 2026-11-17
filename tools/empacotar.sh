#!/usr/bin/env bash
# Empacota o FUTÉBAS como aplicativo Windows (pasta com "FUTEBAS.exe") em dist/.
# Usa só o JDK local (.ferramentas/jdk): não instala nada e não pede administrador.
# Uso: bash tools/empacotar.sh            (gera dist/FUTEBAS/)
#      bash tools/empacotar.sh --zip      (também gera dist/FUTEBAS.zip)
#
# O que vai junto (só o que o jogo lê em tempo de execução):
#   dados/mundo.sqlite                 banco mestre do mundo de 2026
#   assets/escudos/                    escudos dos clubes
#   assets/bandeiras/                  bandeiras dos países
#   assets/fotos/                      fotos dos jogadores
#   assets/brasfoot-mod/trofeus/       troféus das competições
#   assets/brasfoot-mod/camisas/       camisas dos clubes (escalação)
# Escudos, fotos e arquivos do mod são de uso pessoal: o pacote não deve ir para repositório público.
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
JDK="$RAIZ/.ferramentas/jdk"
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"
NOME="FUTEBAS"          # nome do executável e da pasta (sem acento, para o Windows)
TITULO="FUTÉBAS"        # nome exibido
VERSAO="0.2.0"
ALVO="$RAIZ/jogo/target"
ENTRADA="$ALVO/pacote/entrada"
DIST="$RAIZ/dist"
ICONE="$RAIZ/assets/logo/futebas.ico"

for f in "$RAIZ/dados/mundo.sqlite" "$ICONE"; do
  [ -f "$f" ] || { echo "Faltando: $f" >&2; exit 1; }
done

echo "1/4 Compilando e copiando dependências..."
bash "$RAIZ/tools/mvn.sh" -q -DskipTests package
rm -rf "$ALVO/pacote"
mkdir -p "$ENTRADA"
bash "$RAIZ/tools/mvn.sh" -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="$ENTRADA"
JAR="$(ls "$ALVO"/futebas-*.jar | grep -v -- '-sources' | head -n 1)"
cp "$JAR" "$ENTRADA/"
JAR_NOME="$(basename "$JAR")"

echo "2/4 Copiando o banco mestre e os assets..."
mkdir -p "$ENTRADA/dados" "$ENTRADA/assets/brasfoot-mod"
cp "$RAIZ/dados/mundo.sqlite" "$ENTRADA/dados/"
# times customizados (Kings League IOS): dados e imagens (sem as capturas de tela)
if [ -d "$RAIZ/dados/custom" ]; then cp -r "$RAIZ/dados/custom" "$ENTRADA/dados/"; fi
if [ -d "$RAIZ/assets/custom" ]; then cp -r "$RAIZ/assets/custom" "$ENTRADA/assets/"; rm -rf "$ENTRADA/assets/custom/ios/capturas"; fi
for d in escudos bandeiras fotos kings competicoes logo; do
  if [ -d "$RAIZ/assets/$d" ]; then cp -r "$RAIZ/assets/$d" "$ENTRADA/assets/"; else echo "  aviso: assets/$d não existe"; fi
done
for d in trofeus camisas; do
  if [ -d "$RAIZ/assets/brasfoot-mod/$d" ]; then cp -r "$RAIZ/assets/brasfoot-mod/$d" "$ENTRADA/assets/brasfoot-mod/"; else echo "  aviso: assets/brasfoot-mod/$d não existe"; fi
done
du -sh "$ENTRADA/assets" "$ENTRADA/dados" 2>/dev/null || true

echo "3/4 Gerando o executável com jpackage..."
mkdir -p "$DIST"
rm -rf "$DIST/$NOME"
"$JDK/bin/jpackage" \
  --type app-image \
  --name "$NOME" \
  --app-version "$VERSAO" \
  --vendor "$TITULO" \
  --description "$TITULO - jogo de técnico de futebol (mundo inteiro, 2026)" \
  --input "$ENTRADA" \
  --main-jar "$JAR_NOME" \
  --main-class br.futebas.ui.Lancador \
  --icon "$ICONE" \
  --add-modules java.se,jdk.unsupported,jdk.charsets,jdk.localedata,jdk.crypto.ec,jdk.zipfs \
  --jlink-options "--strip-debug --no-header-files --no-man-pages" \
  --java-options "-Xss8m -Xmx3g -Dfile.encoding=UTF-8 -Dprism.lcdtext=false" \
  --dest "$DIST"

# ícone solto ao lado do exe: o atalho da área de trabalho aponta para ele (o Windows guarda o ícone
# antigo do exe em cache pelo caminho; um arquivo com nome novo força o ícone atual)
cp "$ICONE" "$DIST/$NOME/futebas-arranque.ico"
echo "4/4 Pronto: $DIST/$NOME/$NOME.exe"
if [ "${1:-}" = "--zip" ]; then
  (cd "$DIST" && powershell.exe -NoProfile -Command "Compress-Archive -Force -Path '$NOME' -DestinationPath '$NOME.zip'")
  echo "Zip: $DIST/$NOME.zip"
fi
