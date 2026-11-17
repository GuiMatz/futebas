package br.futebas.mundo;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Save menor: as listas enormes (jogadores vivos, aposentados arquivados) vão "por coluna" —
 * {@code {"colunas":1,"n":N,"forca":[...N valores],"nome":[...],...}} em vez de N objetos. Valores
 * parecidos ficam lado a lado e o gzip comprime muito melhor, e os nomes dos campos não se repetem.
 * Uma lista dentro do elemento (a carreira, {@link Passagem}) vira {"len":[...],"cols":{...}} com
 * todas as temporadas de todos os jogadores juntas.
 * <p>Ler continua aceitando o formato antigo (lista de objetos): saves antigos carregam.
 */
final class Colunas implements TypeAdapterFactory {
    /** Elementos das listas gravadas por coluna. */
    private final Set<Class<?>> listas;
    /** Listas dentro dos elementos que são achatadas (todas as entradas juntas). */
    private final Set<Class<?>> aninhadas;

    /** Campos int[] com passo (ex.: {@code Arquivado.temporadas}, 6 inteiros por temporada): gravados de-intercalados. */
    private final Map<String, Integer> passos;

    Colunas(Set<Class<?>> listas, Set<Class<?>> aninhadas, Map<String, Integer> passos) {
        this.listas = listas;
        this.aninhadas = aninhadas;
        this.passos = passos;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        Class<?> el = elemento(type.getType());
        if (el == null || !listas.contains(el) || !List.class.isAssignableFrom(type.getRawType())) return null;
        TypeAdapter<T> del = gson.getDelegateAdapter(this, type);
        Tabela tab = new Tabela(gson, el);
        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T valor) throws IOException {
                if (valor == null) {
                    out.nullValue();
                    return;
                }
                tab.escrever(out, (List) valor);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() != JsonToken.BEGIN_OBJECT) return del.read(in);
                return (T) tab.ler(in);
            }
        };
    }

    private static Class<?> elemento(Type t) {
        if (!(t instanceof ParameterizedType p) || p.getActualTypeArguments().length != 1) return null;
        Type a = p.getActualTypeArguments()[0];
        return a instanceof Class<?> c ? c : null;
    }

    /** As colunas de uma classe. */
    private final class Tabela {
        final Class<?> classe;
        final List<Field> campos = new ArrayList<>();
        final Map<String, Field> porNome = new HashMap<>();
        final Map<Field, TypeAdapter<Object>> adaptadores = new HashMap<>();
        final Map<Field, Tabela> filhas = new HashMap<>();
        /** int[] e String[]: achatados ({"len":[...],"v":[...]}); texto igual ao anterior do mesmo elemento = 0. */
        final Set<Field> vetores = new java.util.HashSet<>();

        @SuppressWarnings("unchecked")
        Tabela(Gson gson, Class<?> classe) {
            this.classe = classe;
            for (Class<?> c = classe; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    int mod = f.getModifiers();
                    if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) continue;
                    f.setAccessible(true);
                    campos.add(f);
                    porNome.put(f.getName(), f);
                    Class<?> sub = List.class.isAssignableFrom(f.getType()) ? elemento(f.getGenericType()) : null;
                    if (sub != null && aninhadas.contains(sub)) filhas.put(f, new Tabela(gson, sub));
                    else if (f.getType() == int[].class || f.getType() == String[].class) vetores.add(f);
                    else adaptadores.put(f, (TypeAdapter<Object>) gson.getAdapter(TypeToken.get(f.getGenericType())));
                }
            }
        }

        void escrever(JsonWriter out, List<?> l) throws IOException {
            escrever(out, l, null);
        }

        /** {@code mesmo[i]}: o elemento i é do mesmo dono que o i-1 (texto repetido vira 0). */
        void escrever(JsonWriter out, List<?> l, boolean[] mesmo) throws IOException {
            out.beginObject();
            out.name("colunas").value(1);
            out.name("n").value(l.size());
            try {
                for (Field f : campos) {
                    out.name(f.getName());
                    Tabela filha = filhas.get(f);
                    if (filha != null) {
                        List<Object> todas = new ArrayList<>();
                        List<Boolean> grupo = new ArrayList<>();
                        out.beginObject().name("len").beginArray();
                        for (Object e : l) {
                            List<?> sub = (List<?>) f.get(e);
                            out.value(sub == null ? -1 : sub.size());
                            if (sub != null) {
                                for (int i = 0; i < sub.size(); i++) grupo.add(i > 0);
                                todas.addAll(sub);
                            }
                        }
                        boolean[] g = new boolean[grupo.size()];
                        for (int i = 0; i < g.length; i++) g[i] = grupo.get(i);
                        out.endArray().name("cols");
                        filha.escrever(out, todas, g);
                        out.endObject();
                        continue;
                    }
                    if (vetores.contains(f)) {
                        escreverVetores(out, l, f);
                        continue;
                    }
                    TypeAdapter<Object> a = adaptadores.get(f);
                    boolean texto = f.getType() == String.class && mesmo != null;
                    out.beginArray();
                    Object ant = null;
                    for (int i = 0; i < l.size(); i++) {
                        Object v = f.get(l.get(i));
                        if (texto && v != null && mesmo[i] && v.equals(ant)) out.value(0);
                        else a.write(out, v);
                        ant = v;
                    }
                    out.endArray();
                }
            } catch (IllegalAccessException ex) {
                throw new IOException(ex);
            }
            out.endObject();
        }

        void escreverVetores(JsonWriter out, List<?> l, Field f) throws IOException, IllegalAccessException {
            boolean inteiro = f.getType() == int[].class;
            int passo = inteiro ? passos.getOrDefault(classe.getSimpleName() + "." + f.getName(), 1) : 1;
            out.beginObject().name("len").beginArray();
            for (Object e : l) {
                Object v = f.get(e);
                out.value(v == null ? -1 : java.lang.reflect.Array.getLength(v));
            }
            out.endArray().name("passo").value(passo).name("v").beginArray();
            for (int c = 0; c < passo; c++)
                for (Object e : l) {
                    Object v = f.get(e);
                    if (v == null) continue;
                    if (inteiro) {
                        int[] x = (int[]) v;
                        for (int k = c; k < x.length; k += passo) out.value(x[k]);
                    } else {
                        String[] x = (String[]) v;
                        for (int k = 0; k < x.length; k++) {
                            if (k > 0 && x[k] != null && x[k].equals(x[k - 1])) out.value(0);
                            else out.value(x[k]);
                        }
                    }
                }
            out.endArray().endObject();
        }

        void lerVetores(JsonReader in, List<Object> l, Field f) throws IOException, IllegalAccessException {
            boolean inteiro = f.getType() == int[].class;
            int[] len = null;
            int passo = 1;
            in.beginObject();
            while (in.hasNext()) {
                String s = in.nextName();
                if (s.equals("len")) {
                    List<Integer> x = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) x.add(in.nextInt());
                    in.endArray();
                    len = x.stream().mapToInt(Integer::intValue).toArray();
                    for (int i = 0; i < len.length && i < l.size(); i++)
                        f.set(l.get(i), len[i] < 0 ? null : inteiro ? new int[len[i]] : new String[len[i]]);
                } else if (s.equals("passo")) passo = in.nextInt();
                else if (s.equals("v")) {
                    in.beginArray();
                    for (int c = 0; c < passo; c++)
                        for (int i = 0; len != null && i < len.length && i < l.size(); i++) {
                            Object v = f.get(l.get(i));
                            if (v == null) continue;
                            if (inteiro) {
                                int[] x = (int[]) v;
                                for (int k = c; k < x.length; k += passo) x[k] = in.nextInt();
                            } else {
                                String[] x = (String[]) v;
                                for (int k = 0; k < x.length; k++) {
                                    if (in.peek() == JsonToken.NULL) {
                                        in.nextNull();
                                        x[k] = null;
                                    } else if (in.peek() == JsonToken.NUMBER) {
                                        in.nextInt();
                                        x[k] = k > 0 ? x[k - 1] : null;
                                    } else x[k] = in.nextString();
                                }
                            }
                        }
                    while (in.hasNext()) in.skipValue();
                    in.endArray();
                } else in.skipValue();
            }
            in.endObject();
        }

        List<Object> ler(JsonReader in) throws IOException {
            List<Object> l = new ArrayList<>();
            try {
                in.beginObject();
                while (in.hasNext()) {
                    String nome = in.nextName();
                    if (nome.equals("colunas")) {
                        in.nextInt();
                        continue;
                    }
                    if (nome.equals("n")) {
                        int n = in.nextInt();
                        var k = classe.getDeclaredConstructor();
                        k.setAccessible(true);
                        for (int i = 0; i < n; i++) l.add(k.newInstance());
                        continue;
                    }
                    Field f = porNome.get(nome);
                    if (f == null) {
                        in.skipValue();
                        continue;
                    }
                    Tabela filha = filhas.get(f);
                    if (filha != null) {
                        int[] len = null;
                        List<Object> todas = null;
                        in.beginObject();
                        while (in.hasNext()) {
                            String s = in.nextName();
                            if (s.equals("len")) {
                                List<Integer> x = new ArrayList<>();
                                in.beginArray();
                                while (in.hasNext()) x.add(in.nextInt());
                                in.endArray();
                                len = x.stream().mapToInt(Integer::intValue).toArray();
                            } else if (s.equals("cols")) todas = filha.ler(in);
                            else in.skipValue();
                        }
                        in.endObject();
                        int pos = 0;
                        for (int i = 0; len != null && i < len.length && i < l.size(); i++) {
                            if (len[i] < 0) {
                                f.set(l.get(i), null);
                                continue;
                            }
                            List<Object> sub = new ArrayList<>(todas.subList(pos, pos + len[i]));
                            pos += len[i];
                            f.set(l.get(i), sub);
                        }
                        continue;
                    }
                    if (vetores.contains(f)) {
                        lerVetores(in, l, f);
                        continue;
                    }
                    TypeAdapter<Object> a = adaptadores.get(f);
                    boolean primitivo = f.getType().isPrimitive();
                    boolean texto = f.getType() == String.class;
                    in.beginArray();
                    for (int i = 0; in.hasNext(); i++) {
                        Object v;
                        if (texto && in.peek() == JsonToken.NUMBER) {
                            in.nextInt();
                            v = i > 0 && i - 1 < l.size() ? f.get(l.get(i - 1)) : null;
                        } else v = a.read(in);
                        if (i >= l.size() || (v == null && primitivo)) continue;
                        f.set(l.get(i), v);
                    }
                    in.endArray();
                }
                in.endObject();
            } catch (ReflectiveOperationException ex) {
                throw new IOException(ex);
            }
            return l;
        }
    }
}
