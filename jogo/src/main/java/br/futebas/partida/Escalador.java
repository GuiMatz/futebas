package br.futebas.partida;

import br.futebas.Balanceamento;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Posicao;
import br.futebas.mundo.Tatica;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Monta o time de uma partida: escalação manual (humano) ou automática (melhor encaixe). */
public final class Escalador {
    private Escalador() {}

    /** Força do dia: moral (−5% a +3%) e energia (até −10% sem energia nenhuma). */
    public static double forcaDoDia(Jogador j) {
        double moral = 1 + Balanceamento.MORAL_PESO * (j.moral - Balanceamento.MORAL_NEUTRA) / 100.0;
        double energia = 1 - Balanceamento.ENERGIA_PESO * (100 - Math.max(0, Math.min(100, j.energia))) / 100.0;
        return j.forca * moral * energia;
    }

    /**
     * Garante o jogador como titular (jogo de despedida): entra na vaga da posição dele (ou no
     * lugar do pior de linha), se ainda não estiver entre os 11.
     */
    public static void forcarTitular(TimePartida tp, Jogador j) {
        for (Escalado e : tp.titulares) if (e.id == j.id) return;
        tp.reservas.removeIf(e -> e.id == j.id);
        int alvo = -1;
        for (int i = 0; i < tp.titulares.size(); i++) if (tp.titulares.get(i).vaga == j.posicao) alvo = i;
        if (alvo < 0) {
            double pior = Double.MAX_VALUE;
            for (int i = 0; i < tp.titulares.size(); i++) {
                Escalado e = tp.titulares.get(i);
                if (e.vaga == Posicao.GOL) continue;
                if (e.rende() < pior) {
                    pior = e.rende();
                    alvo = i;
                }
            }
        }
        if (alvo < 0) return;
        tp.titulares.set(alvo, escalado(j, tp.titulares.get(alvo).vaga));
    }

    /** O jogador na partida, com a força do dia e a energia com que começa. */
    static Escalado de(Jogador j, Posicao vaga) {
        Escalado e = new Escalado(j.id, j.nomeExibicao(), j.posicao, vaga, forcaDoDia(j));
        e.energia = j.energia;
        e.anoNasc = j.anoNasc;
        return e;
    }

    /**
     * Regra do jogo: jogador do clube do TÉCNICO vendido ou emprestado fora da janela (negócio
     * "aguardando_janela") continua no clube até a janela abrir, mas não joga mais por ele: some da
     * escalação, do banco, da troca ao vivo e do pré-jogo. (Clubes da IA não mudam.)
     */
    public static Set<Integer> deSaida(Mundo m, Clube c) {
        Set<Integer> s = new HashSet<>();
        if (m == null || c == null || !c.humano || m.propostas == null) return s;
        for (br.futebas.ia.PropostaJogador p : m.propostas) {
            if ("aguardando_janela".equals(p.estado) && c.id.equals(p.vendedorId)) s.add(p.jogadorId);
        }
        return s;
    }

    /**
     * Tira da escalação salva do clube (titulares, reservas, capitão e cobradores) quem não pode mais
     * jogar por ele: vendido/emprestado esperando a janela ou que já saiu do clube. A vaga do titular
     * fica vazia (-1) para o técnico (ou o auxiliar) completar. Devolve o que foi tirado, em texto.
     */
    public static List<String> limparEscalacao(Mundo m, Clube c) {
        List<String> tirados = new ArrayList<>();
        if (m == null || c == null || c.tatica == null) return tirados;
        Tatica t = c.tatica;
        Set<Integer> saindo = deSaida(m, c);
        java.util.function.IntPredicate saiu = id -> {
            Jogador j = m.jogador(id);
            return j == null || !c.id.equals(j.clubeId) || saindo.contains(id);
        };
        if (t.titulares != null) {
            for (int i = 0; i < t.titulares.size(); i++) {
                Integer id = t.titulares.get(i);
                if (id == null || id < 0 || !saiu.test(id)) continue;
                Jogador j = m.jogador(id);
                String nome = j == null ? "Jogador" : j.nomeExibicao();
                tirados.add(nome + (saindo.contains(id) ? ": negociado, aguarda a janela para sair" : ": não está mais no elenco"));
                t.titulares.set(i, -1);
            }
        }
        if (t.reservas != null) t.reservas.removeIf(id -> id == null || saiu.test(id));
        if (t.capitao != null && saiu.test(t.capitao)) t.capitao = null;
        if (t.cobradorPenalti != null && saiu.test(t.cobradorPenalti)) t.cobradorPenalti = null;
        if (t.cobradorFalta != null && saiu.test(t.cobradorFalta)) t.cobradorFalta = null;
        return tirados;
    }

    /**
     * Jogadores do clube disponíveis para jogar (profissionais e base com 16+). No
     * clube de um técnico, a base só entra com a caixa "Sub-20" marcada, e só o time Sub-20.
     */
    public static List<Jogador> disponiveis(Mundo m, Clube c) {
        List<Jogador> l = new ArrayList<>();
        Set<Integer> saindo = deSaida(m, c);
        for (int id : c.elenco) {
            Jogador j = m.jogador(id);
            if (j != null && j.disponivel() && !saindo.contains(id)) l.add(j);
        }
        for (int id : c.base) {
            Jogador j = m.jogador(id);
            if (j == null || !j.disponivel() || j.idade(m.ano) < 16) continue;
            if (c.humano && !(c.tatica.usarSub20 && j.noSub20)) continue;
            l.add(j);
        }
        return l;
    }

    /** Escalação do auxiliar: ignora a escalação manual (tática e formação do clube continuam). */
    public static TimePartida montarAutomatico(Mundo m, Clube c, int substituicoes) {
        return montarAutomatico(m, c, substituicoes, 0);
    }

    /** Como {@link #montarAutomatico(Mundo, Clube, int)}, com o número de reservas no banco (0 = trocas + 4). */
    public static TimePartida montarAutomatico(Mundo m, Clube c, int substituicoes, int banco) {
        Tatica t = c.tatica;
        List<Integer> tit = t.titulares, res = t.reservas;
        t.titulares = new ArrayList<>();
        t.reservas = new ArrayList<>();
        try {
            return montar(m, c, substituicoes, banco);
        } finally {
            t.titulares = tit;
            t.reservas = res;
        }
    }

    public static TimePartida montar(Mundo m, Clube c, int substituicoes) {
        return montar(m, c, substituicoes, 0);
    }

    /** Monta o time do jogo; {@code banco} = reservas relacionados (0 = trocas + 4, o antigo). */
    public static TimePartida montar(Mundo m, Clube c, int substituicoes, int banco) {
        List<Jogador> disp = disponiveis(m, c);
        Tatica t = c.tatica;
        Posicao[] vagas = Formacoes.vagas(t.formacao);
        List<Escalado> titulares = null;
        Set<Integer> usados = new HashSet<>();
        if (c.humano && t.titulares.size() == 11) {
            // a escalação do técnico; vaga vazia ou com quem não pode jogar (lesão, suspensão, vendido): o auxiliar completa só ela
            titulares = new ArrayList<>();
            List<Integer> vazias = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                Integer id = t.titulares.get(i);
                Jogador j = id == null || id < 0 ? null : m.jogador(id);
                if (j == null || !disp.contains(j) || usados.contains(j.id)) {
                    titulares.add(null);
                    vazias.add(i);
                    continue;
                }
                usados.add(j.id);
                titulares.add(escalado(j, vagas[i]));
            }
            if (vazias.size() == 11) {
                titulares = null;
            } else if (!vazias.isEmpty()) {
                Posicao[] vv = new Posicao[vazias.size()];
                for (int k = 0; k < vv.length; k++) vv[k] = vagas[vazias.get(k)];
                try {
                    List<Escalado> f = automatico(disp, vv, usados);
                    for (int k = 0; k < vv.length; k++) titulares.set(vazias.get(k), f.get(k));
                } catch (IllegalStateException e) {
                    titulares = null;
                }
            }
        }
        if (titulares == null) {
            usados.clear();
            titulares = automatico(disp, vagas, usados);
        }
        List<Escalado> reservas = new ArrayList<>();
        int tamBanco = banco > 0 ? banco : Math.max(5, substituicoes + 4);
        if (c.humano && !t.reservas.isEmpty()) {
            for (int id : t.reservas) {
                Jogador j = m.jogador(id);
                if (j != null && disp.contains(j) && !usados.contains(id) && reservas.size() < tamBanco) {
                    usados.add(id);
                    reservas.add(de(j, j.posicao));
                }
            }
        }
        List<Jogador> resto = new ArrayList<>();
        for (Jogador j : disp) if (!usados.contains(j.id)) resto.add(j);
        resto.sort(Comparator.comparingDouble((Jogador j) -> -j.forca).thenComparingInt(j -> j.id));
        boolean temGoleiro = false;
        for (Escalado e : reservas) if (e.natural == Posicao.GOL) temGoleiro = true;
        for (Jogador j : resto) {
            if (reservas.size() >= tamBanco) break;
            if (j.posicao == Posicao.GOL) {
                if (temGoleiro) continue;
                temGoleiro = true;
            }
            reservas.add(de(j, j.posicao));
        }
        TimePartida tp = new TimePartida(c.id, c.nome, titulares, reservas);
        tp.estilo = t.estilo;
        tp.marcacao = t.marcacao();
        tp.concentracao = t.concentracao();
        tp.capitao = t.capitao;
        tp.cobradorPenalti = t.cobradorPenalti;
        tp.cobradorFalta = t.cobradorFalta;
        return tp;
    }

    /**
     * Troca de formação no meio do jogo: para cada jogador (pela posição natural, na ordem dada), o
     * índice da vaga nova em {@code vagas}. Melhor encaixe total, guloso como o {@link #distribuir}:
     * os pares jogador × vaga do maior encaixe para o menor (o goleiro fica no gol). Mesmo número de
     * jogadores e vagas.
     */
    public static int[] remapear(Posicao[] naturais, Posicao[] vagas) {
        int n = Math.min(naturais.length, vagas.length);
        int[] r = new int[naturais.length];
        java.util.Arrays.fill(r, -1);
        List<int[]> pares = new ArrayList<>();
        for (int i = 0; i < n; i++) for (int k = 0; k < vagas.length; k++) pares.add(new int[]{i, k});
        java.util.function.ToDoubleFunction<int[]> valor = p -> {
            Posicao nat = naturais[p[0]], v = vagas[p[1]];
            if (nat == null) return 0;
            if ((nat == Posicao.GOL) != (v == Posicao.GOL)) return -1; // goleiro no gol, linha na linha
            return nat == v ? 1.0 : nat.encaixe(v);
        };
        pares.sort(Comparator.comparingDouble((int[] p) -> -valor.applyAsDouble(p)).thenComparingInt(p -> p[0]).thenComparingInt(p -> p[1]));
        boolean[] usada = new boolean[vagas.length];
        for (int[] p : pares) {
            if (r[p[0]] >= 0 || usada[p[1]]) continue;
            r[p[0]] = p[1];
            usada[p[1]] = true;
        }
        for (int i = 0; i < r.length; i++) {
            if (r[i] >= 0) continue;
            for (int k = 0; k < vagas.length; k++) if (!usada[k]) {
                r[i] = k;
                usada[k] = true;
                break;
            }
        }
        return r;
    }

    /**
     * Distribui jogadores já escolhidos pelas vagas da formação (melhor encaixe; só para desenhar o
     * campo, ex.: o sub-20, que joga com os 11 mais fortes). Devolve 11 itens; null = vaga vazia.
     */
    public static List<Escalado> distribuir(List<Jogador> jogadores, String formacao) {
        Posicao[] vagas = Formacoes.vagas(formacao == null ? "4-4-2" : formacao);
        List<Escalado> r = new ArrayList<>();
        if (jogadores.size() >= vagas.length) return automatico(jogadores, vagas, new HashSet<>());
        for (int i = 0; i < vagas.length; i++) r.add(null);
        // menos jogadores que vagas: cada um (do mais forte) na vaga livre em que rende mais
        List<Jogador> l = new ArrayList<>(jogadores);
        l.sort(Comparator.comparingDouble((Jogador j) -> -j.forca).thenComparingInt(j -> j.id));
        for (Jogador j : l) {
            int melhor = -1;
            double mv = -1;
            for (int i = 0; i < vagas.length; i++) {
                if (r.get(i) != null) continue;
                double v = valorEscolha(j, vagas[i]);
                if (v > mv) {
                    mv = v;
                    melhor = i;
                }
            }
            if (melhor >= 0) r.set(melhor, escalado(j, vagas[melhor]));
        }
        return r;
    }

    /**
     * Rendimento (0 a 1) do jogador na vaga: posição principal 1,0; posições secundárias do
     * banco 0,95; posição sem fonte (MC provisório) = meio-campista flexível com pequena
     * penalidade; senão, a tabela de encaixe fora de posição.
     */
    public static double encaixe(Jogador j, Posicao vaga) {
        if (j.posicao == vaga && !j.posicaoIncerta) return 1.0;
        if (vaga == Posicao.GOL || j.posicao == Posicao.GOL) return j.posicao == vaga ? 1.0 : 0.0;
        if (j.posicaoIncerta) {
            return switch (vaga) {
                case VOL, MC, MEI -> 0.93;
                default -> Posicao.MC.encaixe(vaga) * 0.93;
            };
        }
        double f = j.posicao.encaixe(vaga);
        if (f >= 1.0) return 1.0; // ex.: ponta na outra ponta (regra do jogo)
        if (j.posicoesSec.contains(vaga)) return 0.95;
        for (Posicao s : j.posicoesSec) f = Math.max(f, s.encaixe(vaga) * 0.9);
        return f;
    }

    /** Valor de escolha: quem joga fora de posição é penalizado de novo, para preferir os naturais. */
    static double valorEscolha(Jogador j, Posicao vaga) {
        double f = encaixe(j, vaga);
        if (f <= 0) {
            if (vaga == Posicao.GOL || j.posicao == Posicao.GOL) return 0.05 * forcaDoDia(j); // só em último caso
            f = 0.6;
        }
        double esc = f >= 0.999 ? 1.0 : f * f;
        return forcaDoDia(j) * esc;
    }

    /**
     * Escalação automática: atribuição ótima (algoritmo húngaro) dos jogadores às 11 vagas,
     * maximizando a soma de {@link #valorEscolha}. Assim um lateral só vira zagueiro quando
     * isso realmente compensa, e não porque a vaga de zagueiro foi preenchida antes.
     */
    static List<Escalado> automatico(List<Jogador> disp, Posicao[] vagas, Set<Integer> usados) {
        List<Jogador> cand = new ArrayList<>();
        for (Jogador j : disp) if (!usados.contains(j.id)) cand.add(j);
        if (cand.size() < vagas.length) throw new IllegalStateException("Elenco sem " + vagas.length + " jogadores disponíveis.");
        cand.sort(Comparator.comparingInt((Jogador j) -> j.id));
        int n = vagas.length, mm = cand.size();
        double[][] custo = new double[n + 1][mm + 1];
        for (int i = 0; i < n; i++) for (int k = 0; k < mm; k++) custo[i + 1][k + 1] = 1000 - valorEscolha(cand.get(k), vagas[i]);
        int[] col = hungaro(custo, n, mm);
        List<Escalado> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Jogador j = cand.get(col[i]);
            usados.add(j.id);
            l.add(escalado(j, vagas[i]));
        }
        return l;
    }

    static Escalado escalado(Jogador j, Posicao vaga) {
        Escalado e = de(j, vaga);
        e.encaixe = encaixe(j, vaga);
        return e;
    }

    /** Húngaro (linhas = vagas, colunas = jogadores, n <= m). Devolve a coluna (0-based) de cada linha. */
    private static int[] hungaro(double[][] a, int n, int mm) {
        double[] u = new double[n + 1], v = new double[mm + 1];
        int[] p = new int[mm + 1], way = new int[mm + 1];
        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[mm + 1];
            java.util.Arrays.fill(minv, Double.MAX_VALUE);
            boolean[] used = new boolean[mm + 1];
            do {
                used[j0] = true;
                int i0 = p[j0], j1 = 0;
                double delta = Double.MAX_VALUE;
                for (int j = 1; j <= mm; j++) {
                    if (used[j]) continue;
                    double cur = a[i0][j] - u[i0] - v[j];
                    if (cur < minv[j]) {
                        minv[j] = cur;
                        way[j] = j0;
                    }
                    if (minv[j] < delta) {
                        delta = minv[j];
                        j1 = j;
                    }
                }
                for (int j = 0; j <= mm; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }
        int[] res = new int[n];
        for (int j = 1; j <= mm; j++) if (p[j] != 0) res[p[j] - 1] = j - 1;
        return res;
    }

    /**
     * Time a partir de uma lista de jogadores (seleções): os 11 melhores encaixes e o banco.
     * Com menos de 11 disponíveis, completa com vagas virtuais da força indicada.
     */
    public static TimePartida deLista(String id, String nome, List<Jogador> lista, String formacao, int substituicoes,
                                      double nivelVirtual, int lado, br.futebas.mundo.Rng rng) {
        List<Jogador> disp = new ArrayList<>();
        for (Jogador j : lista) if (j != null && j.lesaoSemanas <= 0 && j.suspensao <= 0) disp.add(j);
        Posicao[] vagas = Formacoes.vagas(formacao == null ? "4-3-3" : formacao);
        if (disp.size() < 11) {
            TimePartida v = virtual(id, nome, lado, nivelVirtual, rng);
            // usa os reais que houver nas vagas da mesma posição
            int k = 0;
            for (Jogador j : disp) {
                if (k >= 11) break;
                v.titulares.set(k, escalado(j, v.titulares.get(k).vaga));
                k++;
            }
            return v;
        }
        Set<Integer> usados = new HashSet<>();
        List<Escalado> titulares = automatico(disp, vagas, usados);
        List<Escalado> reservas = new ArrayList<>();
        List<Jogador> resto = new ArrayList<>();
        for (Jogador j : disp) if (!usados.contains(j.id)) resto.add(j);
        resto.sort(Comparator.comparingDouble((Jogador j) -> -j.forca).thenComparingInt(j -> j.id));
        for (Jogador j : resto) {
            if (reservas.size() >= Math.max(5, substituicoes + 4)) break;
            reservas.add(de(j, j.posicao));
        }
        return new TimePartida(id, nome, titulares, reservas);
    }

    /** Time de um clube sem elenco (só força, sem nomes): vagas rotuladas pela posição. */
    public static TimePartida virtual(Clube c, int lado, double nivel, br.futebas.mundo.Rng rng) {
        return virtual(c.id, c.nome, lado, nivel, rng);
    }

    public static TimePartida virtual(String id, String nome, int lado, double nivel, br.futebas.mundo.Rng rng) {
        Posicao[] vagas = Formacoes.vagas("4-4-2");
        List<Escalado> t = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            t.add(new Escalado(-(1 + lado * 40 + i), vagas[i].nome + " (" + nome + ")", vagas[i], vagas[i], nivel + rng.gauss(0, 2.5)));
        }
        List<Escalado> r = new ArrayList<>();
        Posicao[] banco = {Posicao.GOL, Posicao.ZAG, Posicao.MC, Posicao.PE, Posicao.CA};
        for (int i = 0; i < banco.length; i++) {
            r.add(new Escalado(-(20 + lado * 40 + i), "Reserva " + banco[i].nome.toLowerCase() + " (" + nome + ")", banco[i], banco[i], nivel - 4));
        }
        return new TimePartida(id, nome, t, r);
    }

    /** Força média dos 11 que jogariam (para IA e telas). */
    public static double forcaTime(Mundo m, Clube c) {
        if (c.virtual) return 45 + 0.42 * c.reputacao;
        List<Jogador> disp = disponiveis(m, c);
        if (disp.size() < 11) {
            // conta os reforços de emergência que viriam (nível do clube)
            double s = 0;
            List<Jogador> top = new ArrayList<>(disp);
            for (Jogador j : top) s += j.forca;
            s += (11 - top.size()) * (c.nivel() - 4);
            return s / 11;
        }
        try {
            List<Escalado> onze = automatico(disp, Formacoes.vagas(c.tatica.formacao), new HashSet<>());
            double s = 0;
            for (Escalado e : onze) s += e.rende();
            return s / 11;
        } catch (IllegalStateException e) {
            return c.nivel();
        }
    }
}
