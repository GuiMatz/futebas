@echo off
set RAIZ=%~dp0..
set JAVA_HOME=%RAIZ%\.ferramentas\jdk
set PATH=%JAVA_HOME%\bin;%PATH%
call "%RAIZ%\.ferramentas\maven\bin\mvn.cmd" -f "%RAIZ%\jogo\pom.xml" %*
