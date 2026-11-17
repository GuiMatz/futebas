# Desempenho (telas e semana) — 12/11/2026

Requisito: "melhore isso (telas lentas) e faça testes de desempenho e cuide".

Medido com o banco completo (`dados/mundo.sqlite`: 8.370 clubes, 192 mil jogadores no banco, ~217 mil no
mundo), carreira do Flamengo na semana 21 e carreira só da Kings (kings-brasil-capim), JVM com `-Xmx3g`,
máquina de 12 núcleos **ocupada por outros processos** (os tempos de parede variam ±30% entre rodadas;
as tabelas trazem a 2ª abertura como a melhor de 3).

## Como medir

| O quê | Comando |
|---|---|
| Tudo (semanas, painéis, fim de temporada, Kings) com limites | `java ... br.futebas.ui.ferramentas.TestesDesempenho [--semanas 20] [--clube flamengo] [--temporadas 0] [--kings sim] [--fim sim] [--paineis sim] [--limite-painel 300] [--limite-semana 4000] [--limite-virada 20000] [--saida arq.txt]` |
| Memória e virada de temporada | `TestesDesempenho --paineis nao --kings nao --temporadas 5` |
| Como era antes (sem aquecimento, imagens lidas na tela) | `-Dmedir.aquecer=false -Dfutebas.imagensNaHora=true` |
| Painéis com janelas para cruzar com o JFR | `br.futebas.ui.ferramentas.MedirPaineis [semanas] [clube]` com `-Dmedir.janelas=true` |
| Imagens que a tela pediu e não estavam no cache | `-Dfutebas.imagens.log=true` (linhas `IMAGEM-FALTA`) |
| Conferir o índice de datas da semana | `-Dfutebas.verificarIndice=true` (lança erro se divergir do `Torneio`) |
| Testes JUnit (banco de teste, limites folgados) | `DesempenhoTest`, `IndiceDatasTest` (`-Dfutebas.desempenho.fator=2` dobra os limites; `-Dfutebas.desempenho=pular` pula) |

`TestesDesempenho` sai com código 1 se a 2ª abertura de algum painel passar de 300 ms, uma semana comum
de 4 s ou a virada de 20 s (2 = erro). As ferramentas com JavaFX ficam em `br.futebas.ui.ferramentas`
(o `tools/motor.sh` compila só o que está fora de `ui/`).

## Antes × depois

### Abrir o jogo → 1ª central

| | Antes | Depois |
|---|---:|---:|
| 1ª central (topo, navegação e Próximo jogo), carreira de clube | 862–887 ms | **227 ms** |
| 1ª central, carreira só da Kings | 323 ms | 360 ms (ruído; sem ganho) |
| Classes carregadas na 1ª central | 2.093 | 238 |
| Imagens decodificadas na thread da tela (sessão inteira de medida) | 1.001 (740 ms) | **0** |
| Banco (~3 s) ao clicar Continuar/Novo jogo | carregado no clique | já carregado em segundo plano desde o menu |

### Painéis (carreira de clube, semana 21; 1ª abertura / 2ª abertura, ms)

| Painel | Antes (`MedirPaineis` original) | Antes (`TestesDesempenho`, fluxo antigo) | Depois |
|---|---:|---:|---:|
| Próximo jogo | 684 / 87 | 82 / 65 | 76 / 70 |
| Elenco | 275 / 124 | 270 / 158 | 194 / 89 |
| Escalação | 176 / 87 | 178 / 91 | 107 / 62 |
| Campeonatos (Ver campeonatos) | 203 / 120 | 240 / 114 | 163 / 100 |
| Seleções (Copa: 12 grupos) | 257 / 143 | 423 / 151 | 77 / 70 (+60 ms nos quadros seguintes) |
| Vagas | 571 / 56 | 100 / 58 | 332 / 39 (*) |
| Pré-jogo | — | 35 / 16 | 36 / 21 |
| Escalação antes do jogo | — | 41 / 41 | 56 / 49 |
| Fim de temporada | — | 228 / 56 | 143 / 54 |

(*) A 1ª abertura de Vagas varia muito entre rodadas (100–570 ms): é carga de classes e a lista de vagas
inteira num VBox; a 2ª abertura fica em ~40–60 ms.

Nenhum painel passa de 300 ms na 2ª abertura (limite do `TestesDesempenho`), nas duas carreiras.

### Semana (`Fluxo.avancarSemana`, banco completo)

| | Antes | Depois |
|---|---:|---:|
| Média das semanas 1–20 (clube) | 639 ms (`MedirPaineis` original: 895 ms) | 561–593 ms |
| Pior semana (1–20) | 987 ms | 889–951 ms |
| `Torneio.diaDaProximaData` no perfil da semana | ~22% | 3,6% |
| Média por temporada (2027–2031) | — | 478–506 ms |
| Virada de temporada | — | 1,0–1,5 s |
| Heap depois de GC, fim de 2027 → 2031 | — | 442 → 487 MB (+~11 MB/temporada; 264 mil ativos, 12 mil arquivados em 2032) |

## O que mudou

- **Imagens (`ui.comp.Imagens`)**: o cache LRU (por caminho e tamanho, decodificando no tamanho de
  exibição 2x) já existia; agora decodifica fora da trava, e na thread da tela uma imagem fora do cache é
  lida por duas threads de fundo (fila LIFO: o painel aberto agora primeiro) e entregue num único
  `runLater` por leva. Os nós (escudo, bandeira, bandeira redonda, camisa, troféu, logo da Kings) já têm o
  tamanho final e recebem a imagem quando chega; escudo que não abre vira o escudo gerado.
  `Imagens.pedir`, `preaquecer`, `ocupado`, `estatisticas`; `-Dfutebas.imagensNaHora=true` desliga; as
  capturas de tela (`--capturas`) leem na hora.
- **Fotos (`Foto.de`)**: a silhueta fica até a foto chegar; a foto entra com fade de 160 ms.
- **Abrir o jogo (`App`)**: com o menu à vista, uma thread carrega o banco e as classes da interface
  (`Aquecimento.classes`). Com a espera "Carregando…" à vista, `Aquecimento.tela` monta fora da janela uma
  cena com os controles da central (tabela com linhas, abas, lista, combo, navegação) para pagar a 1ª vez
  do CSS, das fontes e das peles; ao fim da carga, `Hub.prepararCentral` põe na fila os escudos e troféus da
  1ª central (seu clube, próximos adversários, times e troféus das suas competições) e espera até 0,3 s.
- **Tabelas de grupo (`Ui.adiado`)**: Copa do Mundo, Libertadores etc. montam os 6 primeiros grupos na
  abertura; os outros entram nos quadros seguintes (até ~12 ms por quadro), e não são montados se o painel
  já saiu da tela. Usado em `PainelTabelas.conteudoFase` (Campeonatos, página da competição, Kings) e nos
  grupos da aba da seleção.
- **Semana (`Simulador.avancarSemana`)**: a cada data jogada a semana perguntava a todos os torneios o dia
  da próxima data, e cada um varria a temporada inteira. Agora as partidas de cada torneio ficam indexadas
  por data durante a semana (dia, jogada e times lidos ao vivo; índice refeito se o torneio ganha
  partidas). Resultado idêntico: conferido com `-Dfutebas.verificarIndice=true` em 2,4 temporadas do banco
  completo e no `IndiceDatasTest`.
- **Medição**: `ui.ferramentas.TestesDesempenho` (novo), `DesempenhoTest` e `IndiceDatasTest` (novos),
  `Hub.painelVisivel`.

## Ainda lento, fora do que este trabalho podia mexer

Do perfil (JFR) da semana com o banco completo, depois das mudanças:

1. **`competicao.Agenda.resolver` — ~31% da semana.** Chamado toda semana por `Simulador.ajustarAgenda`
   (varre todas as partidas da temporada, inclusive as já jogadas, montando `TreeMap`s por time) e a cada
   rodada nova de mata-mata por `Simulador.novasPartidas` (duas varreduras de todas as partidas do mundo).
   Sugestões: pular partidas jogadas e de semanas passadas; no modo `times`, indexar partidas por time em
   vez de varrer tudo; `HashMap` em vez de `TreeMap` onde a ordem não importa (o perfil mostra
   `HashMap$TreeNode.getTreeNode` e `TreeMap.computeIfAbsent` quentes).
2. **`kings.MotorKings.escalacaoAutomatica` / `buscar` — ~17% da semana.** Busca exaustiva com memo
   `HashMap<Long, Double>` (caixa um `Long` e um `Double` por estado). Um `double[]` indexado por
   `(máscara << 3) | v` (n ≤ ~14 jogadores) ou memo por escalação do time na semana cortaria quase tudo.
3. **`competicao.Torneio.diaDaProximaData`** varre a temporada inteira; a semana já usa o índice do
   `Simulador`, mas o método continua caro para quem o chamar direto (um índice por data dentro do
   `Torneio` resolveria na fonte).
4. `ia.Mercado.semana` (~12%) são passadas lineares pelos elencos; sem ponto quente isolado.
5. A 1ª abertura de qualquer tela ainda paga a carga de classes da JVM; um arquivo CDS (AppCDS) no
   empacotamento (`-XX:SharedArchiveFile`) tiraria boa parte disso — fica para quem cuida do `jpackage`.

## Sub-20 de 23 em todo clube (13/11/2026)

`br.futebas.ferramentas.MedirSub20 [temporadas] [clube]` (banco completo, carreira do Flamengo). Comparação
no mesmo código com e sem o Sub-20 novo, máquina muito ocupada por outros processos, então a medida é o
**tempo de CPU da thread** (2 rodadas de cada; o tempo de parede variou até 2x entre rodadas):

| | Antes | Depois |
|---|---:|---:|
| Clubes com Sub-20 de 23 na criação | 0 | 2.109 (países em detalhe; os outros montam quando a tela pede) |
| Jogadores gerados para o Sub-20 | 0 | 23.199 na criação, ~14 mil por virada |
| Jogadores ativos no fim de 2027 | ~236 mil | ~278 mil |
| Competições Sub-20 | 95 | 127 (estaduais de acesso) |
| Criação do mundo (parede) | 4,6–5,4 s | 4,0–4,5 s (ruído) |
| Semana 1–20 (CPU) | 1.172–1.184 ms | 1.120–1.144 ms |
| Resto de 2026 + virada (CPU) | 24,8–28,1 s | 26,5–29,5 s |
| Temporada 2027 inteira (CPU) | 42,9–46,6 s | 46,3–53,0 s (~+10%) |

A semana não ficou mais pesada (a geração só acontece na virada e quando a tela pede).
