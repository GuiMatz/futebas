package br.futebas.ui.modelo;

import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;

import java.util.ArrayList;
import java.util.List;

/**
 * Opção "confirmar escalação com desfalques": titulares da escalação manual que não podem jogar
 * (lesão, suspensão, convocação, saída do clube, venda esperando a janela; esses dois últimos já
 * saem da escalação salva aqui). Escalação automática não tem problema: o
 * auxiliar escala quem está disponível.
 */
public final class ConferirEscalacao {
    private ConferirEscalacao() {}

    public static List<String> problemas(Mundo m, Clube c) {
        List<String> l = new ArrayList<>();
        if (m == null || c == null || c.tatica == null || c.tatica.titulares == null) return l;
        // vendidos/emprestados esperando a janela e quem já saiu: saem da escalação salva (a vaga fica vazia)
        l.addAll(br.futebas.partida.Escalador.limparEscalacao(m, c));
        int vazias = 0;
        for (Integer id : c.tatica.titulares) {
            if (id == null || id < 0) {
                vazias++;
                continue;
            }
            Jogador j = m.jogador(id);
            if (j == null || !c.id.equals(j.clubeId)) {
                l.add((j == null ? "Jogador" : j.nomeExibicao()) + ": não está mais no elenco");
                continue;
            }
            if (j.lesaoSemanas > 0) l.add(j.nomeExibicao() + ": lesionado (" + j.lesaoSemanas + (j.lesaoSemanas == 1 ? " semana)" : " semanas)"));
            else if (j.suspensao > 0) l.add(j.nomeExibicao() + ": suspenso");
            else if (j.convocado) l.add(j.nomeExibicao() + ": convocado pela seleção");
            else if (!j.disponivel()) l.add(j.nomeExibicao() + ": indisponível");
        }
        if (!c.tatica.titulares.isEmpty() && c.tatica.titulares.size() < 11) l.add("Escalação com menos de 11 titulares");
        else if (vazias > 0 && vazias < 11) l.add(vazias == 1 ? "1 vaga vazia na escalação (o auxiliar completa)" : vazias + " vagas vazias na escalação (o auxiliar completa)");
        return l;
    }
}
