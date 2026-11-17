package br.futebas.ui.modelo;

import br.futebas.kings.AoVivoKings;
import br.futebas.kings.LigaKings;
import br.futebas.kings.MotorKings;
import br.futebas.partida.EventoJogo;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Ponte entre o motor da Kings League e a tela: a cada minuto publica uma foto do jogo, aplica as
 * ordens do técnico (carta secreta, pênalti do presidente) e espera o tempo da velocidade.
 * Regra do jogo (12/11/2026, "não precisa pausar toda hora, só no intervalo"): só o intervalo (e o
 * fim, para a tela de fim de jogo) esperam o técnico; carta, dado, match ball e shootouts aparecem
 * como letreiro e o jogo segue. Cada cobrança (pênalti, shootout, presidente, disputa) segura o
 * motor pelo tempo do suspense ({@link #cobranca}), sem esperar clique.
 */
public final class AcompanhamentoKings implements AoVivoKings {

    /** Foto imutável do jogo da Kings. */
    public record Estado(int minuto, int golsA, int golsB, int emCampoA, int emCampoB, String fase, String dado,
                         List<EventoJogo> eventos, MotorKings.Carta minhaCarta, boolean podeUsarCarta, boolean podePresidente,
                         String pausa, boolean primeiro, List<LigaKings.AtletaKings> meusEmCampo, List<LigaKings.AtletaKings> rivaisEmCampo,
                         AoVivoKings.Cobranca cobranca) {}

    /** Pausas que esperam o técnico; as outras só avisam a tela. */
    static boolean esperaTecnico(String motivo) {
        return "intervalo".equals(motivo) || "fim".equals(motivo);
    }

    private volatile int meuLado;
    private final Consumer<Estado> ouvinte;
    private final ConcurrentLinkedQueue<Consumer<ControleKings>> ordens = new ConcurrentLinkedQueue<>();
    /**
     * Ritmo do jogo da Kings: o jogo tem ~40 minutos (36 + match ball), então cada minuto dura
     * 90/40 do minuto da partida normal na velocidade das Opções — o jogo inteiro leva o mesmo tempo.
     */
    private volatile int msPorMinuto = msKings();

    /** Ritmo da Kings no modo automático (capturas): o roteiro do portfólio desacelera para gravar o jogo. */
    public static volatile int msAutomatico = 150;

    private static int msKings() {
        if (br.futebas.ui.App.automatico) return msAutomatico;
        return ms(br.futebas.ui.Opcoes.velocidadeKings());
    }

    /** ms por minuto da Kings numa velocidade (velocidade própria da Kings, botões na tela do jogo). */
    public static int ms(AcompanhamentoPartida.Velocidade v) {
        return (int) Math.round(v.msPorMinuto * 90.0 / 40.0);
    }
    private volatile boolean pausado, pular;
    /** Shoot out: quem mostra a escolha da ordem (null = automática) e a resposta. */
    private volatile Consumer<List<LigaKings.AtletaKings>> aoPedirOrdem;
    private volatile List<String> ordem;
    private volatile boolean ordemRespondida;

    /** Quem mostra a escolha da ordem do shoot out (chamado na thread do motor; responda com {@link #responderOrdem}). */
    public void aoPedirOrdem(Consumer<List<LigaKings.AtletaKings>> c) { aoPedirOrdem = c; }

    /** Resposta à ordem do shoot out: as chaves na ordem (null = a automática). */
    public void responderOrdem(List<String> chaves) {
        ordem = chaves == null ? null : List.copyOf(chaves);
        ordemRespondida = true;
    }

    @Override
    public List<String> ordemShootout(int lado, List<LigaKings.AtletaKings> sugestao) {
        Consumer<List<LigaKings.AtletaKings>> mostrar = aoPedirOrdem;
        // simular até o fim, sem tela ou lado de outro técnico: automática
        if (pular || mostrar == null || lado != meuLado || sugestao == null || sugestao.isEmpty()) return null;
        ordemRespondida = false;
        ordem = null;
        mostrar.accept(List.copyOf(sugestao));
        while (!ordemRespondida && !pular) dormir(40);
        return ordemRespondida ? ordem : null;
    }
    private boolean comecou;

    public AcompanhamentoKings(int meuLado, Consumer<Estado> ouvinte) {
        this.meuLado = meuLado;
        this.ouvinte = ouvinte;
    }

    public int meuLado() { return meuLado; }

    /** Um jogo novo começa (o lado do técnico pode mudar). */
    public void novoJogo(int lado) {
        meuLado = lado;
        comecou = false;
        pular = false;
        pausado = false;
        ordens.clear();
    }

    public void velocidade(int ms) { msPorMinuto = ms; }

    /** Segue o jogo depois de uma pausa. */
    public void seguir() { pausado = false; }

    /** Roda o resto sem esperas. */
    public void pularParaOFim() {
        pular = true;
        pausado = false;
    }

    public void usarCarta(MotorKings.Carta coringaVira) {
        usarCarta(coringaVira, null);
    }

    /** {@code escolha}: chave do atleta (jogador estrela: um seu; suspensão e pênalti invertido: um de linha do rival); null = automático. */
    public void usarCarta(MotorKings.Carta coringaVira, String escolha) {
        ordens.add(c -> c.usarCarta(meuLado, coringaVira, escolha));
    }

    /** Esperas do suspense de uma cobrança ({vai para a bola, bateu, resultado}); zero ao pular. */
    public long[] esperasSuspense() {
        if (pular || br.futebas.ui.App.automatico) return new long[] {0, 0, 0};
        return br.futebas.ui.Opcoes.velocidade().suspensePenalti();
    }

    public boolean pulando() { return pular; }

    @Override
    public void cobranca(ControleKings c, AoVivoKings.Cobranca k) {
        aplicar(c);
        publicar(c, null, k);
        long[] ms = esperasSuspense();
        long fim = System.currentTimeMillis() + ms[0] + ms[1] + ms[2];
        while (!pular && System.currentTimeMillis() < fim) dormir(30);
    }

    /** Substituição do técnico (ilimitada; quem sai pode voltar). Aplicada no próximo minuto (ou já, se pausado). */
    public void substituir(String sai, String entra) {
        ordens.add(c -> c.substituir(meuLado, sai, entra));
    }

    /** Pausa enquanto a tela de trocas está aberta (como no futebol); {@code false} retoma. */
    public void pausarTrocas(boolean sim) {
        trocasAbertas = sim;
    }

    private volatile boolean trocasAbertas;

    /** Foto atual de quem está em campo e no banco do seu time, com a energia (para a tela de trocas). */
    public volatile List<Object[]> elencoAoVivo = List.of();

    public void cobrarPenaltiPresidente() {
        ordens.add(c -> c.cobrarPenaltiPresidente(meuLado));
    }

    @Override
    public void minuto(ControleKings c) {
        aplicar(c);
        publicar(c, null);
        if (!pular) dormir(msPorMinuto);
        // tela de trocas aberta: o jogo espera (as trocas entram na hora)
        while (trocasAbertas && !pular) {
            dormir(40);
            if (!ordens.isEmpty()) {
                aplicar(c);
                publicar(c, null);
            }
        }
    }

    @Override
    public void pausa(ControleKings c, String motivo) {
        aplicar(c);
        if (pular && !"fim".equals(motivo)) return;
        if (!esperaTecnico(motivo)) {
            // carta, dado, match ball, shootouts: só o letreiro, o jogo segue
            publicar(c, motivo);
            return;
        }
        pausado = true;
        publicar(c, motivo);
        long limite = Long.MAX_VALUE;
        while (pausado && System.currentTimeMillis() < limite) {
            dormir(40);
            if (!ordens.isEmpty()) {
                aplicar(c);
                publicar(c, motivo);
            }
        }
        pausado = false;
        publicar(c, null);
    }

    private void aplicar(ControleKings c) {
        Consumer<ControleKings> o;
        while ((o = ordens.poll()) != null) o.accept(c);
    }

    private void publicar(ControleKings c, String pausa) {
        publicar(c, pausa, null);
    }

    /** Os números do jogo até a última foto publicada (null antes do apito). */
    private volatile br.futebas.partida.Estatisticas estatisticas;

    public br.futebas.partida.Estatisticas estatisticas() { return estatisticas; }

    private void publicar(ControleKings c, String pausa, AoVivoKings.Cobranca k) {
        estatisticas = c.numerosAoVivo();
        int a = meuLado < 0 ? 0 : meuLado;
        Estado e = new Estado(c.minuto(), c.gols(0), c.gols(1), c.emCampo(0), c.emCampo(1), c.fase(), c.dado(), List.copyOf(c.eventos()),
                c.carta(a), c.podeUsarCarta(a), c.podePenaltiPresidente(a), pausa, !comecou, c.emCampoAgora(a), c.emCampoAgora(1 - a), k);
        comecou = true;
        List<Object[]> foto = new java.util.ArrayList<>();
        for (LigaKings.AtletaKings x : c.emCampoAgora(a)) foto.add(new Object[]{x, true, c.energiaDe(x)});
        for (LigaKings.AtletaKings x : c.bancoAgora(a)) foto.add(new Object[]{x, false, c.energiaDe(x)});
        elencoAoVivo = foto;
        if (ouvinte != null) ouvinte.accept(e);
    }

    private static void dormir(int ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
