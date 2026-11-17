#!/usr/bin/env bash
# Roda o Maven do projeto com o JDK do projeto.
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="$RAIZ/.ferramentas/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
exec "$RAIZ/.ferramentas/maven/bin/mvn" -f "$RAIZ/jogo/pom.xml" "$@"
