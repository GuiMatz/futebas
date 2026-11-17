package br.futebas.mundo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Jogo salvo: o {@link Mundo} inteiro em JSON compactado (gzip), em
 * {@code %USERPROFILE%\Documents\FUTEBAS\saves\<nome>.futebas.json.gz}.
 * O banco mestre não vai junto: ele é lido de novo ao carregar.
 */
public final class Salvamento {
    private Salvamento() {}

    public static final String EXTENSAO = ".futebas.json.gz";

    static Gson gson() {
        return new GsonBuilder().serializeSpecialFloatingPointValues()
                .registerTypeAdapterFactory(new Colunas(java.util.Set.of(Jogador.class, Arquivado.class, br.futebas.competicao.Partida.class), java.util.Set.of(Passagem.class), java.util.Map.of("Arquivado.temporadas", Arquivado.CAMPOS)))
                .registerTypeAdapterFactory(new SemPadroes(java.util.Set.of(Jogador.class, Passagem.class, Contrato.class, Arquivado.class,
                        br.futebas.competicao.Partida.class, Treinador.class)))
                .create();
    }

    /** gzip no nível máximo (o save é escrito poucas vezes; ~5% menor). */
    static final class Gzip9 extends GZIPOutputStream {
        Gzip9(java.io.OutputStream o) throws IOException {
            super(o, 1 << 16);
            def.setLevel(java.util.zip.Deflater.BEST_COMPRESSION);
        }
    }

    public static Path pastaPadrao() {
        String home = System.getProperty("user.home");
        return Paths.get(home, "Documents", "FUTEBAS", "saves");
    }

    public static Path salvar(Mundo m, Path arquivo) {
        try {
            if (arquivo.getParent() != null) Files.createDirectories(arquivo.getParent());
            m.arredondarParaSalvar();
            Path tmp = arquivo.resolveSibling(arquivo.getFileName() + ".tmp");
            var tirados = m.omitirNomesDeClube();
            try (Writer w = new BufferedWriter(new OutputStreamWriter(new Gzip9(Files.newOutputStream(tmp)), StandardCharsets.UTF_8))) {
                gson().toJson(m, w);
            } finally {
                Mundo.devolverNomes(tirados);
            }
            Files.move(tmp, arquivo, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return arquivo;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível salvar o jogo em " + arquivo + ": " + e.getMessage(), e);
        }
    }

    public static Mundo carregar(Path arquivo) {
        try (Reader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(arquivo)), StandardCharsets.UTF_8))) {
            Mundo m = gson().fromJson(r, Mundo.class);
            if (m == null) throw new IllegalStateException("Arquivo salvo vazio: " + arquivo);
            if (m.versao > Mundo.VERSAO) throw new IllegalStateException("Jogo salvo numa versão mais nova do jogo.");
            m.completarNomesDeClube();
            m.normalizarTecnicos();
            return m;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível carregar " + arquivo + ": " + e.getMessage(), e);
        }
    }
}
