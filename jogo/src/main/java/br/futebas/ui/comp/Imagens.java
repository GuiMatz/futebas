package br.futebas.ui.comp;

import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.ui.Caminhos;
import br.futebas.ui.Sessao;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import javafx.application.Platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Imagens do jogo (escudos, bandeiras, camisas, troféus, fotos) lidas de {@code assets/}, já
 * reduzidas para o tamanho de exibição (2x, para telas de alta densidade) e guardadas num cache
 * LRU. Com 8 mil clubes, cada escudo só é decodificado quando aparece, uma vez por tamanho.
 */
public final class Imagens {
    private Imagens() {}

    private static final int LIMITE = 3500;
    private static final Map<String, Image> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> e) {
            return size() > LIMITE;
        }
    };
    /** Caminho relativo → existe? (evita ir ao disco a cada célula de tabela). */
    private static final Map<String, Path> EXISTE = new HashMap<>();
    private static final Path NENHUM = Path.of("");

    static BancoMundo banco() {
        return Sessao.bancoCarregado() ? Sessao.banco() : null;
    }

    /** Arquivo do asset (relativo à raiz do jogo), ou null se não existir. */
    public static synchronized Path arquivo(String relativo) {
        if (relativo == null || relativo.isBlank() || relativo.startsWith("http")) return null;
        Path p = EXISTE.get(relativo);
        if (p == null) {
            try {
                Path f = Caminhos.raiz().resolve(relativo);
                p = Files.isRegularFile(f) ? f : NENHUM;
            } catch (java.nio.file.InvalidPathException e) {
                p = NENHUM; // id com caractere inválido para caminho (ex.: "uf:AC")
            }
            EXISTE.put(relativo, p);
        }
        return p == NENHUM ? null : p;
    }

    /** Imagem do asset na altura pedida (largura proporcional), ou null. Lê na hora (veja {@link #pedir}). */
    public static Image imagem(String relativo, double altura) {
        return obter(relativo, 0, (int) Math.ceil(altura * 2));
    }

    /** Imagem em caixa de largura × altura (a maior dimensão manda), ou null. Lê na hora (veja {@link #pedir}). */
    public static Image imagemCaixa(String relativo, double largura, double altura) {
        return obter(relativo, (int) Math.ceil(largura * 2), (int) Math.ceil(altura * 2));
    }

    private static String chave(String relativo, int w, int h) {
        return relativo + "|" + w + "x" + h;
    }

    /** Do cache, ou decodificada agora (fora da trava: outra thread pode estar lendo ao mesmo tempo). */
    private static Image obter(String relativo, int w, int h) {
        Path f = arquivo(relativo);
        if (f == null) return null;
        String k = chave(relativo, w, h);
        Image img = doCache(k);
        if (img != null) return img;
        img = decodificar(f, w, h);
        return img == null ? null : guardar(k, img);
    }

    private static Image doCache(String k) {
        synchronized (CACHE) {
            Image img = CACHE.get(k);
            if (img != null) acertos++;
            return img;
        }
    }

    private static Image guardar(String k, Image img) {
        synchronized (CACHE) {
            Image ja = CACHE.get(k);
            if (ja != null) return ja;
            CACHE.put(k, img);
            return img;
        }
    }

    private static Image decodificar(Path f, int w, int h) {
        long t = System.nanoTime();
        Image img = new Image(f.toUri().toString(), w, h, true, true, false);
        contar(t);
        return img.isError() ? null : img;
    }

    // ------------------------------------------------------------------ leitura em segundo plano
    /**
     * Na thread da tela, imagem fora do cache é lida por duas threads de fundo e aparece quando fica
     * pronta (o quadro já tem o tamanho certo, vazio ou com a silhueta, até lá): abrir um painel com
     * 40 escudos novos não espera os 40 PNGs. Desligado ({@code -Dfutebas.imagensNaHora=true}), tudo
     * é lido na hora.
     */
    public static volatile boolean emSegundoPlano = !Boolean.getBoolean("futebas.imagensNaHora");

    /** Pedidos da tela vão para a frente da fila (o painel aberto agora primeiro); o pré-aquecimento, para o fim. */
    private static final LinkedBlockingDeque<Runnable> FILA = new LinkedBlockingDeque<>();
    /** Chave → quem espera por ela (guardado pela trava do CACHE). */
    private static final Map<String, List<Consumer<Image>>> ESPERANDO = new HashMap<>();
    /** Imagens prontas a entregar na thread da tela, num único runLater por leva. */
    private static final ConcurrentLinkedQueue<Runnable> ENTREGAS = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean ENTREGA_MARCADA = new AtomicBoolean();
    private static boolean leitoresIniciados;

    private static synchronized void iniciarLeitores() {
        if (leitoresIniciados) return;
        leitoresIniciados = true;
        for (int i = 0; i < 2; i++) {
            Thread t = new Thread(() -> {
                while (true) {
                    Runnable r;
                    try {
                        r = FILA.takeFirst();
                    } catch (InterruptedException e) {
                        return;
                    }
                    try {
                        r.run();
                    } catch (RuntimeException e) {
                        // imagem ruim: segue com as outras
                    }
                }
            }, "futebas-imagens-" + i);
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.start();
        }
    }

    /**
     * Entrega a imagem (ou null, se não existir ou não abrir) a {@code pronta}. Se já está no cache,
     * ou fora da thread da tela, ou com {@link #emSegundoPlano} desligado: na hora, e retorna true.
     * Senão lê em segundo plano e chama {@code pronta} depois, na thread da tela; retorna false.
     */
    public static boolean pedir(String relativo, double largura, double altura, Consumer<Image> pronta) {
        int w = largura <= 0 ? 0 : (int) Math.ceil(largura * 2), h = (int) Math.ceil(altura * 2);
        Path f = arquivo(relativo);
        if (f == null) {
            pronta.accept(null);
            return true;
        }
        String k = chave(relativo, w, h);
        Image img = doCache(k);
        if (img != null || !emSegundoPlano || !Platform.isFxApplicationThread()) {
            pronta.accept(img != null ? img : obter(relativo, w, h));
            return true;
        }
        // requisito: a foto aparece junto com a tela (sem esperar nem fade). Lê na hora enquanto
        // couber no orçamento de tempo da tela; só o excedente (listas enormes) vai para o segundo plano.
        long agora = System.nanoTime();
        if (agora - janelaSync > 150_000_000L) {
            janelaSync = agora;
            gastoSync = 0;
        }
        if (gastoSync < 60_000_000L) {
            Image lida = obter(relativo, w, h);
            gastoSync += System.nanoTime() - agora;
            pronta.accept(lida);
            return true;
        }
        if (LOG_FALTAS) System.err.println("IMAGEM-FALTA " + k);
        enfileirar(f, k, w, h, pronta, true);
        return false;
    }

    /** Diagnóstico: {@code -Dfutebas.imagens.log=true} lista cada imagem que a tela pediu e não estava no cache. */
    /** Orçamento de leitura na hora (thread da tela): até 60 ms a cada janela de 150 ms. */
    private static long janelaSync, gastoSync;

    private static final boolean LOG_FALTAS = Boolean.getBoolean("futebas.imagens.log");

    private static void enfileirar(Path f, String k, int w, int h, Consumer<Image> pronta, boolean frente) {
        synchronized (CACHE) {
            List<Consumer<Image>> l = ESPERANDO.get(k);
            if (l != null) {
                if (pronta != null) l.add(pronta);
                return;
            }
            l = new ArrayList<>(2);
            if (pronta != null) l.add(pronta);
            ESPERANDO.put(k, l);
            if (pronta != null) assincronas++;
            else preaquecidas++;
        }
        iniciarLeitores();
        Runnable ler = () -> {
            Image img = doCache(k);
            if (img == null) {
                try {
                    img = decodificar(f, w, h);
                } catch (RuntimeException e) {
                    img = null;
                }
                if (img != null) img = guardar(k, img);
            }
            List<Consumer<Image>> quem;
            synchronized (CACHE) {
                quem = ESPERANDO.remove(k);
            }
            if (quem == null || quem.isEmpty()) return;
            Image fim = img;
            ENTREGAS.add(() -> {
                for (Consumer<Image> c : quem) c.accept(fim);
            });
            if (ENTREGA_MARCADA.compareAndSet(false, true)) Platform.runLater(Imagens::entregar);
        };
        if (frente) FILA.addFirst(ler);
        else FILA.addLast(ler);
    }

    private static void entregar() {
        ENTREGA_MARCADA.set(false);
        Runnable r;
        while ((r = ENTREGAS.poll()) != null) {
            try {
                r.run();
            } catch (RuntimeException e) {
                // um nó que já saiu da tela não impede os outros
            }
        }
    }

    /** Há imagens pedidas ainda não entregues? (testes e capturas esperam a fila esvaziar.) */
    public static boolean ocupado() {
        synchronized (CACHE) {
            return !ESPERANDO.isEmpty() || !ENTREGAS.isEmpty();
        }
    }

    /** Um pedido de pré-aquecimento: caminho relativo e a caixa em pixels de tela (largura 0 = proporcional). */
    public record Pedido(String relativo, double largura, double altura) {}

    /**
     * Pré-aquecimento (tela de carregamento): lê em segundo plano, no fim da fila, as imagens que a
     * central vai pedir logo de cara; quando a tela pedir, já estão no cache.
     */
    public static void preaquecer(Collection<Pedido> pedidos) {
        for (Pedido p : pedidos) {
            if (p == null || p.relativo() == null) continue;
            int w = p.largura() <= 0 ? 0 : (int) Math.ceil(p.largura() * 2), h = (int) Math.ceil(p.altura() * 2);
            Path f = arquivo(p.relativo());
            if (f == null) continue;
            String k = chave(p.relativo(), w, h);
            synchronized (CACHE) {
                if (CACHE.containsKey(k) || ESPERANDO.containsKey(k)) continue;
            }
            enfileirar(f, k, w, h, null, false);
        }
    }

    /** Pedido do escudo de um participante na caixa {@code altura} × {@code altura} (seleções: a bandeira redonda). */
    public static Pedido pedidoEscudo(String id, double altura) {
        if (id == null) return null;
        if (id.startsWith("sel-")) return new Pedido(caminhoBandeira(id.substring(4)), altura * 0.82 * 1.6, altura * 0.82);
        String kwc = paisKingsNations(id);
        if (kwc != null) return new Pedido(caminhoBandeira(kwc), altura * 0.82 * 1.6, altura * 0.82);
        return new Pedido(caminhoEscudo(id), altura, altura);
    }

    /** Pedido do troféu de uma competição (a mesma caixa de {@link #trofeu}). */
    public static Pedido pedidoTrofeu(String competicaoId, String ambito, String pais, double altura) {
        return new Pedido(caminhoTrofeu(competicaoId, ambito, pais), altura * 0.8, altura);
    }

    /** Caminho do escudo de um clube ou time da Kings (o mesmo que {@link #escudo} usa). */
    public static String caminhoEscudo(String id) {
        BancoMundo db = banco();
        if (db != null) {
            Db.Clube c = db.clubes.get(id);
            if (c != null) return c.escudoPng() != null ? c.escudoPng() : "assets/escudos/" + id + ".png";
            Db.KingsTime k = db.kingsTimes.get(id);
            if (k != null && k.escudoPng() != null) return k.escudoPng();
        }
        if (id.startsWith(CUSTOM_PREFIXO)) return CUSTOM_ESCUDOS + id + ".png";
        return "assets/escudos/" + id + ".png";
    }

    /** ImageView do tamanho da caixa que recebe a imagem quando ela fica pronta; {@code falhou} roda se não abrir. */
    static ImageView vistaPedida(String relativo, double largura, double altura, Runnable falhou) {
        ImageView v = new ImageView();
        v.setFitWidth(largura);
        v.setFitHeight(altura);
        v.setPreserveRatio(true);
        v.setSmooth(true);
        pedir(relativo, largura, altura, img -> {
            if (img != null) v.setImage(img);
            else if (falhou != null) falhou.run();
        });
        return v;
    }

    // ------------------------------------------------------------------ estatísticas (TestesDesempenho)
    private static long acertos, decodificadas, decodificadasNaTela, nanosNaTela, assincronas, preaquecidas;

    private static void contar(long t0) {
        boolean tela = Platform.isFxApplicationThread();
        long d = System.nanoTime() - t0;
        synchronized (CACHE) {
            decodificadas++;
            if (tela) {
                decodificadasNaTela++;
                nanosNaTela += d;
            }
        }
    }

    /** Resumo do cache: acertos, decodificações (e quantas/quanto tempo na thread da tela). */
    public static String estatisticas() {
        synchronized (CACHE) {
            return String.format(Locale.ROOT, "%d no cache, %d acertos, %d decodificadas (%d na thread da tela: %.0f ms; %d pedidas em segundo plano, %d pré-aquecidas)",
                    CACHE.size(), acertos, decodificadas, decodificadasNaTela, nanosNaTela / 1e6, assincronas, preaquecidas);
        }
    }

    public static ImageView vista(Image img, double altura) {
        ImageView v = new ImageView(img);
        v.setFitHeight(altura);
        v.setPreserveRatio(true);
        v.setSmooth(true);
        return v;
    }

    private static Node caixa(Node n, double largura, double altura) {
        StackPane p = n == null ? new StackPane() : new StackPane(n);
        p.setMinSize(largura, altura);
        p.setPrefSize(largura, altura);
        p.setMaxSize(largura, altura);
        return p;
    }

    private static final java.util.Map<String, String> KWC_NATIONS = java.util.Map.ofEntries(
            java.util.Map.entry("algeria", "DZA"), java.util.Map.entry("argentina", "ARG"), java.util.Map.entry("brazil", "BRA"),
            java.util.Map.entry("chile", "CHL"), java.util.Map.entry("colombia", "COL"), java.util.Map.entry("france", "FRA"),
            java.util.Map.entry("germany", "DEU"), java.util.Map.entry("india", "IND"), java.util.Map.entry("indonesia", "IDN"),
            java.util.Map.entry("italy", "ITA"), java.util.Map.entry("japan", "JPN"), java.util.Map.entry("mexico", "MEX"),
            java.util.Map.entry("morocco", "MAR"), java.util.Map.entry("netherlands", "NLD"), java.util.Map.entry("peru", "PER"),
            java.util.Map.entry("poland", "POL"), java.util.Map.entry("qatar", "QAT"), java.util.Map.entry("saudi-arabia", "SAU"),
            java.util.Map.entry("spain", "ESP"), java.util.Map.entry("usa", "USA"));

    /** País (ISO-3) de um time da Kings World Cup Nations, ou null. */
    public static String paisKingsNations(String id) {
        if (id == null) return null;
        // depois de 2026 as seleções são montadas pelo jogo: "kwcn-<iso3>"
        if (id.startsWith("kwcn-") && id.length() == 8) return id.substring(5).toUpperCase(java.util.Locale.ROOT);
        if (!id.startsWith("kings-world-cup-nations-")) return null;
        return KWC_NATIONS.get(id.substring("kings-world-cup-nations-".length()));
    }

    // ------------------------------------------------------------------ escudos
    /** Escudo de qualquer participante: clube, seleção ("sel-XXX") ou time da Kings League. */
    public static Node escudo(String id, double altura) {
        if (id == null) return caixa(Escudo.gerado("?", "?", null, null, altura), altura, altura);
        if (id.startsWith("sel-")) return caixa(bandeiraRedonda(id.substring(4), altura * 0.82), altura, altura);
        // seleções da Kings World Cup Nations também usam a bandeira (regra do jogo)
        String paisKwc = paisKingsNations(id);
        if (paisKwc != null) return caixa(bandeiraRedonda(paisKwc, altura * 0.82), altura, altura);
        BancoMundo db = banco();
        String rel = null, nome = id, c1 = null, c2 = null;
        if (db != null) {
            Db.Clube c = db.clubes.get(id);
            if (c != null) {
                rel = c.escudoPng() != null ? c.escudoPng() : "assets/escudos/" + id + ".png";
                nome = c.nome();
                c1 = c.cor1();
                c2 = c.cor2();
            } else {
                Db.KingsTime k = db.kingsTimes.get(id);
                if (k != null) {
                    rel = k.escudoPng();
                    nome = k.nome();
                    c1 = k.cor1();
                    c2 = k.cor2();
                }
            }
        }
        if (rel == null && id.startsWith(CUSTOM_PREFIXO)) rel = CUSTOM_ESCUDOS + id + ".png"; // times customizados (Kings League IOS)
        if (rel == null) rel = "assets/escudos/" + id + ".png";
        if (arquivo(rel) == null) return caixa(Escudo.gerado(id, nome, c1, c2, altura), altura, altura);
        StackPane p = (StackPane) caixa(null, altura, altura);
        String nomeF = nome, c1F = c1, c2F = c2;
        ImageView v = vistaPedida(rel, altura, altura, () -> p.getChildren().setAll(Escudo.gerado(id, nomeF, c1F, c2F, altura)));
        if (p.getChildren().isEmpty()) p.getChildren().add(v);
        return p;
    }

    /** Escudo gerado com nome e cores conhecidos (quando não há id no banco). */
    public static Node escudo(String id, String nome, String cor1, String cor2, double altura) {
        BancoMundo db = banco();
        if (db != null && id != null && (db.clubes.containsKey(id) || id.startsWith("sel-"))) return escudo(id, altura);
        return caixa(Escudo.gerado(id, nome, cor1, cor2, altura), altura, altura);
    }

    // ------------------------------------------------------------------ bandeiras
    public static String caminhoBandeira(String pais) {
        BancoMundo db = banco();
        Db.Pais p = db == null ? null : db.pais(pais);
        return p != null && p.bandeiraPng() != null ? p.bandeiraPng() : "assets/bandeiras/" + pais + ".png";
    }

    /** Bandeira retangular (3:2) com cantos levemente arredondados. */
    public static Node bandeira(String pais, double altura) {
        double w = Math.round(altura * 1.5);
        String rel = pais == null ? null : caminhoBandeira(pais);
        if (rel == null || arquivo(rel) == null) {
            javafx.scene.layout.Region r = new javafx.scene.layout.Region();
            r.setMinSize(w, altura);
            r.setPrefSize(w, altura);
            r.setMaxSize(w, altura);
            r.setStyle("-fx-background-color: #3a4049; -fx-background-radius: 2;");
            return r;
        }
        ImageView v = vistaPedida(rel, w, altura, null);
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(w, altura);
        clip.setArcWidth(4);
        clip.setArcHeight(4);
        v.setViewport(null);
        StackPane p = new StackPane(v);
        p.setMinSize(w, altura);
        p.setMaxSize(w, altura);
        p.setClip(clip);
        p.setStyle("-fx-border-color: rgba(0,0,0,0.12); -fx-border-width: 0.5;");
        return p;
    }

    /** Bandeira recortada em círculo (para "escudos" de seleções). */
    public static Node bandeiraRedonda(String pais, double diametro) {
        String rel = caminhoBandeira(pais);
        if (arquivo(rel) == null) return Escudo.gerado("sel-" + pais, pais, null, null, diametro);
        ImageView v = new ImageView();
        v.setFitHeight(diametro);
        v.setPreserveRatio(true);
        v.setSmooth(true);
        javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(diametro / 2, diametro / 2, diametro / 2);
        v.setClip(clip);
        StackPane p = new StackPane(v);
        pedir(rel, diametro * 1.6, diametro, img -> {
            if (img == null) {
                p.getChildren().setAll(Escudo.gerado("sel-" + pais, pais, null, null, diametro));
                return;
            }
            clip.setCenterX(diametro * img.getWidth() / Math.max(1, img.getHeight()) / 2);
            v.setImage(img);
        });
        p.setMinSize(diametro, diametro);
        p.setMaxSize(diametro, diametro);
        javafx.scene.shape.Circle aro = new javafx.scene.shape.Circle(diametro / 2);
        aro.setFill(null);
        aro.setStroke(javafx.scene.paint.Color.rgb(0, 0, 0, 0.15));
        p.getChildren().add(aro);
        return p;
    }

    // ------------------------------------------------------------------ camisas
    public static Node camisa(String clubeId, double altura) {
        BancoMundo db = banco();
        String c1 = null, c2 = null;
        if (db != null && clubeId != null) {
            Db.Clube c = db.clubes.get(clubeId);
            if (c != null) {
                c1 = c.cor1();
                c2 = c.cor2();
            }
        }
        String rel = clubeId == null ? null : "assets/brasfoot-mod/camisas/" + clubeId + ".png";
        if (rel != null && arquivo(rel) != null) return caixa(vistaPedida(rel, altura, altura, null), altura, altura);
        return caixa(Escudo.camisaGerada(c1, c2, altura), altura, altura);
    }

    // ------------------------------------------------------------------ troféus
    /** Caminho do troféu de uma competição (logo do banco ou o genérico do tipo). */
    public static String caminhoTrofeu(String competicaoId, String ambito, String pais) {
        return caminhoTrofeu(competicaoId, ambito, pais, true);
    }

    /**
     * Como {@link #caminhoTrofeu(String, String, String)}; com {@code logos} false pula as logos de
     * {@code assets/competicoes} e fica com a arte do troféu (tela de campeão, quando a logo é só letreiro).
     */
    public static String caminhoTrofeu(String competicaoId, String ambito, String pais, boolean logos) {
        String k = caminhoKings(competicaoId);
        if (k != null) return k;
        if (logos && competicaoId != null && competicaoId.indexOf(':') < 0 && arquivo("assets/competicoes/" + competicaoId + ".png") != null)
            return "assets/competicoes/" + competicaoId + ".png";
        BancoMundo db = banco();
        if (db != null && competicaoId != null) {
            Db.Competicao c = db.competicoes.get(competicaoId);
            if (c != null && c.logoPng() != null && arquivo(c.logoPng()) != null) return c.logoPng();
            if (c != null) {
                if (ambito == null) ambito = c.ambito();
                if (pais == null) pais = c.pais();
            }
        }
        String id = competicaoId == null ? "" : competicaoId.toLowerCase(Locale.ROOT);
        String b = "assets/brasfoot-mod/trofeus/";
        // jogos únicos fictícios entre campeões de confederações (Desafio; a Taça Atlântica tem arte própria)
        if (id.startsWith("desafio") || id.contains("taca-atlantica")) return b + "tr_supercopa_generico.png";
        if (id.contains("copa-do-mundo")) return b + "tr_copamundo.png";
        if (id.contains("eurocopa")) return b + "tr_eurocopa.png";
        if (id.contains("copa-america")) return b + "tr_copaamerica.png";
        if (id.contains("nations")) return b + "tr_liganacoes.png";
        if (id.contains("mundial") || id.contains("intercontinental")) return b + "tr_mundial.png";
        if (id.contains("nordeste")) return b + "tr_copanordeste.png";
        if (id.contains("copa-verde")) return b + "tr_copaverde.png";
        if (id.contains("recopa")) return b + "tr_recopasulamaericana.png";
        if (id.contains("uefa-supercopa")) return b + "tr_recopaeuropa.png";
        if (id.contains("concacaf")) return b + "tr_ligaconcacaf.png";
        if (id.contains("afc")) return b + "tr_ligaafc.png";
        if (id.contains("caf")) return b + "tr_ligacaf.png";
        if (id.contains("ofc")) return b + "tr_ligaofc.png";
        if (id.contains("asia")) return b + "tr_copaasia.png";
        if (id.contains("africa")) return b + "tr_copaafrica.png";
        if (id.contains("ouro") || id.contains("gold")) return b + "tr_copaouro.png";
        if (id.contains("supercopa")) return b + "tr_supercopa_generico.png";
        if ("estadual".equals(ambito)) return b + "tr_estadualgenerico.png";
        if ("nacional".equals(ambito)) return b + "tr_nacionalgenerico.png";
        return b + "tr_copa.png";
    }

    /** Times customizados (Opções → Times customizados): escudos, logos e troféus fora de repositório público. */
    public static final String CUSTOM_PREFIXO = "kings-ios-", CUSTOM_LIGA = "kings-ios",
            CUSTOM_ESCUDOS = "assets/custom/ios/escudos/", CUSTOM_ARTE = "assets/custom/ios/liga/";

    /** Troféu/logo oficial da Kings League para as competições da Kings (null se não for da Kings). */
    private static String caminhoKings(String id) {
        if (id == null || !id.startsWith("kings")) return null;
        String[] tenta;
        if (id.equals(CUSTOM_LIGA) || id.startsWith(CUSTOM_PREFIXO)) {
            tenta = id.endsWith("-cup")
                    ? new String[]{CUSTOM_ARTE + "trofeu-kings-ios-cup.png", CUSTOM_ARTE + "kings-ios-cup.png"}
                    : new String[]{CUSTOM_ARTE + "trofeu-kings-ios.png", CUSTOM_ARTE + "kings-ios.png"};
        } else if (id.equals("kings")) tenta = new String[]{"assets/kings/kings-league.png"};
        else if (id.startsWith("kings-mundial-clubes") || id.startsWith("kings-world-cup-clubs"))
            tenta = new String[]{"assets/kings/trofeus/kings-world-cup-clubs.png", "assets/kings/ligas/kings-world-cup-clubs.png"};
        else if (id.startsWith("kings-mundial-selecoes") || id.startsWith("kings-world-cup-nations"))
            tenta = new String[]{"assets/kings/trofeus/kings-world-cup-nations.png", "assets/kings/ligas/kings-world-cup-nations.png"};
        else if (id.endsWith("-cup")) {
            String liga = id.substring(0, id.length() - 4);
            tenta = new String[]{"assets/kings/trofeus/kings-cup.png", "assets/kings/ligas/" + liga + ".png"};
        } else {
            String liga = id.replaceAll("-split\\d+$", "");
            tenta = new String[]{"assets/kings/trofeus/" + liga + ".png", "assets/kings/ligas/" + liga + ".png"};
        }
        for (String t : tenta) if (arquivo(t) != null) return t;
        return arquivo("assets/kings/kings-league.png") != null ? "assets/kings/kings-league.png" : null;
    }

    // ------------------------------------------------------------------ fotos da Kings League
    /** Índice das fotos oficiais dos atletas da Kings (assets/kings/fotos/indice.json, de coleta_kings_fotos.py). */
    private static Map<String, String> kingsPorTime, kingsPorNome;
    private static final Map<String, String> KINGS_PARECIDO = new HashMap<>();

    /** Nome normalizado como no índice: sem acento, aspas e pontuação, minúsculo. */
    static String chaveNomeKings(String s) {
        String n = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-zA-Z0-9 ]+", " ").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** Foto oficial de um atleta da Kings: pelo time + nome da fonte, senão só pelo nome; null se não houver. */
    public static synchronized String caminhoFotoKings(String timeId, String nome) {
        if (nome == null) return null;
        if (kingsPorTime == null) {
            kingsPorTime = new HashMap<>();
            kingsPorNome = new HashMap<>();
            Path f = arquivo("assets/kings/fotos/indice.json");
            if (f != null) {
                try {
                    com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                    for (String k : new String[]{"por_time", "por_nome"}) {
                        if (!o.has(k)) continue;
                        Map<String, String> dest = k.equals("por_time") ? kingsPorTime : kingsPorNome;
                        for (var e : o.getAsJsonObject(k).entrySet()) dest.put(e.getKey(), e.getValue().getAsString());
                    }
                } catch (Exception e) {
                    // índice ausente ou corrompido: fica sem fotos da Kings (silhueta)
                }
            }
        }
        String r = timeId == null ? null : kingsPorTime.get(timeId + "|" + nome);
        if (r == null) r = kingsPorNome.get(chaveNomeKings(nome));
        if (r == null && timeId != null) {
            r = KINGS_PARECIDO.computeIfAbsent(timeId + "|" + nome, k -> {
                String x = parecidoNoTime(timeId, nome);
                return x == null ? "" : x;
            });
            if (r.isEmpty()) r = null;
        }
        return r != null && arquivo(r) != null ? r : null;
    }

    /**
     * Mesmo time, nome escrito de outro jeito (nome de exibição do jogo x nome da fonte: "Tchouameni" x
     * "Tchouaméni", nome curto x completo): as palavras de um contidas no outro, e só um candidato no time.
     */
    private static String parecidoNoTime(String timeId, String nome) {
        java.util.Set<String> a = new java.util.HashSet<>(java.util.Arrays.asList(chaveNomeKings(nome).split(" ")));
        a.remove("");
        if (a.size() < 2) return null;
        String achado = null;
        String pre = timeId + "|";
        for (Map.Entry<String, String> e : kingsPorTime.entrySet()) {
            if (!e.getKey().startsWith(pre)) continue;
            java.util.Set<String> b = new java.util.HashSet<>(java.util.Arrays.asList(chaveNomeKings(e.getKey().substring(pre.length())).split(" ")));
            b.remove("");
            if (b.size() < 2 || !(a.containsAll(b) || b.containsAll(a))) continue;
            if (achado != null && !achado.equals(e.getValue())) return null; // ambíguo
            achado = e.getValue();
        }
        return achado;
    }

    /** Logo de uma Kings League (ou da marca Kings League). */
    public static Node logoKings(String ligaId, double altura) {
        String rel = ligaId != null && arquivo("assets/kings/ligas/" + ligaId + ".png") != null
                ? "assets/kings/ligas/" + ligaId + ".png" : "assets/kings/kings-league.png";
        if (ligaId != null && (ligaId.equals(CUSTOM_LIGA) || ligaId.startsWith(CUSTOM_PREFIXO))) {
            String c = CUSTOM_ARTE + (ligaId.endsWith("-cup") ? "kings-ios-cup.png" : "kings-ios.png");
            if (arquivo(c) != null) rel = c;
        }
        if (arquivo(rel) == null) return trofeu("kings", "kings", null, altura);
        return caixa(vistaPedida(rel, altura, altura, null), altura, altura);
    }

    public static Node trofeu(String competicaoId, String ambito, String pais, double altura) {
        String rel = caminhoTrofeu(competicaoId, ambito, pais);
        if (arquivo(rel) == null) {
            javafx.scene.layout.Region r = new javafx.scene.layout.Region();
            r.setPrefSize(altura * 0.6, altura);
            return r;
        }
        return caixa(vistaPedida(rel, altura * 0.8, altura, null), altura * 0.8, altura);
    }
}
