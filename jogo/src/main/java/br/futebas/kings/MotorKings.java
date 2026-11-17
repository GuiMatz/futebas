package br.futebas.kings;

import br.futebas.mundo.Posicao;
import br.futebas.mundo.Rng;
import br.futebas.partida.EventoJogo;
import br.futebas.partida.Lance;
import br.futebas.partida.ResultadoPartida;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Partida da Kings League com as regras de 2026/27 (dados/pesquisa/kings-2026.json), minuto a
 * minuto: 7 contra 7, 2 × 20 min corridos com o tempo regulamentar até o min 36; rampa inicial
 * (1v1 → 5v5 nos 5 primeiros minutos, 7v7 depois); gol duplo do min 17 ao 20; dado no começo do
 * 2º tempo (3v3, 2v2 ou 1v1 do min 20 ao 23); amarelo = 2 min com um a menos; vermelho = 5 min
 * com um a menos; pênalti do presidente (um por time, min 5–35 fora das fases especiais); cartas
 * secretas (baralho de 10 cartas de 2026/27, uma por time, janelas 5–16:59 e 23–35:59); no min 36,
 * empate vai para os shootouts (5 + morte súbita); senão, match ball (vence quem chegar ao placar do líder + 1).
 * O jogo nunca termina empatado: os shootouts ficam em {@code penaltisMandante/Visitante}.
 */
public final class MotorKings {
    private MotorKings() {}

    public enum Carta {
        GOL_DUPLO("Gol Duplo"), SANCAO("Suspensão"), PENALTI_SHOOTOUT("Shoot Out"), JOGADOR_ESTRELA("Jogador Estrela"),
        PENALTI("Pênalti"), PENALTI_INVERTIDO("Pênalti Invertido"), CORINGA("Coringa");

        public final String nome;

        Carta(String nome) { this.nome = nome; }
    }

    /** Baralho de 2026/27: 2 Gol Duplo, 1 Suspensão (Sanção), 2 Shoot Out, 2 Jogador Estrela, 1 Pênalti, 1 Pênalti Invertido, 1 Coringa. */
    public static final Carta[] BARALHO = {Carta.GOL_DUPLO, Carta.GOL_DUPLO, Carta.SANCAO, Carta.PENALTI_SHOOTOUT, Carta.PENALTI_SHOOTOUT,
            Carta.JOGADOR_ESTRELA, Carta.JOGADOR_ESTRELA, Carta.PENALTI, Carta.PENALTI_INVERTIDO, Carta.CORINGA};

    static final int FIM_REGULAMENTAR = 36, GOL_DUPLO_DE = 17, DADO_DE = 20, DADO_ATE = 23, RAMPA = 5;
    /**
     * Gols esperados por time em 36 min de 7v7 equilibrado, sem pênaltis, cartas e match ball.
     * Era 3,7: com os extras dava ~12 gols por jogo; 3,0 dá ~10 (Kings real: ~8–11). Balanço 2026-11.
     * 2,9 com o ritmo por número em campo (12/11/2026): ~10,6 gols por jogo (65 x 65).
     */
    static final double GOLS_BASE = 2.9;
    static final double AMARELO_JOGO = 0.5, VERMELHO_JOGO = 0.03;
    /**
     * Rodízio (substituições ilimitadas): energia gasta por minuto em campo (linha / goleiro), recuperada
     * por minuto no banco; abaixo de LIMIAR_TROCA o mais cansado sai se houver reserva com ENERGIA_ENTRA.
     * Cansado rende × (1 − FADIGA × falta de energia). Balanço 2026-11, rodada 2 (antes os 7 jogavam tudo).
     */
    static final double GASTO_MINUTO = 2.0, GASTO_GOLEIRO = 0.5, RECUPERA_MINUTO = 4.0, LIMIAR_TROCA = 65, ENERGIA_ENTRA = 88, FADIGA = 0.2;
    /** Presidente-jogador não é atleta em tempo integral: cansa mais rápido e roda mais (joga menos minutos). */
    static final double GASTO_PRESIDENTE = 3.0;
    /**
     * Capitão em campo (decisão do motor, rodada 2): +1% de força (liderança) e 20% menos amarelos
     * (disciplina). Sem escolha do técnico: o presidente-jogador, senão o mais forte.
     */
    static final double CAPITAO_FORCA = 0.01, CAPITAO_CARTOES = 0.8;
    static final double CONV_PENALTI = 0.75, CONV_SHOOTOUT = 0.55, CONV_PRESIDENTE = 0.55;
    static final int MINUTOS_GOL_DUPLO_CARTA = 4, MINUTOS_SANCAO = 4, MINUTOS_AMARELO = 2, MINUTOS_VERMELHO = 5;

    /** Força do time no 7x7 (média dos 7 que jogam), para telas e notícias. */
    public static double forcaPublica(LigaKings.TimeKings t) {
        return forca(t, 7);
    }

    static double forca(LigaKings.TimeKings t, int emCampo) {
        List<LigaKings.AtletaKings> esc = escalados(t);
        if (esc != null) {
            // escalação do técnico humano: cada um na vaga da formação (fora de posição rende menos)
            List<Posicao> vagas = vagasDaFormacao(t.formacao, emCampo);
            double s = 0;
            int n = Math.min(emCampo, esc.size());
            boolean gk = temGoleiro(t);
            for (int i = 0; i < n; i++) s += esc.get(i).forca * encaixe(esc.get(i).posicao, vagas.get(i), gk);
            s += (emCampo - n) * 30;
            return s / Math.max(1, emCampo);
        }
        List<LigaKings.AtletaKings> l = new ArrayList<>(t.elenco);
        l.sort(Comparator.comparingDouble((LigaKings.AtletaKings a) -> -a.forca));
        double s = 0;
        int n = Math.min(emCampo, l.size());
        for (int i = 0; i < n; i++) s += l.get(i).forca;
        s += (emCampo - n) * 30; // faltando gente: muito mais fraco
        return s / Math.max(1, emCampo);
    }

    public static ResultadoPartida simular(LigaKings.TimeKings a, LigaKings.TimeKings b, LigaKings.RegrasKings regras, Rng rng, boolean narrar) {
        return simular(a, b, regras, rng, narrar, null, -1);
    }

    /** Com acompanhamento ao vivo do técnico no {@code ladoHumano} (0 ou 1; -1 = nenhum). */
    public static ResultadoPartida simular(LigaKings.TimeKings a, LigaKings.TimeKings b, LigaKings.RegrasKings regras, Rng rng, boolean narrar,
                                           AoVivoKings aoVivo, int ladoHumano) {
        return new Jogo(a, b, regras, rng, narrar, aoVivo, ladoHumano).rodar();
    }

    /**
     * Multi-treinador: os dois lados podem ser de técnicos humanos (duelo). A IA não decide carta nem
     * pênalti do presidente por nenhum lado humano (antes o 2º técnico do duelo ficava com a IA).
     */
    public static ResultadoPartida simular(LigaKings.TimeKings a, LigaKings.TimeKings b, LigaKings.RegrasKings regras, Rng rng, boolean narrar,
                                           AoVivoKings aoVivo, boolean humanoA, boolean humanoB) {
        Jogo j = new Jogo(a, b, regras, rng, narrar, aoVivo, humanoA ? 0 : humanoB ? 1 : -1);
        if (aoVivo != null) {
            j.ladoHumano[0] = humanoA;
            j.ladoHumano[1] = humanoB;
        }
        return j.rodar();
    }

    /** Testes: o mandante recebe a carta {@code carta0} (em vez do sorteio) e o {@code aoVivo} decide por ele. */
    static ResultadoPartida simularComCarta(LigaKings.TimeKings a, LigaKings.TimeKings b, Rng rng, Carta carta0, AoVivoKings aoVivo) {
        Jogo j = new Jogo(a, b, new LigaKings.RegrasKings(), rng, true, aoVivo, 0);
        j.forcarCarta0 = carta0;
        return j.rodar();
    }

    private static final class Jogo implements AoVivoKings.ControleKings {
        Carta forcarCarta0;
        final LigaKings.TimeKings[] t;
        final double[] forca = new double[2];
        final Rng rng;
        final boolean narrar;
        final AoVivoKings aoVivo;
        final int humano;
        /** Lados de técnico humano ao vivo (a IA não decide carta nem presidente por eles). */
        final boolean[] ladoHumano = new boolean[2];
        final ResultadoPartida r = new ResultadoPartida();
        final int[] placar = new int[2];
        final Carta[] carta = new Carta[2];
        final boolean[] presidenteUsado = new boolean[2];
        /** Quem cobrou o pênalti do presidente (sempre um dos presidentes reais do time). */
        final String[] cobradorPresidente = new String[2];
        /** Até que minuto (exclusive) vale cada efeito. */
        final int[] golDuploAte = new int[2], sancaoAte = new int[2];
        /** Inferioridade por cartões: lista de minutos de fim por lado. */
        final List<List<Integer>> foraAte = List.of(new ArrayList<>(), new ArrayList<>());
        /** Jogador estrela escolhido pela carta: todo gol dele vale 2 até o fim do jogo. */
        final LigaKings.AtletaKings[] estrela = new LigaKings.AtletaKings[2];
        /** Carta Suspensão: o atleta de linha do lado que está fora (até {@code sancaoAte}); null = ninguém. */
        final LigaKings.AtletaKings[] suspenso = new LigaKings.AtletaKings[2];
        final double escala;
        int min;
        String dado;
        int dadoN = 7;
        String fase = "rampa";
        /** Jogadores em campo pela regra (7). */
        final int n;
        /**
         * Rodízio (substituições ilimitadas da Kings, Balanço 2026-11 rodada 2): quem está em campo
         * (na ordem das vagas, para o humano), o banco e a energia de cada atleta no jogo.
         */
        final List<List<LigaKings.AtletaKings>> campo = List.of(new ArrayList<>(), new ArrayList<>());
        final List<List<LigaKings.AtletaKings>> banco = List.of(new ArrayList<>(), new ArrayList<>());
        final java.util.Map<LigaKings.AtletaKings, Double> energia = new java.util.IdentityHashMap<>();
        final List<List<Posicao>> vagas = List.of(new ArrayList<>(), new ArrayList<>());
        /** O lado usa a escalação do técnico humano (vagas da formação e ordem do banco). */
        final boolean[] escalacaoHumana = new boolean[2];
        final LigaKings.AtletaKings[] capitao = new LigaKings.AtletaKings[2];
        final int[] trocas = new int[2];
        /**
         * Chances que não viram gol (fora, defesa, trave, bloqueio): só no jogo narrado e com um Rng
         * próprio (copiado do estado, sem consumir o do jogo), para o placar ser o mesmo narrado ou não.
         */
        Rng chances;

        /** Titulares e banco. IA: o melhor goleiro + os 6 mais fortes; o resto no banco, do mais forte ao mais fraco. */
        void montar(int s) {
            LigaKings.TimeKings tm = t[s];
            List<LigaKings.AtletaKings> esc = escalados(tm);
            List<LigaKings.AtletaKings> c = campo.get(s), b = banco.get(s);
            if (esc != null) {
                // humano: os 7 primeiros começam; os demais listados são o banco, nessa ordem (só 7 = sem rodízio)
                escalacaoHumana[s] = true;
                for (int i = 0; i < esc.size(); i++) (i < n ? c : b).add(esc.get(i));
                vagas.get(s).addAll(vagasDaFormacao(tm.formacao, n));
            } else {
                // IA (e o humano sem escalação): cada um na sua posição (escalação automática), o resto no banco
                List<LigaKings.AtletaKings> l = new ArrayList<>(tm.elenco);
                l.sort(Comparator.comparingDouble((LigaKings.AtletaKings x) -> -x.forca).thenComparing(x -> x.nome == null ? "" : x.nome));
                List<LigaKings.AtletaKings> titulares = n == 7 ? escalacaoAutomatica(tm, tm.humano ? tm.formacao : "2-3-1") : List.of();
                for (LigaKings.AtletaKings x : titulares) if (c.size() < n) c.add(x);
                l.removeAll(c);
                for (LigaKings.AtletaKings x : l) (c.size() < n ? c : b).add(x);
            }
            for (LigaKings.AtletaKings x : tm.elenco) energia.put(x, 100.0);
            capitao[s] = capitaoDe(tm);
        }

        double energia(LigaKings.AtletaKings x) {
            return energia.getOrDefault(x, 100.0);
        }

        /** Força do atleta agora: cansado rende menos. */
        double rende(LigaKings.AtletaKings x) {
            return x.forca * (1 - FADIGA * (100 - energia(x)) / 100);
        }

        void atualizarForca(int s) {
            List<LigaKings.AtletaKings> c = campo.get(s);
            boolean gk = temGoleiro(t[s]);
            double soma = 0;
            for (int i = 0; i < c.size() && i < n; i++) {
                double enc = escalacaoHumana[s] && i < vagas.get(s).size() ? encaixe(c.get(i).posicao, vagas.get(s).get(i), gk) : 1;
                soma += rende(c.get(i)) * enc;
            }
            soma += Math.max(0, n - c.size()) * 30;
            double f = soma / Math.max(1, n);
            if (capitao[s] != null && c.contains(capitao[s])) f *= 1 + CAPITAO_FORCA;
            forca[s] = f;
        }

        boolean capitaoEmCampo(int s) {
            return capitao[s] != null && campo.get(s).contains(capitao[s]);
        }

        /** Um minuto: quem está em campo cansa, o banco descansa; depois a troca (se houver). */
        void cansarETrocar(int s) {
            for (LigaKings.AtletaKings x : campo.get(s)) energia.put(x, Math.max(0, energia(x) - (x.posicao == Posicao.GOL ? GASTO_GOLEIRO : x.presidente ? GASTO_PRESIDENTE : GASTO_MINUTO)));
            for (LigaKings.AtletaKings x : banco.get(s)) energia.put(x, Math.min(100, energia(x) + RECUPERA_MINUTO));
            if (min >= RAMPA) trocar(s);
            atualizarForca(s);
        }

        /**
         * Rodízio: o jogador de linha mais cansado sai quando passa do limite, e entra o reserva de
         * linha descansado (IA: o que mais rende; humano: o primeiro da ordem do banco). Uma troca
         * por minuto. A IA não troca o craque cansado por um reserva que rende muito menos.
         */
        void trocar(int s) {
            List<LigaKings.AtletaKings> c = campo.get(s), b = banco.get(s);
            if (b.isEmpty()) return;
            LigaKings.AtletaKings sai = null;
            for (LigaKings.AtletaKings x : c) if (x.posicao != Posicao.GOL && x != suspenso[s] && (sai == null || energia(x) < energia(sai))) sai = x;
            if (sai == null || energia(sai) >= LIMIAR_TROCA) return;
            LigaKings.AtletaKings entra = null;
            for (LigaKings.AtletaKings x : b) {
                if (x.posicao == Posicao.GOL || energia(x) < ENERGIA_ENTRA) continue;
                if (escalacaoHumana[s]) {
                    entra = x;
                    break;
                }
                // rodízio da IA: prefere quem é do mesmo setor de quem sai (regra do jogo: posição correta)
                boolean mesmo = setor(x.posicao) == setor(sai.posicao);
                boolean mesmoAtual = entra != null && setor(entra.posicao) == setor(sai.posicao);
                if (entra == null || (mesmo && !mesmoAtual) || (mesmo == mesmoAtual && rende(x) > rende(entra))) entra = x;
            }
            if (entra == null || (!escalacaoHumana[s] && rende(entra) < rende(sai) - 4)) return;
            c.set(c.indexOf(sai), entra);
            b.remove(entra);
            b.add(sai);
            trocas[s]++;
            lance("substituicao", "Troca no " + t[s].nome + ": sai " + sai.nome + ", entra " + entra.nome + ".", s, entra);
        }

        /** Autor de um gol/cobrança/falta: só entre quem está em campo agora (pesos de {@link MotorKings#autor}). */
        LigaKings.AtletaKings autor(LigaKings.TimeKings tm, Rng rr) {
            int s = tm == t[0] ? 0 : 1;
            List<LigaKings.AtletaKings> c = campo.get(s);
            if (suspenso[s] != null && c.contains(suspenso[s])) {
                c = new ArrayList<>(c);
                c.remove(suspenso[s]);
            }
            if (c.isEmpty()) return MotorKings.autor(tm, rr);
            return c.get(rr.peso(pesosAutor(c)));
        }

        Jogo(LigaKings.TimeKings a, LigaKings.TimeKings b, LigaKings.RegrasKings regras, Rng rng, boolean narrar, AoVivoKings aoVivo, int humano) {
            this.t = new LigaKings.TimeKings[]{a, b};
            this.rng = rng;
            this.narrar = narrar;
            this.aoVivo = aoVivo;
            this.humano = aoVivo == null ? -1 : humano;
            if (this.humano >= 0) ladoHumano[this.humano] = true;
            n = regras == null ? 7 : Math.max(5, regras.jogadores_em_campo);
            for (int s = 0; s < 2; s++) {
                montar(s);
                atualizarForca(s);
            }
            escala = regras == null ? 1.0 : Math.max(0.5, Math.min(1.5, regras.duracao_min / 40.0));
            r.mandante = a.nome;
            r.visitante = b.nome;
            r.mandanteId = a.id;
            r.visitanteId = b.id;
            chances = new Rng(rng.estado * 31 + 0x51ED);
        }

        /** Chances (não gols) por gol esperado: escala com a mesma taxa, logo com o número em campo. */
        static final double CHANCES_POR_GOL = 1.6;

        /**
         * Uma chance perdida do lado {@code s}: {@code chance_defesa} (jogador = "chutador|goleiro"),
         * {@code chance_fora}, {@code chance_trave} ou {@code chance_bloqueio}. A tela escolhe a frase.
         */
        void chance(int s) {
            LigaKings.AtletaKings x = autor(t[s], chances);
            if (x == null) return;
            double u = chances.real();
            if (u < 0.40) {
                LigaKings.AtletaKings gk = goleiroEmCampo(1 - s);
                evento("chance_defesa", s, x, x.nome + "|" + (gk == null ? "" : gk.nome));
                lance("defesa", x.nome + " finaliza" + (gk == null ? " e a defesa salva." : " e " + gk.nome + " defende."), s, x);
            } else if (u < 0.75) {
                evento("chance_fora", s, x, x.nome);
                lance("fora", x.nome + " finaliza para fora.", s, x);
            } else if (u < 0.87) {
                evento("chance_trave", s, x, x.nome);
                lance("trave", x.nome + " acerta a trave!", s, x);
            } else {
                evento("chance_bloqueio", s, x, x.nome);
                lance("fora", "Chute de " + x.nome + " bloqueado.", s, x);
            }
        }

        // ------------------------------------------------------------ narração e eventos
        void evento(String tipo, int lado, LigaKings.AtletaKings x, String detalhe) {
            r.eventos.add(new EventoJogo(min, 0, tipo, lado, x == null || x.jogadorId == null ? -1 : x.jogadorId,
                    detalhe != null ? detalhe : x == null ? null : x.nome));
        }

        void lance(String tipo, String texto, int lado, LigaKings.AtletaKings x) {
            if (!narrar) return;
            r.lances.add(new Lance(Math.max(0, min), 0, tipo, texto, lado, placar[0], placar[1], x == null ? null : x.jogadorId,
                    x == null ? null : x.nome, null, null));
        }

        // ------------------------------------------------------------ controle (ao vivo)
        @Override public int minuto() { return min; }
        @Override public int gols(int lado) { return placar[lado]; }
        @Override public String fase() { return fase; }
        @Override public String dado() { return dado; }
        @Override public List<EventoJogo> eventos() { return Collections.unmodifiableList(r.eventos); }

        /**
         * Números até agora para a tela de trocas (a mesma do futebol): posse pela força em campo, chutes
         * a partir dos gols e do tempo jogado, cartões pelos eventos. Sem sorteio (não muda o jogo).
         */
        @Override
        public br.futebas.partida.Estatisticas numerosAoVivo() {
            double soma = Math.max(1, forca[0] + forca[1]);
            int p = (int) Math.round(Math.max(30, Math.min(70, 100 * forca[0] / soma)));
            int[] posse = {p, 100 - p}, gols = new int[2], am = new int[2], vm = new int[2];
            for (EventoJogo ev : r.eventos) {
                if (ev.lado() < 0 || ev.lado() > 1) continue;
                if (ev.gol()) gols[ev.lado()]++;
                else if ("amarelo".equals(ev.tipo())) am[ev.lado()]++;
                else if ("vermelho".equals(ev.tipo())) vm[ev.lado()]++;
            }
            double f = Math.min(1, Math.max(0, min / 40.0));
            int[] fin = new int[2], alvo = new int[2], esc = new int[2], fal = new int[2], imp = new int[2];
            for (int s = 0; s < 2; s++) {
                double share = posse[s] / 50.0;
                alvo[s] = gols[s] + (int) (f * 3.5 * share);
                fin[s] = alvo[s] + (int) (f * 6 * share);
                esc[s] = (int) (f * 3 * share);
                fal[s] = (int) (f * 4.5) + am[s] + vm[s];
                imp[s] = (int) (f * 1.5 * share);
            }
            return new br.futebas.partida.Estatisticas(min, posse, fin, alvo, esc, fal, am, vm, imp);
        }
        @Override public Carta carta(int lado) { return carta[lado]; }

        boolean janelaCarta() {
            return (min >= RAMPA && min < GOL_DUPLO_DE) || (min >= DADO_ATE && min < FIM_REGULAMENTAR);
        }

        boolean faseEspecial() {
            return min < RAMPA || (min >= GOL_DUPLO_DE && min < DADO_ATE) || min >= FIM_REGULAMENTAR;
        }

        @Override public boolean podeUsarCarta(int lado) { return carta[lado] != null && janelaCarta(); }

        @Override public boolean podePenaltiPresidente(int lado) {
            return !presidenteUsado[lado] && t[lado].presidente != null && !faseEspecial();
        }

        @Override
        public int emCampo(int lado) {
            int base = min < RAMPA ? min + 1 : (min >= DADO_DE && min < DADO_ATE) ? dadoN : 7;
            int menos = 0;
            for (int ate : foraAte.get(lado)) if (min < ate) menos++;
            if (min < sancaoAte[lado]) menos++;
            return Math.max(1, base - menos);
        }

        @Override
        public boolean usarCarta(int lado, Carta coringaVira, String alvoInvertido) {
            if (!podeUsarCarta(lado)) {
                // o técnico clicou, mas a ordem chegou com a janela fechada: avisa (a carta continua com ele)
                if (ladoHumano[lado] && carta[lado] != null)
                    lance("carta", "A janela da carta fechou: a " + carta[lado].nome + " fica para a próxima janela.", lado, null);
                return false;
            }
            Carta c = carta[lado];
            carta[lado] = null;
            evento("carta_usada", lado, null, c.nome);
            lance("carta", "Carta secreta do " + t[lado].nome + ": " + c.nome + "!", lado, null);
            if (c == Carta.CORINGA) {
                if (coringaVira == null && carta[1 - lado] != null) {
                    // rouba a carta ainda não usada do adversário
                    c = carta[1 - lado];
                    carta[1 - lado] = null;
                    lance("carta", "O Coringa rouba a carta do " + t[1 - lado].nome + " (" + c.nome + ")!", lado, null);
                } else {
                    c = coringaVira == null || coringaVira == Carta.CORINGA ? Carta.GOL_DUPLO : coringaVira;
                    lance("carta", "O Coringa vira " + c.nome + ".", lado, null);
                }
                evento("carta_usada", lado, null, "Coringa → " + c.nome);
            }
            aplicarCarta(lado, c, alvoInvertido);
            return true;
        }

        void aplicarCarta(int lado, Carta c, String alvoInvertido) {
            switch (c) {
                case GOL_DUPLO -> golDuploAte[lado] = min + MINUTOS_GOL_DUPLO_CARTA;
                case SANCAO -> suspender(lado, alvoInvertido);
                case JOGADOR_ESTRELA -> escolherEstrela(lado, alvoInvertido);
                case PENALTI -> penaltiDaCarta(lado, autor(t[lado], rng));
                case PENALTI_SHOOTOUT -> cobranca(lado, "shootout", CONV_SHOOTOUT + 0.004 * (forca[lado] - forca[1 - lado]), autor(t[lado], rng));
                case PENALTI_INVERTIDO -> penaltiInvertido(lado, alvoInvertido);
                case CORINGA -> golDuploAte[lado] = min + MINUTOS_GOL_DUPLO_CARTA;
            }
        }

        /** Atleta em campo do lado {@code s} pela chave, fora o goleiro de ofício (null se não houver). */
        LigaKings.AtletaKings deLinhaEmCampo(int s, String chave) {
            if (chave == null) return null;
            for (LigaKings.AtletaKings x : campo.get(s)) if (chave.equals(x.chave()) && x.posicao != Posicao.GOL && x != suspenso[s]) return x;
            return null;
        }

        /** O melhor finalizador em campo (o que mais rende, fora o goleiro); sem ninguém de linha, o do elenco. */
        LigaKings.AtletaKings melhorFinalizador(int s) {
            LigaKings.AtletaKings m = null;
            for (LigaKings.AtletaKings x : campo.get(s))
                if (x.posicao != Posicao.GOL && x != suspenso[s] && (m == null || rende(x) > rende(m))) m = x;
            return m != null ? m : melhor(t[s]);
        }

        /**
         * Carta Jogador Estrela (regra do jogo, 12/11/2026): o time escolhe um atleta em campo e todo
         * gol DELE vale 2 até o fim do jogo (no placar; na artilharia ele ganha 1 gol). Humano: a
         * escolha da tela; IA (ou sem escolha): o melhor finalizador.
         */
        void escolherEstrela(int lado, String chave) {
            LigaKings.AtletaKings x = null;
            if (chave != null) for (LigaKings.AtletaKings a : campo.get(lado)) if (chave.equals(a.chave())) x = a;
            if (x == null) x = melhorFinalizador(lado);
            estrela[lado] = x;
            if (x == null) return;
            evento("jogador_estrela", lado, x, x.nome);
            lance("carta", "⭐ " + x.nome + " é o jogador estrela: os gols dele valem 2.", lado, x);
        }

        /**
         * Carta Suspensão (regra do jogo, 12/11/2026): quem usa escolhe um atleta de linha do rival em
         * campo (nunca o goleiro); ele fica fora {@link #MINUTOS_SANCAO} minutos e o rival joga com um
         * a menos (conta no número em campo, e daí na chance de gol). Humano: a escolha da tela; IA
         * (ou sem escolha): o melhor de linha do rival.
         */
        void suspender(int lado, String chave) {
            int def = 1 - lado;
            if (suspenso[def] != null) voltarSuspenso(def);
            LigaKings.AtletaKings x = deLinhaEmCampo(def, chave);
            if (x == null) x = melhorFinalizador(def);
            if (x != null && x.posicao == Posicao.GOL) x = null;
            sancaoAte[def] = min + MINUTOS_SANCAO;
            suspenso[def] = x;
            String quem = x == null ? "um jogador do " + t[def].nome : x.nome;
            evento("suspensao", def, x, quem);
            lance("carta", "Suspensão: " + quem + " (" + t[def].nome + ") fora por " + MINUTOS_SANCAO + " minutos.", def, x);
        }

        void voltarSuspenso(int s) {
            LigaKings.AtletaKings x = suspenso[s];
            suspenso[s] = null;
            if (x == null) return;
            evento("suspensao_fim", s, x, x.nome);
            lance("substituicao", x.nome + " volta ao jogo depois da suspensão.", s, x);
        }

        /** Fim das suspensões que venceram (a cada minuto, também no match ball). */
        void conferirSuspensoes() {
            for (int s = 0; s < 2; s++) if (suspenso[s] != null && min >= sancaoAte[s]) voltarSuspenso(s);
        }

        /** Goleiro em campo do lado (o de ofício; senão quem estiver na 1ª vaga); null sem ninguém. */
        LigaKings.AtletaKings goleiroEmCampo(int s) {
            for (LigaKings.AtletaKings x : campo.get(s)) if (x.posicao == Posicao.GOL) return x;
            return campo.get(s).isEmpty() ? null : campo.get(s).get(0);
        }

        /** "GOL", "DEFENDEU" ou "PRA FORA" (o erro: 55% defesa do goleiro, 45% para fora). */
        String resultadoCobranca(double prob) {
            if (rng.chance(Math.max(0.2, Math.min(0.92, prob)))) return "GOL";
            return rng.chance(0.55) ? "DEFENDEU" : "PRA FORA";
        }

        /**
         * Carta Pênalti: um atleta do time da carta cobra contra o goleiro adversário. Sempre gera o
         * evento {@code penalti_carta_gol|_defendeu|_fora} (cobrador, goleiro e resultado) e, no gol,
         * o gol normal ({@code penalti_gol}, conta na artilharia de quem bateu).
         */
        void penaltiDaCarta(int lado, LigaKings.AtletaKings cobrador) {
            LigaKings.AtletaKings gk = goleiroEmCampo(1 - lado);
            String res = resultadoCobranca(CONV_PENALTI + 0.003 * (forca[lado] - forca[1 - lado]));
            String quem = cobrador == null ? t[lado].nome : cobrador.nome;
            String txt = "Pênalti da carta: " + quem + " (" + t[lado].nome + ") contra " + (gk == null ? "o gol vazio" : gk.nome) + "... " + res + "!";
            suspense("penalti_carta", lado, quem, gk, res, null);
            evento(tipoResultado("penalti_carta", res), lado, cobrador, quem + " (" + t[lado].nome + ") × " + (gk == null ? "sem goleiro" : gk.nome) + " · " + res);
            if (res.equals("GOL")) gol(lado, cobrador, "penalti_gol", txt);
            else lance("fora", txt, lado, cobrador);
        }

        /**
         * Carta Pênalti Invertido (regra do jogo, 12/11/2026): quem usa a carta escolhe um adversário
         * (em campo) para bater o pênalti; se ele converter, nada acontece; se errar (defesa ou para
         * fora), o time que usou a carta ganha 1 gol (vale 1, sem gol duplo, e não entra na
         * artilharia de ninguém). A IA (ou o humano sem escolha) chama o pior finalizador do rival.
         */
        void penaltiInvertido(int lado, String alvo) {
            int def = 1 - lado;
            LigaKings.AtletaKings cobrador = null;
            if (alvo != null) {
                // o goleiro não pode ser escolhido (regra do jogo)
                for (LigaKings.AtletaKings x : campo.get(def)) if (alvo.equals(x.chave()) && x.posicao != Posicao.GOL) cobrador = x;
                if (cobrador == null) for (LigaKings.AtletaKings x : t[def].elenco) if (alvo.equals(x.chave()) && x.posicao != Posicao.GOL) cobrador = x;
            }
            if (cobrador == null) cobrador = piorFinalizador(def);
            LigaKings.AtletaKings gk = goleiroEmCampo(lado);
            double fc = cobrador == null ? 60 : cobrador.forca;
            String res = resultadoCobranca(CONV_PENALTI - 0.1 + 0.004 * (fc - 60));
            String quem = cobrador == null ? "o adversário" : cobrador.nome;
            String base = "Pênalti invertido: " + quem + " (" + t[def].nome + ") bate contra " + (gk == null ? "o gol vazio" : gk.nome) + "... ";
            suspense("penalti_invertido", def, quem, gk, res, null);
            evento(tipoResultado("penalti_invertido", res), lado, cobrador, quem + " (" + t[def].nome + ") × " + (gk == null ? "sem goleiro" : gk.nome) + " · " + res);
            if (res.equals("GOL")) {
                lance("fora", base + "converteu, nada acontece.", def, cobrador);
            } else {
                gol(lado, null, "penalti_gol", base + (res.equals("DEFENDEU") ? "DEFENDEU" : "PRA FORA") + "! Errou: gol para o " + t[lado].nome + ".", true);
            }
        }

        static String tipoResultado(String prefixo, String res) {
            return prefixo + (res.equals("GOL") ? "_gol" : res.equals("DEFENDEU") ? "_defendeu" : "_fora");
        }

        /** O pior finalizador do lado entre quem está em campo (fora o goleiro); sem ninguém, o do elenco. */
        LigaKings.AtletaKings piorFinalizador(int s) {
            LigaKings.AtletaKings m = null;
            for (LigaKings.AtletaKings x : campo.get(s)) if (x.posicao != Posicao.GOL && x != suspenso[s] && (m == null || x.forca < m.forca)) m = x;
            return m != null ? m : pior(t[s]);
        }

        @Override
        public List<LigaKings.AtletaKings> emCampoAgora(int lado) {
            return List.copyOf(campo.get(lado));
        }

        @Override
        public List<LigaKings.AtletaKings> bancoAgora(int lado) {
            return List.copyOf(banco.get(lado));
        }

        @Override
        public double energiaDe(LigaKings.AtletaKings a) {
            return energia(a);
        }

        /** Troca do técnico: ilimitada e de volta (quem sai vai para o banco); não troca o suspenso. */
        @Override
        public boolean substituir(int lado, String sai, String entra) {
            if (lado < 0 || lado > 1 || sai == null || entra == null) return false;
            List<LigaKings.AtletaKings> c = campo.get(lado), b = banco.get(lado);
            LigaKings.AtletaKings s = null, e = null;
            for (LigaKings.AtletaKings x : c) if (sai.equals(x.chave())) s = x;
            for (LigaKings.AtletaKings x : b) if (entra.equals(x.chave())) e = x;
            if (s == null || e == null || s == suspenso[lado]) return false;
            c.set(c.indexOf(s), e);
            b.remove(e);
            b.add(s);
            trocas[lado]++;
            lance("substituicao", "Troca no " + t[lado].nome + ": sai " + s.nome + ", entra " + e.nome + ".", lado, e);
            atualizarForca(lado);
            return true;
        }

        @Override
        public boolean cobrarPenaltiPresidente(int lado) {
            if (!podePenaltiPresidente(lado)) return false;
            presidenteUsado[lado] = true;
            String pres = cobradorPresidente(t[lado], min);
            cobradorPresidente[lado] = pres;
            LigaKings.AtletaKings quem = presidenteJogador(t[lado], pres);
            String res = resultadoCobranca(CONV_PRESIDENTE);
            suspense("penalti_presidente", lado, pres, goleiroEmCampo(1 - lado), res, null);
            if (res.equals("GOL")) {
                gol(lado, quem, "penalti_presidente_gol", "PÊNALTI DO PRESIDENTE! " + pres + " bate e marca para o " + t[lado].nome + ".");
            } else {
                evento("penalti_presidente_perdido", lado, quem, pres);
                lance("fora", "O presidente " + pres + " cobra o pênalti e perde" + (res.equals("DEFENDEU") ? ": o goleiro defendeu!" : ": pra fora!"), lado, null);
            }
            return true;
        }

        /** Avisa a tela da cobrança antes do resultado (suspense; só no jogo acompanhado ao vivo). */
        void suspense(String tipo, int lado, String cobrador, LigaKings.AtletaKings goleiro, String res, String[] disputa) {
            if (aoVivo == null) return;
            aoVivo.cobranca(this, new AoVivoKings.Cobranca(tipo, lado, cobrador, t[lado].nome, goleiro == null ? null : goleiro.nome, res, disputa));
        }

        void cobranca(int lado, String tipo, double prob, LigaKings.AtletaKings autor) {
            String res = resultadoCobranca(prob);
            suspense("shootout", lado, autor == null ? t[lado].nome : autor.nome, goleiroEmCampo(1 - lado), res, null);
            if (res.equals("GOL")) {
                gol(lado, autor, tipo + "_gol", (tipo.equals("shootout") ? "Shoot out convertido" : "Pênalti convertido") + " por "
                        + (autor == null ? t[lado].nome : autor.nome) + "!");
            } else {
                evento(tipo + "_perdido", lado, autor, null);
                lance("fora", (tipo.equals("shootout") ? "Shoot out perdido" : "Pênalti perdido") + " por " + (autor == null ? t[lado].nome : autor.nome)
                        + (res.equals("DEFENDEU") ? ": o goleiro defendeu." : ": pra fora."), lado, autor);
            }
        }

        /** Gol do lado; vale 2 no gol duplo (fase ou carta) ou quando é do jogador estrela. */
        void gol(int lado, LigaKings.AtletaKings autor, String tipo, String texto) {
            gol(lado, autor, tipo, texto, false);
        }

        /** {@code valeUm}: gol que vale sempre 1 (o do Pênalti Invertido). */
        void gol(int lado, LigaKings.AtletaKings autor, String tipo, String texto, boolean valeUm) {
            boolean presidente = tipo.startsWith("penalti_presidente");
            boolean duplo = !valeUm && !presidente && ((min >= GOL_DUPLO_DE && min < DADO_DE) || min < golDuploAte[lado]);
            boolean daEstrela = !valeUm && !presidente && autor != null && autor == estrela[lado];
            if (daEstrela) {
                duplo = true;
                if (texto == null) texto = "GOL DUPLO de " + autor.nome + " (jogador estrela)!";
                else texto = texto + " GOL DUPLO do jogador estrela!";
            }
            int valor = duplo ? 2 : 1;
            placar[lado] += valor;
            // pênalti do presidente: o gol é do time, não entra na artilharia nem nas estatísticas do atleta
            // (regra da Kings; os presidentes da IOS eram artilheiros em 60 de 60 competições). Balanço 2026-11, rodada 2.
            if (autor != null && !presidente) {
                autor.gols++;
                if (autor.jogadorId != null) r.gols.add(new int[]{lado, autor.jogadorId, Math.max(1, min), 0, 0});
            }
            evento(duplo ? tipo + "_x2" : tipo, lado, presidente ? null : autor, presidente ? cobradorPresidente[lado] : null);
            lance("gol", texto == null ? "GOL do " + t[lado].nome + "! " + (autor == null ? "" : autor.nome + " marca.") + (duplo ? " Vale 2!" : "")
                    : texto + (duplo ? " Vale 2!" : ""), lado, autor);
        }

        // ------------------------------------------------------------ IA (cartas e presidente)
        void decidirIA(int lado) {
            int dif = placar[lado] - placar[1 - lado];
            if (podeUsarCarta(lado)) {
                Carta c = carta[lado];
                boolean usar = switch (c) {
                    case JOGADOR_ESTRELA -> true;
                    case GOL_DUPLO, SANCAO, CORINGA -> dif <= 0 || min >= 30;
                    default -> dif < 0 || min >= 28 || rng.chance(0.08);
                };
                if (usar && (min >= 25 || dif < 0 || rng.chance(0.25))) usarCarta(lado, null, null);
            }
            if (podePenaltiPresidente(lado) && (dif < 0 || min >= 30 || rng.chance(0.04))) {
                cobrarPenaltiPresidente(lado);
            }
        }

        // ------------------------------------------------------------ jogo
        ResultadoPartida rodar() {
            List<Carta> baralho = new ArrayList<>(List.of(BARALHO));
            rng.embaralhar(baralho);
            for (int s = 0; s < 2; s++) {
                carta[s] = baralho.get(s);
                if (s == 0 && forcarCarta0 != null) carta[s] = forcarCarta0;
                evento("carta_sorteada", s, null, carta[s].nome);
            }
            min = 0;
            lance("inicio", "A bola cai da jaula! Rampa: 1 contra 1.", -1, null);
            for (min = 0; min < FIM_REGULAMENTAR; min++) {
                if (min == RAMPA) {
                    fase = "normal";
                    pausar("carta_disponivel");
                }
                if (min == GOL_DUPLO_DE) {
                    fase = "gol_duplo";
                    evento("fase", -1, null, "Gol duplo");
                    lance("tatica", "Começa o GOL DUPLO: até o intervalo, todo gol vale 2!", -1, null);
                }
                if (min == DADO_DE) {
                    lance("intervalo", "Intervalo: " + t[0].nome + " " + placar[0] + " x " + placar[1] + " " + t[1].nome + ".", -1, null);
                    pausar("intervalo");
                    int face = rng.inteiro(6);
                    dadoN = face < 2 ? 3 : face < 4 ? 2 : 1;
                    dado = dadoN + "v" + dadoN;
                    fase = "dado";
                    evento("dado", -1, null, dado);
                    lance("tatica", "DADO! " + dado + " até o minuto 23.", -1, null);
                    pausar("dado");
                }
                if (min == DADO_ATE) {
                    fase = "normal";
                    lance("tatica", "Todos de volta: 7 contra 7.", -1, null);
                    pausar("carta_disponivel");
                }
                if (aoVivo != null) aoVivo.minuto(this);
                for (int s = 0; s < 2; s++) if (!ladoHumano[s]) decidirIA(s);
                minutoDeJogo();
            }
            min = FIM_REGULAMENTAR;
            if (placar[0] == placar[1]) shootouts();
            else matchBall();
            r.golsMandante = placar[0];
            r.golsVisitante = placar[1];
            estatisticas();
            lance("fim", "Fim de jogo: " + t[0].nome + " " + placar[0] + " x " + placar[1] + " " + t[1].nome
                    + (r.penaltisMandante != null ? " (shoot out " + r.penaltisMandante + " x " + r.penaltisVisitante + ")" : "") + ".", -1, null);
            pausar("fim");
            return r;
        }

        /**
         * Números do jogo para a tela de fim (a mesma do futebol): posse pela força, finalizações e
         * chutes no gol a partir dos gols, faltas pelos cartões, gols esperados, notas e o melhor em
         * campo (quem mais marcou, desempate pela força). Usa um Rng derivado: não muda o jogo.
         */
        void estatisticas() {
            Rng e = rng.derivar(4242);
            double soma = Math.max(1, forca[0] + forca[1]);
            r.posse[0] = (int) Math.round(Math.max(30, Math.min(70, 100 * forca[0] / soma + e.gauss(0, 4))));
            r.posse[1] = 100 - r.posse[0];
            int[] gols = {0, 0};
            for (EventoJogo ev : r.eventos) if (ev.gol() && ev.lado() >= 0 && ev.lado() <= 1) gols[ev.lado()]++;
            for (int s = 0; s < 2; s++) {
                r.noGol[s] = gols[s] + 2 + e.inteiro(4);
                r.chutes[s] = r.noGol[s] + 3 + e.inteiro(6);
                r.faltas[s] = 2 + e.inteiro(5);
            }
            for (EventoJogo ev : r.eventos) if ((ev.tipo().equals("amarelo") || ev.tipo().equals("vermelho")) && ev.lado() >= 0) r.faltas[ev.lado()]++;
            r.xgMandante = Math.round((gols[0] * 0.8 + r.noGol[0] * 0.25) * 10) / 10.0;
            r.xgVisitante = Math.round((gols[1] * 0.8 + r.noGol[1] * 0.25) * 10) / 10.0;
            java.util.Map<LigaKings.AtletaKings, Integer> golsDe = new java.util.IdentityHashMap<>();
            for (int s = 0; s < 2; s++) for (LigaKings.AtletaKings x : t[s].elenco) golsDe.put(x, 0);
            for (EventoJogo ev : r.eventos) {
                if (!ev.gol() || ev.jogador() == null || ev.lado() < 0 || ev.tipo().startsWith("penalti_presidente")) continue;
                for (LigaKings.AtletaKings x : t[ev.lado()].elenco)
                    if (ev.jogadorId() >= 0 ? x.jogadorId != null && x.jogadorId == ev.jogadorId() : ev.jogador().equals(x.nome)) golsDe.merge(x, 1, Integer::sum);
            }
            int venc = placar[0] > placar[1] ? 0 : placar[1] > placar[0] ? 1 : -1;
            LigaKings.AtletaKings melhor = null;
            double notaMelhor = -1;
            for (int s = 0; s < 2; s++) for (LigaKings.AtletaKings x : t[s].elenco) {
                double nota = Math.max(4.5, Math.min(10, 6.0 + 0.8 * golsDe.getOrDefault(x, 0) + (x.forca - 65) / 25 + (s == venc ? 0.4 : 0) + e.gauss(0, 0.3)));
                nota = Math.round(nota * 10) / 10.0;
                if (x.jogadorId != null) r.notas.put(x.jogadorId, nota);
                if (nota > notaMelhor) {
                    notaMelhor = nota;
                    melhor = x;
                }
            }
            if (melhor != null) {
                r.melhorNome = melhor.nome;
                r.melhorId = melhor.jogadorId;
            }
        }

        void pausar(String motivo) {
            if (aoVivo != null) aoVivo.pausa(this, motivo);
        }

        /**
     * Ritmo de gols no match ball (campo aberto, todo mundo no ataque): com o ritmo normal o match
     * ball durava 7 min em média e até 58 min; com 2x, ~3,5 min. Balanço 2026-11. Com o ritmo por número
     * em campo ({@link #numeros}, 12/11/2026) o campo vazio já acelera: 1,5x mantém ~4,2 min de match ball.
     */
    static final double MATCH_BALL_RITMO = 1.5;

    /**
     * Gols pelo número em campo (regra do jogo, 12/11/2026: "quanto menos jogadores, mais gol"):
     * com n por lado (média dos dois), o ritmo é {@code 1 + K_POUCOS × (7 − n)} vezes o do 7x7 —
     * o 7x7 é o menor; a rampa (1v1 → 5v5), o dado e o match ball (5v5 → 1v1) sobem conforme o campo
     * esvazia. Vantagem numérica (cartão, suspensão) pesa {@code (ns/no)^1,1}.
     */
    static final double K_POUCOS = 0.18;

    static double numeros(int ns, int no) {
        double n = Math.max(1, (ns + no) / 2.0);
        return Math.pow((double) Math.max(1, ns) / Math.max(1, no), 1.1) * (1 + K_POUCOS * Math.max(0, 7 - n));
    }

    /** Chance, por minuto e por time, de falta no 1v1 (cada falta vira shootout para o adversário). */
        static final double FALTA_1V1 = 0.05;

        /** Falta no 1v1 (rampa, dado ou match ball): sem cartão, shootout para o adversário. */
        boolean faltaNoUmContraUm(int s) {
            if (rng.real() >= FALTA_1V1) return false;
            LigaKings.AtletaKings x = autor(t[s], rng);
            lance("cartao", "Falta no 1v1 de " + (x == null ? t[s].nome : x.nome) + ": shoot out para o " + t[1 - s].nome + "!", s, x);
            cobranca(1 - s, "shootout", CONV_SHOOTOUT + 0.004 * (forca[1 - s] - forca[s]), autor(t[1 - s], rng));
            return true;
        }

        void minutoDeJogo() {
            conferirSuspensoes();
            for (int s = 0; s < 2; s++) cansarETrocar(s);
            int n0 = emCampo(0), n1 = emCampo(1);
            boolean umContraUm = n0 == 1 && n1 == 1;
            for (int s = 0; s < 2; s++) {
                int ns = s == 0 ? n0 : n1, no = s == 0 ? n1 : n0;
                double taxa = taxa(s, ns, no);
                if (rng.real() < taxa) gol(s, autor(t[s], rng), "gol", null);
                else if (narrar && chances.real() < taxa * CHANCES_POR_GOL) chance(s);
                if (umContraUm) {
                    faltaNoUmContraUm(s);
                } else if (min >= RAMPA) {
                    if (rng.real() < VERMELHO_JOGO / FIM_REGULAMENTAR) {
                        LigaKings.AtletaKings x = autor(t[s], rng);
                        foraAte.get(s).add(min + MINUTOS_VERMELHO);
                        evento("vermelho", s, x, null);
                        lance("vermelho", "Vermelho para " + (x == null ? "um jogador" : x.nome) + ": 5 minutos com um a menos.", s, x);
                    } else if (rng.real() < AMARELO_JOGO / FIM_REGULAMENTAR * (capitaoEmCampo(s) ? CAPITAO_CARTOES : 1)) {
                        LigaKings.AtletaKings x = autor(t[s], rng);
                        foraAte.get(s).add(min + MINUTOS_AMARELO);
                        evento("amarelo", s, x, null);
                        lance("cartao", "Amarelo para " + (x == null ? "um jogador" : x.nome) + ": 2 minutos fora.", s, x);
                    }
                }
            }
        }

        double taxa(int s, int ns, int no) {
            double qual = Math.exp(0.03 * 25 * Math.tanh((forca[s] - forca[1 - s]) / 25));
            return Math.min(0.8, GOLS_BASE * escala / FIM_REGULAMENTAR * qual * numeros(ns, no));
        }

        /**
         * Match ball (min 36+): o alvo é o placar do líder + 1 (ex.: 3 x 0 → quem chegar a 4 vence).
         * O líder precisa de 1 gol; quem está atrás precisa virar até o alvo. O campo diminui a cada
         * minuto (5v5, 4v4, 3v3, 2v2, 1v1 com goleiro, e fica no 1v1) até alguém chegar ao alvo.
         */
        void matchBall() {
            fase = "match_ball";
            int alvo = Math.max(placar[0], placar[1]) + 1;
            evento("fase", -1, null, "Match ball");
            lance("tatica", "MATCH BALL! Quem chegar a " + alvo + " gols vence.", -1, null);
            pausar("match_ball");
            int[] ns = {5, 4, 3, 2, 1};
            for (int k = 0; k < 200; k++, min++) {
                conferirSuspensoes();
                int n = ns[Math.min(k, ns.length - 1)];
                // suspensão que ainda corre no match ball: um a menos (no mínimo 1)
                int[] nl = {Math.max(1, n - (suspenso[0] != null ? 1 : 0)), Math.max(1, n - (suspenso[1] != null ? 1 : 0))};
                int lider = placar[0] >= placar[1] ? 0 : 1;
                for (int s : new int[]{lider, 1 - lider}) {
                    if (rng.real() < Math.min(0.8, taxa(s, nl[s], nl[1 - s]) * MATCH_BALL_RITMO)) {
                        gol(s, autor(t[s], rng), "gol", null);
                        if (placar[s] >= alvo) return;
                    }
                    if (n == 1 && faltaNoUmContraUm(s) && placar[1 - s] >= alvo) return;
                }
            }
            // segurança (não deve acontecer): quem estava na frente leva
            int lider = placar[0] >= placar[1] ? 0 : 1;
            while (placar[lider] < alvo) gol(lider, autor(t[lider], rng), "gol", null);
        }

        void shootouts() {
            fase = "match_ball";
            evento("fase", -1, null, "Shoot out");
            lance("penalidades", "Empate no tempo: decisão no SHOOT OUT!", -1, null);
            pausar("shootouts");
            // a ordem dos batedores: o técnico que acompanha ao vivo escolhe a dele (como na disputa de pênaltis do
            // futebol); sem tela, a IA e quem deixa no automático seguem como antes (ordem da escalação ou sorteio)
            List<List<LigaKings.AtletaKings>> ordem = new ArrayList<>(java.util.Arrays.asList(null, null));
            if (aoVivo != null) {
                for (int s = 0; s < 2; s++) {
                    if (t[s] == null || !t[s].humano) continue;
                    List<String> ch = aoVivo.ordemShootout(s, Collections.unmodifiableList(sugestaoShootout(t[s])));
                    if (ch == null) continue;
                    List<LigaKings.AtletaKings> l = new ArrayList<>();
                    for (String k : ch) for (LigaKings.AtletaKings a : t[s].elenco) if (k.equals(a.chave()) && !l.contains(a)) l.add(a);
                    if (!l.isEmpty()) ordem.set(s, l);
                }
            }
            int[] g = new int[2];
            StringBuilder[] hist = {new StringBuilder(), new StringBuilder()};
            double[] p = {CONV_SHOOTOUT + 0.004 * (forca[0] - forca[1]), CONV_SHOOTOUT + 0.004 * (forca[1] - forca[0])};
            for (int i = 0; i < 5 || g[0] == g[1]; i++) {
                for (int s = 0; s < 2; s++) {
                    List<LigaKings.AtletaKings> os = ordem.get(s);
                    LigaKings.AtletaKings x = os != null ? os.get(i % os.size()) : cobradorDaVez(t[s], i);
                    if (x == null) x = MotorKings.autor(t[s], rng); // shootouts: qualquer um do elenco (relacionados do humano), não só quem está em campo
                    String res = resultadoCobranca(Math.min(0.9, p[s]));
                    boolean ok = res.equals("GOL");
                    suspense("disputa", s, x == null ? t[s].nome : x.nome, goleiroEmCampo(1 - s), res, new String[]{hist[0].toString(), hist[1].toString()});
                    hist[s].append(ok ? 'G' : 'X');
                    if (ok) g[s]++;
                    evento(ok ? "disputa_gol" : "disputa_perdido", s, x, null);
                }
                if (i > 30) {
                    g[rng.inteiro(2)]++;
                    break;
                }
            }
            r.penaltisMandante = g[0];
            r.penaltisVisitante = g[1];
        }
    }

    /**
     * O pênalti do presidente é sempre cobrado por um presidente do time (a pessoa, que nunca muda);
     * com dois presidentes, eles se revezam (pelo minuto da cobrança, sem sorteio).
     */
    static String cobradorPresidente(LigaKings.TimeKings t, int minuto) {
        List<String> l = t.nomesPresidentes();
        if (t.batedorPresidente != null && l.contains(t.batedorPresidente)) return t.batedorPresidente; // escolha do técnico humano
        if (l.isEmpty()) return t.presidente;
        return l.get(Math.floorMod(minuto, l.size()));
    }

    /** O atleta do elenco que é aquele presidente (presidente-jogador), ou null. */
    static LigaKings.AtletaKings presidenteJogador(LigaKings.TimeKings t, String nome) {
        if (nome == null) return null;
        for (LigaKings.AtletaKings a : t.elenco) if (a.presidente && nome.equalsIgnoreCase(a.nome)) return a;
        return null;
    }

    // ------------------------------------------------------------ escalação do humano (formação 7x7)
    /** Formações 7x7 (goleiro + linhas de defesa-meio-ataque). */
    public static final List<String> FORMACOES = List.of("2-3-1", "3-2-1", "2-2-2", "3-1-2", "1-3-2", "4-2");

    /** Vagas da formação: GOL, depois defensores (ZAG), meias (MC) e atacantes (CA); completa com MC. */
    public static List<Posicao> vagasDaFormacao(String formacao, int emCampo) {
        List<Posicao> l = new ArrayList<>();
        l.add(Posicao.GOL);
        String f = formacao == null || !FORMACOES.contains(formacao) ? "2-3-1" : formacao;
        String[] p = f.split("-");
        Posicao[] tipo = p.length == 2 ? new Posicao[]{Posicao.ZAG, Posicao.MC} : new Posicao[]{Posicao.ZAG, Posicao.MC, Posicao.CA};
        for (int i = 0; i < p.length; i++) for (int k = 0; k < Integer.parseInt(p[i]); k++) l.add(tipo[i]);
        while (l.size() < Math.max(emCampo, 7)) l.add(Posicao.MC);
        return l;
    }

    /** Rendimento de quem joga numa vaga: no setor 1,0; setor vizinho 0,95; dois setores 0,9; goleiro na linha (ou o contrário) 0,75. */
    public static double encaixe(Posicao jogador, Posicao vaga, boolean temGoleiro) {
        if (jogador == null || vaga == null) return 1;
        int a = setor(jogador), b = setor(vaga);
        if (a == b) return 1;
        // muitos elencos da Kings não têm goleiro de ofício: sem goleiro no elenco, quem vai pro gol não perde
        if (b == 0 && !temGoleiro) return 1;
        if (a == 0 || b == 0) return 0.75;
        return Math.abs(a - b) == 1 ? 0.95 : 0.9;
    }

    /**
     * Escalação automática (regra do jogo, 12/11/2026: "sempre priorizar os jogadores na posição
     * correta"): os 7 na ordem das vagas da formação (o 1º é o goleiro). Atribuição ótima (programação
     * dinâmica sobre as vagas e os subconjuntos do elenco, até 12 atletas) maximizando força × encaixe,
     * com penalidade forte fora do setor: o goleiro de ofício sempre no gol e cada vaga com alguém do
     * setor, se houver; só sem ninguém do setor entra o melhor encaixe seguinte.
     */
    public static List<LigaKings.AtletaKings> escalacaoAutomatica(LigaKings.TimeKings t, String formacao) {
        List<Posicao> vagas = vagasDaFormacao(formacao, 7);
        List<LigaKings.AtletaKings> el = new ArrayList<>(t.elenco);
        el.sort(Comparator.comparingDouble((LigaKings.AtletaKings x) -> -x.forca).thenComparing(x -> x.nome == null ? "" : x.nome));
        if (el.size() > 16) el = new ArrayList<>(el.subList(0, 16));
        int n = el.size(), k = Math.min(vagas.size(), n);
        boolean gk = temGoleiro(t);
        double[][] valor = new double[k][n];
        for (int v = 0; v < k; v++)
            for (int i = 0; i < n; i++) {
                LigaKings.AtletaKings a = el.get(i);
                boolean mesmo = setor(a.posicao) == setor(vagas.get(v)) || (vagas.get(v) == Posicao.GOL && !gk);
                valor[v][i] = a.forca * encaixe(a.posicao, vagas.get(v), gk) - (mesmo ? 0 : FORA_DE_POSICAO);
            }
        // dp[v][mask]: melhor soma preenchendo as vagas 0..v-1 com os atletas de mask
        java.util.Map<Long, Double> memo = new java.util.HashMap<>();
        int[] escolha = new int[k];
        double[] melhor = {Double.NEGATIVE_INFINITY};
        // busca com poda simples (k ≤ 7, n ≤ 16): ordem das vagas, atletas em ordem de valor
        buscar(0, 0, 0, k, n, valor, new int[k], escolha, melhor, memo);
        List<LigaKings.AtletaKings> r = new ArrayList<>();
        for (int v = 0; v < k; v++) r.add(el.get(escolha[v]));
        return r;
    }

    /** Penalidade (em pontos de força) por jogador fora do setor na escalação automática. */
    static final double FORA_DE_POSICAO = 40;

    private static void buscar(int v, long mask, double soma, int k, int n, double[][] valor, int[] atual, int[] escolha, double[] melhor,
                               java.util.Map<Long, Double> memo) {
        if (v == k) {
            if (soma > melhor[0]) {
                melhor[0] = soma;
                System.arraycopy(atual, 0, escolha, 0, k);
            }
            return;
        }
        long chave = mask * 8 + v;
        Double visto = memo.get(chave);
        if (visto != null && visto >= soma) return; // já chegou aqui com soma maior
        memo.put(chave, soma);
        for (int i = 0; i < n; i++) {
            if ((mask & (1L << i)) != 0) continue;
            atual[v] = i;
            buscar(v + 1, mask | (1L << i), soma + valor[v][i], k, n, valor, atual, escolha, melhor, memo);
        }
    }

    /** O elenco tem goleiro de ofício? */
    public static boolean temGoleiro(LigaKings.TimeKings t) {
        for (LigaKings.AtletaKings a : t.elenco) if (a.posicao == Posicao.GOL) return true;
        return false;
    }

    private static int setor(Posicao p) {
        return switch (p) {
            case GOL -> 0;
            case ZAG, LD, LE -> 1;
            case VOL, MC -> 2;
            default -> p.name().startsWith("M") ? 2 : 3;
        };
    }

    /** Os escalados pelo humano (na ordem das vagas, completando com os mais fortes); null = automático. */
    public static List<LigaKings.AtletaKings> escalados(LigaKings.TimeKings t) {
        if (t == null || !t.humano || t.titulares == null || t.titulares.isEmpty()) return null;
        List<LigaKings.AtletaKings> l = new ArrayList<>();
        for (String k : t.titulares) for (LigaKings.AtletaKings a : t.elenco) if (k.equals(a.chave()) && !l.contains(a)) l.add(a);
        if (l.isEmpty()) return null;
        List<LigaKings.AtletaKings> resto = new ArrayList<>(t.elenco);
        resto.removeAll(l);
        resto.sort(Comparator.comparingDouble((LigaKings.AtletaKings a) -> -a.forca));
        while (l.size() < 7 && !resto.isEmpty()) l.add(resto.remove(0));
        return l;
    }

    /**
     * Sugestão da ordem do shoot out para a tela do técnico: os cobradores definidos na escalação (na ordem
     * dele), depois os escalados e o resto do elenco, do mais forte ao mais fraco.
     */
    public static List<LigaKings.AtletaKings> sugestaoShootout(LigaKings.TimeKings t) {
        List<LigaKings.AtletaKings> l = new ArrayList<>();
        if (t.cobradores != null) for (String k : t.cobradores) for (LigaKings.AtletaKings a : t.elenco) if (k.equals(a.chave()) && !l.contains(a)) l.add(a);
        List<LigaKings.AtletaKings> esc = escalados(t);
        List<LigaKings.AtletaKings> resto = new ArrayList<>(esc == null ? List.of() : esc);
        resto.sort(Comparator.comparingDouble((LigaKings.AtletaKings a) -> -a.forca));
        List<LigaKings.AtletaKings> outros = new ArrayList<>(t.elenco);
        outros.sort(Comparator.comparingDouble((LigaKings.AtletaKings a) -> -a.forca));
        resto.addAll(outros);
        for (LigaKings.AtletaKings a : resto) if (!l.contains(a)) l.add(a);
        return l;
    }

    /** Cobrador de shootout da vez pela ordem do humano (null = sorteio do motor). */
    static LigaKings.AtletaKings cobradorDaVez(LigaKings.TimeKings t, int vez) {
        if (t == null || !t.humano || t.cobradores == null || t.cobradores.isEmpty()) return null;
        List<LigaKings.AtletaKings> l = new ArrayList<>();
        for (String k : t.cobradores) for (LigaKings.AtletaKings a : t.elenco) if (k.equals(a.chave())) l.add(a);
        return l.isEmpty() ? null : l.get(Math.floorMod(vez, l.size()));
    }

    private static LigaKings.AtletaKings melhor(LigaKings.TimeKings t) {
        LigaKings.AtletaKings m = null;
        for (LigaKings.AtletaKings a : t.elenco) if (m == null || a.forca > m.forca) m = a;
        return m;
    }

    private static LigaKings.AtletaKings pior(LigaKings.TimeKings t) {
        LigaKings.AtletaKings m = null;
        for (LigaKings.AtletaKings a : t.elenco) if (a.posicao != br.futebas.mundo.Posicao.GOL && (m == null || a.forca < m.forca)) m = a;
        return m;
    }

    private static LigaKings.AtletaKings autor(LigaKings.TimeKings t, Rng rng) {
        if (t.elenco.isEmpty()) return null;
        // escalação do humano: os gols saem de quem ele pôs em campo
        List<LigaKings.AtletaKings> el = escalados(t);
        if (el == null) el = t.elenco;
        return el.get(rng.peso(pesosAutor(el)));
    }

    /**
     * Peso de cada atleta para marcar: setor × e^(AUTOR_K × (força − 60)). No 7x7 todo mundo ataca:
     * AUTOR_K era 0,04 (agora 0,01; defesa 0,4 → 0,55 e meio 0,7 → 0,8) e, com o pênalti do presidente na conta, os presidentes da IOS (75) faziam 26%
     * dos gols e eram artilheiros em 60 de 60 competições. Balanço 2026-11, rodada 2.
     */
    static double[] pesosAutor(List<LigaKings.AtletaKings> el) {
        double[] p = new double[el.size()];
        for (int i = 0; i < p.length; i++) {
            LigaKings.AtletaKings a = el.get(i);
            double pos = switch (a.posicao) {
                case GOL -> 0.05;
                case ZAG, LD, LE -> 0.55;
                case VOL, MC -> 0.8;
                default -> 1.0;
            };
            p[i] = pos * Math.exp(AUTOR_K * (a.forca - 60));
        }
        return p;
    }

    static final double AUTOR_K = 0.01;

    /** Capitão do time: o escolhido pelo técnico humano (se estiver no elenco), senão o presidente-jogador, senão o mais forte. */
    static LigaKings.AtletaKings capitaoDe(LigaKings.TimeKings t) {
        if (t.capitao != null) for (LigaKings.AtletaKings a : t.elenco) if (t.capitao.equals(a.chave())) return a;
        for (LigaKings.AtletaKings a : t.elenco) if (a.presidente) return a;
        return melhor(t);
    }
}
