package br.futebas.ui.comp;

import br.futebas.ui.modelo.Textos;
import javafx.geometry.VPos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.HashMap;
import java.util.Map;

/**
 * Reserva para quando não há imagem real: um brasão simples com as cores do clube (cor1/cor2),
 * um padrão escolhido pelo id e as iniciais; e uma camisa lisa com as cores do clube.
 */
public final class Escudo {
    private Escudo() {}

    private static final Map<String, Image> CACHE = new HashMap<>();
    private static final String[] PALETA = {"#1d5a43", "#8a2b22", "#22302a", "#2e6672", "#a8781d", "#5a2d5e", "#3c3c3c", "#c0392b"};

    /** Escudo como imagem (com cache), na altura pedida. */
    public static ImageView gerado(String id, String nome, String cor1, String cor2, double altura) {
        String chave = id + "|" + Math.round(altura);
        Image img = CACHE.get(chave);
        if (img == null) {
            Canvas c = desenhar(id, nome, cor1, cor2, altura);
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            img = c.snapshot(sp, new WritableImage((int) Math.ceil(c.getWidth()), (int) Math.ceil(c.getHeight())));
            if (CACHE.size() > 3000) CACHE.clear();
            CACHE.put(chave, img);
        }
        ImageView v = new ImageView(img);
        v.setSmooth(true);
        return v;
    }

    /** Camisa lisa (gola em V, mangas na segunda cor), com cache. */
    public static ImageView camisaGerada(String cor1, String cor2, double altura) {
        String chave = "camisa|" + cor1 + "|" + cor2 + "|" + Math.round(altura);
        Image img = CACHE.get(chave);
        if (img == null) {
            double h = altura, w = altura;
            Canvas cv = new Canvas(w, h);
            GraphicsContext g = cv.getGraphicsContext2D();
            Color c1 = cor(cor1, Color.web("#3c4a57"));
            Color c2 = cor(cor2, luminancia(c1) > 0.6 ? Color.web("#16201c") : Color.WHITE);
            if (c1.equals(c2)) c2 = luminancia(c1) > 0.6 ? Color.web("#16201c") : Color.WHITE;
            double[] xs = {w * 0.30, w * 0.40, w * 0.5, w * 0.60, w * 0.70, w * 0.97, w * 0.86, w * 0.78, w * 0.78, w * 0.22, w * 0.22, w * 0.14, w * 0.03};
            double[] ys = {h * 0.06, h * 0.06, h * 0.16, h * 0.06, h * 0.06, h * 0.26, h * 0.42, h * 0.36, h * 0.96, h * 0.96, h * 0.36, h * 0.42, h * 0.26};
            g.setFill(c1);
            g.fillPolygon(xs, ys, xs.length);
            g.setFill(c2);
            g.fillPolygon(new double[]{w * 0.70, w * 0.97, w * 0.86, w * 0.78}, new double[]{h * 0.06, h * 0.26, h * 0.42, h * 0.36}, 4);
            g.fillPolygon(new double[]{w * 0.30, w * 0.03, w * 0.14, w * 0.22}, new double[]{h * 0.06, h * 0.26, h * 0.42, h * 0.36}, 4);
            g.setStroke(Color.rgb(0, 0, 0, 0.35));
            g.setLineWidth(Math.max(1, h * 0.025));
            g.strokePolygon(xs, ys, xs.length);
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            img = cv.snapshot(sp, null);
            CACHE.put(chave, img);
        }
        return new ImageView(img);
    }

    static Color cor(String hex, Color padrao) {
        if (hex == null || hex.isBlank()) return padrao;
        try {
            return Color.web(hex.trim());
        } catch (IllegalArgumentException e) {
            return padrao;
        }
    }

    static double luminancia(Color c) {
        return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
    }

    public static Canvas desenhar(String id, String nome, String cor1, String cor2, double altura) {
        double h = altura, w = Math.round(altura * 0.86);
        Canvas cv = new Canvas(w, h);
        GraphicsContext g = cv.getGraphicsContext2D();
        int hash = Math.abs((id == null ? "x" : id).hashCode());
        Color c1 = cor(cor1, Color.web(PALETA[hash % PALETA.length]));
        Color c2 = cor(cor2, luminancia(c1) > 0.6 ? Color.web("#16201c") : Color.WHITE);
        if (c1.equals(c2)) c2 = luminancia(c1) > 0.6 ? Color.web("#16201c") : Color.WHITE;
        // forma
        double x0 = w * 0.06, x1 = w * 0.94, y0 = h * 0.05;
        Runnable forma = () -> {
            g.beginPath();
            g.moveTo(x0, y0);
            g.lineTo(w * 0.30, y0);
            g.lineTo(w * 0.50, y0 + h * 0.05);
            g.lineTo(w * 0.70, y0);
            g.lineTo(x1, y0);
            g.lineTo(x1, h * 0.46);
            g.quadraticCurveTo(x1, h * 0.80, w * 0.5, h * 0.97);
            g.quadraticCurveTo(x0, h * 0.80, x0, h * 0.46);
            g.closePath();
        };
        g.save();
        forma.run();
        g.setFill(c1);
        g.fill();
        g.clip();
        g.setFill(c2);
        int padrao = (hash / 7) % 6;
        Color fundoTexto = c1;
        switch (padrao) {
            case 0 -> { // faixa vertical central
                g.fillRect(w * 0.38, 0, w * 0.24, h);
                fundoTexto = c2;
            }
            case 1 -> // faixa horizontal no alto
                    g.fillRect(0, h * 0.05, w, h * 0.24);
            case 2 -> { // banda diagonal
                g.beginPath();
                g.moveTo(0, h * 0.18);
                g.lineTo(w * 0.2, 0);
                g.lineTo(w, h * 0.75);
                g.lineTo(w, h * 0.98);
                g.closePath();
                g.fill();
                fundoTexto = c2;
            }
            case 3 -> { // listras verticais
                for (int i = 0; i < 5; i++) if (i % 2 == 1) g.fillRect(w * i / 5.0, 0, w / 5.0, h);
            }
            case 4 -> // metade
                    g.fillRect(w * 0.5, 0, w * 0.5, h);
            default -> { // divisa (chevron)
                g.beginPath();
                g.moveTo(0, h * 0.55);
                g.lineTo(w * 0.5, h * 0.30);
                g.lineTo(w, h * 0.55);
                g.lineTo(w, h * 0.72);
                g.lineTo(w * 0.5, h * 0.47);
                g.lineTo(0, h * 0.72);
                g.closePath();
                g.fill();
            }
        }
        g.restore();
        // contorno
        forma.run();
        g.setStroke(Color.web("#16201c"));
        g.setLineWidth(Math.max(1.2, h * 0.045));
        g.setLineJoin(StrokeLineJoin.ROUND);
        g.stroke();
        // iniciais
        String ini = Textos.iniciais(nome);
        double tam = h * (ini.length() >= 3 ? 0.27 : 0.33);
        g.setFont(Font.font("Bahnschrift", FontWeight.BOLD, tam));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        Color txt = luminancia(fundoTexto) > 0.55 ? Color.web("#16201c") : Color.WHITE;
        Color contorno = luminancia(txt) > 0.5 ? Color.web("#16201c") : Color.WHITE;
        double ty = h * 0.50;
        if (h >= 28) {
            g.setStroke(contorno.deriveColor(0, 1, 1, 0.75));
            g.setLineWidth(Math.max(1, h * 0.03));
            g.strokeText(ini, w * 0.5, ty);
        }
        g.setFill(txt);
        g.fillText(ini, w * 0.5, ty);
        return cv;
    }
}
