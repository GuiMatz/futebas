package br.futebas.mundo;

import br.futebas.Balanceamento;

/**
 * Evolução dos jogadores: curva de idade (sobe até ~27, platô até ~30, cai a partir de 31-32,
 * cada vez mais rápido, como no Brasfoot), potencial escondido, minutos, treino e estrutura.
 */
public final class Evolucao {
    private Evolucao() {}

    /** Fração do potencial que o jogador costuma ter em cada idade. */
    public static double curva(double idade) {
        double[] t = {
            0.62, // 15
            0.66, 0.70, 0.74, 0.78, 0.82, // 16-20
            0.86, 0.90, 0.93, 0.95, 0.97, // 21-25
            0.99, 1.00, 1.00, 1.00, 1.00, // 26-30
            0.985, 0.97, 0.95, 0.925, 0.895, // 31-35
            0.86, 0.82, 0.78, 0.74, 0.70, 0.66 // 36-41
        };
        if (idade <= 15) return t[0];
        if (idade >= 41) return t[t.length - 1];
        int i = (int) Math.floor(idade) - 15;
        double fr = idade - Math.floor(idade);
        return t[i] + (t[Math.min(t.length - 1, i + 1)] - t[i]) * fr;
    }

    /** Potencial escondido a partir do auge real: uma distribuição em volta do potencial_ref. */
    public static double sortearPotencial(double potencialRef, int idade, Rng rng) {
        double desvio = idade <= 19 ? 5.0 : idade <= 23 ? 3.5 : 2.0;
        return Math.max(20, Math.min(99, rng.gauss(potencialRef, desvio)));
    }

    /** Força inicial de quem entra no mundo com {@code idade} anos. */
    public static double forcaInicial(double potencial, int idade, Rng rng) {
        return Math.max(10, Math.min(99, potencial * curva(idade) * (1 + rng.gauss(0, 0.03))));
    }

    /** Perfil inicial de atributos (forma do jogador), ajustado para dar a força pedida. */
    public static void criarAtributos(Jogador j, double forca, Rng rng) {
        double[] p = j.posicao.pesosAtributos();
        for (int i = 0; i < 7; i++) {
            double base = p[i] > 0 ? forca + rng.gauss(0, 4) : forca * 0.6 + rng.gauss(0, 8);
            if (i == 0 && j.posicao != Posicao.GOL) base = Math.min(base, 25 + rng.gauss(0, 5));
            j.atributos[i] = Math.max(1, Math.min(99, base));
        }
        j.definirForca(forca);
    }

    /**
     * Evolução de meio ano (chamada duas vezes por temporada).
     *
     * @param minutosFrac fração dos minutos possíveis que ele jogou no período (0..1)
     * @param estrutura   estrutura do clube (0..100)
     */
    public static void evoluir(Jogador j, int ano, double minutosFrac, double estrutura, Rng rng) {
        evoluir(j, ano, minutosFrac, estrutura, 99, false, rng);
    }

    /**
     * Teto do clube (Brasfoot): tabela divisão × reputação, na escala de força do FUTÉBAS,
     * interpolada entre as faixas. Sem clube: o teto da 5ª linha com reputação 0.
     */
    public static double tetoClube(Clube c) {
        int div = c == null || c.divisao <= 0 ? 5 : Math.min(c.divisao, 5);
        double rep = c == null ? 0 : Math.max(0, Math.min(100, c.reputacao));
        int[] t = Balanceamento.TETO_CLUBE[div - 1];
        double x = Math.max(0, Math.min(t.length - 1, (rep - 8.5) / 17.0));
        int i = (int) Math.floor(x);
        int k = Math.min(t.length - 1, i + 1);
        return t[i] + (t[k] - t[i]) * (x - i);
    }

    /** Teto do jogador no clube: o do clube mais uma parte do talento (potencial acima do teto). */
    public static double tetoJogador(Jogador j, double tetoClube) {
        return tetoClube + Math.max(0, j.potencial - tetoClube) * Balanceamento.TETO_TALENTO;
    }

    /**
     * Evolução de meio ano com o teto do clube: acima do teto o jogador quase não cresce (precisa
     * de um clube maior). A partir dos 32 anos, declínio do Brasfoot: (idade − 31) × fator por
     * ano, maior para os mais fortes; clubes de elite seguram um pouco o declínio.
     */
    public static void evoluir(Jogador j, int ano, double minutosFrac, double estrutura, double tetoClube, boolean elite, Rng rng) {
        double idade = j.idade(ano) + 0.5;
        double alvo = j.potencial * curva(idade);
        double f = j.forca;
        double teto = tetoJogador(j, tetoClube);
        if (idade < 27) {
            // quanto mais perto do potencial, mais difícil subir; minutos e estrutura ajudam
            double ritmo = 0.25 + 0.30 * minutosFrac + 0.10 * estrutura / 100.0;
            if ("principais".equals(j.treino)) ritmo += 0.04;
            double delta = (alvo - f) * ritmo + rng.gauss(0, 0.8);
            if (alvo < f) delta = Math.max(delta, -0.5);
            f += freio(f, delta, teto);
            // quem não joga entre 18 e 23 anos pode se perder
            if (idade >= 18 && idade <= 23 && minutosFrac < 0.15) j.potencial -= rng.real(0, 1.2);
            if (idade <= 23 && minutosFrac > 0.6 && j.notaMediaTemp() >= 6.8) j.potencial += rng.real(0, 0.8);
            j.potencial = Math.min(j.potencial, Math.max(j.potencialRef + 6, j.forca + 2));
        } else if (idade < Balanceamento.IDADE_DECLINIO) {
            f += freio(f, (alvo - f) * 0.2 + rng.gauss(0, 0.6), teto);
        } else {
            double k = idade - (Balanceamento.IDADE_DECLINIO - 1) - (elite ? 1 : 0);
            double mult = f < 55 ? 0.7 : f < 70 ? 1.0 : 1.2;
            double queda = Math.max(0, k) * Balanceamento.DECLINIO_ANO * mult / 2; // meio ano
            if ("secundarias".equals(j.treino)) queda *= 0.85;
            f -= queda + Math.abs(rng.gauss(0, 0.3));
        }
        if (j.lesaoSemanas > 8) f -= rng.real(0, 1.0);
        j.definirForca(Math.max(8, Math.min(99, f)));
        if ("secundarias".equals(j.treino)) {
            // treino de habilidades secundárias: perfil mais completo
            double[] p = j.posicao.pesosAtributos();
            for (int i = 0; i < 7; i++) if (p[i] == 0) j.atributos[i] = Math.min(99, j.atributos[i] + 0.4);
        }
    }

    /** Crescimento acima do teto é freado (Brasfoot: estagna até ir para um clube maior). */
    static double freio(double forca, double delta, double teto) {
        if (delta <= 0) return delta;
        if (forca >= teto) return delta * Balanceamento.TETO_FREIO;
        double livre = teto - forca;
        return delta <= livre ? delta : livre + (delta - livre) * Balanceamento.TETO_FREIO;
    }

    /** Chance de aposentadoria ao fim da temporada. */
    public static boolean aposenta(Jogador j, int ano, boolean semClube, Rng rng) {
        int idade = j.idade(ano);
        if (idade >= 41) return true;
        if (idade < 33 && !(semClube && idade >= 30)) return false;
        double p = idade >= 34 ? 0.12 + 0.13 * (idade - 34) : 0.04;
        if (j.forca < 45) p += 0.15;
        if (semClube) p += 0.3;
        if (j.lesaoSemanas > 12) p += 0.15;
        return rng.chance(Math.min(0.95, p));
    }
}
