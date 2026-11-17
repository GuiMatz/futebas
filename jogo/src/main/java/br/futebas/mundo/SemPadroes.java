package br.futebas.mundo;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.bind.TypeAdapters;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Save menor: nas classes que aparecem centenas de milhares de vezes (jogador, temporada da
 * carreira, contrato, partida, aposentado), os campos com o valor padrão do construtor não vão
 * para o arquivo — ao carregar, o construtor devolve o mesmo valor. A leitura é a normal do Gson.
 */
final class SemPadroes implements TypeAdapterFactory {
    private final Set<Class<?>> classes;

    SemPadroes(Set<Class<?>> classes) {
        this.classes = classes;
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!classes.contains(type.getRawType())) return null;
        TypeAdapter<T> del = gson.getDelegateAdapter(this, type);
        JsonObject padrao;
        try {
            @SuppressWarnings("unchecked")
            T novo = (T) type.getRawType().getDeclaredConstructor().newInstance();
            JsonElement e = del.toJsonTree(novo);
            padrao = e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (ReflectiveOperationException ex) {
            return null;
        }
        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T valor) throws IOException {
                if (valor == null) {
                    out.nullValue();
                    return;
                }
                JsonElement e = del.toJsonTree(valor);
                if (e.isJsonObject()) {
                    JsonObject o = e.getAsJsonObject();
                    List<String> tirar = new ArrayList<>();
                    for (Map.Entry<String, JsonElement> x : o.entrySet()) if (x.getValue().equals(padrao.get(x.getKey()))) tirar.add(x.getKey());
                    for (String k : tirar) o.remove(k);
                }
                TypeAdapters.JSON_ELEMENT.write(out, e);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                return del.read(in);
            }
        };
    }
}
