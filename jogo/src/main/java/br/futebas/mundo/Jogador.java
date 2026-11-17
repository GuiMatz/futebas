package br.futebas.mundo;

import java.util.ArrayList;
import java.util.List;

/**
 * Um jogador do mundo. A força (1 a 100) é o número que aparece na tela; por baixo há 7
 * atributos (modelo do Brasfoot) e um potencial escondido.
 */
public class Jogador {
    public int id;
    /** Id no banco mestre (jogadores do banco, reais ou gerados); null para amadores e jovens da peneira. */
    public Integer dbId;
    public String nome;
    public String nomeConhecido;
    public int anoNasc;
    public int diaNasc = 1;
    public String pais = "BRA";
    public String pais2;
    public String uf;
    public Integer camisa;
    /** Até 2 características inatas (jeito de jogar), do banco; nunca mudam. */
    public List<String> caracteristicas = new ArrayList<>();
    public Posicao posicao = Posicao.MC;
    public List<Posicao> posicoesSec = new ArrayList<>();
    /** Posição sem fonte no banco (o banco marcou MC provisório): joga como meio-campista flexível. */
    public boolean posicaoIncerta;
    public String pe;
    public Origem origem = Origem.REAL;

    /** Atributos (ver {@link Posicao#ATRIBUTOS}). */
    public double[] atributos = new double[7];
    /** Força atual (contínua; a tela mostra arredondado). */
    public double forca;
    /** Potencial escondido (teto aproximado do auge). */
    public double potencial;
    /** Talento escondido 1–10 (Brasfoot); 0 = deduzido do potencial ({@link #talento()}). */
    public int talento;
    /** Potencial de referência (auge real, do banco). */
    public double potencialRef;
    public double fama;
    public boolean estrela;
    /** Top Mundial: estrela que repetiu um grande prêmio (Brasfoot). */
    public boolean topMundial;
    /** Já ganhou a manchete de "desponta" (jovem que chegou a nível de seleção). */
    public boolean despontou;
    /** Convocado para um torneio de seleções em andamento (fica fora do clube). */
    public boolean convocado;
    public int jogosSelecao, golsSelecao;

    public StatusJogador status = StatusJogador.LIVRE;
    public String clubeId;
    public Contrato contrato;
    public int anoAposentadoria;

    public int moral = 65;
    /** Energia/condição física 0–100: cai a cada jogo (mais com a idade e a marcação pesada) e volta com o descanso. */
    public int energia = 100;
    /** Dia (epochDay) até o qual a energia já recuperou; 0 = ainda não contado. */
    public long diaEnergia;
    /** Virou técnico ao se aposentar (fica fora da Kings League). */
    public boolean virouTecnico;
    /** Ao se aposentar, escolheu jogar a Kings League deste país (entra no draft dessa liga). */
    public String kingsPais;
    public int lesaoSemanas;
    public String lesao;
    public int suspensao;
    public int amarelos;
    public int semanasSemJogar;
    /**
     * Já jogou (com minutos na temporada) por um clube de 1ª divisão nacional. Quem se aposenta sem
     * isso e sem jogo de seleção vai para o arquivo enxuto (sem a carreira detalhada). Saves antigos:
     * false, e o arquivo também olha a divisão atual dos clubes da carreira.
     */
    public boolean jogouElite;
    /** Viradas de temporada seguidas sem clube (LIVRE). */
    public int anosLivre;
    /** Último clube (saiu livre): o mercado da IA usa como ponto de partida do degrau de divisão. */
    public String ultimoClubeId;
    /** Semana absoluta em que ficou livre (0 = desconhecida, livre há muito tempo). */
    public int livreDesde;
    /** Anunciou (6 meses antes) que se aposenta no fim da temporada. */
    public boolean vaiAposentar;
    /** Júnior da base que o técnico subiu para o time sub-20 (ainda não é profissional). */
    public boolean noSub20;
    /** Valor de mercado real (Transfermarkt, em R$) no início do jogo e o ano dele; 0 = sem dado. */
    public long valorReal;
    public int anoValorReal;

    public boolean aVenda;
    /** Na lista de empréstimos do clube (a IA pode pedir emprestado). */
    public boolean aEmprestimo;
    public long precoPedido;
    /** Treino escolhido pelo humano: "principais" | "secundarias" | null (auxiliar decide). */
    public String treino;

    // estatísticas da temporada
    public int jogosTemp, golsTemp, assistTemp, minutosTemp, minutosMarco;
    public double somaNotasTemp;
    // estatísticas da carreira
    public int jogosCarreira, golsCarreira;
    public List<Passagem> carreira = new ArrayList<>();

    /** Time da Kings League (se estiver numa). */
    public String kingsTimeId;

    public Jogador() {}

    public String nomeExibicao() {
        if (origem == Origem.COMPLEMENTAR_TESTE) return nome;
        return NomeCurto.de(nome, nomeConhecido);
    }

    public int idade(int ano) {
        return ano - anoNasc;
    }

    public int forcaExibida() {
        return (int) Math.max(1, Math.min(100, Math.round(forca)));
    }

    /** Talento escondido 1–10 (Brasfoot): o sorteado na peneira ou, sem ele, deduzido do potencial. */
    public int talento() {
        if (talento > 0) return talento;
        return (int) Math.max(1, Math.min(10, Math.round((potencial - 40) / 5.5)));
    }

    /** Pode jogar o sub-20 no ano (completa no máximo 20 anos nele). */
    public boolean sub20(int ano) {
        return ano - anoNasc <= 20;
    }

    public boolean amador() {
        return origem == Origem.COMPLEMENTAR_TESTE;
    }

    /** Reforço de emergência (contratado porque faltava gente no dia de jogo): não vai para a Kings League. */
    public boolean reforco;

    public boolean disponivel() {
        return lesaoSemanas <= 0 && suspensao <= 0 && !convocado
                && (status == StatusJogador.PROFISSIONAL || status == StatusJogador.BASE);
    }

    public double notaMediaTemp() {
        return jogosTemp == 0 ? 0 : somaNotasTemp / jogosTemp;
    }

    /** Recalcula a força a partir dos atributos e da posição. */
    public void recalcularForca() {
        double[] p = posicao.pesosAtributos();
        double f = 0;
        for (int i = 0; i < 7; i++) f += p[i] * atributos[i];
        forca = Math.max(1, Math.min(99, f));
    }

    /** Ajusta os atributos para que a força vire {@code alvo}, mantendo o perfil. */
    public void definirForca(double alvo) {
        alvo = Math.max(1, Math.min(99, alvo));
        double atual = 0;
        double[] p = posicao.pesosAtributos();
        for (int i = 0; i < 7; i++) atual += p[i] * atributos[i];
        if (atual <= 0.01) {
            for (int i = 0; i < 7; i++) atributos[i] = alvo;
        } else {
            double delta = alvo - atual;
            for (int i = 0; i < 7; i++) atributos[i] = Math.max(1, Math.min(99, atributos[i] + delta));
        }
        recalcularForca();
        // se o recorte em 1..99 deixou diferença, corrige direto
        if (Math.abs(forca - alvo) > 0.5) forca = alvo;
    }

    /** Estatísticas da temporada nos jogos do sub-20 (separadas das do profissional). */
    public int sub20Jogos, sub20Gols, sub20Assist;
    public double sub20SomaNotas;

    public double notaMediaSub20() {
        return sub20Jogos == 0 ? 0 : sub20SomaNotas / sub20Jogos;
    }

    public void zerarTemporada() {
        jogosTemp = golsTemp = assistTemp = minutosTemp = minutosMarco = 0;
        somaNotasTemp = 0;
        sub20Jogos = sub20Gols = sub20Assist = 0;
        sub20SomaNotas = 0;
        amarelos = 0;
    }
}
