package br.futebas.mundo;

/** Receitas e despesas da temporada atual e da anterior (Brasfoot: 4 receitas + salários). */
public class FinancasClube {
    public long ingressos, tvPatrocinio, vendas, premios, outros;
    public long salarios, compras, juros, obras;
    public long saldoInicio;

    public FinancasClube() {}

    public long receitas() { return ingressos + tvPatrocinio + vendas + premios + outros; }

    public long despesas() { return salarios + compras + juros + obras; }

    public FinancasClube copia() {
        FinancasClube f = new FinancasClube();
        f.ingressos = ingressos; f.tvPatrocinio = tvPatrocinio; f.vendas = vendas; f.premios = premios; f.outros = outros;
        f.salarios = salarios; f.compras = compras; f.juros = juros; f.obras = obras; f.saldoInicio = saldoInicio;
        return f;
    }
}
