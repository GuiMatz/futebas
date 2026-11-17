package br.futebas.ui;

import br.futebas.historia.Historia;
import br.futebas.mundo.Mensagem;
import br.futebas.ui.modelo.Manchetes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Página inicial (requisito): poucas manchetes, as do seu time primeiro, o resto do mundo só como destaque. */
class ManchetesTest {

    private static Historia.Noticia noticia(int semana, String tipo, int foco, int peso, String titulo) {
        Historia.Noticia n = new Historia.Noticia();
        n.ano = 2026;
        n.semana = semana;
        n.tipo = tipo;
        n.foco = foco;
        n.peso = peso;
        n.titulo = titulo;
        return n;
    }

    private static List<Historia.Noticia> megatransferencias(int n) {
        List<Historia.Noticia> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(noticia(20, "mercado", 3, 90, "Clube " + i + " paga R$ 225 mi por um craque"));
        return l;
    }

    @Test
    void carreiraDaKingsPoeAKingsNaFrenteEOMundoSoComoDestaque() {
        List<Mensagem> msgs = new ArrayList<>();
        for (int i = 0; i < 3; i++) msgs.add(new Mensagem(2026, 19, "kings", "Kings: rodada " + i, "texto"));
        msgs.get(0).lida = true;
        msgs.add(new Mensagem(2026, 20, "resultado", "Resultado", "fica de fora"));
        List<Manchetes.Item> l = Manchetes.escolher(msgs, megatransferencias(12), 2026, 20, true, 3, 7);
        assertTrue(l.size() <= 7);
        for (int i = 0; i < 3; i++) assertNotNull(l.get(i).mensagem(), "as da Kings vêm primeiro: " + l.get(i));
        long mundo = l.stream().filter(Manchetes.Item::mundo).count();
        assertTrue(mundo <= Manchetes.MUNDO_MAX, "megatransferências só como destaque: " + mundo);
        assertTrue(l.stream().noneMatch(i -> i.mensagem() != null && "resultado".equals(i.mensagem().tipo)));
    }

    @Test
    void clubePrimeiroDepoisCompeticoesELimite() {
        List<Historia.Noticia> ns = new ArrayList<>(megatransferencias(5));
        ns.add(noticia(18, "meu_time", 0, 40, "Seu time vence o clássico"));
        ns.add(noticia(20, "tabela", 1, 50, "Líder tropeça"));
        for (int i = 0; i < 10; i++) ns.add(noticia(20, "tabela", 2, 30, "Notícia do país " + i));
        List<Manchetes.Item> l = Manchetes.escolher(List.of(), ns, 2026, 20, false, 3, 7);
        assertEquals(7, l.size());
        assertEquals("Seu time vence o clássico", l.get(0).noticia().titulo);
        assertEquals("Líder tropeça", l.get(1).noticia().titulo);
        assertTrue(l.stream().filter(Manchetes.Item::mundo).count() <= Manchetes.MUNDO_MAX);
    }

    @Test
    void respeitaAOpcaoDeNoticiasDoMundo() {
        List<Manchetes.Item> l = Manchetes.escolher(List.of(), megatransferencias(4), 2026, 20, false, 2, 7);
        assertTrue(l.isEmpty(), "com o foco máximo 2, nada do resto do mundo");
    }
}
