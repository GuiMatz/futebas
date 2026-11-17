package br.futebas.ui.telas;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.partida.Concentracao;
import br.futebas.partida.Escalado;
import br.futebas.partida.Escalador;
import br.futebas.partida.Estilo;
import br.futebas.partida.Marcacao;
import br.futebas.partida.TimePartida;
import br.futebas.ui.App;
import br.futebas.ui.Sessao;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.AcompanhamentoPartida.Atleta;
import br.futebas.ui.modelo.AcompanhamentoPartida.Estado;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Bug corrigido: "cada vez que eu troco alguém no intervalo a tela da escalação vai ficando menor".
 * Cinco substituições (e cartão amarelo) na camada de trocas: a escala e o tamanho do campo não mudam.
 */
class TrocasAoVivoTest {
    private static boolean fx;

    @BeforeAll
    static void iniciarFx() throws Exception {
        CountDownLatch l = new CountDownLatch(1);
        try {
            Platform.startup(l::countDown);
            fx = l.await(10, TimeUnit.SECONDS);
        } catch (IllegalStateException jaIniciado) {
            fx = true;
        } catch (Throwable semTela) {
            fx = false;
        }
    }

    private static Atleta atleta(Escalado e, boolean amarelo, int energia) {
        return new Atleta(e.id, e.nome, e.natural, e.vaga, (int) Math.round(e.forca), amarelo, energia);
    }

    private static Estado estado(List<Atleta> vagas, List<Atleta> banco, Set<Integer> sairam, List<Atleta> fora, int subs) {
        return new Estado(subs + 1, false, false, 46, 0, true, "A", "B", "a", "b", 0, 0, List.of(), 0,
                List.copyOf(vagas), List.copyOf(banco), Set.copyOf(sairam), List.copyOf(vagas), List.copyOf(fora), java.util.Map.of(),
                subs, 5, Estilo.EQUILIBRADO, Marcacao.NORMAL, Concentracao.EQUILIBRADO, null, List.of(), null);
    }

    /** Rodapé da partida (requisito): a 1366 e a 1920 px nenhum botão corta ou quebra. */
    @Test
    void rodapeCabeEm1366E1920() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        Jogo j = Jogo.novoJogo(Fixture.banco(), 32, "bra-1-01", "Técnico");
        Sessao s = new Sessao(j, "teste");
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                for (double largura : new double[] {1366, 1920}) {
                    TelaPartida t = new TelaPartida(s);
                    Scene cena = new Scene(t.raiz(), largura, 768);
                    cena.getStylesheets().add(App.class.getResource("/br/futebas/ui/tema.css").toExternalForm());
                    cena.getRoot().applyCss();
                    cena.getRoot().layout();
                    double direita = 0;
                    for (javafx.scene.control.Control c : t.controlesRodape()) {
                        assertTrue(c.getWidth() >= c.prefWidth(-1) - 0.5, largura + " px: \"" + ((javafx.scene.control.Labeled) c).getText()
                                + "\" cortado (" + c.getWidth() + " < " + c.prefWidth(-1) + ")");
                        assertTrue(c.getHeight() >= 30, "altura do botão: " + c.getHeight());
                        javafx.geometry.Bounds b = c.localToScene(c.getBoundsInLocal());
                        direita = Math.max(direita, b.getMaxX());
                    }
                    assertTrue(direita <= largura, largura + " px: o rodapé passa da tela (" + direita + ")");
                }
            } catch (Throwable x) {
                erro.set(x);
            } finally {
                fim.countDown();
            }
        });
        assertTrue(fim.await(120, TimeUnit.SECONDS));
        if (erro.get() != null) throw new AssertionError(erro.get());
    }

    /** Bug corrigido: todo mundo que saiu aparecia "expulso". Expulso fica na vaga; substituído dá a vaga a quem entrou. */
    @Test
    void substituidoNaoApareceComoExpulso() {
        Atleta x = new Atleta(1, "Expulso", br.futebas.mundo.Posicao.ZAG, br.futebas.mundo.Posicao.ZAG, 70, false, 80);
        Atleta y = new Atleta(2, "Trocado", br.futebas.mundo.Posicao.CA, br.futebas.mundo.Posicao.CA, 70, false, 70);
        Atleta z = new Atleta(3, "Entrou", br.futebas.mundo.Posicao.CA, br.futebas.mundo.Posicao.CA, 68, false, 100);
        Estado e = new Estado(1, false, false, 70, 0, false, "A", "B", "a", "b", 0, 0, List.of(), 0,
                List.of(z), List.of(), Set.of(1, 2), List.of(x, z), List.of(x, y), java.util.Map.of(2, "substituído aos 62' por Entrou"),
                1, 5, Estilo.EQUILIBRADO, Marcacao.NORMAL, Concentracao.EQUILIBRADO, null, List.of(), null);
        assertTrue(e.expulso(1));
        assertFalse(e.expulso(2));
        assertEquals("expulso", e.comoSaiu(1));
        assertEquals("substituído aos 62' por Entrou", e.comoSaiu(2));
    }

    @Test
    void campoNaoEncolheACadaTroca() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        Jogo j = Jogo.novoJogo(Fixture.banco(), 31, "bra-1-01", "Técnico");
        Sessao s = new Sessao(j, "teste");
        TimePartida tp = Escalador.montar(j.mundo(), j.meuClube(), 5, 12);
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TrocasAoVivo t = new TrocasAoVivo(s, () -> null);
                // como na TelaPartida: a caixa sobreposta com o tamanho do conteúdo
                VBox caixa = new VBox(12, Ui.rotulo("Intervalo"), t.raiz(), t.botaoSubstituir());
                caixa.setMaxSize(1100, Region.USE_PREF_SIZE);
                Scene cena = new Scene(new StackPane(caixa), 1280, 800);
                cena.getStylesheets().add(App.class.getResource("/br/futebas/ui/tema.css").toExternalForm());
                List<Atleta> vagas = new ArrayList<>(), banco = new ArrayList<>(), fora = new ArrayList<>();
                for (Escalado e : tp.titulares) vagas.add(atleta(e, false, 80));
                for (Escalado e : tp.reservas) banco.add(atleta(e, false, 100));
                Set<Integer> sairam = new HashSet<>();
                Runnable mostrar = () -> {
                    cena.getRoot().applyCss();
                    cena.getRoot().layout();
                    cena.getRoot().layout();
                };
                t.atualizar(estado(vagas, banco, sairam, fora, 0));
                mostrar.run();
                double k = t.escalaCampo(), w = t.larguraCampo(), h = t.alturaCampo();
                assertTrue(k > 0.4 && w > 200 && h > 200, "campo visível: " + k + " " + w + "x" + h);
                // requisito: o banco inteiro à vista (até 12 reservas sem rolar)
                assertEquals(12, banco.size(), "banco de 12 no teste");
                assertTrue(t.bancoCabeSemRolar(), "12 reservas sem barra de rolagem");
                for (int n = 1; n <= 5; n++) {
                    // sai o da vaga n, entra o reserva n; o da vaga 10 leva amarelo
                    Atleta sai = vagas.get(n), entra = banco.remove(0);
                    vagas.set(n, new Atleta(entra.id(), entra.nome(), entra.natural(), sai.vaga(), entra.forca(), false, entra.energia()));
                    vagas.set(10, new Atleta(vagas.get(10).id(), vagas.get(10).nome(), vagas.get(10).natural(), vagas.get(10).vaga(),
                            vagas.get(10).forca(), true, 70 - n));
                    sairam.add(sai.id());
                    fora.add(sai);
                    for (int r = 0; r < 3; r++) {
                        t.atualizar(estado(vagas, banco, sairam, fora, n));
                        mostrar.run();
                    }
                    assertEquals(k, t.escalaCampo(), 1e-9, "escala depois da troca " + n);
                    assertEquals(w, t.larguraCampo(), 1e-9, "largura depois da troca " + n);
                    assertEquals(h, t.alturaCampo(), 1e-9, "altura depois da troca " + n);
                    Estado agora = estado(vagas, banco, sairam, fora, n);
                    for (Atleta x : fora) assertFalse(agora.expulso(x.id()), "substituído não é expulso: " + x.nome());
                }
            } catch (Throwable x) {
                erro.set(x);
            } finally {
                fim.countDown();
            }
        });
        assertTrue(fim.await(120, TimeUnit.SECONDS));
        if (erro.get() != null) throw new AssertionError(erro.get());
    }

    /** Controle de mentira: grava as ordens que chegam ao motor. */
    static final class ControleGravador implements br.futebas.partida.AoVivo.Controle {
        final List<String> ordens = new ArrayList<>();
        final TimePartida meu, outro;

        ControleGravador(TimePartida meu, TimePartida outro) {
            this.meu = meu;
            this.outro = outro;
        }

        @Override public int minuto() { return 46; }
        @Override public int acrescimo() { return 0; }
        @Override public boolean intervalo() { return true; }
        @Override public boolean prorrogacao() { return false; }
        @Override public int gols(int lado) { return 0; }
        @Override public List<br.futebas.partida.Lance> lances() { return new ArrayList<>(); }
        @Override public TimePartida time(int lado) { return lado == 0 ? meu : outro; }
        @Override public List<Escalado> emCampo(int lado) { return lado == 0 ? meu.titulares : outro.titulares; }
        @Override public List<Escalado> banco(int lado) { return lado == 0 ? meu.reservas : outro.reservas; }
        @Override public int substituicoesFeitas(int lado) { return 0; }
        @Override public int substituicoesPermitidas() { return 5; }
        @Override public Estilo estilo(int lado) { return Estilo.EQUILIBRADO; }
        @Override public Set<Integer> sairam(int lado) { return Set.of(); }
        @Override public boolean substituir(int lado, int saiId, int entraId) { ordens.add("sub " + saiId + ">" + entraId); return true; }
        @Override public boolean trocarPosicoes(int lado, int a, int b) { ordens.add("troca " + a + "<>" + b); return true; }
        @Override public void mudarEstilo(int lado, Estilo estilo) {}
        @Override public List<br.futebas.partida.EventoJogo> eventos() { return List.of(); }
        @Override public Marcacao marcacao(int lado) { return Marcacao.NORMAL; }
        @Override public void mudarMarcacao(int lado, Marcacao marcacao) {}
        @Override public Concentracao concentracao(int lado) { return Concentracao.EQUILIBRADO; }
        @Override public void mudarConcentracao(int lado, Concentracao concentracao) {}
        @Override public void assumirControle(int lado) {}
        @Override public boolean mudarFormacao(int lado, String formacao) { ordens.add("formacao " + formacao); return true; }
    }

    /**
     * Requisito: mudar a formação no meio do jogo, preparada como as trocas (vale no Confirmar),
     * sem gastar substituição; o campo já mostra cada um na vaga nova.
     */
    @Test
    void formacaoPreparadaVaiNoConfirmar() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        Jogo j = Jogo.novoJogo(Fixture.banco(), 34, "bra-1-01", "Técnico");
        Sessao s = new Sessao(j, "teste");
        TimePartida tp = Escalador.montar(j.mundo(), j.meuClube(), 5, 12);
        TimePartida adv = Escalador.montarAutomatico(j.mundo(), j.mundo().clube("bra-1-02"), 5);
        br.futebas.ui.modelo.AcompanhamentoPartida acomp = new br.futebas.ui.modelo.AcompanhamentoPartida(j.meuClube().id,
                br.futebas.ui.modelo.AcompanhamentoPartida.Velocidade.INSTANTANEA, null);
        ControleGravador ctl = new ControleGravador(tp, adv);
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TrocasAoVivo t = new TrocasAoVivo(s, () -> acomp);
                List<Atleta> vagas = new ArrayList<>(), banco = new ArrayList<>();
                for (Escalado e : tp.titulares) vagas.add(atleta(e, false, 80));
                for (Escalado e : tp.reservas) banco.add(atleta(e, false, 100));
                t.atualizar(estado(vagas, banco, Set.of(), List.of(), 0));
                String nova = j.meuClube().tatica.formacao.equals("3-5-2") ? "4-3-3" : "3-5-2";
                assertTrue(t.prepararTroca(vagas.get(1).id(), vagas.get(2).id()));
                assertTrue(t.prepararFormacao(nova));
                assertTrue(t.temPendentes());
                assertTrue(t.pendentes().isEmpty(), "trocas de posição da formação antiga saem");
                br.futebas.mundo.Posicao[] exib = t.vagasExibidas().stream().map(Atleta::vaga).toArray(br.futebas.mundo.Posicao[]::new);
                assertArrayEquals(br.futebas.partida.Formacoes.vagas(nova), exib, "o campo mostra a formação nova");
                assertEquals(vagas.size(), t.vagasExibidas().stream().map(Atleta::id).distinct().count(), "todos continuam em campo");
                assertFalse(t.prepararFormacao("nao-existe"));
                acomp.minuto(ctl);
                assertTrue(ctl.ordens.isEmpty(), "nada vai ao motor antes do Confirmar");
                assertTrue(t.preparar(vagas.get(5).id(), banco.get(0).id()), "a substituição continua possível");
                t.confirmar();
                assertFalse(t.temPendentes());
                assertEquals(nova, t.formacaoAtual());
                acomp.minuto(ctl);
                assertEquals("formacao " + nova, ctl.ordens.get(0), "a formação vai primeiro: " + ctl.ordens);
                assertEquals(2, ctl.ordens.size());
            } catch (Throwable x) {
                erro.set(x);
            } finally {
                fim.countDown();
            }
        });
        assertTrue(fim.await(120, TimeUnit.SECONDS));
        if (erro.get() != null) throw new AssertionError(erro.get());
    }

    /** Regra do jogo: "a troca só acontece depois que confirma". Preparar não manda nada ao motor; confirmar manda tudo, na ordem. */
    @Test
    void trocaSoDepoisDeConfirmar() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        Jogo j = Jogo.novoJogo(Fixture.banco(), 33, "bra-1-01", "Técnico");
        Sessao s = new Sessao(j, "teste");
        TimePartida tp = Escalador.montar(j.mundo(), j.meuClube(), 5, 12);
        tp = new TimePartida(j.meuClube().id, j.meuClube().nome, tp.titulares, tp.reservas);
        TimePartida adv = Escalador.montarAutomatico(j.mundo(), j.mundo().clube("bra-1-02"), 5);
        br.futebas.ui.modelo.AcompanhamentoPartida acomp = new br.futebas.ui.modelo.AcompanhamentoPartida(j.meuClube().id,
                br.futebas.ui.modelo.AcompanhamentoPartida.Velocidade.INSTANTANEA, null);
        ControleGravador ctl = new ControleGravador(tp, adv);
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch fim = new CountDownLatch(1);
        TimePartida meu = tp;
        Platform.runLater(() -> {
            try {
                TrocasAoVivo t = new TrocasAoVivo(s, () -> acomp);
                List<Atleta> vagas = new ArrayList<>(), banco = new ArrayList<>();
                for (Escalado e : meu.titulares) vagas.add(atleta(e, false, 80));
                for (Escalado e : meu.reservas) banco.add(atleta(e, false, 100));
                t.atualizar(estado(vagas, banco, Set.of(), List.of(), 0));
                int sai = vagas.get(3).id(), entra = banco.get(0).id();
                assertTrue(t.preparar(sai, entra));
                assertTrue(t.prepararTroca(vagas.get(1).id(), vagas.get(2).id()));
                assertEquals(2, t.pendentes().size());
                assertEquals(entra, t.vagasExibidas().get(3).id(), "o reserva aparece na vaga (pendente)");
                assertFalse(t.bancoExibido().stream().anyMatch(a -> a.id() == entra));
                // nada foi para o motor ainda
                acomp.minuto(ctl);
                assertTrue(ctl.ordens.isEmpty(), "preparar não troca: " + ctl.ordens);
                // desfazer limpa
                t.descartar();
                assertFalse(t.temPendentes());
                assertEquals(sai, t.vagasExibidas().get(3).id());
                // limite: 4 feitas + 1 pendente = 5; a 2ª não cabe
                t.atualizar(estado(vagas, banco, Set.of(), List.of(), 4));
                assertTrue(t.preparar(sai, entra));
                assertFalse(t.preparar(vagas.get(4).id(), banco.get(1).id()), "não passa do limite");
                t.descartar();
                // confirmar: as trocas chegam ao motor, na ordem
                t.atualizar(estado(vagas, banco, Set.of(), List.of(), 0));
                t.preparar(sai, entra);
                t.prepararTroca(vagas.get(1).id(), vagas.get(2).id());
                t.confirmar();
                assertFalse(t.temPendentes());
                acomp.minuto(ctl);
                assertEquals(List.of("sub " + sai + ">" + entra, "troca " + vagas.get(1).id() + "<>" + vagas.get(2).id()), ctl.ordens);
            } catch (Throwable x) {
                erro.set(x);
            } finally {
                fim.countDown();
            }
        });
        assertTrue(fim.await(120, TimeUnit.SECONDS));
        if (erro.get() != null) throw new AssertionError(erro.get());
    }

    /**
     * Requisitos: o expulso não sai nem é substituído, mas alguém em campo pode ir para a vaga dele;
     * trocar de lugar no campo não gasta substituição (nem com as 5 já feitas); trocar de ideia sobre o
     * reserva de uma substituição preparada não gasta outra.
     */
    @Test
    void expulsoRecebeRemanejamentoSemGastarTroca() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        Jogo j = Jogo.novoJogo(Fixture.banco(), 35, "bra-1-01", "Técnico");
        Sessao s = new Sessao(j, "teste");
        TimePartida tp = Escalador.montar(j.mundo(), j.meuClube(), 5, 12);
        tp = new TimePartida(j.meuClube().id, j.meuClube().nome, tp.titulares, tp.reservas);
        TimePartida adv = Escalador.montarAutomatico(j.mundo(), j.mundo().clube("bra-1-02"), 5);
        br.futebas.ui.modelo.AcompanhamentoPartida acomp = new br.futebas.ui.modelo.AcompanhamentoPartida(j.meuClube().id,
                br.futebas.ui.modelo.AcompanhamentoPartida.Velocidade.INSTANTANEA, null);
        ControleGravador ctl = new ControleGravador(tp, adv);
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch fim = new CountDownLatch(1);
        TimePartida meu = tp;
        Platform.runLater(() -> {
            try {
                TrocasAoVivo t = new TrocasAoVivo(s, () -> acomp);
                List<Atleta> vagas = new ArrayList<>(), banco = new ArrayList<>();
                for (Escalado e : meu.titulares) vagas.add(atleta(e, false, 80));
                for (Escalado e : meu.reservas) banco.add(atleta(e, false, 100));
                Atleta expulso = vagas.get(2), meia = vagas.get(6), outro = vagas.get(7);
                List<Atleta> emCampo = new ArrayList<>(vagas);
                emCampo.remove(expulso);
                java.util.function.IntFunction<Estado> est = subs -> new Estado(subs + 10, false, false, 60, 0, false, "A", "B", "a", "b", 0, 0,
                        List.of(), 0, List.copyOf(emCampo), List.copyOf(banco), Set.of(expulso.id()), List.copyOf(vagas), List.of(expulso),
                        java.util.Map.of(), subs, 5, Estilo.EQUILIBRADO, Marcacao.NORMAL, Concentracao.EQUILIBRADO, null, List.of(), null);
                Estado e0 = est.apply(0);
                assertTrue(e0.expulso(expulso.id()));
                t.atualizar(e0);
                // o expulso não sai (nem um reserva entra no lugar dele)
                assertFalse(t.preparar(expulso.id(), banco.get(0).id()), "não substitui o expulso");
                // trocar de ideia: o reserva preparado sai antes de entrar = outra escolha, não outra troca
                assertTrue(t.preparar(outro.id(), banco.get(0).id()));
                assertTrue(t.preparar(banco.get(0).id(), banco.get(1).id()));
                assertEquals(List.of(new TrocasAoVivo.Mudanca(true, outro.id(), banco.get(1).id())), t.pendentes());
                assertTrue(t.bancoExibido().stream().anyMatch(a -> a.id() == banco.get(0).id()), "o primeiro reserva volta ao banco");
                t.descartar();
                // com as 5 substituições feitas: ninguém entra, mas o meia ainda vai para a vaga do expulso
                t.atualizar(est.apply(5));
                assertFalse(t.cabeSubstituicao());
                assertFalse(t.preparar(meia.id(), banco.get(0).id()), "sem trocas sobrando");
                assertTrue(t.prepararTroca(meia.id(), expulso.id()), "remaneja para a vaga do expulso");
                assertEquals(expulso.vaga(), t.vagasExibidas().get(2).vaga());
                assertEquals(meia.id(), t.vagasExibidas().get(2).id(), "o meia aparece na vaga do expulso");
                assertEquals(expulso.id(), t.vagasExibidas().get(6).id(), "o buraco vai para a vaga do meia");
                assertFalse(t.prepararTroca(expulso.id(), expulso.id()));
                t.confirmar();
                acomp.minuto(ctl);
                assertEquals(List.of("troca " + meia.id() + "<>" + expulso.id()), ctl.ordens, "só a troca de posição, nenhuma substituição");
            } catch (Throwable x) {
                erro.set(x);
            } finally {
                fim.countDown();
            }
        });
        assertTrue(fim.await(120, TimeUnit.SECONDS));
        if (erro.get() != null) throw new AssertionError(erro.get());
    }

    /** Kings (mesma tela): 7 em campo, trocas ilimitadas, sem troca de posição; Confirmar manda tudo para a fonte. */
    @Test
    void kingsIlimitadasComAMesmaTela() throws Exception {
        assumeTrue(fx, "JavaFX indisponível neste ambiente");
        Jogo j = Jogo.novoJogo(Fixture.banco(), 34, "bra-1-01", "Técnico");
        Sessao s = new Sessao(j, "teste");
        List<TrocasAoVivo.Mudanca> aplicadas = new ArrayList<>();
        AtomicReference<Throwable> erro = new AtomicReference<>();
        CountDownLatch fim = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                boolean[] fechou = {false};
                TrocasAoVivo t = new TrocasAoVivo(s, new TrocasAoVivo.Fonte() {
                    @Override public void aplicar(List<TrocasAoVivo.Mudanca> ms) { aplicadas.addAll(ms); }
                    @Override public boolean trocaDePosicao() { return false; }
                    @Override public javafx.scene.Node foto(int id, double w, double h) { return new javafx.scene.layout.Region(); }
                    @Override public double encaixe(int id, br.futebas.mundo.Posicao n, br.futebas.mundo.Posicao v) { return 1; }
                });
                t.aoConfirmar(() -> fechou[0] = true);
                br.futebas.mundo.Posicao[] ps = {br.futebas.mundo.Posicao.GOL, br.futebas.mundo.Posicao.ZAG, br.futebas.mundo.Posicao.ZAG,
                        br.futebas.mundo.Posicao.MC, br.futebas.mundo.Posicao.MC, br.futebas.mundo.Posicao.MC, br.futebas.mundo.Posicao.CA};
                List<Atleta> campo = new ArrayList<>(), banco = new ArrayList<>();
                for (int i = 0; i < 7; i++) campo.add(new Atleta(i, "K" + i, ps[i], ps[i], 60, false, 70));
                for (int i = 7; i < 12; i++) banco.add(new Atleta(i, "K" + i, br.futebas.mundo.Posicao.MC, br.futebas.mundo.Posicao.MC, 58, false, 100));
                t.atualizar(new Estado(1, false, false, 10, 0, false, "A", "B", "a", "b", 0, 0, List.of(), 0, campo, banco, Set.of(), campo,
                        List.of(), java.util.Map.of(), 0, Integer.MAX_VALUE, null, null, null, null, List.of(), null));
                for (int i = 0; i < 5; i++) assertTrue(t.preparar(i + 1, 7 + i), "ilimitadas: " + i);
                assertFalse(t.prepararTroca(0, 6), "sem troca de posição na Kings");
                t.botaoSubstituir().fire();
                assertTrue(fechou[0], "Confirmar aplica e fecha");
                assertEquals(5, aplicadas.size());
                assertFalse(t.temPendentes());
            } catch (Throwable x) {
                erro.set(x);
            } finally {
                fim.countDown();
            }
        });
        assertTrue(fim.await(120, TimeUnit.SECONDS));
        if (erro.get() != null) throw new AssertionError(erro.get());
    }
}
