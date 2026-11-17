package br.futebas.ui;

import br.futebas.ui.modelo.AcompanhamentoPartida.Velocidade;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;

/**
 * Opções do jogo (valem para todos os saves), guardadas nas preferências do usuário
 * ({@code java.util.prefs}, nó {@code futebas}; a propriedade {@code futebas.prefs} troca o nó, e
 * os testes usam um nó próprio). Os padrões repetem o comportamento de antes das opções existirem.
 * Leituras vêm de um cache em memória (algumas são chamadas a cada célula de tabela).
 */
public final class Opcoes {
    private Opcoes() {}

    // ------------------------------------------------------------------ escolhas
    /** Notícias do mundo na central (pelo foco do noticiário: 0 clube, 1 liga, 2 país, 3 mundo). */
    public enum Noticias {
        TODAS("Todas", 3), PAIS("Meu país", 2), LIGA("Minha liga", 1);

        public final String nome;
        /** Foco máximo mostrado. */
        public final int focoMaximo;

        Noticias(String nome, int focoMaximo) {
            this.nome = nome;
            this.focoMaximo = focoMaximo;
        }
    }

    public enum Escala {
        E90("90%", 0.90), E100("100%", 1.00), E110("110%", 1.10), E125("125%", 1.25);

        public final String nome;
        public final double fator;

        Escala(String nome, double fator) {
            this.nome = nome;
            this.fator = fator;
        }
    }

    public enum Moeda {
        ABREVIADA("R$ 46,5 mi"), COMPLETA("R$ 46.500.000");

        public final String nome;

        Moeda(String nome) { this.nome = nome; }
    }

    public enum AutoSalvar {
        DESLIGADO("Desligado"), SEMANAL("Toda semana"), MENSAL("Todo mês"), TEMPORADA("Fim de temporada");

        public final String nome;

        AutoSalvar(String nome) { this.nome = nome; }
    }

    // ------------------------------------------------------------------ chaves e padrões
    static final String VELOCIDADE = "velocidade";
    /** Velocidade própria do jogo da Kings (requisito: "a kings ta muito rapido"); padrão Normal. */
    static final String VELOCIDADE_KINGS = "partida.velocidadeKings";
    static final String PAUSA_GOL_MEU = "partida.pausaGolMeu";
    static final String PAUSA_GOL_SOFRIDO = "partida.pausaGolSofrido";
    static final String PAUSA_LESAO = "partida.pausaLesao";
    static final String PAUSA_EXPULSAO = "partida.pausaExpulsao";
    static final String TATICA_INTERVALO = "partida.taticaIntervalo";
    static final String ESCOLHER_BATEDOR = "partida.escolherBatedor";
    static final String OUTROS_AO_VIVO = "partida.outrosAoVivo";
    static final String TABELA_AO_VIVO = "partida.tabelaAoVivo";
    static final String RENOVACAO_NOVOS = "jogo.renovacaoAutomatica";
    static final String CONFIRMAR_ESCALACAO = "jogo.confirmarEscalacao";
    static final String ESCALACAO_ANTES = "jogo.escalacaoAntesDoJogo";
    static final String DELEGAR_AMISTOSOS = "jogo.delegarAmistosos";
    static final String NOTICIAS = "jogo.noticias";
    static final String TIMES_CUSTOM = "jogo.timesCustomizados";
    static final String ESCALA = "interface.escala";
    static final String MOEDA = "interface.moeda";
    static final String TELA_CHEIA = "interface.telaCheia";
    static final String FOTOS = "interface.fotos";
    static final String AUTO_SALVAR = "salvar.automatico";
    static final String VAGAS_AUTO = "salvar.vagas";
    static final String CONFIRMAR_SAIR = "salvar.confirmarSair";

    static final String[] TODAS = {VELOCIDADE, PAUSA_GOL_MEU, PAUSA_GOL_SOFRIDO, PAUSA_LESAO, PAUSA_EXPULSAO, TATICA_INTERVALO,
            ESCOLHER_BATEDOR, OUTROS_AO_VIVO, TABELA_AO_VIVO, RENOVACAO_NOVOS, CONFIRMAR_ESCALACAO, ESCALACAO_ANTES, DELEGAR_AMISTOSOS, NOTICIAS, TIMES_CUSTOM,
            ESCALA, MOEDA, TELA_CHEIA, FOTOS, AUTO_SALVAR, VAGAS_AUTO, CONFIRMAR_SAIR};

    public static final int VAGAS_MIN = 1, VAGAS_MAX = 3;

    // ------------------------------------------------------------------ armazenamento
    private static final Map<String, String> cache = new ConcurrentHashMap<>();
    private static final String VAZIO = "\u0000";
    private static volatile String no = System.getProperty("futebas.prefs", "futebas");
    /** Avisados quando qualquer opção muda (a tela aplica escala, tela cheia...). */
    private static final java.util.List<Runnable> ouvintes = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Troca o nó das preferências (testes). Limpa o cache. */
    public static void usarNo(String nome) {
        no = nome;
        cache.clear();
    }

    public static void aoMudar(Runnable r) { ouvintes.add(r); }

    private static Preferences prefs() {
        return Preferences.userRoot().node(no);
    }

    private static String ler(String chave) {
        String v = cache.computeIfAbsent(chave, k -> {
            try {
                String s = prefs().get(k, null);
                return s == null ? VAZIO : s;
            } catch (RuntimeException e) {
                return VAZIO;
            }
        });
        return VAZIO.equals(v) ? null : v;
    }

    private static void gravar(String chave, String valor) {
        cache.put(chave, valor);
        try {
            prefs().put(chave, valor);
            prefs().flush();
        } catch (Exception e) {
            // sem preferências: vale só nesta sessão
        }
        for (Runnable r : ouvintes) r.run();
    }

    private static boolean bool(String chave, boolean padrao) {
        String v = ler(chave);
        return v == null ? padrao : Boolean.parseBoolean(v);
    }

    private static <E extends Enum<E>> E enumeracao(String chave, Class<E> tipo, E padrao) {
        String v = ler(chave);
        if (v == null) return padrao;
        try {
            return Enum.valueOf(tipo, v);
        } catch (RuntimeException e) {
            return padrao;
        }
    }

    private static void definir(String chave, boolean v) { gravar(chave, Boolean.toString(v)); }

    private static void enumeracao(String chave, Enum<?> v) {
        if (v != null) gravar(chave, v.name());
    }

    /** Volta tudo ao padrão. */
    public static void restaurarPadroes() {
        cache.clear();
        try {
            Preferences p = prefs();
            for (String k : TODAS) p.remove(k);
            p.flush();
        } catch (Exception e) {
            // sem preferências
        }
        for (String k : TODAS) cache.put(k, VAZIO);
        for (Runnable r : ouvintes) r.run();
    }

    // ------------------------------------------------------------------ Partida
    /** Velocidade da partida; a antiga "Instantânea" (fora da tela agora) vira Ultra rápida. */
    public static Velocidade velocidade() {
        Velocidade v = enumeracao(VELOCIDADE, Velocidade.class, Velocidade.NORMAL);
        return v == Velocidade.INSTANTANEA ? Velocidade.ULTRA : v;
    }
    public static void velocidade(Velocidade v) { enumeracao(VELOCIDADE, v); }

    /** Velocidade do jogo da Kings, separada da partida normal (padrão Normal). */
    public static Velocidade velocidadeKings() {
        Velocidade v = enumeracao(VELOCIDADE_KINGS, Velocidade.class, Velocidade.NORMAL);
        return v == Velocidade.INSTANTANEA ? Velocidade.ULTRA : v;
    }
    public static void velocidadeKings(Velocidade v) { enumeracao(VELOCIDADE_KINGS, v); }

    public static boolean pausarGolMeu() { return bool(PAUSA_GOL_MEU, false); }
    public static void pausarGolMeu(boolean v) { definir(PAUSA_GOL_MEU, v); }

    public static boolean pausarGolSofrido() { return bool(PAUSA_GOL_SOFRIDO, false); }
    public static void pausarGolSofrido(boolean v) { definir(PAUSA_GOL_SOFRIDO, v); }

    public static boolean pausarLesao() { return bool(PAUSA_LESAO, true); }
    public static void pausarLesao(boolean v) { definir(PAUSA_LESAO, v); }

    public static boolean pausarExpulsao() { return bool(PAUSA_EXPULSAO, true); }
    public static void pausarExpulsao(boolean v) { definir(PAUSA_EXPULSAO, v); }

    public static boolean taticaNoIntervalo() { return bool(TATICA_INTERVALO, true); }
    public static void taticaNoIntervalo(boolean v) { definir(TATICA_INTERVALO, v); }

    public static boolean escolherBatedor() { return bool(ESCOLHER_BATEDOR, true); }
    public static void escolherBatedor(boolean v) { definir(ESCOLHER_BATEDOR, v); }

    public static boolean outrosJogosAoVivo() { return bool(OUTROS_AO_VIVO, true); }
    public static void outrosJogosAoVivo(boolean v) { definir(OUTROS_AO_VIVO, v); }

    public static boolean tabelaAoVivo() { return bool(TABELA_AO_VIVO, true); }
    public static void tabelaAoVivo(boolean v) { definir(TABELA_AO_VIVO, v); }

    // ------------------------------------------------------------------ Jogo
    public static boolean renovacaoAutomaticaNovos() { return bool(RENOVACAO_NOVOS, false); }
    public static void renovacaoAutomaticaNovos(boolean v) { definir(RENOVACAO_NOVOS, v); }

    public static boolean confirmarEscalacao() { return bool(CONFIRMAR_ESCALACAO, false); }
    public static void confirmarEscalacao(boolean v) { definir(CONFIRMAR_ESCALACAO, v); }

    /** Antes de cada jogo seu, a tela de escalação (campo, banco e formação) com o botão JOGAR. Padrão: ligado. */
    public static boolean escalacaoAntesDoJogo() { return bool(ESCALACAO_ANTES, true); }
    public static void escalacaoAntesDoJogo(boolean v) { definir(ESCALACAO_ANTES, v); }

    public static boolean delegarAmistosos() { return bool(DELEGAR_AMISTOSOS, false); }
    public static void delegarAmistosos(boolean v) { definir(DELEGAR_AMISTOSOS, v); }

    /** Times customizados (Kings League IOS) nos jogos novos. Padrão: desligado. */
    public static boolean timesCustomizados() { return bool(TIMES_CUSTOM, false); }
    public static void timesCustomizados(boolean v) { definir(TIMES_CUSTOM, v); }

    public static Noticias noticias() { return enumeracao(NOTICIAS, Noticias.class, Noticias.TODAS); }
    public static void noticias(Noticias v) { enumeracao(NOTICIAS, v); }

    // ------------------------------------------------------------------ Interface
    public static Escala escala() { return enumeracao(ESCALA, Escala.class, Escala.E100); }
    public static void escala(Escala v) { enumeracao(ESCALA, v); }

    public static Moeda moeda() { return enumeracao(MOEDA, Moeda.class, Moeda.ABREVIADA); }
    public static void moeda(Moeda v) { enumeracao(MOEDA, v); }

    public static boolean telaCheia() { return bool(TELA_CHEIA, true); }
    public static void telaCheia(boolean v) { definir(TELA_CHEIA, v); }

    public static boolean fotos() { return bool(FOTOS, true); }
    public static void fotos(boolean v) { definir(FOTOS, v); }

    // ------------------------------------------------------------------ Salvamento
    public static AutoSalvar autoSalvar() { return enumeracao(AUTO_SALVAR, AutoSalvar.class, AutoSalvar.TEMPORADA); }
    public static void autoSalvar(AutoSalvar v) { enumeracao(AUTO_SALVAR, v); }

    public static int vagasAutomaticas() {
        String v = ler(VAGAS_AUTO);
        try {
            return v == null ? 1 : Math.max(VAGAS_MIN, Math.min(VAGAS_MAX, Integer.parseInt(v)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static void vagasAutomaticas(int n) { gravar(VAGAS_AUTO, Integer.toString(Math.max(VAGAS_MIN, Math.min(VAGAS_MAX, n)))); }

    public static boolean confirmarSair() { return bool(CONFIRMAR_SAIR, true); }
    public static void confirmarSair(boolean v) { definir(CONFIRMAR_SAIR, v); }
}
