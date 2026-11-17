package br.futebas.partida;

import br.futebas.Balanceamento;
import br.futebas.mundo.Posicao;
import br.futebas.mundo.Rng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Motor de partida, portado de {@code simulation.py} do FUT.AE: o jogo minuto a minuto, com
 * chances por minuto (processo de Poisson), tipos de jogada, finalizador × goleiro, cartões,
 * pressão, mudança tática, notas 0-10 e posse.
 *
 * <p>Diferenças em relação ao original (calibração pedida em docs/referencias/futae.md):
 * <ul>
 *   <li>mando de campo mais forte ({@link #MANDO});</li>
 *   <li>diferenças de qualidade comprimidas com {@code tanh} (placares menos exagerados entre
 *       times desiguais);</li>
 *   <li>regras da época: substituições, VAR, prorrogação e gol de ouro;</li>
 *   <li>energia por jogador, gasta minuto a minuto (Brasfoot): o cansado rende menos e a IA o troca.</li>
 * </ul>
 * Com {@code narrar = false} é o caminho rápido: o mesmo cálculo, sem montar textos.
 */
public final class MotorPartida {
    private MotorPartida() {}

    static final int MEIO_TEMPO = 45;
    // Chances
    /** 4,2 até 2026-11; 4,25 com o cansaço por jogador (o goleiro cansa menos que o atacante: a conversão caiu ~2%). */
    static final double CHANCES_BASE = 4.15;
    static final double FATOR_QUALIDADE = 0.022;
    static final double LIMITE_DIFERENCA = 24;      // tanh: diferença máxima efetiva (18 → 24 em 12/11/2026: grandes x pequenos 62% → 70% de vitória, regra do jogo)
    static final double FATOR_MEIO = 0.01;
    static final double MIN_CHANCES = 0.8, MAX_CHANCES = 12.0;
    // Conversão
    static final double CONVERSAO_BASE = 0.30;
    static final double FATOR_FINALIZACAO = 0.022;
    static final double MIN_CONV = 0.06, MAX_CONV = 0.70;
    static final double FATOR_FINALIZADOR = 0.03;
    static final double PENALTI_PARTE = 0.04, FALTA_PARTE = 0.07;
    // Dinâmica
    /** Chances do mandante e do visitante (FUT.AE: 1,07 / 0,95). */
    static final double[] MANDO = {1.25, 0.87};
    /** "Dia" de cada time (desvio do log das chances): jogos menos previsíveis e menos empates. */
    static final double DIA = 0.27;
    static final int MINUTO_TROCA_TATICA = 70;
    static final double[] ATRAS = {1.0, 1.06, 1.14};
    static final double FRENTE = 0.92, EXPOSTO = 1.06;
    static final double VERMELHO_ATAQUE = 0.7, VERMELHO_EXPOSICAO = 1.15;
    static final double AMARELO_MINUTO = 0.018, PRESSAO_CARTAO = 1.5;
    static final double VERMELHO_DIRETO = 0.0002;
    static final double PRESSAO = 1.6;
    static final int PRESSAO_MINUTOS = 5, PRESSAO_ESPERA = 15;
    static final double GOL_CONTRA = 0.025, IMPEDIMENTO = 0.05, IMPEDIMENTO_VAR = 0.065;
    static final double MILAGRE_LIMIAR = 0.3, MILAGRE_PARTE = 0.25, PENALTI_DEFESA = 0.74;
    static final double SEGUNDO_AMARELO = 0.15;
    /** De quantos em quantos minutos o perfil dos times é refeito com o cansaço (custo do caminho rápido). */
    static final int CANSACO_PASSO = 5;
    /** Lesão durante o jogo: chance por minuto por time (~0,12 por time no jogo, como no Brasfoot). */
    static final double LESAO_MINUTO = 0.12 / 95;
    /** Quem segue machucado em campo (sem substituição) rende isto. */
    static final double LESAO_RENDE = 0.6;

    // Tipos de jogada: (tipo, participação, multiplicador de conversão)
    static final String[] JOGADAS = {null, "cabeca", "fora", "escanteio", "rebote", "contra_ataque"};
    static final double[] JOGADAS_PARTE = {0.55, 0.15, 0.12, 0.07, 0.05, 0.06};
    static final double[] JOGADAS_FATOR = {1.0, 0.9, 0.55, 0.8, 1.3, 1.25};

    static double cabeceio(Posicao p) {
        return switch (p) {
            case ZAG, CA -> 1.0;
            case MC, VOL -> 0.35;
            case LE, LD -> 0.2;
            case MEI, PE, PD -> 0.15;
            case GOL -> 0.0;
        };
    }

    static double chuteDeFora(Posicao p) {
        return switch (p) {
            case MEI -> 1.0;
            case MC -> 0.8;
            case VOL -> 0.6;
            case PE, PD -> 0.6;
            case CA -> 0.5;
            case LE, LD -> 0.25;
            case ZAG -> 0.1;
            case GOL -> 0.0;
        };
    }

    static double comprimir(double diferenca) {
        return LIMITE_DIFERENCA * Math.tanh(diferenca / LIMITE_DIFERENCA);
    }

    /**
     * Elite (Balanço 2026-11, rodada 2): no alto da escala cada ponto de força vale mais (86 × 80
     * é um abismo; 62 × 56 não é), como o valor de mercado, que cresce exponencialmente com a força.
     * As diferenças de qualidade são multiplicadas por 1 + ELITE_K × (nível do jogo − ELITE_DE),
     * limitado a ELITE_MAX pontos. Abaixo de ELITE_DE nada muda (ligas menores, gols e mando iguais).
     * Sem isto o campeão da Champions ficava fora dos 8 mais fortes em metade das temporadas. Nas ligas
     * K = 0,05 (quase nada muda: as ligas já eram justas); nas continentais e mundiais de clubes
     * K = 0,35 (hoje 0,28; Simulador.continentalOuMundial): medido com ferramentas.MedirMataMata, o campeão da
     * Champions entre os 8 mais fortes passou de ~60% para ~80% das temporadas (Libertadores ~65% → ~80%).
     */
    public static final double ELITE_DE = 74,
            ELITE_K = 0.02, // 0,05 → 0,02 em 16/11/2026 (saúde do mundo): num PSG (89) × Marseille (83) o fator era 1,6 e o mais forte da liga era campeão em 15–17 de 20 anos
            ELITE_K_CONTINENTAL = 0.28, // 0,35 → 0,28 em 16/11/2026 (saúde do mundo): Champions/Libertadores com 6–7 campeões em 20–30 anos
            ELITE_MAX = 14;

    /** Fator de elite para um jogo de nível médio {@code nivel} (média de ataque e defesa dos dois times). */
    public static double fatorElite(double nivel) {
        return fatorElite(nivel, ELITE_K);
    }

    /** Com o K da competição (continentais: {@link #ELITE_K_CONTINENTAL}). */
    public static double fatorElite(double nivel, double k) {
        // continentais começam a abrir antes (69): a Sul-Americana tem clubes de 66 a 77 e virava sorteio
        double de = k >= ELITE_K_CONTINENTAL ? ELITE_DE_CONTINENTAL : ELITE_DE;
        return 1 + k * Math.max(0, Math.min(ELITE_MAX, nivel - de));
    }

    /** Início do fator de elite nas competições continentais e mundiais (2026-11-12, Sul-Americana). */
    public static final double ELITE_DE_CONTINENTAL = 69;

    public static double kElite(OpcoesPartida op) {
        return op == null || Double.isNaN(op.eliteK) ? ELITE_K : op.eliteK;
    }

    static double fatorElite(PerfilTime a, PerfilTime b) {
        return fatorElite((a.ataque + a.defesa + b.ataque + b.defesa) / 4);
    }

    /** Chances perigosas que {@code proprio} cria contra {@code adversario} (sem estilo). */
    public static double chancesEsperadas(PerfilTime proprio, PerfilTime adversario) {
        return chancesEsperadas(proprio, adversario, fatorElite(proprio, adversario));
    }

    static double chancesEsperadas(PerfilTime proprio, PerfilTime adversario, double elite) {
        double qualidade = Math.exp(FATOR_QUALIDADE * comprimir(elite * (proprio.ataque - adversario.defesa)));
        double volume = (proprio.volumeAtaque / PerfilTime.VOLUME_ATAQUE_REF)
                / Math.max(0.5, adversario.volumeDefesa / PerfilTime.VOLUME_DEFESA_REF);
        double meio = Math.exp(FATOR_MEIO * comprimir(elite * (proprio.meio - adversario.meio)));
        return CHANCES_BASE * qualidade * volume * meio;
    }

    /**
     * Vantagem (em pontos de força) de atacar pelo meio em vez de pelas laterais: a defesa
     * adversária mais fraca no meio e o próprio ataque mais forte no meio deixam g positivo.
     */
    public static double vantagemMeio(PerfilTime proprio, PerfilTime adversario) {
        return Balanceamento.CONCENTRAR_PESO_DEFESA * (adversario.defesaLados - adversario.defesaMeio)
                + Balanceamento.CONCENTRAR_PESO_ATAQUE * (proprio.ataqueMeio - proprio.ataqueLados)
                - Balanceamento.CONCENTRAR_VIES;
    }

    /** Fator nas chances criadas por concentrar os ataques (1 no equilibrado). */
    public static double concentrar(Concentracao c, PerfilTime proprio, PerfilTime adversario) {
        if (c == null || c == Concentracao.EQUILIBRADO) return 1.0;
        double g = vantagemMeio(proprio, adversario);
        if (c == Concentracao.LATERAIS) g = -g;
        double lim = Balanceamento.CONCENTRAR_LIMITE;
        double f = Math.exp(Balanceamento.CONCENTRAR_K * g);
        return Math.max(1 - lim, Math.min(1 + lim, f)) * Balanceamento.CONCENTRAR_CUSTO;
    }

    /** A melhor concentração contra esse adversário (o que um auxiliar atento sugeriria). */
    public static Concentracao melhorConcentracao(PerfilTime proprio, PerfilTime adversario) {
        double g = vantagemMeio(proprio, adversario);
        double ganho = Math.min(Balanceamento.CONCENTRAR_LIMITE, Math.exp(Balanceamento.CONCENTRAR_K * Math.abs(g)) - 1);
        if ((1 + ganho) * Balanceamento.CONCENTRAR_CUSTO <= 1.0) return Concentracao.EQUILIBRADO;
        return g > 0 ? Concentracao.MEIO : Concentracao.LATERAIS;
    }

    public static double conversao(double finalizador, double goleiro, double fatorEstilo) {
        return conversao(finalizador, goleiro, fatorEstilo, 1.0);
    }

    static double conversao(double finalizador, double goleiro, double fatorEstilo, double elite) {
        double v = CONVERSAO_BASE * Math.exp(FATOR_FINALIZACAO * comprimir(elite * (finalizador - goleiro)));
        return Math.min(MAX_CONV, Math.max(MIN_CONV, v * fatorEstilo));
    }

    static double conversaoFalta(double cobrador, double goleiro) {
        return Math.min(0.3, Math.max(0.04, 0.12 * Math.exp(0.05 * comprimir(cobrador - goleiro))));
    }

    static double conversaoPenalti(double cobrador, double goleiro) {
        return Math.min(0.92, Math.max(0.55, 0.76 + 0.006 * (cobrador - goleiro)));
    }

    // ================================================================== estado de um lado
    private static final class Lado {
        final TimePartida time;
        final int indice;
        /** As 11 vagas (expulsos continuam aqui para o perfil, como no original). */
        final List<Escalado> vagas;
        final List<Escalado> emCampo;
        final List<Escalado> banco;
        PerfilTime perfil;
        Estilo estilo;
        Marcacao marcacao;
        Concentracao concentracao;
        /** Todos que entraram em campo (cópias do motor: a energia muda durante o jogo). */
        final List<Escalado> jogaram = new ArrayList<>();
        double chances;
        int expulsos;
        /** Saíram lesionados sem substituição possível: o time joga com menos, como na expulsão (sem cartão). */
        int desfalques;
        int pressaoAte = -1, ultimaPressao = -PRESSAO_ESPERA;
        boolean estiveAtras;
        int subs;
        final Map<Integer, Integer> entrou = new HashMap<>();
        final Map<Integer, Integer> saiu = new HashMap<>();
        final Set<Integer> amarelados = new HashSet<>();
        final Map<Integer, Integer> golsJogador = new HashMap<>();
        final Map<Integer, Double> bonus = new HashMap<>();

        Lado(TimePartida t, int indice) {
            this.time = t;
            this.indice = indice;
            // cópias: o motor gasta a energia sem mexer no time de entrada (a mesma escalação pode ser jogada de novo)
            this.vagas = new ArrayList<>();
            for (Escalado e : t.titulares) vagas.add(entrar(e.copia(e.vaga, e.forca, true)));
            this.emCampo = new ArrayList<>(vagas);
            this.jogaram.addAll(vagas);
            this.banco = new ArrayList<>();
            for (Escalado e : t.reservas) banco.add(e.copia(e.vaga, e.forca, true));
            this.estilo = t.estilo;
            this.marcacao = t.marcacao == null ? Marcacao.NORMAL : t.marcacao;
            this.concentracao = t.concentracao == null ? Concentracao.EQUILIBRADO : t.concentracao;
            this.perfil = PerfilTime.de(vagas, t.capitao);
            for (Escalado e : t.titulares) entrou.put(e.id, 0);
        }

        /** Marca a energia com que o jogador entra em campo (o cansaço conta a partir dela). */
        static Escalado entrar(Escalado e) {
            e.energia = Math.max(0, Math.min(100, e.energia));
            e.energiaInicio = e.energia;
            e.folego = 1.0;
            return e;
        }

        /**
         * Perfil de agora: as vagas da formação, com as vagas de quem saiu sem substituto (expulso,
         * lesionado sem troca) vazias no setor delas ({@link PerfilTime#de(List, Integer, Set)}).
         */
        PerfilTime perfilAtual() {
            if (emCampo.size() >= vagas.size()) return PerfilTime.de(vagas, time.capitao);
            Set<Integer> fora = new HashSet<>();
            for (Escalado e : vagas) if (!emCampo.contains(e)) fora.add(e.id);
            return PerfilTime.de(vagas, time.capitao, fora);
        }

        /** Põe {@code entra} (do banco) na vaga de {@code sai}. */
        Escalado trocar(Escalado sai, Escalado entra) {
            banco.remove(entra);
            Escalado novo = entrar(entra.copia(sai.vaga, entra.forca, false));
            emCampo.set(emCampo.indexOf(sai), novo);
            int iv = vagas.indexOf(sai);
            if (iv >= 0) vagas.set(iv, novo);
            jogaram.add(novo);
            return novo;
        }

        Escalado goleiro() {
            for (Escalado e : emCampo) if (e.vaga == Posicao.GOL) return e;
            return emCampo.isEmpty() ? vagas.get(0) : emCampo.get(0);
        }

        void bump(int id, double v) {
            bonus.merge(id, v, Double::sum);
        }
    }

    // ================================================================== simulação
    public static ResultadoPartida simular(TimePartida mandante, TimePartida visitante, OpcoesPartida op, Rng rng) {
        if (mandante.titulares.size() != 11 || visitante.titulares.size() != 11) {
            throw new IllegalArgumentException("A simulação exige exatamente 11 titulares de cada lado.");
        }
        return new Execucao(mandante, visitante, op, rng).rodar();
    }

    private static final class Execucao implements AoVivo.Controle {
        final Lado[] l = new Lado[2];
        /** Lados comandados ao vivo pelo técnico (sem trocas automáticas). */
        final boolean[] manual = new boolean[2];
        int minAtual, acrAtual;
        boolean noIntervalo, naProrrogacao;
        final OpcoesPartida op;
        final Rng rng;
        final boolean narrar;
        final ResultadoPartida r = new ResultadoPartida();
        final int[] placar = new int[2];
        final double[] mando;
        final double[] dia = new double[2];
        /** Fator de elite do jogo (recalculado com os perfis em definirChances). */
        double elite = 1;
        int[] minutos;
        int[] acrescimos;
        int passos;
        double ritmoTotal;
        boolean houveVirada;
        int fimDoJogo = 90;
        // números "até agora" (posse, chutes comuns, escanteios, faltas, impedimentos): só estatística,
        // com sorteio próprio (nunca o rng do jogo), para a tela de trocas/intervalo e o fim
        java.util.SplittableRandom est;
        final double[] posseSoma = new double[1];
        int minutosNumeros;
        final double[] chutesExtra = new double[2], alvoExtra = new double[2], escanteiosAc = new double[2],
                faltasAc = new double[2], impedimentosAc = new double[2];
        final int[] anulados = new int[2];

        Execucao(TimePartida m, TimePartida v, OpcoesPartida op, Rng rng) {
            this.op = op;
            this.rng = rng;
            this.narrar = op.narrar;
            l[0] = new Lado(m, 0);
            l[1] = new Lado(v, 1);
            mando = op.mando ? MANDO : new double[]{1.0, 1.0};
            for (int s = 0; s < 2; s++) dia[s] = Math.exp(rng.gauss(0, DIA) - DIA * DIA / 2);
            r.mandante = m.nome;
            r.visitante = v.nome;
            r.mandanteId = m.clubeId;
            r.visitanteId = v.clubeId;
        }

        void lance(int min, int acr, String tipo, String texto, int lado, Integer jid, String jogador, String detalhe, String jogada) {
            if (!narrar) return;
            r.lances.add(new Lance(min, acr, tipo, texto, lado, placar[0], placar[1], jid, jogador, detalhe, jogada));
        }

        void definirChances() {
            elite = fatorElite((l[0].perfil.ataque + l[0].perfil.defesa + l[1].perfil.ataque + l[1].perfil.defesa) / 4, kElite(op));
            for (int s = 0; s < 2; s++) {
                Lado a = l[s], b = l[1 - s];
                double c = chancesEsperadas(a.perfil, b.perfil, elite);
                c *= a.estilo.cria * b.estilo.cede;
                c *= a.estilo.confronto(b.estilo);
                c *= Balanceamento.MARCACAO_CEDE[b.marcacao.ordinal()] * Balanceamento.MARCACAO_CRIA[a.marcacao.ordinal()];
                c *= concentrar(a.concentracao, a.perfil, b.perfil);
                c *= op.fatorGols * dia[s];
                double fs = s == 0 ? op.formaMandante : op.formaVisitante, fo = s == 0 ? op.formaVisitante : op.formaMandante;
                if (fs != 0 || fo != 0) c *= Math.exp(FATOR_QUALIDADE * (fs - fo)); // forma da temporada
                a.chances = Math.min(MAX_CHANCES, Math.max(MIN_CHANCES, c));
            }
        }

        static double ritmo(int minuto, int acrescimo) {
            if (minuto > 90) return 1.1;
            if (acrescimo > 0) return minuto == 90 ? 1.3 : 1.0;
            if (minuto <= MEIO_TEMPO) return 0.85 + 0.15 * minuto / MEIO_TEMPO;
            return 0.95 + 0.3 * (minuto - MEIO_TEMPO) / MEIO_TEMPO;
        }

        ResultadoPartida rodar() {
            definirChances();
            r.xgMandante = golsEsperados(l[0], l[1]);
            r.xgVisitante = golsEsperados(l[1], l[0]);
            lance(1, 0, "inicio", "Rola a bola! " + r.mandante + " x " + r.visitante + ".", -1, null, null, null, null);
            int primeiro = rng.entre(1, 3), segundo = rng.entre(2, 5);
            // linha do tempo: minuto, acréscimo; -1 = intervalo
            List<int[]> tl = new ArrayList<>();
            for (int m = 1; m <= MEIO_TEMPO; m++) tl.add(new int[]{m, 0});
            for (int a = 1; a <= primeiro; a++) tl.add(new int[]{MEIO_TEMPO, a});
            tl.add(new int[]{-1, 0});
            for (int m = MEIO_TEMPO + 1; m <= 90; m++) tl.add(new int[]{m, 0});
            for (int a = 1; a <= segundo; a++) tl.add(new int[]{90, a});
            passos = tl.size();
            ritmoTotal = 0;
            for (int[] t : tl) if (t[0] > 0) ritmoTotal += ritmo(t[0], t[1]);
            int[] janelasSub = janelasSubstituicao();

            for (int passo = 0; passo < tl.size(); passo++) {
                int min = tl.get(passo)[0], acr = tl.get(passo)[1];
                if (min == -1) {
                    lance(MEIO_TEMPO, primeiro, "intervalo", "Intervalo: " + r.mandante + " " + placar[0] + " x " + placar[1] + " " + r.visitante + ".", -1, null, null, null, null);
                    avisar(MEIO_TEMPO, primeiro, true);
                    if (op.aoVivo != null && op.ladoHumano >= 0) {
                        minAtual = MEIO_TEMPO;
                        acrAtual = primeiro;
                        noIntervalo = true;
                        op.aoVivo.pausa(this, AoVivo.Pausa.INTERVALO, op.ladoHumano, null);
                        // duelo de dois técnicos humanos: primeiro o mandante, depois o visitante
                        if (op.ambosHumanos) op.aoVivo.pausa(this, AoVivo.Pausa.INTERVALO, 1 - op.ladoHumano, null);
                        noIntervalo = false;
                    }
                    lance(MEIO_TEMPO + 1, 0, "segundo_tempo", "Bola rolando para o segundo tempo.", -1, null, null, null, null);
                    continue;
                }
                acumularNumeros(min);
                avisar(min, acr, false);
                if (acr == 0 && min == MINUTO_TROCA_TATICA) trocaTatica(min);
                if (acr == 0) for (int js : janelasSub) if (js == min) substituicoes(min);
                if (minuto(passo, min, acr, false)) break;
            }
            if (op.prorrogacao && placar[0] == placar[1]) {
                r.prorrogacao = true;
                lance(90, segundo, "prorrogacao", "Empate no tempo normal. Vamos para a prorrogação!", -1, null, null, null, null);
                naProrrogacao = true;
                for (int m = 91; m <= 120; m++) {
                    acumularNumeros(m);
                    avisar(m, 0, false);
                    if (minuto(passos + m - 90, m, 0, true)) break;
                    if (op.golDeOuro && placar[0] != placar[1]) {
                        lance(m, 0, "feito", "GOL DE OURO! Fim de jogo na prorrogação.", -1, null, null, null, null);
                        fimDoJogo = m;
                        break;
                    }
                    fimDoJogo = m;
                }
            }
            r.golsMandante = placar[0];
            r.golsVisitante = placar[1];
            fechar(segundo);
            // quem terminou em campo (a disputa de pênaltis só usa estes)
            for (Lado x : l) x.time.emCampoFim = List.copyOf(x.emCampo);
            if (op.aoVivo != null) op.aoVivo.fim(r);
            return r;
        }

        // ---------------------------------------------------------- ao vivo (AoVivo.Controle)
        void avisar(int min, int acr, boolean intervalo) {
            if (op.aoVivo == null) return;
            minAtual = min;
            acrAtual = acr;
            noIntervalo = intervalo;
            op.aoVivo.minuto(this);
            noIntervalo = false;
        }

        @Override public int minuto() { return minAtual; }
        @Override public int acrescimo() { return acrAtual; }
        @Override public boolean intervalo() { return noIntervalo; }
        @Override public boolean prorrogacao() { return naProrrogacao; }
        @Override public int gols(int lado) { return placar[lado]; }
        @Override public List<Lance> lances() { return r.lances; }
        @Override public TimePartida time(int lado) { return l[lado].time; }
        @Override public List<Escalado> emCampo(int lado) { return l[lado].emCampo; }
        @Override public List<Escalado> vagas(int lado) { return l[lado].vagas; }
        @Override public List<Escalado> banco(int lado) { return l[lado].banco; }
        @Override public int substituicoesFeitas(int lado) { return l[lado].subs; }
        @Override public int substituicoesPermitidas() { return op.substituicoes; }
        @Override public Estilo estilo(int lado) { return l[lado].estilo; }
        @Override public List<EventoJogo> eventos() { return java.util.Collections.unmodifiableList(r.eventos); }

        /**
         * Um minuto a mais nos números do jogo (posse, chutes sem perigo, escanteios, faltas e
         * impedimentos), pelo time em campo agora (perfil, estilo, marcação, concentração). Sorteio
         * próprio, semeado pelos clubes: não toca no rng do jogo, então o resultado não muda.
         */
        void acumularNumeros(int min) {
            if (op.aoVivo == null) return; // só os jogos acompanhados (os outros fecham os números no fim)
            if (est == null) est = new java.util.SplittableRandom(0x9E3779B97F4A7C15L * java.util.Objects.hash(r.mandanteId, r.visitanteId, "numeros"));
            double p = 50 + 1.5 * (l[0].perfil.meio - l[1].perfil.meio) + l[0].estilo.inclinacaoPosse - l[1].estilo.inclinacaoPosse
                    + (est.nextDouble() - 0.5) * 14;
            posseSoma[0] += Math.min(72, Math.max(28, p));
            minutosNumeros++;
            double media = posseSoma[0] / minutosNumeros;
            for (int s = 0; s < 2; s++) {
                Lado a = l[s], b = l[1 - s];
                double share = (s == 0 ? media : 100 - media) / 50.0;
                double vol = est.nextDouble();
                double chutes = 6.8 * Math.pow(share, 0.9) * (a.estilo == Estilo.RETRANCA ? 0.75 : a.estilo == Estilo.ATAQUE_TOTAL ? 1.2 : 1.0) / 90;
                chutesExtra[s] += chutes * (0.4 + 1.2 * vol);
                alvoExtra[s] += chutes * (0.4 + 1.2 * vol) * 0.22;
                double cant = a.concentracao == Concentracao.LATERAIS ? 1.4 : a.concentracao == Concentracao.MEIO ? 0.8 : 1.0;
                escanteiosAc[s] += 5.0 * Math.pow(share, 0.8) * cant / 90 * (0.3 + 1.4 * est.nextDouble());
                faltasAc[s] += Balanceamento.FALTAS_BASE * Balanceamento.MARCACAO_FALTAS[a.marcacao.ordinal()]
                        * (a.estilo == Estilo.PRESSAO_ALTA ? 1.15 : 1.0) / 90 * (0.3 + 1.4 * est.nextDouble());
                // linha alta do adversário (pressão/ataque total) pega mais impedimento
                double linha = b.estilo == Estilo.PRESSAO_ALTA || b.estilo == Estilo.ATAQUE_TOTAL ? 1.4 : b.estilo == Estilo.RETRANCA ? 0.7 : 1.0;
                impedimentosAc[s] += 1.8 * linha * Math.pow(share, 0.5) / 90 * (0.3 + 1.4 * est.nextDouble());
            }
        }

        @Override
        public Estatisticas estatisticas() {
            int[] am = new int[2], vm = new int[2];
            for (int[] x : r.amarelos) am[x[0]]++;
            for (int[] x : r.vermelhos) vm[x[0]]++;
            int pm = minutosNumeros == 0 ? 50 : (int) Math.round(posseSoma[0] / minutosNumeros);
            int[] fin = new int[2], alvo = new int[2], esc = new int[2], fal = new int[2], imp = new int[2];
            for (int s = 0; s < 2; s++) {
                fin[s] = r.chutes[s] + (int) chutesExtra[s];
                alvo[s] = Math.min(fin[s], r.noGol[s] + (int) alvoExtra[s]);
                esc[s] = (int) escanteiosAc[s];
                fal[s] = (int) faltasAc[s];
                imp[s] = anulados[s] + (int) impedimentosAc[s];
            }
            return new Estatisticas(minAtual, new int[] {pm, 100 - pm}, fin, alvo, esc, fal, am, vm, imp);
        }

        void evento(int min, int acr, String tipo, int lado, Escalado e) {
            r.eventos.add(new EventoJogo(min, acr, tipo, lado, e == null ? -1 : e.id, e == null ? null : e.nome));
        }

        boolean humano(int lado) {
            return op.aoVivo != null && (op.ladoHumano == lado || op.ambosHumanos);
        }

        void pausar(AoVivo.Pausa motivo, int lado, Integer jogadorId) {
            if (!humano(lado)) return;
            op.aoVivo.pausa(this, motivo, lado, jogadorId);
        }
        @Override public Marcacao marcacao(int lado) { return l[lado].marcacao; }
        @Override public Concentracao concentracao(int lado) { return l[lado].concentracao; }

        @Override
        public void mudarMarcacao(int lado, Marcacao marcacao) {
            Lado a = l[lado];
            manual[lado] = true;
            if (marcacao == null || a.marcacao == marcacao) return;
            a.marcacao = marcacao;
            lance(Math.max(1, minAtual), acrAtual, "tatica", "Mudança tática no " + a.time.nome + ": marcação " + marcacao.nome.toLowerCase() + ".", lado, null, null, null, null);
            definirChances();
        }

        @Override
        public void mudarConcentracao(int lado, Concentracao concentracao) {
            Lado a = l[lado];
            manual[lado] = true;
            if (concentracao == null || a.concentracao == concentracao) return;
            a.concentracao = concentracao;
            String txt = concentracao == Concentracao.EQUILIBRADO ? "ataques variados" : "ataques " + concentracao.nome.toLowerCase();
            lance(Math.max(1, minAtual), acrAtual, "tatica", "Mudança tática no " + a.time.nome + ": " + txt + ".", lado, null, null, null, null);
            definirChances();
        }
        @Override public Set<Integer> sairam(int lado) { return new HashSet<>(l[lado].saiu.keySet()); }
        @Override public void assumirControle(int lado) { manual[lado] = true; }

        @Override
        public boolean substituir(int lado, int saiId, int entraId) {
            Lado a = l[lado];
            if (a.subs >= op.substituicoes) return false;
            Escalado sai = null, entra = null;
            for (Escalado e : a.emCampo) if (e.id == saiId) sai = e;
            for (Escalado e : a.banco) if (e.id == entraId) entra = e;
            if (sai == null || entra == null) return false;
            manual[lado] = true;
            Escalado novo = a.trocar(sai, entra);
            int min = Math.max(1, minAtual);
            a.saiu.put(sai.id, min);
            a.entrou.put(novo.id, min);
            a.subs++;
            lance(min, acrAtual, "substituicao", "Substituição no " + a.time.nome + ": sai " + sai.nome + ", entra " + novo.nome + ".",
                    lado, novo.id, novo.nome, sai.nome, null);
            for (Lado x : l) x.perfil = x.perfilAtual();
            definirChances();
            return true;
        }

        @Override
        public boolean trocarPosicoes(int lado, int idA, int idB) {
            Lado a = l[lado];
            Escalado x = null, y = null;
            // pelas vagas: um dos dois pode ser a vaga de quem saiu sem substituto (expulso ou lesionado
            // sem troca). Requisito: "pode trocar a posição dele para alguém ficar na posição dele".
            for (Escalado e : a.vagas) {
                if (e.id == idA) x = e;
                if (e.id == idB) y = e;
            }
            if (x == null || y == null || x == y) return false;
            boolean xEm = a.emCampo.contains(x), yEm = a.emCampo.contains(y);
            if (!xEm && !yEm) return false; // duas vagas vazias: nada a fazer
            manual[lado] = true;
            Posicao v = x.vaga;
            x.vaga = y.vaga;
            y.vaga = v;
            x.encaixe = -1; // o encaixe do escalador valia para a vaga antiga
            y.encaixe = -1;
            int ix = a.vagas.indexOf(x), iy = a.vagas.indexOf(y);
            a.vagas.set(ix, y);
            a.vagas.set(iy, x);
            if (xEm && yEm) {
                int jx = a.emCampo.indexOf(x), jy = a.emCampo.indexOf(y);
                a.emCampo.set(jx, y);
                a.emCampo.set(jy, x);
            }
            Escalado quem = xEm ? x : y, vazia = xEm ? y : x;
            String txt = xEm && yEm ? x.nome + " e " + y.nome + " trocam de posição."
                    : quem.nome + " vai para a posição de " + vazia.nome + " (" + quem.vaga.name() + ").";
            // troca de posição não gasta substituição (regra do jogo)
            lance(Math.max(1, minAtual), acrAtual, "tatica", "Mudança no " + a.time.nome + ": " + txt, lado, null, null, null, null);
            a.perfil = a.perfilAtual();
            definirChances();
            return true;
        }

        @Override
        public boolean mudarFormacao(int lado, String formacao) {
            Posicao[] novas = formacao == null || !Formacoes.nomes().contains(formacao) ? null : Formacoes.vagas(formacao);
            Lado a = l[lado];
            if (novas == null || novas.length != a.vagas.size()) return false;
            Posicao[] nat = new Posicao[a.vagas.size()];
            for (int i = 0; i < nat.length; i++) nat[i] = a.vagas.get(i).natural;
            int[] destino = Escalador.remapear(nat, novas);
            Escalado[] ordem = new Escalado[novas.length];
            for (int i = 0; i < destino.length; i++) {
                Escalado x = a.vagas.get(i);
                x.vaga = novas[destino[i]];
                x.encaixe = -1; // o encaixe do escalador valia para a vaga antiga
                ordem[destino[i]] = x;
            }
            List<Escalado> antes = new ArrayList<>(a.emCampo);
            a.vagas.clear();
            a.vagas.addAll(java.util.Arrays.asList(ordem));
            a.emCampo.clear();
            for (Escalado x : a.vagas) if (antes.contains(x)) a.emCampo.add(x);
            for (Escalado x : antes) if (!a.emCampo.contains(x)) a.emCampo.add(x);
            manual[lado] = true;
            // trocar a formação não gasta substituição (regra do jogo)
            lance(Math.max(1, minAtual), acrAtual, "tatica", "Mudança no " + a.time.nome + ": o time passa a jogar no " + formacao + ".", lado, null, null, null, null);
            a.perfil = a.perfilAtual();
            definirChances();
            return true;
        }

        @Override
        public void mudarEstilo(int lado, Estilo estilo) {
            Lado a = l[lado];
            manual[lado] = true;
            if (estilo == null || a.estilo == estilo) return;
            a.estilo = estilo;
            lance(Math.max(1, minAtual), acrAtual, "tatica", "Mudança tática no " + a.time.nome + ": " + estilo.nome.toLowerCase() + ".", lado, null, null, null, null);
            definirChances();
        }

        int[] janelasSubstituicao() {
            int n = op.substituicoes;
            if (n <= 0) return new int[0];
            int[] base = {60, 70, 78, 84, 88};
            int[] js = new int[Math.min(n, 5)];
            for (int i = 0; i < js.length; i++) js[i] = base[i];
            return js;
        }

        /** Um minuto de jogo. Retorna true se o jogo deve parar (gol de ouro). */
        boolean minuto(int passo, int min, int acr, boolean prorrog) {
            cansar();
            for (int s = 0; s < 2; s++) {
                Lado a = l[s], b = l[1 - s];
                int atras = placar[1 - s] - placar[s];
                double taxa = a.chances * ritmo(min, acr) / ritmoTotal * mando[s];
                if (atras > 0) taxa *= ATRAS[Math.min(atras, 2)];
                else if (atras < 0) taxa *= FRENTE * EXPOSTO;
                taxa *= Math.pow(VERMELHO_ATAQUE, a.expulsos + a.desfalques) * Math.pow(VERMELHO_EXPOSICAO, b.expulsos + b.desfalques);
                if (passo <= a.pressaoAte) taxa *= PRESSAO;
                if (prorrog) taxa *= 0.9;
                if (rng.real() < taxa) {
                    chance(a, b, passo, min, acr);
                    if (prorrog && op.golDeOuro && placar[0] != placar[1]) return false;
                }
                cartoes(a, passo, min, acr);
                if (rng.real() < LESAO_MINUTO) lesao(a, min, acr);
            }
            return false;
        }

        int minutosJogados;

        /**
         * Um minuto de energia gasta por quem está em campo (Brasfoot): a base por idade
         * ({@link Balanceamento#ENERGIA_GASTO}, num jogo de 90 min) × estilo × marcação, mais para quem
         * começou cansado; goleiro gasta menos. O fôlego (rende) cai com a energia gasta desde a entrada.
         * A cada {@link #CANSACO_PASSO} minutos os perfis dos times são refeitos.
         */
        void cansar() {
            for (Lado a : l) {
                double fe = Balanceamento.ESTILO_ENERGIA[a.estilo.ordinal()] * Balanceamento.MARCACAO_ENERGIA[a.marcacao.ordinal()];
                for (Escalado e : a.emCampo) {
                    double g = gastoPorJogo(e) / 90.0 * fe * (1 + Balanceamento.CANSACO_ENERGIA * (100 - e.energiaInicio) / 100.0);
                    if (e.vaga == Posicao.GOL) g *= Balanceamento.ENERGIA_GOLEIRO;
                    e.energia = Math.max(0, e.energia - g);
                    e.folego = 1 - Balanceamento.FADIGA_JOGO * Math.max(0, e.energiaInicio - e.energia) / 100.0;
                }
            }
            if (++minutosJogados % CANSACO_PASSO == 0) {
                for (Lado a : l) a.perfil = a.perfilAtual();
                definirChances();
            }
        }

        /** Energia que o jogador gasta num jogo inteiro, pela idade (desconhecida: a faixa de 26 a 31). */
        double gastoPorJogo(Escalado e) {
            int[] g = Balanceamento.ENERGIA_GASTO;
            if (op.ano <= 0 || e.anoNasc <= 0) return g[2];
            int idade = op.ano - e.anoNasc;
            return idade <= 20 ? g[0] : idade <= 25 ? g[1] : idade <= 31 ? g[2] : idade <= 36 ? g[3] : g[4];
        }

        void chance(Lado a, Lado b, int passo, int min, int acr) {
            Escalado goleiroAdv = b.goleiro();
            double goleiro = b.perfil.goleiro;
            double faltas = Balanceamento.MARCACAO_FALTAS[b.marcacao.ordinal()];
            double parteP = (op.var ? PENALTI_PARTE * 1.2 : PENALTI_PARTE) * (1 + (faltas - 1) * 0.5);
            double parteF = FALTA_PARTE;
            double sorteio = rng.real();
            String jogada;
            Escalado autor;
            double prob;
            if (sorteio < parteP) {
                jogada = "penalti";
                Integer escolhido = a.time.cobradorPenalti;
                if (humano(a.indice)) {
                    avisarPausa(min, acr);
                    List<Escalado> cands = new ArrayList<>();
                    for (Escalado e : a.emCampo) if (e.vaga != Posicao.GOL || a.emCampo.size() == 1) cands.add(e);
                    lance(min, acr, "penalti", "PÊNALTI para o " + a.time.nome + "!", a.indice, null, null, null, "penalti");
                    Integer x = op.aoVivo.escolherBatedor(this, a.indice, java.util.Collections.unmodifiableList(cands));
                    if (x != null) escolhido = x;
                }
                autor = cobrador(a, escolhido);
                prob = conversaoPenalti(autor.rende(), goleiro);
            } else if (sorteio < parteP + parteF) {
                jogada = "falta";
                autor = cobrador(a, a.time.cobradorFalta);
                prob = conversaoFalta(autor.rende(), goleiro);
            } else {
                double[] partes = JOGADAS_PARTE.clone();
                if (placar[a.indice] > placar[b.indice] || a.estilo == Estilo.CONTRA_ATAQUE) partes[5] *= 2.5;
                if (a.concentracao == Concentracao.LATERAIS) {
                    partes[1] *= 1.6; // cruzamentos: mais cabeçadas e escanteios
                    partes[3] *= 1.3;
                    partes[2] *= 0.7;
                } else if (a.concentracao == Concentracao.MEIO) {
                    partes[2] *= 1.5; // mais chutes de fora e tabelas pelo meio
                    partes[1] *= 0.7;
                    partes[3] *= 0.85;
                }
                int k = rng.peso(partes);
                jogada = JOGADAS[k];
                if ("cabeca".equals(jogada) || "escanteio".equals(jogada)) autor = escolher(a.emCampo, 1);
                else if ("fora".equals(jogada)) autor = escolher(a.emCampo, 2);
                else autor = finalizador(a.emCampo, a.concentracao);
                prob = Math.min(MAX_CONV, conversao(autor.rende(), goleiro, a.estilo.converte, elite) * JOGADAS_FATOR[k]);
            }
            String time = a.time.nome;
            boolean bolaParada = "penalti".equals(jogada) || "falta".equals(jogada);
            if (rng.real() < prob) {
                double imp = op.var ? IMPEDIMENTO_VAR : IMPEDIMENTO;
                if (!bolaParada && rng.real() < imp) {
                    r.chutes[a.indice]++;
                    anulados[a.indice]++;
                    lance(min, acr, "impedimento", (op.var ? "Revisão do VAR: gol anulado! " : "Gol anulado! ")
                            + autor.nome + " estava impedido.", a.indice, autor.id, autor.nome, null, null);
                    return;
                }
                if (!bolaParada && rng.real() < GOL_CONTRA) {
                    jogada = "contra";
                    autor = escolher(b.emCampo, 3);
                }
                boolean empatadoAntes = placar[0] == placar[1];
                placar[a.indice]++;
                boolean contra = "contra".equals(jogada);
                if (!contra) {
                    r.chutes[a.indice]++;
                    r.noGol[a.indice]++;
                }
                Escalado assist = null;
                if (!bolaParada && !contra && !"rebote".equals(jogada)) assist = assistente(a, autor);
                r.gols.add(new int[]{a.indice, autor.id, min, assist == null ? 0 : assist.id, contra ? 1 : 0});
                evento(min, acr, contra ? "gol_contra" : "penalti".equals(jogada) ? "penalti_gol" : "gol", a.indice, autor);
                if (contra) b.bump(autor.id, -1.0);
                else {
                    a.bump(autor.id, 1.2);
                    if (assist != null) a.bump(assist.id, 0.7);
                }
                if (narrar) {
                    String texto;
                    if ("penalti".equals(jogada)) {
                        texto = "GOL DE PÊNALTI! " + autor.nome + " " + Narracao.frasePenalti(Narracao.jeitoPenalti(true, rng), null)
                                + ". Gol do " + time + ".";
                    } else {
                        String modelo = jogada == null ? Narracao.sortear(Narracao.GOL, rng) : Narracao.GOL_JOGADA.get(jogada);
                        texto = Narracao.preencher(modelo, time, autor.nome, null);
                    }
                    if (assist != null) texto += " Assistência de " + assist.nome + ".";
                    if (min >= 88) texto += Narracao.GOL_NO_FIM;
                    lance(min, acr, "gol", texto, a.indice, autor.id, autor.nome, assist == null ? null : assist.nome, jogada);
                }
                comemorar(a, b, autor, contra, min, acr, empatadoAntes);
                if (placar[b.indice] <= placar[a.indice] && rng.real() < 0.4) pressao(b, passo, min, acr);
            } else {
                boolean pen = "penalti".equals(jogada);
                if (pen) evento(min, acr, "penalti_perdido", a.indice, autor);
                r.chutes[a.indice]++;
                double defesa = Math.min(pen ? 0.9 : 0.75, Math.max(0.2,
                        (pen ? PENALTI_DEFESA : 0.5) + 0.012 * (goleiro - 75) - 0.006 * (autor.rende() - 75)));
                double x = rng.real();
                String tipo;
                if (x < defesa) {
                    tipo = prob >= MILAGRE_LIMIAR && rng.real() < MILAGRE_PARTE ? "milagre" : "defesa";
                    r.noGol[a.indice]++;
                    b.bump(goleiroAdv.id, "milagre".equals(tipo) ? 0.6 : 0.3);
                } else if (x < defesa + (pen ? 0.06 : 0.12)) {
                    tipo = "trave";
                } else {
                    tipo = "fora";
                    a.bump(autor.id, -0.1);
                }
                if (narrar) {
                    String texto;
                    if (pen) {
                        String jeito = switch (tipo) {
                            case "defesa" -> "defendeu";
                            case "milagre" -> "milagre";
                            case "trave" -> "trave";
                            default -> rng.chance(0.6) ? "isolou" : "fora";
                        };
                        texto = "Pênalti perdido! " + autor.nome + " " + Narracao.frasePenalti(jeito, goleiroAdv.nome) + ".";
                    } else {
                        String[] frases = switch (tipo) {
                            case "defesa" -> Narracao.DEFESA;
                            case "milagre" -> Narracao.MILAGRE;
                            case "trave" -> Narracao.TRAVE;
                            default -> Narracao.FORA;
                        };
                        texto = Narracao.preencher(Narracao.sortear(frases, rng), time, autor.nome, goleiroAdv.nome);
                    }
                    lance(min, acr, tipo, texto, a.indice, autor.id, autor.nome,
                            tipo.equals("defesa") || tipo.equals("milagre") ? goleiroAdv.nome : null, jogada);
                }
                if (("trave".equals(tipo) || "milagre".equals(tipo)) && rng.real() < 0.35) pressao(a, passo, min, acr);
            }
        }

        void comemorar(Lado a, Lado b, Escalado autor, boolean contra, int min, int acr, boolean empatadoAntes) {
            if (!contra) {
                int n = a.golsJogador.merge(autor.id, 1, Integer::sum);
                if (n >= 3) {
                    String modelo = Narracao.FEITO.getOrDefault(n, Narracao.FEITO.get(5));
                    lance(min, acr, "feito", modelo.replace("{autor}", autor.nome).replace("{gols}", String.valueOf(n)),
                            a.indice, autor.id, autor.nome, null, "hattrick");
                    String rotulo = n == 3 ? "Hat-trick" : n == 4 ? "Poker" : n + " gols";
                    r.destaques.removeIf(h -> h.endsWith("de " + autor.nome + " (" + a.time.nome + ")"));
                    r.destaques.add(rotulo + " de " + autor.nome + " (" + a.time.nome + ")");
                }
            }
            if (placar[b.indice] < placar[a.indice]) b.estiveAtras = true;
            if (a.estiveAtras && placar[a.indice] == placar[b.indice] + 1 && empatadoAntes) {
                a.estiveAtras = false;
                houveVirada = true;
                lance(min, acr, "feito", "VIRADA! O " + a.time.nome + " estava perdendo e agora está na frente.", a.indice, null, null, null, "virada");
                r.destaques.add("Virada do " + a.time.nome);
            } else if (placar[a.indice] == placar[b.indice] && min >= 85) {
                lance(min, acr, "feito", "EMPATE NO FIM! O " + a.time.nome + " busca o empate quando ninguém esperava.", a.indice, null, null, null, "empate_fim");
                r.destaques.add("Empate do " + a.time.nome + " no fim");
            }
        }

        void pressao(Lado a, int passo, int min, int acr) {
            if (passo - a.ultimaPressao < PRESSAO_ESPERA || passo >= passos - 3) return;
            a.pressaoAte = passo + PRESSAO_MINUTOS;
            a.ultimaPressao = passo;
            if (narrar) lance(min, acr, "pressao", Narracao.preencher(Narracao.sortear(Narracao.PRESSAO, rng), a.time.nome, null, null), a.indice, null, null, null, null);
        }

        void trocaTatica(int min) {
            boolean mudou = false;
            for (int s = 0; s < 2; s++) {
                Lado a = l[s];
                int atras = placar[1 - s] - placar[s];
                if (manual[s] || atras < 2 || a.estilo == Estilo.ATAQUE_TOTAL) continue;
                a.estilo = Estilo.ATAQUE_TOTAL;
                mudou = true;
                lance(min, 0, "tatica", "Mudança tática! O " + a.time.nome + " vai para o tudo ou nada: ataque total.", s, null, null, null, "ataque");
            }
            if (mudou) definirChances();
        }

        void substituicoes(int min) {
            boolean mudou = false;
            for (int s = 0; s < 2; s++) {
                Lado a = l[s];
                if (manual[s] || a.subs >= op.substituicoes || a.banco.isEmpty()) continue;
                // sai o pior rendimento de linha (já com o cansaço; amarelado e muito cansado pesam), entra o melhor do banco
                Escalado sai = null;
                double pior = Double.MAX_VALUE;
                for (Escalado e : a.emCampo) {
                    if (e.vaga == Posicao.GOL) continue;
                    double v = e.rende() - (a.amarelados.contains(e.id) ? 6 : 0)
                            - 0.25 * Math.max(0, Balanceamento.ENERGIA_TROCA_IA - e.energia);
                    if (v < pior) {
                        pior = v;
                        sai = e;
                    }
                }
                if (sai == null) continue;
                Escalado entra = null;
                double melhor = 0;
                for (Escalado e : a.banco) {
                    if (e.natural == Posicao.GOL) continue;
                    double f = e.natural.encaixe(sai.vaga);
                    double v = e.forca * (f > 0 ? f : 0.7);
                    if (v > melhor) {
                        melhor = v;
                        entra = e;
                    }
                }
                boolean cansado = sai.energia < Balanceamento.ENERGIA_TROCA_IA;
                if (entra == null || melhor < sai.rende() * (cansado ? 0.8 : 0.85)) continue;
                Escalado novo = a.trocar(sai, entra);
                a.saiu.put(sai.id, min);
                a.entrou.put(novo.id, min);
                a.subs++;
                mudou = true;
                lance(min, 0, "substituicao", "Substituição no " + a.time.nome + ": sai " + sai.nome + (cansado ? " (cansado)" : "")
                        + ", entra " + novo.nome + ".", s, novo.id, novo.nome, sai.nome, null);
            }
            if (mudou) {
                for (Lado a : l) a.perfil = a.perfilAtual();
                definirChances();
            }
        }

        void cartoes(Lado a, int passo, int min, int acr) {
            List<Escalado> linha = new ArrayList<>();
            for (Escalado e : a.emCampo) if (e.vaga != Posicao.GOL) linha.add(e);
            if (linha.isEmpty()) return;
            double fatorVar = op.var ? 1.1 : 1.0;
            double fm = Balanceamento.MARCACAO_CARTOES[a.marcacao.ordinal()];
            if (rng.real() < VERMELHO_DIRETO * fm * fatorVar * (1 + (double) passo / passos)) {
                Escalado f = linha.get(rng.inteiro(linha.size()));
                expulsar(a, f, min, acr, "Expulso! " + f.nome + " entra duro e recebe o vermelho direto. " + a.time.nome + " com um a menos.");
            } else if (rng.real() < AMARELO_MINUTO * fm * (a.estilo == Estilo.PRESSAO_ALTA ? PRESSAO_CARTAO : 1.0)) {
                Escalado f = linha.get(rng.inteiro(linha.size()));
                if (a.amarelados.contains(f.id)) {
                    if (rng.real() >= SEGUNDO_AMARELO) return;
                    a.bump(f.id, -0.3);
                    expulsar(a, f, min, acr, "Segundo amarelo! " + f.nome + " está fora. " + a.time.nome + " com um a menos.");
                    return;
                }
                a.amarelados.add(f.id);
                a.bump(f.id, -0.3);
                r.amarelos.add(new int[]{a.indice, f.id});
                evento(min, acr, "amarelo", a.indice, f);
                lance(min, acr, "cartao", "Cartão amarelo para " + f.nome + " (" + a.time.nome + ").", a.indice, f.id, f.nome, null, null);
            }
        }

        void expulsar(Lado a, Escalado f, int min, int acr, String texto) {
            a.emCampo.remove(f);
            a.expulsos++;
            a.saiu.put(f.id, min);
            a.perfil = a.perfilAtual(); // a vaga dele fica vazia no setor dele
            definirChances();
            a.bump(f.id, -1.5);
            r.vermelhos.add(new int[]{a.indice, f.id});
            evento(min, acr, "vermelho", a.indice, f);
            lance(min, acr, "vermelho", texto, a.indice, f.id, f.nome, null, null);
            if (humano(a.indice)) {
                avisarPausa(min, acr);
                pausar(AoVivo.Pausa.EXPULSAO, a.indice, f.id);
            }
        }

        void avisarPausa(int min, int acr) {
            minAtual = min;
            acrAtual = acr;
        }

        /**
         * Lesão no jogo: a IA (ou o lado não comandado) troca na hora se puder; o técnico recebe a
         * pausa e decide. Quem fica em campo machucado rende {@link #LESAO_RENDE}.
         */
        void lesao(Lado a, int min, int acr) {
            List<Escalado> cand = new ArrayList<>();
            for (Escalado e : a.emCampo) if (!r.lesoesIds().contains(e.id)) cand.add(e);
            if (cand.isEmpty()) return;
            Escalado f = cand.get(rng.inteiro(cand.size()));
            r.lesoes.add(new int[]{a.indice, f.id, min});
            evento(min, acr, "lesao", a.indice, f);
            lance(min, acr, "lesao", f.nome + " sente uma lesão e pede atendimento. Problema para o " + a.time.nome + ".",
                    a.indice, f.id, f.nome, null, null);
            if (humano(a.indice)) {
                avisarPausa(min, acr);
                pausar(AoVivo.Pausa.LESAO, a.indice, f.id);
            }
            if (!manual[a.indice] && a.emCampo.contains(f)) trocarLesionado(a, f, min, acr);
            // sem substituição possível (acabaram as trocas ou o banco): sai e o time fica com um a menos
            // (requisito; como a expulsão nas chances, mas sem cartão nem suspensão)
            if (a.emCampo.contains(f) && (a.subs >= op.substituicoes || a.banco.isEmpty())) {
                a.emCampo.remove(f);
                a.desfalques++;
                a.saiu.put(f.id, Math.max(1, min));
                a.perfil = a.perfilAtual();
                lance(min, acr, "desfalque", f.nome + " sai lesionado e o " + a.time.nome + " fica com um a menos.",
                        a.indice, f.id, f.nome, null, null);
                definirChances();
                return;
            }
            // seguiu em campo (o técnico ainda pode trocar): joga no sacrifício
            int i = a.emCampo.indexOf(f);
            if (i >= 0) {
                Escalado mal = f.copia(f.vaga, f.forca * LESAO_RENDE, true);
                a.emCampo.set(i, mal);
                int iv = a.vagas.indexOf(f);
                if (iv >= 0) a.vagas.set(iv, mal);
                int ij = a.jogaram.indexOf(f);
                if (ij >= 0) a.jogaram.set(ij, mal);
                a.perfil = a.perfilAtual();
                definirChances();
            }
        }

        void trocarLesionado(Lado a, Escalado sai, int min, int acr) {
            if (a.subs >= op.substituicoes || a.banco.isEmpty()) return;
            Escalado entra = null;
            double melhor = 0;
            for (Escalado e : a.banco) {
                if ((e.natural == Posicao.GOL) != (sai.vaga == Posicao.GOL)) continue;
                double fe = e.natural.encaixe(sai.vaga);
                double v = e.forca * (fe > 0 ? fe : 0.7);
                if (v > melhor) {
                    melhor = v;
                    entra = e;
                }
            }
            if (entra == null) return;
            Escalado novo = a.trocar(sai, entra);
            a.saiu.put(sai.id, min);
            a.entrou.put(novo.id, min);
            a.subs++;
            lance(min, acr, "substituicao", "Substituição no " + a.time.nome + ": sai " + sai.nome + " (lesionado), entra " + novo.nome + ".",
                    a.indice, novo.id, novo.nome, sai.nome, null);
            a.perfil = a.perfilAtual();
            definirChances();
        }

        Escalado cobrador(Lado a, Integer escolhido) {
            if (escolhido != null) for (Escalado e : a.emCampo) if (e.id == escolhido) return e;
            Escalado melhor = null;
            double mv = -1;
            for (Escalado e : a.emCampo) {
                if (e.vaga == Posicao.GOL) continue;
                double v = e.rende() * (0.6 + e.vaga.pesoAtaque);
                if (v > mv) {
                    mv = v;
                    melhor = e;
                }
            }
            return melhor != null ? melhor : a.emCampo.get(0);
        }

        Escalado finalizador(List<Escalado> campo, Concentracao conc) {
            double[] p = new double[campo.size()];
            for (int i = 0; i < p.length; i++) {
                Escalado e = campo.get(i);
                p[i] = e.vaga.pesoAtaque * Math.exp(FATOR_FINALIZADOR * (e.rende() - 75)) * fatorSetor(e.vaga, conc);
            }
            return campo.get(rng.peso(p));
        }

        /** modo 1 = cabeceio, 2 = chute de fora, 3 = defensor (gol contra). */
        Escalado escolher(List<Escalado> campo, int modo) {
            double[] p = new double[campo.size()];
            for (int i = 0; i < p.length; i++) {
                Escalado e = campo.get(i);
                double w = modo == 1 ? cabeceio(e.vaga) : modo == 2 ? chuteDeFora(e.vaga) : e.vaga.pesoDefesa;
                p[i] = w * Math.exp(0.04 * (e.rende() - 75));
            }
            return campo.get(rng.peso(p));
        }

        Escalado assistente(Lado a, Escalado autor) {
            List<Escalado> op = new ArrayList<>();
            for (Escalado e : a.emCampo) if (e != autor && e.vaga != Posicao.GOL) op.add(e);
            if (op.isEmpty() || rng.real() < 0.25) return null;
            double[] p = new double[op.size()];
            for (int i = 0; i < p.length; i++) {
                Escalado e = op.get(i);
                p[i] = (e.vaga.pesoMeio + e.vaga.pesoAtaque * 0.8 + 0.1 * Math.exp(0.04 * (e.rende() - 75)))
                        * fatorSetor(e.vaga, a.concentracao) * (a.concentracao == Concentracao.LATERAIS && lateral(e.vaga) ? 1.3 : 1.0);
            }
            return op.get(rng.peso(p));
        }

        static boolean lateral(Posicao p) {
            return p == Posicao.LE || p == Posicao.LD || p == Posicao.PE || p == Posicao.PD;
        }

        /** Quem aparece mais nos lances do setor escolhido. */
        static double fatorSetor(Posicao p, Concentracao c) {
            if (c == null || c == Concentracao.EQUILIBRADO) return 1.0;
            boolean lado = lateral(p);
            if (c == Concentracao.LATERAIS) return lado ? 1.4 : 0.85;
            return lado ? 0.75 : 1.15;
        }

        double golsEsperados(Lado a, Lado b) {
            double[] w = new double[a.emCampo.size()];
            double total = 0, aberto = 0;
            for (int i = 0; i < w.length; i++) {
                Escalado e = a.emCampo.get(i);
                w[i] = e.vaga.pesoAtaque * Math.exp(FATOR_FINALIZADOR * (e.rende() - 75));
                total += w[i];
            }
            for (int i = 0; i < w.length; i++) aberto += w[i] * conversao(a.emCampo.get(i).rende(), b.perfil.goleiro, a.estilo.converte, elite);
            aberto /= Math.max(1e-9, total);
            double fatorJogadas = 0;
            for (int i = 0; i < JOGADAS_PARTE.length; i++) fatorJogadas += JOGADAS_PARTE[i] * JOGADAS_FATOR[i];
            double porChance = (1 - PENALTI_PARTE - FALTA_PARTE) * aberto * fatorJogadas
                    + FALTA_PARTE * conversaoFalta(cobrador(a, a.time.cobradorFalta).rende(), b.perfil.goleiro)
                    + PENALTI_PARTE * conversaoPenalti(cobrador(a, a.time.cobradorPenalti).rende(), b.perfil.goleiro);
            return Math.round(a.chances * mando[a.indice] * porChance * 100) / 100.0;
        }

        void fechar(int segundo) {
            // minutos jogados
            int fim = r.prorrogacao ? fimDoJogo : 90;
            for (Lado a : l) {
                for (Map.Entry<Integer, Integer> e : a.entrou.entrySet()) {
                    int saida = a.saiu.getOrDefault(e.getKey(), fim);
                    r.minutos.put(e.getKey(), Math.max(1, saida - e.getValue()));
                }
            }
            // notas 0-10
            double melhor = -1;
            for (int s = 0; s < 2; s++) {
                Lado a = l[s];
                int sofridos = placar[1 - s];
                double res = placar[s] > sofridos ? 0.3 : placar[s] < sofridos ? -0.2 : 0;
                List<Escalado> todos = new ArrayList<>(a.vagas);
                for (Escalado e : a.jogaram) if (!todos.contains(e)) todos.add(e);
                for (Escalado e : todos) {
                    if (r.notas.containsKey(e.id)) continue;
                    r.energia.put(e.id, (int) Math.round(e.energia));
                    double v = 6.0 + 0.03 * (e.rendeInteiro() - 75) + res;
                    Posicao p = e.vaga;
                    if (p == Posicao.GOL || p == Posicao.ZAG || p == Posicao.LE || p == Posicao.LD) {
                        v += sofridos == 0 ? 0.5 : -0.25 * sofridos;
                    }
                    v += a.bonus.getOrDefault(e.id, 0.0) + rng.real(-0.3, 0.3);
                    v = Math.round(Math.min(10.0, Math.max(3.0, v)) * 10) / 10.0;
                    r.notas.put(e.id, v);
                    if (v > melhor) {
                        melhor = v;
                        r.melhorId = e.id;
                        r.melhorNome = e.nome;
                    }
                }
            }
            for (int s = 0; s < 2; s++) {
                double f = Balanceamento.FALTAS_BASE * Balanceamento.MARCACAO_FALTAS[l[s].marcacao.ordinal()]
                        * (l[s].estilo == Estilo.PRESSAO_ALTA ? 1.15 : 1.0) * (r.prorrogacao ? 1.3 : 1.0);
                r.faltas[s] = Math.max(2, (int) Math.round(f + rng.gauss(0, 3)));
            }
            double posse = 50 + 1.5 * (l[0].perfil.meio - l[1].perfil.meio) + l[0].estilo.inclinacaoPosse
                    - l[1].estilo.inclinacaoPosse + rng.real(-3, 3);
            int p = (int) Math.round(Math.min(72, Math.max(28, posse)));
            r.posse = new int[]{p, 100 - p};
            // Chutes "comuns" (de longe, travados, sem perigo): o motor só conta as chances perigosas
            // (~5 por time), mas no futebol real são ~12 chutes por time. Só estatística: não mexe nos
            // gols nem no sorteio do jogo (sorteio próprio, derivado do próprio resultado).
            java.util.SplittableRandom sr = new java.util.SplittableRandom(31L * p + 1009L * placar[0] + 7919L * placar[1] + 104729L * r.chutes[0] + r.chutes[1]);
            for (int s = 0; s < 2; s++) {
                double share = r.posse[s] / 50.0;
                double media = 6.8 * Math.pow(share, 0.9) * (l[s].estilo == Estilo.RETRANCA ? 0.75 : l[s].estilo == Estilo.ATAQUE_TOTAL ? 1.2 : 1.0);
                int extra = Math.max(1, (int) Math.round(media + (sr.nextDouble() - 0.5) * 4));
                int alvo = 0;
                for (int k = 0; k < extra; k++) if (sr.nextDouble() < 0.22) alvo++;
                r.chutes[s] += extra;
                r.noGol[s] += alvo;
            }
            // escanteios e impedimentos: ao vivo, os mesmos números que a tela mostrou (sorteio próprio);
            // nos outros jogos, pela posse, depois dos chutes (não muda nada do que veio antes)
            for (int s = 0; s < 2; s++) {
                if (minutosNumeros > 0) {
                    r.escanteios[s] = (int) Math.round(escanteiosAc[s]);
                    r.impedimentos[s] = anulados[s] + (int) Math.round(impedimentosAc[s]);
                } else {
                    double share = r.posse[s] / 50.0;
                    double cant = l[s].concentracao == Concentracao.LATERAIS ? 1.4 : l[s].concentracao == Concentracao.MEIO ? 0.8 : 1.0;
                    r.escanteios[s] = (int) Math.round(5.0 * share * cant * (0.5 + sr.nextDouble()));
                    r.impedimentos[s] = anulados[s] + (int) Math.round(1.8 * (0.4 + 1.2 * sr.nextDouble()));
                }
            }
            int margem = placar[0] - placar[1];
            if (Math.abs(margem) >= 4) r.destaques.add("Goleada do " + (margem > 0 ? r.mandante : r.visitante));
            if (narrar) {
                r.comentario = Narracao.comentario(r, houveVirada, rng);
                r.lances.add(new Lance(r.prorrogacao ? fimDoJogo : 90, r.prorrogacao ? 0 : segundo, "fim",
                        "Fim de jogo: " + r.mandante + " " + placar[0] + " x " + placar[1] + " " + r.visitante + ".",
                        -1, placar[0], placar[1], null, null, null, null));
            }
        }
    }
}
