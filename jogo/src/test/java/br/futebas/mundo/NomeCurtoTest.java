package br.futebas.mundo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Nome de exibição: o apelido real; sem apelido, o próprio nome real encurtado (nunca inventado). */
class NomeCurtoTest {

    @Test
    void usaONomeConhecido() {
        assertEquals("Cafu", NomeCurto.de("Marcos Evangelista de Morais", "Cafu"));
        assertEquals("Rivaldo", NomeCurto.de("Rivaldo Vítor Borba Ferreira", "Rivaldo"));
        assertEquals("Hulk", NomeCurto.de("Givanildo Vieira de Sousa", "Hulk"));
    }

    @Test
    void encurtaONomeRealQuandoNaoHaApelido() {
        assertEquals("Oscar Emboaba", NomeCurto.de("Oscar dos Santos Emboaba Júnior", "Oscar dos Santos Emboaba Júnior"));
        assertEquals("Carlos Henrique", NomeCurto.de("Carlos Henrique dos Santos Souza", null));
        assertEquals("Magno Novaes", NomeCurto.de("Magno Macedo Novaes", "Magno Macedo Novaes"));
        // nomes curtos ou de duas palavras ficam como estão
        assertEquals("Martin Braithwaite", NomeCurto.de("Martin Braithwaite", "Martin Braithwaite"));
    }

    @Test
    void cadaPalavraDoNomeCurtoVemDoNomeReal() {
        String[] nomes = {"Marcelo Vieira da Silva Júnior", "Renato Dirnei Florêncio", "Alex Sandro Mendonça dos Santos", "Mariano Ferreira Filho"};
        for (String n : nomes) {
            String c = NomeCurto.encurtar(n);
            assertTrue(c.length() < n.length(), c);
            for (String w : c.split(" ")) assertTrue(n.contains(w), w + " vem de " + n);
        }
    }

    @Test
    void amadorMantemORotulo() {
        Jogador j = new Jogador();
        j.origem = Origem.COMPLEMENTAR_TESTE;
        j.nome = "Amador em teste #3 (Flamengo)";
        assertEquals(j.nome, j.nomeExibicao());
    }
}
