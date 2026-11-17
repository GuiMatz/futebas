package br.futebas.ferramentas;

import br.futebas.dados.Esquema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Banco FICTÍCIO no contrato v2 (docs/arquitetura-v2.md), para os testes e para o LongRun
 * enquanto o {@code dados/mundo.sqlite} v2 não existe. Tudo aqui é dado de teste ("Jogador
 * Teste 00001", "Clube Teste ENG 3", fonte fictícia). Nunca é usado num jogo de verdade.
 * <p>
 * Mundo: Brasil (Séries A–D, 2 estaduais, copa), Argentina (1ª), Inglaterra (1ª e 2ª), Espanha
 * (1ª, com tabela inicial real fictícia), clubes avulsos de outros países, Libertadores,
 * Sul-Americana, Champions (fase de liga de 36), Liga Europa, Copa do Mundo 2026 (48 seleções
 * com grupos), Kings League. {@code paisesExtras} acrescenta países sintéticos (2 divisões de
 * 18 clubes cada) para medir desempenho num mundo grande.
 */
public final class BancoFicticio {
    private BancoFicticio() {}

    static final String FONTE = "[\"https://exemplo.test/fonte-ficticia\"]";

    /** Grupos da Copa de 2026 (fictícios para o teste, 12 grupos de 4). */
    static final String[][] COPA = {
        {"A", "MEX", "RSA", "KOR", "CZE"}, {"B", "CAN", "ITA", "QAT", "SUI"}, {"C", "BRA", "MAR", "HAI", "SCO"},
        {"D", "USA", "PAR", "AUS", "TUR"}, {"E", "GER", "CUW", "CIV", "ECU"}, {"F", "NED", "JPN", "UKR", "TUN"},
        {"G", "BEL", "EGY", "IRN", "NZL"}, {"H", "ESP", "CPV", "KSA", "URU"}, {"I", "FRA", "SEN", "IRQ", "NOR"},
        {"J", "ARG", "ALG", "AUT", "JOR"}, {"K", "POR", "COD", "UZB", "COL"}, {"L", "ENG", "CRO", "GHA", "PAN"}};

    static final Map<String, String> CONF = new LinkedHashMap<>();

    static {
        for (String p : new String[]{"BRA", "ARG", "URU", "COL", "PAR", "ECU", "CHI"}) CONF.put(p, "CONMEBOL");
        for (String p : new String[]{"ENG", "ESP", "GER", "FRA", "ITA", "POR", "NED", "BEL", "CRO", "SUI", "CZE", "SCO", "TUR",
                "UKR", "NOR", "AUT"}) CONF.put(p, "UEFA");
        for (String p : new String[]{"MEX", "USA", "CAN", "HAI", "CUW", "PAN"}) CONF.put(p, "CONCACAF");
        for (String p : new String[]{"RSA", "MAR", "CIV", "TUN", "EGY", "CPV", "SEN", "ALG", "COD", "GHA"}) CONF.put(p, "CAF");
        for (String p : new String[]{"KOR", "QAT", "JPN", "AUS", "IRN", "KSA", "IRQ", "JOR", "UZB"}) CONF.put(p, "AFC");
        CONF.put("NZL", "OFC");
    }

    record ClubeF(String id, String pais, String uf, double rep) {}

    public static Path criar(Path arquivo, long semente) {
        return criar(arquivo, semente, 0);
    }

    /**
     * Gera o mundo fictício para jogar sem a base real:
     * {@code mvn -f jogo/pom.xml compile exec:java@mundo-ficticio} (grava em {@code dados/mundo.sqlite}).
     */
    public static void main(String[] args) {
        Path arquivo = Path.of(args.length > 0 ? args[0] : "dados/mundo.sqlite");
        try {
            if (arquivo.toAbsolutePath().getParent() != null) Files.createDirectories(arquivo.toAbsolutePath().getParent());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        criar(arquivo, args.length > 1 ? Long.parseLong(args[1]) : 42);
        System.out.println("Mundo fictício criado em " + arquivo.toAbsolutePath());
    }

    public static Path criar(Path arquivo, long semente, int paisesExtras) {
        try {
            Files.deleteIfExists(arquivo);
            if (arquivo.getParent() != null) Files.createDirectories(arquivo.getParent());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        Random r = new Random(semente);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + arquivo.toAbsolutePath())) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                for (String ddl : Esquema.DDL.split(";")) if (!ddl.isBlank()) s.execute(ddl);
            }
            exec(c, "INSERT INTO fontes (id,url,tipo,titulo,acessado_em,confiabilidade) VALUES (1,'https://exemplo.test/fonte-ficticia','base_dados','Fonte fictícia de teste','2026-11-11',1)");
            exec(c, "INSERT INTO meta VALUES ('versao_esquema','2'),('tipo','ficticio')");

            // ---------------------------------------------------------------- países
            int rank = 1;
            List<String> rankOrdem = List.of("ARG", "ESP", "FRA", "ENG", "BRA", "POR", "NED", "BEL", "ITA", "GER", "CRO", "MAR",
                    "COL", "URU", "SUI", "JPN", "SEN", "USA", "MEX", "IRN", "KOR", "AUS", "ECU", "AUT", "TUR", "UKR", "SCO", "CZE",
                    "NOR", "EGY", "ALG", "CIV", "TUN", "PAR", "QAT", "KSA", "CAN", "PAN", "UZB", "COD", "IRQ", "RSA", "JOR", "CPV",
                    "GHA", "CUW", "HAI", "NZL", "CHI");
            try (PreparedStatement p = c.prepareStatement("INSERT INTO paises VALUES (?,?,?,?,?,?,?,?)")) {
                for (String id : rankOrdem) {
                    p.setString(1, id);
                    p.setString(2, "País " + id);
                    p.setString(3, CONF.get(id));
                    p.setString(4, "UEFA".equals(CONF.get(id)) ? "europeu" : "ano");
                    p.setString(5, "assets/bandeiras/" + id + ".png");
                    p.setInt(6, rank++);
                    p.setString(7, "#008800");
                    p.setString(8, "#FFFF00");
                    p.addBatch();
                }
                for (int x = 1; x <= paisesExtras; x++) {
                    String id = String.format("X%02d", x);
                    String conf = new String[]{"UEFA", "CAF", "AFC", "CONCACAF"}[x % 4];
                    p.setString(1, id);
                    p.setString(2, "País sintético " + x);
                    p.setString(3, conf);
                    p.setString(4, "UEFA".equals(conf) || x % 3 == 0 ? "europeu" : "ano");
                    p.setString(5, null);
                    p.setInt(6, 60 + x);
                    p.setString(7, "#0000AA");
                    p.setString(8, "#FFFFFF");
                    p.addBatch();
                }
                p.executeBatch();
            }

            // ---------------------------------------------------------------- clubes
            Map<String, List<ClubeF>> grupos = new LinkedHashMap<>();
            grupos.put("bra-1", serie("bra", "BRA", 1, 20, 82, 52, r));
            grupos.put("bra-2", serie("bra", "BRA", 2, 20, 55, 38, r));
            grupos.put("bra-3", serie("bra", "BRA", 3, 20, 40, 28, r));
            grupos.put("bra-4", serie("bra", "BRA", 4, 32, 30, 15, r));
            grupos.put("bra-x", serie("bra", "BRA", 5, 10, 15, 10, r)); // só estadual
            grupos.put("arg-1", serie("arg", "ARG", 1, 20, 78, 45, r));
            grupos.put("eng-1", serie("eng", "ENG", 1, 20, 92, 58, r));
            grupos.put("eng-2", serie("eng", "ENG", 2, 24, 60, 40, r));
            grupos.put("esp-1", serie("esp", "ESP", 1, 20, 94, 55, r));
            String[][] avulsos = {{"URU", "4", "60", "45"}, {"COL", "4", "62", "45"}, {"PAR", "2", "52", "45"}, {"CHI", "2", "50", "44"},
                    {"POR", "4", "80", "60"}, {"GER", "4", "88", "62"}, {"FRA", "4", "86", "60"}, {"NED", "4", "78", "60"},
                    {"ITA", "4", "87", "65"}, {"BEL", "6", "70", "58"}};
            for (String[] a : avulsos) grupos.put("avulso-" + a[0], serie(a[0].toLowerCase(), a[0], 1, Integer.parseInt(a[1]),
                    Double.parseDouble(a[2]), Double.parseDouble(a[3]), r));
            for (int x = 1; x <= paisesExtras; x++) {
                String id = String.format("X%02d", x);
                double topo = 70 - (x % 10) * 2.5;
                grupos.put(id.toLowerCase() + "-1", serie(id.toLowerCase(), id, 1, 18, topo, topo - 30, r));
                grupos.put(id.toLowerCase() + "-2", serie(id.toLowerCase(), id, 2, 18, topo - 28, topo - 48, r));
            }
            // UFs do Brasil: SP e RJ para os estaduais, o resto espalhado
            String[] ufs = {"SP", "RJ", "MG", "RS", "SP", "RJ", "PR", "BA", "SP", "PE"};
            List<ClubeF> todos = new ArrayList<>();
            for (List<ClubeF> l : grupos.values()) todos.addAll(l);
            List<ClubeF> comUf = new ArrayList<>();
            int k = 0;
            for (ClubeF cf : todos) comUf.add("BRA".equals(cf.pais()) ? new ClubeF(cf.id(), cf.pais(), ufs[k++ % ufs.length], cf.rep()) : cf);
            Map<String, ClubeF> porId = new LinkedHashMap<>();
            for (ClubeF cf : comUf) porId.put(cf.id(), cf);
            for (Map.Entry<String, List<ClubeF>> e : grupos.entrySet()) {
                List<ClubeF> l = new ArrayList<>();
                for (ClubeF cf : e.getValue()) l.add(porId.get(cf.id()));
                e.setValue(l);
            }
            try (PreparedStatement p = c.prepareStatement("INSERT INTO clubes (id,nome,nome_completo,sigla,cidade,uf,pais,fundacao,cor1,cor2,estadio,capacidade,reputacao,torcida,orcamento_ref,escudo_png,rival_ids) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                for (ClubeF cf : comUf) {
                    p.setString(1, cf.id());
                    p.setString(2, "Clube Teste " + cf.id().toUpperCase());
                    p.setString(3, "Clube de Teste " + cf.id());
                    p.setString(4, cf.id().substring(0, 3).toUpperCase());
                    p.setString(5, "Cidade " + cf.pais());
                    p.setString(6, cf.uf());
                    p.setString(7, cf.pais());
                    p.setString(8, "1910");
                    p.setString(9, "#AA0000");
                    p.setString(10, "#FFFFFF");
                    p.setString(11, "Estádio " + cf.id());
                    p.setInt(12, (int) (5000 + cf.rep() * 700));
                    p.setDouble(13, cf.rep());
                    p.setDouble(14, cf.rep());
                    if (r.nextBoolean()) p.setLong(15, (long) (1_000_000 * Math.exp(cf.rep() / 11.2)));
                    else p.setNull(15, Types.INTEGER);
                    p.setString(16, "assets/escudos/" + cf.id() + ".png");
                    p.setString(17, "[]");
                    p.addBatch();
                }
                p.executeBatch();
            }
            exec(c, "INSERT INTO clube_fontes SELECT id, 1 FROM clubes");

            // ---------------------------------------------------------------- nomes gerados
            List<String> paisesComNomes = new ArrayList<>(rankOrdem);
            for (int x = 1; x <= paisesExtras; x++) paisesComNomes.add(String.format("X%02d", x));
            try (PreparedStatement p = c.prepareStatement("INSERT INTO nomes_gerados VALUES (?,?,?,?)")) {
                for (String pais : paisesComNomes) {
                    for (int i = 1; i <= 12; i++) {
                        p.setString(1, pais);
                        p.setString(2, "prenome");
                        p.setString(3, "Pre" + pais + i);
                        p.setInt(4, 13 - i);
                        p.addBatch();
                        p.setString(1, pais);
                        p.setString(2, "sobrenome");
                        p.setString(3, "Sob" + pais + i);
                        p.setInt(4, 13 - i);
                        p.addBatch();
                    }
                }
                p.executeBatch();
            }

            // ---------------------------------------------------------------- jogadores
            String[] pos = {"GOL", "GOL", "ZAG", "ZAG", "ZAG", "ZAG", "LD", "LD", "LE", "LE", "VOL", "VOL", "MC", "MC", "MC", "MEI", "MEI",
                    "PE", "PE", "PD", "PD", "CA", "CA", "CA"};
            String[] estrangeiros = {"FRA", "POR", "GER", "NED", "BEL", "CRO", "MAR", "SEN", "JPN", "KOR", "USA", "MEX", "URU", "COL",
                    "NOR", "SUI", "ECU", "ALG", "EGY", "CIV"};
            String[] contratos = {"2026-06-30", "2026-12-31", "2027-06-30", "2027-12-31", "2028-06-30", "2029-06-30"};
            int id = 1;
            List<Integer> livres = new ArrayList<>();
            try (PreparedStatement pj = c.prepareStatement("INSERT INTO jogadores (id,nome,nome_conhecido,nascimento,pais,posicao,posicoes_sec,pe,altura,clube_id,emprestado_de,contrato_ate,camisa,valor_mercado,forca,potencial,atributos,caracteristicas,origem,foto_png) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                 PreparedStatement pf = c.prepareStatement("INSERT INTO jogador_fontes VALUES (?,1)")) {
                for (ClubeF cf : comUf) {
                    double alvo = 40 + 0.5 * cf.rep();
                    int tam = 22 + (cf.rep() > 70 ? 2 : 0);
                    for (int i = 0; i < tam; i++) {
                        String nac = r.nextDouble() < ("ENG".equals(cf.pais()) || "ESP".equals(cf.pais()) ? 0.35 : 0.12)
                                ? estrangeiros[r.nextInt(estrangeiros.length)] : cf.pais();
                        boolean gerado = r.nextDouble() < 0.15;
                        int idade = (int) Math.max(17, Math.min(37, Math.round(26 + r.nextGaussian() * 4.5)));
                        double f = alvo + (i % 12 < 11 ? 2 : -4) + r.nextGaussian() * 4;
                        if (idade <= 20) f -= 6;
                        jogador(pj, pf, r, id, nac, pos[i % pos.length], cf.id(), contratos[r.nextInt(contratos.length)], f, idade, gerado, i + 1);
                        id++;
                    }
                }
                for (int i = 0; i < 120; i++) { // sem clube
                    String nac = i % 3 == 0 ? "BRA" : i % 3 == 1 ? "ENG" : "ARG";
                    jogador(pj, pf, r, id, nac, pos[i % pos.length], null, null, 45 + r.nextGaussian() * 8, 24 + r.nextInt(14), false, null);
                    livres.add(id);
                    id++;
                }
                pj.executeBatch();
                pf.executeBatch();
            }
            // jogadores reais SEM fonte (devem ser descartados pelo leitor)
            for (int i = 0; i < 3; i++) {
                exec(c, "INSERT INTO jogadores (id,nome,nome_conhecido,nascimento,pais,posicao,clube_id,forca,potencial,origem) VALUES ("
                        + (9_000_000 + i) + ",'Sem Fonte " + i + "','Sem Fonte " + i + "','1995-01-01','BRA','CA','bra-1-01',70,70,'real')");
            }
            // um empréstimo: o 1º jogador do bra-2-01 pertence ao bra-1-01
            exec(c, "UPDATE jogadores SET emprestado_de='bra-1-01' WHERE id=(SELECT MIN(id) FROM jogadores WHERE clube_id='bra-2-01')");

            // ---------------------------------------------------------------- competições
            comp(c, "bra-1", "Série A (teste)", "nacional", "BRA", null, "CONMEBOL", 1);
            comp(c, "bra-2", "Série B (teste)", "nacional", "BRA", null, "CONMEBOL", 2);
            comp(c, "bra-3", "Série C (teste)", "nacional", "BRA", null, "CONMEBOL", 3);
            comp(c, "bra-4", "Série D (teste)", "nacional", "BRA", null, "CONMEBOL", 4);
            comp(c, "est-sp", "Paulista (teste)", "estadual", "BRA", "SP", "CONMEBOL", null);
            comp(c, "est-rj", "Carioca (teste)", "estadual", "BRA", "RJ", "CONMEBOL", null);
            comp(c, "est-sp-2", "Paulista A2 (teste)", "estadual", "BRA", "SP", "CONMEBOL", 2);
            comp(c, "bra-copa", "Copa do Brasil (teste)", "copa_nacional", "BRA", null, "CONMEBOL", null);
            comp(c, "arg-1", "Liga Argentina (teste)", "nacional", "ARG", null, "CONMEBOL", 1);
            comp(c, "eng-1", "Premier League (teste)", "nacional", "ENG", null, "UEFA", 1);
            comp(c, "eng-2", "Championship (teste)", "nacional", "ENG", null, "UEFA", 2);
            comp(c, "esp-1", "La Liga (teste)", "nacional", "ESP", null, "UEFA", 1);
            comp(c, "conmebol-libertadores", "Libertadores (teste)", "continental", null, null, "CONMEBOL", null);
            comp(c, "conmebol-sul-americana", "Sul-Americana (teste)", "continental", null, null, "CONMEBOL", null);
            comp(c, "uefa-champions", "Champions League (teste)", "continental", null, null, "UEFA", null);
            comp(c, "uefa-europa", "Liga Europa (teste)", "continental", null, null, "UEFA", null);
            comp(c, "fifa-copa-do-mundo", "Copa do Mundo (teste)", "selecoes", null, null, null, null);
            for (int x = 1; x <= paisesExtras; x++) {
                String pid = String.format("X%02d", x);
                comp(c, pid.toLowerCase() + "-1", "Liga " + pid + " (teste)", "nacional", pid, null, null, 1);
                comp(c, pid.toLowerCase() + "-2", "Liga 2 " + pid + " (teste)", "nacional", pid, null, null, 2);
            }

            formato(c, "bra-1", """
                {"participantes":20,"fases":[{"nome":"Pontos corridos","tipo":"pontos_corridos","turnos":2}],"rebaixados":4,
                 "vagas":[{"para":"conmebol-libertadores","posicoes":[1,2,3,4,5]},{"para":"conmebol-sul-americana","posicoes":[6,7,8,9,10,11]}]}""");
            formato(c, "bra-2", """
                {"participantes":20,"fases":[{"nome":"Pontos corridos","tipo":"pontos_corridos","turnos":2}],"promovidos":4,"rebaixados":4}""");
            formato(c, "bra-3", """
                {"participantes":20,"fases":[{"nome":"Pontos corridos","tipo":"pontos_corridos","turnos":2}],"promovidos":4,"rebaixados":4}""");
            formato(c, "bra-4", """
                {"participantes":32,"fases":[{"nome":"Grupos","tipo":"grupos","grupos":8,"turnos":2,"classificam":2},
                 {"nome":"Mata-mata","tipo":"mata_mata","ida_volta":true,"desempate":"penaltis"}],"promovidos":4,"rebaixados":8}""");
            formato(c, "est-sp", """
                {"participantes":16,"fases":[{"nome":"Primeira fase","tipo":"pontos_corridos","turnos":1,"classificam":8},
                 {"nome":"Mata-mata","tipo":"mata_mata","ida_volta":false,"desempate":"penaltis"}],"rebaixados":2,
                 "vagas":[{"para":"bra-4","posicoes":[1,2,3,4,5,6,7,8]}]}""");
            formato(c, "est-sp-2", """
                {"participantes":6,"fases":[{"nome":"Turno único","tipo":"pontos_corridos","turnos":1}],"promovidos":2}""");
            formato(c, "est-rj", """
                {"participantes":12,"fases":[{"nome":"Taça","tipo":"pontos_corridos","turnos":1,"classificam":4},
                 {"nome":"Finais","tipo":"mata_mata","ida_volta":true,"desempate":"melhor_campanha"}],"rebaixados":1,
                 "vagas":[{"para":"bra-4","posicoes":[1,2,3,4]}]}""");
            formato(c, "bra-copa", """
                {"participantes":32,"fases":[{"nome":"Mata-mata","tipo":"mata_mata","ida_volta":true,"desempate":"penaltis","chaveamento":"1x8"}],
                 "vagas":[{"para":"conmebol-libertadores","posicoes":[1]}]}""");
            formato(c, "arg-1", """
                {"participantes":20,"fases":[{"nome":"Liga","tipo":"pontos_corridos","turnos":2}],"rebaixados":2,
                 "vagas":[{"para":"conmebol-libertadores","posicoes":[1,2,3,4,5]},{"para":"conmebol-sul-americana","posicoes":[6,7,8,9,10]}]}""");
            formato(c, "eng-1", """
                {"participantes":20,"fases":[{"nome":"Liga","tipo":"pontos_corridos","turnos":2}],"rebaixados":3,
                 "vagas":[{"para":"uefa-champions","posicoes":[1,2,3,4]},{"para":"uefa-europa","posicoes":[5,6]}]}""");
            formato(c, "eng-2", """
                {"participantes":24,"fases":[{"nome":"Liga","tipo":"pontos_corridos","turnos":2}],"promovidos":3,"rebaixados":0}""");
            formato(c, "esp-1", """
                {"participantes":20,"fases":[{"nome":"Liga","tipo":"pontos_corridos","turnos":2}],"rebaixados":0,
                 "vagas":[{"para":"uefa-champions","posicoes":[1,2,3,4]},{"para":"uefa-europa","posicoes":[5,6]}]}""");
            formato(c, "conmebol-libertadores", """
                {"participantes":32,"fases":[{"nome":"Grupos","tipo":"grupos","grupos":8,"turnos":2,"classificam":2},
                 {"nome":"Mata-mata","tipo":"mata_mata","ida_volta":true,"desempate":"penaltis","final_jogo_unico":true,"chaveamento":"1x8"}]}""");
            formato(c, "conmebol-sul-americana", """
                {"participantes":16,"fases":[{"nome":"Mata-mata","tipo":"mata_mata","ida_volta":true,"desempate":"penaltis","final_jogo_unico":true}]}""");
            formato(c, "uefa-champions", """
                {"participantes":36,"fases":[{"nome":"Fase de liga","tipo":"liga_suica","jogos":8,"classificam":24},
                 {"nome":"Playoff","tipo":"mata_mata","ida_volta":true,"desempate":"penaltis","classificam":16},
                 {"nome":"Oitavas em diante","tipo":"mata_mata","ida_volta":true,"desempate":"penaltis","final_jogo_unico":true,"chaveamento":"1x8"}]}""");
            formato(c, "uefa-europa", """
                {"participantes":16,"fases":[{"nome":"Mata-mata","tipo":"mata_mata","ida_volta":true,"desempate":"penaltis","final_jogo_unico":true}]}""");
            formato(c, "fifa-copa-do-mundo", """
                {"participantes":48,"fases":[{"nome":"Fase de grupos","tipo":"grupos","grupos":12,"turnos":1,"classificam":2,"melhores_terceiros":8},
                 {"nome":"Mata-mata","tipo":"mata_mata","desempate":"prorrogacao_penaltis","chaveamento":"1x8"}]}""");
            for (int x = 1; x <= paisesExtras; x++) {
                String pid = String.format("x%02d", x);
                formato(c, pid + "-1", "{\"participantes\":18,\"fases\":[{\"nome\":\"Liga\",\"tipo\":\"pontos_corridos\",\"turnos\":2}],\"rebaixados\":3}");
                formato(c, pid + "-2", "{\"participantes\":18,\"fases\":[{\"nome\":\"Liga\",\"tipo\":\"pontos_corridos\",\"turnos\":2}],\"promovidos\":3,\"rebaixados\":0}");
            }

            // ---------------------------------------------------------------- participantes iniciais
            for (String liga : List.of("bra-1", "bra-2", "bra-3", "bra-4", "arg-1")) for (ClubeF cf : grupos.get(liga)) lc(c, liga, "2026", cf.id());
            for (String liga : List.of("eng-1", "eng-2", "esp-1")) for (ClubeF cf : grupos.get(liga)) lc(c, liga, "2025/26", cf.id());
            for (int x = 1; x <= paisesExtras; x++) {
                String pid = String.format("x%02d", x);
                for (String liga : List.of(pid + "-1", pid + "-2")) for (ClubeF cf : grupos.get(liga)) lc(c, liga, "2026", cf.id());
            }
            List<ClubeF> bra = new ArrayList<>();
            for (String g : List.of("bra-1", "bra-2", "bra-3", "bra-4", "bra-x")) bra.addAll(grupos.get(g));
            int nsp = 0, nrj = 0;
            for (ClubeF cf : bra) {
                if ("SP".equals(cf.uf()) && nsp < 16) { lc(c, "est-sp", "2026", cf.id()); nsp++; }
                else if ("SP".equals(cf.uf()) && nsp < 22) { lc(c, "est-sp-2", "2026", cf.id()); nsp++; }
                if ("RJ".equals(cf.uf()) && nrj < 12) { lc(c, "est-rj", "2026", cf.id()); nrj++; }
            }
            for (int i = 0; i < 32; i++) lc(c, "bra-copa", "2026", bra.get(i).id());
            List<String> lib = new ArrayList<>();
            for (int i = 0; i < 10; i++) lib.add(grupos.get("bra-1").get(i).id());
            for (int i = 0; i < 10; i++) lib.add(grupos.get("arg-1").get(i).id());
            for (String a : List.of("URU", "COL")) for (ClubeF cf : grupos.get("avulso-" + a)) lib.add(cf.id());
            for (String a : List.of("PAR", "CHI")) for (ClubeF cf : grupos.get("avulso-" + a)) lib.add(cf.id());
            for (String x : lib) lc(c, "conmebol-libertadores", "2026", x);
            for (int i = 10; i < 18; i++) {
                lc(c, "conmebol-sul-americana", "2026", grupos.get("bra-1").get(i).id());
                lc(c, "conmebol-sul-americana", "2026", grupos.get("arg-1").get(i).id());
            }
            List<String> ucl = new ArrayList<>();
            for (int i = 0; i < 5; i++) ucl.add(grupos.get("eng-1").get(i).id());
            for (int i = 0; i < 5; i++) ucl.add(grupos.get("esp-1").get(i).id());
            for (String a : List.of("GER", "FRA", "ITA", "POR", "NED", "BEL")) for (ClubeF cf : grupos.get("avulso-" + a)) ucl.add(cf.id());
            for (String x : ucl) lc(c, "uefa-champions", "2025/26", x);
            for (int i = 5; i < 11; i++) {
                lc(c, "uefa-europa", "2025/26", grupos.get("eng-1").get(i).id());
                lc(c, "uefa-europa", "2025/26", grupos.get("esp-1").get(i).id());
            }
            for (int i = 0; i < 4; i++) lc(c, "uefa-europa", "2025/26", grupos.get("eng-2").get(i).id());

            // tabela inicial (fictícia) da La Liga em 1/jan/2026: 17 rodadas
            try (PreparedStatement p = c.prepareStatement("INSERT INTO tabelas_iniciais VALUES ('esp-1','2025/26','Liga',NULL,?,?,?,?,?,?,?,?,'https://exemplo.test/fonte-ficticia')")) {
                List<ClubeF> esp = grupos.get("esp-1");
                for (int i = 0; i < esp.size(); i++) {
                    int v = Math.max(0, 13 - i * 13 / 19), e = 2 + (i % 3), d = 17 - v - e;
                    if (d < 0) { e += d; d = 0; }
                    p.setString(1, esp.get(i).id());
                    p.setInt(2, 17);
                    p.setInt(3, v);
                    p.setInt(4, e);
                    p.setInt(5, d);
                    p.setInt(6, 20 + v * 2);
                    p.setInt(7, 12 + d * 2);
                    p.setInt(8, v * 3 + e);
                    p.addBatch();
                }
                p.executeBatch();
            }

            // ---------------------------------------------------------------- calendários (datas reais fictícias)
            cal(c, "BRA", "bra-1", "2026", "2026-01-28", "2026-12-02");
            cal(c, "BRA", "est-sp", "2026", "2026-01-14", "2026-03-29");
            cal(c, "BRA", "est-sp-2", "2026", "2026-01-14", "2026-03-29");
            cal(c, "ENG", "eng-1", "2025/26", "2025-08-15", "2026-05-24");
            cal(c, "ENG", "eng-2", "2025/26", "2025-08-08", "2026-05-02");
            cal(c, "ESP", "esp-1", "2025/26", "2025-08-15", "2026-05-24");
            cal(c, null, "uefa-champions", "2025/26", "2025-09-16", "2026-05-30");
            cal(c, null, "conmebol-libertadores", "2026", "2026-02-04", "2026-11-28");
            cal(c, null, "fifa-copa-do-mundo", "2026", "2026-06-11", "2026-07-19");

            // ---------------------------------------------------------------- seleções
            try (PreparedStatement p = c.prepareStatement("INSERT INTO selecoes VALUES (?,?,?,?,?,?)")) {
                for (String[] g : COPA) {
                    for (int i = 1; i < g.length; i++) {
                        p.setString(1, g[i]);
                        p.setString(2, "Técnico " + g[i]);
                        p.setInt(3, rankOrdem.indexOf(g[i]) + 1);
                        p.setInt(4, 1);
                        p.setString(5, g[0]);
                        p.setString(6, "https://exemplo.test/fonte-ficticia");
                        p.addBatch();
                    }
                }
                p.setString(1, "CHI");
                p.setString(2, "Técnico CHI");
                p.setInt(3, rankOrdem.indexOf("CHI") + 1);
                p.setInt(4, 0);
                p.setString(5, null);
                p.setString(6, "https://exemplo.test/fonte-ficticia");
                p.addBatch();
                p.executeBatch();
            }

            // ---------------------------------------------------------------- regras
            exec(c, "INSERT INTO regras VALUES ('pontos_vitoria','3','1995-01-01','mundo','https://exemplo.test/fonte-ficticia')");
            exec(c, "INSERT INTO regras VALUES ('substituicoes','5','2020-06-01','mundo','https://exemplo.test/fonte-ficticia')");
            exec(c, "INSERT INTO regras VALUES ('var','1','2019-04-01','mundo','https://exemplo.test/fonte-ficticia')");

            // ---------------------------------------------------------------- Kings League
            exec(c, "INSERT INTO kings_ligas VALUES ('kings-brasil','Kings League Brasil (teste)','BRA','2025-11-01',"
                    + "'{\"jogadores_em_campo\":7,\"duracao_min\":40,\"classificados_playoff\":4,\"semanas\":10}',"
                    + "'{\"rodadas\":8,\"ordem\":\"serpente\",\"mantidos\":2,\"onze\":1,\"coringas\":1}','" + FONTE + "',NULL)");
            exec(c, "INSERT INTO kings_ligas VALUES ('kings-espanha','Kings League Espanha (teste)','ESP','2023-01-01',"
                    + "'{\"classificados_playoff\":4,\"semanas\":12}','{\"rodadas\":8}','" + FONTE + "',NULL)");
            int li = 0;
            for (String liga : List.of("kings-brasil", "kings-espanha")) {
                for (int t = 1; t <= 6; t++) {
                    String tid = (liga.equals("kings-brasil") ? "kb-" : "ke-") + t;
                    exec(c, "INSERT INTO kings_times VALUES ('" + tid + "','" + liga + "','Time Kings " + tid + "','Presidente " + t
                            + "','assets/kings/" + tid + ".png','#000000','#FFD700','https://exemplo.test/kings-ficticia')");
                    for (String temporada : liga.equals("kings-brasil") ? List.of("2026") : List.of("2025", "2026")) {
                        for (int a = 1; a <= 9; a++) {
                            if (a <= 4) {
                                int jid = livres.get(li++ % livres.size());
                                exec(c, "INSERT INTO kings_elencos VALUES ('" + tid + "','" + temporada + "'," + jid + ",'Teste " + jid
                                        + "','draft','https://exemplo.test/kings-ficticia')");
                            } else {
                                exec(c, "INSERT INTO kings_elencos VALUES ('" + tid + "','" + temporada + "',NULL,'Atleta Kings " + tid + "-" + a
                                        + "','draft','https://exemplo.test/kings-ficticia')");
                            }
                        }
                    }
                }
            }
            c.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao criar banco fictício: " + e.getMessage(), e);
        }
        return arquivo;
    }

    private static List<ClubeF> serie(String prefixo, String pais, int div, int n, double repTopo, double repBase, Random r) {
        List<ClubeF> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double rep = repTopo - (repTopo - repBase) * i / Math.max(1, n - 1) + r.nextGaussian() * 1.5;
            l.add(new ClubeF(prefixo + "-" + div + "-" + String.format("%02d", i + 1), pais, null, Math.max(5, Math.min(98, rep))));
        }
        return l;
    }

    private static void jogador(PreparedStatement pj, PreparedStatement pf, Random r, int id, String pais, String pos, String clube,
                                String contrato, double forca, int idade, boolean gerado, Integer camisa) throws SQLException {
        int f = (int) Math.max(25, Math.min(94, Math.round(forca)));
        int pot = idade <= 23 ? Math.min(97, f + 3 + r.nextInt(12)) : f;
        pj.setInt(1, id);
        if (gerado) {
            String pre = "Pre" + pais + (1 + r.nextInt(12)), sob = "Sob" + pais + (1 + r.nextInt(12));
            pj.setString(2, pre + " " + sob);
            pj.setString(3, pre);
        } else {
            pj.setString(2, "Jogador Teste " + String.format("%05d", id));
            pj.setString(3, "Teste " + id);
        }
        pj.setString(4, (2026 - idade) + "-" + String.format("%02d", 1 + r.nextInt(12)) + "-" + String.format("%02d", 1 + r.nextInt(28)));
        pj.setString(5, pais);
        pj.setString(6, pos);
        pj.setString(7, "MC".equals(pos) ? "[\"VOL\"]" : "[]");
        pj.setString(8, r.nextBoolean() ? "D" : "E");
        pj.setInt(9, 170 + r.nextInt(25));
        pj.setString(10, clube);
        pj.setString(11, null);
        pj.setString(12, contrato);
        if (camisa == null) pj.setNull(13, Types.INTEGER);
        else pj.setInt(13, camisa);
        pj.setNull(14, Types.INTEGER);
        pj.setInt(15, f);
        pj.setInt(16, pot);
        pj.setString(17, r.nextDouble() < 0.3 ? "{\"ritmo\":" + (f + 2) + ",\"finalizacao\":" + (f - 3) + ",\"passe\":" + f + ",\"drible\":" + (f + 1)
                + ",\"defesa\":" + (f - 5) + ",\"fisico\":" + f + ",\"goleiro\":" + ("GOL".equals(pos) ? f + 3 : 20) + "}" : null);
        pj.setString(18, r.nextDouble() < 0.5 ? "[\"Velocidade\",\"Finalização\"]" : "[\"Marcação\"]");
        pj.setString(19, gerado ? "gerado" : "real");
        pj.setString(20, "assets/fotos/" + id + ".png");
        pj.addBatch();
        if (!gerado) {
            pf.setInt(1, id);
            pf.addBatch();
        }
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static void comp(Connection c, String id, String nome, String ambito, String pais, String uf, String conf, Integer div) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO competicoes (id,nome,ambito,pais,uf,confederacao,divisao,calendario,logo_png) VALUES (?,?,?,?,?,?,?,?,?)")) {
            p.setString(1, id);
            p.setString(2, nome);
            p.setString(3, ambito);
            p.setString(4, pais);
            p.setString(5, uf);
            p.setString(6, conf);
            if (div == null) p.setNull(7, Types.INTEGER);
            else p.setInt(7, div);
            p.setString(8, "selecoes".equals(ambito) ? "evento" : null);
            p.setString(9, null);
            p.execute();
        }
    }

    private static void formato(Connection c, String comp, String json) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO formatos VALUES (?,?,?,?,?)")) {
            p.setString(1, comp);
            p.setInt(2, 2024);
            p.setNull(3, Types.INTEGER);
            p.setString(4, json);
            p.setString(5, FONTE);
            p.execute();
        }
    }

    private static void lc(Connection c, String comp, String temporada, String clube) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO ligas_clubes VALUES (?,?,?)")) {
            p.setString(1, comp);
            p.setString(2, temporada);
            p.setString(3, clube);
            p.execute();
        }
    }

    private static void cal(Connection c, String pais, String comp, String temporada, String de, String ate) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO calendarios VALUES (?,?,?,?,?,?)")) {
            p.setString(1, pais);
            p.setString(2, comp);
            p.setString(3, temporada);
            p.setString(4, de);
            p.setString(5, ate);
            p.setString(6, FONTE);
            p.execute();
        }
    }
}
