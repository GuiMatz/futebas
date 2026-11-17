package br.futebas.ui.comp;

import br.futebas.mundo.Posicao;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Aquecimento da interface enquanto o jogador espera (menu inicial e tela de carregamento). A 1ª
 * central custava ~0,7 s a mais que as seguintes: a JVM carregando e interpretando as classes da
 * interface, o JavaFX lendo as fontes, criando as peles dos controles e casando os seletores do CSS
 * pela 1ª vez. Aqui isso acontece antes, numa cena fora da janela, enquanto o banco carrega.
 */
public final class Aquecimento {
    private Aquecimento() {}

    private static boolean telaFeita;
    private static volatile boolean classesFeitas;

    /**
     * Fora da thread da tela: carrega (sem inicializar) as classes da interface do jogo. Seguro em
     * qualquer thread; erros são ignorados (é só aquecimento).
     */
    public static void classes() {
        if (classesFeitas) return;
        classesFeitas = true;
        ClassLoader cl = Aquecimento.class.getClassLoader();
        for (String n : nomesDasClasses("br/futebas/ui/")) {
            try {
                Class.forName(n, false, cl);
            } catch (Throwable e) {
                // classe que não carrega sozinha: fica para quando for usada
            }
        }
    }

    /** Classes do jogo sob um pacote, lidas da pasta de classes ou do .jar. */
    static List<String> nomesDasClasses(String pacote) {
        List<String> l = new ArrayList<>();
        try {
            URL u = Aquecimento.class.getProtectionDomain().getCodeSource().getLocation();
            Path p = Path.of(u.toURI());
            if (Files.isDirectory(p)) {
                Path base = p.resolve(pacote);
                if (!Files.isDirectory(base)) return l;
                try (Stream<Path> s = Files.walk(base)) {
                    s.filter(f -> f.toString().endsWith(".class")).forEach(f -> {
                        String r = p.relativize(f).toString().replace('\\', '/');
                        l.add(r.substring(0, r.length() - 6).replace('/', '.'));
                    });
                }
            } else {
                try (java.util.jar.JarFile jf = new java.util.jar.JarFile(p.toFile())) {
                    jf.stream().map(java.util.jar.JarEntry::getName)
                            .filter(n -> n.startsWith(pacote) && n.endsWith(".class"))
                            .forEach(n -> l.add(n.substring(0, n.length() - 6).replace('/', '.')));
                }
            }
        } catch (Exception | Error e) {
            // sem lista: sem aquecimento de classes
        }
        return l;
    }

    /**
     * Na thread da tela (uma vez por execução): monta fora da janela uma cena com os controles da
     * central — navegação, cabeçalho, tabela de classificação com linhas, abas, lista, rolagem,
     * combo, campo de texto, escudos e camisas gerados — e aplica o CSS e o layout. Leva ~0,2–0,4 s
     * na 1ª vez; chame com a tela de carregamento à vista.
     */
    public static void tela(String css) {
        if (telaFeita || css == null) return;
        telaFeita = true;
        try {
            BorderPane raiz = new BorderPane();
            raiz.getStyleClass().add("raiz");
            Scene cena = new Scene(raiz, 1366, 768);
            cena.getStylesheets().add(css);

            VBox nav = new VBox();
            nav.getStyleClass().add("nav");
            nav.getChildren().add(Ui.rotulo("Hoje", "nav-grupo"));
            for (String n : new String[] {"Próximo jogo", "Elenco", "Campeonatos"}) {
                Button b = new Button(n);
                b.getStyleClass().setAll("nav-item");
                nav.getChildren().add(b);
            }
            Button jogar = new Button("JOGAR");
            jogar.getStyleClass().setAll("jogar");
            nav.getChildren().addAll(Ui.espaco(), jogar);
            raiz.setLeft(nav);

            HBox topo = new HBox(Ui.rotulo("Clube", "cab-nome"), Ui.rotulo("Técnico · Série A", "cab-meta"), new TextField());
            topo.getStyleClass().add("cabecalho");
            topo.setStyle(Tema.estiloCabecalho("#c8102e", "#000000"));
            raiz.setTop(new VBox(topo));

            TableView<Integer> tv = Ui.tabela();
            Ui.colNo(tv, "", 40, i -> Ui.rotulo(String.valueOf(i + 1), "zona", "zona-classifica"));
            Ui.col(tv, "Clube", 240, i -> "Clube " + i).setCellFactory(x -> new javafx.scene.control.TableCell<>() {
                @Override
                protected void updateItem(String s, boolean vazio) {
                    super.updateItem(s, vazio);
                    setText(null);
                    setGraphic(vazio || s == null ? null : Ui.linha(8, Escudo.gerado(s, s, "#c8102e", "#ffffff", 20), Ui.rotulo(s)));
                }
            });
            Ui.colNum(tv, "P", 52, i -> 40 - i);
            Ui.colNum(tv, "J", 36, i -> 20);
            Ui.colNo(tv, "Últimos", 120, i -> Ui.linha(3, Ui.forma("V"), Ui.forma("E"), Ui.forma("D")));
            Ui.colForca(tv, "F", i -> 60 + i);
            Ui.colPos(tv, i -> Posicao.values()[i % Posicao.values().length]);
            Ui.colDinheiro(tv, "Valor", 90, i -> 1_000_000L * i);
            List<Integer> linhas = new ArrayList<>();
            for (int i = 0; i < 20; i++) linhas.add(i);
            tv.getItems().setAll(linhas);
            Ui.estiloLinhas(tv, (i, r) -> {
                if (i == 3) r.getStyleClass().add("meu-clube");
            });
            Ui.alturaPorLinhas(tv, 20);

            TabPane abas = new TabPane(new Tab("Tabela", Ui.painelTabela("Classificação", null, tv)), new Tab("Jogos"), new Tab("Artilharia"));
            ListView<String> lista = new ListView<>();
            for (int i = 0; i < 20; i++) lista.getItems().add("Competição " + i);
            ComboBox<String> combo = new ComboBox<>();
            combo.getItems().addAll("Todas", "Brasil");
            combo.setValue("Todas");
            VBox textos = new VBox(Ui.titulo("Título"), Ui.tituloPainel("Painel"), Ui.rotulo("suave", "texto-suave"),
                    Ui.rotulo("forte", "texto-forte"), Ui.etiqueta("3", "verde"), Ui.info("informação"), Ui.forca(77),
                    Ui.pos(Posicao.values()[Posicao.values().length - 1]), Ui.botao("Primário", null, "primario"), Ui.botao("Fantasma", null, "fantasma"),
                    new ProgressBar(0.5), Escudo.camisaGerada("#c8102e", "#000000", 40), Foto.silhueta(50, 50), combo);
            HBox corpo = new HBox(12, lista, Ui.rolar(textos), Ui.painel("Cartão", Ui.vazio("Nada aqui.", null, null)));
            VBox centro = new VBox(12, abas, corpo);
            centro.setAlignment(Pos.TOP_LEFT);
            raiz.setCenter(centro);
            raiz.applyCss();
            raiz.layout();
            // o "ao passar o mouse" e a seleção também têm seletores próprios
            abas.getSelectionModel().select(1);
            tv.getSelectionModel().select(2);
            lista.getSelectionModel().select(2);
            raiz.applyCss();
            raiz.layout();
            for (Node n : List.<Node>of(tv, lista)) n.setVisible(false);
        } catch (RuntimeException e) {
            // aquecimento é opcional
        }
    }
}
