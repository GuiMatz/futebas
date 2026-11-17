package br.futebas.ui.comp;

import br.futebas.mundo.PerfilFoto;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Rosto gerado para quem não tem foto real (jogadores gerados, base, regens, atletas da Kings sem foto):
 * busto recortado sem fundo, no enquadramento das fotos de {@code assets/fotos} (197x256), com camisa nas
 * cores do clube, pescoço, rosto, cabelo, sobrancelhas e barba. Determinístico: a mesma
 * {@link PerfilFoto#semente()} dá sempre o mesmo rosto. Tom de pele, cabelo e olhos seguem uma
 * distribuição plausível para a nacionalidade (e o estado, no Brasil), sem traços exagerados.
 * As imagens ficam em cache na memória (listas mostram centenas).
 */
public final class Avatar {
    private Avatar() {}

    /** Quadro de referência (o das fotos reais). */
    static final double RW = 200, RH = 256;

    // ================================================================ cache
    private static final int MAX_CACHE = 1500;
    private static final Map<String, Image> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> e) {
            return size() > MAX_CACHE;
        }
    };

    /** Altura (px) em que a imagem é desenhada: degraus fixos, para o cache servir vários tamanhos. */
    static int degrau(double alturaPx) {
        if (alturaPx <= 64) return 64;
        if (alturaPx <= 128) return 128;
        if (alturaPx <= 256) return 256;
        return 512;
    }

    /** Imagem do avatar (fundo transparente) com pelo menos {@code alturaPx} de altura; só na thread do JavaFX. */
    public static synchronized Image imagem(PerfilFoto p, double alturaPx) {
        int h = degrau(alturaPx);
        String k = chave(p) + "@" + h;
        Image img = CACHE.get(k);
        if (img != null) return img;
        int w = (int) Math.round(h * RW / RH);
        Canvas c = new Canvas(w, h);
        desenhar(c.getGraphicsContext2D(), p, 0, 0, w, h);
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        img = c.snapshot(sp, null);
        CACHE.put(k, img);
        return img;
    }

    private static String chave(PerfilFoto p) {
        return Long.toHexString(p.semente()) + "|" + p.pais() + "|" + p.uf() + "|" + p.anoNasc() + "|" + p.cor1() + "|" + p.cor2();
    }

    /** Nó com o avatar encostado embaixo e centrado num quadro {@code w}×{@code h} (como a foto real). */
    public static Node no(PerfilFoto p, double w, double h) {
        double esc = Math.min(w / RW, h / RH);
        double iw = RW * esc, ih = RH * esc;
        Node n;
        if (Platform.isFxApplicationThread()) {
            ImageView v = new ImageView(imagem(p, ih * 2));
            v.setFitWidth(iw);
            v.setFitHeight(ih);
            v.setPreserveRatio(true);
            v.setSmooth(true);
            n = v;
        } else {
            Canvas c = new Canvas(iw, ih);
            desenhar(c.getGraphicsContext2D(), p, 0, 0, iw, ih);
            n = c;
        }
        StackPane.setAlignment(n, Pos.BOTTOM_CENTER);
        return n;
    }

    // ================================================================ sorteio
    /** Gerador próprio (splitmix64): o rosto não muda com a versão do Java. */
    static final class Rng {
        private long s;

        Rng(long semente) { s = semente; }

        long proximo() {
            long z = (s += 0x9e3779b97f4a7c15L);
            z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
            z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
            return z ^ (z >>> 31);
        }

        double d() { return (proximo() >>> 11) * 0x1.0p-53; }

        double entre(double a, double b) { return a + (b - a) * d(); }

        boolean chance(double p) { return d() < p; }

        int peso(double... pesos) {
            double t = 0;
            for (double x : pesos) t += x;
            double r = d() * t;
            for (int i = 0; i < pesos.length; i++) {
                r -= pesos[i];
                if (r < 0) return i;
            }
            return pesos.length - 1;
        }
    }

    // ================================================================ regiões
    private static final Map<String, String> REGIAO = new HashMap<>();

    private static void reg(String regiao, String paises) {
        for (String p : paises.split(" ")) REGIAO.put(p, regiao);
    }

    static {
        reg("nordico", "NOR SWE FIN DNK ISL FRO");
        reg("centro", "DEU AUT CHE POL CZE SVK HUN RUS UKR BLR LTU LVA EST SVN IRL SCO NIR WAL ROU BGR MDA LIE LUX");
        reg("oeste", "FRA ENG GBR BEL NLD");
        reg("sul", "ESP ITA PRT GRC HRV SRB BIH MNE MKD ALB CYP MLT AND XKX KOS SMR GIB");
        reg("turco", "TUR GEO ARM AZE");
        reg("arabe", "MAR DZA TUN LBY EGY SAU IRQ SYR JOR LBN ARE QAT KWT BHR OMN YEM PSE IRN ISR");
        reg("africa", "NGA GHA CMR SEN CIV MLI COD COG ZWE UGA KEN TZA ZMB AGO BFA GIN GNB GMB SLE LBR TGO BEN NER TCD "
                + "CAF GAB GNQ RWA BDI ETH SDN SSD SOM MOZ MWI MDG NAM BWA LSO SWZ CPV MRT ERI DJI COM STP MUS");
        reg("zaf", "ZAF");
        reg("brasil", "BRA");
        reg("platino", "ARG URY");
        reg("chile", "CHL");
        reg("mestico", "MEX PER BOL PRY GTM HND SLV NIC CRI");
        reg("andino", "COL VEN ECU PAN");
        reg("caribe", "JAM HTI TTO BRB GRD LCA VCT ATG KNA BHS CUW SUR GUY DMA BLZ BER ARU MTQ GLP GUF");
        reg("antilhas", "CUB DOM PRI");
        reg("norteam", "USA CAN AUS NZL");
        reg("lesteasia", "JPN KOR CHN PRK TWN HKG MNG MAC");
        reg("sudeste", "VNM THA IDN MYS PHL SGP KHM LAO MMR BRN TLS");
        reg("sulasia", "IND PAK BGD LKA NPL AFG MDV BTN");
        reg("asiacentral", "KAZ UZB KGZ TJK TKM");
        reg("oceania", "PNG FJI SLB VUT NCL TAH SAM TON");
    }

    /** Pesos dos 6 tons de pele (do mais claro ao mais escuro) por região. */
    private static double[] pesosPele(String regiao, String uf) {
        return switch (regiao) {
            case "nordico" -> new double[]{46, 36, 9, 3, 3, 3};
            case "centro" -> new double[]{38, 40, 13, 4, 2, 3};
            case "oeste" -> new double[]{20, 30, 14, 10, 12, 14};
            case "sul" -> new double[]{12, 36, 35, 11, 3, 3};
            case "turco" -> new double[]{5, 28, 45, 20, 2, 0};
            case "arabe" -> new double[]{3, 14, 38, 33, 9, 3};
            case "africa" -> new double[]{0, 0, 1, 5, 32, 62};
            case "zaf" -> new double[]{6, 10, 12, 14, 24, 34};
            case "brasil" -> {
                Set<String> sul = Set.of("RS", "SC", "PR");
                Set<String> norte = Set.of("BA", "MA", "PI", "PA", "AP", "AM", "RR", "AC", "RO", "TO", "SE", "AL", "PE", "PB", "RN", "CE");
                if (uf != null && sul.contains(uf)) yield new double[]{24, 32, 22, 12, 6, 4};
                if (uf != null && norte.contains(uf)) yield new double[]{5, 12, 20, 28, 20, 15};
                yield new double[]{12, 22, 22, 18, 14, 12};
            }
            case "platino" -> new double[]{20, 36, 30, 10, 2, 2};
            case "chile" -> new double[]{8, 28, 40, 20, 3, 1};
            case "mestico" -> new double[]{3, 13, 30, 38, 10, 6};
            case "andino" -> new double[]{3, 12, 26, 34, 14, 11};
            case "caribe" -> new double[]{0, 2, 4, 10, 30, 54};
            case "antilhas" -> new double[]{6, 14, 20, 22, 20, 18};
            case "norteam" -> new double[]{26, 28, 12, 12, 10, 12};
            case "lesteasia" -> new double[]{12, 48, 36, 4, 0, 0};
            case "sudeste" -> new double[]{0, 8, 32, 46, 14, 0};
            case "sulasia" -> new double[]{0, 3, 15, 40, 32, 10};
            case "asiacentral" -> new double[]{6, 30, 40, 20, 4, 0};
            case "oceania" -> new double[]{0, 0, 2, 15, 43, 40};
            default -> new double[]{18, 24, 20, 16, 11, 11};
        };
    }

    /** Regiões onde cabelo crespo é comum também em tons médios. */
    private static final Set<String> AFRO = Set.of("africa", "zaf", "brasil", "caribe", "antilhas", "oeste", "norteam", "andino", "oceania");

    private static final String[] PELE = {"#ebc8ad", "#ddb08c", "#cf9f79", "#b07b53", "#8a5738", "#5e3b27"};
    private static final String[] PELE_ASIA = {"#ecd0b0", "#e2bb94", "#d5a87f", "#b88759", "#8f603d", "#5e3b27"};

    // ================================================================ traços
    enum Corte { RASPADO, CURTO, LATERAL, TOPETE, FRANJA, MEDIO, COQUE, DEGRADE, AFRO, TRANCAS, CARECA, CACHOS }

    enum Barba { NENHUMA, SOMBRA, CURTA, CHEIA, CAVANHAQUE, BIGODE }

    /** Tudo o que o desenho usa, sorteado uma vez da semente. */
    static final class Tracos {
        Color pele, peleSombra, peleLuz, labio, cabelo, cabeloLuz, sobrancelha, olho, camisa, detalhe;
        Corte corte;
        Barba barba;
        int textura; // 0 liso, 1 ondulado, 2 cacheado, 3 crespo
        double largura, mandibula, queixo, pescoco, entradas, olhoAlt, olhoAbre, nariz, boca, sorriso, sobGrossa, sobArco;
        double ladoRisca, faseCachos;
        boolean listras, gola_v, raspadoLados;
        double idade;
    }

    static Tracos sortear(PerfilFoto p) {
        Rng r = new Rng(p.semente() ^ 0x5DEECE66DL);
        Tracos t = new Tracos();
        String regiao = p.pais() == null ? "mundo" : REGIAO.getOrDefault(p.pais(), "mundo");
        t.idade = p.anoNasc() > 1900 ? Math.max(15, Math.min(48, 2026 - p.anoNasc())) : 25;

        // pele
        int tom = r.peso(pesosPele(regiao, p.uf()));
        boolean asia = regiao.equals("lesteasia") || regiao.equals("sudeste") || regiao.equals("asiacentral");
        Color base = Color.web((asia ? PELE_ASIA : PELE)[tom]);
        double ajuste = r.entre(-0.045, 0.045);
        base = base.deriveColor(r.entre(-2.5, 2.5), 1 + r.entre(-0.06, 0.06), 1 + ajuste, 1);
        t.pele = base;
        t.peleSombra = mistura(base, Color.web("#3a1e12"), tom >= 4 ? 0.32 : 0.26);
        t.peleLuz = mistura(base, Color.WHITE, tom >= 4 ? 0.10 : 0.16);
        t.labio = mistura(base, Color.web(tom >= 4 ? "#4a2420" : "#a5585a"), tom >= 4 ? 0.35 : 0.30);

        // textura do cabelo
        boolean afro = AFRO.contains(regiao);
        double pCrespo = switch (tom) {
            case 5 -> 0.93;
            case 4 -> afro ? 0.78 : (regiao.equals("sulasia") || regiao.equals("arabe") || regiao.equals("sudeste") ? 0.08 : 0.35);
            case 3 -> afro ? 0.30 : 0.05;
            case 2 -> afro ? 0.08 : 0.01;
            default -> 0.0;
        };
        if (r.chance(pCrespo)) t.textura = 3;
        else if (regiao.equals("lesteasia") || regiao.equals("sudeste")) t.textura = r.chance(0.85) ? 0 : 1;
        else t.textura = r.peso(48, 36, 16);

        // cor do cabelo
        String cab;
        if (t.textura == 3 || tom >= 4) {
            cab = new String[]{"#16110e", "#231811", "#2c1d14"}[r.peso(70, 22, 8)];
        } else if (asia) {
            cab = r.chance(0.85) ? "#15110f" : "#2a1d16";
        } else {
            double[] w = regiao.equals("nordico") ? new double[]{2, 10, 25, 30, 28, 5}
                    : (tom <= 1 && (regiao.equals("centro") || regiao.equals("oeste") || regiao.equals("norteam"))) ? new double[]{8, 25, 30, 22, 12, 3}
                    : tom <= 1 ? new double[]{18, 36, 26, 12, 6, 2}
                    : tom == 2 ? new double[]{30, 42, 20, 6, 2, 0}
                    : new double[]{60, 32, 8, 0, 0, 0};
            cab = new String[]{"#17120f", "#2b1e16", "#4a3222", "#6e4c31", "#b48d55", "#97502b"}[r.peso(w)];
        }
        Color cabelo = Color.web(cab).deriveColor(0, 1, 1 + r.entre(-0.08, 0.08), 1);
        // platinado (moda no futebol), raro e só entre os mais novos
        boolean platinado = t.idade < 30 && r.chance(t.textura == 3 ? 0.05 : 0.02);
        if (platinado) cabelo = Color.web("#ddd0b0");
        // grisalho
        if (!platinado && t.idade >= 33 && r.chance(t.idade >= 38 ? 0.55 : 0.22)) cabelo = mistura(cabelo, Color.web("#a9a6a2"), r.entre(0.3, 0.6));
        t.cabelo = cabelo;
        t.cabeloLuz = mistura(cabelo, Color.WHITE, cabelo.getBrightness() > 0.5 ? 0.25 : 0.18);
        Color sob = platinado ? Color.web(cab.equals("#ddd0b0") ? "#3a2a1e" : "#2b1e16") : cabelo;
        if (sob.getBrightness() > 0.55) sob = mistura(sob, Color.web("#5a4028"), 0.45);
        t.sobrancelha = mistura(sob, t.peleSombra, 0.12);

        // olhos
        if (tom <= 1 && (regiao.equals("nordico") || regiao.equals("centro") || regiao.equals("oeste") || regiao.equals("norteam"))) {
            t.olho = Color.web(new String[]{"#3b2618", "#4f7192", "#5f7350", "#6e5230"}[r.peso(40, 32, 13, 15)]);
        } else if (tom <= 2 && !asia) {
            t.olho = Color.web(new String[]{"#3b2618", "#5f7350", "#6e5230", "#4f7192"}[r.peso(74, 8, 12, 6)]);
        } else {
            t.olho = Color.web(r.chance(0.8) ? "#2a1a10" : "#3b2618");
        }

        // corte
        double velho = t.idade >= 35 ? 2 : t.idade >= 30 ? 1 : 0;
        if (t.textura == 3) {
            t.corte = new Corte[]{Corte.RASPADO, Corte.DEGRADE, Corte.AFRO, Corte.TRANCAS, Corte.CARECA, Corte.CACHOS}
                    [r.peso(26, 34, 9, 12, 1 + 6 * velho, 12)];
        } else {
            double cachos = t.textura >= 1 ? 9 : 0;
            t.corte = new Corte[]{Corte.RASPADO, Corte.CURTO, Corte.LATERAL, Corte.TOPETE, Corte.FRANJA, Corte.MEDIO, Corte.COQUE, Corte.CARECA, Corte.CACHOS}
                    [r.peso(9, 22, 18, 18, asia ? 16 : 9, 9, 4, 0.5 + 6 * velho, cachos)];
        }
        t.entradas = Math.max(0, r.entre(-0.4, 0.5) + 0.25 * velho);
        t.raspadoLados = r.chance(0.55);
        t.ladoRisca = r.chance(0.5) ? 1 : -1;
        t.faseCachos = r.entre(0, Math.PI * 2);

        // barba
        double[] wb = t.idade < 21 ? new double[]{68, 22, 3, 0, 7, 0}
                : asia ? new double[]{62, 22, 7, 2, 5, 2}
                : new double[]{38, 26, 18, 6 + 3 * velho, 9, 3};
        t.barba = Barba.values()[r.peso(wb)];

        // proporções (variação contida: nada de caricatura)
        t.largura = r.entre(0.93, 1.07);
        t.mandibula = r.entre(0.88, 1.08);
        t.queixo = r.entre(0.85, 1.15);
        t.pescoco = r.entre(0.92, 1.12);
        t.olhoAlt = r.entre(-2, 2);
        t.olhoAbre = r.entre(0.85, 1.12) * (regiao.equals("lesteasia") ? 0.82 : 1);
        t.nariz = r.entre(0.88, 1.12) * (tom >= 4 ? 1.06 : 1);
        t.boca = r.entre(0.9, 1.1);
        t.sorriso = r.chance(0.3) ? r.entre(0.6, 1.4) : r.entre(-0.3, 0.3);
        t.sobGrossa = r.entre(0.85, 1.25);
        t.sobArco = r.entre(-1.5, 2.5);

        // camisa
        Color c1 = cor(p.cor1(), null), c2 = cor(p.cor2(), null);
        if (c1 == null) {
            c1 = Color.web("#3d4350");
            c2 = Color.web("#2a2f39");
        } else if (c2 == null || distancia(c1, c2) < 0.12) {
            c2 = c1.getBrightness() > 0.6 ? c1.deriveColor(0, 1, 0.6, 1) : c1.deriveColor(0, 0.8, 1.6, 1);
        }
        t.camisa = c1;
        t.detalhe = c2;
        t.listras = r.chance(0.45);
        t.gola_v = r.chance(0.35);
        return t;
    }

    // ================================================================ desenho
    /** Desenha o avatar no retângulo (x, y, w, h): busto encostado embaixo, centrado. */
    public static void desenhar(GraphicsContext g, PerfilFoto p, double x, double y, double w, double h) {
        Tracos t = sortear(p);
        double esc = Math.min(w / RW, h / RH);
        g.save();
        g.translate(x + (w - RW * esc) / 2, y + h - RH * esc);
        g.scale(esc, esc);
        g.beginPath();
        g.rect(0, 0, RW, RH);
        g.clip();
        // um pouco mais perto (cabeça maior, como nas fotos reais)
        g.translate(RW / 2, RH);
        g.scale(ZOOM, ZOOM);
        g.translate(-RW / 2, -RH);
        new Desenho(g, t, esc * ZOOM).tudo();
        g.restore();
    }

    private static final double ZOOM = 1.07;

    /** O desenho de um rosto no quadro de referência 200x256. */
    private static final class Desenho {
        final GraphicsContext g;
        final Tracos t;
        final double cx = 100;
        // crânio
        final double cy = 106, rx, ry = 66;
        // rosto
        final double jw, cw, chinY, olhoY, bocaY;
        final double nw; // meia largura do pescoço
        final double esc; // px por unidade de referência
        /** Quanto o volume do cabelo se concentra no alto (maior = laterais rentes ao crânio). */
        double expoente = 0.7;

        Desenho(GraphicsContext g, Tracos t, double esc) {
            this.g = g;
            this.t = t;
            this.esc = esc;
            rx = 45 * t.largura;
            jw = 37 * t.largura * t.mandibula;
            cw = 15 * t.queixo;
            chinY = 184;
            olhoY = 122 + t.olhoAlt;
            bocaY = 162;
            nw = 20 * t.pescoco;
        }

        void tudo() {
            if (t.corte == Corte.MEDIO) cabeloAtras();
            pescoco();
            camisa();
            orelhas();
            cabeca();
            sombrasRosto();
            if (t.barba != Barba.NENHUMA) {
                g.save();
                g.setEffect(new javafx.scene.effect.GaussianBlur(Math.max(0.5, esc * 2)));
                barba();
                g.restore();
            }
            olhos();
            sobrancelhas();
            nariz();
            boca();
            if (t.barba == Barba.CURTA || t.barba == Barba.CHEIA || t.barba == Barba.CAVANHAQUE || t.barba == Barba.BIGODE) bigode();
            // bordas do cabelo macias (nada de "capacete" recortado)
            g.save();
            g.setEffect(new javafx.scene.effect.GaussianBlur(Math.max(0.6, 1.3 * esc * 2)));
            cabelo();
            g.restore();
        }

        // ---------------------------------------------------------------- corpo
        void pescoco() {
            double top = 150, base = 222;
            g.beginPath();
            g.moveTo(cx - nw, top);
            g.bezierCurveTo(cx - nw, 180, cx - nw - 2, 200, cx - nw - 8, base);
            g.lineTo(cx + nw + 8, base);
            g.bezierCurveTo(cx + nw + 2, 200, cx + nw, 180, cx + nw, top);
            g.closePath();
            g.setFill(t.pele);
            g.fill();
            // sombra do queixo sobre o pescoço
            g.save();
            g.clip();
            g.setFill(new LinearGradient(0, top, 0, top + 52, false, CycleMethod.NO_CYCLE,
                    new Stop(0, t.peleSombra), new Stop(0.55, alfa(t.peleSombra, 0.45)), new Stop(1, alfa(t.peleSombra, 0))));
            g.fillRect(0, top, RW, 60);
            // lados mais escuros (volume)
            g.setFill(new LinearGradient(cx - nw - 8, 0, cx + nw + 8, 0, false, CycleMethod.NO_CYCLE,
                    new Stop(0, alfa(t.peleSombra, 0.55)), new Stop(0.25, alfa(t.peleSombra, 0)), new Stop(0.75, alfa(t.peleSombra, 0)),
                    new Stop(1, alfa(t.peleSombra, 0.55))));
            g.fillRect(0, top, RW, base - top);
            g.restore();
        }

        void camisa() {
            double golaY = 201, fundo = t.gola_v ? 228 : 216;
            double ombroY = 224;
            Color luz = mistura(t.camisa, Color.WHITE, 0.10), sombra = mistura(t.camisa, Color.BLACK, 0.28);
            g.beginPath();
            g.moveTo(-4, RH + 2);
            g.lineTo(-4, ombroY + 14);
            g.bezierCurveTo(4, ombroY - 6, 28, golaY - 2, cx - nw - 12, golaY - 4);
            if (t.gola_v) {
                g.lineTo(cx, fundo);
                g.lineTo(cx + nw + 12, golaY - 4);
            } else {
                g.bezierCurveTo(cx - nw - 6, fundo, cx + nw + 6, fundo, cx + nw + 12, golaY - 4);
            }
            g.bezierCurveTo(RW - 28, golaY - 2, RW - 4, ombroY - 6, RW + 4, ombroY + 14);
            g.lineTo(RW + 4, RH + 2);
            g.closePath();
            g.setFill(new LinearGradient(0, golaY - 6, 0, RH, false, CycleMethod.NO_CYCLE, new Stop(0, luz), new Stop(1, sombra)));
            g.fill();
            g.save();
            g.clip();
            // volume: laterais e dobra do peito
            g.setFill(new LinearGradient(0, 0, RW, 0, false, CycleMethod.NO_CYCLE,
                    new Stop(0, alfa(Color.BLACK, 0.30)), new Stop(0.22, alfa(Color.BLACK, 0)), new Stop(0.78, alfa(Color.BLACK, 0)),
                    new Stop(1, alfa(Color.BLACK, 0.30))));
            g.fillRect(0, golaY - 10, RW, RH);
            // listras nos ombros (raglan), na cor do detalhe
            if (t.listras) {
                g.setStroke(t.detalhe);
                g.setLineWidth(4.5);
                g.setLineCap(StrokeLineCap.BUTT);
                for (int lado = -1; lado <= 1; lado += 2) {
                    double sx = cx + lado * (nw + 16), ex = cx + lado * 96;
                    g.beginPath();
                    g.moveTo(sx, golaY - 6);
                    g.bezierCurveTo(cx + lado * (nw + 34), golaY - 2, cx + lado * 80, ombroY - 4, ex, ombroY + 10);
                    g.stroke();
                }
            }
            // sombra que o pescoço faz na gola
            g.setStroke(alfa(Color.BLACK, 0.22));
            g.setLineWidth(7);
            golaCaminho(golaY, fundo + 4);
            g.stroke();
            g.restore();
            // gola
            g.setStroke(t.detalhe);
            g.setLineWidth(5);
            g.setLineCap(StrokeLineCap.ROUND);
            golaCaminho(golaY, fundo);
            g.stroke();
            g.setStroke(alfa(Color.BLACK, 0.18));
            g.setLineWidth(1);
            golaCaminho(golaY + 2.6, fundo + 2.6);
            g.stroke();
        }

        void golaCaminho(double golaY, double fundo) {
            g.beginPath();
            g.moveTo(cx - nw - 12, golaY - 4);
            if (t.gola_v) {
                g.lineTo(cx, fundo);
                g.lineTo(cx + nw + 12, golaY - 4);
            } else {
                g.bezierCurveTo(cx - nw - 6, fundo, cx + nw + 6, fundo, cx + nw + 12, golaY - 4);
            }
        }

        // ---------------------------------------------------------------- cabeça
        void orelhas() {
            for (int lado = -1; lado <= 1; lado += 2) {
                double ex = cx + lado * (rx - 1), ey = olhoY + 6;
                g.setFill(t.pele);
                g.fillOval(ex - 8, ey - 15, 16, 30);
                g.setFill(alfa(t.peleSombra, 0.55));
                g.fillOval(ex - 4 + lado * 1.5, ey - 10, 8, 20);
                g.setFill(alfa(t.peleSombra, 0.35));
                g.fillOval(ex - 8, ey - 15, 16, 30);
            }
        }

        /** Orelhas por cima do cabelo comprido (cabelo atrás da orelha), sem cobrir o rosto. */
        void orelhasNaFrente() {
            g.save();
            g.setEffect(null);
            g.beginPath();
            g.rect(0, 0, cx - rx + 1.5, RH);
            g.rect(cx + rx - 1.5, 0, RW, RH);
            g.clip();
            orelhas();
            g.restore();
        }

        void contorno(double d) {
            contorno(d, true);
        }

        /** Contorno da cabeça (crânio + rosto); {@code d} afasta o contorno (barba cheia, cabelo). */
        void contorno(double d, boolean novo) {
            double r = rx + d, top = cy - ry - d;
            if (novo) g.beginPath();
            g.moveTo(cx, top);
            g.bezierCurveTo(cx + r * 0.56, top, cx + r, cy - ry * 0.62, cx + r, cy - 2);
            g.bezierCurveTo(cx + r, cy + 26, cx + jw + d + 3, cy + 48, cx + cw + d * 0.7, chinY - 8 + d);
            g.bezierCurveTo(cx + cw * 0.55, chinY + d, cx - cw * 0.55, chinY + d, cx - cw - d * 0.7, chinY - 8 + d);
            g.bezierCurveTo(cx - jw - d - 3, cy + 48, cx - r, cy + 26, cx - r, cy - 2);
            g.bezierCurveTo(cx - r, cy - ry * 0.62, cx - r * 0.56, top, cx, top);
            g.closePath();
        }

        void cabeca() {
            contorno(0);
            g.setFill(t.pele);
            g.fill();
        }

        void sombrasRosto() {
            g.save();
            contorno(0);
            g.clip();
            // luz de cima/frente e sombra nas bordas
            g.setFill(new RadialGradient(0, 0, cx - 4, cy + 4, rx * 1.25, false, CycleMethod.NO_CYCLE,
                    new Stop(0, alfa(t.peleLuz, 0.55)), new Stop(0.45, alfa(t.peleLuz, 0)), new Stop(0.78, alfa(t.peleSombra, 0)),
                    new Stop(1, alfa(t.peleSombra, 0.75))));
            g.fillRect(0, 0, RW, RH);
            // maçãs do rosto (sombra suave embaixo)
            for (int lado = -1; lado <= 1; lado += 2) {
                g.setFill(new RadialGradient(0, 0, cx + lado * (rx - 6), bocaY - 8, 20, false, CycleMethod.NO_CYCLE,
                        new Stop(0, alfa(t.peleSombra, 0.30)), new Stop(1, alfa(t.peleSombra, 0))));
                g.fillRect(0, 0, RW, RH);
            }
            // sombra das órbitas
            for (int lado = -1; lado <= 1; lado += 2) {
                g.setFill(new RadialGradient(0, 0, cx + lado * 18, olhoY - 2, 15, false, CycleMethod.NO_CYCLE,
                        new Stop(0, alfa(t.peleSombra, 0.32)), new Stop(1, alfa(t.peleSombra, 0))));
                g.fillRect(0, 0, RW, RH);
            }
            // queixo
            g.setFill(new RadialGradient(0, 0, cx, chinY - 10, 14, false, CycleMethod.NO_CYCLE,
                    new Stop(0, alfa(t.peleLuz, 0.35)), new Stop(1, alfa(t.peleLuz, 0))));
            g.fillRect(0, 0, RW, RH);
            g.restore();
        }

        // ---------------------------------------------------------------- olhos, sobrancelhas, nariz, boca
        void olhos() {
            double ab = t.olhoAbre;
            for (int lado = -1; lado <= 1; lado += 2) {
                double ex = cx + lado * 18.5, ey = olhoY;
                double ext = ex + lado * 9.5, inn = ex - lado * 8.5;
                // pálpebra/olho
                g.beginPath();
                g.moveTo(inn, ey + 0.5);
                g.bezierCurveTo(inn + lado * 4, ey - 5.6 * ab, ext - lado * 5, ey - 6 * ab, ext, ey - 1.2);
                g.bezierCurveTo(ext - lado * 5, ey + 3.6 * ab, inn + lado * 4, ey + 3.8 * ab, inn, ey + 0.5);
                g.closePath();
                g.setFill(mistura(Color.web("#f4efe8"), t.pele, 0.18));
                g.fill();
                g.save();
                g.clip();
                double ix = ex + 0.4 * lado * 0, iy = ey - 0.6;
                g.setFill(t.olho);
                g.fillOval(ix - 4.4, iy - 4.4, 8.8, 8.8);
                g.setFill(mistura(t.olho, Color.BLACK, 0.4));
                g.setStroke(mistura(t.olho, Color.BLACK, 0.45));
                g.setLineWidth(0.8);
                g.strokeOval(ix - 4.4, iy - 4.4, 8.8, 8.8);
                g.setFill(Color.web("#0d0907"));
                g.fillOval(ix - 1.9, iy - 1.9, 3.8, 3.8);
                g.setFill(alfa(Color.WHITE, 0.85));
                g.fillOval(ix + 0.8, iy - 2.4, 1.6, 1.6);
                // sombra da pálpebra de cima
                g.setFill(new LinearGradient(0, ey - 6, 0, ey, false, CycleMethod.NO_CYCLE,
                        new Stop(0, alfa(t.peleSombra, 0.65)), new Stop(1, alfa(t.peleSombra, 0))));
                g.fillRect(ex - 12, ey - 7, 24, 7);
                g.restore();
                // linha da pálpebra de cima (cílios)
                g.setStroke(mistura(Color.web("#1a120d"), t.peleSombra, 0.25));
                g.setLineWidth(1.7);
                g.setLineCap(StrokeLineCap.ROUND);
                g.beginPath();
                g.moveTo(inn, ey + 0.3);
                g.bezierCurveTo(inn + lado * 4, ey - 5.6 * ab, ext - lado * 5, ey - 6 * ab, ext + lado * 0.8, ey - 1.0);
                g.stroke();
                // dobra da pálpebra
                g.setStroke(alfa(t.peleSombra, 0.55));
                g.setLineWidth(0.9);
                g.beginPath();
                g.moveTo(inn + lado * 1.5, ey - 4.5 * ab);
                g.bezierCurveTo(inn + lado * 5, ey - 9 * ab, ext - lado * 5, ey - 9.2 * ab, ext - lado * 0.5, ey - 4.2);
                g.stroke();
                // linha de baixo, bem leve
                g.setStroke(alfa(t.peleSombra, 0.40));
                g.setLineWidth(0.8);
                g.beginPath();
                g.moveTo(inn + lado * 2, ey + 2.6);
                g.bezierCurveTo(inn + lado * 6, ey + 4.6 * ab, ext - lado * 5, ey + 4.2 * ab, ext - lado * 1, ey + 1.2);
                g.stroke();
            }
        }

        void sobrancelhas() {
            double gr = t.sobGrossa;
            g.setFill(t.sobrancelha);
            for (int lado = -1; lado <= 1; lado += 2) {
                double ex = cx + lado * 18.5, by = olhoY - 11;
                double inn = ex - lado * 11, ext = ex + lado * 12.5;
                g.beginPath();
                g.moveTo(inn, by + 1.2);
                g.bezierCurveTo(inn + lado * 6, by - 2.8 - t.sobArco, ext - lado * 7, by - 4.2 - t.sobArco, ext, by + 1.6);
                g.bezierCurveTo(ext - lado * 7, by - 4.2 - t.sobArco + 2.4 * gr, inn + lado * 6, by - 2.8 - t.sobArco + 3.6 * gr, inn, by + 1.2 + 3.4 * gr);
                g.closePath();
                g.setGlobalAlpha(0.92);
                g.fill();
                g.setGlobalAlpha(1);
                // fios na ponta de dentro (menos "carimbo")
                g.setStroke(alfa(t.sobrancelha, 0.5));
                g.setLineWidth(0.7);
                for (int i = 0; i < 4; i++) {
                    double sx = inn + lado * (1 + i * 1.6);
                    g.strokeLine(sx, by + 1.2 + 3.2 * gr, sx + lado * 2.5, by - 1.5);
                }
            }
        }

        void nariz() {
            double n = t.nariz, top = olhoY + 3, ponta = olhoY + 27;
            // dorso: sombra de um lado
            g.setStroke(alfa(t.peleSombra, 0.45));
            g.setLineWidth(1.6);
            g.setLineCap(StrokeLineCap.ROUND);
            g.beginPath();
            g.moveTo(cx - 6, top);
            g.bezierCurveTo(cx - 5, top + 9, cx - 6.5 * n, ponta - 8, cx - 7.5 * n, ponta - 2);
            g.stroke();
            // ponta com luz
            g.setFill(new RadialGradient(0, 0, cx + 1, ponta - 3, 7 * n, false, CycleMethod.NO_CYCLE,
                    new Stop(0, alfa(t.peleLuz, 0.6)), new Stop(1, alfa(t.peleLuz, 0))));
            g.fillOval(cx - 9 * n, ponta - 11, 18 * n, 14);
            // base e narinas
            g.setFill(alfa(t.peleSombra, 0.38));
            g.beginPath();
            g.moveTo(cx - 9.5 * n, ponta);
            g.bezierCurveTo(cx - 7 * n, ponta + 5, cx + 7 * n, ponta + 5, cx + 9.5 * n, ponta);
            g.bezierCurveTo(cx + 6 * n, ponta + 2.6, cx - 6 * n, ponta + 2.6, cx - 9.5 * n, ponta);
            g.fill();
            g.setFill(alfa(mistura(t.peleSombra, Color.BLACK, 0.4), 0.55));
            g.fillOval(cx - 6.2 * n, ponta + 0.3, 4.2, 2.4);
            g.fillOval(cx + 6.2 * n - 4.2, ponta + 0.3, 4.2, 2.4);
            // asas
            g.setStroke(alfa(t.peleSombra, 0.55));
            g.setLineWidth(1.1);
            for (int lado = -1; lado <= 1; lado += 2) {
                g.beginPath();
                g.moveTo(cx + lado * 9.5 * n, ponta - 6);
                g.bezierCurveTo(cx + lado * 11.5 * n, ponta - 3, cx + lado * 11 * n, ponta + 1, cx + lado * 7.5 * n, ponta + 2.2);
                g.stroke();
            }
        }

        void boca() {
            double mw = 13 * t.boca, y = bocaY, s = t.sorriso;
            // lábio de baixo
            g.setFill(alfa(t.labio, 0.85));
            g.beginPath();
            g.moveTo(cx - mw + 1, y - s * 0.8);
            g.bezierCurveTo(cx - mw * 0.5, y + 6.5, cx + mw * 0.5, y + 6.5, cx + mw - 1, y - s * 0.8);
            g.bezierCurveTo(cx + mw * 0.5, y + 1.5, cx - mw * 0.5, y + 1.5, cx - mw + 1, y - s * 0.8);
            g.fill();
            // brilho do lábio de baixo
            g.setFill(alfa(t.peleLuz, 0.35));
            g.fillOval(cx - mw * 0.35, y + 2.2, mw * 0.7, 2.2);
            // lábio de cima
            g.setFill(mistura(t.labio, t.peleSombra, 0.35));
            g.beginPath();
            g.moveTo(cx - mw, y - s);
            g.bezierCurveTo(cx - mw * 0.6, y - 3.2, cx - 3, y - 4.4, cx, y - 3.2);
            g.bezierCurveTo(cx + 3, y - 4.4, cx + mw * 0.6, y - 3.2, cx + mw, y - s);
            g.bezierCurveTo(cx + mw * 0.5, y + 0.6, cx - mw * 0.5, y + 0.6, cx - mw, y - s);
            g.fill();
            // linha da boca
            g.setStroke(mistura(t.labio, Color.BLACK, 0.55));
            g.setLineWidth(1.3);
            g.setLineCap(StrokeLineCap.ROUND);
            g.beginPath();
            g.moveTo(cx - mw, y - s);
            g.bezierCurveTo(cx - mw * 0.5, y + 1.2, cx + mw * 0.5, y + 1.2, cx + mw, y - s);
            g.stroke();
            // sombra embaixo do lábio
            g.setFill(new RadialGradient(0, 0, cx, y + 10, 9, false, CycleMethod.NO_CYCLE,
                    new Stop(0, alfa(t.peleSombra, 0.35)), new Stop(1, alfa(t.peleSombra, 0))));
            g.fillOval(cx - 12, y + 3, 24, 13);
            // filtro (sulco acima do lábio)
            g.setFill(alfa(t.peleSombra, 0.18));
            g.fillOval(cx - 3, y - 13, 6, 9);
        }

        // ---------------------------------------------------------------- barba
        void barba() {
            Color c = t.cabelo.getBrightness() > 0.7 ? mistura(t.cabelo, Color.web("#6b5a48"), 0.4) : t.cabelo;
            double a = switch (t.barba) {
                case SOMBRA -> 0.16;
                case CURTA -> 0.78;
                case CHEIA -> 0.93;
                default -> 0.0;
            };
            if (t.barba == Barba.SOMBRA || t.barba == Barba.CURTA || t.barba == Barba.CHEIA) {
                double d = t.barba == Barba.CHEIA ? 5 : t.barba == Barba.CURTA ? 1.5 : 0;
                g.save();
                caminhoBarba(d);
                g.setFill(alfa(c, a));
                g.fill();
                if (t.barba != Barba.SOMBRA) {
                    g.clip();
                    textura(c, 70, 3.5);
                }
                g.restore();
            } else if (t.barba == Barba.CAVANHAQUE) {
                g.setFill(alfa(c, 0.85));
                double y = bocaY + 7;
                g.beginPath();
                g.moveTo(cx - 10, y);
                g.bezierCurveTo(cx - 5, y + 1.5, cx + 5, y + 1.5, cx + 10, y);
                g.bezierCurveTo(cx + 12, y + 10, cx + 8, chinY - 0.5, cx, chinY + 1.5);
                g.bezierCurveTo(cx - 8, chinY - 0.5, cx - 12, y + 10, cx - 10, y);
                g.fill();
                // sombra leve no resto do rosto
                caminhoBarba(0);
                g.setFill(alfa(c, 0.12));
                g.fill();
            }
        }

        /** Área da barba: costeletas, mandíbula e queixo (sem a boca e as bochechas altas). */
        void caminhoBarba(double d) {
            double sb = olhoY + 4; // costeleta
            g.beginPath();
            g.moveTo(cx - rx + 1, sb);
            // contorno externo, de cima à esquerda até o queixo e de volta à direita
            g.bezierCurveTo(cx - rx - d * 0.3, cy + 26, cx - jw - d - 3, cy + 48, cx - cw - d * 0.7, chinY - 8 + d);
            g.bezierCurveTo(cx - cw * 0.55, chinY + d, cx + cw * 0.55, chinY + d, cx + cw + d * 0.7, chinY - 8 + d);
            g.bezierCurveTo(cx + jw + d + 3, cy + 48, cx + rx + d * 0.3, cy + 26, cx + rx - 1, sb);
            // linha interna (bochecha)
            g.lineTo(cx + rx - 7, sb);
            g.bezierCurveTo(cx + rx - 9, sb + 18, cx + 22, bocaY - 6, cx + 15, bocaY - 9);
            // em volta da boca (o bigode é desenhado à parte)
            g.bezierCurveTo(cx + 18, bocaY + 4, cx + 10, bocaY + 9, cx, bocaY + 9);
            g.bezierCurveTo(cx - 10, bocaY + 9, cx - 18, bocaY + 4, cx - 15, bocaY - 9);
            g.bezierCurveTo(cx - 22, bocaY - 6, cx - rx + 9, sb + 18, cx - rx + 7, sb);
            g.closePath();
        }

        void bigode() {
            Color c = t.cabelo.getBrightness() > 0.7 ? mistura(t.cabelo, Color.web("#6b5a48"), 0.4) : t.cabelo;
            double a = t.barba == Barba.CURTA ? 0.75 : t.barba == Barba.CHEIA ? 0.9 : 0.8;
            double y = bocaY - 4;
            g.setFill(alfa(c, a));
            g.beginPath();
            g.moveTo(cx - 15, y + 3.5);
            g.bezierCurveTo(cx - 12, y - 5.5, cx - 4, y - 7, cx, y - 5.5);
            g.bezierCurveTo(cx + 4, y - 7, cx + 12, y - 5.5, cx + 15, y + 3.5);
            g.bezierCurveTo(cx + 10, y + 0.4, cx + 4, y - 0.2, cx, y + 0.4);
            g.bezierCurveTo(cx - 4, y - 0.2, cx - 10, y + 0.4, cx - 15, y + 3.5);
            g.fill();
        }

        /** Pontinhos/fios para dar textura de pelo (dentro do clip atual). */
        void textura(Color c, int n, double comp) {
            Rng r = new Rng(Double.doubleToLongBits(t.largura * 1000 + t.queixo));
            g.setLineWidth(0.7);
            g.setLineCap(StrokeLineCap.ROUND);
            for (int i = 0; i < n; i++) {
                double x = r.entre(cx - rx - 6, cx + rx + 6), y = r.entre(olhoY, chinY + 6);
                boolean claro = r.chance(0.5);
                g.setStroke(alfa(claro ? mistura(c, Color.WHITE, 0.25) : mistura(c, Color.BLACK, 0.4), 0.35));
                double ang = Math.PI / 2 + (x - cx) / 80 + r.entre(-0.3, 0.3);
                g.strokeLine(x, y, x + Math.cos(ang) * comp, y + Math.sin(ang) * comp);
            }
        }

        // ---------------------------------------------------------------- cabelo
        void cabeloAtras() {
            g.setFill(mistura(t.cabelo, Color.BLACK, 0.25));
            g.beginPath();
            g.moveTo(cx - rx - 8, cy - 10);
            g.bezierCurveTo(cx - rx - 11, cy + 20, cx - rx - 8, cy + 34, cx - rx + 4, cy + 40);
            g.lineTo(cx + rx - 4, cy + 40);
            g.bezierCurveTo(cx + rx + 8, cy + 34, cx + rx + 11, cy + 20, cx + rx + 8, cy - 10);
            g.closePath();
            g.fill();
        }

        void cabelo() {
            Color raspado = mistura(t.cabelo, t.pele, 0.5);
            double hl = 84 - 2 * (t.idade < 20 ? 1 : 0); // linha do cabelo na testa
            double ent = t.entradas;
            switch (t.corte) {
                case CARECA -> {
                    if (t.raspadoLados) {
                        // sombra de cabelo raspado só nos lados, sumindo para o alto
                        g.save();
                        tampa(1.5, 1.5, olhoY - 2, hl, hl + 2, 1.2, 0, false);
                        g.clip();
                        g.setFill(new LinearGradient(cx - rx, 0, cx + rx, 0, false, CycleMethod.NO_CYCLE,
                                new Stop(0, alfa(raspado, 0.55)), new Stop(0.3, alfa(raspado, 0)), new Stop(0.7, alfa(raspado, 0)),
                                new Stop(1, alfa(raspado, 0.55))));
                        g.fillRect(0, cy - 40, RW, 60);
                        g.restore();
                    }
                    brilhoCareca();
                }
                case RASPADO -> {
                    g.setFill(alfa(mistura(t.cabelo, t.pele, 0.28), 0.95));
                    tampa(2, 1.5, olhoY - 4, hl, hl + 2, ent, 0, false);
                    g.fill();
                    acabamento(0.25, false);
                }
                case CURTO -> {
                    topo(raspado, 9, 4, hl, ent, 0, t.textura >= 2, 1);
                    franjaCurta(hl - 1);
                }
                case LATERAL -> {
                    topo(raspado, 13, 5, hl - 1, ent, 4 * t.ladoRisca, false, 1);
                    risca();
                }
                case TOPETE -> {
                    topo(raspado, 21, 3, hl - 2, ent, 3 * t.ladoRisca, t.textura >= 2, 1.2);
                    if (t.textura < 2) fiosParaTras();
                }
                case FRANJA -> {
                    topo(raspado, 11, 5, hl + 8, 0, 0, false, 1);
                    franja(hl + 8);
                }
                case MEDIO -> {
                    g.setFill(t.cabelo);
                    tampa(13, 8, olhoY + 4, hl - 1, hl + 4, ent * 0.5, 6 * t.ladoRisca, t.textura >= 2);
                    g.fill();
                    acabamento(1, t.textura >= 2);
                    risca();
                    orelhasNaFrente();
                }
                case COQUE -> {
                    g.setFill(mistura(t.cabelo, Color.BLACK, 0.12));
                    g.fillOval(cx - 13, cy - ry - 18, 26, 20);
                    topo(raspado, 6, 3, hl - 1, ent, 0, false, 0.8);
                    fiosParaTras();
                }
                case DEGRADE -> topo(raspado, 12, 3, hl, ent, 0, true, 1);
                case AFRO -> {
                    g.setFill(t.cabelo);
                    tampa(25, 15, olhoY + 2, hl - 2, hl + 4, ent * 0.5, 0, true);
                    g.fill();
                    acabamento(1.5, true);
                }
                case CACHOS -> topo(raspado, 17, 4, hl, ent, 0, true, 1.3);
                case TRANCAS -> {
                    topo(raspado, 15, 4, hl, ent, 0, true, 1);
                    trancas(hl);
                }
            }
        }

        /**
         * Corte com os lados curtos: lados (raspados em degradê, ou só curtos) e o volume de cima, que
         * desce pelas têmporas sumindo aos poucos (sem a borda de "tigela").
         */
        void topo(Color raspado, double vTopo, double vLado, double hl, double ent, double pico, boolean crespo, double forca) {
            boolean degrade = t.raspadoLados || t.corte == Corte.DEGRADE || t.corte == Corte.TRANCAS;
            if (degrade) {
                g.save();
                tampa(2, 1.5, olhoY - 2, hl, hl + 2, ent, 0, false);
                g.clip();
                g.setFill(new LinearGradient(0, cy - 40, 0, olhoY, false, CycleMethod.NO_CYCLE,
                        new Stop(0, mistura(t.cabelo, t.pele, 0.2)), new Stop(1, alfa(raspado, 0.35))));
                g.fillRect(0, 0, RW, RH);
                g.restore();
            } else {
                g.setFill(mistura(t.cabelo, Color.BLACK, 0.08));
                tampa(4, 3, olhoY - 3, hl, hl + 2, ent, 0, false);
                g.fill();
                acabamento(0.6, false);
            }
            double base = hl + (degrade ? 18 : 10);
            expoente = degrade ? 1.8 : 1.0;
            tampa(vTopo, degrade ? Math.min(vLado, 1.5) : vLado, base, hl, hl + 4, ent, pico, crespo);
            expoente = 0.7;
            g.setFill(new LinearGradient(0, hl - 26, 0, base, false, CycleMethod.NO_CYCLE,
                    new Stop(0, t.cabelo), new Stop(0.6, t.cabelo), new Stop(1, alfa(t.cabelo, degrade ? 0 : 0.6))));
            g.fill();
            acabamento(forca, crespo);
        }

        /**
         * Caminho da "tampa" de cabelo: contorno externo do crânio afastado ({@code vTopo} no alto,
         * {@code vLado} nos lados) até {@code baseLado}, e a linha do cabelo na testa ({@code hl}, com
         * as entradas). {@code pico}: deslocamento lateral do volume; {@code crespo}: borda ondulada.
         */
        void tampa(double vTopo, double vLado, double baseLado, double hl, double hlLados, double ent, double pico, boolean crespo) {
            int n = 72;
            double ryy = ry;
            // ângulo em que o contorno chega à base dos lados
            double sin0 = (cy - baseLado) / (ryy + vLado);
            sin0 = Math.max(-0.95, Math.min(0.95, sin0));
            double th0 = Math.asin(sin0);
            g.beginPath();
            for (int i = 0; i <= n; i++) {
                double th = th0 + (Math.PI - 2 * th0) * i / n;
                double s = Math.max(0, Math.sin(th));
                double v = vLado + (vTopo - vLado) * Math.pow(s, expoente);
                // volume puxado para um lado (risca/topete)
                v += pico * Math.cos(th) * s;
                if (crespo) v += 2.2 * (0.5 + 0.5 * Math.sin(th * 23 + t.faseCachos)) * (0.4 + s);
                double x = cx + (rx + v) * Math.cos(th);
                double y = cy - (ryy + v) * Math.sin(th);
                if (y > baseLado) y = baseLado;
                if (i == 0) g.moveTo(x, y);
                else g.lineTo(x, y);
            }
            // lado esquerdo: desce para a costeleta e volta pela linha do cabelo
            double xi = rx - 3.5;
            g.lineTo(cx - xi, baseLado);
            g.lineTo(cx - xi, Math.min(baseLado, hlLados + 6));
            double cantoY = hl + 6 + ent * 12, cantoX = 25 - ent * 3;
            g.bezierCurveTo(cx - xi + 2, hlLados - 2, cx - cantoX - 8, cantoY + 2, cx - cantoX, cantoY);
            g.bezierCurveTo(cx - cantoX + 8, hl + ent * 3, cx - 8, hl, cx, hl + (ent > 0.6 ? 2 : 0));
            g.bezierCurveTo(cx + 8, hl, cx + cantoX - 8, hl + ent * 3, cx + cantoX, cantoY);
            g.bezierCurveTo(cx + cantoX + 8, cantoY + 2, cx + xi - 2, hlLados - 2, cx + xi, Math.min(baseLado, hlLados + 6));
            g.lineTo(cx + xi, baseLado);
            g.closePath();
        }

        /** Luz e textura do cabelo (dentro da última tampa desenhada). */
        void acabamento(double forca, boolean crespo) {
            g.save();
            g.clip();
            g.setFill(new RadialGradient(0, 0, cx - 14, cy - ry + 8, 46, false, CycleMethod.NO_CYCLE,
                    new Stop(0, alfa(t.cabeloLuz, 0.45 * Math.min(1, forca))), new Stop(1, alfa(t.cabeloLuz, 0))));
            g.fillRect(0, 0, RW, RH);
            g.setFill(new LinearGradient(0, cy - 60, 0, cy + 10, false, CycleMethod.NO_CYCLE,
                    new Stop(0, alfa(Color.BLACK, 0)), new Stop(1, alfa(Color.BLACK, 0.28))));
            g.fillRect(0, 0, RW, RH);
            Rng r = new Rng(Double.doubleToLongBits(t.faseCachos));
            if (crespo) {
                // pequenos cachos: anéis claros e escuros
                g.setLineWidth(0.8);
                for (int i = 0; i < (int) (140 * forca); i++) {
                    double x = r.entre(cx - rx - 26, cx + rx + 26), y = r.entre(cy - ry - 28, cy + 20);
                    double rr = r.entre(1.2, 2.6);
                    g.setStroke(alfa(r.chance(0.5) ? t.cabeloLuz : mistura(t.cabelo, Color.BLACK, 0.5), 0.35));
                    g.strokeOval(x - rr, y - rr, 2 * rr, 2 * rr);
                }
            } else {
                // fios
                g.setLineWidth(0.8);
                g.setLineCap(StrokeLineCap.ROUND);
                for (int i = 0; i < (int) (70 * forca); i++) {
                    double x = r.entre(cx - rx - 6, cx + rx + 6), y = r.entre(cy - ry - 18, cy - 12);
                    double ang = -Math.PI / 2 + (x - cx) / 70 + r.entre(-0.25, 0.25);
                    double c = r.entre(5, 12);
                    g.setStroke(alfa(r.chance(0.55) ? t.cabeloLuz : mistura(t.cabelo, Color.BLACK, 0.45), 0.30));
                    g.strokeLine(x, y, x + Math.cos(ang) * c, y + Math.sin(ang) * c);
                }
            }
            g.restore();
        }

        void risca() {
            double x = cx + t.ladoRisca * 18;
            g.setStroke(alfa(mistura(t.cabelo, Color.BLACK, 0.5), 0.6));
            g.setLineWidth(1.1);
            g.beginPath();
            g.moveTo(x, 84);
            g.bezierCurveTo(x - t.ladoRisca * 1, 66, x + t.ladoRisca * 3, 52, x + t.ladoRisca * 6, cy - ry - 4);
            g.stroke();
            // fios penteados para o lado oposto
            g.setStroke(alfa(t.cabeloLuz, 0.35));
            g.setLineWidth(0.9);
            for (int i = 0; i < 7; i++) {
                double y0 = 54 + i * 4.5;
                g.beginPath();
                g.moveTo(x - t.ladoRisca * 2, y0);
                g.bezierCurveTo(x - t.ladoRisca * 18, y0 - 4, x - t.ladoRisca * 34, y0 + 2, x - t.ladoRisca * 46, y0 + 12);
                g.stroke();
            }
        }

        void fiosParaTras() {
            g.setStroke(alfa(t.cabeloLuz, 0.35));
            g.setLineWidth(0.9);
            g.setLineCap(StrokeLineCap.ROUND);
            for (int i = -4; i <= 4; i++) {
                double x = cx + i * 8;
                g.beginPath();
                g.moveTo(x, 84);
                g.bezierCurveTo(x + i * 0.5, 66, x + i * 1.5, 52, x + i * 2.5, cy - ry - 10);
                g.stroke();
            }
        }

        void franjaCurta(double hl) {
            g.setFill(t.cabelo);
            Rng r = new Rng(Double.doubleToLongBits(t.faseCachos) + 7);
            for (int i = -3; i <= 3; i++) {
                double x = cx + i * 6.5 + r.entre(-1.5, 1.5);
                double c = r.entre(3, 6);
                g.beginPath();
                g.moveTo(x - 4, hl - 2);
                g.quadraticCurveTo(x, hl + c, x + 4, hl - 2);
                g.closePath();
                g.fill();
            }
        }

        void franja(double hl) {
            Rng r = new Rng(Double.doubleToLongBits(t.faseCachos) + 11);
            g.setFill(t.cabelo);
            for (int i = -4; i <= 4; i++) {
                double x = cx + i * 7 + r.entre(-1.5, 1.5);
                double c = r.entre(4, 9);
                g.beginPath();
                g.moveTo(x - 5, hl - 4);
                g.quadraticCurveTo(x + 1, hl + c, x + 5 + t.ladoRisca * 2, hl + c * 0.6);
                g.lineTo(x + 5, hl - 4);
                g.closePath();
                g.fill();
            }
            g.setStroke(alfa(t.cabeloLuz, 0.3));
            g.setLineWidth(0.8);
            for (int i = -5; i <= 5; i++) {
                double x = cx + i * 6;
                g.strokeLine(x, hl - 18, x + t.ladoRisca * 2, hl + 2);
            }
        }

        void trancas(double hl) {
            Color esc = mistura(t.cabelo, Color.BLACK, 0.35), luz = t.cabeloLuz;
            g.setLineCap(StrokeLineCap.ROUND);
            for (int i = -6; i <= 6; i++) {
                double x0 = cx + i * 6.5, y0 = cy - ry - 8 + Math.abs(i) * Math.abs(i) * 0.9;
                double x1 = x0 + i * 1.6, y1 = Math.min(hl + 4, y0 + 30);
                g.setStroke(esc);
                g.setLineWidth(5.2);
                g.strokeLine(x0, y0, x1, y1);
                g.setStroke(alfa(luz, 0.35));
                g.setLineWidth(1.2);
                for (double k = 0.15; k < 1; k += 0.22) {
                    double x = x0 + (x1 - x0) * k, y = y0 + (y1 - y0) * k;
                    g.strokeLine(x - 2, y, x + 2, y + 2.2);
                }
            }
        }

        void brilhoCareca() {
            g.save();
            contorno(0);
            g.clip();
            g.setFill(new RadialGradient(0, 0, cx - 10, cy - ry + 14, 22, false, CycleMethod.NO_CYCLE,
                    new Stop(0, alfa(Color.WHITE, 0.18)), new Stop(1, alfa(Color.WHITE, 0))));
            g.fillRect(0, 0, RW, RH);
            g.restore();
        }
    }

    // ================================================================ cores
    static Color mistura(Color a, Color b, double t) {
        return a.interpolate(b, Math.max(0, Math.min(1, t)));
    }

    static Color alfa(Color c, double a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(1, a)));
    }

    private static double distancia(Color a, Color b) {
        double dr = a.getRed() - b.getRed(), dg = a.getGreen() - b.getGreen(), db = a.getBlue() - b.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static Color cor(String s, Color padrao) {
        if (s == null || s.isBlank()) return padrao;
        try {
            return Color.web(s.trim());
        } catch (IllegalArgumentException e) {
            return padrao;
        }
    }
}
