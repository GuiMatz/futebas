package br.futebas.financas;

import br.futebas.mundo.Calendario;

import br.futebas.Balanceamento;
import br.futebas.competicao.MontadorTemporada;
import br.futebas.competicao.Torneio;
import br.futebas.dados.Db;
import br.futebas.mundo.Clube;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Origem;
import br.futebas.mundo.Posicao;

/**
 * Finanças no espírito do Brasfoot, ancoradas no orçamento real de cada clube.
 *
 * <p>Cada clube tem uma <b>receita de referência</b> anual E (orçamento do banco, corrigido pela
 * reputação ganha/perdida e pela divisão). Dela saem TV/patrocínio, o preço sugerido do
 * ingresso, o custeio, o limite do banco, os prêmios e a <b>escala salarial</b> do clube:
 * como no Brasfoot (salário = força × base do clube), o salário depende do jogador e de
 * quem paga, e o valor de mercado é 4·F² × base do clube. Assim a IA tem finanças simples e
 * estáveis em qualquer país, e o humano sente cada decisão (ingresso, folha, obras, banco).
 * Valores em reais (R$). Constantes em {@link Balanceamento}.
 */
public final class Financas {
    private Financas() {}

    /** Correção anual dos valores (1,0 = 2026; ~1,5% ao ano depois). */
    public static double fatorEpoca(int ano) {
        return ano <= 2026 ? 1.0 : Math.min(2.0, 1.0 + 0.015 * (ano - 2026));
    }

    // ------------------------------------------------------------------ receita de referência
    static double pesoDivisao(int div) {
        double[] d = Balanceamento.RECEITA_DIVISAO;
        return div >= 1 && div <= 4 ? d[div - 1] : d[4];
    }

    /** Correção da receita pela divisão atual em relação à divisão de 2026 (acesso dá dinheiro). */
    public static double fatorDivisao(Clube c) {
        if (c.divisaoInicial == null) return 1.0;
        double r = pesoDivisao(c.divisao) / pesoDivisao(c.divisaoInicial);
        return Math.max(0.35, Math.min(2.5, Math.pow(r, Balanceamento.RECEITA_DIVISAO_EXP)));
    }

    /** Receita anual de referência E do clube (R$). */
    public static double receitaReferencia(Clube c, int ano) {
        double e = receitaBruta(c);
        double r = c.receitaFreio;
        // retorno decrescente acima da 3ª maior receita da liga (regra do jogo 16/11/2026, saúde do mundo)
        if (r > 0 && e > r) e = r * Math.pow(e / r, Balanceamento.RECEITA_RETORNO);
        return e * fatorEpoca(ano);
    }

    /** Receita de referência sem o freio e sem a época (R$ de 2026). */
    public static double receitaBruta(Clube c) {
        double base;
        if (c.orcamentoRef > 0) {
            base = c.orcamentoRef * Math.exp(Math.max(-40, Math.min(40, c.reputacao - c.reputacaoInicial)) / Balanceamento.RECEITA_REP_DIV);
        } else {
            base = Balanceamento.RECEITA_BASE * Math.exp(c.reputacao / Balanceamento.RECEITA_DIV);
        }
        return base * fatorDivisao(c);
    }

    /**
     * Freio à concentração (16/11/2026): para cada país, a {@link Balanceamento#RECEITA_FREIO_POSICAO}ª
     * maior receita bruta da 1ª divisão vira a referência de todos os clubes do país. Os gigantes
     * (Real Madrid, PSG, Bayern) tinham 2–5× a receita do 3º da liga e ganhavam quase todo ano.
     */
    public static void atualizarFreio(java.util.Collection<Clube> clubes) {
        java.util.Map<String, java.util.List<Double>> porPais = new java.util.HashMap<>();
        for (Clube c : clubes) if (!c.virtual && c.existe && c.divisao == 1) porPais.computeIfAbsent(c.pais, k -> new java.util.ArrayList<>()).add(receitaBruta(c));
        java.util.Map<String, Double> ref = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, java.util.List<Double>> x : porPais.entrySet()) {
            java.util.List<Double> l = x.getValue();
            l.sort(java.util.Comparator.reverseOrder());
            if (l.size() >= Balanceamento.RECEITA_FREIO_POSICAO) ref.put(x.getKey(), l.get(Balanceamento.RECEITA_FREIO_POSICAO - 1));
        }
        for (Clube c : clubes) c.receitaFreio = ref.getOrDefault(c.pais, 0.0);
    }

    /** Receita fixa anual (TV + patrocínio) da temporada. Mantido por compatibilidade. */
    public static double receitaFixaBase(Clube c, int ano) {
        return receitaReferencia(c, ano) * Balanceamento.RECEITA_TV;
    }

    /** TV + patrocínio da temporada (pago no início). */
    public static long tvPatrocinio(Clube c, int ano) {
        return arred(receitaFixaBase(c, ano));
    }

    // ------------------------------------------------------------------ salários
    /** Unidades salariais do jogador: e^(K×(F−60)), estrela/Top, corte por idade. */
    public static double unidadesSalario(Jogador j, int ano) {
        double u = Math.exp(Balanceamento.SALARIO_K * (j.forca - 60));
        if (j.topMundial) u *= Balanceamento.SALARIO_ESTRELA * Balanceamento.SALARIO_TOP;
        else if (j.estrela) u *= Balanceamento.SALARIO_ESTRELA;
        int idade = j.idade(ano);
        if (idade >= 33) u *= Math.max(0.6, 1 - Balanceamento.SALARIO_IDADE * (idade - 32));
        return u;
    }

    /** Escala salarial padrão (sem calibração): folha alvo de um elenco de 22 no nível do clube. */
    public static double fatorSalarialPadrao(Clube c) {
        double u = Math.exp(Balanceamento.SALARIO_K * (c.nivel() - 60)) * 1.15;
        return Balanceamento.FOLHA_ALVO / 12.0 / (22 * u);
    }

    /**
     * Calibra a escala salarial do clube pelo elenco atual: a folha (em unidades) vezes a escala
     * dá {@link Balanceamento#FOLHA_ALVO} da receita de referência.
     */
    public static void calibrar(Clube c, Iterable<Jogador> elenco, int ano) {
        double soma = 0;
        int n = 0;
        for (Jogador j : elenco) {
            if (j == null || j.amador()) continue;
            soma += unidadesSalario(j, ano);
            n++;
        }
        double nivel = Math.exp(Balanceamento.SALARIO_K * (c.nivel() - 60));
        if (n < 22) soma += (22 - n) * nivel * 0.8; // elenco curto: conta os reforços que virão
        c.fatorSalarial = soma <= 0 ? fatorSalarialPadrao(c) : Balanceamento.FOLHA_ALVO / 12.0 / soma;
    }

    /** Salário mensal de uma unidade salarial no clube (a "base" do Brasfoot). */
    public static double baseSalarial(Clube c, int ano) {
        double q = c.fatorSalarial > 0 ? c.fatorSalarial : fatorSalarialPadrao(c);
        return q * receitaReferencia(c, ano);
    }

    /** Base de quem está sem clube (só para exibição e referência). */
    static double baseSemClube(int ano) {
        return 8_000_000 * fatorEpoca(ano) * Balanceamento.FOLHA_ALVO / 12.0 / (22 * 1.15);
    }

    public static long salarioMinimo(int ano) {
        return arred(Balanceamento.SALARIO_MINIMO * fatorEpoca(ano));
    }

    /**
     * Salário mensal que o jogador pede para jogar no clube {@code destino} (força × base do
     * clube). Quem já tem contrato em outro clube não aceita ganhar menos de 60% do que ganha.
     */
    public static long salarioPedido(Jogador j, Clube destino, int ano) {
        if (j.origem == Origem.COMPLEMENTAR_TESTE) return salarioMinimo(ano);
        double base = destino == null || destino.virtual ? baseSemClube(ano) : baseSalarial(destino, ano);
        double s = base * unidadesSalario(j, ano);
        if (destino != null && j.contrato != null && j.clubeId != null && !j.clubeId.equals(destino.id) && !j.contrato.teste()) {
            s = Math.max(s, j.contrato.salario * 0.6);
        }
        return Math.max(salarioMinimo(ano), arred(s));
    }

    /** Salário na escala do clube, sem a exigência de quem vem de clube mais rico (empréstimos, base). */
    public static long salarioNaEscala(Jogador j, Clube c, int ano) {
        if (j.origem == Origem.COMPLEMENTAR_TESTE) return salarioMinimo(ano);
        double base = c == null || c.virtual ? baseSemClube(ano) : baseSalarial(c, ano);
        return Math.max(salarioMinimo(ano), arred(base * unidadesSalario(j, ano)));
    }

    /** Salário pedido sem clube de destino conhecido (referência genérica). */
    public static long salarioPedido(Jogador j, int ano) {
        return salarioPedido(j, null, ano);
    }

    // ------------------------------------------------------------------ valor de mercado
    /** Fator de idade do valor (Brasfoot: termo de idade somado à base). */
    static double fatorIdadeValor(int idade) {
        if (idade < 20) return 1.45;
        if (idade <= 25) return 1 + (32 - idade) * 0.035;
        // 30+ calibrado contra o Transfermarkt (veteranos valiam 3x o real)
        if (idade <= 29) return 1 + (32 - idade) * 0.02;
        if (idade <= 31) return (1 + (32 - idade) * 0.02) * 0.9;
        if (idade <= 33) return (0.85 - 0.1 * (idade - 32)) * 0.72;
        return Math.max(0.08, (0.6 - 0.12 * (idade - 34)) * 0.6);
    }

    /** Valor de mercado no clube {@code c}: 4·F² × base do clube (Brasfoot), com prêmio de craque, idade e potencial. */
    public static long valorMercado(Jogador j, Clube c, int ano) {
        if (j.origem == Origem.COMPLEMENTAR_TESTE) return 0;
        double base = c == null || c.virtual ? baseSemClube(ano) : baseSalarial(c, ano);
        int idade = j.idade(ano);
        double k = fatorIdadeValor(idade);
        if (idade <= 23) k *= 1 + Math.max(0, j.potencial - j.forca) * 0.03;
        if (j.topMundial) k *= Balanceamento.VALOR_ESTRELA * Balanceamento.VALOR_TOP;
        else if (j.estrela) k *= Balanceamento.VALOR_ESTRELA;
        if (j.posicao == Posicao.CA || j.posicao == Posicao.PE || j.posicao == Posicao.PD) k *= Balanceamento.VALOR_ATACANTE;
        double v = Balanceamento.VALOR_FATOR * j.forca * j.forca * base * Math.exp(Balanceamento.VALOR_K * (j.forca - 70)) * k;
        // jogador fraco valia 3–10x o real: desconto abaixo de 65
        v /= 1 + Math.max(0, 65 - j.forca) * 0.25;
        // âncora no valor real do Transfermarkt: no começo vale o real; a fórmula assume em ~6 anos
        if (j.valorReal > 0) {
            double w = Math.max(0, 1 - Math.max(0, ano - j.anoValorReal) / (double) Balanceamento.VALOR_REAL_ANOS);
            if (w > 0) v = Math.exp(w * Math.log(j.valorReal) + (1 - w) * Math.log(Math.max(1, v)));
        }
        return arred(v);
    }

    /** Valor sem clube conhecido (referência genérica). */
    public static long valorMercado(Jogador j, int ano) {
        return valorMercado(j, null, ano);
    }

    public static long multaRescisoria(Jogador j, Clube c, int ano) {
        return arred(Math.max(valorMercado(j, c, ano) * 2.0, salarioPedido(j, c, ano) * 24.0));
    }

    public static long multaRescisoria(Jogador j, int ano) {
        return multaRescisoria(j, null, ano);
    }

    // ------------------------------------------------------------------ bilheteria (Brasfoot)
    /** Público-base pela reputação do mandante (Brasfoot: soma dos 4 setores por nível 0–5). */
    static double publicoReputacao(double reputacao) {
        double[] t = {750, 7_200, 13_500, 26_800, 38_400, 51_000};
        double x = Math.max(0, Math.min(5, reputacao / 20.0));
        int i = (int) Math.min(4, Math.floor(x));
        return t[i] + (t[i + 1] - t[i]) * (x - i);
    }

    static String ambito(Torneio t) {
        return t == null || t.ambito == null ? "nacional" : t.ambito;
    }

    /** Multiplicadores da competição: {público-base, fração extra da capacidade, preço}. */
    static double[] fatorCompeticao(Torneio t) {
        if (t == null) return new double[]{0.4, 0.0, 0.5};
        return switch (ambito(t)) {
            case "continental" -> MontadorTemporada.nivelContinental(new Db.Competicao(t.id, t.nome, t.ambito, null, null, null, null, null, null)) == 0
                    ? new double[]{1.0, 0.30, 1.8} : new double[]{1.0, 0.15, 1.3};
            case "estadual", "regional" -> new double[]{0.7, 0.0, 0.6};
            case "copa_nacional" -> new double[]{0.9, 0.10, 0.9};
            case "amistoso" -> new double[]{0.4, 0.0, 0.5};
            case "selecoes" -> new double[]{1.0, 0.30, 1.5};
            default -> new double[]{1.0, 0.0, 1.0};
        };
    }

    /** Capacidade de referência do preço (a de 2026: ampliar o estádio rende mais). */
    static int capacidadeReferencia(Clube c) {
        int cap = c.capacidade > 0 ? c.capacidade : 8000;
        return c.capacidadeInicial > 0 ? Math.min(cap, c.capacidadeInicial) : cap;
    }

    /** Preço sugerido do ingresso na liga (R$): calibrado para a bilheteria render ~15% da receita. */
    public static long precoSugerido(Clube c, int ano) {
        int cap = capacidadeReferencia(c);
        double publico = Math.min(cap * 0.95, publicoReputacao(c.reputacao) + 0.30 * cap) * 0.75;
        double p = Balanceamento.RECEITA_INGRESSOS * receitaReferencia(c, ano) / (Balanceamento.JOGOS_CASA_REF * Math.max(500, publico));
        return Math.max(2, Math.round(p));
    }

    /** Preço cobrado no jogo (o fixado pelo técnico vale para a liga; as outras competições seguem a proporção). */
    public static double precoJogo(Clube mandante, Torneio t, int ano) {
        double sug = precoSugerido(mandante, ano) * fatorCompeticao(t)[2];
        if (mandante.precoIngresso > 0) sug *= mandante.precoIngresso / (double) precoSugerido(mandante, ano);
        return sug;
    }

    /** Efeito do preço no público: ±% por fração fora do sugerido (Brasfoot: 3% por unidade). */
    public static double efeitoPreco(Clube c, int ano) {
        if (c.precoIngresso <= 0) return 1.0;
        double x = c.precoIngresso / (double) precoSugerido(c, ano);
        return x >= 1 ? Math.max(0.15, 1 - Balanceamento.PRECO_ELASTICIDADE_ACIMA * (x - 1))
                : 1 + Balanceamento.PRECO_ELASTICIDADE_ABAIXO * (1 - x);
    }

    /**
     * Público de um jogo em casa (Brasfoot): base pela reputação + parte da capacidade pela
     * competição, ±5% por nível de reputação do visitante, clássico, confiança da torcida e preço.
     */
    public static int publico(Clube mandante, Clube visitante, Torneio t) {
        return publico(mandante, visitante, t, 2026);
    }

    public static int publico(Clube mandante, Clube visitante, Torneio t, int ano) {
        double[] f = fatorCompeticao(t);
        int cap = mandante.capacidade > 0 ? mandante.capacidade : 8000;
        double base = publicoReputacao(mandante.reputacao) * f[0] + cap * (0.30 + f[1]);
        if (visitante != null) {
            double niveis = Math.max(-5, Math.min(5, (visitante.reputacao - mandante.reputacao) / 20.0));
            base *= 1 + Balanceamento.PUBLICO_REPUTACAO * niveis;
            if (mandante.rivais.contains(visitante.id)) base *= 1.25;
        }
        base *= 0.55 + 0.45 * Math.max(0, Math.min(100, mandante.confiancaTorcida)) / 100.0;
        base *= 0.9 + 0.2 * mandante.aproveitamentoRecente();
        base *= efeitoPreco(mandante, ano);
        return (int) Math.max(cap * 0.03, Math.min(cap * 0.98, base));
    }

    /** Renda de um jogo. */
    public static long renda(int publico, Torneio t, Clube mandante, int ano) {
        return arred(publico * precoJogo(mandante, t, ano));
    }

    // ------------------------------------------------------------------ prêmios
    /** Fração do prêmio do campeão para a competição (sobre a receita média dos participantes). */
    static double fracaoPremio(Torneio t) {
        return switch (ambito(t)) {
            case "continental" -> MontadorTemporada.nivelContinental(new Db.Competicao(t.id, t.nome, t.ambito, null, null, null, null, null, null)) == 0
                    ? Balanceamento.PREMIO_CONTINENTAL_1
                    : MontadorTemporada.nivelContinental(new Db.Competicao(t.id, t.nome, t.ambito, null, null, null, null, null, null)) == 2
                    ? Balanceamento.PREMIO_CONTINENTAL_3 : Balanceamento.PREMIO_CONTINENTAL_2;
            case "estadual", "regional" -> Balanceamento.PREMIO_ESTADUAL;
            case "kings", "amistoso", "selecoes" -> 0;
            case "copa_nacional" -> Balanceamento.PREMIO_COPA;
            default -> t.divisao == null ? Balanceamento.PREMIO_COPA : t.divisao == 1 ? Balanceamento.PREMIO_LIGA_1 : Balanceamento.PREMIO_LIGA_OUTRAS;
        };
    }

    /** Prêmio por colocação ao fim de um torneio, proporcional à receita média dos participantes. */
    public static long premio(Torneio t, int posicao, double receitaMediaParticipantes) {
        double campeao = fracaoPremio(t) * receitaMediaParticipantes;
        double fator = posicao == 1 ? 1.0 : posicao == 2 ? 0.5 : posicao <= 4 ? 0.25 : posicao <= 8 ? 0.1 : 0.03;
        return arred(campeao * fator);
    }

    /** Compatibilidade: prêmio com a receita do próprio clube como referência. */
    public static long premio(Torneio t, int posicao, Clube c, int ano) {
        return premio(t, posicao, c == null ? 0 : receitaReferencia(c, ano));
    }

    // ------------------------------------------------------------------ custos e banco
    /**
     * Custeio mensal (funcionários, viagens, manutenção, CT): fração da receita de referência,
     * mais um pouco pela estrutura. Caixa muito acima da receita vira investimento (não acumula).
     */
    public static long custeioMensal(Clube c, int ano) {
        if (c.divisao == 0 && c.jogosTemp == 0) return 0; // clube parado
        double e = receitaReferencia(c, ano);
        double base = e * Balanceamento.CUSTEIO / 12.0 * (0.85 + 0.3 * c.estrutura / 100.0);
        double teto = e * Balanceamento.CAIXA_TETO;
        if (c.dinheiro > teto) base += (c.dinheiro - teto) * Balanceamento.CAIXA_EXCESSO_MES;
        return arred(base);
    }

    /** Limite de empréstimo bancário (Brasfoot: por divisão; aqui fração da receita). */
    public static long limiteEmprestimo(Clube c, int ano) {
        return arred(Math.max(200_000 * fatorEpoca(ano), receitaReferencia(c, ano) * Balanceamento.LIMITE_EMPRESTIMO));
    }

    public static final double JUROS_MES = Balanceamento.JUROS_MES;

    /** Prazo do empréstimo do humano: a dívida é dividida em 12 parcelas mensais (pedir mais renegocia o prazo). */
    public static final int PRAZO_EMPRESTIMO_MESES = 12;

    /** Parcelas que faltam (save antigo com dívida e sem prazo = prazo cheio). */
    public static int parcelasRestantes(Clube c) {
        if (c.divida <= 0) return 0;
        return c.dividaParcelas > 0 ? c.dividaParcelas : PRAZO_EMPRESTIMO_MESES;
    }

    /** Amortização da próxima parcela (sem os juros): dívida ÷ parcelas que faltam. */
    public static long amortizacaoParcela(Clube c) {
        int n = parcelasRestantes(c);
        if (n <= 0) return 0;
        return n == 1 ? c.divida : Math.min(c.divida, (c.divida + n - 1) / n);
    }

    /** Uma parcela do cronograma: semana absoluta do vencimento, amortização, juros e o saldo depois. */
    public record Parcela(int semana, long amortizacao, long juros, long saldoDepois) {
        public long total() { return amortizacao + juros; }
    }

    /**
     * Próxima semana de fechamento do mês (salários, juros e parcela): semanas 4, 8, ..., 52 do ano.
     * A semana atual ainda não foi jogada, então conta (o fechamento acontece ao avançar dela).
     */
    public static int proximoFechamento(int semanaAbsoluta) {
        int a = semanaAbsoluta;
        while (Calendario.semana(a) % 4 != 0) a++;
        return a;
    }

    /** Cronograma das parcelas que faltam, a partir da semana atual (sem novos pagamentos extras). */
    public static java.util.List<Parcela> cronograma(Clube c, int semanaAbsoluta) {
        java.util.List<Parcela> l = new java.util.ArrayList<>();
        long saldo = c.divida;
        int n = parcelasRestantes(c);
        int sem = semanaAbsoluta - 1;
        while (saldo > 0 && n > 0) {
            sem = proximoFechamento(sem + 1);
            long juros = Math.round(saldo * JUROS_MES);
            long amort = n == 1 ? saldo : Math.min(saldo, (saldo + n - 1) / n);
            saldo -= amort;
            n--;
            l.add(new Parcela(sem, amort, juros, saldo));
        }
        return l;
    }

    /**
     * Fechamento do mês do empréstimo do humano (os juros já foram cobrados): paga a parcela de
     * amortização com o que tiver em caixa. Sem caixa para a parcela inteira, paga o que der e a
     * parcela que falta fica para o mês seguinte (o prazo estica um mês). Devolve o valor pago.
     */
    public static long pagarParcela(Clube c) {
        if (c.divida <= 0) {
            c.dividaParcelas = 0;
            return 0;
        }
        if (c.dividaParcelas <= 0) c.dividaParcelas = PRAZO_EMPRESTIMO_MESES;
        long parcela = amortizacaoParcela(c);
        long pago = Math.max(0, Math.min(parcela, c.dinheiro));
        c.divida -= pago;
        c.dinheiro -= pago;
        if (pago >= parcela) c.dividaParcelas = Math.max(0, c.dividaParcelas - 1);
        if (c.divida <= 0) {
            c.divida = 0;
            c.dividaParcelas = 0;
        } else if (c.dividaParcelas == 0) c.dividaParcelas = 1;
        return pago;
    }

    /** Custo e prazo (semanas) de uma ampliação de estádio. */
    public static long custoObra(int lugares, int ano) {
        return arred(lugares * (double) Balanceamento.CUSTO_POR_LUGAR * fatorEpoca(ano));
    }

    public static int semanasObra(int lugares) {
        return Math.max(4, (int) Math.ceil(lugares / 1000.0 * Balanceamento.SEMANAS_OBRA_POR_MIL));
    }

    public static long folhaMensal(Iterable<Jogador> elenco) {
        long s = 0;
        for (Jogador j : elenco) if (j.contrato != null) s += j.contrato.salario;
        return s;
    }

    public static long arred(double v) {
        if (v >= 1_000_000) return Math.round(v / 10_000) * 10_000;
        if (v >= 10_000) return Math.round(v / 1_000) * 1_000;
        return Math.round(v / 100) * 100;
    }

    public static String formatar(long v) {
        if (Math.abs(v) >= 1_000_000_000) return String.format("R$ %.2f bi", v / 1e9).replace('.', ',');
        if (Math.abs(v) >= 1_000_000) return String.format("R$ %.1f mi", v / 1e6).replace('.', ',');
        if (Math.abs(v) >= 1_000) return String.format("R$ %d mil", v / 1000);
        return "R$ " + v;
    }
}
