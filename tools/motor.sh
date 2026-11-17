#!/usr/bin/env bash
# Compila sÃ³ o motor (sem br.futebas.ui) em jogo/target-motor/motor e roda uma classe.
# uso: bash tools/motor.sh [Classe args...]   (ex.: ferramentas.MedirTaticas 20000)
RAIZ="$(cd "$(dirname "$0")/.." && pwd -W 2>/dev/null || pwd)"
J="$RAIZ/.ferramentas/jdk/bin"
M2="$(cygpath -m "$HOME/.m2/repository" 2>/dev/null || echo "$HOME/.m2/repository")"
CP="$M2/org/xerial/sqlite-jdbc/3.46.1.0/sqlite-jdbc-3.46.1.0.jar;$M2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar;$M2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar"
OUT="$RAIZ/jogo/target-motor/motor"
SRC="$RAIZ/jogo/src/main/java"
find "$SRC" -name '*.java' -not -path '*/ui/*' > "$RAIZ/jogo/target-motor/fontes.txt"
"$J/javac" -encoding UTF-8 -nowarn -d "$OUT" -cp "$CP" @"$RAIZ/jogo/target-motor/fontes.txt" || exit 1
[ $# -eq 0 ] && exit 0
if [ "$1" = "testes" ]; then
  # testes do motor (sem br.futebas.ui), sem o Maven: bash tools/motor.sh testes [br.futebas.x.ClasseTest ...]
  shift
  JU="$M2/org/junit"
  TCP="$JU/jupiter/junit-jupiter-api/5.10.3/junit-jupiter-api-5.10.3.jar;$JU/jupiter/junit-jupiter-engine/5.10.3/junit-jupiter-engine-5.10.3.jar;$JU/jupiter/junit-jupiter-params/5.10.3/junit-jupiter-params-5.10.3.jar;$JU/platform/junit-platform-commons/1.10.3/junit-platform-commons-1.10.3.jar;$JU/platform/junit-platform-engine/1.10.3/junit-platform-engine-1.10.3.jar;$JU/platform/junit-platform-launcher/1.10.3/junit-platform-launcher-1.10.3.jar;$M2/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar;$M2/org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar"
  TOUT="$RAIZ/jogo/target-motor/motor-testes"
  find "$RAIZ/jogo/src/test/java" -name '*.java' -not -path '*/ui/*' > "$RAIZ/jogo/target-motor/testes.txt"
  echo "$RAIZ/tools/junit/RodarTestes.java" >> "$RAIZ/jogo/target-motor/testes.txt"
  "$J/javac" -encoding UTF-8 -nowarn -d "$TOUT" -cp "$OUT;$CP;$TCP" @"$RAIZ/jogo/target-motor/testes.txt" || exit 1
  [ -d "$RAIZ/jogo/src/test/resources" ] && cp -r "$RAIZ/jogo/src/test/resources/." "$TOUT/"
  exec "$J/java" ${JOPTS:--Xmx2g -Xss8m} -cp "$TOUT;$OUT;$CP;$TCP" RodarTestes "$@"
fi
CLASSE="$1"; shift
# cópia própria das classes: dá para recompilar enquanto uma rodada longa continua
RUN="$RAIZ/jogo/target-motor/run-$$"
cp -r "$OUT" "$RUN"
"$J/java" ${JOPTS:--Xmx2g -Xss8m} -cp "$RUN;$CP" "br.futebas.$CLASSE" "$@"
st=$?
rm -rf "$RUN"
exit $st
