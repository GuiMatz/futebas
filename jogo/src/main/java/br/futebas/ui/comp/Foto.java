package br.futebas.ui.comp;

import br.futebas.mundo.PerfilFoto;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Foto do jogador num quadro escuro: a foto real (assets/fotos, fotos da Kings) quando existe; senão o
 * avatar gerado ({@link Avatar}) a partir do perfil que vem no caminho ({@link PerfilFoto#chave()}, é o que
 * {@code Jogo.foto} devolve). A silhueta fica só para vaga vazia (sem jogador) e para a opção "fotos dos
 * jogadores" desligada.
 */
public final class Foto {
    private Foto() {}

    /**
     * {@code caminho}: chave de perfil ({@code Jogo.foto}), caminho relativo de uma foto ou null;
     * {@code dbId} serve de reserva para achar a foto real.
     */
    public static Node de(String caminho, Integer dbId, double largura, double altura) {
        StackPane p = new StackPane();
        p.setMinSize(largura, altura);
        p.setPrefSize(largura, altura);
        p.setMaxSize(largura, altura);
        p.setStyle(FUNDO_COM_FOTO);
        PerfilFoto perfil = PerfilFoto.ler(caminho);
        String rel = perfil != null ? perfil.foto() : caminho;
        if (rel == null && dbId != null) rel = "assets/fotos/" + dbId + ".png";
        // opção "fotos dos jogadores" desligada: só a silhueta (sem ler os PNGs nem desenhar rostos)
        if (!br.futebas.ui.Opcoes.fotos()) {
            p.getChildren().add(silhuetaEmbaixo(largura, altura));
        } else if (rel == null || Imagens.arquivo(rel) == null) {
            p.getChildren().add(silhuetaEmbaixo(largura, altura));
        } else {
            ImageView v = new ImageView();
            v.setFitWidth(largura);
            v.setFitHeight(altura);
            v.setPreserveRatio(true);
            v.setSmooth(true);
            StackPane.setAlignment(v, javafx.geometry.Pos.BOTTOM_CENTER);
            p.getChildren().add(v);
            // a foto é lida em segundo plano: até chegar, a silhueta; depois a foto aparece suavemente
            boolean[] tarde = {false};
            Canvas[] sil = {null};
            boolean naHora = Imagens.pedir(rel, largura, altura, img -> {
                if (img == null) {
                    p.getChildren().remove(v);
                    if (sil[0] != null) p.getChildren().remove(sil[0]);
                    p.getChildren().add(0, silhuetaEmbaixo(largura, altura));
                    return;
                }
                v.setImage(img);
                if (tarde[0]) {
                    // sem fade (requisito): a foto entra direto; o quadro deixa de ser "sem foto"
                    v.setOpacity(1);
                    p.getChildren().remove(sil[0]);
                    if (p.getStyleClass().remove("sem-foto")) p.setStyle(FUNDO_COM_FOTO);
                }
            });
            if (!naHora) {
                tarde[0] = true;
                v.setOpacity(0);
                sil[0] = silhuetaEmbaixo(largura, altura);
                p.getChildren().add(0, sil[0]);
            }
        }
        Rectangle clip = new Rectangle(largura, altura);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        p.setClip(clip);
        return p;
    }

    /**
     * Foto de um atleta da Kings League: a foto normal do jogador ({@code caminho}/{@code dbId}) quando ele é
     * do banco e ela existe; senão a foto oficial da Kings (time + nome, depois só nome); senão o avatar com a
     * camisa do time da Kings ({@code caminho} pode ser a chave de perfil de {@code Jogo.foto}).
     */
    public static Node kings(String caminho, Integer dbId, String timeId, String nome, double largura, double altura) {
        PerfilFoto perfil = PerfilFoto.ler(caminho);
        String rel = perfil != null ? perfil.foto() : caminho;
        if (rel == null && dbId != null) rel = "assets/fotos/" + dbId + ".png";
        if (rel != null && Imagens.arquivo(rel) != null) return de(rel, null, largura, altura);
        String k = Imagens.caminhoFotoKings(timeId, nome);
        if (k != null) return de(k, null, largura, altura);
        // regra do jogo (13/11): só foto real; quem não tem fica com a silhueta (sem avatar gerado)
        if (true) return de(null, null, largura, altura);
        if (nome == null && perfil == null) return de(rel, null, largura, altura);
        if (perfil == null) perfil = PerfilFoto.deNome("kings", nome, paisKings(timeId), 0, null, null);
        String[] cores = timeId == null ? null : PerfilFoto.coresKings.apply(timeId);
        if (cores != null && cores[0] != null) perfil = perfil.comCores(cores[0], cores[1]);
        return de(perfil.comFoto(null).chave(), null, largura, altura);
    }

    /** País provável de um atleta da Kings só com nome, pela liga do time (para o tom de pele do avatar). */
    static String paisKings(String timeId) {
        if (timeId == null) return null;
        String[][] ligas = {{"kings-espanha", "ESP"}, {"kings-americas", "MEX"}, {"kings-italia", "ITA"}, {"kings-brasil", "BRA"},
                {"kings-franca", "FRA"}, {"kings-alemanha", "DEU"}, {"kings-mena", "MAR"}, {"kings-ios", "BRA"}};
        for (String[] l : ligas) if (timeId.startsWith(l[0])) return l[1];
        if (timeId.startsWith("kings-world-cup-nations-")) {
            String n = timeId.substring("kings-world-cup-nations-".length());
            String[][] paises = {{"brazil", "BRA"}, {"brasil", "BRA"}, {"spain", "ESP"}, {"mexico", "MEX"}, {"italy", "ITA"}, {"france", "FRA"},
                    {"germany", "DEU"}, {"morocco", "MAR"}, {"argentina", "ARG"}, {"colombia", "COL"}, {"usa", "USA"}, {"united-states", "USA"},
                    {"peru", "PER"}, {"chile", "CHL"}, {"portugal", "PRT"}, {"japan", "JPN"}, {"saudi", "SAU"}, {"egypt", "EGY"},
                    {"algeria", "DZA"}, {"tunisia", "TUN"}, {"nigeria", "NGA"}, {"senegal", "SEN"}, {"england", "ENG"}, {"netherlands", "NLD"},
                    {"belgium", "BEL"}, {"poland", "POL"}, {"turkey", "TUR"}, {"turkiye", "TUR"}, {"uruguay", "URY"}, {"venezuela", "VEN"},
                    {"ecuador", "ECU"}, {"korea", "KOR"}, {"china", "CHN"}, {"canada", "CAN"}, {"switzerland", "CHE"}, {"croatia", "HRV"}};
            for (String[] x : paises) if (n.startsWith(x[0])) return x[1];
        }
        return null;
    }

    /** Fundo preto do quadro sem foto real (regra do jogo 13/11): silhueta cinza-claro sobre preto. */
    static final String FUNDO_COM_FOTO = "-fx-background-color: linear-gradient(to bottom, #343943, #2a2e35); -fx-background-radius: 8;";

    public static final String FUNDO_SEM_FOTO = "-fx-background-color: #08090b; -fx-background-radius: 8;";

    private static Canvas silhuetaEmbaixo(double w, double h) {
        Canvas c = silhueta(w, h);
        c.parentProperty().addListener((o, a, pai) -> {
            if (pai instanceof StackPane sp) {
                sp.getStyleClass().add("sem-foto");
                sp.setStyle(FUNDO_SEM_FOTO);
            }
        });
        StackPane.setAlignment(c, javafx.geometry.Pos.BOTTOM_CENTER);
        return c;
    }

    public static Canvas silhueta(double w, double h) {
        // busto neutro (sem rosto), com volume: degradê suave, pescoço, gola e uma luz de recorte no topo
        Canvas c = new Canvas(w, h);
        GraphicsContext g = c.getGraphicsContext2D();
        double cx = w / 2, u = Math.min(w, h * 0.86);
        // ombros/camisa
        double topo = h * 0.66, larg = u * 0.92;
        g.setFill(new javafx.scene.paint.LinearGradient(0, topo, 0, h, false, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, Color.web("#b4bac3")), new javafx.scene.paint.Stop(1, Color.web("#7c838d"))));
        g.beginPath();
        g.moveTo(cx - larg / 2, h + 1);
        g.bezierCurveTo(cx - larg / 2, topo + u * 0.06, cx - larg * 0.30, topo - u * 0.02, cx - u * 0.13, topo - u * 0.03);
        g.lineTo(cx + u * 0.13, topo - u * 0.03);
        g.bezierCurveTo(cx + larg * 0.30, topo - u * 0.02, cx + larg / 2, topo + u * 0.06, cx + larg / 2, h + 1);
        g.closePath();
        g.fill();
        // pescoço
        double pw = u * 0.17;
        g.setFill(Color.web("#a3aab4"));
        g.fillRoundRect(cx - pw / 2, h * 0.50, pw, topo - h * 0.50 + u * 0.02, pw * 0.5, pw * 0.5);
        // gola em V
        g.setFill(Color.web("#08090b", 0.45));
        g.beginPath();
        g.moveTo(cx - u * 0.13, topo - u * 0.03);
        g.lineTo(cx, topo + u * 0.10);
        g.lineTo(cx + u * 0.13, topo - u * 0.03);
        g.closePath();
        g.fill();
        // cabeça
        double r = u * 0.205, hy = h * 0.37;
        g.setFill(new javafx.scene.paint.RadialGradient(0, 0, cx - r * 0.35, hy - r * 0.45, r * 1.6, false, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, Color.web("#d3d8de")), new javafx.scene.paint.Stop(1, Color.web("#959ca6"))));
        g.fillOval(cx - r, hy - r * 1.12, 2 * r, 2 * r * 1.16);
        // luz de recorte no topo da cabeça e dos ombros
        g.setStroke(Color.web("#ffffff", 0.25));
        g.setLineWidth(Math.max(1, u * 0.012));
        g.strokeArc(cx - r, hy - r * 1.12, 2 * r, 2 * r * 1.16, 35, 110, javafx.scene.shape.ArcType.OPEN);
        return c;
    }
}
