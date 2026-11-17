package br.futebas.ia;

import br.futebas.Balanceamento;
import br.futebas.financas.Financas;
import br.futebas.mundo.Tecnico;
import br.futebas.mundo.Calendario;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Contrato;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Leilao;
import br.futebas.mundo.Mensagem;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Origem;
import br.futebas.mundo.Pais;
import br.futebas.mundo.Posicao;
import br.futebas.mundo.Rng;
import br.futebas.mundo.StatusJogador;
import br.futebas.mundo.TipoContrato;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IA do mercado internacional: compras e vendas entre clubes de qualquer país (valores e
 * orçamentos), empréstimos, renovações, leilões e jogadores livres. Sem permissão de trabalho e
 * sem limite de estrangeiros (por enquanto). Cada janela de transferência segue o calendário do
 * país do comprador. Ofertas indexadas por posição e ordenadas por força: nenhuma busca é O(n²).
 */
public final class Mercado {
    private final Mundo m;
    private final Rng rng;

    public Mercado(Mundo m) {
        this.m = m;
        this.rng = m.rng;
    }

    static final Map<Posicao, Integer> MINIMO = new EnumMap<>(Posicao.class);

    static {
        MINIMO.put(Posicao.GOL, 3);
        MINIMO.put(Posicao.ZAG, 4);
        MINIMO.put(Posicao.LD, 2);
        MINIMO.put(Posicao.LE, 2);
        MINIMO.put(Posicao.VOL, 2);
        MINIMO.put(Posicao.MC, 3);
        MINIMO.put(Posicao.MEI, 2);
        MINIMO.put(Posicao.PE, 2);
        MINIMO.put(Posicao.PD, 2);
        MINIMO.put(Posicao.CA, 3);
    }

    /** Janela aberta para o calendário na semana do ano? */
    public static boolean janelaAberta(String calendario, int semana) {
        int[][] j = "europeu".equals(calendario) ? Balanceamento.JANELAS_TRANSFERENCIA_EUROPEU : Balanceamento.JANELAS_TRANSFERENCIA_ANO;
        for (int[] x : j) if (semana >= x[0] && semana <= x[1]) return true;
        return false;
    }

    /** Semana absoluta da próxima abertura de janela do calendário depois de {@code sa} (fora da janela). */
    public static int proximaAbertura(String calendario, int sa) {
        for (int s = sa + 1; s <= sa + 60; s++) if (janelaAberta(calendario, br.futebas.mundo.Calendario.semana(s))) return s;
        return sa + 1;
    }

    private static boolean primeiraSemana(String calendario, int semana) {
        int[][] j = "europeu".equals(calendario) ? Balanceamento.JANELAS_TRANSFERENCIA_EUROPEU : Balanceamento.JANELAS_TRANSFERENCIA_ANO;
        for (int[] x : j) if (semana == x[0]) return true;
        return false;
    }

    // =================================================================== semana
    private Set<Integer> excedentes = new HashSet<>();

    public void semana() {
        leiloes();
        boolean ano = janelaAberta("ano", m.semana), eur = janelaAberta("europeu", m.semana);
        if (!ano && !eur) {
            emergencia();
            return;
        }
        marcarExcedentes();
        Map<Posicao, List<Jogador>> oferta = new EnumMap<>(Posicao.class);
        Map<Posicao, List<Jogador>> estrelas = new EnumMap<>(Posicao.class);
        montarOferta(oferta, estrelas);
        Camadas kOferta = indexar(oferta), kEstrelas = indexar(estrelas);
        Comparator<Jogador> ordemForca = Comparator.comparingDouble((Jogador j) -> -j.forca).thenComparingInt(j -> j.id);
        for (Posicao p : Posicao.values()) {
            List<Jogador> v = kOferta.vitrine.computeIfAbsent(p, x -> new ArrayList<>());
            v.addAll(jovensSul.get(p));
            v.sort(ordemForca);
        }
        List<Clube> ordem = new ArrayList<>();
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe || c.humano) continue;
            if (!janelaAberta(c.calendario, m.semana)) continue;
            ordem.add(c);
        }
        rng.embaralhar(ordem);
        for (Clube c : ordem) {
            if (primeiraSemana(c.calendario, m.semana)) emprestarJovens(c);
            if (c.elenco.size() < Balanceamento.ELENCO_MINIMO_IA) {
                for (int k = 0; k < 3 && c.elenco.size() < Balanceamento.ELENCO_ALVO; k++) tentarContratar(c, oferta, estrelas, kOferta, kEstrelas);
            } else if (rng.chance(rico(c) ? Balanceamento.MERCADO_CHANCE_RICO : Balanceamento.MERCADO_CHANCE_CONTRATAR)) {
                tentarContratar(c, oferta, estrelas, kOferta, kEstrelas);
            }
            enxugar(c);
            if (c.dinheiro < 0) colocarAVenda(c);
        }
        emergencia();
    }

    /** Fora da janela: clube com elenco curto contrata jogadores livres (como no Brasfoot). */
    private void emergencia() {
        List<Clube> curtos = new ArrayList<>();
        for (Clube c : m.clubes.values()) {
            if (c.virtual || !c.existe || c.humano || c.elenco.size() >= Balanceamento.ELENCO_EMERGENCIA) continue;
            curtos.add(c);
        }
        if (curtos.isEmpty()) return;
        Map<Posicao, List<Jogador>> livres = new EnumMap<>(Posicao.class);
        for (Posicao p : Posicao.values()) livres.put(p, new ArrayList<>());
        for (Jogador j : m.jogadores) {
            if (j.status != StatusJogador.LIVRE || j.amador() || j.kingsTimeId != null) continue; // quem está na Kings não volta a clube
            int idade = j.idade(m.ano);
            if (idade >= 17 && idade <= 36) livres.get(j.posicao).add(j);
        }
        Comparator<Jogador> ordem = Comparator.comparingDouble((Jogador j) -> -j.forca).thenComparingInt(j -> j.id);
        for (List<Jogador> l : livres.values()) l.sort(ordem);
        Map<Posicao, List<Jogador>> nada = new EnumMap<>(Posicao.class);
        for (Posicao p : Posicao.values()) nada.put(p, new ArrayList<>());
        Camadas kLivres = indexar(livres), kNada = indexar(nada);
        for (Clube c : curtos) {
            naEmergencia = true;
            try {
                for (int k = 0; k < 4 && c.elenco.size() < Balanceamento.ELENCO_EMERGENCIA; k++) tentarContratar(c, livres, nada, kLivres, kNada);
            } finally {
                naEmergencia = false;
            }
            // ainda curto: o melhor livre da posição mais carente, sem exigência de nível
            for (int k = 0; k < 4 && c.elenco.size() < Balanceamento.ELENCO_EMERGENCIA; k++) {
                Posicao p = necessidade(c);
                Posicao pos = p == null ? Posicao.MC : p;
                // em camadas (16/11/2026): o próprio país, a confederação e o mundo
                // sem ninguém em casa nem na confederação, o clube sobe a base (abaixo) em vez de importar qualquer um;
                // o mundo só entra com o elenco crítico e sem base (Balanceamento.ELENCO_CRITICO_MUNDO)
                boolean critico = c.elenco.size() < Balanceamento.ELENCO_CRITICO_MUNDO && c.base.isEmpty();
                List<List<Jogador>> camadas = critico ? camadas(c, kLivres, livres, pos) : locais(c, kLivres, pos);
                Jogador escolhido = null;
                for (List<Jogador> l : camadas) {
                    for (Jogador j : l) if (j.status == StatusJogador.LIVRE && j.forca <= c.nivel() + 5 && topaFluxo(j, origemDe(j), c)) { escolhido = j; break; }
                    if (escolhido != null) break;
                }
                if (escolhido == null) for (List<Jogador> l : camadas) {
                    for (Jogador j : l) if (j.status == StatusJogador.LIVRE && j.forca <= c.nivel() + 5) { escolhido = j; break; }
                    if (escolhido != null) break;
                }
                if (escolhido == null && critico) {
                    for (List<Jogador> outra : livres.values()) {
                        for (Jogador j : outra) if (j.status == StatusJogador.LIVRE) { escolhido = j; pos = j.posicao; break; }
                        if (escolhido != null) break;
                    }
                }
                if (escolhido == null) break;
                String pa = paisOrigem(escolhido), co = confederacao(pa);
                transferir(escolhido, c, 0);
                tirar(livres, kLivres, escolhido, escolhido.posicao, pa, co);
            }
            // sem livres: sobe juniores da base
            while (c.elenco.size() < Balanceamento.ELENCO_EMERGENCIA && !c.base.isEmpty()) {
                Jogador j = null;
                for (int id : c.base) if (j == null || m.jogador(id).forca > j.forca) j = m.jogador(id);
                c.base.remove(Integer.valueOf(j.id));
                c.elenco.add(j.id);
                j.status = StatusJogador.PROFISSIONAL;
                j.contrato = Contrato.profissional(Financas.salarioPedido(j, c, m.ano), fimDaTemporada(c) + 52, Financas.multaRescisoria(j, c, m.ano));
            }
        }
    }

    /** Fora dos 22 melhores de um elenco grande: o clube aceita negociar. */
    private void marcarExcedentes() {
        excedentes = new HashSet<>();
        marcarListaDeEmprestimos();
        for (Clube c : m.clubes.values()) {
            if (c.humano || c.elenco.size() <= 24) continue;
            List<Jogador> l = new ArrayList<>();
            for (int id : c.elenco) l.add(m.jogador(id));
            l.sort(Comparator.comparingDouble((Jogador j) -> -j.forca).thenComparingInt(j -> j.id));
            for (int i = 22; i < l.size(); i++) {
                Jogador j = l.get(i);
                if (j.contrato != null && j.contrato.tipo == TipoContrato.PROFISSIONAL) excedentes.add(j.id);
            }
        }
    }

    /**
     * Lista de empréstimos dos clubes da IA (regra do jogo: a IA também põe jogadores para empréstimo).
     * Fora dos 16 mais fortes: jovens até 23 anos; fora dos 20: qualquer um até 30. Quem está entre os
     * 16 sai da lista. Sem sorteio (não mexe na sequência do mundo).
     */
    private void marcarListaDeEmprestimos() {
        for (Clube c : m.clubes.values()) {
            if (c.humano || c.virtual || !c.existe || c.elenco.size() < 18) continue;
            List<Jogador> l = new ArrayList<>();
            for (int id : c.elenco) {
                Jogador j = m.jogador(id);
                if (j != null) l.add(j);
            }
            l.sort(Comparator.comparingDouble((Jogador j) -> -j.forca).thenComparingInt(j -> j.id));
            for (int i = 0; i < l.size(); i++) {
                Jogador j = l.get(i);
                boolean pode = !j.amador() && j.contrato != null && j.contrato.tipo == TipoContrato.PROFISSIONAL && j.kingsTimeId == null;
                int idade = j.idade(m.ano);
                j.aEmprestimo = pode && (i >= 16 && idade <= 23 || i >= 20 && idade <= 30);
            }
        }
    }

    private boolean excedente(Jogador j) {
        return excedentes.contains(j.id);
    }

    /** País de onde o jogador sai: o clube atual, o último clube (livre) ou, sem nenhum, a nacionalidade. */
    String paisOrigem(Jogador j) {
        Clube c = origemDe(j);
        return c != null ? c.pais : j.pais;
    }

    String confederacao(String pais) {
        Pais p = pais == null ? null : m.pais(pais);
        return p == null ? null : p.confederacao;
    }

    /**
     * Mercado em camadas (regra do jogo 16/11/2026, saúde do mundo): a mesma lista (por posição, em
     * ordem de força) separada também por país e por confederação de origem. A IA procura primeiro no
     * próprio país, depois na confederação e só então no mundo ({@link #camadas}).
     */
    static final class Camadas {
        final Map<Posicao, Map<String, List<Jogador>>> pais = new EnumMap<>(Posicao.class), conf = new EnumMap<>(Posicao.class);
        /** Vitrine: jovens (até VITRINE_IDADE) saindo da América do Sul, que os clubes europeus olham logo depois de casa. */
        final Map<Posicao, List<Jogador>> vitrine = new EnumMap<>(Posicao.class);
    }

    private Camadas indexar(Map<Posicao, List<Jogador>> lista) {
        Camadas k = new Camadas();
        for (Map.Entry<Posicao, List<Jogador>> e : lista.entrySet()) {
            Map<String, List<Jogador>> pp = new HashMap<>(), cc = new HashMap<>();
            List<Jogador> vit = new ArrayList<>();
            for (Jogador j : e.getValue()) { // a lista já está em ordem de força: as sublistas também ficam
                String pa = paisOrigem(j), co = confederacao(pa);
                if (pa != null) pp.computeIfAbsent(pa, x -> new ArrayList<>()).add(j);
                if (co != null) cc.computeIfAbsent(co, x -> new ArrayList<>()).add(j);
                if ("CONMEBOL".equals(co) && j.idade(m.ano) <= Balanceamento.VITRINE_IDADE) vit.add(j);
            }
            k.vitrine.put(e.getKey(), vit);
            k.pais.put(e.getKey(), pp);
            k.conf.put(e.getKey(), cc);
        }
        return k;
    }

    /** As listas em que o clube procura, na ordem: o próprio país, a confederação, o mundo. */
    private List<List<Jogador>> camadas(Clube c, Camadas k, Map<Posicao, List<Jogador>> mundo, Posicao alvo) {
        return camadas(c, k, mundo, alvo, false);
    }

    /**
     * {@code exterior}: nesta tentativa o clube olha primeiro o mercado inteiro (a rede de observação dele
     * achou alguém de fora; {@link Balanceamento#MERCADO_EXTERIOR}) e só depois o próprio país.
     */
    private List<List<Jogador>> camadas(Clube c, Camadas k, Map<Posicao, List<Jogador>> mundo, Posicao alvo, boolean exterior) {
        List<List<Jogador>> l = new ArrayList<>(3);
        Map<String, List<Jogador>> pp = k.pais.get(alvo), cc = k.conf.get(alvo);
        if (exterior) {
            l.add(mundo.get(alvo));
            if (pp != null && pp.containsKey(c.pais)) l.add(pp.get(c.pais));
            return l;
        }
        if (pp != null && pp.containsKey(c.pais)) l.add(pp.get(c.pais));
        if ("UEFA".equals(c.confederacao) && k.vitrine.containsKey(alvo) && rng.chance(Balanceamento.VITRINE_CHANCE)) l.add(k.vitrine.get(alvo));
        // outro país da confederação: só numa parte das tentativas (o resto fica em casa)
        if (cc != null && c.confederacao != null && cc.containsKey(c.confederacao) && rng.chance(Balanceamento.MERCADO_CHANCE_CONFEDERACAO))
            l.add(cc.get(c.confederacao));
        // fora da própria confederação: os europeus olham o mundo todo (é a vitrine da América do Sul → Europa);
        // os outros só às vezes (rede de observação menor)
        boolean mundoTodo = "UEFA".equals(c.confederacao) || rng.chance(Balanceamento.MERCADO_CHANCE_MUNDO);
        if (mundoTodo) l.add(mundo.get(alvo));
        return l;
    }

    /** Só o próprio país e a confederação. */
    private List<List<Jogador>> locais(Clube c, Camadas k, Posicao alvo) {
        List<List<Jogador>> l = new ArrayList<>(2);
        Map<String, List<Jogador>> pp = k.pais.get(alvo), cc = k.conf.get(alvo);
        if (pp != null && pp.containsKey(c.pais)) l.add(pp.get(c.pais));
        if (cc != null && c.confederacao != null && cc.containsKey(c.confederacao)) l.add(cc.get(c.confederacao));
        return l;
    }

    /** Reforço de emergência (elenco curto fora da janela): procura sempre a partir de casa. */
    private boolean naEmergencia;

    private static void tirar(Map<Posicao, List<Jogador>> mundo, Camadas k, Jogador j, Posicao alvo, String pais, String conf) {
        mundo.get(alvo).remove(j);
        List<Jogador> v = k.vitrine.get(alvo);
        if (v != null) v.remove(j);
        List<Jogador> l = k.pais.getOrDefault(alvo, Map.of()).get(pais);
        if (l != null) l.remove(j);
        l = k.conf.getOrDefault(alvo, Map.of()).get(conf);
        if (l != null) l.remove(j);
    }

    /**
     * Promessas sul-americanas com contrato (fora da oferta comum): jovens até VITRINE_IDADE de clube da
     * CONMEBOL com potencial ≥ VITRINE_POTENCIAL. Só os clubes europeus as veem (na vitrine), avaliadas
     * também pelo potencial ({@link #tentarContratar}).
     */
    private final Map<Posicao, List<Jogador>> jovensSul = new EnumMap<>(Posicao.class);

    private boolean promessaSul(Jogador j, int idade) {
        if (idade > Balanceamento.VITRINE_IDADE || j.potencial < Balanceamento.VITRINE_POTENCIAL || j.contrato == null
                || j.contrato.tipo != TipoContrato.PROFISSIONAL || j.kingsTimeId != null) return false;
        Clube c = m.clube(j.clubeId);
        return c != null && !c.humano && "CONMEBOL".equals(c.confederacao);
    }

    private void montarOferta(Map<Posicao, List<Jogador>> of, Map<Posicao, List<Jogador>> estrelas) {
        for (Posicao p : Posicao.values()) jovensSul.put(p, new ArrayList<>());
        for (Posicao p : Posicao.values()) {
            of.put(p, new ArrayList<>());
            estrelas.put(p, new ArrayList<>());
        }
        java.util.Set<Integer> fechados = negociacoes == null ? java.util.Set.of() : negociacoes.comprometidos();
        for (Jogador j : m.jogadores) {
            if (j.origem == Origem.COMPLEMENTAR_TESTE || fechados.contains(j.id)) continue;
            int idade = j.idade(m.ano);
            if (j.status == StatusJogador.LIVRE) {
                if (idade >= 17 && idade <= 36) of.get(j.posicao).add(j);
            } else if (j.status == StatusJogador.PROFISSIONAL) {
                if (j.forca < 72 && !j.aVenda && !excedente(j) && (idade > Balanceamento.VITRINE_IDADE || j.potencial < Balanceamento.VITRINE_POTENCIAL))
                    continue; // não entra em nenhuma lista: nem procura o clube
                Clube c = m.clube(j.clubeId);
                if (c != null && c.humano && !j.aVenda) {
                    if (j.forca >= 72) estrelas.get(j.posicao).add(j); // a IA pode fazer proposta ao humano
                    continue;
                }
                if (j.aVenda || excedente(j)) of.get(j.posicao).add(j);
                else if (promessaSul(j, idade)) jovensSul.get(j.posicao).add(j);
                else if (j.forca >= 72 && idade <= 31 && j.contrato != null && j.contrato.tipo == TipoContrato.PROFISSIONAL) estrelas.get(j.posicao).add(j);
            }
        }
        Comparator<Jogador> ordem = Comparator.comparingDouble((Jogador j) -> -j.forca).thenComparingInt(j -> j.id);
        for (List<Jogador> l : of.values()) l.sort(ordem);
        for (List<Jogador> l : estrelas.values()) l.sort(ordem);
        for (List<Jogador> l : jovensSul.values()) l.sort(ordem);
    }

    /** Primeiro índice com força ≤ teto (lista em ordem decrescente de força). */
    private static int inicio(List<Jogador> l, double teto) {
        int lo = 0, hi = l.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (l.get(mid).forca > teto) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private void tentarContratar(Clube c, Map<Posicao, List<Jogador>> oferta, Map<Posicao, List<Jogador>> estrelas, Camadas kOferta, Camadas kEstrelas) {
        Posicao alvo = necessidade(c);
        if (alvo == null) return;
        double piorNaPosicao = Double.MAX_VALUE;
        int naPosicao = 0;
        for (int id : c.elenco) {
            Jogador j = m.jogador(id);
            if (j.posicao == alvo) {
                naPosicao++;
                piorNaPosicao = Math.min(piorNaPosicao, j.forca);
            }
        }
        double nivel = c.nivel();
        double exigencia = naPosicao < MINIMO.get(alvo) || c.elenco.size() < Balanceamento.ELENCO_MINIMO_IA ? nivel - 16
                : Math.max(piorNaPosicao + 3, nivel - 2);
        double teto = c.reputacao >= 70 ? 99 : nivel + 14;
        // caixa acima do teto (CAIXA_TETO × receita): a IA reinveste em reforços (rodada 2 do balanço)
        boolean rico = rico(c);
        long orcamento = Math.max(0, (long) (c.dinheiro * (rico ? Balanceamento.ORCAMENTO_RICO : 0.45)));
        Jogador escolhido = null;
        long preco = 0;
        long folha = folhaMensal(c);
        boolean exterior = !naEmergencia && rng.chance(Balanceamento.MERCADO_EXTERIOR);
        // 1) estrelas de clubes menores (só clubes grandes e com dinheiro)
        if (c.reputacao >= 65 && orcamento > 0) camada:for (List<Jogador> l : camadas(c, kEstrelas, estrelas, alvo, exterior)) {
            int verificados = 0;
            for (int i = inicio(l, teto); i < l.size() && verificados < 60; i++) {
                Jogador j = l.get(i);
                if (j.forca < Math.max(exigencia, nivel + 1)) break;
                if (c.id.equals(j.clubeId)) continue;
                verificados++;
                Clube atual = m.clube(j.clubeId);
                if (atual == null || atual.reputacao + (rico ? 0 : 5) > c.reputacao || atual.elenco.size() <= Balanceamento.ELENCO_MINIMO_IA) continue;
                if (!topaFluxo(j, atual, c) || rivalNaoVende(j, atual, c)) continue;
                // rico (caixa acima do teto) também tira estrela de clube do mesmo porte, pagando mais (rodada 2: o caixa só acumulava)
                long p = Financas.arred(Financas.valorMercado(j, atual, m.ano) * (atual.reputacao + 5 > c.reputacao ? 2.0 : 1.5));
                if (p > orcamento || !cabeNaFolha(c, Financas.salarioPedido(j, c, m.ano), folha)) continue;
                escolhido = j;
                preco = p;
                break camada;
            }
        }
        // 2) livres, à venda e excedentes
        if (escolhido == null) camada:for (List<Jogador> l : camadas(c, kOferta, oferta, alvo, exterior)) {
            int verificados = 0;
            boolean vitrine = l == kOferta.vitrine.get(alvo);
            for (int i = inicio(l, teto); i < l.size() && verificados < 120; i++) {
                Jogador j = l.get(i);
                if (vitrine) {
                    // promessa: o clube europeu compra o que ele vai ser (média de força e potencial)
                    if (j.forca < exigencia - Balanceamento.VITRINE_MARGEM) break;
                    if ((j.forca + j.potencial) / 2 < exigencia) continue;
                    Clube dono = m.clube(j.clubeId);
                    if (dono != null && !j.aVenda && !excedente(j) && dono.elenco.size() <= Balanceamento.ELENCO_MINIMO_IA) continue;
                } else if (j.forca < exigencia) break;
                if (j.clubeId != null && j.clubeId.equals(c.id)) continue;
                if (j.status != StatusJogador.LIVRE && j.status != StatusJogador.PROFISSIONAL) continue;
                if (j.kingsTimeId != null && j.clubeId == null) continue; // jogador só da Kings
                verificados++;
                long p = precoPedido(j);
                if (p > orcamento) continue;
                long sal = Financas.salarioPedido(j, c, m.ano);
                if (!cabeNaFolha(c, sal, folha)) continue;
                if (!aceitaIr(j, c) || rivalNaoVende(j, m.clube(j.clubeId), c)) continue;
                escolhido = j;
                preco = p;
                break camada;
            }
        }
        if (escolhido == null) return;
        Clube vendedor = m.clube(escolhido.clubeId);
        if (vendedor != null && vendedor.humano) {
            proporAoHumano(escolhido, c, preco);
            return;
        }
        String pa = paisOrigem(escolhido), co = confederacao(pa);
        transferir(escolhido, c, preco);
        tirar(oferta, kOferta, escolhido, alvo, pa, co);
        tirar(estrelas, kEstrelas, escolhido, alvo, pa, co);
    }

    /**
     * Teto da folha da IA (finanças abstratas, estáveis): a folha anual não passa de
     * {@link Balanceamento#FOLHA_TETO_IA} da receita de referência do clube. Clube endividado
     * ou no vermelho só contrata salário baixo.
     */
    boolean cabeNaFolha(Clube c, long salarioNovo) {
        return cabeNaFolha(c, salarioNovo, folhaMensal(c));
    }

    long folhaMensal(Clube c) {
        long folha = 0;
        for (int id : c.elenco) {
            Jogador x = m.jogador(id);
            if (x != null && x.contrato != null) folha += x.contrato.salario;
        }
        return folha;
    }

    /** Com a folha já somada (a busca testa dezenas de candidatos para o mesmo clube: soma uma vez só). */
    boolean cabeNaFolha(Clube c, long salarioNovo, long folha) {
        double receita = Financas.receitaReferencia(c, m.ano);
        long limiteBanco = Financas.limiteEmprestimo(c, m.ano);
        if ((c.divida > limiteBanco / 2 || c.dinheiro < 0) && salarioNovo > Financas.baseSalarial(c, m.ano)) return false;
        return (folha + salarioNovo) * 12 <= receita * Balanceamento.FOLHA_TETO_IA;
    }

    /** Caixa acima de CAIXA_TETO × a receita de referência: dinheiro sobrando para reinvestir. */
    boolean rico(Clube c) {
        return c.dinheiro > Balanceamento.CAIXA_TETO * Financas.receitaReferencia(c, m.ano);
    }

    /**
     * Rival não vende (16/11/2026, saúde do mundo): clube da 1ª divisão não vende um dos seus
     * RIVAL_PROTEGIDOS melhores a outro clube da 1ª divisão do mesmo país (salvo se ele está à venda ou
     * sobrando). Com o mercado doméstico em camadas, o mais forte de cada liga (PSG, Bayern, Real) tirava
     * todo ano os melhores dos rivais e era campeão em 14–17 de 20 anos.
     */
    boolean rivalNaoVende(Jogador j, Clube vendedor, Clube comprador) {
        if (vendedor == null || comprador == null || vendedor == comprador || vendedor.divisao != 1 || comprador.divisao != 1
                || !vendedor.pais.equals(comprador.pais) || j.aVenda || excedente(j) || j.status != StatusJogador.PROFISSIONAL) return false;
        int melhores = 0;
        for (int id : vendedor.elenco) {
            Jogador x = m.jogador(id);
            if (x != null && x != j && x.forca > j.forca && ++melhores >= Balanceamento.RIVAL_PROTEGIDOS) return false;
        }
        return true;
    }

    /** Posição mais carente: abaixo do mínimo, ou titular fraco para o nível do clube. */
    Posicao necessidade(Clube c) {
        Map<Posicao, Integer> cont = new EnumMap<>(Posicao.class);
        Map<Posicao, Double> melhor = new EnumMap<>(Posicao.class);
        for (int id : c.elenco) {
            Jogador j = m.jogador(id);
            cont.merge(j.posicao, 1, Integer::sum);
            melhor.merge(j.posicao, j.forca, Math::max);
        }
        Posicao pior = null;
        double falta = 0;
        for (Posicao p : Posicao.values()) {
            int f = MINIMO.get(p) - cont.getOrDefault(p, 0);
            if (f > falta) {
                falta = f;
                pior = p;
            }
        }
        if (pior != null) return pior;
        if (c.elenco.size() >= 30) return null;
        double menor = Double.MAX_VALUE;
        for (Posicao p : Posicao.values()) {
            double v = melhor.getOrDefault(p, 0.0) - c.nivel();
            if (v < menor) {
                menor = v;
                pior = p;
            }
        }
        if (menor < -2) return pior;
        // clube grande com caixa sobrando reforça a posição mais fraca mesmo sem carência (antes o
        // dinheiro só acumulava: folha/receita da Premier League caía de 0,45 para 0,17)
        boolean rico = c.reputacao >= 50 && rico(c); // rodada 2: era só reputação ≥ 65
        return rico ? pior : null;
    }

    public long precoPedido(Jogador j) {
        if (j.status == StatusJogador.LIVRE) return 0;
        long valor = Financas.valorMercado(j, m.clube(j.clubeId), m.ano);
        return j.aVenda && j.precoPedido > 0 ? j.precoPedido : (long) (valor * 1.15);
    }

    /**
     * O jogador topa ir? Para clube muito menor, só se estiver sem espaço; e o fluxo tem de ser
     * plausível ({@link #topaFluxo}): degrau de divisão e saída da Europa.
     */
    boolean aceitaIr(Jogador j, Clube destino) {
        Clube atual = m.clube(j.clubeId);
        if (j.status != StatusJogador.LIVRE && atual != null && destino.reputacao + 25 < atual.reputacao && !excedente(j) && !j.aVenda) return false;
        return topaFluxo(j, origemDe(j), destino);
    }

    // =================================================================== fluxo realista
    /** Só para medição (MedirFluxoTransferencias --antes): desliga o fluxo realista e o degrau dos empréstimos. */
    public static boolean fluxoLivre;
    private Map<String, Integer> ultimaDivisao;
    private int ultimaDivisaoSemana = Integer.MIN_VALUE;

    /** Última divisão nacional do país (0 = sem divisões). */
    int ultimaDivisao(String pais) {
        int sa = m.semanaAbsoluta();
        if (ultimaDivisao == null || ultimaDivisaoSemana != sa) {
            ultimaDivisao = new HashMap<>();
            for (Clube c : m.clubes.values()) if (c.existe && !c.virtual && c.divisao > 0) ultimaDivisao.merge(c.pais, c.divisao, Math::max);
            ultimaDivisaoSemana = sa;
        }
        return ultimaDivisao.getOrDefault(pais, 0);
    }

    /** Divisão do clube; sem divisão nacional conta como a última + 1. */
    int divisaoEfetiva(Clube c) {
        return c.divisao > 0 ? c.divisao : ultimaDivisao(c.pais) + 1;
    }

    /** De onde o jogador vem: o clube atual ou, livre, o último clube. */
    Clube origemDe(Jogador j) {
        Clube c = m.clube(j.clubeId);
        if (c == null && j.ultimoClubeId != null) c = m.clube(j.ultimoClubeId);
        return c;
    }

    /**
     * Quantas divisões o jogador desce: no mesmo país, a diferença de divisão; entre países, a queda
     * de nível (do clube de origem, limitado pela força do jogador + 4) em passos de DEGRAU_NIVEL.
     */
    double degrau(Jogador j, Clube origem, Clube destino) {
        if (origem.pais.equals(destino.pais)) return divisaoEfetiva(destino) - divisaoEfetiva(origem);
        double ref = Math.min(origem.nivel(), j.forca + 4);
        return Math.max(0, ref - destino.nivel()) / Balanceamento.DEGRAU_NIVEL;
    }

    private static boolean jogouNo(Jogador j, Clube c) {
        for (br.futebas.mundo.Passagem p : j.carreira) if (c.id.equals(p.clubeId)) return true;
        return false;
    }

    /**
     * Chance (por temporada, país e divisão de destino) de o jogador aceitar essa mudança, pela regra
     * do jogo: da Europa para fora é raro (5 grandes ligas: raríssimo), salvo veterano
     * ou volta para casa; e ninguém desce muitas divisões de uma vez (ver {@link Balanceamento#DEGRAU_TOLERANCIA}).
     */
    double afinidade(Jogador j, Clube origem, Clube destino) {
        if (origem == null || origem == destino) return 1;
        if (destino.pais.equals(j.pais) && !origem.pais.equals(j.pais) || jogouNo(j, destino)) return 1; // volta para casa
        int idade = j.idade(m.ano);
        double p = 1;
        if ("UEFA".equals(origem.confederacao) && destino.confederacao != null && !"UEFA".equals(destino.confederacao)) {
            boolean forte = origem.divisao == 1 && Balanceamento.LIGAS_FORTES_EUROPA.contains(origem.pais);
            double s = idade >= 33 ? Balanceamento.SAIDA_EUROPA_33 : idade >= 31 ? Balanceamento.SAIDA_EUROPA_31
                    : forte ? Balanceamento.SAIDA_EUROPA_FORTE : Balanceamento.SAIDA_EUROPA;
            Pais pj = m.pais(j.pais);
            if (pj != null && destino.confederacao.equals(pj.confederacao)) s *= Balanceamento.SAIDA_MESMA_CONF;
            if (Balanceamento.MERCADOS_ATRAENTES.contains(destino.pais)) s *= Balanceamento.SAIDA_ATRAENTE;
            p *= Math.min(1, s);
        }
        if (idade >= 35) return p;
        double tol = Balanceamento.DEGRAU_TOLERANCIA;
        if (idade <= 21 || j.forca < origem.nivel() - 8 || excedente(j)) tol++;
        if (idade >= 33) tol++;
        if (!"UEFA".equals(origem.confederacao) && "UEFA".equals(destino.confederacao)) {
            tol += Balanceamento.DEGRAU_VITRINE_EUROPA;
            if (idade <= Balanceamento.VITRINE_IDADE && j.forca >= Balanceamento.VITRINE_FORCA) tol += Balanceamento.DEGRAU_VITRINE_JOVEM;
        }
        if (j.status == StatusJogador.LIVRE) tol += (j.livreDesde > 0 ? m.semanaAbsoluta() - j.livreDesde : 99) / Balanceamento.DEGRAU_SEMANAS_LIVRE;
        double excesso = degrau(j, origem, destino) - tol;
        if (excesso > 1) p *= Balanceamento.DEGRAU_CHANCE_2;
        else if (excesso > 0) p *= Balanceamento.DEGRAU_CHANCE_1;
        return p;
    }

    /**
     * Sorteio do fluxo sem gastar o RNG do mundo: um número fixo por (jogador, país e divisão de
     * destino, temporada). Assim tentar 20 clubes do mesmo país não vira 20 sorteios.
     */
    boolean topaFluxo(Jogador j, Clube origem, Clube destino) {
        if (fluxoLivre) return true;
        double p = afinidade(j, origem, destino);
        if (p >= 1) return true;
        long h = m.semente * 0x9E3779B97F4A7C15L + j.id * 0xBF58476D1CE4E5B9L + destino.pais.hashCode() * 0x94D049BB133111EBL
                + (long) destino.divisao * 7_919L + m.ano * 104_729L;
        h ^= h >>> 31;
        h *= 0x7FB5D329728EA185L;
        h ^= h >>> 27;
        h *= 0x81DADEF4BC2DD44DL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53 < p;
    }

    public void transferir(Jogador j, Clube destino, long preco) {
        transferir(j, destino, preco, false);
    }

    /** {@code jaPago}: o valor já foi pago no acerto (negócio fechado fora da janela). */
    public void transferir(Jogador j, Clube destino, long preco, boolean jaPago) {
        long salario = Financas.salarioPedido(j, destino, m.ano);
        Clube origem = m.clube(j.clubeId);
        if (origem != null) {
            origem.elenco.remove(Integer.valueOf(j.id));
            origem.base.remove(Integer.valueOf(j.id));
            if (!jaPago) {
                origem.dinheiro += preco;
                origem.financas.vendas += preco;
            }
        }
        if (!jaPago) {
            destino.dinheiro -= preco;
            destino.financas.compras += preco;
        }
        j.clubeId = destino.id;
        j.status = StatusJogador.PROFISSIONAL;
        j.aVenda = false;
        j.precoPedido = 0;
        int idade = j.idade(m.ano);
        int meses = idade <= 23 ? 12 * rng.entre(2, 4) : idade <= 30 ? 12 * rng.entre(1, 3) : (rng.chance(0.5) ? 6 : 12);
        j.contrato = Contrato.profissional(salario, fimContrato(meses), Financas.multaRescisoria(j, destino, m.ano));
        destino.elenco.add(j.id);
        j.moral = Math.min(100, j.moral + 10);
        var est = m.historia.estat(m.ano);
        est.transferencias++;
        est.volumeTransferencias += preco;
        boolean internacional = origem != null && !origem.pais.equals(destino.pais);
        if (internacional) est.transferenciasInternacionais++;
        if (preco >= Financas.fatorEpoca(m.ano) * 150_000_000L || (preco > 0 && j.topMundial)) {
            String de = origem != null ? "do " + origem.nome : "";
            m.historia.evento(m.ano, m.semana, "transferencia", "Contratação de peso: " + j.nomeExibicao() + " sai " + de + " para o " + destino.nome
                    + " por " + Financas.formatar(preco) + ".");
        }
        if (noticiario != null) noticiario.transferencia(j, origem, destino, preco);
        if (br.futebas.mundo.Sonda.Atual.s != null) br.futebas.mundo.Sonda.Atual.s.transferencia(j, origem, destino, preco);
        if (destino.humano) {
            m.mensagemDoClube(destino.id, "mercado", "Reforço contratado", j.nomeExibicao() + " é o novo reforço do " + destino.nome + ".");
        } else if (origem != null && origem.humano) {
            m.mensagemDoClube(origem.id, "mercado", "Jogador vendido", j.nomeExibicao() + " foi vendido ao " + destino.nome + " por " + Financas.formatar(preco) + ".");
        }
    }

    /** Fim de contrato daqui a {@code meses}, alinhado ao fim de temporada do calendário do clube. */
    public int fimContrato(int meses) {
        int semanas = Math.max(1, Math.round(meses * 52 / 12f));
        return m.semanaAbsoluta() + semanas;
    }

    /** Negociações do humano (as propostas da IA por jogadores dele passam por aqui). */
    public Negociacoes negociacoes;
    /** Noticiário (o Simulador liga). */
    public br.futebas.historia.Noticiario noticiario;

    /** Clube menor do mesmo país que aceitaria o jogador emprestado (null = nenhum). */
    public Clube destinoEmprestimo(Jogador j, Clube c) {
        List<Clube> cand = new ArrayList<>();
        for (Clube d : m.clubes.values()) {
            if (d.virtual || !d.existe || d == c || d.humano || d.elenco.size() >= 30 || !d.pais.equals(c.pais)) continue;
            if (j.forca >= d.nivel() - 12 && d.reputacao <= c.reputacao && (fluxoLivre || divisaoEfetiva(d) - divisaoEfetiva(c) <= Balanceamento.EMPRESTIMO_DEGRAU)) cand.add(d);
        }
        if (cand.isEmpty()) return null;
        cand.sort(Comparator.comparingDouble((Clube d) -> -d.reputacao).thenComparing(d -> d.id));
        return cand.get(Math.min(cand.size() - 1, rng.inteiro(Math.min(3, cand.size()))));
    }

    /** Por que o dono não empresta o jogador ao clube {@code c} (null = empresta). */
    public static String motivoRecusaEmprestimo(Mundo m, Jogador j, Clube dono, Clube c) {
        if (j.contrato == null || j.contrato.tipo != TipoContrato.PROFISSIONAL) return j.nomeExibicao() + " já está emprestado e não pode ser repassado.";
        int melhores = 0;
        for (int id : dono.elenco) {
            Jogador x = m.jogador(id);
            if (x != null && x != j && x.forca > j.forca) melhores++;
        }
        boolean jovemSemEspaco = j.idade(m.ano) <= 23 && melhores >= 11;
        if (melhores < 14 && !jovemSemEspaco) return "O " + dono.nome + " não empresta " + j.nomeExibicao() + ": ele faz parte do time principal.";
        if (dono.reputacao > c.reputacao + 35 && j.idade(m.ano) > 23) return j.nomeExibicao() + " não quer ir para um clube tão menor.";
        return null;
    }

    private void proporAoHumano(Jogador j, Clube comprador, long valor) {
        if (negociacoes != null) {
            negociacoes.receber(j, comprador, Math.max(valor, Financas.valorMercado(j, m.clube(j.clubeId), m.ano)), "compra");
            return;
        }
        for (PropostaJogador p : m.propostas) if (p.jogadorId == j.id && "pendente".equals(p.estado)) return;
        PropostaJogador p = new PropostaJogador();
        p.id = m.proximaProposta++;
        p.jogadorId = j.id;
        p.compradorId = comprador.id;
        p.comprador = comprador.nome;
        p.vendedorId = j.clubeId;
        p.valor = Math.max(valor, Financas.valorMercado(j, m.clube(j.clubeId), m.ano));
        p.salario = Financas.salarioPedido(j, comprador, m.ano);
        p.semanaLimite = m.semanaAbsoluta() + 2;
        m.propostas.add(p);
        Pais pais = m.pais(comprador.pais);
        Mensagem msg = new Mensagem(m.ano, m.semana, "proposta", "Proposta por " + j.nomeExibicao(),
                "O " + comprador.nome + (pais != null && !comprador.pais.equals(m.clube(j.clubeId).pais) ? " (" + pais.nome + ")" : "")
                        + " oferece " + Financas.formatar(p.valor) + " por " + j.nomeExibicao() + ".");
        msg.propostaId = p.id;
        m.mensagemPara(m.multi() ? m.tecnicoDoClube(p.vendedorId) : null, msg);
    }

    // =================================================================== leilões
    /** Clubes endividados leiloam o mais valioso; lances da IA na última semana; vence o maior. */
    private void leiloes() {
        int agora = m.semanaAbsoluta();
        int novos = 0;
        if (m.semana % 2 == 0) {
            for (Clube c : m.clubes.values()) {
                if (novos >= 3) break;
                if (c.humano || c.virtual || c.dinheiro >= 0) continue;
                if (-c.dinheiro < Financas.limiteEmprestimo(c, m.ano) / 2 || !rng.chance(Balanceamento.LEILAO_CHANCE)) continue;
                Jogador melhor = null;
                long mv = 0;
                for (int id : c.elenco) {
                    Jogador j = m.jogador(id);
                    if (j.amador() || j.contrato == null || j.contrato.tipo != TipoContrato.PROFISSIONAL) continue;
                    long v = Financas.valorMercado(j, c, m.ano);
                    if (v > mv) {
                        mv = v;
                        melhor = j;
                    }
                }
                if (melhor == null || emLeilao(melhor.id) || (negociacoes != null && negociacoes.comprometido(melhor.id))) continue;
                Leilao l = new Leilao();
                l.id = m.proximaProposta++;
                l.jogadorId = melhor.id;
                l.vendedorId = c.id;
                l.lanceMinimo = Financas.arred(mv * 0.6);
                l.fim = agora + Balanceamento.LEILAO_SEMANAS;
                m.leiloes.add(l);
                novos++;
                m.historia.estat(m.ano).leiloes++;
                if (m.tecnico != null) {
                    m.mensagemTodos("mercado", "Leilão", "O " + c.nome + " está leiloando " + melhor.nomeExibicao() + " (força " + melhor.forcaExibida()
                            + "). Lance mínimo: " + Financas.formatar(l.lanceMinimo) + ".");
                }
            }
        }
        for (Leilao l : m.leiloes) {
            if (!"aberto".equals(l.estado) || l.fim > agora) continue;
            Jogador j = m.jogador(l.jogadorId);
            Clube vendedor = m.clube(l.vendedorId);
            if (j == null || vendedor == null || !vendedor.id.equals(j.clubeId)) {
                l.estado = "sem_lances";
                continue;
            }
            // lances da IA: alguns clubes com dinheiro da mesma confederação
            List<Clube> ricos = new ArrayList<>();
            for (Clube c : m.clubes.values()) {
                if (c == vendedor || c.humano || c.virtual || c.dinheiro < l.lanceMinimo) continue;
                if (vendedor.confederacao != null && !vendedor.confederacao.equals(c.confederacao)) continue;
                if (j.forca < c.nivel() - 4 || !topaFluxo(j, vendedor, c)) continue;
                ricos.add(c);
            }
            long valor = Financas.valorMercado(j, vendedor, m.ano);
            for (int k = 0; k < Math.min(4, ricos.size()); k++) {
                Clube c = ricos.get(rng.inteiro(ricos.size()));
                long lance = Financas.arred(rng.real(l.lanceMinimo, Math.max(l.lanceMinimo + 1, valor * 1.1)));
                if (lance > l.maiorLance && c.dinheiro >= lance && cabeNaFolha(c, Financas.salarioPedido(j, c, m.ano))) {
                    l.maiorLance = lance;
                    l.compradorId = c.id;
                }
            }
            Clube comprador = m.clube(l.compradorId);
            if (comprador == null || comprador.dinheiro < l.maiorLance) {
                l.estado = "sem_lances";
                continue;
            }
            l.estado = "vendido";
            if (!janelaAberta(comprador.calendario, m.semana) && negociacoes != null) {
                // fora da janela do comprador: o jogador fica no clube até a janela abrir
                negociacoes.agendarIA(j, vendedor, comprador, l.maiorLance, "compra");
                continue;
            }
            transferir(j, comprador, l.maiorLance);
            if (m.tecnico != null) m.mensagemTodos("mercado", "Leilão encerrado", j.nomeExibicao() + " foi arrematado pelo " + comprador.nome
                    + " por " + Financas.formatar(l.maiorLance) + ".");
        }
    }

    public boolean emLeilao(int jogadorId) {
        for (Leilao l : m.leiloes) if (l.jogadorId == jogadorId && "aberto".equals(l.estado)) return true;
        return false;
    }

    // =================================================================== empréstimos e enxugamento
    private Map<String, List<Clube>> porPais;

    /**
     * Empréstimos da IA na 1ª semana de cada janela (Balanço 2026-11, rodada 2: só ~0,5% dos
     * profissionais estavam emprestados; no futebol real são 5–10%). Clube com reputação ≥
     * EMPRESTIMO_REP_MIN e elenco ≥ EMPRESTIMO_ELENCO_MIN empresta:
     * <ul><li>jovens (≤ 22) fora dos 16 melhores, para ganhar minutos (chance EMPRESTIMO_JOVEM);</li>
     * <li>excedentes (≤ 29) fora dos 22 melhores (chance EMPRESTIMO_EXCEDENTE).</li></ul>
     * O elenco nunca fica abaixo de {@link Balanceamento#ELENCO_MINIMO_IA}. Volta no fim da temporada.
     */
    private void emprestarJovens(Clube c) {
        if (c.reputacao < Balanceamento.EMPRESTIMO_REP_MIN || c.elenco.size() < Balanceamento.EMPRESTIMO_ELENCO_MIN) return;
        List<Jogador> l = new ArrayList<>();
        for (int id : c.elenco) l.add(m.jogador(id));
        l.sort(Comparator.comparingDouble((Jogador j) -> -j.forca).thenComparingInt(j -> j.id));
        for (int i = 16; i < l.size() && c.elenco.size() > Balanceamento.ELENCO_MINIMO_IA; i++) {
            Jogador j = l.get(i);
            if (j.amador() || j.contrato == null || j.contrato.tipo != TipoContrato.PROFISSIONAL || j.posicao == Posicao.GOL && i < 24) continue;
            int idade = j.idade(m.ano);
            double chance = idade <= 22 ? Balanceamento.EMPRESTIMO_JOVEM : idade <= 29 && i >= 22 ? Balanceamento.EMPRESTIMO_EXCEDENTE : 0;
            if (chance <= 0 || !rng.chance(chance)) continue;
            Clube destino = destinoEmprestimo(c, j);
            if (destino == null) continue;
            emprestar(j, c, destino);
        }
    }

    /** Clube menor do mesmo país que aceita o jovem emprestado. */
    public Clube destinoEmprestimo(Clube origem, Jogador j) {
        if (porPais == null) {
            porPais = new HashMap<>();
            for (Clube c : m.clubes.values()) porPais.computeIfAbsent(c.pais, k -> new ArrayList<>()).add(c);
        }
        // degrau (regra do jogo 13/11/2026): de preferência 1 divisão abaixo; no máximo EMPRESTIMO_DEGRAU
        List<Clube> cand = new ArrayList<>(), longe = new ArrayList<>();
        int div = divisaoEfetiva(origem);
        for (Clube d : porPais.getOrDefault(origem.pais, List.of())) {
            if (d.virtual || !d.existe || d == origem || d.humano) continue;
            if (d.reputacao > origem.reputacao - 6 || d.elenco.size() >= 30) continue; // rodada 2: era −10
            if (j.forca < d.nivel() - 12) continue;
            int queda = fluxoLivre ? 0 : divisaoEfetiva(d) - div;
            if (queda <= 1) cand.add(d);
            else if (queda <= Balanceamento.EMPRESTIMO_DEGRAU) longe.add(d);
        }
        if (cand.isEmpty()) cand = longe;
        if (cand.isEmpty()) return null;
        return cand.get(rng.inteiro(cand.size()));
    }

    public void emprestar(Jogador j, Clube origem, Clube destino) {
        origem.elenco.remove(Integer.valueOf(j.id));
        destino.elenco.add(j.id);
        Contrato k = new Contrato();
        k.tipo = TipoContrato.EMPRESTIMO;
        k.clubeOrigem = origem.id;
        k.salario = Math.min(j.contrato.salario, Financas.salarioNaEscala(j, destino, m.ano));
        k.fim = fimDaTemporada(destino);
        k.multa = j.contrato.multa;
        j.contrato = k;
        j.clubeId = destino.id;
        if (br.futebas.mundo.Sonda.Atual.s != null) br.futebas.mundo.Sonda.Atual.s.emprestimo(j, origem, destino);
        if (origem.humano) m.mensagemDoClube(origem.id, "mercado", "Empréstimo", j.nomeExibicao() + " foi emprestado ao " + destino.nome + " até o fim da temporada.");
    }

    /** Última semana absoluta da temporada atual do clube. */
    public int fimDaTemporada(Clube c) {
        if ("europeu".equals(c.calendario)) {
            int ano = m.semana < Balanceamento.SEMANA_VIRADA_EUROPEIA ? m.ano : m.ano + 1;
            return Calendario.absoluta(ano, Balanceamento.SEMANA_VIRADA_EUROPEIA - 1);
        }
        return Calendario.absoluta(m.ano, 52);
    }

    /** Fim do empréstimo: volta ao clube de origem com contrato de 1 ano. */
    public void devolverEmprestado(Jogador j) {
        Clube destino = m.clube(j.clubeId);
        Clube origem = m.clube(j.contrato.clubeOrigem);
        if (destino != null) destino.elenco.remove(Integer.valueOf(j.id));
        if (origem == null || !origem.existe) {
            j.status = StatusJogador.LIVRE;
            j.clubeId = null;
            j.contrato = null;
            return;
        }
        origem.elenco.add(j.id);
        j.clubeId = origem.id;
        j.contrato = null;
        j.contrato = Contrato.profissional(Financas.salarioPedido(j, origem, m.ano), fimDaTemporada(origem) + 52,
                Financas.multaRescisoria(j, origem, m.ano));
    }

    private void enxugar(Clube c) {
        if (c.elenco.size() <= Balanceamento.ELENCO_MAXIMO) return;
        List<Jogador> l = new ArrayList<>();
        for (int id : c.elenco) l.add(m.jogador(id));
        l.sort(Comparator.comparingDouble((Jogador j) -> j.forca - Math.max(0, 28 - j.idade(m.ano)) * 0.8).thenComparingInt(j -> j.id));
        int sobra = c.elenco.size() - 30;
        for (int i = 0; i < l.size() && sobra > 0; i++) {
            Jogador j = l.get(i);
            if (j.contrato != null && j.contrato.tipo == TipoContrato.EMPRESTIMO) continue;
            liberar(j, c);
            sobra--;
        }
    }

    public void liberar(Jogador j, Clube c) {
        c.elenco.remove(Integer.valueOf(j.id));
        c.base.remove(Integer.valueOf(j.id));
        j.status = StatusJogador.LIVRE;
        j.ultimoClubeId = c.id;
        j.livreDesde = m.semanaAbsoluta();
        j.clubeId = null;
        j.contrato = null;
        j.aVenda = false;
    }

    private void colocarAVenda(Clube c) {
        Jogador melhor = null;
        long mv = 0;
        for (int id : c.elenco) {
            Jogador j = m.jogador(id);
            if (j.amador() || j.aVenda) continue;
            long v = Financas.valorMercado(j, c, m.ano);
            if (v > mv) {
                mv = v;
                melhor = j;
            }
        }
        if (melhor != null && rng.chance(0.3)) {
            melhor.aVenda = true;
            melhor.precoPedido = Financas.arred(mv * 0.9);
        }
    }

    // =================================================================== contratos
    /** Contratos que venceram: a IA renova quem vale a pena; os outros saem livres. */
    public void vencimentos() {
        int agora = m.semanaAbsoluta();
        for (Clube c : m.clubes.values()) {
            if (c.virtual) continue;
            for (int i = c.elenco.size() - 1; i >= 0; i--) {
                if (i >= c.elenco.size()) continue;
                Jogador j = m.jogador(c.elenco.get(i));
                if (j.contrato == null || j.contrato.teste()) continue;
                if (j.contrato.tipo == TipoContrato.EMPRESTIMO) {
                    if (j.contrato.fim < agora) devolverEmprestado(j);
                    continue;
                }
                if (c.humano && j.contrato.fim == agora + 3) {
                    m.mensagemDoClube(c.id, "jogador", "Contrato acabando", "O contrato de " + j.nomeExibicao() + " termina em 3 semanas.");
                }
                if (j.contrato.fim >= agora) continue;
                if (c.humano && renovaSozinho(c, j)) {
                    int idade = j.idade(m.ano);
                    int meses = idade <= 26 ? 24 : idade <= 31 ? 12 : 6;
                    long sal = Financas.salarioPedido(j, c, m.ano);
                    j.contrato = Contrato.profissional(sal, fimContrato(meses), Financas.multaRescisoria(j, c, m.ano));
                    m.mensagemDoClube(c.id, "jogador", "Contrato renovado", "Renovação automática: " + j.nomeExibicao() + " renovou por " + meses
                            + " meses (salário " + Financas.formatar(sal) + ").");
                    continue;
                }
                if (!c.humano && renovaria(c, j)
                        && cabeNaFolha(c, Math.max(0, Financas.salarioPedido(j, c, m.ano) - j.contrato.salario))) {
                    int idade = j.idade(m.ano);
                    int meses = idade <= 26 ? 24 : idade <= 31 ? 12 : 6;
                    j.contrato = Contrato.profissional(Financas.salarioPedido(j, c, m.ano), fimContrato(meses), Financas.multaRescisoria(j, c, m.ano));
                } else {
                    liberar(j, c);
                    if (c.humano) m.mensagemDoClube(c.id, "jogador", "Fim de contrato", j.nomeExibicao() + " deixou o clube ao fim do contrato.");
                }
            }
        }
    }

    /** Renovação automática do técnico humano (configurável no {@code Jogo}). */
    private boolean renovaSozinho(Clube c, Jogador j) {
        Tecnico t = m.multi() ? m.tecnicoDoClube(c.id) : m.tecnico;
        if (t == null || !t.renovacaoAutomatica) return false;
        // regra do jogo (13/11): com a renovação automática ligada ninguém sai por fim de contrato
        // (só quem já anunciou a aposentadoria); a força mínima só vale se o técnico a definir
        if (j.vaiAposentar) return false;
        return t.renovacaoForcaMinima <= 0 || j.forca >= t.renovacaoForcaMinima;
    }

    private boolean renovaria(Clube c, Jogador j) {
        int idade = j.idade(m.ano);
        if (idade >= 35) return rng.chance(0.15);
        if (c.elenco.size() <= Balanceamento.ELENCO_MINIMO_IA) return rng.chance(0.85);
        if (j.forca >= c.nivel() - 8) return rng.chance(0.9);
        if (idade <= 23 && j.potencial >= c.nivel()) return rng.chance(0.8);
        return c.elenco.size() < 24 && rng.chance(0.5);
    }
}
