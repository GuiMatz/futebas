package br.futebas;

/**
 * Todas as constantes de balanceamento do motor num só lugar (o estudo do Brasfoot por engenharia
 * reversa vai refinar estes números). Nada aqui é dado real: são parâmetros do jogo.
 */
public final class Balanceamento {
    private Balanceamento() {}

    // ------------------------------------------------------------------ calendário
    /** Data inicial do jogo: 1º de janeiro de 2026. */
    public static final int ANO_INICIAL = 2026;
    /**
     * Calendário único (decisão de design 12/11, "igual Brasfoot"): em jogos novos todos os países jogam
     * jan–dez e toda competição começa do zero em 2026 (sem tabelas_iniciais nem jogos já jogados);
     * as continentais da UEFA também cabem no ano. Saves antigos mantêm o calendário com que foram criados.
     */
    public static final boolean CALENDARIO_UNICO = true;
    /** Semana do ano em que as temporadas agosto–maio viram (começo de julho; só saves antigos). */
    public static final int SEMANA_VIRADA_EUROPEIA = 27;
    /** Semanas do ano com data FIFA (as ligas param; seleções jogam amistosos/eliminatórias). */
    public static final int[] JANELAS_FIFA = {13, 37, 41, 46};
    /** Semanas da Copa do Mundo / torneios de seleções de meio de ano (ligas jan–dez param). */
    public static final int SEMANA_TORNEIO_SELECOES_DE = 24, SEMANA_TORNEIO_SELECOES_ATE = 29;
    /** Janelas de transferência por calendário (semanas do ano, inclusive). */
    public static final int[][] JANELAS_TRANSFERENCIA_ANO = {{1, 12}, {27, 34}};
    public static final int[][] JANELAS_TRANSFERENCIA_EUROPEU = {{1, 5}, {27, 35}};

    // ------------------------------------------------------------------ nível de detalhe
    /** Até este número de clubes, todos os países têm simulação completa (lance a lance). */
    public static final int LIMITE_CLUBES_TUDO_DETALHADO = 2500;
    /** Acima do limite: quantos países (os mais fortes) ficam com simulação completa. */
    public static final int PAISES_DETALHADOS = 12;

    // ------------------------------------------------------------------ partida rápida (Poisson)
    /** Gols esperados do mandante e do visitante num jogo equilibrado. */
    public static final double RAPIDO_GOLS_MANDANTE = 1.42, RAPIDO_GOLS_VISITANTE = 1.13;
    /** Sensibilidade dos gols esperados à diferença de força (por ponto). */
    public static final double RAPIDO_SENSIBILIDADE = 0.048;
    /** Ruído do dia (desvio padrão, em pontos de força). */
    public static final double RAPIDO_RUIDO = 2.5;

    // ------------------------------------------------------------------ seleções
    public static final int CONVOCADOS = 26;
    /** Força da seleção: média ponderada dos 23 melhores (11 primeiros peso 1, demais PESO_RESERVAS). */
    public static final double SELECAO_PESO_RESERVAS = 0.2;
    /** Jogos de seleção (IA × IA): sensibilidade à diferença de força e ruído do dia, mais fortes que nos clubes. */
    public static final double SELECAO_SENSIBILIDADE = 0.085, SELECAO_RUIDO = 1.8;
    /** Fator de elite nos jogos rápidos de seleções ({@code MotorPartida.fatorElite} com este K; rodada 2 do balanço: Copas com zebras demais). */
    public static final double SELECAO_ELITE_K = 0.03;
    /**
     * Gols nos jogos rápidos de seleções: média por lado em campo neutro / com mando, e quanto o
     * total cresce com a diferença de força (total = 2 × base × cosh(k·d)^EXP; 1 = sem freio).
     */
    public static final double SELECAO_GOLS_NEUTRO = 1.17, SELECAO_GOLS_MANDANTE = 1.32, SELECAO_GOLS_VISITANTE = 1.04, SELECAO_GOLS_EXP = 0.45;
    /** Elo: fator K e vantagem de mando. */
    public static final double ELO_K = 30, ELO_MANDO = 60;
    /** Reputação mínima do técnico para receber convite de seleção. */
    public static final double REPUTACAO_CONVITE_SELECAO = 62;

    // ------------------------------------------------------------------ elencos e base
    public static final int ELENCO_MAXIMO = 32, ELENCO_ALVO = 26, ELENCO_MINIMO_IA = 22;
    /** Peneira anual: média de juniores por clube (mais com estrutura alta). */
    public static final double PENEIRA_MEDIA = 1.2;
    /** Chance de uma grande promessa na peneira. */
    public static final double PENEIRA_CRAQUE = 0.010;
    /** Bônus de potencial da grande promessa (antes 10–20: craques de 90+ demais depois de 15 anos). */
    public static final double CRAQUE_BONUS_MIN = 5, CRAQUE_BONUS_MAX = 12;
    /** Teto do potencial de um júnior da peneira (era 96). */
    public static final double POTENCIAL_MAX_PENEIRA = 94;
    /**
     * Potencial médio da peneira: BASE + NIVEL × nível do clube + 0,08 × estrutura. O nível usado é o
     * maior entre o atual e o de 2026 ({@code Clube.nivelInicial}): com o nível atual, cada geração
     * mais fraca baixava o potencial da seguinte (espiral: a 1ª divisão na idade do auge caiu de
     * p50 67 em 2026 para 58 em 2045). Era 30 + 0,45 × nível atual. Balanço 2026-11.
     */
    public static final double PENEIRA_POT_BASE = 33, PENEIRA_POT_NIVEL = 0.45, PENEIRA_POT_ESTRUTURA = 0.08;

    public static double potencialPeneira(double nivel, double estrutura) {
        return PENEIRA_POT_BASE + PENEIRA_POT_NIVEL * nivel + PENEIRA_POT_ESTRUTURA * estrutura;
    }
    /** Talento 1–10 da peneira (Brasfoot F.a), pesos em % por faixa do clube (reputação ≥ 68 / 50–68 / demais). */
    public static final int[] TALENTO_ELITE = {2, 3, 0, 5, 15, 35, 20, 10, 8, 2}, TALENTO_MEDIO = {2, 3, 0, 5, 20, 35, 25, 5, 3, 2},
            TALENTO_OUTROS = {4, 4, 7, 10, 25, 25, 20, 3, 1, 1};
    /** Potencial por ponto de talento acima/abaixo de 6. */
    public static final double TALENTO_POTENCIAL = 3.2;
    /** Sub-20: base mínima dos clubes dos países em detalhe e juniores a mais por peneira (Brasfoot: 3–5). */
    public static final int SUB20_BASE_MINIMA = 12, SUB20_PENEIRA_MAX = 5;
    /**
     * Peneira anual do técnico (requisito 15/11/2026): PENEIRA_TECNICO_MIN + sorteio com estes
     * pesos (5 garotos 60%, 6 garotos 28%, 7 garotos 12%), e a chance de um deles ser um talento.
     */
    public static final int PENEIRA_TECNICO_MIN = 5;
    public static final double[] PENEIRA_TECNICO_PESOS = {60, 28, 12};
    public static final double PENEIRA_TECNICO_TALENTO = 0.25;
    /** Sub-20 de todo clube (requisito, 13/11/2026): sempre 23 jogadores, salário de R$ 500 por mês. */
    public static final int SUB20_ELENCO = 23;
    public static final long SUB20_SALARIO = 500;
    /** Sub-20: força de quem completa o time quando faltam jogadores com idade (fração do nível do clube). */
    public static final double SUB20_COMPLETA = 0.62;
    /**
     * Gerações por nacionalidade (força das seleções acompanha o mundo real): bônus de potencial dos
     * juniores do país = K × déficit da seleção (limitado a ±MAX); academias de clubes com reputação
     * ≥ REP_ACADEMIA recrutam ESTRANGEIRO dos juniores em países com déficit (referência ≥ REF_MIN).
     */
    public static final double GERACAO_K = 0.4, GERACAO_BONUS_MAX = 5, GERACAO_REP_ACADEMIA = 60, GERACAO_ESTRANGEIRO = 0.15,
            GERACAO_REF_MIN_ESTRANGEIRO = 74;
    /*
     * Balanço 2026-11: era K 0,6, máx 7 e academias recrutando em países com referência ≥ 66. A
     * resposta chega ~8 anos depois (os juniores amadurecem) e o bônus continuava: seleções pequenas
     * passavam muito da referência (Turcomenistão 83,8 com referência 68,7; Namíbia 82,1/68,0; Sudão
     * do Sul 80,7/69,0 em 2045) e ganhavam Copa da Ásia/África.
     */
    /** Erro máximo (±) do CPE (capacidade potencial estimada) mostrado ao técnico. */
    public static final int CPE_ERRO = 6;
    /** Racha: estrelas no elenco acima disto podem brigar. */
    public static final int RACHA_ESTRELAS = 5;
    public static final double RACHA_CHANCE_SEMANA = 0.06;

    // ------------------------------------------------------------------ dinheiro (R$)
    /*
     * Modelo (Brasfoot + orçamento real): cada clube tem uma RECEITA DE REFERÊNCIA anual E
     * (orçamento do banco, corrigido pela reputação e pela divisão). TV/patrocínio, ingressos,
     * custeio, limite do banco, prêmios e a escala salarial são frações de E, então o caixa da IA
     * fica estável em qualquer país; só o humano sente de verdade cada decisão.
     */
    /** Sem orçamento de referência: E = BASE × e^(reputação / DIV). */
    public static final double RECEITA_BASE = 1_000_000, RECEITA_DIV = 11.2;
    /** Sensibilidade de E à reputação ganha/perdida desde 2026: × e^(Δrep / DIV). */
    public static final double RECEITA_REP_DIV = 13;
    /**
     * Freio à concentração de dinheiro (regra do jogo 16/11/2026, saúde do mundo): a receita acima da
     * RECEITA_FREIO_POSICAO-ésima maior da 1ª divisão do país cresce com expoente RECEITA_RETORNO
     * (retorno decrescente). Em 2026: PSG 6,5 bi → 3,0 bi (o 3º da Ligue 1 tem 1,4 bi), Real Madrid 6,8 → 4,2
     * (Atlético 2,6), Bayern 4,5 → 3,3 (Leipzig 2,4). Do 3º para baixo nada muda. Antes o PSG ganhava a Ligue 1
     * em 17–22 de 30 anos e LaLiga tinha 2–3 campeões. (Uma 1ª versão com teto fixo de R$ 1,5 bi e expoente
     * 0,6 quase não mudou nada: PSG 17 de 20.)
     */
    public static final int RECEITA_FREIO_POSICAO = 3;
    public static final double RECEITA_RETORNO = 0.5;
    /** Peso da divisão (1ª, 2ª, 3ª, 4ª, outras/sem divisão); E segue (peso atual ÷ peso inicial)^EXP. */
    public static final double[] RECEITA_DIVISAO = {1.0, 0.45, 0.25, 0.15, 0.10};
    public static final double RECEITA_DIVISAO_EXP = 0.6;
    /** Fração de E paga como TV + patrocínio no início da temporada (Brasfoot: patrocínio por divisão). */
    public static final double RECEITA_TV = 0.65;
    /** Fração de E que a bilheteria rende numa temporada normal (calibra o preço sugerido). */
    public static final double RECEITA_INGRESSOS = 0.15;
    /** Jogos em casa por temporada usados para calibrar o preço sugerido. */
    public static final int JOGOS_CASA_REF = 22;
    /** Custeio (funcionários, viagens, CT) por ano, em fração de E; caixa acima de CAIXA_TETO × E é reinvestido. */
    /*
     * Reserva (regra do jogo 16/11/2026, saúde do mundo): CAIXA_TETO era 1,2 receita e o caixa médio dos
     * clubes detalhados ia de 0,1 a 1,8 receita (16 mi → 350 mi), parado. Agora a reserva é meia receita
     * (6 meses); acima dela o clube da IA reinveste: mais tentativas e orçamento maior no mercado
     * (Mercado.rico), estrutura e base (fim de temporada) e custeio/obras (CAIXA_EXCESSO_MES).
     */
    public static final double CUSTEIO = 0.13, CAIXA_TETO = 0.5, CAIXA_EXCESSO_MES = 0.10; // rodada 2: 0,03 → 0,05; 16/11/2026: 0,10 (com 0,05 o caixa ainda ia a 1,35 receita)
    /** Reajuste anual máximo da folha de um clube da IA com caixa sobrando ({@code Simulador.reajusteDosRicos}). */
    public static final double REAJUSTE_RICO = 0.005; // era 0,12 (16/11/2026): o salário do mesmo jogador no mesmo clube subia 4,4% ao ano (com 0,015 ainda 3,7%; meta: 1–3%, com a época)
    /** Caixa ao criar o mundo (fração de E). */
    public static final double CAIXA_INICIAL = 0.1;
    /** TV/patrocínio da temporada pago em parcelas a cada 4 semanas (com a folha). */
    public static final int TV_PARCELAS = 13;
    /** Folha anual alvo (calibração inicial) e teto da IA, em fração de E. */
    public static final double FOLHA_ALVO = 0.50, FOLHA_TETO_IA = 0.58;
    /** Unidades salariais da força: e^(K × (força − 60)); estrela × e Top Mundial × (Brasfoot +F×250, ×1,4). */
    public static final double SALARIO_K = 0.07, SALARIO_ESTRELA = 1.35, SALARIO_TOP = 1.4;
    /** Mínimo absoluto (R$/mês) e corte por idade a partir dos 33 (Brasfoot: −(idade−32)×300). */
    public static final long SALARIO_MINIMO = 100;
    public static final double SALARIO_IDADE = 0.06;
    /** Valor de mercado (Brasfoot 4·F²×base do clube): FATOR × F² × base salarial do clube × e^(K×(F−70)). */
    /** 0,024 inflava ~2,1x contra o Transfermarkt (medido com ferramentas.MedirValores). */
    public static final double VALOR_FATOR = 0.0114, VALOR_K = 0.04;
    /** Anos até o valor real do Transfermarkt deixar de pesar no valor de mercado. */
    public static final int VALOR_REAL_ANOS = 6;
    /** Multiplicadores de valor (Brasfoot): estrela ×1,7, Top ×1,6 extra (suavizados), atacante. */
    public static final double VALOR_ESTRELA = 1.5, VALOR_TOP = 1.3, VALOR_ATACANTE = 1.15;
    /** Euro → real (valor_mercado do banco vem em euros). */
    public static final double EURO_EM_REAIS = 6.0;
    /** Juros do banco: 3% ao mês sobre o saldo devedor (Brasfoot). Limite = fração de E. */
    public static final double JUROS_MES = 0.03, LIMITE_EMPRESTIMO = 0.35;
    /** Prêmio do campeão em fração da receita média dos participantes (liga 1ª, outras, copa, estadual, continental principal/outras). */
    public static final double PREMIO_LIGA_1 = 0.22, PREMIO_LIGA_OUTRAS = 0.12, PREMIO_COPA = 0.15, PREMIO_ESTADUAL = 0.05,
            PREMIO_CONTINENTAL_1 = 0.30, PREMIO_CONTINENTAL_2 = 0.15, PREMIO_CONTINENTAL_3 = 0.08;
    /** Público (Brasfoot): ±5% por "nível" (20 pontos) de reputação do visitante; elasticidade ao preço acima/abaixo do sugerido. */
    public static final double PUBLICO_REPUTACAO = 0.05, PRECO_ELASTICIDADE_ACIMA = 1.2, PRECO_ELASTICIDADE_ABAIXO = 0.6;
    /** Ampliação de estádio: custo por lugar e semanas de obra por mil lugares. */
    public static final long CUSTO_POR_LUGAR = 2_500;
    public static final double SEMANAS_OBRA_POR_MIL = 1.5;
    public static final int CAPACIDADE_MAXIMA = 120_000;

    // ------------------------------------------------------------------ mercado
    /** Chance por semana de janela de um clube da IA tentar uma contratação. */
    public static final double MERCADO_CHANCE_CONTRATAR = 0.35;
    /**
     * Clube com caixa sobrando (acima de CAIXA_TETO × receita) reinveste: tenta contratar mais vezes,
     * usa mais do caixa por negócio e tira estrelas até de clubes do mesmo porte (pagando 2× o valor).
     * Antes o caixa da Premier League chegava a 2,5 receitas anuais (Balanço 2026-11, rodada 2).
     */
    public static final double MERCADO_CHANCE_RICO = 0.6, ORCAMENTO_RICO = 0.6;
    /**
     * Mercado em camadas (regra do jogo 16/11/2026, saúde do mundo): a IA procura reforço primeiro no
     * próprio país, depois na confederação e só então no mundo ({@code Mercado.camadas}). Clube de fora
     * da UEFA só olha fora da confederação nesta fração das tentativas (os europeus, sempre: a ida
     * América do Sul → Europa não pode cair). Antes os alvos vinham de uma lista mundial ordenada só
     * por força e 97–98% das transferências mudavam de país.
     */
    public static final double MERCADO_CHANCE_MUNDO = 0.15;
    /**
     * Fração das tentativas de contratação (na janela) em que o clube olha primeiro o mercado inteiro e só
     * depois o próprio país. Sem isto, com as camadas, o mercado ia ficando todo doméstico (87–91% depois de
     * 2038) e a Europa → Brasil sumia; com isto as domésticas ficam perto de 55–70%.
     */
    public static final double MERCADO_EXTERIOR = 0.3;
    /** Fração das tentativas em que a IA olha também os outros países da confederação (o resto só o próprio país). */
    public static final double MERCADO_CHANCE_CONFEDERACAO = 0.3;
    /**
     * Reforço de emergência fora da janela (elenco abaixo de ELENCO_EMERGENCIA): livres do próprio país ou
     * da confederação; sem nenhum, sobe a base. Livre de outra confederação só com o elenco abaixo disto e
     * sem base (antes ~8 mil livres por ano iam da Europa e da América do Sul para clubes curtos da África,
     * Ásia e Oceania, a maior parte das transferências entre países).
     */
    public static final int ELENCO_CRITICO_MUNDO = 15;
    /** Empréstimos da IA (ver {@code Mercado.emprestarJovens}); antes: reputação ≥ 50, elenco ≥ 26, só ≤ 21 anos fora dos 22 melhores, 50%. */
    public static final double EMPRESTIMO_REP_MIN = 35, EMPRESTIMO_JOVEM = 0.6, EMPRESTIMO_EXCEDENTE = 0.45;
    public static final int EMPRESTIMO_ELENCO_MIN = 23;
    /**
     * Fluxo realista das transferências da IA (regra do jogo 13/11/2026, ver {@code Mercado.afinidade}).
     * <b>Degrau</b>: quantas "divisões" o jogador desce. No mesmo país é a diferença de divisão (clube
     * sem divisão = última divisão + 1); entre países, a queda de nível do clube de origem (limitada
     * pela força do jogador + 4) para o de destino, em passos de DEGRAU_NIVEL pontos. Até a tolerância
     * (DEGRAU_TOLERANCIA, +1 sem espaço no clube ou jovem até 21, +1 aos 33, sem limite aos 35, +1 a
     * cada DEGRAU_SEMANAS_LIVRE semanas sem clube, +1 indo de fora para a Europa) a mudança é normal; 1 degrau além disso passa com
     * DEGRAU_CHANCE_1, mais que isso com DEGRAU_CHANCE_2. Voltar a um clube antigo ou ao seu país não tem limite.
     * Medido (MedirFluxoTransferencias, 2 temporadas): queda de 3+ divisões caiu de 6,6% para ~1%.
     */
    public static final int DEGRAU_TOLERANCIA = 1, DEGRAU_SEMANAS_LIVRE = 8;
    public static final double DEGRAU_NIVEL = 7, DEGRAU_CHANCE_1 = 0.2, DEGRAU_CHANCE_2 = 0.02;
    /** Ir para a Europa (vindo de fora dela) é vitrine: aceita descer mais este tanto de degraus. */
    public static final int DEGRAU_VITRINE_EUROPA = 1;
    /**
     * Vitrine dos jovens (16/11/2026, saúde do mundo): com o mercado em camadas os europeus compravam
     * quase só em casa e a ida América do Sul → Europa caía. Clube europeu olha, logo depois do próprio
     * país, os jovens até VITRINE_IDADE que saem da CONMEBOL; e jovem com força ≥ VITRINE_FORCA indo de
     * fora para a Europa aceita descer mais DEGRAU_VITRINE_JOVEM degrau (sugestão do relatório SAUDE-MUNDO).
     */
    public static final int VITRINE_IDADE = 23, DEGRAU_VITRINE_JOVEM = 1;
    public static final double VITRINE_FORCA = 68;
    /** Fração das tentativas de um clube europeu em que ele olha a vitrine sul-americana. */
    public static final double VITRINE_CHANCE = 0.6;
    /**
     * Na vitrine entram também as promessas com contrato (até VITRINE_IDADE, potencial ≥ VITRINE_POTENCIAL)
     * de clubes da CONMEBOL; o europeu as avalia pela média de força e potencial e aceita até VITRINE_MARGEM
     * pontos de força abaixo da exigência. Sem isso a ida América do Sul → Europa caía de ~1,8 mil para ~450
     * por ano em 2041 (as promessas não estavam à venda nem sobrando no elenco).
     */
    public static final double VITRINE_POTENCIAL = 76, VITRINE_MARGEM = 6;
    /**
     * Saída da Europa (UEFA) para fora dela, sem ser volta para casa: chance por temporada e país de
     * destino. Das 5 grandes ligas (LIGAS_FORTES_EUROPA, 1ª divisão) é rara (SAIDA_EUROPA_FORTE); das
     * outras ligas europeias, SAIDA_EUROPA. Veterano: SAIDA_EUROPA_31 (31–32) e SAIDA_EUROPA_33 (33+).
     * Jogador cuja nacionalidade é da confederação de destino (ex.: argentino para o Brasil): × SAIDA_MESMA_CONF.
     * Mercados que pagam muito (MERCADOS_ATRAENTES: Arábia, Catar, Emirados, EUA): × SAIDA_ATRAENTE.
     */
    public static final double SAIDA_EUROPA_FORTE = 0.01, SAIDA_EUROPA = 0.08, SAIDA_EUROPA_31 = 0.35, SAIDA_EUROPA_33 = 0.7,
            SAIDA_MESMA_CONF = 3, SAIDA_ATRAENTE = 4;
    public static final java.util.Set<String> LIGAS_FORTES_EUROPA = java.util.Set.of("ENG", "ESP", "ITA", "DEU", "FRA");
    public static final java.util.Set<String> MERCADOS_ATRAENTES = java.util.Set.of("SAU", "QAT", "ARE", "USA");
    /**
     * Rival não vende ({@code Mercado.rivalNaoVende}, 16/11/2026): quantos dos melhores de cada clube da 1ª
     * divisão ficam fora do alcance dos rivais do país (5 ainda deixava Bayern e PSG com 65% das ligas).
     */
    public static final int RIVAL_PROTEGIDOS = 8;
    /** Empréstimo da IA: o clube de destino fica no máximo EMPRESTIMO_DEGRAU divisões abaixo (prefere 1). */
    public static final int EMPRESTIMO_DEGRAU = 2;
    /** Elenco abaixo disto: o clube contrata livres a qualquer momento (fora da janela também). */
    public static final int ELENCO_EMERGENCIA = 18;
    /** Leilão: duração em semanas e chance de um clube endividado leiloar alguém. */
    public static final int LEILAO_SEMANAS = 2;
    public static final double LEILAO_CHANCE = 0.08;

    // ------------------------------------------------------------------ técnico
    /** Diretoria abaixo disto demite o técnico humano (Brasfoot: < 10). */
    public static final double DEMISSAO_DIRETORIA = 10;
    /**
     * Confiança abaixo da qual a diretoria da IA demite o técnico, vezes a impaciência do país.
     * Era 18 fixo: ~0,3 demissão por 1ª divisão por temporada (Brasileirão real: 15+). Balanço 2026-11.
     */
    public static final double DEMISSAO_IA = 22;
    /** Rebaixado: chance de a IA trocar o técnico na virada; o humano é demitido se a nota da temporada ficar abaixo disto. */
    public static final double DEMISSAO_IA_REBAIXADO = 0.6, DEMISSAO_HUMANO_REBAIXADO = 30;
    /** Impaciência das diretorias por país (1 = padrão): América do Sul, México e Turquia trocam muito de técnico. */
    public static final java.util.Map<String, Double> IMPACIENCIA = java.util.Map.ofEntries(
            java.util.Map.entry("BRA", 1.6), java.util.Map.entry("ARG", 1.45), java.util.Map.entry("COL", 1.35), java.util.Map.entry("PER", 1.35),
            java.util.Map.entry("CHL", 1.3), java.util.Map.entry("ECU", 1.3), java.util.Map.entry("PRY", 1.3), java.util.Map.entry("URY", 1.3),
            java.util.Map.entry("BOL", 1.3), java.util.Map.entry("VEN", 1.3), java.util.Map.entry("MEX", 1.35), java.util.Map.entry("TUR", 1.35),
            java.util.Map.entry("GRC", 1.2), java.util.Map.entry("ITA", 1.2), java.util.Map.entry("ESP", 1.15), java.util.Map.entry("SAU", 1.3),
            java.util.Map.entry("EGY", 1.2), java.util.Map.entry("PRT", 1.1));

    public static double impaciencia(String pais) {
        return pais == null ? 1.0 : IMPACIENCIA.getOrDefault(pais, 1.0);
    }
    /** Pontos do ranking de técnicos por título (Brasfoot). */
    public static final int RANKING_COPA_DO_MUNDO = 35, RANKING_CONTINENTAL_PRINCIPAL = 30, RANKING_CONTINENTAL = 20,
            RANKING_LIGA_1 = 25, RANKING_LIGA_2 = 12, RANKING_LIGA_OUTRAS = 6, RANKING_COPA = 20, RANKING_ESTADUAL = 15,
            RANKING_SELECOES = 25;

    // ------------------------------------------------------------------ tática (marcação, concentrar ataques)
    /** Marcação [LEVE, NORMAL, PESADA]: fator nas chances que o ADVERSÁRIO cria. */
    public static final double[] MARCACAO_CEDE = {1.04, 1.0, 0.90}; // pesada 0,93 → 0,90 com o motor mais sensível à força (2026-11-12)
    /** Marcação: fator nas chances que o próprio time cria (quem corre atrás da bola chega menos inteiro ao ataque). */
    public static final double[] MARCACAO_CRIA = {1.02, 1.0, 0.98};
    /** Marcação: fator nas faltas cometidas (estatística) e, pela metade, nos pênaltis a favor do adversário. */
    public static final double[] MARCACAO_FALTAS = {0.75, 1.0, 1.45};
    /** Marcação: fator nos cartões (amarelo e vermelho direto) do próprio time. */
    public static final double[] MARCACAO_CARTOES = {0.65, 1.0, 1.75};
    /** Marcação: fator na energia gasta minuto a minuto na partida (e, com ela, no cansaço). */
    public static final double[] MARCACAO_ENERGIA = {0.85, 1.0, 1.3};
    /** Estilo [EQUILIBRADO, ATAQUE_TOTAL, CONTRA_ATAQUE, PRESSAO_ALTA, RETRANCA]: fator na energia gasta no jogo. */
    public static final double[] ESTILO_ENERGIA = {1.0, 1.12, 0.95, 1.2, 0.88};
    /** Faltas por time num jogo normal (estatística da partida). */
    public static final double FALTAS_BASE = 12.5;
    /**
     * Concentrar ataques: chances × e^(K × g), limitado a ±LIMITE, vezes CUSTO (ataque previsível).
     * g = vantagem do setor escolhido sobre o outro (pontos de força), com o viés médio descontado.
     */
    public static final double CONCENTRAR_K = 0.012, CONCENTRAR_LIMITE = 0.10, CONCENTRAR_CUSTO = 0.985;
    /** Peso da defesa adversária e do próprio ataque no cálculo de g. */
    public static final double CONCENTRAR_PESO_DEFESA = 0.6, CONCENTRAR_PESO_ATAQUE = 0.4;
    /** Viés médio de g (meio − laterais) medido no banco real: descontado para nenhum lado ser sempre melhor. */
    public static final double CONCENTRAR_VIES = 0.0;

    /**
     * Forma da temporada (regra do jogo 16/11/2026, saúde do mundo): cada clube da IA ganha, a cada
     * temporada, um bônus ou ônus de força ~ Normal(0, FORMA_SD), limitado a ±FORMA_MAX, em todos os jogos
     * dela (lance a lance e rápido). O "posto de força do campeão" mostrou que as ligas eram decididas pelo
     * elenco mais forte (LaLiga e Ligue 1: o mais forte campeão em 15–17 de 20 anos) e o freio de receita
     * sozinho não mudou isso. Um jogo isolado quase não muda (as zebras do jogo a jogo ficam).
     */
    public static final double FORMA_SD = 2.5, FORMA_MAX = 6;

    // ------------------------------------------------------------------ moral e energia
    /** Força do dia: × (1 + MORAL_PESO × (moral − 65) / 100). */
    public static final double MORAL_PESO = 0.08, MORAL_NEUTRA = 65;
    /** Força do dia: × (1 − ENERGIA_PESO × (100 − energia) / 100). */
    public static final double ENERGIA_PESO = 0.10;
    /**
     * Energia gasta num jogo inteiro (90 min, estilo equilibrado, marcação normal) por faixa de idade
     * (≤20, 21–25, 26–31, 32–36, 37+), como no Brasfoot (mais suave). O motor gasta isto minuto a
     * minuto em campo (× estilo × marcação; goleiro × ENERGIA_GOLEIRO) e a energia do fim do jogo é
     * a que o jogador leva para a semana.
     */
    public static final int[] ENERGIA_GASTO = {12, 15, 18, 22, 27};
    /** Goleiro corre menos: fração do gasto da linha. */
    public static final double ENERGIA_GOLEIRO = 0.45;
    /**
     * Energia recuperada por DIA de descanso, por faixa de idade (≤20, 21–25, 26–31, 32–36, 37+):
     * com 3 dias entre jogos o jovem volta inteiro e o veterano acumula cansaço numa sequência de jogos.
     */
    public static final double[] ENERGIA_RECUPERA_DIA = {10, 8.5, 7, 5, 4};
    /** Quem começa o jogo cansado gasta mais: gasto × (1 + CANSACO_ENERGIA × (100 − energia inicial) / 100). */
    public static final double CANSACO_ENERGIA = 0.3;
    /**
     * Cansaço no jogo: rende × (1 − FADIGA_JOGO × (energia ao entrar − energia agora) / 100). Um titular
     * de 28 anos gasta ~18 no jogo e termina rendendo ~5% menos; quem entra do banco vem inteiro.
     */
    public static final double FADIGA_JOGO = 0.30;
    /** IA: troca no intervalo das janelas quem estiver abaixo desta energia, mesmo por um reserva um pouco pior. */
    public static final double ENERGIA_TROCA_IA = 72;

    // ------------------------------------------------------------------ evolução (Brasfoot: teto do clube e declínio)
    /**
     * Teto do clube [divisão 1..4, outras][faixa de reputação 0..5] (Brasfoot fW), na escala de
     * força do FUTÉBAS: ~p99 dos elencos de 2026 + 3. Faixa = reputação / 17 (interpolada).
     */
    public static final int[][] TETO_CLUBE = {
            {68, 71, 74, 80, 86, 95},
            {64, 70, 73, 77, 81, 85},
            {64, 70, 73, 75, 78, 80},
            {63, 69, 73, 75, 77, 79},
            {63, 70, 74, 76, 78, 80}};
    /** Acima do teto do clube o crescimento é multiplicado por isto (estagna, não trava de vez). */
    public static final double TETO_FREIO = 0.15;
    /** Bônus de talento: potencial acima do teto permite passar dele em até este tanto. */
    public static final double TETO_TALENTO = 0.35;
    /** Declínio anual a partir dos 32 (Brasfoot fX): (idade − 31) × DECLINIO × mult(força) por ano. */
    public static final int IDADE_DECLINIO = 32;
    public static final double DECLINIO_ANO = 0.8;
    /** Clube de elite (reputação ≥ isto) segura o declínio em 2 anos (Brasfoot: nível ≥ 20). */
    public static final double DECLINIO_ELITE_REP = 80;

    // ------------------------------------------------------------------ memória
    /** Aposentados há mais que isto (anos) vão para o arquivo compacto (a Kings chama até 5 anos). */
    public static final int ARQUIVAR_APOS_ANOS = 5;
    /**
     * Sem clube: desiste do futebol (aposenta) na virada em que completa LIVRE_ANOS temporadas livre
     * (fraco, abaixo de LIVRE_FRACO_FORCA: LIVRE_FRACO_ANOS). Quem o técnico humano está negociando fica.
     * Regra do jogo 16/11/2026 (saúde do mundo): era "mais de 2" (3 viradas) e "mais de 1" para o fraco;
     * os livres chegavam a 61 mil em 2046 e 73 mil em 2056 (1/5 dos vivos) e quase nunca voltavam.
     */
    public static final int LIVRE_ANOS = 2, LIVRE_FRACO_ANOS = 1;
    public static final double LIVRE_FRACO_FORCA = 50;
    /** Clube sem divisão nacional e sem jogo há mais que isto (semanas) não paga salários (Brasfoot: só paga no mês com jogo). */
    public static final int SALARIO_SEM_JOGO_SEMANAS = 8;
    /**
     * Aposentados que escolheram a Kings abaixo desta força (e não estrelas) são arquivados já no ano
     * seguinte (os outros, até ARQUIVAR_APOS_ANOS). Quem não escolheu a Kings é arquivado na própria virada.
     * A constante existia sem uso; ligada em 16/11/2026 (saúde do mundo: 48 mil aposentados vivos em 2041).
     */
    public static final double ARQUIVAR_FORCA = 62;
}
