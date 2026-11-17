package br.futebas.ferramentas;

import br.futebas.dados.BancoMundo;
import br.futebas.mundo.Jogador;
import br.futebas.mundo.Mundo;
import br.futebas.mundo.Salvamento;
import br.futebas.mundo.Simulador;
import br.futebas.mundo.StatusJogador;
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Mede o tamanho do save (gzip) por campo do {@link Mundo} depois de simular até um ano.
 * <pre>uso: MedirSave [--ate 2036] [--semente 1000]</pre>
 */
public final class MedirSave {
    private MedirSave() {}

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        int ate = 2036;
        long semente = 1000;
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (args[i].equals("--ate")) ate = Integer.parseInt(args[i + 1]);
            if (args[i].equals("--semente")) semente = Long.parseLong(args[i + 1]);
        }
        BancoMundo db = BancoMundo.carregar(LongRun.acharRaiz().resolve("dados").resolve("mundo.sqlite"));
        Simulador s = Simulador.novo(db, semente, null, null);
        while (s.m.ano < ate) s.avancarTemporada();
        medir(s.m);
    }

    public static void medir(Mundo m) throws Exception {
        Gson g = gsonDoSave();
        m.arredondarParaSalvar();
        long total = 0;
        List<String> linhas = new ArrayList<>();
        for (Field f : Mundo.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())) continue;
            Object v = f.get(m);
            if (v == null) continue;
            long[] t = tamanho(g, v);
            total += t[1];
            linhas.add(String.format("  %-26s %8.2f MB json  %7.2f MB gz", f.getName(), t[0] / 1e6, t[1] / 1e6));
        }
        linhas.forEach(System.out::println);
        System.out.printf("  soma gz %.2f MB%n", total / 1e6);
        Map<StatusJogador, Integer> st = new EnumMap<>(StatusJogador.class);
        for (Jogador j : m.jogadores) st.merge(j.status, 1, Integer::sum);
        System.out.println("  status: " + st + " | arquivados " + m.arquivados.size());
        List<Object> carreiras = new ArrayList<>(), contratos = new ArrayList<>(), atributos = new ArrayList<>();
        for (Jogador j : m.jogadores) {
            carreiras.add(j.carreira);
            contratos.add(j.contrato);
            atributos.add(j.atributos);
        }
        System.out.printf("  jogadores.carreira %.2f MB gz | contrato %.2f | atributos %.2f%n", tamanho(g, carreiras)[1] / 1e6,
                tamanho(g, contratos)[1] / 1e6, tamanho(g, atributos)[1] / 1e6);
        colunas(g, "jogadores", m.jogadores);
        List<Object> pass = new ArrayList<>();
        for (Jogador j : m.jogadores) pass.addAll(j.carreira);
        colunas(g, "carreira", pass);
        colunas(g, "arquivados", new ArrayList<>(m.arquivados));
        for (int i = 0; i < 3 && i < m.jogadores.size(); i++) System.out.println("  ex.: " + g.toJson(m.jogadores.get(m.jogadores.size() / 3 * i)));
        if (!m.arquivados.isEmpty()) System.out.println("  arq.: " + g.toJson(m.arquivados.get(m.arquivados.size() / 2)));
        Path tmp = Files.createTempFile("medir", ".futebas.json.gz");
        long t0 = System.nanoTime();
        Salvamento.salvar(m, tmp);
        System.out.printf("  save real: %.2f MB em %.1f s%n", Files.size(tmp) / 1e6, (System.nanoTime() - t0) / 1e9);
        Files.deleteIfExists(tmp);
    }

    /** Tamanho gzip de cada coluna (como o save grava as listas grandes). */
    static void colunas(Gson g, String nome, List<?> l) throws Exception {
        if (l.isEmpty()) return;
        StringBuilder sb = new StringBuilder("  colunas " + nome + ":");
        for (Field f : l.get(0).getClass().getFields()) {
            if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())) continue;
            List<Object> v = new ArrayList<>();
            for (Object o : l) v.add(f.get(o));
            long gz = tamanho(g, v)[1];
            if (gz > 50_000) sb.append(String.format(" %s=%.2f", f.getName(), gz / 1e6));
        }
        System.out.println(sb);
    }

    static Gson gsonDoSave() throws Exception {
        java.lang.reflect.Method mt = Salvamento.class.getDeclaredMethod("gson");
        mt.setAccessible(true);
        return (Gson) mt.invoke(null);
    }

    static long[] tamanho(Gson g, Object v) throws IOException {
        Contador bruto = new Contador();
        try (Writer w = new OutputStreamWriter(bruto, StandardCharsets.UTF_8)) {
            g.toJson(v, w);
        }
        Contador gz = new Contador();
        try (Writer w = new OutputStreamWriter(new GZIPOutputStream(gz), StandardCharsets.UTF_8)) {
            g.toJson(v, w);
        }
        return new long[]{bruto.n, gz.n};
    }

    static final class Contador extends java.io.OutputStream {
        long n;

        @Override
        public void write(int b) { n++; }

        @Override
        public void write(byte[] b, int off, int len) { n += len; }
    }
}
