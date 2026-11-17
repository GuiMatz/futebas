package br.futebas.ui;

import br.futebas.Jogo;
import br.futebas.dados.BancoMundo;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.AcompanhamentoKings;
import br.futebas.ui.modelo.AcompanhamentoPartida;
import br.futebas.ui.telas.Hub;
import br.futebas.ui.telas.TelaNovoJogo;
import br.futebas.ui.telas.TelaPartida;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.text.Text;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Trailer "estilo FM" ({@code -Dfutebas.capturas.fm=true}): grava o jogo de verdade, em 1920×1080,
 * como uma pessoa usando — o cursor (virtual) anda até o alvo, para um instante e clica (eventos de
 * mouse de verdade na cena), e a tela fica parada um pouco para dar para ler. Cada cena vai para uma
 * subpasta com os quadros JPG (~20 por segundo), {@code times.json} ([["00000.jpg", s], ...]),
 * {@code clicks.json} ([[s, x, y, "tipo"], ...]) e {@code moves.json} ([[s, x, y], ...], a cada ~50 ms),
 * para desenhar o cursor depois. Os menus suspensos (janelas de popup) entram no quadro. Prints em
 * {@code prints/}. Cenas: {@code -Dfutebas.capturas.cenas=1,2,...} (padrão: todas). Nunca usa os
 * times customizados.
 */
final class CapturasFm {
    private final App app;
    private final Path pasta;
    private final Deque<Consumer<Runnable>> passos = new ArrayDeque<>();
    private final Set<String> cenas;
    private BancoMundo db;
    private Gravador gravando;
    /** Cursor virtual (coordenadas da cena). */
    private double cx = 960, cy = 540;
    private final List<String> feitas = new ArrayList<>(), puladas = new ArrayList<>();
    /**
     * Câmera lenta da gravação: o roteiro (cursor, pausas, ritmo da partida) roda LENTO× mais devagar
     * e os tempos dos arquivos saem em tempo de vídeo (real ÷ LENTO). Com o snapshot de ~28 ms, dá
     * ~16 quadros por segundo reais, ~24 no vídeo. As animações próprias da interface ficam LENTO×
     * mais rápidas no vídeo.
     */
    private static final double LENTO = Double.parseDouble(System.getProperty("futebas.capturas.lento", "1.5"));

    CapturasFm(App app, Path pasta) {
        this.app = app;
        this.pasta = pasta;
        String c = System.getProperty("futebas.capturas.cenas", "1,2,3,4,5,6,7,8,9");
        cenas = new HashSet<>(Arrays.asList(c.split(",")));
    }

    private boolean cena(int n) { return cenas.contains(String.valueOf(n)); }

    void iniciar() {
        App.automatico = true;
        Ui.semDialogos = true;
        AcompanhamentoPartida.escala = 0.5 * LENTO;
        Opcoes.timesCustomizados(false);
        Opcoes.velocidade(AcompanhamentoPartida.Velocidade.RAPIDA);
        try {
            Files.createDirectories(pasta.resolve("prints"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        passo(f -> {
            tamanho(1920, 1080);
            esperar(1500, f);
        });
        // o banco do mundo já aberto: o "Novo jogo" abre na hora (sem a espera na gravação)
        passo(f -> app.trabalhar("Abrindo o banco...", Sessao::banco, b -> {
            db = b;
            f.run();
        }));
        if (cena(1)) cenaCarreira();
        else passo(f -> app.trabalhar("Abrindo o banco...", Sessao::banco, b -> {
            db = b;
            app.trabalhar("Montando o mundo de 2026...", () -> new Sessao(Jogo.novoJogo(db, 1995, "flamengo", "Técnico"), "capturas-fm"), s -> {
                app.abrir(s);
                tamanho(1920, 1080);
                esperar(800, f);
            });
        }));
        // o mundo andando (sem gravar): Brasileirão com rodadas, dívida no banco e uma proposta de emprego
        passo(f -> {
            Sessao s = app.sessao();
            app.trabalhar("Avançando a temporada...", () -> {
                for (int i = 0; i < Integer.getInteger("futebas.capturas.semanas", 15); i++) {
                    if (s.jogo.meuClube() != null) s.jogo.meuClube().confiancaDiretoria = 90;
                    s.fluxo.jogarProximaRodada(null);
                }
                s.jogo.escalacaoAutomatica();
                s.jogo.pedirEmprestimo(60_000_000);
                br.futebas.mundo.Clube pequeno = null;
                for (br.futebas.mundo.Clube c : s.jogo.mundo().clubes.values())
                    if (!c.humano && c.existe && !c.virtual && "BRA".equalsIgnoreCase(c.pais) && c.divisao == 2 && (pequeno == null || c.reputacao > pequeno.reputacao)) pequeno = c;
                if (pequeno != null) s.jogo.simulador().diretoria.proporAoHumano(pequeno);
                return s;
            }, x -> {
                app.irParaHub("jogo");
                esperar(1200, f);
            });
        });
        if (cena(2)) cenaCentral();
        if (cena(3)) cenaTatica();
        if (cena(4)) cenaJogador();
        if (cena(5)) cenaMercado();
        if (cena(6)) cenaFinancas();
        if (cena(8)) cenaTabelaVagas();
        if (cena(7)) cenaPartida();
        if (cena(9)) cenaKings();
        passo(f -> encerrar());
        proximo();
    }

    // ================================================================== cenas
    private void cenaCarreira() {
        passo(f -> {
            app.irParaInicio();
            esperar(1500, f);
        });
        gravar("fm-01-carreira");
        clicar(() -> texto("Novo jogo"), "clique");
        aguardar(() -> app.telaAtual() instanceof TelaNovoJogo && lista(0) != null && !lista(0).getItems().isEmpty() && !app.ocupado(), 30_000);
        segurar(700);
        clicar(() -> celula(0, x -> x instanceof Jogo.OpcaoPais p && "BRA".equals(p.id())), "clique");
        segurar(700);
        clicar(() -> celula(1, x -> x instanceof Jogo.OpcaoLiga l && l.nome() != null && l.nome().contains("Série A") && !l.nome().contains("Carioca")), "clique");
        segurar(800);
        clicar(() -> textoEm(".grade-clubes", "Flamengo"), "clique");
        segurar(700);
        clicar(() -> campoDe("Seu nome"), "clique");
        digitar(() -> campoDe("Seu nome"), "Técnico");
        segurar(500);
        passo(f -> {
            if (app.telaAtual() instanceof TelaNovoJogo) {
                Node rot = texto("Semente");
                if (rot != null && rot.getParent() != null)
                    for (Node n : rot.getParent().getChildrenUnmodifiable()) if (n instanceof javafx.scene.control.TextField t) t.setText("1995");
            }
            f.run();
        });
        clicar(() -> texto("Começar"), "clique");
        aguardar(() -> app.hub() != null && app.telaAtual() == app.hub() && !app.ocupado(), 120_000, 4_000);
        segurar(1200);
        parar();
        passo(f -> {
            tamanho(1920, 1080);
            esperar(500, f);
        });
    }

    private void cenaCentral() {
        passo(f -> {
            app.irParaHub("jogo");
            esperar(900, f);
        });
        gravar("fm-02-central");
        segurar(700);
        ir(() -> texto("Calendário"), 600);
        segurar(250);
        ir(() -> texto("Elenco"), 450);
        clicar(() -> texto("Caixa de entrada"), "clique");
        segurar(1100);
        clicar(() -> texto("Calendário"), "clique");
        segurar(1100);
        clicar(() -> texto("Próximo jogo"), "clique");
        segurar(1200);
        parar();
    }

    private void cenaTatica() {
        passo(f -> {
            app.irParaHub("jogo");
            esperar(700, f);
        });
        gravar("fm-03-tatica");
        clicar(() -> texto("Escalação e tática"), "clique");
        segurar(900);
        // formação
        clicar(() -> escolha("4-3-3"), "clique");
        segurar(600);
        escolherNoMenu("4-3-3", () -> escolha("4-3-3"), "4-3-3");
        segurar(900);
        // arrasta um ponta para a outra ponta (troca de posições no campo)
        arrastar(() -> vaga(9), () -> vaga(10));
        segurar(900);
        clicar(() -> texto("Pesada"), "clique");
        segurar(600);
        clicar(() -> texto("Pelo meio"), "clique");
        segurar(600);
        clicar(() -> escolhaEstilo(), "clique");
        segurar(600);
        escolherNoMenu("Contra", this::escolhaEstilo, null);
        segurar(1100);
        print("tatica");
        parar();
    }

    private void cenaJogador() {
        passo(f -> {
            app.irParaHub("jogo");
            esperar(700, f);
        });
        gravar("fm-04-jogador");
        clicar(() -> texto("Elenco"), "clique");
        segurar(900);
        clicar(() -> {
            var j = app.sessao().jogo;
            br.futebas.mundo.Jogador melhor = null;
            for (var x : j.elenco(j.meuClube().id)) if (melhor == null || x.forca > melhor.forca) melhor = x;
            return melhor == null ? null : texto(melhor.nomeExibicao());
        }, "clique");
        segurar(1300);
        print("jogador");
        clicar(() -> aba("Carreira"), "clique");
        segurar(1300);
        rolar(0.6, 900);
        segurar(800);
        clicar(() -> aba("Visão geral"), "clique");
        segurar(1000);
        parar();
    }

    private void cenaMercado() {
        passo(f -> {
            app.irParaHub("jogo");
            esperar(700, f);
        });
        gravar("fm-05-mercado");
        clicar(() -> texto("Mercado"), "clique");
        segurar(900);
        // força mínima 85 (digitando)
        clicar(() -> spinner(0), "clique");
        digitar(() -> spinner(0), "85");
        segurar(400);
        // posição: Ponta direita
        clicar(() -> combo("Todas as posições"), "clique");
        segurar(500);
        escolherCombo(() -> combo("Todas as posições"), x -> String.valueOf(x).contains("PD") || String.valueOf(x).toLowerCase(Locale.ROOT).contains("ponta direita"));
        segurar(500);
        clicar(() -> texto("Buscar"), "clique");
        segurar(900);
        clicar(() -> texto("Mohamed Salah"), "clique");
        segurar(1300);
        print("mercado");
        passo(f -> {
            Ui.semDialogos = false;
            f.run();
        });
        clicar(() -> texto("Fazer proposta"), "clique");
        segurar(1200);
        ir(() -> textoEm(".caixa-sobreposta", "Salário oferecido"), 500);
        segurar(1500);
        clicar(() -> textoEm(".caixa-sobreposta", "Cancelar"), "clique");
        segurar(900);
        passo(f -> {
            Ui.semDialogos = true;
            f.run();
        });
        parar();
    }

    private void cenaFinancas() {
        passo(f -> {
            app.irParaHub("jogo");
            esperar(700, f);
        });
        gravar("fm-06-financas");
        clicar(() -> texto("Finanças"), "clique");
        segurar(900);
        rolar(1.0, 1200);
        segurar(900);
        print("financas");
        clicar(() -> texto("Metade da dívida"), "clique");
        segurar(700);
        passo(f -> {
            Ui.semDialogos = false;
            f.run();
        });
        clicar(() -> texto("Pagar"), "clique");
        segurar(2000);
        clicar(() -> textoEm(".caixa-sobreposta", "Cancelar"), "clique");
        segurar(900);
        passo(f -> {
            Ui.semDialogos = true;
            f.run();
        });
        parar();
    }

    private void cenaTabelaVagas() {
        passo(f -> {
            br.futebas.ui.telas.PainelTabelas.focar("bra-1");
            app.irParaHub("jogo");
            esperar(700, f);
        });
        gravar("fm-08-tabela-vagas");
        clicar(() -> texto("Campeonatos"), "clique");
        segurar(1800);
        clicar(() -> texto("Vagas"), "clique");
        segurar(1600);
        rolar(0.5, 900);
        segurar(800);
        parar();
    }

    private void cenaPartida() {
        passo(f -> {
            app.sessao().jogo.escalacaoAutomatica();
            AcompanhamentoPartida.escala = Double.parseDouble(System.getProperty("futebas.capturas.escalaPartida", "0.5")) * LENTO;
            app.irParaHub("jogo");
            esperar(900, f);
        });
        gravar("fm-07-partida");
        clicar(() -> textoEm(".cabecalho", "JOGAR"), "clique");
        aguardar(() -> app.telaAtual() instanceof TelaPartida tp && tp.minuto() >= 1, 60_000);
        segurar(1200);
        clicar(() -> texto("Ultra rápida"), "clique");
        segurar(2300);
        // tática e trocas: o painel fica aberto até confirmar
        passo(f -> {
            App.automatico = false;
            f.run();
        });
        clicar(() -> texto("Tática e trocas"), "clique");
        segurar(900);
        clicar(() -> vagaTrocas(9), "clique");
        segurar(500);
        clicar(() -> bancoTrocas(), "clique");
        segurar(900);
        clicar(() -> textoComeca("Confirmar"), "clique");
        passo(f -> {
            App.automatico = true;
            f.run();
        });
        segurar(4200);
        print("partida");
        segurar(1500);
        clicar(() -> texto("SIMULAR ATÉ O FIM"), "clique");
        aguardar(() -> app.telaAtual() instanceof TelaPartida tp && tp.terminou(), 30_000);
        segurar(800);
        rolar(1.0, 900);
        segurar(1200);
        parar();
        // termina a semana sem gravar
        for (int k = 0; k < 4; k++) passo(f -> {
            if (app.telaAtual() instanceof TelaPartida) app.cena().getRoot().fireEvent(tecla(javafx.scene.input.KeyCode.ENTER));
            esperar(600, () -> {
                if (!app.temPendente() && !(app.telaAtual() instanceof TelaPartida)) {
                    f.run();
                    return;
                }
                app.jogar();
                if (app.telaAtual() instanceof TelaPartida) app.cena().getRoot().fireEvent(tecla(javafx.scene.input.KeyCode.ESCAPE));
                esperarCond(() -> !(app.telaAtual() instanceof TelaPartida t2) || t2.terminou() || t2.entreJogos(), 60_000, f);
            });
        });
    }

    private void cenaKings() {
        passo(f -> app.trabalhar("Montando a carreira da Kings...", () -> {
            if (db == null) db = Sessao.banco();
            Sessao s = new Sessao(Jogo.novoJogoKings(db, 1995, null, primeiroTimeKings(db), "Técnico"), "capturas-fm-kings");
            for (int i = 0; i < 10; i++) s.fluxo.avancarSemana();
            return s;
        }, s -> {
            app.abrir(s);
            tamanho(1920, 1080);
            Hub.abaKings("pool");
            app.irParaHub("jogo");
            esperar(1000, f);
        }));
        gravar("fm-09-kings");
        clicar(() -> texto("Kings League"), "clique");
        segurar(1800);
        rolar(0.5, 700);
        segurar(600);
        passo(f -> {
            AcompanhamentoKings.msAutomatico = (int) Math.round(Integer.getInteger("futebas.capturas.kingsMs", 330) * LENTO);
            Hub.abaKings("competicoes");
            f.run();
        });
        clicar(() -> textoEm(".cabecalho", "JOGAR"), "clique");
        aguardar(() -> app.telaAtual() instanceof TelaPartida tp && tp.kingsVisivel(), 60_000);
        segurar(2600);
        clicar(() -> botaoKings("Carta"), "clique");
        segurar(1600);
        clicar(() -> botaoKings("Presidente"), "clique");
        segurar(5200);
        print("kings");
        parar();
        passo(f -> {
            AcompanhamentoKings.msAutomatico = 150;
            f.run();
        });
    }

    // ================================================================== ações de "pessoa"
    private void passo(Consumer<Runnable> p) { passos.add(p); }

    private void proximo() {
        Consumer<Runnable> p = passos.poll();
        if (p == null) return;
        try {
            p.accept(this::proximo);
        } catch (RuntimeException e) {
            System.out.println("[fm] erro num passo: " + e);
            e.printStackTrace(System.out);
            proximo();
        }
    }

    private void segurar(int ms) { passo(f -> esperar(ms, f)); }

    private void gravar(String nome) {
        passo(f -> {
            gravando = new Gravador(nome);
            gravando.iniciar();
            f.run();
        });
    }

    private void parar() {
        passo(f -> {
            Gravador g = gravando;
            gravando = null;
            if (g == null) f.run();
            else g.parar(f);
        });
    }

    private void aguardar(BooleanSupplier cond, int maxMs) { aguardar(cond, maxMs, 0); }

    /** Espera a condição (com {@code minMs} mínimo antes de seguir). */
    private void aguardar(BooleanSupplier cond, int maxMs, int minMs) {
        passo(f -> {
            long ini = System.currentTimeMillis();
            esperarCond(() -> System.currentTimeMillis() - ini >= minMs * LENTO && cond.getAsBoolean(), maxMs, f);
        });
    }

    /** Move o cursor até o centro do nó (curva suave, ~{@code ms}). */
    private void ir(Supplier<Node> alvo, int ms) {
        passo(f -> {
            Node n = alvo.get();
            if (n == null) {
                System.out.println("[fm] alvo não encontrado (ir)");
                f.run();
                return;
            }
            Point2D p = centro(n);
            mover(p.getX(), p.getY(), ms, f);
        });
    }

    /** Anda até o alvo, para um instante e clica (eventos de mouse na cena). */
    private void clicar(Supplier<Node> alvo, String tipo) {
        passo(f -> {
            Node n = alvo.get();
            if (n == null) {
                System.out.println("[fm] alvo não encontrado (" + tipo + ") — pulado");
                f.run();
                return;
            }
            Point2D p = centro(n);
            double dist = Math.hypot(p.getX() - cx, p.getY() - cy);
            int ms = (int) Math.max(350, Math.min(900, 250 + dist * 0.55));
            mover(p.getX(), p.getY(), ms, () -> esperar(220, () -> {
                Node alvoAgora = alvo.get();
                Node t = alvoAgora != null ? alvoAgora : n;
                Point2D q = centro(t);
                if (gravando != null) gravando.clique(q.getX(), q.getY(), tipo);
                // o clique roda depois: um diálogo (laço aninhado) não trava o roteiro
                Platform.runLater(() -> clicarAgora(t, q));
                esperar(120, f);
            }));
        });
    }

    /** "Arrasta" de um nó para outro: aperta no primeiro, leva o cursor e solta no segundo (troca no campo). */
    private void arrastar(Supplier<Node> de, Supplier<Node> para) {
        passo(f -> {
            Node a = de.get(), b = para.get();
            if (a == null || b == null) {
                f.run();
                return;
            }
            Point2D pa = centro(a), pb = centro(b);
            mover(pa.getX(), pa.getY(), 600, () -> esperar(200, () -> {
                if (gravando != null) gravando.clique(pa.getX(), pa.getY(), "arrastar");
                Platform.runLater(() -> clicarAgora(a, pa));
                esperar(150, () -> mover(pb.getX(), pb.getY(), 750, () -> esperar(120, () -> {
                    if (gravando != null) gravando.clique(pb.getX(), pb.getY(), "soltar");
                    Node b2 = para.get();
                    Node t = b2 != null ? b2 : b;
                    Platform.runLater(() -> clicarAgora(t, centro(t)));
                    esperar(120, f);
                })));
            }));
        });
    }

    /** Digita o texto no campo (um caractere por vez), trocando o valor atual. */
    private void digitar(Supplier<Node> alvo, String texto) {
        passo(f -> {
            Node n = alvo.get();
            javafx.scene.control.TextField tf = n instanceof javafx.scene.control.Spinner<?> sp ? sp.getEditor()
                    : n instanceof javafx.scene.control.TextField t ? t : null;
            if (tf == null) {
                f.run();
                return;
            }
            tf.requestFocus();
            tf.selectAll();
            if (gravando != null) gravando.clique(cx, cy, "digitar");
            int[] i = {0};
            tf.setText("");
            Runnable[] um = new Runnable[1];
            um[0] = () -> {
                if (i[0] >= texto.length()) {
                    tf.fireEvent(new javafx.event.ActionEvent());
                    if (n instanceof javafx.scene.control.Spinner<?> sp) {
                        try {
                            sp.increment(0);
                        } catch (RuntimeException ignorado) {
                            // sem fábrica de valores
                        }
                    }
                    f.run();
                    return;
                }
                tf.appendText(String.valueOf(texto.charAt(i[0]++)));
                esperar(140, um[0]);
            };
            esperar(150, um[0]);
        });
    }

    /** Rola o painel principal até a fração {@code ate} (0–1) em {@code ms}. */
    private void rolar(double ate, int ms) {
        passo(f -> {
            ScrollPane sp = maiorRolagem();
            if (sp == null) {
                f.run();
                return;
            }
            if (gravando != null) gravando.clique(cx, cy, "rolar");
            double de = sp.getVvalue();
            long ini = System.nanoTime();
            new AnimationTimer() {
                @Override
                public void handle(long agora) {
                    double t = Math.min(1, (agora - ini) / (ms * LENTO * 1e6));
                    double e = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
                    sp.setVvalue(de + (ate - de) * e);
                    if (t >= 1) {
                        stop();
                        f.run();
                    }
                }
            }.start();
        });
    }

    /** Abre o menu da escolha e "clica" no item (o menu aparece no quadro). */
    private void escolherNoMenu(String item, Supplier<Node> caixa, String valorFixo) {
        passo(f -> {
            Node itemNo = itemDePopup(item);
            Runnable escolher = () -> {
                Node c = caixa.get();
                if (c instanceof ChoiceBox<?> cb) {
                    for (Object o : cb.getItems()) {
                        String s = cb.getConverter() != null ? cb.getConverter().toString(cast(o)) : String.valueOf(o);
                        if (parecido(s, item) || parecido(String.valueOf(o), item)) {
                            selecionar(cb, o);
                            break;
                        }
                    }
                    cb.hide();
                }
                esperar(100, f);
            };
            if (itemNo == null) {
                escolher.run();
                return;
            }
            Point2D p = centro(itemNo);
            mover(p.getX(), p.getY(), 450, () -> esperar(200, () -> {
                if (gravando != null) gravando.clique(p.getX(), p.getY(), "clique");
                escolher.run();
            }));
        });
    }

    private void escolherCombo(Supplier<Node> caixa, Predicate<Object> qual) {
        passo(f -> {
            Node c = caixa.get();
            if (!(c instanceof ComboBox<?> cb)) {
                f.run();
                return;
            }
            Object alvo = null;
            for (Object o : cb.getItems()) if (qual.test(o)) { alvo = o; break; }
            Object escolhido = alvo;
            Node cel = null;
            if (escolhido != null) {
                for (Window w : new ArrayList<>(Window.getWindows())) {
                    if (!(w instanceof PopupWindow) || !w.isShowing() || w.getScene() == null) continue;
                    for (Node n : w.getScene().getRoot().lookupAll(".list-cell"))
                        if (n instanceof ListCell<?> lc && escolhido.equals(lc.getItem())) cel = n;
                }
            }
            Runnable escolher = () -> {
                if (escolhido != null) selecionarCombo(cb, escolhido);
                cb.hide();
                esperar(100, f);
            };
            if (cel == null) {
                escolher.run();
                return;
            }
            Point2D p = centro(cel);
            mover(p.getX(), p.getY(), 500, () -> esperar(200, () -> {
                if (gravando != null) gravando.clique(p.getX(), p.getY(), "clique");
                escolher.run();
            }));
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) { return (T) o; }

    /** Mesmo texto, ou começa com ele (sem diferenciar maiúsculas, hífen ou espaço). */
    private static boolean parecido(String a, String b) {
        if (a == null || b == null) return false;
        String x = a.toLowerCase(Locale.ROOT).replace('-', ' '), y = b.toLowerCase(Locale.ROOT).replace('-', ' ');
        return x.equals(y) || x.startsWith(y);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void selecionar(ChoiceBox cb, Object o) { cb.getSelectionModel().select(o); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void selecionarCombo(ComboBox cb, Object o) { cb.getSelectionModel().select(o); }

    /** Move o cursor virtual (ease in-out). */
    private void mover(double x, double y, int ms, Runnable depois) {
        double x0 = cx, y0 = cy;
        // uma leve curva, como a mão de alguém
        double mx = (x0 + x) / 2 + (y - y0) * 0.08, my = (y0 + y) / 2 - (x - x0) * 0.08;
        long ini = System.nanoTime();
        new AnimationTimer() {
            @Override
            public void handle(long agora) {
                double t = Math.min(1, (agora - ini) / (ms * LENTO * 1e6));
                double e = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
                double u = 1 - e;
                cx = u * u * x0 + 2 * u * e * mx + e * e * x;
                cy = u * u * y0 + 2 * u * e * my + e * e * y;
                if (t >= 1) {
                    cx = x;
                    cy = y;
                    stop();
                    depois.run();
                }
            }
        }.start();
    }

    /** Pressiona, solta e clica no nó (eventos de mouse sintetizados, como um clique de verdade). */
    private void clicarAgora(Node n, Point2D p) {
        if (n.getScene() == null) return;
        Point2D tela = n.getScene().getWindow() == null ? p
                : new Point2D(n.getScene().getWindow().getX() + n.getScene().getX() + p.getX(), n.getScene().getWindow().getY() + n.getScene().getY() + p.getY());
        PickResult pr = new PickResult(n, p.getX(), p.getY());
        for (var tipo : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
            MouseEvent e = new MouseEvent(tipo, p.getX(), p.getY(), tela.getX(), tela.getY(), MouseButton.PRIMARY, 1,
                    false, false, false, false, tipo == MouseEvent.MOUSE_PRESSED, false, false, true, false, true, pr);
            try {
                Event.fireEvent(n, e);
            } catch (RuntimeException ex) {
                System.out.println("[fm] erro no clique: " + ex);
            }
        }
    }

    // ================================================================== achar coisas na tela
    private Point2D centro(Node n) {
        Bounds b = n.localToScene(n.getBoundsInLocal());
        Point2D c = new Point2D(b.getCenterX(), b.getCenterY());
        // nó dentro de um popup: converte para a cena principal
        Window w = n.getScene() == null ? null : n.getScene().getWindow();
        if (w != null && w != app.palco()) {
            double ox = w.getX() + n.getScene().getX() - (app.palco().getX() + app.cena().getX());
            double oy = w.getY() + n.getScene().getY() - (app.palco().getY() + app.cena().getY());
            c = new Point2D(c.getX() + ox, c.getY() + oy);
        }
        return c;
    }

    private static boolean visivel(Node n) {
        for (Node p = n; p != null; p = p.getParent()) if (!p.isVisible() || p.getOpacity() == 0) return false;
        if (n.getScene() == null) return false;
        Bounds b = n.localToScene(n.getBoundsInLocal());
        return b.getWidth() > 0 && b.getHeight() > 0 && b.getMaxX() > 0 && b.getMaxY() > 0
                && b.getMinX() < n.getScene().getWidth() && b.getMinY() < n.getScene().getHeight();
    }

    private static void todos(Node n, List<Node> l) {
        l.add(n);
        if (n instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) todos(c, l);
    }

    /** O nó visível (de preferência o mais acima na camada) com esse texto exato. */
    private Node texto(String t) { return achar(app.cena().getRoot(), x -> t.equals(textoDe(x))); }

    private Node textoComeca(String t) {
        return achar(app.cena().getRoot(), x -> {
            String s = textoDe(x);
            return s != null && s.startsWith(t);
        });
    }

    private Node textoEm(String classe, String t) {
        for (Node raiz : app.cena().getRoot().lookupAll(classe)) {
            Node n = achar(raiz, x -> t.equals(textoDe(x)));
            if (n != null) return n;
        }
        return null;
    }

    private static String textoDe(Node x) {
        if (x instanceof Labeled l) return l.getText();
        if (x instanceof Text t) return t.getText();
        return null;
    }

    private static Node achar(Node raiz, Predicate<Node> p) {
        List<Node> l = new ArrayList<>();
        todos(raiz, l);
        Node achado = null;
        for (Node n : l) if (p.test(n) && visivel(n)) achado = n; // o último = o que está por cima
        if (achado instanceof Text t && t.getParent() instanceof Labeled) return t.getParent();
        return achado;
    }

    private javafx.scene.control.ListView<?> lista(int i) {
        List<Node> l = new ArrayList<>(app.cena().getRoot().lookupAll(".list-view"));
        l.removeIf(n -> !visivel(n));
        return i < l.size() ? (javafx.scene.control.ListView<?>) l.get(i) : null;
    }

    private Node celula(int lista, Predicate<Object> qual) {
        javafx.scene.control.ListView<?> lv = lista(lista);
        if (lv == null) return null;
        int idx = -1;
        for (int i = 0; i < lv.getItems().size(); i++) if (qual.test(lv.getItems().get(i))) { idx = i; break; }
        if (idx < 0) return null;
        Node c = celulaVisivel(lv, idx);
        if (c == null) {
            lv.scrollTo(Math.max(0, idx - 2));
            lv.layout();
            c = celulaVisivel(lv, idx);
        }
        return c;
    }

    private static Node celulaVisivel(javafx.scene.control.ListView<?> lv, int idx) {
        for (Node n : lv.lookupAll(".list-cell")) if (n instanceof ListCell<?> lc && lc.getIndex() == idx && visivel(n)) return n;
        return null;
    }

    /** O campo de texto ao lado do rótulo (Ui.dado: rótulo em cima, campo embaixo). */
    private Node campoDe(String rotulo) {
        Node r = texto(rotulo);
        if (r == null || r.getParent() == null) return null;
        for (Node n : r.getParent().getChildrenUnmodifiable()) if (n instanceof javafx.scene.control.TextField) return n;
        return null;
    }

    private Node escolha(String contem) {
        for (Node n : app.cena().getRoot().lookupAll(".choice-box"))
            if (n instanceof ChoiceBox<?> cb && visivel(n) && cb.getItems().contains(contem)) return n;
        return null;
    }

    private Node escolhaEstilo() {
        for (Node n : app.cena().getRoot().lookupAll(".choice-box"))
            if (n instanceof ChoiceBox<?> cb && visivel(n) && !cb.getItems().isEmpty() && cb.getItems().get(0) instanceof br.futebas.partida.Estilo) return n;
        return null;
    }

    private Node combo(String valorAtual) {
        for (Node n : app.cena().getRoot().lookupAll(".combo-box"))
            if (n instanceof ComboBox<?> cb && visivel(n)) {
                Object v = cb.getValue();
                String s = cb.getConverter() != null && v != null ? cb.getConverter().toString(cast(v)) : String.valueOf(v);
                if (valorAtual.equals(s) || cb.getButtonCell() != null && valorAtual.equals(cb.getButtonCell().getText())) return n;
            }
        return null;
    }

    private Node spinner(int i) {
        List<Node> l = new ArrayList<>(app.cena().getRoot().lookupAll(".spinner"));
        l.removeIf(n -> !visivel(n));
        return i < l.size() ? l.get(i) : null;
    }

    private Node aba(String nome) {
        for (Node n : app.cena().getRoot().lookupAll(".tab")) {
            Node t = achar(n, x -> nome.equals(textoDe(x)));
            if (t != null && visivel(n)) return n;
        }
        return null;
    }

    private Node itemDePopup(String texto) {
        for (Window w : new ArrayList<>(Window.getWindows())) {
            if (!(w instanceof PopupWindow) || !w.isShowing() || w.getScene() == null) continue;
            Node n = achar(w.getScene().getRoot(), x -> textoDe(x) != null && parecido(textoDe(x), texto));
            if (n != null) return n;
        }
        return null;
    }

    private Node vaga(int i) {
        List<Node> l = new ArrayList<>(app.cena().getRoot().lookupAll(".vaga"));
        l.removeIf(n -> !visivel(n));
        return i < l.size() ? l.get(i) : null;
    }

    private Node vagaTrocas(int i) {
        for (Node raiz : app.cena().getRoot().lookupAll(".caixa-sobreposta")) {
            if (!visivel(raiz)) continue;
            List<Node> l = new ArrayList<>(raiz.lookupAll(".vaga"));
            l.removeIf(n -> !visivel(n));
            if (i < l.size()) return l.get(i);
        }
        return vaga(i);
    }

    private Node bancoTrocas() {
        for (Node raiz : app.cena().getRoot().lookupAll(".caixa-sobreposta")) {
            if (!visivel(raiz)) continue;
            List<Node> l = new ArrayList<>(raiz.lookupAll(".atleta"));
            l.removeIf(n -> !visivel(n) || n.getStyleClass().contains("sai-pendente"));
            if (l.size() > 2) return l.get(2);
            if (!l.isEmpty()) return l.get(0);
        }
        return null;
    }

    private Node botaoKings(String nome) {
        for (Node n : app.cena().getRoot().lookupAll(".kings-botao-icone"))
            if (n instanceof javafx.scene.control.Button b && nome.equals(b.getText()) && visivel(n)) return n;
        return null;
    }

    private ScrollPane maiorRolagem() {
        ScrollPane m = null;
        double area = 0;
        for (Node n : app.cena().getRoot().lookupAll(".scroll-pane")) {
            if (!(n instanceof ScrollPane sp) || !visivel(n)) continue;
            if (sp.getContent() == null || sp.getContent().getBoundsInLocal().getHeight() <= sp.getViewportBounds().getHeight() + 4) continue;
            Bounds b = n.localToScene(n.getBoundsInLocal());
            double a = b.getWidth() * b.getHeight();
            if (a > area) {
                area = a;
                m = sp;
            }
        }
        return m;
    }

    private static String primeiroTimeKings(BancoMundo db) {
        String outro = null;
        for (var kt : db.kingsTimes.values()) {
            if ("kings-brasil".equals(kt.ligaId())) return kt.id();
            if (outro == null) outro = kt.id();
        }
        return outro;
    }

    private static javafx.scene.input.KeyEvent tecla(javafx.scene.input.KeyCode k) {
        return new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", k, false, false, false, false);
    }

    // ================================================================== tempo, prints, janela
    private void esperar(int ms, Runnable depois) {
        PauseTransition p = new PauseTransition(Duration.millis(ms * LENTO));
        p.setOnFinished(e -> depois.run());
        p.play();
    }

    private void esperarCond(BooleanSupplier cond, int maxMs, Runnable depois) {
        long ini = System.currentTimeMillis();
        Runnable[] r = new Runnable[1];
        r[0] = () -> {
            if (cond.getAsBoolean() || System.currentTimeMillis() - ini > maxMs) depois.run();
            else esperar(50, r[0]);
        };
        r[0].run();
    }

    private void print(String nome) {
        passo(f -> {
            try {
                BufferedImage b = quadroComposto();
                ImageIO.write(b, "png", pasta.resolve("prints").resolve(nome + ".png").toFile());
                System.out.println("[fm] print " + nome + " " + b.getWidth() + "x" + b.getHeight());
            } catch (IOException | RuntimeException e) {
                System.out.println("[fm] erro no print " + nome + ": " + e);
            }
            f.run();
        });
    }

    /** A cena principal com os popups visíveis (menus suspensos) desenhados por cima. */
    private BufferedImage quadroComposto() {
        Captura c = capturar();
        return c.montar();
    }

    private record Camada(WritableImage img, int dx, int dy) {}

    private record Captura(WritableImage base, List<Camada> camadas) {
        BufferedImage montar() {
            BufferedImage b = paraImagem(base);
            if (!camadas.isEmpty()) {
                Graphics2D g = b.createGraphics();
                for (Camada k : camadas) g.drawImage(paraImagem(k.img()), k.dx(), k.dy(), null);
                g.dispose();
            }
            return b;
        }
    }

    private Captura capturar() {
        return capturarPopups(app.cena().getRoot().snapshot(null, null));
    }

    private Captura capturarPopups(WritableImage base) {
        List<Camada> camadas = new ArrayList<>();
        double ox = app.palco().getX() + app.cena().getX(), oy = app.palco().getY() + app.cena().getY();
        for (Window w : new ArrayList<>(Window.getWindows())) {
            if (!(w instanceof PopupWindow) || !w.isShowing() || w.getScene() == null || w.getScene().getRoot() == null) continue;
            if (w instanceof javafx.scene.control.Tooltip) continue;
            WritableImage img = w.getScene().getRoot().snapshot(null, null);
            camadas.add(new Camada(img, (int) Math.round(w.getX() + w.getScene().getX() - ox), (int) Math.round(w.getY() + w.getScene().getY() - oy)));
        }
        return new Captura(base, camadas);
    }

    static BufferedImage paraImagem(WritableImage img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] dados = ((java.awt.image.DataBufferInt) b.getRaster().getDataBuffer()).getData();
        PixelReader pr = img.getPixelReader();
        pr.getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), dados, 0, w); // cópia direta (o alfa é ignorado)
        return b;
    }

    private void tamanho(double w, double h) {
        app.palco().setX(0);
        app.palco().setY(0);
        double extraW = app.palco().getWidth() - app.cena().getWidth(), extraH = app.palco().getHeight() - app.cena().getHeight();
        app.palco().setWidth(w + extraW);
        app.palco().setHeight(h + extraH);
    }

    private void encerrar() {
        System.out.println("[fm] fim. cenas: " + feitas + "; puladas: " + puladas);
        Platform.exit();
        System.exit(0);
    }

    // ================================================================== gravador
    /** Quadros (~20 por segundo), instantes, cliques e o caminho do cursor de uma cena. */
    private final class Gravador {
        private final Path dir;
        private final String nome;
        private final List<String> tempos = new ArrayList<>(), cliques = new ArrayList<>(), movimentos = new ArrayList<>();
        private final ExecutorService escritor = Executors.newFixedThreadPool(4);
        /** Imagens reaproveitadas (sem alocar 8 MB por quadro); sem nenhuma livre, o quadro é pulado. */
        private final java.util.concurrent.ArrayBlockingQueue<WritableImage> livres = new java.util.concurrent.ArrayBlockingQueue<>(12);
        private int pulados;
        private double msSnapshot;
        private AnimationTimer timer;
        private long t0, ultimoQuadro, ultimoMov;
        private int quadros;

        Gravador(String nome) {
            this.nome = nome;
            dir = pasta.resolve(nome);
            try {
                Files.createDirectories(dir);
                try (var l = Files.list(dir)) {
                    for (Path p : (Iterable<Path>) l::iterator) Files.deleteIfExists(p);
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        /** Tempo de vídeo (real ÷ LENTO). */
        double agora() { return (System.nanoTime() - t0) / 1e9 / LENTO; }

        void iniciar() {
            int w = (int) Math.round(app.cena().getWidth()), h = (int) Math.round(app.cena().getHeight());
            for (int i = 0; i < 12; i++) livres.offer(new WritableImage(w, h));
            t0 = System.nanoTime();
            timer = new AnimationTimer() {
                @Override
                public void handle(long agora) {
                    if (ultimoMov == 0 || agora - ultimoMov >= (long) (50_000_000L * LENTO)) {
                        ultimoMov = agora;
                        movimentos.add(String.format(Locale.ROOT, "[%.3f, %d, %d]", agora(), Math.round(cx), Math.round(cy)));
                    }
                    if (ultimoQuadro != 0 && agora - ultimoQuadro < 45_000_000L) return;
                    ultimoQuadro = agora;
                    quadro();
                }
            };
            timer.start();
        }

        private void quadro() {
            WritableImage img = livres.poll();
            if (img == null) {
                pulados++;
                return;
            }
            double s = agora();
            long t1 = System.nanoTime();
            WritableImage base = app.cena().getRoot().snapshot(null, img);
            msSnapshot += (System.nanoTime() - t1) / 1e6;
            Captura c = capturarPopups(base);
            String arq = String.format("%05d.jpg", quadros++);
            tempos.add(String.format(Locale.ROOT, "[\"%s\", %.3f]", arq, s));
            escritor.submit(() -> {
                try {
                    BufferedImage b;
                    try {
                        b = c.montar();
                    } finally {
                        if (base == img) livres.offer(img);
                    }
                    ImageWriter it = ImageIO.getImageWritersByFormatName("jpg").next();
                    ImageWriteParam p = it.getDefaultWriteParam();
                    p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    p.setCompressionQuality(0.9f);
                    try (var out = ImageIO.createImageOutputStream(dir.resolve(arq).toFile())) {
                        it.setOutput(out);
                        it.write(null, new IIOImage(b, null, null), p);
                    }
                    it.dispose();
                } catch (IOException | RuntimeException e) {
                    System.out.println("[fm] erro no quadro " + arq + ": " + e);
                }
            });
        }

        void clique(double x, double y, String tipo) {
            cliques.add(String.format(Locale.ROOT, "[%.3f, %d, %d, \"%s\"]", agora(), Math.round(x), Math.round(y), tipo));
            System.out.println(String.format(Locale.ROOT, "[fm] %s %.2fs %s (%d,%d)", nome, agora(), tipo, Math.round(x), Math.round(y)));
        }

        void parar(Runnable depois) {
            timer.stop();
            quadro();
            double dur = agora();
            escritor.shutdown();
            try {
                escritor.awaitTermination(3, TimeUnit.MINUTES);
                Files.writeString(dir.resolve("times.json"), "[" + String.join(",\n", tempos) + "]\n");
                Files.writeString(dir.resolve("clicks.json"), "[" + String.join(",\n", cliques) + "]\n");
                Files.writeString(dir.resolve("moves.json"), "[" + String.join(",\n", movimentos) + "]\n");
            } catch (IOException | InterruptedException e) {
                System.out.println("[fm] erro ao fechar " + nome + ": " + e);
            }
            System.out.println(String.format(Locale.ROOT, "[fm] cena %s: %d quadros, %.2f s (%.1f qps, %d pulados)", nome, quadros, dur, quadros / Math.max(0.01, dur), pulados) + String.format(Locale.ROOT, " snapshot %.1f ms, cena %.0fx%.0f",
                    msSnapshot / Math.max(1, quadros), app.cena().getWidth(), app.cena().getHeight()));
            feitas.add(nome);
            depois.run();
        }
    }
}
