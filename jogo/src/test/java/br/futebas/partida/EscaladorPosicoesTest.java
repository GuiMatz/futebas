package br.futebas.partida;

import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Posicao;
import br.futebas.mundo.StatusJogador;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** A escalação automática respeita as posições: lateral na lateral, zagueiro na zaga. */
class EscaladorPosicoesTest {

    private static Jogador add(Mundo m, Clube c, String nome, Posicao p, double forca) {
        Jogador j = m.novoJogador();
        j.nome = nome;
        j.nomeConhecido = nome;
        j.posicao = p;
        j.status = StatusJogador.PROFISSIONAL;
        j.clubeId = c.id;
        j.anoNasc = 1972;
        j.moral = 65;
        j.forca = forca;
        c.elenco.add(j.id);
        return j;
    }

    private static Mundo mundoCom(Clube c) {
        Mundo m = new Mundo();
        m.ano = 2002;
        m.clubes.put(c.id, c);
        return m;
    }

    private static Clube clube(String id) {
        Clube c = new Clube();
        c.id = id;
        c.nome = id;
        c.tatica.formacao = "4-4-2";
        return c;
    }

    @Test
    void lateralCraqueJogaNaLateralENaoNaZaga() {
        Clube c = clube("teste");
        Mundo m = mundoCom(c);
        add(m, c, "Goleiro", Posicao.GOL, 70);
        Jogador cafu = add(m, c, "Cafu", Posicao.LD, 88);
        add(m, c, "Lateral reserva", Posicao.LD, 58);
        add(m, c, "Zagueiro 1", Posicao.ZAG, 70);
        add(m, c, "Zagueiro 2", Posicao.ZAG, 68);
        add(m, c, "Lateral esquerdo", Posicao.LE, 66);
        add(m, c, "Volante", Posicao.VOL, 70);
        add(m, c, "Meio", Posicao.MC, 70);
        Jogador rivaldo = add(m, c, "Rivaldo", Posicao.PE, 90);
        add(m, c, "Ponta direita", Posicao.PD, 72);
        add(m, c, "Centroavante 1", Posicao.CA, 75);
        add(m, c, "Centroavante 2", Posicao.CA, 72);
        TimePartida t = Escalador.montar(m, c, 3);
        Map<Integer, Posicao> vaga = new HashMap<>();
        for (Escalado e : t.titulares) vaga.put(e.id, e.vaga);
        assertEquals(Posicao.LD, vaga.get(cafu.id), "Cafu na lateral direita");
        assertEquals(Posicao.PE, vaga.get(rivaldo.id), "Rivaldo na ponta, não na lateral");
        for (Escalado e : t.titulares) assertEquals(e.natural, e.vaga, e.nome + " jogando na posição natural");
    }

    @Test
    void posicaoSecundariaVemAntesDeImproviso() {
        Clube c = clube("teste2");
        Mundo m = mundoCom(c);
        add(m, c, "Goleiro", Posicao.GOL, 70);
        add(m, c, "LD", Posicao.LD, 70);
        add(m, c, "Z1", Posicao.ZAG, 70);
        add(m, c, "Z2", Posicao.ZAG, 70);
        // sem lateral esquerdo natural: o MC que tem LE como secundária vai para lá
        Jogador versatil = add(m, c, "Versátil", Posicao.MC, 66);
        versatil.posicoesSec.add(Posicao.LE);
        add(m, c, "VOL", Posicao.VOL, 70);
        add(m, c, "MC", Posicao.MC, 70);
        add(m, c, "PE", Posicao.PE, 70);
        add(m, c, "PD", Posicao.PD, 70);
        add(m, c, "CA1", Posicao.CA, 70);
        add(m, c, "CA2", Posicao.CA, 70);
        add(m, c, "Zagueiro extra", Posicao.ZAG, 69);
        TimePartida t = Escalador.montar(m, c, 3);
        Escalado e = t.titulares.stream().filter(x -> x.id == versatil.id).findFirst().orElseThrow();
        assertEquals(Posicao.LE, e.vaga);
    }

    @Test
    void posicaoDesconhecidaEhMeioCampistaFlexivel() {
        Jogador j = new Jogador();
        j.posicao = Posicao.MC;
        j.posicaoIncerta = true;
        double meio = Escalador.encaixe(j, Posicao.MC);
        assertTrue(meio < 1.0 && meio > 0.85, "pequena penalidade no meio: " + meio);
        assertTrue(Escalador.encaixe(j, Posicao.ZAG) < meio);
        assertEquals(0.0, Escalador.encaixe(j, Posicao.GOL));
        assertTrue(List.of(Posicao.VOL, Posicao.MEI).stream().allMatch(p -> Escalador.encaixe(j, p) == meio));
    }
}
