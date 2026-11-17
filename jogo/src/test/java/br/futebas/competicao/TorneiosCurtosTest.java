package br.futebas.competicao;

import br.futebas.dados.BancoMundo;
import br.futebas.ferramentas.LongRun;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Simulador;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Balanço 2026-11, rodada 2 (pendências da base de dados): (1) ligas de Apertura/Clausura jogam os
 * dois torneios no ano (antes o motor parava no 1º campeão); (2) no 1º ano as divisões com clubes
 * reais a mais que o formato usam todos (antes ficavam só os n primeiros por id).
 */
class TorneiosCurtosTest {

    static final String APERTURA_CLAUSURA = "{\"fases\":["
            + "{\"nome\":\"Apertura - fase regular\",\"tipo\":\"especial\",\"fallback\":{\"tipo\":\"pontos_corridos\",\"turnos\":1}},"
            + "{\"nome\":\"Apertura - Semifinal\",\"tipo\":\"mata_mata\",\"ida_volta\":false,\"desempate\":\"penaltis\"},"
            + "{\"nome\":\"Apertura - Final\",\"tipo\":\"mata_mata\",\"ida_volta\":false,\"desempate\":\"penaltis\"},"
            + "{\"nome\":\"Clausura - fase regular\",\"tipo\":\"especial\",\"fallback\":{\"tipo\":\"pontos_corridos\",\"turnos\":1}},"
            + "{\"nome\":\"Clausura - Semifinal\",\"tipo\":\"mata_mata\",\"ida_volta\":false,\"desempate\":\"penaltis\"},"
            + "{\"nome\":\"Clausura - Final\",\"tipo\":\"mata_mata\",\"ida_volta\":false,\"desempate\":\"penaltis\"},"
            + "%s"
            + "{\"nome\":\"Tabela acumulada\",\"tipo\":\"especial\",\"fallback\":{\"tipo\":\"pontos_corridos\",\"turnos\":2}}]}";

    @Test
    void aperturaEClausuraNoMesmoAno() {
        Torneio t = FormatosTest.torneio(String.format(APERTURA_CLAUSURA, ""), 12);
        assertEquals(List.of(0, 3), t.inicioTorneios);
        assertEquals(List.of("Apertura", "Clausura"), t.rotulosTorneios);
        assertEquals(6, t.fases.size(), "a tabela acumulada não é jogada de novo");
        t.concluir(new FormatosTest.Falso());
        assertTrue(t.encerrado);
        // cada clube joga as duas fases regulares (11 + 11) e mais os mata-matas
        int regular = 0;
        for (Partida p : t.partidas) if (!t.fases.get(p.fase).mataMata()) regular++;
        assertEquals(2 * 66, regular, "turno único de 12 clubes em cada torneio");
        assertNotNull(t.campeoesTorneios.get(0));
        assertNotNull(t.campeoesTorneios.get(1));
        assertEquals(t.campeoesTorneios.get(1), t.campeao, "sem final entre os campeões: o do último torneio é o principal");
        assertEquals(12, t.classificacao.size(), "classificação com todos (acesso/queda pela soma)");
        assertEquals(List.of("Apertura"), t.titulosTorneiosCurtos().stream().map(x -> x[0]).toList(), "o Apertura vira título à parte");
    }

    @Test
    void finalAbsolutaEntreOsCampeoes() {
        Torneio t = FormatosTest.torneio(String.format(APERTURA_CLAUSURA,
                "{\"nome\":\"Final absoluta\",\"tipo\":\"mata_mata\",\"ida_volta\":false,\"desempate\":\"penaltis\"},"), 10);
        assertTrue(t.faseFinalCampeoes > 0);
        t.concluir(new FormatosTest.Falso());
        String a = t.campeoesTorneios.get(0), c = t.campeoesTorneios.get(1);
        assertNotNull(a);
        assertNotNull(c);
        assertTrue(t.campeao.equals(a) || t.campeao.equals(c), "o campeão nacional é um dos dois campeões");
        FaseExec fin = t.fases.get(t.faseFinalCampeoes);
        if (!a.equals(c)) assertEquals(1, fin.confrontos.size(), "final entre os dois");
        else assertTrue(fin.confrontos.isEmpty(), "o mesmo campeão nos dois: campeão direto");
        assertEquals(2, t.titulosTorneiosCurtos().size(), "com final, Apertura e Clausura viram títulos à parte");
    }

    @Test
    void ligaNormalNaoMuda() {
        Torneio t = FormatosTest.torneio("{\"fases\":[{\"nome\":\"Apertura e Clausura - fase regular\",\"tipo\":\"pontos_corridos\",\"turnos\":2}]}", 8);
        assertTrue(t.inicioTorneios.isEmpty(), "nome com os dois torneios = uma liga só");
    }

    @Test
    void bancoRealDivisoesDoPrimeiroAnoComTodosOsClubesEDoisCampeoes() {
        Path banco = LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite");
        Assumptions.assumeTrue(Files.exists(banco), "sem o banco real");
        BancoMundo db = BancoMundo.carregar(banco);
        Simulador s = Simulador.novo(db, 2031, null, null);
        for (String id : List.of("pry-1", "ecu-2", "ury-2")) {
            Torneio t = s.m.torneio(id);
            if (t == null) continue;
            Set<String> reais = new HashSet<>();
            for (String c : db.participantesIniciais(id)) {
                Clube cl = s.m.clube(c);
                if (cl != null && cl.existe) reais.add(c);
            }
            assertTrue(t.participantes.containsAll(reais), id + ": nenhum clube real fica de fora no 1º ano");
        }
        Torneio arg = s.m.torneio("arg-1");
        Assumptions.assumeTrue(arg != null && arg.inicioTorneios.size() >= 2, "formato da Argentina sem Apertura/Clausura");
        while (s.m.ano == 2026) s.avancarSemana();
        Torneio feito = null;
        for (Torneio t : s.m.torneiosAnteriores) if (t.id.equals("arg-1") && t.ano == 2026) feito = t;
        assertNotNull(feito);
        assertTrue(feito.encerrado);
        assertEquals(2, feito.campeoesTorneios.stream().filter(c -> c != null).count(), "Apertura e Clausura com campeão");
        long titulos = s.m.historia.titulos.stream().filter(x -> x.ano == 2026 && (x.competicaoId.equals("arg-1") || x.competicaoId.equals("arg-1-apertura"))).count();
        assertEquals(2, titulos, "dois títulos na história: o do Clausura (principal) e o do Apertura: "
                + s.m.historia.titulos.stream().filter(x -> x.ano == 2026 && x.competicaoId.startsWith("arg-1")).map(x -> x.competicaoId + "=" + x.competicao + "/" + x.campeao).toList());
        // 2027: o Paraguai volta ao tamanho do formato sem perder o campeão da 2ª nem clubes
        Torneio p1a = null, p2a = null, p1 = null;
        for (Torneio t : s.m.torneiosAnteriores) {
            if (t.ano != 2026) continue;
            if (t.id.equals("pry-1")) p1a = t;
            if (t.id.equals("pry-2")) p2a = t;
        }
        for (Torneio t : s.m.torneios) if (t.id.equals("pry-1") && t.ano == 2027) p1 = t;
        if (p1a != null && p2a != null && p1 != null && p1.formato != null && p1.formato.participantes != null) {
            assertEquals((int) p1.formato.participantes, p1.participantes.size(), "2027: tamanho do formato");
            assertTrue(p1.participantes.contains(p2a.campeao), "o campeão da 2ª sobe mesmo com a 1ª encolhendo");
            for (String c : p1a.participantes) assertTrue(s.m.clube(c).divisao > 0, "ninguém some da pirâmide: " + c);
        }
    }
}
