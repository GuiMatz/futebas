package br.futebas;

import br.futebas.dados.BancoMundo;
import br.futebas.ferramentas.BancoFicticio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Banco fictício compartilhado pelos testes (criado uma vez por execução). */
public final class Fixture {
    private Fixture() {}

    private static BancoMundo banco;
    private static Path caminho;

    public static synchronized Path caminho() {
        if (caminho == null) {
            try {
                caminho = Files.createTempDirectory("futebas-teste").resolve("ficticio.sqlite");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            BancoFicticio.criar(caminho, 42);
        }
        return caminho;
    }

    public static synchronized BancoMundo banco() {
        if (banco == null) banco = BancoMundo.carregar(caminho());
        return banco;
    }
}
