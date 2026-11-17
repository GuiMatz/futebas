package br.futebas.partida;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Banco de frases curtas para a linha do tempo da partida (requisito: "várias variações, no
 * espírito das falas de narrador, mas não seja narração"): legendas de uma linha, ~60 caracteres,
 * para chute pra fora, defesa, milagre, trave, chance perdida, impedimento, o título do gol e o
 * suspense do pênalti. Escolhe pelo contexto (minuto, placar, seu time ou o adversário, qualidade do
 * lance) e não repete a mesma frase em seguida. Sem dependência de tela: a Kings usa as mesmas.
 *
 * <p>Marcadores: {@code {j}} jogador, {@code {g}} goleiro, {@code {t}} time (no título do gol, em
 * maiúsculas). Regra do jogo: as legendas de chance e os títulos de gol não usam {@code {j}}: a linha
 * já mostra "Nome · legenda" (ou o autor ao lado do gol), e o nome repetido fica estranho
 * ("Flamengo 7 · Inacreditável o que Flamengo 7 perdeu!"). Só o suspense do pênalti cita o batedor.
 */
public final class Frases {

    // ------------------------------------------------------------------ bancos
    static final String[] FORA = {
        "Pra fora!", "Passou raspando!", "Por pouco!", "Bateu cruzado… pra fora!", "Mandou na arquibancada!",
        "Isolou!", "Foi pela linha de fundo!", "Bola na rede… pelo lado de fora!", "Mirou o ângulo, achou a torcida!",
        "Errou o alvo!", "Rente à trave!", "Rasteiro, mas pra fora!", "Pegou mal na bola!", "Que isso!",
        "Saiu à direita do gol!", "Saiu à esquerda do gol!", "Tirou do goleiro… e do gol também!", "Chutou pro alto!"};
    static final String[] FORA_DE_LONGE = {
        "Arriscou de longe… pra fora!", "Bomba de fora da área… por cima!", "Tentou de fora, passou longe!",
        "Travado na zaga!", "Bloqueado! A zaga se jogou na frente!", "Chutão de longe, sem direção!"};
    static final String[] FORA_CABECA = {
        "Cabeceou pra fora!", "Subiu sozinho e mandou por cima!", "Testou firme… pra fora!", "De cabeça, raspando o travessão!"};
    static final String[] DEFESA = {
        "Pegou o goleiro!", "Defendeu {g}!", "Espalmou para escanteio!", "Fechou o gol, {g}!", "Encaixou firme!",
        "{g} no lugar certo!", "Pegou fácil, {g}!", "Defesa segura de {g}!", "Mandou pra escanteio, {g}!",
        "Saiu bem do gol, {g}!", "Abafou a jogada!", "{g} de reflexo!", "Parou no goleiro!", "Na mão de {g}!",
        "Firme, {g}! Segurou!"};
    static final String[] MILAGRE = {
        "Milagre de {g}!", "Salvou a pátria!", "Que defesa, {g}!", "Defesaça!", "Onde foi buscar essa, {g}?",
        "Defesa de cinema!", "Voou, {g}!", "Tirou com a ponta dos dedos!", "Que milagre!", "Inacreditável, {g}!",
        "Espalmou no ângulo!", "Paredão {g}!"};
    static final String[] TRAVE = {
        "Uhhh! Tirou tinta da trave!", "Na trave!", "No travessão!", "Carimbou a trave!", "Explodiu no travessão!",
        "No poste!", "A trave salvou!", "Bateu na trave e voltou!", "No pé da trave!", "Uhhh! Raspou a trave!",
        "Na madeira!", "Trave! Que azar!"};
    static final String[] PERDEU = {
        "Olha o que ele perdeu!", "Perdeu um gol feito!", "Não pode perder essa!", "Cara a cara… e perdeu!",
        "Inacreditável o que ele perdeu!", "Com o gol aberto!", "Era só empurrar!", "Como não foi gol?!"};
    static final String[] UFA = {
        "Ufa!", "Ufa! Que susto!", "Escapamos!", "Respira, torcida!", "Ufa, passou!", "Ufa! Foi por pouco!"};
    static final String[] TARDE = {
        "Haja coração!", "No finzinho, quase!", "Haja coração! Por pouco!", "A última chance? Quase!", "Que aflição!"};
    static final String[] IMPEDIMENTO = {
        "Bandeira levantada! Impedido.", "Anulado! Estava impedido.", "Era gol… mas impedido!",
        "Impedido! Não vale.", "A bandeirinha estragou a festa!"};

    static final String[] GOL = {
        "GOL DO {t}!!", "GOL DO {t}!!", "GOL DO {t}!!", "TÁ LÁ DENTRO! GOL DO {t}!", "É DO {t}!!", "PRO FUNDO DA REDE!",
        "BALANÇOU A REDE!", "É GOL! É DO {t}!", "LÁ DENTRO!", "ESTUFOU A REDE!", "GOOOL DO {t}!!", "É DELE! GOL DO {t}!",
        "BOLINHA NA REDE!!!", "OLHA O GOL!", "GOOOOOOL!!!", "ENTROU! GOL DO {t}!", "NO CANTINHO! GOL DO {t}!", "BALANÇA O BARBANTE!",
        "QUE GOL DO {t}!", "PRA DENTRO! GOL DO {t}!", "MANDOU PRO FUNDO DO GOL!", "GOL, GOL, GOL! É DO {t}!", "TÁ NA REDE!", "É CAIXA! GOL DO {t}!"};
    static final String[] GOLACO = {"QUE GOLAÇO!", "GOLAÇO DO {t}!!", "UMA PINTURA!", "QUE BOMBA!"};
    static final String[] GOL_CABECA = {"DE CABEÇA! GOL DO {t}!", "TESTOU PRO GOL!", "SUBIU MAIS ALTO! GOL DO {t}!"};
    static final String[] GOL_TARDE = {"NO APAGAR DAS LUZES!", "HAJA CORAÇÃO! GOL DO {t}!", "NO FINALZINHO! GOL DO {t}!"};
    static final String[] GOL_ACRESCIMO = {"NOS ACRÉSCIMOS! GOL DO {t}!", "NO ÚLTIMO SUSPIRO!"};
    static final String[] GOL_EMPATE = {"DEIXA TUDO IGUAL!", "EMPATOU! GOL DO {t}!", "TUDO IGUAL DE NOVO!"};
    static final String[] GOL_FRENTE_TARDE = {"PRA FRENTE! GOL DO {t}!", "O {t} PASSA NA FRENTE!"};
    static final String[] GOL_CONTRA = {"GOL CONTRA! PONTO PRO {t}!", "CONTRA! É DO {t}!", "NO PRÓPRIO GOL!"};

    // pênalti (suspense): ida, batida e o desfecho
    public static final String[] PENALTI_IDA = {"{j} vai para a bola…", "{j} ajeita a bola…", "{j} toma distância…"};
    public static final String[] PENALTI_BATIDA = {"Bateu…", "Correu… bateu…", "Partiu para a bola… bateu…"};
    public static final String[] PENALTI_GOL = {"GOL!", "No ângulo!", "Deslocou o goleiro!", "Cavadinha… GOL!", "Bateu forte, no meio… GOL!"};
    public static final String[] PENALTI_DEFESA = {"DEFENDEU {g}!", "Espalmou!", "Pegou firme!"};
    public static final String[] PENALTI_FORA = {"Pra fora!", "Bateu pra fora, à esquerda!", "Bateu pra fora, à direita!"};

    /**
     * O lance para a frase.
     *
     * @param meu true = do seu time, false = do adversário, null = jogo sem o seu time
     * @param golsPro / golsContra placar do lado do lance depois dele
     * @param jogada  "cabeca", "fora" (de longe), "falta", "rebote", "contra_ataque", "contra" ou null
     */
    public record Contexto(int minuto, int acrescimo, Boolean meu, int golsPro, int golsContra, String jogador,
                           String goleiro, String time, String jogada) {}

    private final Random rng;
    private final Map<String, String> ultimaDoTipo = new HashMap<>();
    private final Deque<String> recentes = new ArrayDeque<>();

    public Frases(long semente) {
        this.rng = new Random(semente);
    }

    /** Legenda de uma chance que não foi gol: tipo do lance "fora", "defesa", "milagre", "trave" ou "impedimento". */
    public String chance(String tipo, Contexto c) {
        List<String> pool = new ArrayList<>();
        String chave = tipo;
        switch (tipo) {
            case "defesa" -> pool.addAll(List.of(DEFESA));
            case "milagre" -> pool.addAll(List.of(MILAGRE));
            case "trave" -> pool.addAll(List.of(TRAVE));
            case "impedimento" -> pool.addAll(List.of(IMPEDIMENTO));
            default -> {
                chave = "fora";
                if ("fora".equals(c.jogada())) pool.addAll(List.of(FORA_DE_LONGE));
                else if ("cabeca".equals(c.jogada())) pool.addAll(List.of(FORA_CABECA));
                if ("rebote".equals(c.jogada()) || "contra_ataque".equals(c.jogada())) pool.addAll(List.of(PERDEU));
                pool.addAll(List.of(FORA));
            }
        }
        if (!"impedimento".equals(tipo)) {
            // o adversário perdeu: alívio; fim de jogo: drama
            if (Boolean.FALSE.equals(c.meu()) && !"defesa".equals(tipo) && !"milagre".equals(tipo) && rng.nextDouble() < 0.5)
                pool = new ArrayList<>(List.of(UFA));
            else if (c.minuto() >= 80 && Math.abs(c.golsPro() - c.golsContra()) <= 1 && rng.nextDouble() < 0.4)
                pool = new ArrayList<>(List.of(TARDE));
        }
        return preencher(escolher(chave, pool), c, false);
    }

    /** Título do gol (grande, na linha do gol): "GOL DO TIME!!" e variações pelo contexto. */
    public String tituloGol(Contexto c) {
        List<String> pool = new ArrayList<>(List.of(GOL));
        String j = c.jogada() == null ? "" : c.jogada();
        if ("contra".equals(j)) pool = new ArrayList<>(List.of(GOL_CONTRA));
        else {
            if ("fora".equals(j) || "falta".equals(j)) pool.addAll(List.of(GOLACO));
            if ("cabeca".equals(j)) pool.addAll(List.of(GOL_CABECA));
            boolean tarde = c.minuto() >= 85;
            if (c.golsPro() == c.golsContra()) pool.addAll(List.of(GOL_EMPATE));
            else if (tarde && c.golsPro() == c.golsContra() + 1) pool.addAll(List.of(GOL_FRENTE_TARDE));
            if (tarde) pool.addAll(List.of(GOL_TARDE));
            if (c.minuto() >= 90 && c.acrescimo() > 0) pool.addAll(List.of(GOL_ACRESCIMO));
        }
        return preencher(escolher("gol", pool), c, true);
    }

    // ------------------------------------------------------------------ pênalti
    public String penaltiIda(String batedor) {
        return escolher("pen-ida", List.of(PENALTI_IDA)).replace("{j}", batedor == null || batedor.isBlank() ? "O batedor" : batedor);
    }

    public String penaltiBatida() {
        return escolher("pen-batida", List.of(PENALTI_BATIDA));
    }

    /** Desfecho do pênalti: "gol", "defesa", "trave", "travessao", "isolou", "escorregou" ou "fora". */
    public String penaltiResultado(String desfecho, String goleiro) {
        String g = goleiro == null || goleiro.isBlank() ? "O GOLEIRO" : goleiro.toUpperCase(Locale.ROOT);
        return switch (desfecho) {
            case "gol" -> escolher("pen-gol", List.of(PENALTI_GOL));
            case "defesa" -> escolher("pen-defesa", List.of(PENALTI_DEFESA)).replace("{g}", g);
            case "trave" -> "Na trave!";
            case "travessao" -> "No travessão!";
            case "isolou" -> "ISOLOU!";
            case "escorregou" -> "Escorregou na hora da batida!";
            default -> escolher("pen-fora", List.of(PENALTI_FORA));
        };
    }

    // ------------------------------------------------------------------ acontecimentos (cartão, troca, lesão)
    static final String[] AMARELO = {"Cartão amarelo", "Leva o amarelo", "Amarelado pelo árbitro", "O juiz mostra o amarelo",
            "Amarelo! Agora joga pendurado", "Advertido com o amarelo", "Amarelo, e reclama muito"};
    static final String[] VERMELHO = {"Vermelho direto! Expulso", "Expulso! Vai mais cedo para o vestiário", "Cartão vermelho!",
            "Rua! Está fora do jogo", "Expulso de campo!"};
    static final String[] SEGUNDO_AMARELO = {"Segundo amarelo: expulso!", "Recebe o segundo amarelo e está fora",
            "Amarelo de novo… vermelho!"};
    static final String[] LESAO = {"Sentiu e pede atendimento", "Ficou no chão, sentindo", "Lesão: o médico entra em campo",
            "Machucado, preocupa", "Sentiu a coxa e pede ajuda"};
    static final String[] DESFALQUE = {"Sai lesionado: o time fica com um a menos", "Não dá pra seguir: um a menos em campo"};
    static final String[] SUBSTITUICAO = {"Entra no lugar de {s}", "Sai {s}, entra ele", "Na vaga de {s}", "Substitui {s}"};
    static final String[] SUB_CANSADO = {"Entra no lugar de {s}, que sai cansado", "{s} sai esgotado; ele entra"};
    static final String[] SUB_LESAO = {"Entra no lugar de {s}, lesionado", "{s} sai machucado; ele entra"};
    static final String[] PENALTI_MARCADO = {"Pênalti marcado!", "É PÊNALTI!", "O árbitro aponta a marca da cal!", "Pênalti! O árbitro não tem dúvida!"};

    /**
     * A linha de baixo de um acontecimento na narração (o nome vai em cima), variada e curta, sem repetir
     * em seguida: "cartao", "vermelho", "lesao", "desfalque", "substituicao" ({@code sai}: quem saiu),
     * "penalti". {@code texto}: o texto do motor (segundo amarelo, saiu cansado/lesionado). null = sem frase.
     */
    public String evento(String tipo, String sai, String texto) {
        String t = texto == null ? "" : texto;
        String s = sai == null || sai.isBlank() ? "outro" : sai;
        return switch (tipo) {
            case "cartao" -> escolher("ev-amarelo", List.of(AMARELO));
            case "vermelho" -> t.contains("Segundo amarelo") ? escolher("ev-2amarelo", List.of(SEGUNDO_AMARELO)) : escolher("ev-vermelho", List.of(VERMELHO));
            case "lesao" -> escolher("ev-lesao", List.of(LESAO));
            case "desfalque" -> escolher("ev-desfalque", List.of(DESFALQUE));
            case "penalti" -> escolher("ev-penalti", List.of(PENALTI_MARCADO));
            case "substituicao" -> escolher("ev-sub", List.of(t.contains("(cansado)") ? SUB_CANSADO : t.contains("(lesionado)") ? SUB_LESAO : SUBSTITUICAO))
                    .replace("{s}", s);
            default -> null;
        };
    }

    // ------------------------------------------------------------------ escolha sem repetir
    /** Sorteia do {@code pool} evitando a última do mesmo tipo e as três últimas de qualquer tipo. */
    private String escolher(String tipo, List<String> pool) {
        List<String> ok = new ArrayList<>();
        String ultima = ultimaDoTipo.get(tipo);
        for (String s : pool) if (!s.equals(ultima) && !recentes.contains(s)) ok.add(s);
        if (ok.isEmpty()) for (String s : pool) if (!s.equals(ultima)) ok.add(s);
        if (ok.isEmpty()) ok = pool;
        String f = ok.get(rng.nextInt(ok.size()));
        ultimaDoTipo.put(tipo, f);
        recentes.addLast(f);
        while (recentes.size() > 3) recentes.removeFirst();
        return f;
    }

    private static String preencher(String f, Contexto c, boolean maiusculas) {
        String j = c.jogador() == null || c.jogador().isBlank() ? "ele" : c.jogador();
        String g = c.goleiro() == null || c.goleiro().isBlank() ? "o goleiro" : c.goleiro();
        String t = c.time() == null ? "" : c.time();
        if (maiusculas) {
            j = j.toUpperCase(Locale.ROOT);
            t = t.toUpperCase(Locale.ROOT);
        }
        return f.replace("{j}", j).replace("{g}", g).replace("{t}", t);
    }
}
