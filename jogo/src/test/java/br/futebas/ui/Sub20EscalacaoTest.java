package br.futebas.ui;

import br.futebas.Fixture;
import br.futebas.Jogo;
import br.futebas.mundo.Jogador;
import br.futebas.partida.Escalado;
import br.futebas.partida.Escalador;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** A escalação do sub-20 na Base mostra o mesmo time que a simulação usa (os 11 mais fortes sem lesão). */
class Sub20EscalacaoTest {

    @Test
    void escalacaoDoSub20EOTimeDaSimulacao() {
        Jogo j = Jogo.novoJogo(Fixture.banco(), 23, "bra-1-01", "T");
        String c = j.meuClube().id;
        List<Jogador> onze = j.onzeSub20(c);
        assertTrue(onze.size() <= 11);
        for (Jogador x : onze) assertTrue(x.lesaoSemanas <= 0, "sem lesionados");
        // a força do sub-20 é a média desses 11, completando com juniores
        double s = 0;
        for (Jogador x : onze) s += x.forca;
        double esperado = (s + (11 - onze.size()) * j.forcaJuniorSub20(c)) / 11.0;
        assertEquals(esperado, j.forcaSub20(c), 1e-9);
        // no campo: os mesmos 11 nas vagas da formação (vaga vazia = júnior anônimo)
        List<Escalado> vagas = Escalador.distribuir(onze, j.meuClube().tatica.formacao);
        assertEquals(11, vagas.size());
        Set<Integer> ids = new HashSet<>();
        for (Escalado e : vagas) if (e != null) ids.add(e.id);
        Set<Integer> esperados = new HashSet<>();
        for (Jogador x : onze) esperados.add(x.id);
        assertEquals(esperados, ids);
        assertEquals(11 - onze.size(), vagas.stream().filter(Objects::isNull).count());
        // com poucos jogadores também distribui
        List<Escalado> poucos = Escalador.distribuir(onze.subList(0, Math.min(5, onze.size())), "4-3-3");
        assertEquals(11, poucos.size());
        assertEquals(Math.min(5, onze.size()), poucos.stream().filter(Objects::nonNull).count());
    }
}
