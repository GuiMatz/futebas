package br.futebas.ui.modelo;

import br.futebas.partida.AoVivo;
import br.futebas.partida.AoVivoMulti;
import br.futebas.partida.Escalado;
import br.futebas.partida.JogoParalelo;
import br.futebas.partida.OpcoesPartida;
import br.futebas.partida.ResultadoPartida;
import br.futebas.ui.modelo.AcompanhamentoPartida.Velocidade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Multi-treinador (regra do jogo): os jogos ao vivo de todos os técnicos humanos da rodada rodam
 * JUNTOS. O motor simula cada jogo na sua thread; a mesa segura os minutos para que todos andem no
 * mesmo relógio (quem está à frente espera os outros; o relógio anda na velocidade escolhida) e põe as
 * pausas em fila: no intervalo, primeiro o técnico 1 mexe no time, depois o 2, e assim por diante (o
 * mesmo vale para lesão e expulsão). Num duelo direto, o mandante primeiro, depois o visitante. Sem
 * JavaFX: os testes usam a mesma classe.
 */
public final class MesaAoVivo implements AoVivoMulti, br.futebas.kings.AoVivoKingsMulti {

    /** Quem mostra a mesa (a tela dividida). */
    public interface Ouvinte {
        /** Um grupo de jogos vai começar: monte as colunas e chame {@link #colunasProntas()}. */
        void grupo(List<Jogo> jogos);

        /**
         * Um 2º grupo na mesma semana: os técnicos desses {@code clubes} voltam à central (cada um na sua
         * vez); chame {@link #liberarGrupo()} quando o último mandar jogar.
         */
        default void antesDoGrupo(List<String> clubes) {}

        /** Todos os jogos do grupo terminaram. */
        default void fimDoGrupo() {}
    }

    /** Ordem do técnico do clube (0 = primeiro a jogar a vez); clubes sem técnico: grande. */
    private final ToIntFunction<String> ordem;
    /** Cria o acompanhamento de um clube humano (o ouvinte de cada um é da tela). */
    private final Function<String, AcompanhamentoPartida> fabrica;
    private volatile Ouvinte ouvinte;
    /** Kings: cria o acompanhamento do time da Kings de um técnico (id do time, lado 0/1). */
    private volatile java.util.function.BiFunction<String, Integer, AcompanhamentoKings> fabricaKings;

    /** Kings no multi: quem cria o acompanhamento de cada time da Kings de técnico. */
    public void fabricaKings(java.util.function.BiFunction<String, Integer, AcompanhamentoKings> f) { fabricaKings = f; }

    private final List<Jogo> montando = new ArrayList<>();
    private final List<Jogo> grupo = new ArrayList<>();
    private volatile Velocidade velocidade;
    private volatile boolean pausado, pulando, colunasProntas, grupoLiberado;
    private long proximoTick;
    /** Jogo que está com a vez de pausa (um técnico mexendo no time). */
    private Jogo comAVez;

    public MesaAoVivo(Velocidade velocidade, ToIntFunction<String> ordem, Function<String, AcompanhamentoPartida> fabrica) {
        this.velocidade = velocidade;
        this.ordem = ordem;
        this.fabrica = fabrica;
    }

    public void ouvinte(Ouvinte o) { ouvinte = o; }

    public Velocidade velocidade() { return velocidade; }

    public void velocidade(Velocidade v) { velocidade = v; }

    /** Para todos os jogos (Pausar, ou um técnico abriu "Tática e trocas"). */
    public void pausar(boolean p) { pausado = p; }

    public boolean pausado() { return pausado; }

    /** SIMULAR ATÉ O FIM: todos os jogos seguem sem espera e sem pausas. */
    public void pular() {
        pulando = true;
        pausado = false;
        synchronized (this) {
            for (Jogo j : grupo) {
                for (AcompanhamentoPartida a : j.lados) a.cancelarEspera();
                for (AcompanhamentoKings k : j.ladosKings) k.pularParaOFim();
            }
        }
    }

    public boolean pulando() { return pulando; }

    /** A tela montou as colunas do grupo: os jogos podem começar. */
    public void colunasProntas() { colunasProntas = true; }

    /** A tela liberou o 2º grupo da semana. */
    public void liberarGrupo() { grupoLiberado = true; }

    /** Os jogos do grupo atual. */
    public synchronized List<Jogo> jogos() { return List.copyOf(grupo); }

    // ------------------------------------------------------------------ AoVivoMulti (motor)
    @Override
    public AoVivo partida(String mandanteId, String visitanteId, OpcoesPartida op) {
        Jogo j = new Jogo(mandanteId, visitanteId);
        if (op.ladoHumano >= 0) j.adicionar(op.ladoHumano == 0 ? mandanteId : visitanteId);
        if (op.ambosHumanos) j.adicionar(op.ladoHumano == 0 ? visitanteId : mandanteId);
        if (j.lados.isEmpty()) return null;
        synchronized (this) {
            montando.add(j);
        }
        return j;
    }

    // ------------------------------------------------------------------ AoVivoKingsMulti (motor da Kings)
    @Override
    public br.futebas.kings.AoVivoKings partida(String timeA, String timeB, boolean humanoA, boolean humanoB) {
        var f = fabricaKings;
        if (f == null) return null;
        Jogo j = new Jogo(timeA, timeB);
        j.kings = true;
        if (humanoA) j.adicionarKings(timeA, 0, f);
        if (humanoB) j.adicionarKings(timeB, 1, f);
        if (j.ladosKings.isEmpty()) return null;
        synchronized (this) {
            montando.add(j);
        }
        return j;
    }

    @Override
    public void terminou(br.futebas.kings.AoVivoKings jogo) {
        if (jogo instanceof Jogo j) {
            synchronized (this) {
                j.terminado = true;
            }
        }
    }

    @Override
    public void iniciarGrupo() {
        synchronized (this) {
            grupo.clear();
            grupo.addAll(montando);
            montando.clear();
            comAVez = null;
            proximoTick = 0;
        }
        colunasProntas = false;
        Ouvinte o = ouvinte;
        if (o == null || jogos().isEmpty()) return;
        o.grupo(jogos());
        // espera a tela montar as colunas (sem isso os primeiros minutos se perdem); no máximo 10 s
        long limite = System.currentTimeMillis() + 10_000;
        while (!colunasProntas && System.currentTimeMillis() < limite) dormir(10);
    }

    @Override
    public void fimDoGrupo() {
        Ouvinte o = ouvinte;
        if (o != null) o.fimDoGrupo();
    }

    @Override
    public void antesDoGrupo(List<String> clubes) {
        Ouvinte o = ouvinte;
        pulando = false; // grupo novo: o "simular até o fim" do anterior não vale para ele
        pausado = false;
        if (o == null) return;
        grupoLiberado = false;
        o.antesDoGrupo(List.copyOf(clubes));
        while (!grupoLiberado) dormir(40);
    }

    // ------------------------------------------------------------------ relógio comum
    /** Chave do momento do jogo (cresce com o tempo): minuto × 100 + acréscimo; intervalo = 45:99. */
    static int chave(AoVivo.Controle c) {
        return c.minuto() * 100 + (c.intervalo() ? 99 : Math.min(98, c.acrescimo()));
    }

    /** O jogo chegou no momento {@code k}: espera os outros e o tempo do relógio (aplicando as ordens enquanto isso). */
    /** Kings: o minuto da Kings (~40) na escala do relógio do futebol (90). */
    static int chaveKings(br.futebas.kings.AoVivoKings.ControleKings c) {
        return (int) Math.round(c.minuto() * 90.0 / 40.0) * 100;
    }

    private void chegar(Jogo j, int k, AoVivo.Controle c) {
        // pênalti cobrado neste minuto: o relógio comum espera o suspense da tela (vai para a bola, bateu, resultado)
        long suspense = 0;
        if (c != null) {
            List<br.futebas.partida.Lance> ls = c.lances();
            for (int i = Math.min(j.lancesVistosPenalti, ls.size()); i < ls.size(); i++)
                if (AcompanhamentoPartida.resultadoDePenalti(ls.get(i))) suspense += velocidade.suspenseTotal();
            j.lancesVistosPenalti = ls.size();
        }
        synchronized (this) {
            j.chave = k;
            j.esperando = true;
            j.suspensePendente += suspense;
        }
        try {
            while (true) {
                synchronized (this) {
                    if (pulando || j.liberado >= k) return;
                    tentarLiberar();
                    if (j.liberado >= k) return;
                }
                for (AcompanhamentoPartida a : j.lados) a.aplicarPendentes(c);
                dormir(12);
            }
        } finally {
            synchronized (this) {
                j.esperando = false;
            }
        }
    }

    /** Libera o momento mais atrasado quando todos chegaram e o relógio andou. Chamar com a trava. */
    private void tentarLiberar() {
        if (pausado || comAVez != null) return;
        int min = Integer.MAX_VALUE;
        for (Jogo x : grupo) {
            if (x.terminado) continue;
            // alguém ainda no minuto, liberado e ainda não saiu, ou pedindo a vez: todos esperam
            if (!x.esperando || x.pedindo || x.liberado >= x.chave) return;
            min = Math.min(min, x.chave);
        }
        if (min == Integer.MAX_VALUE) return;
        long agora = System.currentTimeMillis();
        if (agora < proximoTick) return;
        long suspense = 0;
        for (Jogo x : grupo) if (!x.terminado && x.esperando && x.chave == min) {
            x.liberado = min;
            suspense = Math.max(suspense, x.suspensePendente);
            x.suspensePendente = 0;
        }
        int ms = (int) (velocidade.msPorMinuto + suspense);
        if (min % 100 == 99) ms = Math.max(ms, 1500); // intervalo: um respiro
        proximoTick = agora + (long) Math.max(0, ms * AcompanhamentoPartida.escala);
    }

    /** Fila das pausas: espera todos pararem e a vez deste técnico (ordem dos técnicos). */
    private boolean pedirVez(Jogo j, int ordemTecnico) {
        synchronized (this) {
            j.pedindo = true;
            j.ordemPedido = ordemTecnico;
        }
        try {
            while (true) {
                synchronized (this) {
                    if (pulando) return false;
                    if (comAVez == null && quietos() && primeiroDaFila() == j) {
                        comAVez = j;
                        return true;
                    }
                }
                dormir(12);
            }
        } finally {
            synchronized (this) {
                j.pedindo = false;
            }
        }
    }

    private void devolverVez(Jogo j) {
        synchronized (this) {
            if (comAVez == j) comAVez = null;
        }
    }

    /** Todos os jogos estão parados (esperando o relógio, pedindo a vez ou terminados)? */
    private boolean quietos() {
        for (Jogo x : grupo) {
            if (x.terminado || x.pedindo) continue;
            // esperando o relógio e ainda não liberado: parado; liberado (vai andar) ou no minuto: não
            if (!x.esperando || x.liberado >= x.chave) return false;
        }
        return true;
    }

    private Jogo primeiroDaFila() {
        Jogo melhor = null;
        for (Jogo x : grupo) if (x.pedindo && (melhor == null || x.ordemPedido < melhor.ordemPedido)) melhor = x;
        return melhor;
    }

    private static void dormir(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ um jogo do grupo
    /** Um jogo da mesa: 1 acompanhamento (um técnico) ou 2 (duelo direto: mandante primeiro). */
    public final class Jogo implements AoVivo, br.futebas.kings.AoVivoKings {
        public final String mandanteId, visitanteId;
        /** Os acompanhamentos dos técnicos deste jogo (duelo: o do mandante primeiro). */
        public final List<AcompanhamentoPartida> lados = new ArrayList<>();
        /** Jogo da Kings: os acompanhamentos dos técnicos e o time de cada um (duelo: o do time A primeiro). */
        public final List<AcompanhamentoKings> ladosKings = new ArrayList<>();
        public final List<String> timesKings = new ArrayList<>();
        /** Jogo da Kings League (7x7)? */
        public boolean kings;
        private volatile int chave = -1, liberado = -1, ordemPedido;
        private volatile boolean esperando, pedindo, terminado;
        /** Lances já olhados para o suspense do pênalti; espera do suspense ainda não dada pelo relógio (ms). */
        private int lancesVistosPenalti;
        private long suspensePendente;

        Jogo(String mandanteId, String visitanteId) {
            this.mandanteId = mandanteId;
            this.visitanteId = visitanteId;
        }

        private void adicionar(String clubeId) {
            AcompanhamentoPartida a = fabrica.apply(clubeId);
            if (a != null) lados.add(a);
        }

        private void adicionarKings(String timeId, int lado, java.util.function.BiFunction<String, Integer, AcompanhamentoKings> f) {
            AcompanhamentoKings a = f.apply(timeId, lado);
            if (a == null) return;
            a.velocidade(0); // quem segura o tempo é a mesa
            ladosKings.add(a);
            timesKings.add(timeId);
        }

        /** Duelo entre dois técnicos humanos? */
        public boolean duelo() { return lados.size() > 1 || ladosKings.size() > 1; }

        // ---------------------------------------------- Kings (AoVivoKings)
        @Override
        public void minuto(br.futebas.kings.AoVivoKings.ControleKings c) {
            for (AcompanhamentoKings a : ladosKings) a.minuto(c);
            chegar(this, chaveKings(c), null);
        }

        @Override
        public void pausa(br.futebas.kings.AoVivoKings.ControleKings c, String motivo) {
            if ("fim".equals(motivo)) return; // o fim não espera ninguém no multi (a tela mostra o placar final)
            if ("intervalo".equals(motivo)) {
                // como no futebol: um técnico de cada vez, na ordem (duelo: o time A, depois o B)
                for (int i = 0; i < ladosKings.size(); i++) {
                    if (pulando || !pedirVez(this, ordem.applyAsInt(timesKings.get(i)))) return;
                    try {
                        ladosKings.get(i).pausa(c, motivo);
                    } finally {
                        devolverVez(this);
                    }
                }
                return;
            }
            for (AcompanhamentoKings a : ladosKings) a.pausa(c, motivo); // carta, dado, match ball: só o letreiro
        }

        @Override
        public List<String> ordemShootout(int lado, List<br.futebas.kings.LigaKings.AtletaKings> sugestao) {
            if (pulando) return null;
            for (AcompanhamentoKings a : ladosKings) if (a.meuLado() == lado) return a.ordemShootout(lado, sugestao);
            return null;
        }

        @Override
        public void cobranca(br.futebas.kings.AoVivoKings.ControleKings c, br.futebas.kings.AoVivoKings.Cobranca k) {
            // sem espera (testes, instantânea) a cobrança não segura o motor pelo suspense
            if (velocidade == Velocidade.INSTANTANEA || pulando) return;
            for (AcompanhamentoKings a : ladosKings) a.cobranca(c, k);
        }

        public boolean terminado() { return terminado; }

        private AcompanhamentoPartida doLado(int lado) {
            for (AcompanhamentoPartida a : lados) if (a.meuLado() == lado) return a;
            return null;
        }

        @Override
        public void minuto(Controle c) {
            for (AcompanhamentoPartida a : lados) a.minutoMulti(c);
            chegar(this, chave(c), c);
        }

        @Override
        public void pausa(Controle c, Pausa motivo, int lado, Integer jogadorId) {
            AcompanhamentoPartida a = doLado(lado);
            if (a == null || pulando) return;
            if (!pedirVez(this, ordem.applyAsInt(a.clubeId()))) return;
            try {
                a.pausa(c, motivo, lado, jogadorId);
            } finally {
                devolverVez(this);
            }
        }

        @Override
        public Integer escolherBatedor(Controle c, int lado, List<Escalado> candidatos) {
            AcompanhamentoPartida a = doLado(lado);
            if (a == null || pulando) return null;
            if (!pedirVez(this, ordem.applyAsInt(a.clubeId()))) return null;
            try {
                return a.escolherBatedor(c, lado, candidatos);
            } finally {
                devolverVez(this);
            }
        }

        @Override
        public List<Integer> ordemPenaltis(int lado, List<Escalado> sugestao) {
            // depois do apito final (o grupo já acabou): cada técnico escolhe a dele, um depois do outro
            AcompanhamentoPartida a = doLado(lado);
            return a == null || pulando ? null : a.ordemPenaltis(lado, sugestao);
        }

        @Override
        public void disputaPenaltis(ResultadoPartida r, List<br.futebas.partida.Lance> cobrancas) {
            // quem narra o jogo (no duelo, os dois veem a mesma parte da tela)
            if (!lados.isEmpty()) lados.get(0).disputaPenaltis(r, cobrancas);
        }

        @Override
        public void fim(ResultadoPartida r) {
            for (AcompanhamentoPartida a : lados) a.fim(r);
            synchronized (MesaAoVivo.this) {
                terminado = true;
            }
        }

        @Override
        public void outrosJogos(List<JogoParalelo> jogos) {
            for (AcompanhamentoPartida a : lados) a.outrosJogos(jogos);
        }
    }
}
