# Modo multi-treinador (vários técnicos no mesmo computador)

Requisito (12/11/2026): "coloque o modo de multi treinadores e veja como funciona esse modo no Brasfoot".
A seção 2 descreve como o modo funciona no FUTÉBAS, e isso vale acima do que o Brasfoot faz.

## 1. Como funciona no Brasfoot (pesquisa)

Fontes oficiais e públicas apenas, consultadas em 12/11/2026 com um User-Agent genérico, sem rodar nem decompilar o Brasfoot.

| Tema | O que se sabe | Fonte |
|---|---|---|
| Existe o modo? | Sim, há vários técnicos humanos no mesmo save (hot-seat, um computador). O site oficial não fala do modo: diz só "você é o técnico e o manager do clube". | [1], [2], [3], [4] |
| Quantos técnicos | O Brasfoot 2015 aumentou o limite para **10 técnicos humanos** num save. No Brasfoot 2016, "técnicos humanos ilimitados"; um blog contou mais de 20. | [3], [4], [5] |
| Escolha dos clubes | Cada técnico informa nome e nacionalidade e escolhe um time; um botão adiciona o técnico e deixa incluir outro antes de "Iniciar Jogo". | [3] |
| Ordem da rodada | Quando há jogo, a central de cada técnico humano que ainda não escalou aparece, **um por vez**. Quando todos estão prontos, a data é jogada. | O adaptador de rede feito por fãs [6] segue a mesma regra ("a data só começa quando todos confirmam") |
| Jogos simultâneos e ao vivo | Os jogos da data rodam juntos. No intervalo das partidas com humanos o relógio para, e cada técnico mexe no time antes do 2º tempo. | [6] (descreve o jogo original por baixo do adaptador: "o segundo tempo começa somente quando todos confirmam") |
| Duelo entre dois humanos | Não há descrição pública detalhada. No [6], cada um só mexe no próprio time e vê o do outro só para leitura. | [6] |
| Transferências entre humanos | Existem: um técnico humano compra jogador de outro time humano. A build 2.3 do Brasfoot 2016 corrigiu "erros quando comprando jogadores de outro time humano", e blogs chamam de trapaça o "truque dos 2 técnicos humanos" (vender barato para o outro). | [5], [7] |
| Ofertas de emprego e demissões | Não há documentação pública específica do multi. Cada técnico tem a sua carreira. No Brasfoot 2016, os técnicos humanos também podem assumir seleções, e há a correção "recebendo convites de seleção com técnico humano". | [3], [5] |
| Fim de temporada | Não há documentação pública específica do multi. | — |
| Salvar | O save guarda todos os técnicos (os blogs falam de "técnicos humanos num único save"). Os fãs recomendam salvamento automático a cada jogo. | [4], [8] |

Fontes:

1. Site oficial, página inicial: https://www.brasfoot.com/
2. Site oficial, "O Brasfoot 2022-2023": https://www.brasfoot.com/o-brasfoot-2022-2023.html
3. Ousadia Brasfooteira, "Brasfoot 2016" (lista de novidades): http://ousadiabrasfooteiraoficial.blogspot.com/2016/03/brasfoot-2016.html
4. Campeões de Brasfoot, "Brasfoot 2016: Sonho ou Pesadelo?" (10 técnicos no 2015, mais de 20 no 2016): http://cdebrasfoot.blogspot.com/2016/07/brasfoot-2016-sonho-ou-pesadelo.html
5. Brasfoot Changer, "Download Brasfoot 2016" (notas das builds): https://brasfootchanger.blogspot.com/2016/03/download-brasfoot-2016.html
6. brasfoot-online (projeto de fãs, NÃO oficial: adaptador de rede por cima do jogo original): https://github.com/julio16j/brasfoot-online
7. Brasfoot Análises, "Trapaças, para que te Quero?!": http://brasfootanalises.blogspot.com/2010/04/
8. Brasfoot Análises, "Configurando as Opções do Brasfoot": http://brasfootanalises.blogspot.com/2011/04/configurando-as-opcoes-do-brasfoot.html

O site oficial não tem FAQ nem manual públicos (só download, registro e "conheça o jogo"). Por isso o que não está na tabela foi decidido pelo FUTÉBAS (seção 2).

## 2. Como fica no FUTÉBAS (regra do jogo, implementação própria)

A ideia, em uma frase: "Cada um escolhe um time. Quando eu escalo para jogar a partida, passa para a tela do outro treinador. Ambos os jogos simulam juntos, metade metade, os botões de escalar cada um fica de um lado. No intervalo primeiro um escala, depois o outro, e vai indo assim. Duelos diretos, mesma coisa."

- **Jogo novo:** 2 técnicos (regra do jogo: no máximo 2). Na barra de baixo do Novo jogo, "Adicionar treinador" guarda o nome e o time escolhido (clube **ou** time da Kings, nunca os dois) e limpa a escolha para o próximo. Dois técnicos não podem pegar o mesmo time. "Começar" com a lista vazia é o jogo normal de um técnico.
- **Rodada:** o calendário corre sozinho até a semana em que algum técnico joga. Depois vem a vez de cada técnico, na ordem: a tela "Vez de <Nome>", e então a central dele (caixa de entrada, mercado, escalação...). O "Jogar" dele passa pelo pré-jogo e pela escalação (como no jogo normal) e passa a vez ao próximo. Quando o último manda jogar, a semana é jogada.
- **Tela dividida:** todos os jogos ao vivo dos técnicos rodam juntos, minuto a minuto, numa tela só. A tela fica metade e metade (um jogo de cada lado). Cada metade tem o placar, o relógio e a narração do seu jogo, e o botão "Tática e trocas" de cada técnico fica no lado dele.
- **Intervalo e pausas:** no intervalo os jogos param. Primeiro o técnico 1 faz as trocas, depois o técnico 2, e assim por diante, e só então os segundos tempos seguem juntos. Lesão e expulsão seguem a mesma fila, na ordem dos técnicos. No pênalti a favor, a escolha do batedor abre na metade do técnico, com "ANA — escolha o batedor (Flamengo)" na cor do time, e o outro jogo espera.
- **Duelo direto:** os dois veem o mesmo jogo, com um botão de cada lado (mandante à esquerda, visitante à direita). No intervalo, primeiro o técnico do mandante, depois o do visitante.
- **Por técnico:** caixa de entrada, convites e vagas, carreira, finanças e confiança da diretoria são de cada técnico. A demissão e o desemprego também: o técnico demitido vê a tela "Sem clube" na vez dele e passa a vez com "Aguardar". O fim de temporada mostra o resumo de cada um, na vez dele.
- **Transferência entre técnicos:** a proposta por um jogador de outro técnico vai para a caixa e para a aba Transferências do vendedor, que aceita, recusa ou pede outro valor **na vez dele**. O comprador vê a resposta na vez dele. O dinheiro e o jogador mudam como numa compra normal.
- **Barra de cima:** mostra de quem é a vez ("Vez de Ana · 1/2").
- **Salvar:** o save guarda todos os técnicos e de quem é a vez. O "Continuar" mostra "Multi: Ana, Beto". Saves antigos (um técnico) abrem como antes.

### Limites desta versão

- **Kings League no multi:** os jogos da Kings dos técnicos vão para a tela dividida, ao vivo e juntos. Cada metade mostra placar, fase e narração (cobranças com as linhas do suspense), a coluna da Kings (carta e pênalti do presidente) e "Tática e trocas" (trocas de 7, ilimitadas). O intervalo segue a ordem dos técnicos. O draft espera o técnico do time, que escolhe na vez dele (a central dele abre na aba da Kings). Limites: na carta, o alvo (jogador estrela, suspensão, pênalti invertido) é o automático; no duelo da Kings, a carta do time visitante é usada pela IA; um jogo de futebol e um da Kings na mesma semana rodam um depois do outro (motores diferentes), não juntos.
- **Seleções:** no modo multi, não há convites de seleção.
- **2º jogo na mesma semana** (alguém joga duas vezes): como no jogo de um técnico (um jogo por "Jogar"), depois do 1º jogo a tela mostra "Voltar à central", e os técnicos desse 2º jogo passam de novo pela vez deles ("Vez de", pré-jogo, escalação, Pronto) antes de ele começar. Quem não joga nele não tem vez. Isso não é limite: é a regra do jogo.
- **Formação no meio do jogo** (futebol, também no jogo de um técnico): na tela de trocas, o seletor "Formação" prepara a mudança como as trocas (vale no Confirmar, não gasta substituição). Cada um vai para a vaga nova em que rende mais, e depois dá para trocar posições.
- **"Simular temporada" fica desligado no multi.
- **A mesma vaga de clube** aceita a candidatura de um técnico de cada vez.
