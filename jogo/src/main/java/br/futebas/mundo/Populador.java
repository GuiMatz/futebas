package br.futebas.mundo;

import br.futebas.Balanceamento;
import br.futebas.dados.BancoMundo;
import br.futebas.dados.Db;
import br.futebas.financas.Financas;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quem entra no mundo e onde. Este é o único lugar que cria jogadores com nome.
 * <ul>
 *   <li>Em 1º/jan/2026: todos os jogadores do banco, no clube em que estão no banco
 *       ({@code clube_id}, contrato, empréstimo); sem clube = livre.</li>
 *   <li>A cada temporada: a peneira de cada clube revela juniores GERADOS, com prenome e
 *       sobrenome reais do país ({@code nomes_gerados}), marcados {@link Origem#GERADO}.</li>
 * </ul>
 */
public final class Populador {
    private final Mundo m;
    private final BancoMundo db;
    private final Rng rng;

    public Populador(Mundo m, BancoMundo db) {
        this.m = m;
        this.db = db;
        this.rng = m.rng;
    }

    // ================================================================= 1º de janeiro de 2026
    public void inicial() {
        int y = m.ano;
        for (Db.Jogador dj : db.jogadores.values()) {
            int idade = y - dj.anoNascimento();
            if (idade < 15 || idade > 45) continue;
            Jogador j = criar(dj, idade);
            Clube c = m.clube(dj.clubeId());
            if (c == null || !c.existe) {
                j.status = StatusJogador.LIVRE;
                continue;
            }
            boolean base = idade <= 17 && j.forca < c.nivel() - 8;
            entrarNoClube(j, c, base, fimDoContrato(dj.contratoAte(), c));
            Clube dono = m.clube(dj.emprestadoDe());
            if (!base && dono != null && dono != c) {
                Contrato k = new Contrato();
                k.tipo = TipoContrato.EMPRESTIMO;
                k.clubeOrigem = dono.id;
                k.salario = j.contrato.salario;
                k.fim = fimDeTemporada(c, j.contrato.fim);
                k.multa = j.contrato.multa;
                j.contrato = k;
            }
        }
    }

    private Jogador criar(Db.Jogador dj, int idade) {
        Jogador j = m.novoJogador();
        j.dbId = dj.id();
        if (dj.valorMercado() != null && dj.valorMercado() > 0) {
            j.valorReal = Math.round(dj.valorMercado() * Balanceamento.EURO_EM_REAIS);
            j.anoValorReal = m.ano;
        }
        j.nome = dj.nome();
        j.nomeConhecido = dj.nomeConhecido();
        j.anoNasc = dj.anoNascimento();
        j.diaNasc = dj.diaDoAno();
        j.pais = dj.pais();
        j.pais2 = dj.pais2();
        j.camisa = dj.camisa();
        j.posicao = Posicao.de(dj.posicao());
        for (String s : dj.posicoesSec()) {
            Posicao p = Posicao.de(s);
            if (p != j.posicao && !j.posicoesSec.contains(p)) j.posicoesSec.add(p);
        }
        for (String s : dj.caracteristicas()) if (j.caracteristicas.size() < 2 && s != null && !s.isBlank()) j.caracteristicas.add(s);
        j.moral = moralInicial(rng);
        j.pe = dj.pe();
        j.origem = dj.real() ? Origem.REAL : Origem.GERADO;
        j.potencialRef = dj.potencial();
        j.potencial = Math.max(dj.forca(), Math.min(99, dj.potencial() + (idade <= 23 ? rng.gauss(0, 2.0) : 0)));
        if (dj.atributos() != null) {
            for (int i = 0; i < 7; i++) j.atributos[i] = Math.max(1, Math.min(99, dj.atributos()[i]));
            j.definirForca(dj.forca());
        } else {
            Evolucao.criarAtributos(j, dj.forca(), rng);
        }
        j.fama = Math.max(0, (dj.forca() - 70) * 3.0);
        j.estrela = dj.forca() >= 86;
        j.topMundial = dj.forca() >= 89;
        m.porDbId.put(dj.id(), j.id);
        return j;
    }

    /** Semana absoluta do fim do contrato do banco; sem data, 1 a 3 temporadas. */
    private int fimDoContrato(String data, Clube c) {
        LocalDate d = Db.data(data);
        if (d != null) {
            int s = Calendario.absoluta(d);
            if (s > m.semanaAbsoluta()) return s;
        }
        int anos = rng.entre(1, 3);
        return fimDeTemporada(c, Calendario.absoluta(m.ano + anos - 1, 52));
    }

    /** Ajusta uma semana para o fim da temporada do clube (dezembro ou junho). */
    static int fimDeTemporada(Clube c, int semanaAbs) {
        int ano = Calendario.ano(semanaAbs);
        if (c != null && "europeu".equals(c.calendario)) {
            int fim = Calendario.absoluta(ano, Balanceamento.SEMANA_VIRADA_EUROPEIA - 1);
            return fim >= semanaAbs - 26 ? fim : Calendario.absoluta(ano + 1, Balanceamento.SEMANA_VIRADA_EUROPEIA - 1);
        }
        return Calendario.absoluta(ano, 52);
    }

    void entrarNoClube(Jogador j, Clube c, boolean base, int fimContrato) {
        j.clubeId = c.id;
        if (base) {
            j.status = StatusJogador.BASE;
            Contrato k = new Contrato();
            k.tipo = TipoContrato.BASE;
            k.salario = Financas.arred(Financas.salarioMinimo(m.ano) * 0.4);
            k.fim = Calendario.absoluta(m.ano + 3, 52);
            j.contrato = k;
            c.base.add(j.id);
        } else {
            j.status = StatusJogador.PROFISSIONAL;
            j.contrato = Contrato.profissional(Financas.salarioPedido(j, c, m.ano), fimContrato, Financas.multaRescisoria(j, c, m.ano));
            c.elenco.add(j.id);
        }
    }

    /** Moral inicial: varia de jogador para jogador (média 65). */
    public static int moralInicial(Rng rng) {
        return (int) Math.max(35, Math.min(92, Math.round(rng.gauss(65, 10))));
    }

    // ================================================================= nomes gerados
    private Map<String, List<Db.Nome>[]> nomes;
    private Map<String, String> confDoPais;

    @SuppressWarnings("unchecked")
    private List<Db.Nome>[] nomesDe(String pais) {
        if (nomes == null) {
            nomes = new HashMap<>();
            for (Db.Nome n : db.nomes) {
                if (n.pais() == null || n.nome() == null || n.nome().isBlank()) continue;
                List<Db.Nome>[] par = nomes.computeIfAbsent(n.pais().toUpperCase(), k -> new List[]{new ArrayList<>(), new ArrayList<>()});
                if ("prenome".equalsIgnoreCase(n.tipo())) par[0].add(n);
                else if ("sobrenome".equalsIgnoreCase(n.tipo())) par[1].add(n);
            }
            nomes.values().removeIf(par -> par[0].isEmpty() || par[1].isEmpty());
            confDoPais = new HashMap<>();
            for (Db.Pais p : db.paises.values()) if (p.confederacao() != null) confDoPais.put(p.id(), p.confederacao());
        }
        List<Db.Nome>[] par = nomes.get(pais == null ? "" : pais.toUpperCase());
        if (par != null) return par;
        // país sem nomes no banco: nomes de um vizinho da mesma confederação (sempre nomes reais)
        String conf = confDoPais.get(pais);
        for (Map.Entry<String, List<Db.Nome>[]> e : new java.util.TreeMap<>(nomes).entrySet()) {
            if (conf != null && conf.equals(confDoPais.get(e.getKey()))) return e.getValue();
        }
        return nomes.isEmpty() ? null : new java.util.TreeMap<>(nomes).firstEntry().getValue();
    }

    /** Nome completo gerado (prenome + sobrenome reais do país); null se o banco não tem nomes. */
    public String nomeGerado(String pais) {
        List<Db.Nome>[] par = nomesDe(pais);
        if (par == null) return null;
        return sortearNome(par[0]) + " " + sortearNome(par[1]);
    }

    private String sortearNome(List<Db.Nome> l) {
        double[] p = new double[l.size()];
        for (int i = 0; i < p.length; i++) p[i] = Math.max(1, l.get(i).freq());
        return l.get(rng.peso(p)).nome();
    }

    private static final Posicao[] DISTRIBUICAO = {Posicao.GOL, Posicao.ZAG, Posicao.ZAG, Posicao.LD, Posicao.LE,
            Posicao.VOL, Posicao.MC, Posicao.MEI, Posicao.PE, Posicao.PD, Posicao.CA, Posicao.CA, Posicao.ZAG, Posicao.MC};

    /** Cria um júnior gerado (peneira). Retorna null se o banco não tem nenhum nome real. */
    public Jogador newgen(String pais, Clube clube, double potencialMedio) {
        List<Db.Nome>[] par = nomesDe(pais);
        if (par == null) {
            m.aviso("Sem nomes em nomes_gerados: júnior não criado.");
            return null;
        }
        String pre = sortearNome(par[0]);
        String sob = sortearNome(par[1]);
        Jogador j = m.novoJogador();
        j.nome = pre + " " + sob;
        j.nomeConhecido = rng.chance(0.5) ? pre : pre + " " + sob;
        j.origem = Origem.GERADO;
        j.moral = moralInicial(rng);
        j.pais = pais;
        j.uf = clube == null ? null : clube.uf;
        int idade = rng.entre(16, 17);
        j.anoNasc = m.ano - idade;
        j.diaNasc = rng.entre(1, 365);
        j.posicao = DISTRIBUICAO[rng.inteiro(DISTRIBUICAO.length)];
        // talento escondido 1–10 pela faixa do clube (Brasfoot F.a); o potencial segue o talento
        int talento = sortearTalento(clube);
        double pot = potencialMedio + (talento - 6) * Balanceamento.TALENTO_POTENCIAL + rng.gauss(0, 4) + bonusPais.getOrDefault(pais, 0.0);
        if (rng.chance(Balanceamento.PENEIRA_CRAQUE)) { // rara grande promessa
            pot += rng.real(Balanceamento.CRAQUE_BONUS_MIN, Balanceamento.CRAQUE_BONUS_MAX);
            talento = Math.max(talento, 9);
        }
        j.talento = talento;
        j.potencialRef = Math.max(25, Math.min(Balanceamento.POTENCIAL_MAX_PENEIRA, pot));
        j.potencial = j.potencialRef;
        Evolucao.criarAtributos(j, Evolucao.forcaInicial(j.potencial, idade, rng), rng);
        if (clube != null) entrarNoClube(j, clube, true, 0);
        else j.status = StatusJogador.LIVRE;
        m.historia.estat(m.ano).newgens++;
        return j;
    }

    /**
     * Reforço de emergência (substitui o antigo "amador em teste"): jogador GERADO comum, adulto
     * (18–30), com nome real do país do clube, força pelo nível do clube e contrato profissional
     * até o fim da temporada. Retorna null se o banco não tem nomes para o país.
     */
    public Jogador reforco(Clube c, Posicao p, int fimContrato) {
        List<Db.Nome>[] par = nomesDe(c.pais);
        if (par == null) return null;
        String pre = sortearNome(par[0]);
        String sob = sortearNome(par[1]);
        Jogador j = m.novoJogador();
        j.nome = pre + " " + sob;
        j.nomeConhecido = rng.chance(0.5) ? pre : pre + " " + sob;
        j.origem = Origem.GERADO;
        j.reforco = true;
        j.moral = moralInicial(rng);
        j.pais = c.pais;
        j.uf = c.uf;
        int idade = rng.entre(18, 30);
        j.anoNasc = m.ano - idade;
        j.diaNasc = rng.entre(1, 365);
        j.posicao = p;
        double f = Math.max(15, Math.min(70, c.nivel() - 10 + rng.gauss(0, 3)));
        j.talento = Math.max(1, Math.min(10, (int) Math.round(rng.gauss(4, 1.2))));
        j.potencial = idade <= 23 ? Math.min(Balanceamento.POTENCIAL_MAX_PENEIRA, f + rng.real(0, 6)) : f;
        j.potencialRef = j.potencial;
        Evolucao.criarAtributos(j, f, rng);
        j.clubeId = c.id;
        j.status = StatusJogador.PROFISSIONAL;
        j.contrato = Contrato.profissional(Financas.salarioMinimo(m.ano), fimContrato, Financas.multaRescisoria(j, c, m.ano));
        c.elenco.add(j.id);
        return j;
    }

    /** Talento 1–10 da peneira pela faixa do clube (Brasfoot: nível ≥ 19 / 15–18 / demais). */
    int sortearTalento(Clube c) {
        double rep = c == null ? 0 : c.reputacao;
        int[] pesos = rep >= 68 ? Balanceamento.TALENTO_ELITE : rep >= 50 ? Balanceamento.TALENTO_MEDIO : Balanceamento.TALENTO_OUTROS;
        double[] p = new double[pesos.length];
        for (int i = 0; i < p.length; i++) p[i] = pesos[i];
        return rng.peso(p) + 1;
    }

    /** Bônus de potencial das gerações novas por país (deriva das seleções), atualizado a cada peneira. */
    private final Map<String, Double> bonusPais = new HashMap<>();
    /** Países com falta de talento (para as academias dos clubes fortes recrutarem), com o peso. */
    private final List<String> paisesFalta = new ArrayList<>();
    private double[] pesosFalta = new double[0];

    /**
     * Gerações por nacionalidade: o déficit de cada seleção (força de referência − força atual dos
     * melhores do país) vira bônus (ou corte) no potencial dos juniores do país e peso no
     * recrutamento de juniores estrangeiros pelas academias dos clubes fortes.
     */
    private void atualizarTalentoPorPais() {
        bonusPais.clear();
        paisesFalta.clear();
        List<Double> pesos = new ArrayList<>();
        Map<String, Double> projetada = forcaProjetada();
        for (Selecao s : m.selecoes.values()) {
            double d = br.futebas.selecao.Selecoes.deficit(s);
            // o déficit desconta os jovens que já estão a caminho (potencial): sem isso a resposta
            // chegava ~8 anos depois e passava muito da referência (Irã 89,8 com referência 78)
            Double pr = projetada.get(s.pais);
            if (pr != null) d = Math.min(d, br.futebas.selecao.Selecoes.referencia(s) - pr);
            double b = Math.max(-Balanceamento.GERACAO_BONUS_MAX, Math.min(Balanceamento.GERACAO_BONUS_MAX, Balanceamento.GERACAO_K * d));
            bonusPais.put(s.pais, b);
            if (d > 1 && s.forcaReferencia >= Balanceamento.GERACAO_REF_MIN_ESTRANGEIRO) {
                paisesFalta.add(s.pais);
                pesos.add(d * (s.forcaReferencia - 50));
            }
        }
        pesosFalta = new double[pesos.size()];
        for (int i = 0; i < pesosFalta.length; i++) pesosFalta[i] = pesos.get(i);
    }

    /**
     * Força "projetada" de cada país: média dos 23 melhores valores entre a força dos jogadores de
     * até 30 anos e o potencial dos de até 23 (a geração que vai chegar à seleção).
     */
    private Map<String, Double> forcaProjetada() {
        Map<String, List<Double>> por = new HashMap<>();
        for (Jogador j : m.jogadores) {
            if (j.pais == null || j.amador() || (j.status != StatusJogador.PROFISSIONAL && j.status != StatusJogador.BASE)) continue;
            int idade = j.idade(m.ano);
            if (idade > 30) continue;
            double v = idade <= 23 ? Math.max(j.forca, j.potencial) : j.forca;
            por.computeIfAbsent(j.pais, k -> new ArrayList<>()).add(v);
        }
        Map<String, Double> r = new HashMap<>();
        for (Map.Entry<String, List<Double>> e : por.entrySet()) {
            List<Double> l = e.getValue();
            if (l.size() < 11) continue;
            l.sort(java.util.Collections.reverseOrder());
            double soma = 0;
            int n = Math.min(23, l.size());
            for (int i = 0; i < n; i++) soma += l.get(i);
            r.put(e.getKey(), soma / n);
        }
        return r;
    }

    /** Peneira de fim de temporada dos clubes ativos de um conjunto de países. */
    public int peneira(java.util.Set<String> paises, java.util.Set<String> ativos) {
        int n = 0;
        atualizarTalentoPorPais();
        for (Clube c : m.clubes.values()) {
            if (!c.existe || c.virtual || !paises.contains(c.pais)) continue;
            if (Sub20.temPeneiraDoTecnico(c)) continue; // o técnico tem a peneira anual dele (Sub20.peneiraDoTecnico)
            if (c.divisao == 0 && !ativos.contains(c.id)) continue;
            int tamanho = c.elenco.size() + c.base.size();
            int k = Math.min(4, rng.poisson(Balanceamento.PENEIRA_MEDIA * (0.7 + c.estrutura / 120.0)));
            if (tamanho < Balanceamento.ELENCO_ALVO) k += (Balanceamento.ELENCO_ALVO - tamanho + 2) / 4;
            Pais pa = m.pais(c.pais);
            if (pa != null && pa.detalhado && c.base.size() + k < Balanceamento.SUB20_BASE_MINIMA) {
                // todo clube dos países em detalhe tem sub-20: a peneira completa a base (Brasfoot: 3–5 por ano)
                k = Math.min(Balanceamento.SUB20_PENEIRA_MAX, Balanceamento.SUB20_BASE_MINIMA - c.base.size());
            }
            if (c.nivelInicial <= 0) c.nivelInicial = c.nivel(); // 1ª peneira: o nível de 2026
            double media = Balanceamento.potencialPeneira(Math.max(c.nivel(), c.nivelInicial), c.estrutura);
            for (int i = 0; i < k; i++) {
                String pais = c.pais;
                // academia de clube forte recruta fora: de preferência nos países com falta de talento
                if (c.reputacao >= Balanceamento.GERACAO_REP_ACADEMIA && !paisesFalta.isEmpty() && rng.chance(Balanceamento.GERACAO_ESTRANGEIRO))
                    pais = paisesFalta.get(rng.peso(pesosFalta));
                if (newgen(pais, c, media) != null) n++;
            }
        }
        return n;
    }
}
