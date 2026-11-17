package br.futebas.mundo;

import java.util.List;

/**
 * Gerador de números aleatórios determinístico (SplitMix64). O estado é um único {@code long},
 * então ele é salvo junto com o mundo e a mesma semente sempre gera o mesmo mundo.
 */
public final class Rng {
    public long estado;

    public Rng(long semente) {
        this.estado = semente ^ 0x5DEECE66DL;
    }

    /** Gerador derivado, independente deste, para usos isolados (ex.: uma partida). */
    public Rng derivar(long sal) {
        return new Rng(proximoLong() ^ (sal * 0x9E3779B97F4A7C15L));
    }

    public long proximoLong() {
        long z = (estado += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Uniforme em [0, 1). */
    public double real() {
        return (proximoLong() >>> 11) * 0x1.0p-53;
    }

    public double real(double min, double max) {
        return min + (max - min) * real();
    }

    /** Inteiro em [0, n). */
    public int inteiro(int n) {
        if (n <= 1) return 0;
        return (int) Math.floorMod(proximoLong(), (long) n);
    }

    /** Inteiro em [min, max] (inclusive). */
    public int entre(int min, int max) {
        return min + inteiro(max - min + 1);
    }

    public boolean chance(double p) {
        return real() < p;
    }

    public double gauss() {
        double u1 = Math.max(1e-12, real());
        double u2 = real();
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    }

    public double gauss(double media, double desvio) {
        return media + desvio * gauss();
    }

    public int poisson(double lambda) {
        if (lambda <= 0) return 0;
        if (lambda > 30) return Math.max(0, (int) Math.round(gauss(lambda, Math.sqrt(lambda))));
        double l = Math.exp(-lambda), p = 1;
        int k = 0;
        do {
            k++;
            p *= real();
        } while (p > l);
        return k - 1;
    }

    /** Índice sorteado com pesos (pesos ≤ 0 nunca saem; todos zero = uniforme). */
    public int peso(double[] pesos) {
        double total = 0;
        for (double p : pesos) if (p > 0) total += p;
        if (total <= 0) return inteiro(pesos.length);
        double r = real() * total;
        for (int i = 0; i < pesos.length; i++) {
            if (pesos[i] <= 0) continue;
            r -= pesos[i];
            if (r < 0) return i;
        }
        for (int i = pesos.length - 1; i >= 0; i--) if (pesos[i] > 0) return i;
        return 0;
    }

    public <T> T escolher(List<T> lista) {
        return lista.get(inteiro(lista.size()));
    }

    public <T> void embaralhar(List<T> lista) {
        for (int i = lista.size() - 1; i > 0; i--) {
            int j = inteiro(i + 1);
            T t = lista.get(i);
            lista.set(i, lista.get(j));
            lista.set(j, t);
        }
    }
}
