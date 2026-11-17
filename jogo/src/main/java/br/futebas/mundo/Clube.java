package br.futebas.mundo;

import java.util.ArrayList;
import java.util.List;

/**
 * Um clube do mundo. A grandeza (reputação, torcida, dinheiro, estrutura) começa do dado real
 * e depois sobe ou cai com os resultados.
 */
public class Clube {
    public String id;
    public String nome;
    public String sigla;
    public String pais = "BRA";
    public String confederacao;
    /** 'ano' | 'europeu' (do país). */
    public String calendario = "ano";
    public String uf;
    public String cidade;
    public String cor1, cor2;
    public String estadio;
    public int capacidade;
    /** Capacidade em 2026 (referência do preço sugerido: ampliar o estádio aumenta a renda). */
    public int capacidadeInicial;
    public List<String> rivais = new ArrayList<>();
    /** Clube sem elenco no banco: joga só com força (time virtual, sem nomes). */
    public boolean virtual;
    /** Falso depois da extinção real do clube. */
    public boolean existe = true;
    /** Falência: perde a vaga na divisão nacional no ano seguinte. */
    public boolean rebaixamentoForcado;
    /** Partidas oficiais na temporada (para a evolução por minutos). */
    public int jogosTemp, jogosMarco;
    /** Semana absoluta do último jogo oficial (salários do clube sem divisão). */
    public int ultimoJogoSa;

    public double reputacao;
    /** Técnico da IA do clube (null no clube do humano ou vago). */
    public Integer treinadorId;
    public double reputacaoInicial;
    /** Orçamento anual de referência do banco (R$); 0 = estimar pela reputação. */
    public long orcamentoRef;
    /** Divisão em que o clube começou o mundo (a receita de referência vale para ela); null = desconhecida. */
    public Integer divisaoInicial;
    /**
     * Escala salarial do clube (Brasfoot: salário = força × base do clube): salário mensal de um
     * jogador = fatorSalarial × receita de referência × unidades(força). Calibrada na criação do
     * mundo para a folha inicial ficar perto de {@code Balanceamento.FOLHA_ALVO} da receita.
     */
    public double fatorSalarial;
    /**
     * Freio à concentração de dinheiro (16/11/2026): a 3ª maior receita bruta da 1ª divisão do país (R$ de
     * 2026). Acima dela a receita cresce com retorno decrescente ({@code Financas.receitaReferencia}).
     * 0 = sem freio (saves antigos até a próxima virada).
     */
    public double receitaFreio;
    /**
     * Forma da temporada (16/11/2026, saúde do mundo), em pontos de força: entrosamento, técnico, sorte com
     * lesões. Sorteada a cada temporada (clubes da IA; o do técnico humano fica 0) e somada à força nos
     * jogos. Dá a um clube médio um ano bom de vez em quando sem mudar o jogo a jogo.
     */
    public double formaTemporada;
    /** Preço do ingresso fixado pelo técnico (R$, liga); 0 = preço sugerido (automático). */
    public long precoIngresso;
    /** Obra de ampliação do estádio: lugares a mais e semana absoluta de término (0 = sem obra). */
    public int obraLugares, obraFim;
    /** Nível (16 melhores) na 1ª peneira (2026): a base do clube não piora com elencos mais fracos (0 = ainda não medido). */
    public double nivelInicial;
    /** Força média dos 16 melhores (atualizada toda semana). */
    public double nivelAtual;
    public double torcida;
    public double estrutura;
    public long dinheiro;
    /** TV/patrocínio da temporada ainda a receber, em parcelas mensais (junto com a folha). */
    public long tvPendente;
    public int tvParcelas;
    public long divida;
    /**
     * Empréstimo do clube do humano: parcelas mensais que faltam (a dívida é paga em parcelas
     * iguais de amortização, junto com a folha, mais os juros). 0 com dívida = save antigo (vira
     * {@link br.futebas.financas.Financas#PRAZO_EMPRESTIMO_MESES}).
     */
    public int dividaParcelas;

    /** Divisão nacional atual no país (1..n), 0 = sem divisão. */
    public int divisao;
    /** Posição final na divisão no último ano (para o ranking). */
    public int posicaoDivisao = 99;
    /** Sub-20: divisão na pirâmide sub-20 do país (0 = fora) e posição final nela no último ano. */
    public int divisaoSub20;
    public int posicaoSub20 = 99;

    public List<Integer> elenco = new ArrayList<>();
    public List<Integer> base = new ArrayList<>();
    /** Clube do técnico: o time sub-20 já foi montado uma vez (Base → Sub-20 → Profissional, regra do jogo). */
    public boolean sub20Montado;
    /** Saves antigos: amadores em teste (a mecânica saiu em 15/11/2026; esvaziado ao carregar). */
    public List<Integer> amadores = new ArrayList<>();
    /** Saves antigos: reserva de amadores (esvaziada ao carregar). */
    public List<Integer> amadoresReserva = new ArrayList<>();
    public int contadorAmador;

    public Tatica tatica = new Tatica();
    public boolean humano;
    public double confiancaDiretoria = 60;
    public double confiancaTorcida = 60;
    /** Avaliação da diretoria na última virada de temporada (texto curto) e a nota (0..100). */
    public String vereditoDiretoria;
    /** Receitas da temporada passada (sem vendas de jogadores): base do teto de folha da IA. */
    public long receitaUltimaTemporada;
    public double notaTemporada = -1;
    public int demissoesTecnico;
    public int semanasDesdeTroca = 99;

    public int temporadasNoVermelho;
    public int falencias;
    public FinancasClube financas = new FinancasClube();
    /** Livro-caixa da temporada passada (para as telas e a diretoria). */
    public FinancasClube financasAnterior;

    // forma recente (últimos resultados: 3 vitória, 1 empate, 0 derrota)
    public List<Integer> forma = new ArrayList<>();

    public Clube() {}

    /** Nível do clube (escala da força dos jogadores), usado pelos reforços de emergência e pela IA. */
    public double nivel() {
        return nivelAtual > 0 ? nivelAtual : 35 + 0.45 * reputacao;
    }

    public void registrarForma(int pontos) {
        forma.add(pontos);
        while (forma.size() > 10) forma.remove(0);
    }

    public double aproveitamentoRecente() {
        if (forma.isEmpty()) return 0.5;
        double s = 0;
        for (int p : forma) s += p;
        return s / (3.0 * forma.size());
    }
}
