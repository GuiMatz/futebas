package br.futebas.ui.telas;

import br.futebas.Balanceamento;
import br.futebas.Jogo;
import br.futebas.ia.PropostaJogador;
import br.futebas.mundo.Calendario;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.ui.comp.CampoDinheiro;
import br.futebas.ui.comp.Foto;
import br.futebas.ui.comp.Ui;
import br.futebas.ui.modelo.Textos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Central de transferências: visão geral (o que espera você, a janela e o orçamento), propostas
 * recebidas, propostas feitas, lista de transferências/empréstimos e as chegadas e saídas
 * (confirmadas e as que esperam a próxima janela). Todo negócio pede confirmação.
 */
final class PainelTransferencias implements Hub.Painel {
    private final Hub hub;
    private static String abaAntes = "geral";
    private static String filtroRecebidas = "andamento", filtroFeitas = "andamento";

    PainelTransferencias(Hub hub) {
        this.hub = hub;
    }

    /** Abre na aba pedida ("geral", "recebidas", "feitas", "lista", "movimentos") na próxima vez. */
    static void abrirAba(String aba) {
        abaAntes = aba;
    }

    /** Quantas propostas esperam uma ação sua (para o contador do menu). */
    static int pendentes(Jogo j, Clube c) {
        int n = 0;
        for (PropostaJogador p : j.propostasRecebidas()) if (c != null && c.id.equals(p.vendedorId) && p.minhaVez()) n++;
        for (PropostaJogador p : j.propostasEnviadas()) if (p.minhaVez()) n++;
        return n;
    }

    // ================================================================== janela
    /** Situação da janela de transferências do clube: aberta (até quando) ou fechada (abre quando). */
    record Janela(boolean aberta, LocalDate inicio, LocalDate fim, long dias, int semanaInicio, int semanaFim, int ano) {
        String texto() {
            if (aberta) return dias <= 0 ? "Fecha hoje" : dias == 1 ? "Fecha amanhã" : "Fecha em " + dias + " dias";
            return dias <= 1 ? "Abre amanhã" : "Abre em " + dias + " dias";
        }
    }

    static Janela janela(Jogo j, Clube c) {
        String cal = c == null ? "ano" : c.calendario;
        int[][] js = "europeu".equals(cal) ? Balanceamento.JANELAS_TRANSFERENCIA_EUROPEU : Balanceamento.JANELAS_TRANSFERENCIA_ANO;
        int s = j.semana(), ano = j.ano();
        LocalDate hoje = j.dataAtual();
        for (int[] x : js) {
            if (s >= x[0] && s <= x[1]) {
                LocalDate ini = Calendario.inicio(Calendario.absoluta(ano, x[0]));
                LocalDate fim = Calendario.inicio(Calendario.absoluta(ano, x[1])).plusDays(6);
                return new Janela(true, ini, fim, ChronoUnit.DAYS.between(hoje, fim), x[0], x[1], ano);
            }
        }
        for (int[] x : js) {
            if (x[0] > s) {
                LocalDate ini = Calendario.inicio(Calendario.absoluta(ano, x[0]));
                return new Janela(false, ini, Calendario.inicio(Calendario.absoluta(ano, x[1])).plusDays(6), ChronoUnit.DAYS.between(hoje, ini), x[0], x[1], ano);
            }
        }
        int[] x = js[0];
        LocalDate ini = Calendario.inicio(Calendario.absoluta(ano + 1, x[0]));
        return new Janela(false, ini, Calendario.inicio(Calendario.absoluta(ano + 1, x[1])).plusDays(6), ChronoUnit.DAYS.between(hoje, ini), x[0], x[1], ano + 1);
    }

    // ================================================================== situação (chips)
    /** Etiqueta colorida da situação da negociação. */
    static Label chip(PropostaJogador p) {
        String e = p.estado == null ? "" : p.estado;
        String texto = switch (e) {
            case "aguardando" -> "Aguardando resposta";
            case "pendente", "recebida" -> "Aguardando você";
            case "contraproposta" -> "Contraproposta";
            case "contraproposta_jogador" -> "Jogador pede mais";
            case "aceita" -> "Aceita · acertar contrato";
            case "aguardando_confirmacao" -> "Aceita · confirmar";
            case "aguardando_janela" -> "Chega na próxima janela";
            case "concluida" -> "Concluída";
            case "recusada" -> "Recusada";
            case "expirada" -> "Expirada";
            case "cancelada" -> "Cancelada";
            default -> p.situacao();
        };
        if ("aguardando_janela".equals(e)) {
            boolean sai = !"enviada".equals(p.direcao);
            texto = p.chegada() != null ? (sai ? "Sai em " : "Chega em ") + Textos.dataCurta(p.chegada()) : sai ? "Sai na próxima janela" : texto;
        }
        String cor = switch (e) {
            case "aguardando", "aguardando_janela" -> "azul";
            case "pendente", "recebida", "contraproposta", "contraproposta_jogador" -> "ambar";
            case "aceita", "aguardando_confirmacao", "concluida" -> "verde";
            case "recusada" -> "vermelha";
            default -> "escura";
        };
        Label l = Ui.etiqueta(texto, cor);
        l.getStyleClass().add("tr-chip");
        if (p.mensagem != null && !p.mensagem.isBlank()) javafx.scene.control.Tooltip.install(l, new javafx.scene.control.Tooltip(p.mensagem));
        return l;
    }

    private static String prazo(Jogo j, PropostaJogador p) {
        if (!p.ativa() || "aguardando_janela".equals(p.estado)) return "";
        LocalDate d = j.prazoProposta(p.id);
        if (d == null) return "";
        long dias = ChronoUnit.DAYS.between(j.dataAtual(), d);
        return dias <= 0 ? "Expira hoje" : dias == 1 ? "1 dia" : dias + " dias";
    }

    private static LocalDate criada(PropostaJogador p) {
        return Calendario.inicio(Math.floorDiv(p.criada, 7)).plusDays(Math.floorMod(p.criada, 7));
    }

    private static boolean recebida(PropostaJogador p) {
        return "recebida".equals(p.direcao) || p.direcao == null;
    }

    private static List<PropostaJogador> ordenar(List<PropostaJogador> l) {
        l.sort(Comparator.comparing((PropostaJogador p) -> !p.minhaVez()).thenComparing(p -> !p.ativa()).thenComparingInt(p -> -p.criada));
        return l;
    }

    // ================================================================== tela
    @Override
    public Node construir() {
        Jogo j = hub.jogo();
        Clube c = hub.clube();
        PainelFinancas.registrar(j);
        if (c == null) return Ui.conteudo(Ui.titulo("Transferências"), Ui.vazio("Sem clube.", null, null));
        Mundo m = j.mundo();
        List<PropostaJogador> rec = new ArrayList<>();
        for (PropostaJogador p : j.propostasRecebidas()) if (c.id.equals(p.vendedorId)) rec.add(p);
        List<PropostaJogador> env = new ArrayList<>(j.propostasEnviadas());
        long nRec = rec.stream().filter(PropostaJogador::minhaVez).count();
        long nEnv = env.stream().filter(PropostaJogador::minhaVez).count();

        TabPane abas = new TabPane();
        abas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab geral = aba("geral", "Visão geral", visaoGeral(j, m, c, rec, env));
        Tab tRec = aba("recebidas", "Propostas recebidas" + (nRec > 0 ? " (" + nRec + ")" : ""), propostas(j, m, rec, true));
        Tab tEnv = aba("feitas", "Propostas feitas" + (nEnv > 0 ? " (" + nEnv + ")" : ""), propostas(j, m, env, false));
        Tab tLista = aba("lista", "Lista de transferências", listas(j, m));
        Tab tMov = aba("movimentos", "Chegadas e saídas", movimentos(j, m, c));
        abas.getTabs().addAll(geral, tRec, tEnv, tLista, tMov);
        for (Tab t : abas.getTabs()) if (t.getId().equals(abaAntes)) abas.getSelectionModel().select(t);
        abas.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b != null) abaAntes = b.getId();
        });

        Janela jn = janela(j, c);
        Label estado = Ui.etiqueta(jn.aberta() ? "Janela aberta" : "Janela fechada", jn.aberta() ? "verde" : "vermelha");
        Label quando = Ui.rotulo(jn.texto() + "  ·  " + (jn.aberta() ? "até " + Textos.dataCurta(jn.fim()) : Textos.dataCurta(jn.inicio())), "texto-suave");
        Jogo.ResumoFinanceiro f = j.financas(c.id);
        long orcFolha = PainelFinancas.orcamentoFolha(c, j.ano());
        HBox faixa = Ui.linha(0,
                bloco("Em caixa", Textos.dinheiro(f.saldo()), f.saldo() < 0 ? "negativo" : null),
                bloco("Crédito disponível", Textos.dinheiro(Math.max(0, f.limiteEmprestimo() - f.divida())), null),
                bloco("Folha mensal", Textos.dinheiro(f.folhaMensal()) + " / " + Textos.dinheiro(orcFolha), f.folhaMensal() > orcFolha ? "negativo" : null),
                bloco("Em negociação", String.valueOf(rec.stream().filter(PropostaJogador::ativa).count() + env.stream().filter(PropostaJogador::ativa).count()), null));
        faixa.getStyleClass().add("tr-faixa");
        VBox v = Ui.conteudo(Ui.linha(12, Ui.titulo("Transferências"), estado, quando, Ui.espaco(),
                Ui.botao("Procurar jogadores", () -> hub.selecionar("mercado"), "primario")), faixa, abas);
        VBox.setVgrow(abas, Priority.ALWAYS);
        return v;
    }

    private static Tab aba(String id, String nome, Node conteudo) {
        Tab t = new Tab(nome, conteudo);
        t.setId(id);
        return t;
    }

    private static VBox bloco(String rotulo, String valor, String classe) {
        Label v = Ui.rotulo(valor, "tr-faixa-valor");
        if (classe != null) v.getStyleClass().add(classe);
        VBox b = new VBox(2, Ui.rotulo(rotulo.toUpperCase(java.util.Locale.ROOT), "tr-faixa-rotulo"), v);
        b.getStyleClass().add("tr-faixa-bloco");
        HBox.setHgrow(b, Priority.ALWAYS);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private void atualizar() {
        hub.atualizar();
    }

    // ================================================================== visão geral
    private Node visaoGeral(Jogo j, Mundo m, Clube c, List<PropostaJogador> rec, List<PropostaJogador> env) {
        // esquerda: o que espera você
        List<PropostaJogador> vez = new ArrayList<>();
        for (PropostaJogador p : rec) if (p.minhaVez()) vez.add(p);
        for (PropostaJogador p : env) if (p.minhaVez()) vez.add(p);
        ordenar(vez);
        VBox atencao = Ui.painelAcoes("Requer sua atenção", vez.isEmpty() ? null : Ui.etiqueta(String.valueOf(vez.size()), "ambar"));
        if (vez.isEmpty()) atencao.getChildren().add(Ui.rotulo("Nada pendente", "texto-suave"));
        for (PropostaJogador p : vez) atencao.getChildren().add(cartao(j, m, p));

        VBox andamento = Ui.painel("Aguardando os outros clubes");
        int n = 0;
        for (PropostaJogador p : env) {
            if (!"aguardando".equals(p.estado)) continue;
            n++;
            andamento.getChildren().add(linhaCurta(j, m, p));
        }
        if (n == 0) andamento.getChildren().add(Ui.rotulo("Nenhuma proposta aguardando resposta", "texto-suave"));
        VBox esq = new VBox(16, atencao, andamento);

        // direita: janela, orçamento, movimentações recentes
        Janela jn = janela(j, c);
        VBox janela = Ui.painelAcoes("Janela de transferências", Ui.etiqueta(jn.aberta() ? "Aberta" : "Fechada", jn.aberta() ? "verde" : "vermelha"));
        int[][] js = "europeu".equals(c.calendario) ? Balanceamento.JANELAS_TRANSFERENCIA_EUROPEU : Balanceamento.JANELAS_TRANSFERENCIA_ANO;
        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(6);
        int r = 0;
        for (int[] x : js) {
            LocalDate a = Calendario.inicio(Calendario.absoluta(j.ano(), x[0]));
            LocalDate b = Calendario.inicio(Calendario.absoluta(j.ano(), x[1])).plusDays(6);
            boolean atual = jn.aberta() && jn.semanaInicio() == x[0];
            Label nome = Ui.rotulo(x[0] <= 10 ? "Janela do início do ano" : "Janela do meio do ano", atual ? "texto-forte" : "texto-medio");
            g.addRow(r++, nome, Ui.rotulo(Textos.dataCurta(a) + " a " + Textos.dataCurta(b), "numero"));
        }
        long total = Math.max(1, ChronoUnit.DAYS.between(jn.inicio(), jn.fim()));
        double prog = jn.aberta() ? 1 - Math.max(0, jn.dias()) / (double) total : 0;
        ProgressBar pb = new ProgressBar(prog);
        pb.setMaxWidth(Double.MAX_VALUE);
        pb.getStyleClass().add("tr-barra");
        Label grande = Ui.rotulo(jn.aberta() ? Math.max(0, jn.dias()) + "" : jn.dias() + "", "tr-dias");
        Label rot = Ui.rotulo(jn.aberta() ? (jn.dias() == 1 ? "dia para fechar" : "dias para fechar") : (jn.dias() == 1 ? "dia para abrir" : "dias para abrir"), "texto-suave");
        janela.getChildren().addAll(Ui.linha(10, grande, rot), pb, g);
        if (!jn.aberta()) janela.getChildren().add(Ui.rotulo("Negócio fechado agora: o jogador chega em " + Textos.dataCurta(jn.inicio()) + ".", "texto-pequeno"));

        Jogo.ResumoFinanceiro f = j.financas(c.id);
        long orc = PainelFinancas.orcamentoFolha(c, j.ano());
        VBox orcamento = Ui.painel("Orçamento");
        orcamento.getChildren().addAll(PainelFinancas.barraUso("Folha salarial", f.folhaMensal(), orc),
                Ui.linha(24, Ui.dado("Em caixa", Textos.dinheiro(f.saldo())), Ui.dado("Crédito disponível", Textos.dinheiro(Math.max(0, f.limiteEmprestimo() - f.divida())))),
                Ui.linha(8, Ui.botao("Finanças", () -> hub.selecionar("financas"), "fantasma")));

        VBox recentes = Ui.painel("Movimentações recentes");
        List<PropostaJogador> fechadas = new ArrayList<>();
        for (PropostaJogador p : rec) if ("concluida".equals(p.estado) || "aguardando_janela".equals(p.estado)) fechadas.add(p);
        for (PropostaJogador p : env) if ("concluida".equals(p.estado) || "aguardando_janela".equals(p.estado)) fechadas.add(p);
        fechadas.sort(Comparator.comparingInt((PropostaJogador p) -> -p.criada));
        for (PropostaJogador p : fechadas.subList(0, Math.min(6, fechadas.size()))) recentes.getChildren().add(linhaCurta(j, m, p));
        if (fechadas.isEmpty()) recentes.getChildren().add(Ui.rotulo("Nenhuma movimentação", "texto-suave"));
        VBox dir = new VBox(16, janela, orcamento, recentes);
        dir.setPrefWidth(380);
        dir.setMinWidth(340);
        dir.setMaxWidth(420);

        HBox h = new HBox(16, esq, dir);
        HBox.setHgrow(esq, Priority.ALWAYS);
        h.setStyle("-fx-padding: 14 0 0 0;");
        return Ui.rolar(h);
    }

    /** Cartão de uma negociação que espera você (com os botões). */
    private Node cartao(Jogo j, Mundo m, PropostaJogador p) {
        Jogador x = m.jogador(p.jogadorId);
        Node foto = x == null ? Foto.silhueta(40, 48) : Foto.de(j.foto(x.id), x.dbId, 40, 48);
        Label nome = Ui.nomeJogador(p.jogadorId, x == null ? "?" : x.nomeExibicao(), "texto-forte");
        boolean rec = recebida(p);
        String outro = rec ? p.compradorId : p.vendedorId;
        Node clube = outro == null ? Ui.rotulo(rec ? (p.comprador == null ? "Exterior" : p.comprador) : "Sem clube", "texto-suave") : Ui.clube(m, outro, 16);
        Label dir = Ui.rotulo(rec ? "quer comprar" : "vende", "texto-pequeno");
        if (rec && "emprestimo".equals(p.tipo)) dir.setText("quer emprestado");
        if (!rec && "emprestimo".equals(p.tipo)) dir.setText("empresta");
        long val = p.valorContra != null ? p.valorContra : p.valor;
        VBox info = new VBox(3, Ui.linha(8, nome, x == null ? new Label() : Ui.pos(x.posicao)), Ui.linha(6, clube, dir));
        HBox.setHgrow(info, Priority.ALWAYS);
        VBox valor = new VBox(3, Ui.rotulo(val > 0 ? Textos.dinheiro(val) : "Sem custo", "numero"), Ui.rotulo(prazo(j, p), "texto-pequeno"));
        valor.setAlignment(Pos.CENTER_RIGHT);
        HBox topo = new HBox(12, foto, info, chip(p), valor);
        topo.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(8, topo, acoes(hub, p, this::atualizar));
        card.getStyleClass().add("tr-cartao");
        return card;
    }

    private Node linhaCurta(Jogo j, Mundo m, PropostaJogador p) {
        Jogador x = m.jogador(p.jogadorId);
        boolean rec = recebida(p);
        String outro = rec ? p.compradorId : p.vendedorId;
        Label seta = Ui.rotulo(rec ? "↗" : "↘", rec ? "tr-sai" : "tr-chega");
        Label nome = Ui.nomeJogador(p.jogadorId, x == null ? "?" : x.nomeExibicao());
        nome.setMinWidth(0);
        Node clube = outro == null ? Ui.rotulo(p.comprador == null ? "—" : p.comprador, "texto-suave") : Ui.clube(m, outro, 16, "texto-pequeno");
        VBox txt = new VBox(1, nome, clube);
        HBox.setHgrow(txt, Priority.ALWAYS);
        return Ui.linha(10, seta, txt, Ui.rotulo(p.valor > 0 ? Textos.dinheiro(p.valor) : "—", "numero"), chip(p));
    }

    // ================================================================== tabelas de propostas
    private Node propostas(Jogo j, Mundo m, List<PropostaJogador> todas, boolean recebidas) {
        VBox v = new VBox(10);
        v.setStyle("-fx-padding: 14 0 0 0;");
        String atual = recebidas ? filtroRecebidas : filtroFeitas;
        List<String> ops = List.of("andamento", "encerradas", "todas");
        HBox seg = Ui.segmentos(ops, o -> switch (o) {
            case "andamento" -> "Em andamento";
            case "encerradas" -> "Encerradas";
            default -> "Todas";
        }, atual, o -> {
            if (recebidas) filtroRecebidas = o;
            else filtroFeitas = o;
            abaAntes = recebidas ? "recebidas" : "feitas";
            hub.atualizar();
        });
        Predicate<PropostaJogador> f = switch (atual) {
            case "andamento" -> PropostaJogador::ativa;
            case "encerradas" -> p -> !p.ativa();
            default -> p -> true;
        };
        List<PropostaJogador> ps = new ArrayList<>();
        for (PropostaJogador p : todas) if (f.test(p)) ps.add(p);
        v.getChildren().add(Ui.linha(10, seg, Ui.espaco(), Ui.rotulo(ps.size() + (ps.size() == 1 ? " proposta" : " propostas"), "texto-suave")));
        if (ps.isEmpty()) {
            v.getChildren().add(Ui.painel(null, recebidas ? Ui.vazio("Nenhuma proposta", "Lista de transferências", () -> {
                abaAntes = "lista";
                hub.atualizar();
            }) : Ui.vazio("Nenhuma proposta", "Procurar jogadores", () -> hub.selecionar("mercado"))));
            return v;
        }
        TableView<PropostaJogador> tv = Ui.tabela();
        Ui.colNo(tv, "Jogador", 200, p -> {
            Jogador x = m.jogador(p.jogadorId);
            Label n = Ui.nomeJogador(p.jogadorId, x == null ? "?" : x.nomeExibicao());
            return x == null ? n : Ui.linha(8, Ui.pos(x.posicao), n);
        });
        if (recebidas) Ui.colNo(tv, "Interessado", 190, p -> p.compradorId == null ? Ui.rotulo(p.comprador == null ? "Exterior" : p.comprador, "texto-medio") : Ui.clube(m, p.compradorId, 18));
        else Ui.colNo(tv, "Clube", 190, p -> p.vendedorId == null ? Ui.rotulo("Sem clube", "texto-suave") : Ui.clube(m, p.vendedorId, 18));
        Ui.col(tv, "Tipo", 90, p -> "emprestimo".equals(p.tipo) ? "Empréstimo" : "Compra");
        Ui.colDinheiro(tv, "Oferta", 105, p -> p.valor);
        Ui.col(tv, recebidas ? "Sua contraproposta" : "Pedem", 120, p -> p.valorContra != null ? Textos.dinheiro(p.valorContra)
                : p.salarioContra != null ? Textos.dinheiro(p.salarioContra) + "/mês" : "");
        Ui.colNo(tv, "Situação", 190, PainelTransferencias::chip);
        Ui.col(tv, "Prazo", 80, p -> prazo(j, p));
        Ui.col(tv, "Data", 76, p -> Textos.dataCurta(criada(p)));
        Ui.colNo(tv, "", 320, p -> acoes(hub, p, this::atualizar));
        tv.getItems().setAll(ordenar(ps));
        VBox t = Ui.painelTabela(null, null, tv);
        VBox.setVgrow(t, Priority.ALWAYS);
        v.getChildren().add(t);
        return v;
    }

    // ================================================================== ações (também usadas na caixa de entrada)
    /** Botões da negociação conforme o estado e a direção. {@code depois} redesenha a tela. */
    static Node acoes(Hub hub, PropostaJogador p, Runnable depois) {
        Jogo j = hub.jogo();
        Mundo m = j.mundo();
        String e = p.estado == null ? "" : p.estado;
        if (recebida(p)) {
            return switch (e) {
                case "pendente", "recebida", "contraproposta" -> Ui.linha(6,
                        Ui.botao("Aceitar", () -> {
                            j.responderProposta(p.id, true);
                            // requisito: uma confirmação só. "Aceitar" já fecha a venda
                            if ("aguardando_confirmacao".equals(p.estado)) {
                                fecharVenda(hub, j, p, depois);
                                return;
                            }
                            if ("pendente".equals(e)) Ui.informar("Venda", "Negócio fechado.");
                            depois.run();
                        }, "primario"),
                        Ui.botao("Contrapropor", () -> contrapropor(j, p, p.valor, depois)),
                        Ui.botao("Recusar", () -> {
                            j.responderProposta(p.id, false);
                            depois.run();
                        }));
                case "aguardando_confirmacao" -> Ui.linha(6, Ui.botao("Confirmar venda", () -> fecharVenda(hub, j, p, depois), "primario"),
                        Ui.botao("Cancelar", () -> cancelar(j, p, depois)));
                default -> new HBox();
            };
        }
        return switch (e) {
            case "aguardando" -> Ui.linha(6, Ui.botao("Cancelar", () -> cancelar(j, p, depois), "fantasma"));
            case "contraproposta", "contraproposta_jogador" -> Ui.linha(6,
                    Ui.botao("Aceitar", () -> {
                        j.responderProposta(p.id, true);
                        depois.run();
                    }, "primario"),
                    "contraproposta".equals(e) ? Ui.botao("Contrapropor", () -> contrapropor(j, p, p.valorContra == null ? p.valor : p.valorContra, depois))
                            : Ui.botao("Nova oferta", () -> propor(hub, j, m, p, depois)),
                    Ui.botao("Desistir", () -> cancelar(j, p, depois)));
            case "aceita" -> Ui.linha(6, Ui.botao("Acertar contrato", () -> propor(hub, j, m, p, depois), "primario"), Ui.botao("Desistir", () -> cancelar(j, p, depois)));
            case "aguardando_confirmacao" -> Ui.linha(6, Ui.botao("Confirmar", () -> fecharCompra(hub, j, p, depois), "primario"),
                    Ui.botao("Desistir", () -> cancelar(j, p, depois)));
            default -> new HBox();
        };
    }

    /**
     * Fecha a venda/empréstimo de um jogador seu (requisito: "ainda tem que confirmar 3 vezes
     * para vender e comprar"). O clique em Aceitar/Confirmar já é a confirmação: sem diálogo extra.
     */
    private static void fecharVenda(Hub hub, Jogo j, PropostaJogador p, Runnable depois) {
        String r = j.confirmarNegocio(p.id);
        if (!"concluida".equals(p.estado) && !"aguardando_janela".equals(p.estado)) Ui.informar("Venda", r);
        depois.run();
        hub.atualizar(); // caixa no topo atualizado na hora
    }

    /** Fecha a contratação já acertada (clube e jogador aceitaram): um clique, sem diálogo extra. */
    private static void fecharCompra(Hub hub, Jogo j, PropostaJogador p, Runnable depois) {
        String r = j.confirmarNegocio(p.id);
        if (!"concluida".equals(p.estado) && !"aguardando_janela".equals(p.estado)) Ui.informar("Contratação", r);
        depois.run();
        hub.atualizar();
    }

    private static void cancelar(Jogo j, PropostaJogador p, Runnable depois) {
        if (!Ui.confirmar("Cancelar negociação", "Encerrar esta negociação?")) return;
        j.cancelarProposta(p.id);
        depois.run();
    }

    private static void contrapropor(Jogo j, PropostaJogador p, long base, Runnable depois) {
        CampoDinheiro cd = new CampoDinheiro(base, base, 0);
        int r = Ui.modal("Contraproposta", Ui.dado("Valor", cd), "Enviar", "Cancelar");
        if (r != 0) return;
        long v = cd.valor();
        if (v <= 0) return;
        if (!j.contraproposta(p.id, v)) Ui.informar("Contraproposta", "Não foi possível enviar.");
        depois.run();
    }

    /** Termos com o jogador (salário e duração) depois da taxa aceita. */
    private static void propor(Hub hub, Jogo j, Mundo m, PropostaJogador p, Runnable depois) {
        long pede = p.salarioContra != null ? p.salarioContra : j.salarioPedido(p.jogadorId);
        CampoDinheiro cd = new CampoDinheiro(pede, pede, 0);
        ComboBox<String> meses = new ComboBox<>();
        meses.getItems().setAll("6 meses", "1 ano", "2 anos", "3 anos", "4 anos", "5 anos");
        meses.getSelectionModel().select(2);
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.addRow(0, Ui.rotulo("Salário (mês)", "texto-medio"), cd);
        g.addRow(1, Ui.rotulo("Duração", "texto-medio"), meses);
        g.addRow(2, Ui.rotulo("Pedido", "texto-medio"), Ui.rotulo(Textos.dinheiro(pede), "texto-forte"));
        if (p.valor > 0) g.addRow(3, Ui.rotulo("Taxa", "texto-medio"), Ui.rotulo(Textos.dinheiro(p.valor) + "  ·  caixa depois: "
                + Textos.dinheiro(hub.clube().dinheiro - p.valor)));
        int r = Ui.modal("Contrato", g, "Propor e fechar", "Cancelar");
        if (r != 0) return;
        int ms = switch (meses.getSelectionModel().getSelectedIndex()) {
            case 0 -> 6;
            case 1 -> 12;
            case 3 -> 36;
            case 4 -> 48;
            case 5 -> 60;
            default -> 24;
        };
        String resp = j.proporContrato(p.id, cd.valor(), ms);
        // requisito (uma confirmação só): se o jogador aceitou, o "Propor e fechar" já fecha o negócio
        if ("aguardando_confirmacao".equals(p.estado)) {
            String fim = j.confirmarNegocio(p.id);
            resp = "concluida".equals(p.estado) || "aguardando_janela".equals(p.estado) ? p.mensagem : fim;
            hub.atualizar();
        }
        if (resp != null && !resp.isBlank()) Ui.informar("Contrato", resp);
        depois.run();
    }

    // ================================================================== listas
    private Node listas(Jogo j, Mundo m) {
        VBox v = new VBox(16);
        v.setStyle("-fx-padding: 14 0 0 0;");
        v.getChildren().add(lista(j, m, false));
        v.getChildren().add(lista(j, m, true));
        return Ui.rolar(v);
    }

    private Node lista(Jogo j, Mundo m, boolean emprestimo) {
        List<Jogador> l = emprestimo ? j.listaEmprestimos() : j.listaTransferencias();
        String titulo = emprestimo ? "Disponíveis para empréstimo" : "À venda";
        Node conta = Ui.etiqueta(String.valueOf(l.size()), l.isEmpty() ? "escura" : "azul");
        if (l.isEmpty()) return Ui.painelAcoes(titulo, conta, Ui.linha(10, Ui.rotulo("Ninguém na lista", "texto-suave"), Ui.espaco(),
                Ui.botao("Ir ao elenco", () -> hub.selecionar("elenco"), "fantasma")));
        TableView<Jogador> tv = Ui.tabela();
        Ui.colPos(tv, x -> x.posicao);
        Ui.colNo(tv, "Jogador", 220, x -> Ui.nomeJogador(x.id, x.nomeExibicao()));
        Ui.colForca(tv, "Força", Jogador::forcaExibida);
        Ui.colNum(tv, "Idade", 54, x -> x.idade(j.ano()));
        if (!emprestimo) Ui.colDinheiro(tv, "Preço pedido", 120, x -> x.precoPedido);
        Ui.colDinheiro(tv, "Valor", 110, x -> j.valorDeMercado(x.id));
        Ui.colNum(tv, "Interessados", 96, x -> {
            int n = 0;
            for (PropostaJogador p : j.propostasRecebidas()) if (p.jogadorId == x.id && p.ativa()) n++;
            return n;
        });
        Ui.colNo(tv, "Negociação", 200, x -> {
            PropostaJogador p = j.negociacaoPor(x.id);
            return p == null ? new Label() : chip(p);
        });
        Ui.colNo(tv, "", 130, x -> Ui.botao("Tirar da lista", () -> {
            j.tirarDasListas(x.id);
            abaAntes = "lista";
            hub.atualizar();
        }, "fantasma"));
        tv.getItems().setAll(l);
        Ui.alturaPorLinhas(tv, Math.max(2, l.size()));
        return Ui.painelTabela(titulo, conta, tv);
    }

    // ================================================================== chegadas e saídas
    private Node movimentos(Jogo j, Mundo m, Clube c) {
        List<PropostaJogador> chegam = new ArrayList<>(), saem = new ArrayList<>(), chegaram = new ArrayList<>(), sairam = new ArrayList<>();
        for (PropostaJogador p : j.transferenciasPendentes()) (c.id.equals(p.compradorId) ? chegam : saem).add(p);
        for (PropostaJogador p : m.propostas) {
            if (!"concluida".equals(p.estado)) continue;
            if ("enviada".equals(p.direcao) || c.id.equals(p.compradorId)) chegaram.add(p);
            else if (c.id.equals(p.vendedorId)) sairam.add(p);
        }
        Comparator<PropostaJogador> recente = Comparator.comparingInt(p -> -p.criada);
        for (List<PropostaJogador> l : List.of(chegam, saem, chegaram, sairam)) l.sort(recente);
        VBox v = new VBox(16);
        v.setStyle("-fx-padding: 14 0 0 0;");
        HBox pend = new HBox(16, tabelaMov(j, m, "Chegam na próxima janela", chegam, true, true),
                tabelaMov(j, m, "Saem na próxima janela", saem, false, true));
        HBox conf = new HBox(16, tabelaMov(j, m, "Chegadas confirmadas", chegaram, true, false),
                tabelaMov(j, m, "Saídas confirmadas", sairam, false, false));
        for (HBox h : List.of(pend, conf)) for (Node n : h.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
            ((VBox) n).setPrefWidth(500);
        }
        if (!chegam.isEmpty() || !saem.isEmpty()) v.getChildren().add(pend);
        v.getChildren().add(conf);
        long gasto = 0, recebido = 0;
        for (PropostaJogador p : chegaram) gasto += p.valor;
        for (PropostaJogador p : chegam) gasto += p.valor;
        for (PropostaJogador p : sairam) recebido += p.valor;
        for (PropostaJogador p : saem) recebido += p.valor;
        long saldo = recebido - gasto;
        Label s = Ui.rotulo((saldo >= 0 ? "+" : "") + Textos.dinheiro(saldo), "numero", saldo >= 0 ? "positivo" : "negativo");
        v.getChildren().add(0, Ui.linha(28, Ui.dado("Gasto em contratações", Textos.dinheiro(gasto)), Ui.dado("Recebido em vendas", Textos.dinheiro(recebido)),
                Ui.dado("Saldo", s)));
        return Ui.rolar(v);
    }

    private VBox tabelaMov(Jogo j, Mundo m, String titulo, List<PropostaJogador> ps, boolean chegada, boolean pendente) {
        Node conta = Ui.etiqueta(String.valueOf(ps.size()), ps.isEmpty() ? "escura" : pendente ? "azul" : chegada ? "verde" : "vermelha");
        if (ps.isEmpty()) return Ui.painelAcoes(titulo, conta, Ui.rotulo("Nenhuma", "texto-suave"));
        TableView<PropostaJogador> tv = Ui.tabela();
        Ui.colNo(tv, "Jogador", 190, p -> {
            Jogador x = m.jogador(p.jogadorId);
            Label n = Ui.nomeJogador(p.jogadorId, x == null ? "?" : x.nomeExibicao());
            return x == null ? n : Ui.linha(8, Ui.pos(x.posicao), n);
        });
        Ui.colNo(tv, chegada ? "De" : "Para", 170, p -> {
            String id = chegada ? p.vendedorId : p.compradorId;
            if (id == null) return Ui.rotulo(chegada ? "Sem clube" : p.comprador == null ? "Exterior" : p.comprador, "texto-suave");
            return Ui.clube(m, id, 18);
        });
        Ui.col(tv, "Tipo", 86, p -> "emprestimo".equals(p.tipo) ? "Empréstimo" : p.valor == 0 ? "Livre" : "Compra");
        Ui.colDinheiro(tv, "Valor", 100, p -> p.valor);
        if (pendente) Ui.col(tv, chegada ? "Chega em" : "Sai em", 86, p -> p.chegada() == null ? "" : Textos.dataCurta(p.chegada()));
        else Ui.col(tv, "Data", 76, p -> Textos.dataCurta(criada(p)));
        tv.getItems().setAll(ps);
        Ui.alturaPorLinhas(tv, Math.max(2, ps.size()));
        return Ui.painelTabela(titulo, conta, tv);
    }
}
