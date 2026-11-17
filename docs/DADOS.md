# Dados do mundo

A base do mundo (`dados/mundo.sqlite`, ~100 MB) e os assets visuais (escudos, fotos de jogadores,
bandeiras, logos de competições, camisas e troféus) **não acompanham o repositório**. Eles vêm de
fontes de terceiros, e por isso ficam fora do controle de versão (veja o `.gitignore`).

## Jogar sem a base real

O projeto traz um gerador de mundo fictício (o mesmo dos testes), com clubes, jogadores e
competições de exemplo:

```bash
mvn -f jogo/pom.xml compile exec:java@mundo-ficticio   # cria dados/mundo.sqlite
mvn -f jogo/pom.xml javafx:run
```

Sem os assets, o jogo mostra silhuetas no lugar das fotos e escudos genéricos.

## Gerar a base real

A pasta `coleta/` tem o pipeline em Python que monta `dados/mundo.sqlite` a partir de bases
públicas (Wikidata, Wikipédia e Transfermarkt) e de uma instalação local do Brasfoot com o mod 2026
(caminho em `BRASFOOT_DIR`). O contrato do banco está em [`arquitetura-v2.md`](arquitetura-v2.md).

```bash
python coleta/rodar_tudo.py --so-banco   # monta o banco a partir do cache da coleta
python coleta/rodar_tudo.py              # coleta completa (lenta; respeita limites de requisição)
```

Essas fontes não são redistribuídas aqui; quem rodar a coleta precisa ter acesso a elas.
