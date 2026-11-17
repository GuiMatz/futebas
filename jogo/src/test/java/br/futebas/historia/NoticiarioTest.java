package br.futebas.historia;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.competicao.Torneio;
import br.futebas.mundo.Clube;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Noticiário: determinístico, com foco no técnico (seu clube, suas competições, sua liga) e sem spam. */
class NoticiarioTest {

    static List<String> titulos(Jogo j) {
        List<String> l = new ArrayList<>();
        for (Historia.Noticia n : j.historia().noticias) l.add(n.ano + "/" + n.semana + " " + n.tipo + " " + n.foco + " " + n.titulo + " | " + n.texto);
        return l;
    }

    @Test
    void deterministicoComFocoESemSpam() {
        Jogo a = Jogo.novoJogo(Fixture.banco(), 71, "bra-1-03", "Técnico Teste");
        Jogo b = Jogo.novoJogo(Fixture.banco(), 71, "bra-1-03", "Técnico Teste");
        for (int k = 0; k < 30; k++) {
            a.meuClube().confiancaDiretoria = 90;
            b.meuClube().confiancaDiretoria = 90;
            a.avancarSemana();
            b.avancarSemana();
        }
        List<String> ta = titulos(a);
        assertEquals(ta, titulos(b), "mesma semente, mesmas notícias");
        if (System.getProperty("noticias.imprimir") != null) ta.forEach(System.out::println);
        List<Historia.Noticia> ns = a.historia().noticias;
        assertTrue(ns.size() >= 30, "notícias suficientes: " + ns.size());
        Map<String, Integer> porSemana = new HashMap<>();
        Set<String> chaves = new HashSet<>();
        Set<String> tipos = new HashSet<>();
        int meuTime = 0;
        Clube h = a.meuClube();
        for (Historia.Noticia n : ns) {
            porSemana.merge(n.ano + "/" + n.semana, 1, Integer::sum);
            if (n.chave != null) assertTrue(chaves.add(n.chave), "repetida: " + n.chave);
            tipos.add(n.tipo);
            assertNotNull(n.titulo);
            assertFalse(n.titulo.contains("{") || n.titulo.contains("}"), "marcador sem preencher: " + n.titulo);
            assertFalse(n.titulo.contains("  "), n.titulo);
            if (n.foco == 0) meuTime++;
            // do mundo, só os grandes acontecimentos
            if (n.foco >= 3) assertTrue("mundo".equals(n.tipo) || "mercado".equals(n.tipo), "fora do foco: " + n.titulo);
        }
        for (int v : porSemana.values()) assertTrue(v <= Noticiario.POR_SEMANA, "no máximo " + Noticiario.POR_SEMANA + " por semana");
        assertTrue(meuTime >= 15, "o seu time é a maior parte: " + meuTime);
        assertTrue(tipos.contains("meu_time"));
        // a lista da tela: semana mais nova primeiro, com o foco do técnico na frente
        List<Historia.Noticia> ult = a.noticias(10);
        assertEquals(10, ult.size());
        for (int i = 1; i < ult.size(); i++) {
            Historia.Noticia x = ult.get(i - 1), y = ult.get(i);
            int sx = x.ano * 100 + x.semana, sy = y.ano * 100 + y.semana;
            assertTrue(sx > sy || (sx == sy && x.foco <= y.foco), "ordem");
        }
        assertFalse(a.noticiasDoClube(h.id, 5).isEmpty());
    }

    @Test
    void focoDoTecnico() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 72, "bra-1-03", "T");
        Noticiario n = j.simulador().noticiario;
        Clube h = j.meuClube();
        Torneio liga = n.minhaLiga();
        assertNotNull(liga);
        assertTrue(liga.participantes.contains(h.id));
        assertEquals(0, n.foco(null, h.id));
        String outro = null;
        for (String id : liga.participantes) if (!id.equals(h.id)) outro = id;
        assertEquals(1, n.foco(null, outro), "clube da sua liga");
        assertEquals(1, n.foco(liga.id), "sua competição");
        String fora = null;
        for (Clube c : j.mundo().clubes.values()) if (!c.virtual && !c.pais.equals(h.pais) && c.divisao == 1) fora = c.id;
        assertNotNull(fora);
        assertEquals(3, n.foco(null, fora), "outro país");
    }

    @Test
    void cordaBambaNaSuaLiga() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 73, "bra-1-03", "T");
        for (int k = 0; k < 6; k++) {
            j.meuClube().confiancaDiretoria = 90;
            j.avancarSemana();
        }
        Noticiario n = j.simulador().noticiario;
        Torneio liga = n.minhaLiga();
        Clube alvo = null;
        for (String id : liga.participantes) {
            Clube c = j.mundo().clube(id);
            if (!c.humano && c.treinadorId != null) alvo = c;
        }
        assertNotNull(alvo);
        alvo.confiancaDiretoria = 20;
        alvo.jogosTemp = Math.max(alvo.jogosTemp, 5);
        n.fecharSemana(j.mundo().semanaAbsoluta());
        String tecnico = j.mundo().treinador(alvo.treinadorId).nome;
        boolean achou = false;
        for (Historia.Noticia x : j.historia().noticias)
            if ("tecnicos".equals(x.tipo) && alvo.id.equals(x.clubeId) && x.titulo.contains(tecnico) && x.foco == 1) achou = true;
        assertTrue(achou, "técnico na corda bamba vira notícia");
        // não repete na semana seguinte
        int antes = j.historia().noticias.size();
        n.fecharSemana(j.mundo().semanaAbsoluta());
        for (int i = antes; i < j.historia().noticias.size(); i++) assertNotEquals(alvo.id + "tecnicos", j.historia().noticias.get(i).clubeId + j.historia().noticias.get(i).tipo);
    }

    @Test
    void textosComArtigoCerto() {
        assertTrue(Noticiario.clubeFeminino("Juventus"));
        assertTrue(Noticiario.clubeFeminino("Ponte Preta"));
        assertFalse(Noticiario.clubeFeminino("Flamengo"));
        Map<String, String> v = new HashMap<>();
        v.put("oA", "a Juventus");
        v.put("B", "Milan");
        assertEquals("Milan vence a Juventus", Noticiario.preencher("{B} vence {oA}", v));
    }
}
