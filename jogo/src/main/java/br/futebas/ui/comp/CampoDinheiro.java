package br.futebas.ui.comp;

import br.futebas.ui.modelo.Textos;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Campo de dinheiro: mostra "R$ 10.035.843" enquanto se digita, seleciona tudo ao receber o foco
 * (digitar substitui o valor), aceita "10 mi", "850 mil", "1,5 bi", tem atalhos (−20%, −10%,
 * preço pedido, +10%) e escreve o valor por extenso embaixo, com avisos de limite.
 * No modo {@code emLinha} (diálogos de negociação) os atalhos ficam como segmentos na mesma linha do
 * campo, sem a linha repetida embaixo; o aviso de limite vira a borda vermelha do campo.
 */
public final class CampoDinheiro extends VBox {
    private static final NumberFormat FMT = NumberFormat.getIntegerInstance(new Locale("pt", "BR"));

    private final TextField campo = new TextField();
    private final Label extenso = Ui.rotulo("", "texto-pequeno");
    private final long referencia, maximo;
    private final boolean emLinha;
    private final java.util.List<Button> atalhos = new java.util.ArrayList<>();
    private final java.util.List<Long> valoresAtalhos = new java.util.ArrayList<>();
    private final java.util.List<Runnable> aoMudar = new java.util.ArrayList<>();
    private boolean formatando;

    /**
     * @param inicial    valor inicial
     * @param referencia preço pedido (0 = sem atalhos de porcentagem)
     * @param maximo     teto (0 = sem teto), por exemplo caixa + limite do banco
     */
    public CampoDinheiro(long inicial, long referencia, long maximo) {
        this(inicial, referencia, maximo, false);
    }

    /** @param emLinha atalhos como segmentos ao lado do campo e sem a linha por extenso */
    public CampoDinheiro(long inicial, long referencia, long maximo, boolean emLinha) {
        super(4);
        this.referencia = referencia;
        this.maximo = maximo;
        this.emLinha = emLinha;
        campo.setPrefWidth(emLinha ? 190 : 170);
        definir(inicial);
        campo.focusedProperty().addListener((o, a, b) -> {
            if (b) Platform.runLater(campo::selectAll);
            else definir(valor());
        });
        campo.textProperty().addListener((o, a, t) -> {
            if (formatando) return;
            if (t.matches("[R$\\s.0-9]*")) {
                long v = digitos(t);
                String f = formatar(v);
                if (!f.equals(t)) {
                    formatando = true;
                    campo.setText(t.replaceAll("[^0-9]", "").isEmpty() ? "" : f);
                    campo.positionCaret(campo.getText().length());
                    formatando = false;
                }
            }
            atualizarExtenso();
        });
        if (emLinha) {
            campo.getStyleClass().add("campo-dinheiro");
            HBox linha = Ui.linha(10, campo);
            if (referencia > 0) {
                HBox seg = new HBox(atalho("−20%", Math.round(referencia * 0.8)), atalho("−10%", Math.round(referencia * 0.9)),
                        atalho("Pedido", referencia), atalho("+10%", Math.round(referencia * 1.1)));
                seg.getStyleClass().add("atalhos-dinheiro");
                seg.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                atalhos.get(0).getStyleClass().add("primeiro");
                atalhos.get(atalhos.size() - 1).getStyleClass().add("ultimo");
                linha.getChildren().add(seg);
            }
            getChildren().add(linha);
        } else {
            getChildren().add(campo);
            if (referencia > 0) {
                HBox linha = Ui.linha(4,
                        atalho("−20%", Math.round(referencia * 0.8)), atalho("−10%", Math.round(referencia * 0.9)),
                        atalho("Pedido", referencia), atalho("+10%", Math.round(referencia * 1.1)));
                getChildren().add(linha);
            }
            getChildren().add(extenso);
        }
        atualizarExtenso();
    }

    private Button atalho(String txt, long v) {
        Button b = Ui.botao(txt, () -> definir(v), emLinha ? "atalho-dinheiro" : "chip");
        b.setFocusTraversable(false);
        atalhos.add(b);
        valoresAtalhos.add(v);
        return b;
    }

    /** Avisa a cada mudança do valor (resumo do diálogo, alertas de caixa). */
    public void aoMudar(Runnable r) {
        aoMudar.add(r);
    }

    public TextField campo() { return campo; }

    public void definir(long v) {
        formatando = true;
        campo.setText(formatar(Math.max(0, v)));
        formatando = false;
        atualizarExtenso();
    }

    /** Valor digitado (entende "R$ 1.234", "10 mi", "850 mil", "1,5 bi"). */
    public long valor() {
        return interpretar(campo.getText());
    }

    public static long interpretar(String s) {
        if (s == null) return 0;
        String t = s.toLowerCase(Locale.ROOT).replace("r$", "").trim();
        double mult = 1;
        if (t.matches(".*\\bbi(lh(ão|ao|ões|oes))?\\b.*") || t.endsWith("bi")) mult = 1e9;
        else if (t.matches(".*\\bmi(lh(ão|ao|ões|oes))?\\b.*") || t.endsWith("mi")) mult = 1e6;
        else if (t.matches(".*\\bmil\\b.*") || t.endsWith("mil")) mult = 1e3;
        if (mult > 1) {
            String n = t.replaceAll("[^0-9,.]", "").replace(".", "").replace(',', '.');
            try {
                return Math.round(Double.parseDouble(n) * mult);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return digitos(t);
    }

    private static long digitos(String t) {
        String d = t.replaceAll("[^0-9]", "");
        if (d.isEmpty()) return 0;
        try {
            return Long.parseLong(d.length() > 15 ? d.substring(0, 15) : d);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String formatar(long v) {
        return "R$ " + FMT.format(v);
    }

    private void atualizarExtenso() {
        long v = valor();
        for (int i = 0; i < atalhos.size(); i++) {
            Button b = atalhos.get(i);
            b.getStyleClass().remove("ativo");
            if (valoresAtalhos.get(i) == v) b.getStyleClass().add("ativo");
        }
        if (emLinha) {
            campo.getStyleClass().remove("acima");
            if (maximo > 0 && v > maximo) campo.getStyleClass().add("acima");
        }
        for (Runnable r : aoMudar) r.run();
        if (emLinha) return;
        String txt = Textos.dinheiro(v);
        extenso.getStyleClass().removeAll("negativo", "kings-aviso");
        if (maximo > 0 && v > maximo) {
            txt += "  ·  acima do que o clube pode pagar (" + Textos.dinheiro(maximo) + ")";
            extenso.getStyleClass().add("negativo");
        } else if (referencia > 0 && v > referencia * 3) {
            txt += "  ·  mais de 3× o pedido";
            extenso.getStyleClass().add("kings-aviso");
        }
        extenso.setText(txt);
    }
}
