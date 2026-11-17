package br.futebas.mundo;

import java.util.ArrayList;
import java.util.List;

/**
 * Registro compacto de um jogador aposentado há algum tempo (item 3 da memória): só o que as
 * telas de história usam. O {@link Mundo} devolve um "esboço" ({@link #esboco()}) quando alguém
 * pede o jogador pelo id, então as telas continuam funcionando sem saber do arquivo.
 */
public final class Arquivado {
    /** Inteiros por temporada em {@link #temporadas}. */
    static final int CAMPOS = 6;
    /** Auge abaixo disto: a carreira arquivada é resumida por passagem no clube. */
    static final int RESUMIR_ABAIXO = 70;

    public int id;
    public Integer dbId;
    public String nome, nomeConhecido, pais;
    public int anoNasc;
    public Posicao posicao;
    public Origem origem;
    public int anoAposentadoria;
    public int jogosCarreira, golsCarreira, jogosSelecao, golsSelecao;
    /** Força ao se aposentar e a maior força da carreira. */
    public int forcaFinal, forcaMaxima;
    public boolean estrela, topMundial, virouTecnico;
    /** Carreira: clube (id e nome da época) por temporada. */
    public String[] clubeIds, clubeNomes;
    /** Carreira: {ano, jogos, gols, assistências, nota × 10, força | 1&lt;&lt;16 se empréstimo} por temporada. */
    public int[] temporadas;

    public Arquivado() {}

    static Arquivado de(Jogador j, Interner in) {
        return de(j, in, false);
    }

    /**
     * {@code enxuto}: sem a carreira detalhada (regra do jogo 16/11/2026, saúde do mundo). Fica o que o
     * histórico usa: id, nome, país, posição, nascimento, gols e jogos da carreira e da seleção, força.
     */
    static Arquivado de(Jogador j, Interner in, boolean enxuto) {
        Arquivado a = new Arquivado();
        a.id = j.id;
        a.dbId = j.dbId;
        a.nome = j.nome;
        a.nomeConhecido = j.nomeConhecido;
        a.pais = in.s(j.pais);
        a.anoNasc = j.anoNasc;
        a.posicao = j.posicao;
        a.origem = j.origem;
        a.anoAposentadoria = j.anoAposentadoria;
        a.jogosCarreira = j.jogosCarreira;
        a.golsCarreira = j.golsCarreira;
        a.jogosSelecao = j.jogosSelecao;
        a.golsSelecao = j.golsSelecao;
        a.forcaFinal = (int) Math.round(j.forca);
        a.estrela = j.estrela;
        a.topMundial = j.topMundial;
        a.virouTecnico = j.virouTecnico;
        int max = a.forcaFinal;
        for (Passagem p : j.carreira) max = Math.max(max, p.forca);
        // save menor: quem não foi grande (auge < 70, sem estrela) guarda a carreira por passagem
        // no clube (temporadas seguidas somadas), como a carreira real do banco
        List<Passagem> c = enxuto ? List.of() : max < RESUMIR_ABAIXO && !j.estrela && !j.topMundial ? resumir(j.carreira) : j.carreira;
        int n = c.size();
        if (n > 0) {
            a.clubeIds = new String[n];
            a.clubeNomes = new String[n];
            a.temporadas = new int[n * CAMPOS];
            for (int i = 0; i < n; i++) {
                Passagem p = c.get(i);
                a.clubeIds[i] = in.s(p.clubeId);
                a.clubeNomes[i] = in.s(p.clubeNome);
                int k = i * CAMPOS;
                a.temporadas[k] = p.ano | (p.anoFim > p.ano ? Math.min(255, p.anoFim - p.ano) << 16 : 0);
                a.temporadas[k + 1] = p.jogos;
                a.temporadas[k + 2] = p.gols;
                a.temporadas[k + 3] = p.assistencias;
                a.temporadas[k + 4] = (int) Math.round(p.notaMedia * 10);
                a.temporadas[k + 5] = (p.forca & 0xFFFF) | (p.emprestimo ? 1 << 16 : 0);
            }
        }
        a.forcaMaxima = max;
        return a;
    }

    /** Um {@link Jogador} montado na hora, aposentado e sem clube (alterá-lo não muda nada). */
    public Jogador esboco() {
        Jogador j = new Jogador();
        j.id = id;
        j.dbId = dbId;
        j.nome = nome;
        j.nomeConhecido = nomeConhecido;
        j.pais = pais;
        j.anoNasc = anoNasc;
        j.posicao = posicao;
        j.origem = origem;
        j.status = StatusJogador.APOSENTADO;
        j.anoAposentadoria = anoAposentadoria;
        j.jogosCarreira = jogosCarreira;
        j.golsCarreira = golsCarreira;
        j.jogosSelecao = jogosSelecao;
        j.golsSelecao = golsSelecao;
        j.forca = forcaFinal;
        j.potencial = forcaMaxima;
        j.potencialRef = forcaMaxima;
        j.estrela = estrela;
        j.topMundial = topMundial;
        j.virouTecnico = virouTecnico;
        j.carreira = carreira();
        return j;
    }

    public List<Passagem> carreira() {
        List<Passagem> l = new ArrayList<>();
        if (temporadas == null) return l;
        for (int i = 0; i < clubeIds.length; i++) {
            int k = i * CAMPOS;
            Passagem p = new Passagem(temporadas[k] & 0xFFFF, clubeIds[i], clubeNomes[i], temporadas[k + 1], temporadas[k + 2],
                    temporadas[k + 3], temporadas[k + 4] / 10.0, temporadas[k + 5] & 0xFFFF);
            p.emprestimo = (temporadas[k + 5] & (1 << 16)) != 0;
            if ((temporadas[k] >>> 16) > 0) p.anoFim = p.ano + (temporadas[k] >>> 16);
            l.add(p);
        }
        return l;
    }

    /** Temporadas seguidas no mesmo clube (e mesmo tipo: empréstimo ou não) viram uma passagem. */
    static List<Passagem> resumir(List<Passagem> c) {
        List<Passagem> l = new ArrayList<>();
        Passagem atual = null;
        double somaNota = 0;
        for (Passagem p : c) {
            int fim = Math.max(p.ano, p.anoFim);
            if (atual != null && java.util.Objects.equals(atual.clubeId, p.clubeId) && atual.emprestimo == p.emprestimo
                    && p.ano <= Math.max(atual.ano, atual.anoFim) + 1) {
                atual.anoFim = Math.max(Math.max(atual.ano, atual.anoFim), fim);
                atual.jogos += p.jogos;
                atual.gols += p.gols;
                atual.assistencias += p.assistencias;
                atual.forca = Math.max(atual.forca, p.forca);
                somaNota += p.notaMedia * Math.max(1, p.jogos);
                continue;
            }
            if (atual != null) atual.notaMedia = fecharNota(atual, somaNota);
            atual = new Passagem(p.ano, p.clubeId, p.clubeNome, p.jogos, p.gols, p.assistencias, p.notaMedia, p.forca);
            atual.emprestimo = p.emprestimo;
            atual.anoFim = p.anoFim;
            somaNota = p.notaMedia * Math.max(1, p.jogos);
            l.add(atual);
        }
        if (atual != null) atual.notaMedia = fecharNota(atual, somaNota);
        return l;
    }

    private static double fecharNota(Passagem p, double somaNota) {
        // peso = max(1, jogos) por temporada; aproximação suficiente para a ficha
        return p.jogos > 0 ? Math.round(somaNota / Math.max(p.jogos, 1) * 10) / 10.0 : p.notaMedia;
    }

    public String nomeExibicao() {
        return nomeConhecido != null && !nomeConhecido.isBlank() ? nomeConhecido : nome;
    }

    /** Uma cópia única de cada texto repetido (ids e nomes de clubes, países). */
    static final class Interner {
        private final java.util.HashMap<String, String> m = new java.util.HashMap<>();

        String s(String x) {
            if (x == null) return null;
            String y = m.putIfAbsent(x, x);
            return y == null ? x : y;
        }
    }
}
