package br.futebas.ui.modelo;

import br.futebas.mundo.Posicao;
import br.futebas.partida.AoVivo;
import br.futebas.partida.Concentracao;
import br.futebas.partida.Escalado;
import br.futebas.partida.Estilo;
import br.futebas.partida.JogoParalelo;
import br.futebas.partida.Marcacao;
import br.futebas.partida.Lance;
import br.futebas.partida.ResultadoPartida;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Ponte entre o motor (thread de trabalho) e a tela da partida. A cada minuto, aplica as
 * ordens do técnico (substituição, estilo), publica uma foto imutável do jogo e espera o tempo
 * da velocidade escolhida. Não depende de JavaFX: os testes usam a mesma classe.
 */
public final class AcompanhamentoPartida implements AoVivo {

    /**
     * Velocidades da partida. Na tela e nas Opções: Lenta, Normal, Rápida e Ultra rápida (pedido do
     * dono). INSTANTANEA fica só para testes e capturas (sem espera); a preferência antiga
     * "Instantânea" vira Ultra rápida ({@code Opcoes.velocidade}). Para pular o resto do jogo: o botão
     * "SIMULAR ATÉ O FIM".
     */
    public enum Velocidade {
        LENTA("Lenta", 2000), NORMAL("Normal", 1000), RAPIDA("Rápida", 400), ULTRA("Ultra rápida", 120), INSTANTANEA("Instantânea", 0);

        public final String nome;
        public final int msPorMinuto;

        Velocidade(String nome, int ms) {
            this.nome = nome;
            this.msPorMinuto = ms;
        }

        /**
         * Suspense do pênalti (requisito: "foi para a bola, bateu, espera um pouco"), em ms:
         * as esperas entre "Pênalti marcado", "vai para a bola", "bateu" e o resultado. Fixas em todas
         * as velocidades (o padrão é devagar); Instantânea (testes/capturas) = sem espera.
         */
        public long[] suspensePenalti() {
            // ritmo fixo, igual em todas as velocidades (requisito): uma linha a cada ~1,2 s
            return this == INSTANTANEA ? new long[] {0, 0, 0} : new long[] {RITMO_PENALTI_MS, RITMO_PENALTI_MS, RITMO_PENALTI_MS};
        }

        /** Tempo total do suspense de uma cobrança (ms). */
        public long suspenseTotal() {
            long t = 0;
            for (long x : suspensePenalti()) t += x;
            return t;
        }

        /** Espera entre as linhas do pênalti na narração (fixa: não muda com a velocidade). */
        public static final long RITMO_PENALTI_MS = 1200;

        /** As que aparecem na tela da partida e nas Opções. */
        public static java.util.List<Velocidade> visiveis() {
            return java.util.List.of(LENTA, NORMAL, RAPIDA, ULTRA);
        }
    }

    /** Um jogador na foto do jogo ({@code energia} 0–100, gasta minuto a minuto pelo motor). */
    public record Atleta(int id, String nome, Posicao natural, Posicao vaga, int forca, boolean amarelo, int energia, boolean lesionado) {
        public Atleta(int id, String nome, Posicao natural, Posicao vaga, int forca, boolean amarelo, int energia) {
            this(id, nome, natural, vaga, forca, amarelo, energia, false);
        }
    }

    /** Em {@code Estado.saidas}: saiu lesionado sem substituição possível (o time ficou com um a menos). */
    public static final String SAIU_LESIONADO = "saiu lesionado (um a menos)";

    /**
     * O motor parou e espera o técnico: intervalo, lesão ou expulsão no seu time (a tela abre a
     * tática e as trocas e chama {@link #pausar(boolean) pausar(false)}), ou pênalti a favor
     * ({@code candidatos} preenchido; a tela responde com {@link #responderBatedor}).
     */
    public record Pedido(AoVivo.Pausa motivo, Integer jogadorId, String jogador, List<Atleta> candidatos) {}

    /**
     * Disputa de pênaltis: o motor pede a ordem dos batedores do seu time ({@code sugestao}: quem
     * terminou em campo, na ordem automática). A tela responde com {@link #responderOrdem}.
     */
    public record PedidoOrdem(int lado, String time, String adversario, List<Atleta> sugestao) {}

    /** Foto imutável do jogo num minuto. */
    public record Estado(int sequencia, boolean novoJogo, boolean terminado, int minuto, int acrescimo, boolean intervalo,
                         String mandante, String visitante, String mandanteId, String visitanteId,
                         int golsMandante, int golsVisitante, List<Lance> novosLances, int meuLado,
                         List<Atleta> emCampo, List<Atleta> banco, Set<Integer> sairam,
                         List<Atleta> vagas, List<Atleta> fora, java.util.Map<Integer, String> saidas,
                         int subsFeitas, int subsPermitidas, Estilo estilo, Marcacao marcacao, Concentracao concentracao,
                         ResultadoPartida resultado, List<JogoParalelo> paralelos, Pedido pedido) {
        /**
         * Expulso: saiu do jogo mas continua na vaga dele (o motor deixa o expulso na vaga; o
         * substituído dá a vaga a quem entrou). Bug corrigido: antes todo mundo que saiu aparecia "expulso".
         */
        public boolean expulso(int id) {
            if (!sairam.contains(id) || foraPorLesao(id)) return false;
            for (Atleta a : emCampo) if (a.id() == id) return false;
            for (Atleta a : vagas) if (a.id() == id) return true;
            return false;
        }

        /** Saiu lesionado sem poder ser substituído: a vaga fica vazia (um a menos), sem cartão. */
        public boolean foraPorLesao(int id) {
            return saidas != null && SAIU_LESIONADO.equals(saidas.get(id));
        }

        /** Como saiu quem não volta: "expulso" ou "substituído aos 62' por X" (o que se souber). */
        public String comoSaiu(int id) {
            if (expulso(id)) return "expulso";
            String s = saidas == null ? null : saidas.get(id);
            return s == null ? "substituído" : s;
        }

        public String relogio() {
            if (terminado) return "Fim";
            if (intervalo) return "Intervalo";
            return acrescimo > 0 ? minuto + "+" + acrescimo + "'" : minuto + "'";
        }
    }

    /** Fator sobre o tempo por minuto (as capturas automáticas aceleram). */
    public static volatile double escala = 1.0;

    public interface Ouvinte {
        void atualizar(Estado e);
    }

    private final String meuClubeId;
    private final Ouvinte ouvinte;
    private final ConcurrentLinkedQueue<Consumer<Controle>> ordens = new ConcurrentLinkedQueue<>();
    private volatile Velocidade velocidade;
    private volatile boolean pausado;
    private volatile boolean pausarNoIntervalo;
    private volatile boolean cancelar;

    private Controle atual;
    private int enviados;
    private int sequencia;
    private int meuLado = -1;
    private final Set<Integer> amarelados = new HashSet<>();
    /** Quem se machucou neste jogo (lances "lesao"). */
    private final Set<Integer> lesionados = new HashSet<>();
    /** Todos os do seu time já vistos neste jogo (para listar quem saiu e não volta). */
    private final java.util.Map<Integer, Atleta> vistos = new java.util.LinkedHashMap<>();
    /** Substituições do seu time vistas na narração: id de quem saiu → "substituído aos 62' por X". */
    private final java.util.Map<Integer, String> saidas = new java.util.HashMap<>();
    private int jogos;
    private volatile List<JogoParalelo> paralelos = List.of();
    private volatile Integer batedor;
    /** Disputa de pênaltis: quem mostra a escolha da ordem (null = ordem automática) e a resposta. */
    private volatile Consumer<PedidoOrdem> aoPedirOrdem;
    private volatile List<Integer> ordem;
    private volatile boolean ordemRespondida;
    /** Disputa de pênaltis decidida: quem mostra as cobranças (multi-treinador). */
    private volatile java.util.function.BiConsumer<ResultadoPartida, List<Lance>> aoDisputar;
    private volatile boolean respondido;
    /** A tela espera o técnico nas pausas do motor (sem isto, as pausas só são publicadas). */
    private volatile boolean esperarTecnico;
    /** Segundo jogo da mesma semana: o motor espera o técnico voltar da central. */
    private volatile boolean aguardandoProximo;

    // pausas escolhidas nas Opções (padrão: como antes das opções)
    private volatile boolean pausaIntervalo = true, pausaLesao = true, pausaExpulsao = true, pedirBatedor = true;
    private volatile boolean pausaGolMeu, pausaGolSofrido;
    /** Quantos lances já foram olhados para o suspense do pênalti. */
    private int lancesVistosPenalti;
    /** Quantos lances já foram olhados para a pausa no gol. */
    private int lancesVistosGol;

    /**
     * Onde o jogo para para o técnico: intervalo (tática), lesão e expulsão no seu time, pênalti a
     * favor (escolher o batedor; desligado = cobrador definido), gol a favor e gol sofrido.
     */
    public void configurarPausas(boolean intervalo, boolean lesao, boolean expulsao, boolean batedor, boolean golMeu, boolean golSofrido) {
        pausaIntervalo = intervalo;
        pausaLesao = lesao;
        pausaExpulsao = expulsao;
        pedirBatedor = batedor;
        pausaGolMeu = golMeu;
        pausaGolSofrido = golSofrido;
    }

    public boolean aguardandoProximo() { return aguardandoProximo; }

    public void liberarProximo() { aguardandoProximo = false; }

    public void esperarTecnico(boolean e) { esperarTecnico = e; }

    public AcompanhamentoPartida(String meuClubeId, Velocidade velocidade, Ouvinte ouvinte) {
        this.meuClubeId = meuClubeId;
        this.velocidade = velocidade;
        this.ouvinte = ouvinte;
    }

    public Velocidade velocidade() { return velocidade; }

    public void velocidade(Velocidade v) {
        velocidade = v;
    }

    public boolean pausado() { return pausado; }

    public void pausar(boolean p) {
        pausado = p;
    }

    public void pausarNoIntervalo(boolean p) {
        pausarNoIntervalo = p;
    }

    /** Pula para o fim (o restante do jogo roda sem espera e sem pausa). */
    public void cancelarEspera() {
        cancelar = true;
        pausado = false;
    }

    public int jogosAcompanhados() { return jogos; }

    /** Os números do jogo acompanhado até a última foto publicada (null antes do apito). */
    private volatile br.futebas.partida.Estatisticas estatisticas;

    public br.futebas.partida.Estatisticas estatisticas() { return estatisticas; }

    // ------------------------------------------------------------------ ordens do técnico
    public void substituir(int saiId, int entraId) {
        ordens.add(c -> {
            if (meuLado >= 0) c.substituir(meuLado, saiId, entraId);
        });
    }

    /** Muda a formação do seu time (não gasta substituição; cada um vai para a vaga em que rende mais). */
    public void mudarFormacao(String formacao) {
        ordens.add(c -> {
            if (meuLado >= 0) c.mudarFormacao(meuLado, formacao);
        });
    }

    /** Dois do seu time em campo trocam de posição (não gasta substituição). */
    public void trocarPosicoes(int idA, int idB) {
        ordens.add(c -> {
            if (meuLado >= 0) c.trocarPosicoes(meuLado, idA, idB);
        });
    }

    public void mudarEstilo(Estilo e) {
        ordens.add(c -> {
            if (meuLado >= 0) c.mudarEstilo(meuLado, e);
        });
    }

    public void mudarMarcacao(Marcacao m) {
        ordens.add(c -> {
            if (meuLado >= 0) c.mudarMarcacao(meuLado, m);
        });
    }

    public void mudarConcentracao(Concentracao x) {
        ordens.add(c -> {
            if (meuLado >= 0) c.mudarConcentracao(meuLado, x);
        });
    }

    /**
     * Quem mostra a escolha da ordem dos pênaltis (chamado na thread do motor; a tela responde com
     * {@link #responderOrdem}). Sem ninguém, a ordem é a automática.
     */
    public void aoPedirOrdem(Consumer<PedidoOrdem> c) { aoPedirOrdem = c; }

    /** Quem mostra as cobranças da disputa de pênaltis (chamado na thread do motor). */
    public void aoDisputar(java.util.function.BiConsumer<ResultadoPartida, List<Lance>> c) { aoDisputar = c; }

    /** Resposta à ordem dos pênaltis: os ids na ordem (null = a automática). */
    public void responderOrdem(List<Integer> ids) {
        ordem = ids == null ? null : List.copyOf(ids);
        ordemRespondida = true;
    }

    /** Resposta ao pedido de batedor de pênalti (null = o automático escolhe). */
    public void responderBatedor(Integer jogadorId) {
        batedor = jogadorId;
        respondido = true;
    }

    // ------------------------------------------------------------------ AoVivo
    /** Começo de um jogo novo: zera o que é do jogo anterior e acha o lado do técnico. */
    private void comecarJogo(Controle c) {
        atual = c;
        enviados = 0;
        jogos++;
        cancelar = false;
        lancesVistosGol = 0;
        lancesVistosPenalti = 0;
        amarelados.clear();
        lesionados.clear();
        estatisticas = null;
        vistos.clear();
        saidas.clear();
        meuLado = meuClubeId == null ? -1
                : meuClubeId.equals(c.time(0).clubeId) ? 0 : meuClubeId.equals(c.time(1).clubeId) ? 1 : -1;
    }

    // ------------------------------------------------------------------ multi-treinador (MesaAoVivo)
    /**
     * Multi-treinador: o minuto sem a espera da velocidade (quem segura o tempo é a {@link MesaAoVivo},
     * que anda com todos os jogos juntos). Aplica as ordens e publica a foto do jogo.
     */
    public void minutoMulti(Controle c) {
        boolean novo = c != atual;
        if (novo) comecarJogo(c);
        aplicarOrdens(c);
        publicar(c, novo, false, null);
    }

    /** Multi-treinador: enquanto o jogo espera os outros, as ordens do técnico valem na hora. */
    public void aplicarPendentes(Controle c) {
        if (ordens.isEmpty() || c == null) return;
        aplicarOrdens(c);
        publicar(c, false, false, null);
    }

    /** Lado do técnico no jogo atual (0 mandante, 1 visitante, -1 nenhum/antes do apito). */
    public int meuLado() { return meuLado; }

    /** Clube do técnico que acompanha. */
    public String clubeId() { return meuClubeId; }

    @Override
    public void minuto(Controle c) {
        boolean novo = c != atual;
        if (novo) comecarJogo(c);
        boolean jaEsperou = esperouAntes;
        if (novo) esperouAntes = false;
        if (novo && jogos > 1 && esperarTecnico && !jaEsperou) {
            aguardandoProximo = true;
            publicar(c, true, false, null);
            while (aguardandoProximo) dormir(40);
            aplicarOrdens(c);
            publicar(c, false, false, null);
            esperar(c);
            return;
        }
        aplicarOrdens(c);
        publicar(c, novo, false, null);
        esperar(c);
    }

    /** Já esperou o técnico antes de montar os times deste jogo (2º jogo da semana). */
    private volatile boolean esperouAntes;

    @Override
    public void antesDoProximoJogo(String mandanteId, String mandante, String visitanteId, String visitante) {
        if (jogos < 1 || !esperarTecnico || ouvinte == null) return;
        // volta à central antes de o motor montar os times: o pré-jogo e a escalação valem para este jogo
        aguardandoProximo = true;
        ouvinte.atualizar(new Estado(++sequencia, true, false, 0, 0, false, mandante, visitante, mandanteId, visitanteId, 0, 0,
                List.of(), -1, List.of(), List.of(), Set.of(), List.of(), List.of(), java.util.Map.of(), 0, 0, null, null, null,
                null, paralelos, null));
        while (aguardandoProximo) dormir(40);
        esperouAntes = true;
    }

    @Override
    public void fim(ResultadoPartida r) {
        if (atual == null) return;
        publicar(atual, false, true, r);
    }

    @Override
    public void outrosJogos(List<JogoParalelo> jogos) {
        paralelos = jogos == null ? List.of() : List.copyOf(jogos);
    }

    @Override
    public void pausa(Controle c, AoVivo.Pausa motivo, int lado, Integer jogadorId) {
        if (cancelar || ouvinte == null || meuLado < 0) return;
        if (motivo != AoVivo.Pausa.INTERVALO && lado != meuLado) return;
        boolean querPausa = switch (motivo) {
            case INTERVALO -> pausaIntervalo;
            case LESAO -> pausaLesao;
            case EXPULSAO -> pausaExpulsao;
            default -> true;
        };
        if (!esperarTecnico || !querPausa) {
            aplicarOrdens(c);
            return;
        }
        aplicarOrdens(c);
        String nome = null;
        if (jogadorId != null) {
            for (int l = 0; l < 2 && nome == null; l++) {
                for (Escalado e : c.emCampo(l)) if (e.id == jogadorId) nome = e.nome;
                for (Escalado e : c.banco(l)) if (e.id == jogadorId) nome = e.nome;
            }
        }
        pausado = true;
        Pedido p = new Pedido(motivo, jogadorId, nome, null);
        publicar(c, false, false, null, p);
        while (pausado && !cancelar) {
            dormir(40);
            if (!ordens.isEmpty()) {
                aplicarOrdens(c);
                // bug das trocas: o técnico confirma e fecha a tela (pausado = false) enquanto o motor
                // dorme; publicar de novo com o pedido reabria a tela de expulsão/lesão logo depois
                publicar(c, false, false, null, pausado ? p : null);
            }
        }
        aplicarOrdens(c);
        publicar(c, false, false, null, null);
    }

    @Override
    public Integer escolherBatedor(Controle c, int lado, List<Escalado> candidatos) {
        if (cancelar || ouvinte == null || !esperarTecnico || !pedirBatedor || lado != meuLado || candidatos == null || candidatos.isEmpty()) return null;
        respondido = false;
        batedor = null;
        List<Atleta> cs = new ArrayList<>();
        for (Escalado e : candidatos) cs.add(atleta(e));
        publicar(c, false, false, null, new Pedido(AoVivo.Pausa.PENALTI_A_FAVOR, null, null, List.copyOf(cs)));
        while (!respondido && !cancelar) dormir(40);
        Integer b = batedor;
        publicar(c, false, false, null, null);
        return cancelar ? null : b;
    }

    @Override
    public List<Integer> ordemPenaltis(int lado, List<Escalado> sugestao) {
        Consumer<PedidoOrdem> mostrar = aoPedirOrdem;
        // SIMULAR ATÉ O FIM, sem tela ou lado de outro técnico: ordem automática
        if (cancelar || mostrar == null || !esperarTecnico || lado != meuLado || sugestao == null || sugestao.isEmpty()) return null;
        ordemRespondida = false;
        ordem = null;
        List<Atleta> cs = new ArrayList<>();
        for (Escalado e : sugestao) cs.add(atleta(e));
        String time = atual == null ? "" : atual.time(lado).nome, adv = atual == null ? "" : atual.time(1 - lado).nome;
        mostrar.accept(new PedidoOrdem(lado, time, adv, List.copyOf(cs)));
        while (!ordemRespondida && !cancelar) dormir(40);
        return cancelar && !ordemRespondida ? null : ordem;
    }

    @Override
    public void disputaPenaltis(ResultadoPartida r, List<Lance> cobrancas) {
        var d = aoDisputar;
        if (d != null) d.accept(r, cobrancas);
    }

    private void aplicarOrdens(Controle c) {
        Consumer<Controle> o;
        boolean houve = false;
        while ((o = ordens.poll()) != null) {
            o.accept(c);
            houve = true;
        }
        if (houve && meuLado >= 0) c.assumirControle(meuLado);
    }

    private void esperar(Controle c) {
        if (cancelar) return;
        int ms = velocidade.msPorMinuto;
        if (ms > 0) {
            // gol: um respiro para a emoção
            List<Lance> ls = c.lances();
            if (!ls.isEmpty() && "gol".equals(ls.get(ls.size() - 1).tipo) && ls.get(ls.size() - 1).minuto == c.minuto())
                ms += velocidade == Velocidade.LENTA ? 2500 : velocidade == Velocidade.NORMAL ? 1800 : velocidade == Velocidade.ULTRA ? 400 : 1000;
            if (c.intervalo()) ms = Math.max(ms, 1500);
            // pênalti cobrado neste minuto: o motor espera o suspense da tela (vai para a bola, bateu, resultado)
            for (int i = lancesVistosPenalti; i < ls.size(); i++) if (resultadoDePenalti(ls.get(i))) ms += (int) velocidade.suspenseTotal();
            dormir((int) Math.max(1, ms * escala));
        }
        lancesVistosPenalti = c.lances().size();
        if (c.intervalo() && pausarNoIntervalo && !cancelar) pausado = true;
        if (pausouNoGol(c)) publicar(c, false, false, null);
        while (pausado && !cancelar) {
            dormir(40);
            if (!ordens.isEmpty()) {
                aplicarOrdens(c);
                publicar(c, false, false, null);
            }
        }
    }

    /** Gol novo do seu time (ou contra ele) com a pausa ligada: para o jogo até o técnico retomar. */
    private boolean pausouNoGol(Controle c) {
        List<Lance> ls = c.lances();
        boolean pausa = false;
        for (int i = lancesVistosGol; i < ls.size(); i++) {
            Lance l = ls.get(i);
            if (!"gol".equals(l.tipo) || meuLado < 0) continue;
            if (l.lado == meuLado ? pausaGolMeu : pausaGolSofrido) pausa = true;
        }
        lancesVistosGol = ls.size();
        if (pausa && esperarTecnico && !cancelar && ouvinte != null) {
            pausado = true;
            return true;
        }
        return false;
    }

    /** O lance é o desfecho de um pênalti no jogo (gol, defesa, trave ou fora com jogada "penalti")? */
    public static boolean resultadoDePenalti(Lance l) {
        if (!"penalti".equals(l.jogada) || l.lado < 0) return false;
        return switch (l.tipo) {
            case "gol", "defesa", "milagre", "trave", "fora" -> true;
            default -> false;
        };
    }

    private static void dormir(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void publicar(Controle c, boolean novo, boolean terminado, ResultadoPartida r) {
        publicar(c, novo, terminado, r, null);
    }

    private void publicar(Controle c, boolean novo, boolean terminado, ResultadoPartida r, Pedido pedido) {
        List<Lance> todos = c.lances();
        List<Lance> novos = new ArrayList<>();
        for (int i = enviados; i < todos.size(); i++) {
            Lance l = todos.get(i);
            novos.add(l);
            if ("cartao".equals(l.tipo) && l.jogadorId != null) amarelados.add(l.jogadorId);
            if ("lesao".equals(l.tipo) && l.jogadorId != null) lesionados.add(l.jogadorId);
        }
        enviados = todos.size();
        List<Atleta> campo = new ArrayList<>(), banco = new ArrayList<>(), vagas = new ArrayList<>(), fora = new ArrayList<>();
        Set<Integer> sairam = Set.of();
        int subs = 0;
        Estilo estilo = null;
        Marcacao marcacao = null;
        Concentracao concentracao = null;
        if (meuLado >= 0) {
            marcacao = c.marcacao(meuLado);
            concentracao = c.concentracao(meuLado);
            for (Escalado e : c.emCampo(meuLado)) campo.add(atleta(e));
            for (Escalado e : c.banco(meuLado)) banco.add(atleta(e));
            for (Escalado e : c.vagas(meuLado)) vagas.add(atleta(e));
            for (Atleta a : campo) vistos.put(a.id(), a);
            for (Atleta a : vagas) vistos.putIfAbsent(a.id(), a);
            sairam = Set.copyOf(c.sairam(meuLado));
            for (int id : sairam) if (vistos.containsKey(id)) fora.add(vistos.get(id));
            // quem saiu substituído: o minuto e quem entrou (lance "substituicao": jogador = quem entra, detalhe = quem sai)
            for (Lance l : todos) {
                if ("desfalque".equals(l.tipo) && l.lado == meuLado && l.jogadorId != null) saidas.put(l.jogadorId, SAIU_LESIONADO);
                if (!"substituicao".equals(l.tipo) || l.lado != meuLado || l.detalhe == null) continue;
                for (Atleta a : fora) {
                    if (a.nome().equals(l.detalhe) && !saidas.containsKey(a.id()))
                        saidas.put(a.id(), "substituído aos " + l.minuto + "'" + (l.jogador == null ? "" : " por " + l.jogador));
                }
            }
            subs = c.substituicoesFeitas(meuLado);
            estilo = c.estilo(meuLado);
        }
        Estado e = new Estado(++sequencia, novo, terminado, c.minuto(), c.acrescimo(), c.intervalo(),
                c.time(0).nome, c.time(1).nome, c.time(0).clubeId, c.time(1).clubeId, c.gols(0), c.gols(1),
                List.copyOf(novos), meuLado, List.copyOf(campo), List.copyOf(banco), sairam, List.copyOf(vagas), List.copyOf(fora), java.util.Map.copyOf(saidas), subs,
                c.substituicoesPermitidas(), estilo, marcacao, concentracao, r, paralelos, pedido);
        estatisticas = r != null ? br.futebas.partida.Estatisticas.de(r) : c.estatisticas();
        if (ouvinte != null) ouvinte.atualizar(e);
    }

    private Atleta atleta(Escalado e) {
        return new Atleta(e.id, e.nome, e.natural, e.vaga, (int) Math.round(e.forca), amarelados.contains(e.id), (int) Math.round(e.energia),
                lesionados.contains(e.id));
    }
}
