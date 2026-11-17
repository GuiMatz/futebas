package br.futebas.ui.comp;

import br.futebas.mundo.Jogador;
import br.futebas.mundo.PerfilFoto;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Avatar gerado: o perfil passa pelo caminho da foto sem se perder e o mesmo jogador tem sempre o mesmo rosto. */
class AvatarTest {

    @Test
    void chaveDoPerfilIdaEVolta() {
        PerfilFoto p = new PerfilFoto(PerfilFoto.hash("db42:Fulano"), "BRA", "BA", 2004, "#c8102e", "#000000", "assets/fotos/42.png");
        PerfilFoto q = PerfilFoto.ler(p.chave());
        assertEquals(p, q);
        assertNull(PerfilFoto.ler("assets/fotos/42.png"));
        PerfilFoto vazio = new PerfilFoto(-1L, null, null, 0, null, null, null);
        assertEquals(vazio, PerfilFoto.ler(vazio.chave()));
    }

    @Test
    void mesmoJogadorMesmoRosto() {
        Jogador j = new Jogador();
        j.id = 7;
        j.nome = "João da Silva";
        j.pais = "BRA";
        j.anoNasc = 2008;
        PerfilFoto a = PerfilFoto.de(j, "#fff", "#000", null), b = PerfilFoto.de(j, "#00f", "#fff", null);
        assertEquals(a.semente(), b.semente(), "trocar de clube não muda o rosto");
        Avatar.Tracos t1 = Avatar.sortear(a), t2 = Avatar.sortear(a);
        assertEquals(t1.pele, t2.pele);
        assertEquals(t1.cabelo, t2.cabelo);
        assertEquals(t1.corte, t2.corte);
        assertEquals(t1.barba, t2.barba);
        j.nome = "Pedro Souza";
        assertNotEquals(a.semente(), PerfilFoto.de(j, null, null, null).semente());
    }

    @Test
    void rostosVariam() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            Avatar.Tracos t = Avatar.sortear(new PerfilFoto(PerfilFoto.hash("x" + i), i % 2 == 0 ? "BRA" : "NGA", null, 2000, null, null, null));
            vistos.add(t.corte + "/" + t.barba + "/" + t.pele);
        }
        assertTrue(vistos.size() > 150, "200 jogadores devem ter rostos bem diferentes: " + vistos.size());
    }
}
