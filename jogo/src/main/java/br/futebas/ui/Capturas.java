package br.futebas.ui;

import br.futebas.Jogo;
import br.futebas.dados.BancoMundo;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.AcompanhamentoPartida;
import br.futebas.ui.modelo.Fluxo;
import br.futebas.ui.telas.Hub;
import br.futebas.ui.telas.TelaFimTemporada;
import br.futebas.ui.telas.TelaPartida;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;

/**
 * Modo de capturas (opção {@code --capturas} ou {@code -Dfutebas.capturas=pasta}): joga sozinho um
 * roteiro — novo jogo em 1º/jan/2026 com um clube da Série A, todas as telas da central (inclusive
 * Seleções), uma partida ao vivo, o resto da temporada, o fim de temporada, o mundo, o técnico sem
 * clube e a Kings League — salvando cada tela em PNG.
 */
final class Capturas {
    private final App app;
    private final Path pasta;
    private final Deque<Runnable> passos = new ArrayDeque<>();
    private BancoMundo db;
    private int n;

    Capturas(App app, Path pasta) {
        this.app = app;
        this.pasta = pasta;
    }

    void iniciar() {
        if (Boolean.getBoolean("futebas.capturas.fm")) {
            // trailer "estilo FM": cursor, cliques e cenas gravadas (CapturasFm)
            new CapturasFm(app, pasta).iniciar();
            return;
        }
        App.automatico = true;
        AcompanhamentoPartida.escala = 0.1;
        Ui.semDialogos = true;
        try {
            Files.createDirectories(pasta);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        String clube = System.getProperty("futebas.capturas.clube", "flamengo");
        passos.add(() -> foto("inicio", this::proximo));
        if (Boolean.getBoolean("futebas.capturas.inicio")) {
            // roteiro curto: a tela de início em 1280×800 e 1920×1080, com um item focado, e nas escalas 90% e 125%
            passos.add(() -> {
                tamanho(1280, 800);
                esperar(900, () -> foto("inicio-1280x800", this::proximo));
            });
            passos.add(() -> {
                tamanho(1920, 1080);
                esperar(900, () -> foto("inicio-1920x1080", this::proximo));
            });
            passos.add(() -> {
                app.cena().getRoot().lookupAll(".ini-item").stream().skip(1).findFirst().ifPresent(javafx.scene.Node::requestFocus);
                esperar(500, () -> foto("inicio-1920x1080-foco", this::proximo));
            });
            for (Opcoes.Escala e : new Opcoes.Escala[] {Opcoes.Escala.E125, Opcoes.Escala.E90}) {
                passos.add(() -> {
                    tamanho(1280, 800);
                    Opcoes.Escala antes = Opcoes.escala();
                    Opcoes.escala(e);
                    esperar(900, () -> foto("inicio-1280x800-" + e.name().toLowerCase(java.util.Locale.ROOT), () -> {
                        Opcoes.escala(antes);
                        proximo();
                    }));
                });
            }
            passos.add(this::encerrar);
            proximo();
            return;
        }
        if (Boolean.getBoolean("futebas.capturas.opcoes")) {
            // roteiro curto: as quatro seções das Opções (sem jogo aberto) + escala 125%
            for (String s : br.futebas.ui.telas.TelaOpcoes.SECOES) {
                passos.add(() -> {
                    var t = br.futebas.ui.telas.TelaOpcoes.abrir();
                    if (t != null) t.mostrar(s);
                    esperar(400, () -> foto("opcoes-" + s.toLowerCase(java.util.Locale.ROOT), this::proximo));
                });
            }
            passos.add(() -> {
                Opcoes.Escala antes = Opcoes.escala();
                Opcoes.escala(Opcoes.Escala.E125);
                esperar(400, () -> foto("opcoes-escala-125", () -> {
                    Opcoes.escala(antes);
                    proximo();
                }));
            });
            passos.add(this::encerrar);
            proximo();
            return;
        }
        if (Boolean.getBoolean("futebas.capturas.inicial")) {
            roteiroInicial(clube);
            return;
        }
        if (Boolean.getBoolean("futebas.capturas.ios")) {
            roteiroIos();
            return;
        }
        if (Boolean.getBoolean("futebas.capturas.kings")) {
            roteiroKings();
            return;
        }
        if (Boolean.getBoolean("futebas.capturas.portfolio")) {
            roteiroPortfolio(clube);
            return;
        }
        passos.add(() -> app.trabalhar("Abrindo o banco...", Sessao::banco, b -> {
            db = b;
            app.novoJogo();
            esperarLivre(() -> esperar(800, () -> foto("novo-jogo", this::proximo)));
        }));
        passos.add(() -> {
            String id = clube;
            if (!db.clubes.containsKey(id)) {
                for (Jogo.OpcaoClube o : Jogo.clubesDisponiveis(db)) if (Integer.valueOf(1).equals(o.divisao())) { id = o.id(); break; }
            }
            String escolhido = id;
            app.trabalhar("Montando o mundo de 2026...", () -> new Sessao(Jogo.novoJogo(db, 1995, escolhido, "Técnico de Teste"), "capturas"), s -> {
                app.abrir(s);
                esperar(500, this::proximo);
            });
        });
        if (Boolean.getBoolean("futebas.capturas.escalacao")) {
            // roteiro curto: só a escalação em duas resoluções
            passos.add(() -> {
                app.irParaHub("escalacao");
                esperar(600, () -> foto("escalacao-1280x800", this::proximo));
            });
            passos.add(() -> {
                app.palco().setWidth(1920);
                app.palco().setHeight(1080 + (app.palco().getHeight() - app.cena().getHeight()));
                esperar(900, () -> foto("escalacao-1920x1080", this::proximo));
            });
            passos.add(this::encerrar);
            proximo();
            return;
        }
        String so = System.getProperty("futebas.capturas.paineis");
        if (so != null && !so.isBlank()) {
            // roteiro curto: avança N semanas (futebas.capturas.semanas) e fotografa só os painéis pedidos
            int semanas = Integer.getInteger("futebas.capturas.semanas", 0);
            passos.add(() -> {
                for (int i = 0; i < semanas; i++) app.hub().jogo().avancarSemana();
                proximo();
            });
            for (String p : so.split(",")) {
                passos.add(() -> {
                    // "tabelas:est-rj" abre a tabela de uma competição; "competicao:id" abre a página dela
                    String[] partes = p.trim().split(":", 2);
                    if (partes.length == 2 && partes[0].equals("tabelas")) br.futebas.ui.telas.PainelTabelas.focar(partes[1]);
                    if (partes.length == 2 && partes[0].equals("competicao")) {
                        app.irParaHub("jogo");
                        app.hub().abrirCompeticao(partes[1]);
                    } else app.irParaHub(partes[0]);
                    esperar(700, () -> foto("so-" + p.trim().replace(':', '-'), this::proximo));
                });
            }
            passos.add(this::encerrar);
            proximo();
            return;
        }
        boolean rapido = Boolean.getBoolean("futebas.capturas.rapido");
        String[] paineis = rapido ? new String[] {"jogo"} : new String[] {"jogo", "caixa", "elenco", "escalacao", "treino", "base", "tabelas", "calendario", "selecoes", "mundo", "kings", "mercado",
                "financas", "clube"};
        for (String p : paineis) {
            passos.add(() -> {
                app.irParaHub(p);
                esperar(450, () -> foto("hub-" + p, this::proximo));
            });
        }
        passos.add(() -> {
            br.futebas.ui.telas.PainelBase.abrirSub20(app.hub());
            esperar(450, () -> foto("hub-base-sub20", this::proximo));
        });
        passos.add(() -> {
            app.irParaHub("transferencias");
            esperar(450, () -> foto("hub-transferencias", this::proximo));
        });
        // cada formação no campo da escalação
        if (!rapido) for (String f : br.futebas.partida.Formacoes.nomes()) {
            passos.add(() -> {
                app.sessao().jogo.definirTatica(f, null);
                app.irParaHub("escalacao");
                esperar(350, () -> foto("formacao-" + f.replace(' ', '-'), this::proximo));
            });
        }
        passos.add(() -> {
            app.sessao().jogo.definirTatica("4-4-2", null);
            app.irParaHub("escalacao");
            esperar(400, () -> {
                // simula o arraste: vagas-alvo coloridas pela adequação
                var vagas = new java.util.ArrayList<>(app.cena().getRoot().lookupAll(".vaga"));
                if (vagas.size() >= 11) {
                    vagas.get(9).getStyleClass().add("alvo-bom");
                    vagas.get(1).getStyleClass().add("alvo-ruim");
                    vagas.get(6).getStyleClass().add("alvo-medio");
                }
                esperar(200, () -> foto("escalacao-arrastar", this::proximo));
            });
        });
        passos.add(() -> {
            app.sessao().jogo.escalacaoAutomatica();
            proximo();
        });
        // pré-jogo
        passos.add(() -> {
            app.irParaHub("jogo");
            var pj = app.sessao().fluxo.proximoJogo();
            if (pj != null) app.hub().abrirPreJogo(pj);
            esperar(500, () -> foto("pre-jogo", () -> {
                app.irParaHub("jogo");
                proximo();
            }));
        });
        // uma partida ao vivo
        passos.add(() -> {
            app.jogar();
            esperarCond(() -> app.processando() || app.telaAtual() instanceof TelaPartida, 0, () -> {
                if (app.processando()) esperar(500, () -> fotoAgora("avancando-calendario", () -> {}));
            });
            esperarCond(() -> app.telaAtual() instanceof TelaPartida tp && tp.minuto() >= 3, 0,
                    () -> fotoAgora("partida-inicio", () -> esperarCond(() -> app.telaAtual() instanceof TelaPartida tp && (tp.minuto() >= 30 || tp.painelAberto()), 0,
                            () -> fotoAgora("partida-ao-vivo", () -> {
                                if (app.telaAtual() instanceof TelaPartida tp && !tp.painelAberto()) {
                                    tp.acompanharOutro(0);
                                    esperar(150, () -> fotoAgora("partida-outro-jogo", () -> {
                                        if (app.telaAtual() instanceof TelaPartida t2) t2.acompanharOutro(-1);
                                        proximo();
                                    }));
                                } else proximo();
                            }))));
        });
        passos.add(() -> esperarCond(() -> app.telaAtual() instanceof TelaPartida tp && (tp.painelAberto() || tp.terminou()), 0,
                () -> esperar(250, () -> fotoAgora("partida-intervalo", this::proximo))));
        passos.add(() -> esperarAte(() -> app.telaAtual() instanceof TelaPartida tp && tp.terminou() || !(app.telaAtual() instanceof TelaPartida),
                () -> esperar(500, () -> foto("partida-resultado", this::proximo))));
        if (rapido) {
            passos.add(this::encerrar);
            proximo();
            return;
        }
        // mais duas rodadas pela interface (instantâneo)
        for (int i = 0; i < 2; i++) {
            passos.add(() -> {
                if (app.telaAtual() instanceof TelaPartida) app.cena().getRoot().fireEvent(tecla(javafx.scene.input.KeyCode.ENTER));
                esperar(300, () -> {
                    app.jogar();
                    if (app.telaAtual() instanceof TelaPartida) app.cena().getRoot().fireEvent(tecla(javafx.scene.input.KeyCode.DIGIT4));
                    esperarCond(() -> !(app.telaAtual() instanceof TelaPartida tp) || tp.terminou() || tp.entreJogos(), 0, () -> {
                        if (app.telaAtual() instanceof TelaPartida tp && tp.entreJogos()) {
                            // outro jogo na mesma semana: volta à central e retoma pelo JOGAR
                            app.cena().getRoot().fireEvent(tecla(javafx.scene.input.KeyCode.ENTER));
                            esperar(400, () -> {
                                fotoAgora("central-entre-jogos", () -> {
                                    app.jogar();
                                    if (app.telaAtual() instanceof TelaPartida) app.cena().getRoot().fireEvent(tecla(javafx.scene.input.KeyCode.DIGIT4));
                                    esperarCond(() -> !(app.telaAtual() instanceof TelaPartida t2) || t2.terminou() || t2.entreJogos(), 0, () -> {
                                        if (app.telaAtual() instanceof TelaPartida) app.cena().getRoot().fireEvent(tecla(javafx.scene.input.KeyCode.ENTER));
                                        esperar(300, this::proximo);
                                    });
                                });
                            });
                        } else {
                            proximo();
                        }
                    });
                });
            });
        }
        // termina jogos pendentes da semana antes de simular o resto
        for (int k = 0; k < 3; k++) {
            passos.add(() -> {
                if (!app.temPendente()) {
                    proximo();
                    return;
                }
                app.jogar();
                if (app.telaAtual() instanceof TelaPartida) app.cena().getRoot().fireEvent(tecla(javafx.scene.input.KeyCode.ESCAPE));
                esperarCond(() -> !(app.telaAtual() instanceof TelaPartida t2) || t2.terminou() || t2.entreJogos(), 0, () -> {
                    if (app.telaAtual() instanceof TelaPartida) app.cena().getRoot().fireEvent(tecla(javafx.scene.input.KeyCode.ENTER));
                    esperar(300, this::proximo);
                });
            });
        }
        // o resto da temporada pelo laço do jogo (sem narração), até o resumo do fim de temporada
        passos.add(() -> {
            Sessao s = app.sessao();
            app.irParaHub("jogo");
            app.trabalhar("Jogando o resto da temporada...", () -> {
                AcompanhamentoPartida a = new AcompanhamentoPartida(null, AcompanhamentoPartida.Velocidade.INSTANTANEA, null);
                for (int k = 0; k < 120; k++) {
                    Fluxo.Rodada r = s.fluxo.jogarProximaRodada(s.jogo.meuClube() == null ? null : new AcompanhamentoPartida(s.jogo.meuClube().id, AcompanhamentoPartida.Velocidade.INSTANTANEA, null));
                    if (r.fimDeTemporada() != null) return r;
                    if (r.demitido() || s.jogo.meuClube() == null) {
                        Fluxo.ResumoTemporada fim = s.fluxo.avancarTemporada();
                        return new Fluxo.Rodada(r.ano(), r.semana(), r.meusJogos(), r.narradas(), r.outrosResultados(), r.novasMensagens(), fim, true);
                    }
                }
                return null;
            }, r -> {
                if (r != null && r.fimDeTemporada() != null) app.mostrar(new TelaFimTemporada(s, r.fimDeTemporada()));
                esperar(600, () -> foto("fim-de-temporada", this::proximo));
            });
        });
        passos.add(() -> {
            app.irParaHub("tabelas");
            esperar(500, () -> foto("tabelas-nova-temporada", this::proximo));
        });
        passos.add(() -> {
            app.irParaHub("selecoes");
            esperar(500, () -> foto("selecoes-apos-1-temporada", this::proximo));
        });
        passos.add(() -> {
            app.irParaHub("premios");
            esperar(500, () -> foto("premios", this::proximo));
        });
        passos.add(() -> {
            app.irParaHub("jogo");
            Hub h = app.hub();
            String outro = app.sessao().jogo.meuClube() != null && "palmeiras".equals(app.sessao().jogo.meuClube().id) ? "flamengo" : "palmeiras";
            if (h != null) h.abrirFichaClube(outro);
            esperar(500, () -> foto("ficha-clube", this::proximo));
        });
        passos.add(() -> {
            Hub h = app.hub();
            var j = app.sessao().jogo;
            var t = j.treinadorDoClube("palmeiras");
            if (h != null && t != null) h.abrirFicha(br.futebas.ui.telas.Fichas.treinador(h, t, () -> app.irParaHub("jogo")));
            esperar(500, () -> foto("ficha-tecnico", this::proximo));
        });
        passos.add(() -> {
            Hub h = app.hub();
            if (h != null) h.abrirCompeticao("bra-1");
            esperar(600, () -> foto("pagina-competicao", this::proximo));
        });
        passos.add(() -> {
            Hub h = app.hub();
            var j = app.sessao().jogo;
            var cm = j.copaDoMundo();
            if (h != null) h.abrirCompeticao(cm != null ? cm.id : br.futebas.selecao.Selecoes.COPA);
            esperar(600, () -> foto("pagina-copa", this::proximo));
        });
        passos.add(() -> {
            Hub h = app.hub();
            if (h != null) h.abrirParticipante("sel-BRA");
            esperar(600, () -> foto("pagina-selecao", this::proximo));
        });
        passos.add(() -> {
            Hub h = app.hub();
            var j = app.sessao().jogo;
            String alvo = null;
            for (var l : j.ligasKings()) if (!l.times.isEmpty() && l.ativa) {
                alvo = l.times.get(0).id;
                break;
            }
            if (h != null && alvo != null) h.abrirParticipante(alvo);
            esperar(600, () -> foto("pagina-kings", this::proximo));
        });
        passos.add(() -> {
            Hub h = app.hub();
            var j = app.sessao().jogo;
            for (var r : j.buscar("Neymar", 5)) {
                if (!"jogador".equals(r.tipo())) continue;
                var x = j.jogador(Integer.parseInt(r.id()));
                if (x != null && h != null) {
                    h.abrirFichaJogador(x);
                    break;
                }
            }
            esperar(600, () -> foto("ficha-jogador", this::proximo));
        });
        passos.add(() -> {
            br.futebas.ui.telas.PainelTabelas.focar("bra-1");
            app.irParaHub("tabelas");
            esperar(500, () -> {
                app.cena().getRoot().lookupAll(".tab-pane").stream().findFirst().ifPresent(n -> {
                    var tp = (javafx.scene.control.TabPane) n;
                    tp.getSelectionModel().select(tp.getTabs().size() - 1);
                });
                esperar(500, () -> foto("campeoes-historicos", this::proximo));
            });
        });
        String[] abasMundo = {"campeoes", "historico", "artilheiros", "tecnico", "estrelas", "recordes", "kings"};
        for (int i = 0; i < abasMundo.length; i++) {
            int aba = i;
            passos.add(() -> {
                new br.futebas.ui.telas.PainelMundo(app.sessao()).selecionarAba(aba);
                app.irParaHub("mundo");
                esperar(500, () -> foto("mundo-" + abasMundo[aba], this::proximo));
            });
        }
        passos.add(() -> {
            new br.futebas.ui.telas.PainelMundo(app.sessao()).selecionarAba(0);
            proximo();
        });
        // o técnico pede demissão e espera convites
        passos.add(() -> {
            Sessao s = app.sessao();
            app.trabalhar("Procurando convites...", () -> {
                if (s.jogo.meuClube() != null) s.jogo.pedirDemissao();
                for (int k = 0; k < 30 && s.jogo.propostasDeEmprego().stream().noneMatch(p -> p.clubeId != null); k++) s.fluxo.avancarSemana();
                return s;
            }, x -> {
                app.abrir(x);
                esperar(600, () -> foto("desempregado", this::proximo));
            });
        });
        // carreira da Kings League (exclusiva: o técnico treina só o time da Kings, sem clube)
        passos.add(() -> app.trabalhar("Montando o mundo de 2026...", () -> {
            String time = primeiroTimeKings(db);
            Sessao s = new Sessao(time == null ? Jogo.novoJogo(db, 2023, clube, "Técnico de Teste") : Jogo.novoJogoKings(db, 2023, null, time, "Técnico de Teste"), "capturas-kings");
            for (int i = 0; i < 4; i++) s.fluxo.jogarProximaRodada(null);
            return s;
        }, s -> {
            app.abrir(s);
            app.irParaHub("kings");
            esperar(600, () -> foto("hub-kings-como-tecnico", this::proximo));
        }));
        // um jogo da Kings ao vivo (aparece por cima da partida quando a semana tem jogo do time da Kings)
        for (int i = 0; i < 6; i++) {
            passos.add(() -> {
                if (capturouKings) {
                    proximo();
                    return;
                }
                app.jogar();
                esperarCond(() -> app.telaAtual() instanceof TelaPartida tp && (tp.kingsVisivel() || tp.terminou() || tp.entreJogos()), 0, () -> {
                    if (app.telaAtual() instanceof TelaPartida tp && tp.kingsVisivel()) {
                        capturouKings = true;
                        esperar(2500, () -> fotoAgora("kings-ao-vivo", () -> esperarCond(() -> !(app.telaAtual() instanceof TelaPartida t2) || t2.terminou() || t2.entreJogos(), 0,
                                () -> continuarPartida(this::proximo))));
                    } else {
                        esperarCond(() -> !(app.telaAtual() instanceof TelaPartida t2) || t2.terminou() || t2.entreJogos(), 0, () -> continuarPartida(this::proximo));
                    }
                });
            });
        }
        passos.add(this::encerrar);
        // vigia: se um passo travar (erro no motor), encerra em vez de ficar parado
        javafx.animation.Timeline vigia = new javafx.animation.Timeline(new javafx.animation.KeyFrame(Duration.seconds(5), e -> {
            if (System.currentTimeMillis() - ultimoProgresso > 240_000) {
                System.out.println("[captura] travou depois de " + n + " telas");
                App.errosAutomaticos.add("captura travada depois de " + n + " telas");
                encerrar();
            }
        }));
        vigia.setCycleCount(javafx.animation.Animation.INDEFINITE);
        vigia.play();
        proximo();
    }

    private static javafx.scene.input.KeyEvent tecla(javafx.scene.input.KeyCode k) {
        return new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", k, false, false, false, false);
    }

    private boolean capturouKings;

    /** Primeiro time da Kings League Brasil (ou de qualquer liga) no banco; null se não há Kings. */
    private static String primeiroTimeKings(br.futebas.dados.BancoMundo db) {
        String outro = null;
        for (var kt : db.kingsTimes.values()) {
            if ("kings-brasil".equals(kt.ligaId())) return kt.id();
            if (outro == null) outro = kt.id();
        }
        return outro;
    }

    /**
     * Roteiro curto da carreira da Kings ({@code -Dfutebas.capturas.kings=true}): a barra de baixo
     * do jogo novo com um time da Kings escolhido (no lugar do clube), a central da carreira só da
     * Kings (Kings League, calendário, caixa, mundo), o fim de temporada e a tela sem emprego.
     */
    /**
     * Roteiro curto da página inicial e da caixa de entrada em 1366×768 e 1920×1080: carreira de
     * clube depois de algumas rodadas ({@code futebas.capturas.semanas}, padrão 6) e carreira só da
     * Kings com o split andando.
     */
    private void roteiroInicial(String clube) {
        int rodadas = Integer.getInteger("futebas.capturas.semanas", 6);
        int[][] tamanhos = {{1366, 768}, {1920, 1080}};
        passos.add(() -> app.trabalhar("Abrindo o banco...", Sessao::banco, b -> {
            db = b;
            proximo();
        }));
        passos.add(() -> app.trabalhar("Montando o mundo de 2026...", () -> {
            String id = db.clubes.containsKey(clube) ? clube : null;
            if (id == null) for (Jogo.OpcaoClube o : Jogo.clubesDisponiveis(db)) if (Integer.valueOf(1).equals(o.divisao())) { id = o.id(); break; }
            Sessao s = new Sessao(Jogo.novoJogo(db, 1995, id, "Técnico de Teste"), "capturas-inicial");
            for (int i = 0; i < rodadas; i++) s.fluxo.jogarProximaRodada(null);
            return s;
        }, s -> {
            app.abrir(s);
            esperar(500, this::proximo);
        }));
        for (int[] t : tamanhos) for (String p : new String[] {"jogo", "caixa"}) {
            passos.add(() -> {
                tamanho(t[0], t[1]);
                app.irParaHub(p);
                esperar(900, () -> foto("inicial-" + p + "-" + t[0] + "x" + t[1], this::proximo));
            });
        }
        passos.add(() -> app.trabalhar("Montando a carreira da Kings...", () -> {
            Sessao s = new Sessao(Jogo.novoJogoKings(db, 2025, null, primeiroTimeKings(db), "Técnico de Teste"), "capturas-inicial-kings");
            for (int i = 0; i < 10; i++) s.fluxo.avancarSemana();
            return s;
        }, s -> {
            app.abrir(s);
            esperar(500, this::proximo);
        }));
        for (int[] t : tamanhos) for (String p : new String[] {"jogo", "caixa"}) {
            passos.add(() -> {
                tamanho(t[0], t[1]);
                app.irParaHub(p);
                esperar(900, () -> foto("inicial-kings-" + p + "-" + t[0] + "x" + t[1], this::proximo));
            });
        }
        passos.add(this::encerrar);
        proximo();
    }

    private void roteiroKings() {
        passos.add(() -> app.trabalhar("Abrindo o banco...", Sessao::banco, b -> {
            db = b;
            app.novoJogo();
            esperarLivre(() -> esperar(800, () -> {
                String time = primeiroTimeKings(db);
                if (app.telaAtual() instanceof br.futebas.ui.telas.TelaNovoJogo t) {
                    var kt = time == null ? null : db.kingsTimes.get(time);
                    t.selecionarLiga("KINGS", kt == null ? "kings:kings-brasil" : "kings:" + kt.ligaId());
                    esperar(500, () -> {
                        t.selecionarTimeKings(time);
                        esperar(500, () -> foto("novo-jogo-kings-barra", this::proximo));
                    });
                } else proximo();
            }));
        }));
        passos.add(() -> app.trabalhar("Montando o mundo de 2026...", () -> {
            Sessao s = new Sessao(Jogo.novoJogoKings(db, 2025, null, primeiroTimeKings(db), "Técnico de Teste"), "capturas-so-kings");
            for (int i = 0; i < 10; i++) s.fluxo.avancarSemana();
            return s;
        }, s -> {
            app.abrir(s);
            esperar(800, () -> foto("kings-carreira-hub", this::proximo));
        }));
        for (String p : new String[] {"kings", "elenco", "escalacao", "mercado", "financas", "clube", "calendario", "caixa", "vagas", "tabelas", "mundo"}) {
            passos.add(() -> {
                app.irParaHub(p);
                esperar(600, () -> foto("kings-carreira-" + p, this::proximo));
            });
        }
        passos.add(() -> {
            Hub.abaKings("pool");
            app.irParaHub("kings");
            esperar(700, () -> foto("kings-carreira-no-draft", this::proximo));
        });
        // entradas na Kings fora do menu: página do próprio time, de outro time, da competição e a pesquisa
        passos.add(() -> {
            Hub.abaKings("competicoes");
            Jogo j = app.sessao().jogo;
            app.irParaHub("jogo");
            app.hub().abrirParticipante(j.mundo().tecnico.kingsTimeId);
            esperar(700, () -> foto("kings-carreira-pagina-time", this::proximo));
        });
        passos.add(() -> {
            Jogo j = app.sessao().jogo;
            var meu = j.meuTimeKings();
            var liga = meu == null ? null : j.ligaKingsDoTime(meu.id);
            String outro = null;
            if (liga != null) for (var t : liga.times) if (!t.id.equals(meu.id)) { outro = t.id; break; }
            app.irParaHub("jogo");
            if (outro != null) app.hub().abrirParticipante(outro);
            esperar(700, () -> foto("kings-carreira-pagina-outro-time", this::proximo));
        });
        passos.add(() -> {
            Jogo j = app.sessao().jogo;
            var meu = j.meuTimeKings();
            var liga = meu == null ? null : j.ligaKingsDoTime(meu.id);
            app.irParaHub("jogo");
            if (liga != null && liga.torneio != null) app.hub().abrirCompeticao(liga.torneio.id);
            esperar(700, () -> foto("kings-carreira-pagina-competicao", this::proximo));
        });
        passos.add(() -> {
            Jogo j = app.sessao().jogo;
            var meu = j.meuTimeKings();
            app.irParaHub("jogo");
            if (meu != null) app.hub().pesquisar(meu.nome.length() > 5 ? meu.nome.substring(0, 5) : meu.nome);
            esperar(700, () -> foto("kings-carreira-pesquisa", () -> {
                app.hub().pesquisar("");
                proximo();
            }));
        });
        // o JOGAR da carreira da Kings vai direto para o jogo da Kings ao vivo
        for (int i = 0; i < 4; i++) {
            passos.add(() -> {
                if (capturouKings) {
                    proximo();
                    return;
                }
                app.jogar();
                esperarCond(() -> app.telaAtual() instanceof TelaPartida tp && (tp.kingsVisivel() || tp.terminou() || tp.entreJogos()), 0, () -> {
                    if (app.telaAtual() instanceof TelaPartida tp && tp.kingsVisivel()) {
                        capturouKings = true;
                        esperar(2500, () -> fotoAgora("kings-carreira-ao-vivo", () -> esperarCond(() -> !(app.telaAtual() instanceof TelaPartida t2) || t2.terminou() || t2.entreJogos(), 0,
                                () -> continuarPartida(this::proximo))));
                    } else {
                        esperarCond(() -> !(app.telaAtual() instanceof TelaPartida t2) || t2.terminou() || t2.entreJogos(), 0, () -> continuarPartida(this::proximo));
                    }
                });
            });
        }
        passos.add(() -> {
            app.irParaHub("kings");
            esperar(600, () -> foto("kings-carreira-depois-do-jogo", this::proximo));
        });
        passos.add(() -> {
            Sessao s = app.sessao();
            app.trabalhar("Até o fim da temporada...", () -> {
                Fluxo.ResumoTemporada fim = null;
                for (int i = 0; i < 60 && fim == null; i++) fim = s.fluxo.avancarSemana().fimDeTemporada();
                return fim;
            }, fim -> {
                if (fim != null) app.mostrar(new br.futebas.ui.telas.TelaFimTemporada(s, fim));
                esperar(700, () -> foto("kings-carreira-fim-temporada", this::proximo));
            });
        });
        // o draft de 2027 (o auxiliar escolhe se o técnico não escolher) e a aba Draftados
        passos.add(() -> {
            Sessao s = app.sessao();
            app.trabalhar("Draft...", () -> {
                for (int i = 0; i < 4; i++) s.fluxo.avancarSemana();
                return s;
            }, x -> {
                Hub.abaKings("draftados");
                app.abrir(x);
                app.irParaHub("kings");
                esperar(700, () -> foto("kings-carreira-draftados", () -> {
                    Hub.abaKings("competicoes");
                    proximo();
                }));
            });
        });
        passos.add(() -> {
            Sessao s = app.sessao();
            app.trabalhar("Deixando o time...", () -> {
                s.jogo.pedirDemissao();
                s.fluxo.avancarSemana();
                return s;
            }, x -> {
                app.abrir(x);
                esperar(700, () -> foto("kings-carreira-sem-emprego", this::proximo));
            });
        });
        passos.add(this::encerrar);
        proximo();
    }

    /**
     * Roteiro curto dos times customizados ({@code -Dfutebas.capturas.ios=true}, com um nó de
     * preferências próprio em {@code -Dfutebas.prefs}): liga a opção, seletor do jogo novo com a
     * Kings League IOS, e um jogo como técnico de um time da IOS (página da liga e do time).
     */
    private void roteiroIos() {
        Opcoes.timesCustomizados(true);
        passos.add(() -> app.trabalhar("Abrindo o banco...", Sessao::banco, b -> {
            db = b;
            app.novoJogo();
            esperarLivre(() -> esperar(800, () -> {
                if (app.telaAtual() instanceof br.futebas.ui.telas.TelaNovoJogo t) t.selecionarLiga("KINGS", "kings:kings-ios");
                esperar(800, () -> foto("novo-jogo-kings-ios", this::proximo));
            }));
        }));
        passos.add(() -> {
            var f = br.futebas.kings.KingsCustom.localizar(Caminhos.raiz());
            var def = f == null ? null : br.futebas.kings.KingsCustom.carregar(f);
            String time = def == null || def.times.isEmpty() ? null : def.times.get(10).id;
            app.trabalhar("Montando o mundo de 2026...", () -> new Sessao(Jogo.novoJogoKings(db, 2024, null, time, "Técnico de Teste", def), "capturas-ios"), s -> {
                app.abrir(s);
                esperar(500, this::proximo);
            });
        });
        passos.add(() -> {
            var j = app.hub().jogo();
            for (int i = 0; i < 9; i++) {
                if (j.meuClube() != null) j.meuClube().confiancaDiretoria = 90;
                j.avancarSemana();
            }
            app.irParaHub("kings");
            esperar(700, () -> foto("hub-kings-ios", this::proximo));
        });
        passos.add(() -> {
            app.hub().abrirCompeticao("kings-ios-split1");
            esperar(700, () -> foto("pagina-kings-league-ios", this::proximo));
        });
        passos.add(() -> {
            app.hub().abrirCompeticao("kings-ios-cup");
            esperar(700, () -> foto("pagina-kings-cup-ios", this::proximo));
        });
        passos.add(() -> {
            var j = app.hub().jogo();
            app.hub().abrirParticipante(j.mundo().tecnico.kingsTimeId);
            esperar(700, () -> foto("pagina-time-ios", this::proximo));
        });
        passos.add(() -> {
            app.hub().abrirParticipante("kings-brasil-g3x");
            esperar(700, () -> foto("pagina-g3x-kelvin", this::proximo));
        });
        passos.add(this::encerrar);
        proximo();
    }

    // ================================================================== portfólio (prints e sequências do trailer)
    /**
     * Roteiro do portfólio ({@code -Dfutebas.capturas.portfolio=true}, de preferência com um nó de
     * preferências próprio em {@code -Dfutebas.prefs}): em 1920×1080, os prints (tela inicial, tática
     * de um clube grande, partida ao vivo, Kings League oficial, tabela, mercado, vagas) e três
     * sequências de quadros para o trailer (partida, Kings e navegação), cada uma numa subpasta com
     * {@code times.json} ([["00000.jpg", segundos], ...]) e, na navegação, {@code clicks.json}
     * ([[segundos, x, y], ...]). Nunca usa os times customizados.
     */
    private void roteiroPortfolio(String clube) {
        Opcoes.timesCustomizados(false);
        Opcoes.velocidade(AcompanhamentoPartida.Velocidade.RAPIDA);
        long semente = Long.getLong("futebas.capturas.semente", 1995);
        int semanas = Integer.getInteger("futebas.capturas.semanas", 18);
        // partes: prints, nav, partida, kings (padrão: todas)
        java.util.Set<String> partes = new java.util.HashSet<>(java.util.List.of(System.getProperty("futebas.capturas.partes", "prints,nav,partida,kings").split(",")));
        boolean comClube = partes.contains("prints") || partes.contains("nav") || partes.contains("partida");
        passos.add(() -> {
            tamanho(1920, 1080);
            if (partes.contains("prints")) esperar(1500, () -> fotoNome("tela-inicial", this::proximo));
            else esperar(500, this::proximo);
        });
        passos.add(() -> app.trabalhar("Abrindo o banco...", Sessao::banco, b -> {
            db = b;
            proximo();
        }));
        if (comClube) passos.add(() -> app.trabalhar("Montando o mundo de 2026...", () -> {
            String id = db.clubes.containsKey(clube) ? clube : null;
            if (id == null) for (Jogo.OpcaoClube o : Jogo.clubesDisponiveis(db)) if (Integer.valueOf(1).equals(o.divisao())) { id = o.id(); break; }
            Sessao s = new Sessao(Jogo.novoJogo(db, semente, id, "Técnico"), "capturas-portfolio");
            for (int i = 0; i < semanas; i++) {
                if (s.jogo.meuClube() != null) s.jogo.meuClube().confiancaDiretoria = 90;
                s.fluxo.jogarProximaRodada(null);
            }
            s.jogo.escalacaoAutomatica();
            return s;
        }, s -> {
            app.abrir(s);
            tamanho(1920, 1080);
            esperar(800, this::proximo);
        }));
        if (partes.contains("prints")) passos.add(() -> {
            app.irParaHub("escalacao");
            esperar(1200, () -> fotoNome("tatica", this::proximo));
        });
        if (partes.contains("prints")) passos.add(() -> {
            br.futebas.ui.telas.PainelTabelas.focar("bra-1");
            app.irParaHub("tabelas");
            esperar(1200, () -> fotoNome("tabela", this::proximo));
        });
        if (partes.contains("prints")) passos.add(() -> {
            app.irParaHub("mercado");
            esperar(1500, () -> fotoNome("mercado", this::proximo));
        });
        if (partes.contains("prints")) passos.add(() -> {
            app.irParaHub("vagas");
            esperar(1000, () -> fotoNome("vagas", this::proximo));
        });
        // navegação: Próximo jogo → Escalação e tática (troca a formação) → Mercado (escolhe um jogador)
        if (partes.contains("nav")) passos.add(() -> {
            app.irParaHub("jogo");
            esperar(1200, () -> {
                Gravador g = new Gravador("seq-navegacao");
                g.iniciar();
                esperar(900, () -> clicarNav(g, "Escalação e tática", () -> esperar(2200, () -> {
                    javafx.scene.control.ChoiceBox<?> cb = (javafx.scene.control.ChoiceBox<?>) app.cena().getRoot().lookupAll(".choice-box").stream()
                            .filter(n -> n instanceof javafx.scene.control.ChoiceBox<?> c && c.getItems().contains("4-3-3")).findFirst().orElse(null);
                    if (cb != null) {
                        g.clique(cb);
                        int atual = cb.getSelectionModel().getSelectedIndex();
                        cb.getSelectionModel().select(atual == 1 ? 0 : 1);
                    }
                    esperar(2200, () -> clicarNav(g, "Mercado", () -> esperar(1500, () -> {
                        var tabela = app.cena().getRoot().lookupAll(".table-view").stream().findFirst().orElse(null);
                        if (tabela instanceof javafx.scene.control.TableView<?> tv && tv.getItems().size() > 3) {
                            var linhas = new java.util.ArrayList<>(tv.lookupAll(".table-row-cell"));
                            if (linhas.size() > 3) g.clique(linhas.get(3));
                            tv.getSelectionModel().select(2);
                        }
                        esperar(1800, () -> g.parar(() -> {
                            app.sessao().jogo.escalacaoAutomatica();
                            proximo();
                        }));
                    })));
                })));
            });
        });
        // partida ao vivo: o jogo inteiro andando em ~15 s (relógio, narração, gols) e o print logo depois
        if (partes.contains("partida")) passos.add(() -> {
            app.irParaHub("jogo");
            AcompanhamentoPartida.escala = Double.parseDouble(System.getProperty("futebas.capturas.escalaPartida", "0.38"));
            app.jogar();
            esperarCond(() -> app.telaAtual() instanceof TelaPartida tp && tp.minuto() >= 1, 0, () -> {
                Gravador g = new Gravador("seq-partida");
                g.iniciar();
                long ini = System.currentTimeMillis();
                esperarCond(() -> System.currentTimeMillis() - ini > 15_000 || !(app.telaAtual() instanceof TelaPartida tp) || tp.terminou()
                                || tp.minuto() >= 88, 0,
                        () -> g.parar(() -> esperarCond(() -> !(app.telaAtual() instanceof TelaPartida tp) || !tp.painelAberto(), 0, () -> fotoNome("partida", () -> {
                            AcompanhamentoPartida.escala = 0.05;
                            esperarCond(() -> !(app.telaAtual() instanceof TelaPartida t2) || t2.terminou() || t2.entreJogos(), 0,
                                    () -> continuarPartida(this::proximo));
                        }))));
            });
        });
        // outros jogos da semana (o trabalho da semana espera por eles): joga sem esperar
        if (partes.contains("partida")) for (int k = 0; k < 3; k++) {
            passos.add(() -> {
                if (!app.temPendente() && !(app.telaAtual() instanceof TelaPartida tp && tp.entreJogos())) {
                    proximo();
                    return;
                }
                app.jogar();
                if (app.telaAtual() instanceof TelaPartida) app.cena().getRoot().fireEvent(tecla(javafx.scene.input.KeyCode.ESCAPE));
                esperarCond(() -> !(app.telaAtual() instanceof TelaPartida t2) || t2.terminou() || t2.entreJogos(), 0, () -> continuarPartida(this::proximo));
            });
        }
        // Kings League oficial (carreira da Kings num time da Kings League Brasil)
        if (partes.contains("kings")) passos.add(() -> app.trabalhar("Montando a carreira da Kings...", () -> {
            Sessao s = new Sessao(Jogo.novoJogoKings(db, semente, null, primeiroTimeKings(db), "Técnico"), "capturas-portfolio-kings");
            for (int i = 0; i < 10; i++) s.fluxo.avancarSemana();
            return s;
        }, s -> {
            app.abrir(s);
            tamanho(1920, 1080);
            esperar(800, this::proximo);
        }));
        if (partes.contains("kings")) for (int i = 0; i < 6; i++) {
            passos.add(() -> {
                if (capturouKings) {
                    proximo();
                    return;
                }
                AcompanhamentoPartida.escala = 1;
                br.futebas.ui.modelo.AcompanhamentoKings.msAutomatico = Integer.getInteger("futebas.capturas.kingsMs", 450);
                app.jogar();
                esperarCond(() -> app.telaAtual() instanceof TelaPartida tp && (tp.kingsVisivel() || tp.terminou() || tp.entreJogos()), 0, () -> {
                    if (app.telaAtual() instanceof TelaPartida tp && tp.kingsVisivel()) {
                        capturouKings = true;
                        // começa perto do fim do 1º tempo: gol duplo (17'), o dado (20'–23') e o pênalti do presidente
                        esperar(Integer.getInteger("futebas.capturas.kingsInicio", 6_000), () -> {
                            Gravador g = new Gravador("seq-kings");
                            g.iniciar();
                            tentarPresidente(g, 0);
                            int dur = Integer.getInteger("futebas.capturas.kingsDuracao", 9_500);
                            esperar(Integer.getInteger("futebas.capturas.kingsFoto", 4_500), () -> fotoNome("kings", () -> {}));
                            esperar(dur, () -> g.parar(() -> {
                                br.futebas.ui.modelo.AcompanhamentoKings.msAutomatico = 150;
                                esperarCond(() -> !(app.telaAtual() instanceof TelaPartida t2) || t2.terminou() || t2.entreJogos(), 0,
                                        () -> continuarPartida(this::proximo));
                            }));
                        });
                    } else {
                        esperarCond(() -> !(app.telaAtual() instanceof TelaPartida t2) || t2.terminou() || t2.entreJogos(), 0, () -> continuarPartida(this::proximo));
                    }
                });
            });
        }
        passos.add(this::encerrar);
        javafx.animation.Timeline vigia = new javafx.animation.Timeline(new javafx.animation.KeyFrame(Duration.seconds(5), e -> {
            if (System.currentTimeMillis() - ultimoProgresso > 600_000) {
                System.out.println("[captura] travou depois de " + n + " telas");
                encerrar();
            }
        }));
        vigia.setCycleCount(javafx.animation.Animation.INDEFINITE);
        vigia.play();
        proximo();
    }

    /** O botão "Presidente" da Kings, quando liberado (um clique de verdade, gravado). */
    private void tentarPresidente(Gravador g, int tentativa) {
        if (tentativa > 14) return;
        var b = app.cena().getRoot().lookupAll(".kings-botao-icone").stream()
                .filter(x -> x instanceof javafx.scene.control.Button bt && "Presidente".equals(bt.getText())).findFirst().orElse(null);
        if (b instanceof javafx.scene.control.Button bt && !bt.isDisabled() && bt.isVisible()) {
            esperar(1200, () -> {
                g.clique(bt);
                bt.fire();
            });
            return;
        }
        esperar(500, () -> tentarPresidente(g, tentativa + 1));
    }

    /** Clica (de verdade) no item do menu lateral com esse nome e grava o clique. */
    private void clicarNav(Gravador g, String nome, Runnable depois) {
        javafx.scene.Node alvo = null;
        for (javafx.scene.Node n : app.cena().getRoot().lookupAll(".nav-texto")) {
            if (n instanceof javafx.scene.control.Label l && nome.equals(l.getText())) {
                javafx.scene.Node p = n;
                while (p != null && !(p instanceof javafx.scene.control.Button)) p = p.getParent();
                alvo = p;
                break;
            }
        }
        if (alvo instanceof javafx.scene.control.Button b) {
            g.clique(b);
            b.fire();
        }
        depois.run();
    }

    /** Print com nome fixo (sem número), na pasta das capturas. */
    private void fotoNome(String nome, Runnable depois) {
        try {
            WritableImage img = app.cena().getRoot().snapshot(null, null);
            Path f = pasta.resolve(nome + ".png");
            ImageIO.write(converter(img), "png", f.toFile());
            System.out.println("[captura] " + f.toAbsolutePath() + " " + (int) img.getWidth() + "x" + (int) img.getHeight());
        } catch (IOException | RuntimeException e) {
            app.erro(e);
        }
        depois.run();
    }

    /**
     * Grava a cena em quadros JPG (~12 por segundo) numa subpasta, com o instante real de cada quadro
     * ({@code times.json}) e os cliques ({@code clicks.json}, se houver). A conversão e a gravação dos
     * arquivos rodam fora da thread da tela.
     */
    private final class Gravador {
        private final Path dir;
        private final java.util.List<String> tempos = new java.util.ArrayList<>();
        private final java.util.List<String> cliques = new java.util.ArrayList<>();
        private final java.util.concurrent.ExecutorService escritor = java.util.concurrent.Executors.newFixedThreadPool(3);
        private javafx.animation.AnimationTimer timer;
        private long t0, ultimo;
        private int quadros;

        Gravador(String nome) {
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

        void iniciar() {
            t0 = System.nanoTime();
            ultimo = 0;
            timer = new javafx.animation.AnimationTimer() {
                @Override
                public void handle(long agora) {
                    if (ultimo != 0 && agora - ultimo < 80_000_000L) return;
                    ultimo = agora;
                    quadro();
                }
            };
            timer.start();
        }

        private void quadro() {
            double s = (System.nanoTime() - t0) / 1e9;
            WritableImage img = app.cena().getRoot().snapshot(null, null);
            String nome = String.format("%05d.jpg", quadros++);
            tempos.add(String.format(java.util.Locale.ROOT, "[\"%s\", %.3f]", nome, s));
            escritor.submit(() -> {
                try {
                    int w = (int) img.getWidth(), h = (int) img.getHeight();
                    BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    PixelReader pr = img.getPixelReader();
                    int[] linha = new int[w];
                    for (int y = 0; y < h; y++) {
                        pr.getPixels(0, y, w, 1, javafx.scene.image.PixelFormat.getIntArgbInstance(), linha, 0, w);
                        b.setRGB(0, y, w, 1, linha, 0, w);
                    }
                    var it = ImageIO.getImageWritersByFormatName("jpg").next();
                    var p = it.getDefaultWriteParam();
                    p.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                    p.setCompressionQuality(0.92f);
                    try (var out = ImageIO.createImageOutputStream(dir.resolve(nome).toFile())) {
                        it.setOutput(out);
                        it.write(null, new javax.imageio.IIOImage(b, null, null), p);
                    }
                    it.dispose();
                } catch (IOException e) {
                    System.out.println("[captura] erro no quadro " + nome + ": " + e);
                }
            });
        }

        /** Registra um clique no centro do nó (coordenadas da cena). */
        void clique(javafx.scene.Node n) {
            var b = n.localToScene(n.getBoundsInLocal());
            double s = (System.nanoTime() - t0) / 1e9;
            cliques.add(String.format(java.util.Locale.ROOT, "[%.3f, %d, %d]", s, Math.round(b.getCenterX()), Math.round(b.getCenterY())));
        }

        void parar(Runnable depois) {
            if (timer != null) timer.stop();
            quadro();
            escritor.shutdown();
            try {
                escritor.awaitTermination(2, java.util.concurrent.TimeUnit.MINUTES);
                Files.writeString(dir.resolve("times.json"), "[" + String.join(",\n", tempos) + "]\n");
                if (!cliques.isEmpty()) Files.writeString(dir.resolve("clicks.json"), "[" + String.join(",\n", cliques) + "]\n");
                System.out.println("[captura] " + dir.getFileName() + ": " + quadros + " quadros");
            } catch (IOException | InterruptedException e) {
                app.erro(new RuntimeException(e));
            }
            depois.run();
        }
    }

    private void continuarPartida(Runnable depois) {
        if (app.telaAtual() instanceof TelaPartida) app.cena().getRoot().fireEvent(tecla(javafx.scene.input.KeyCode.ENTER));
        esperar(400, depois);
    }

    private volatile long ultimoProgresso = System.currentTimeMillis();

    private void proximo() {
        ultimoProgresso = System.currentTimeMillis();
        Runnable r = passos.poll();
        if (r == null) return;
        try {
            r.run();
        } catch (RuntimeException e) {
            app.erro(e);
            proximo();
        }
    }

    private void esperar(int ms, Runnable depois) {
        PauseTransition p = new PauseTransition(Duration.millis(ms));
        p.setOnFinished(e -> depois.run());
        p.play();
    }

    private void esperarLivre(Runnable depois) {
        esperarAte(() -> !app.ocupado(), depois);
    }

    private void esperarAte(BooleanSupplier cond, Runnable depois) {
        esperarAte(cond, depois, 0);
    }

    private void esperarAte(BooleanSupplier cond, Runnable depois, int tentativas) {
        if (cond.getAsBoolean() && !app.ocupado() || tentativas > 3000) {
            depois.run();
            return;
        }
        esperar(100, () -> esperarAte(cond, depois, tentativas + 1));
    }

    /** Espera uma condição mesmo com o motor ocupado (partida rolando). */
    private void esperarCond(BooleanSupplier cond, int tentativas, Runnable depois) {
        if (cond.getAsBoolean() || tentativas > 900) {
            depois.run();
            return;
        }
        esperar(50, () -> esperarCond(cond, tentativas + 1, depois));
    }

    private void foto(String nome, Runnable depois) {
        esperarLivre(() -> fotoAgora(nome, depois));
    }

    private void fotoAgora(String nome, Runnable depois) {
        {
            try {
                WritableImage img = app.cena().getRoot().snapshot(null, null);
                Path f = pasta.resolve(String.format("%02d-%s.png", ++n, nome));
                ImageIO.write(converter(img), "png", f.toFile());
                System.out.println("[captura] " + f.toAbsolutePath());
            } catch (IOException | RuntimeException e) {
                app.erro(e);
            }
            depois.run();
        }
    }

    static BufferedImage converter(WritableImage img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader pr = img.getPixelReader();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) b.setRGB(x, y, pr.getArgb(x, y));
        return b;
    }

    private void tamanho(double w, double h) {
        double extraW = app.palco().getWidth() - app.cena().getWidth(), extraH = app.palco().getHeight() - app.cena().getHeight();
        app.palco().setWidth(w + extraW);
        app.palco().setHeight(h + extraH);
    }

    private void encerrar() {
        System.out.println("[captura] fim: " + n + " telas; erros: " + App.errosAutomaticos.size());
        for (String e : App.errosAutomaticos) System.out.println("[erro] " + e);
        if (!Boolean.getBoolean("futebas.capturas.manter")) {
            Platform.exit();
            System.exit(App.errosAutomaticos.isEmpty() ? 0 : 3);
        }
    }

    @SuppressWarnings("unused")
    private static Class<?> referencia() { return Hub.class; }
}
