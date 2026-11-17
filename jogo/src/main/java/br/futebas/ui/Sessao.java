package br.futebas.ui;

import br.futebas.Jogo;
import br.futebas.dados.BancoMundo;
import br.futebas.mundo.Clube;
import br.futebas.ui.modelo.Fluxo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/** O jogo aberto: o mundo, o laço do jogo e o nome do arquivo salvo. */
public final class Sessao {
    private static BancoMundo banco;

    public final Jogo jogo;
    public final Fluxo fluxo;
    public String nomeSave;
    /** Trava do mundo: quem avança o tempo numa thread segura isto; a tela também, para ler com segurança. */
    public final ReentrantLock trava = new ReentrantLock();

    /** Semana (absoluta) do último salvamento feito pelo técnico; -1 = nunca salvo. */
    private volatile int salvoEm;

    public Sessao(Jogo jogo, String nomeSave) {
        this.jogo = jogo;
        this.fluxo = new Fluxo(jogo);
        this.nomeSave = nomeSave;
        this.salvoEm = nomeSave != null ? jogo.mundo().semanaAbsoluta() : -1;
    }

    /** Há progresso desde o último "Salvar" (ou o jogo nunca foi salvo)? */
    public boolean naoSalvo() {
        return salvoEm != jogo.mundo().semanaAbsoluta();
    }

    /** Banco mestre (carregado uma vez). Chamar fora da thread da tela. */
    public static synchronized BancoMundo banco() {
        if (banco == null) banco = BancoMundo.carregar(Caminhos.banco());
        return banco;
    }

    public static synchronized boolean bancoCarregado() {
        return banco != null;
    }

    public static synchronized void usarBanco(BancoMundo b) {
        banco = b;
    }

    public String nomeSugerido() {
        Clube c = jogo.meuClube();
        var tk = c == null ? jogo.meuTimeKings() : null; // carreira da Kings: o nome do time
        String base = c != null ? c.nome : tk != null ? tk.nome : (jogo.soAssistindo() ? "Mundo" : "Carreira");
        return (base + " " + jogo.ano()).replaceAll("[\\\\/:*?\"<>|]", "");
    }

    public Path salvar() {
        if (nomeSave == null || nomeSave.isBlank()) nomeSave = nomeSugerido();
        Path p = jogo.salvar(nomeSave);
        br.futebas.ui.modelo.ResumoSave.de(jogo).gravar(p);
        salvoEm = jogo.mundo().semanaAbsoluta();
        return p;
    }

    /**
     * Salvamento automático (chamado pelo {@link Fluxo} no fim de cada semana, na thread do motor):
     * na frequência das Opções, em rodízio de 1 a 3 vagas "Nome (automático N)". Devolve o arquivo ou null.
     */
    public Path autoSalvarSeDevido(java.time.LocalDate antes, java.time.LocalDate depois, boolean novaTemporada) {
        if (!br.futebas.ui.modelo.AutoSalvamento.devido(Opcoes.autoSalvar(), antes, depois, novaTemporada)) return null;
        return autoSalvar(Opcoes.vagasAutomaticas());
    }

    /** Salva na próxima vaga automática (a vazia ou a mais antiga) e apaga as vagas acima de {@code vagas}. */
    public Path autoSalvar(int vagas) {
        String base = nomeSave == null || nomeSave.isBlank() ? nomeSugerido() : nomeSave;
        Path pasta = Jogo.pastaSaves();
        int max = Opcoes.VAGAS_MAX;
        long[] mod = new long[max];
        for (int v = 1; v <= max; v++) {
            Path f = pasta.resolve(br.futebas.ui.modelo.AutoSalvamento.nome(base, v) + br.futebas.mundo.Salvamento.EXTENSAO);
            try {
                mod[v - 1] = Files.isRegularFile(f) ? Files.getLastModifiedTime(f).toMillis() : 0;
            } catch (IOException e) {
                mod[v - 1] = 0;
            }
        }
        int vaga = br.futebas.ui.modelo.AutoSalvamento.escolherVaga(mod, vagas);
        Path p = jogo.salvar(br.futebas.ui.modelo.AutoSalvamento.nome(base, vaga));
        br.futebas.ui.modelo.ResumoSave.de(jogo).gravar(p);
        for (int v : br.futebas.ui.modelo.AutoSalvamento.sobrando(vagas, max)) {
            try {
                Path f = pasta.resolve(br.futebas.ui.modelo.AutoSalvamento.nome(base, v) + br.futebas.mundo.Salvamento.EXTENSAO);
                Files.deleteIfExists(f);
                Files.deleteIfExists(br.futebas.ui.modelo.ResumoSave.arquivo(f));
            } catch (IOException e) {
                // fica
            }
        }
        return p;
    }

    public record Save(String nome, Path arquivo, long modificado) {}

    /** Apaga o save e o resumo dele. Devolve false se o arquivo não pôde ser apagado. */
    public static boolean excluir(Save s) {
        try {
            Files.deleteIfExists(s.arquivo());
        } catch (IOException e) {
            return false;
        }
        try {
            Files.deleteIfExists(br.futebas.ui.modelo.ResumoSave.arquivo(s.arquivo()));
        } catch (IOException e) {
            // resumo órfão é ignorado na listagem
        }
        return true;
    }

    public static List<Save> saves() {
        List<Save> l = new ArrayList<>();
        Path pasta = Jogo.pastaSaves();
        if (!Files.isDirectory(pasta)) return l;
        try (Stream<Path> s = Files.list(pasta)) {
            s.filter(p -> p.getFileName().toString().endsWith(br.futebas.mundo.Salvamento.EXTENSAO)).forEach(p -> {
                String n = p.getFileName().toString();
                n = n.substring(0, n.length() - br.futebas.mundo.Salvamento.EXTENSAO.length());
                long t = 0;
                try {
                    t = Files.getLastModifiedTime(p).toMillis();
                } catch (IOException e) {
                    // sem data
                }
                l.add(new Save(n, p, t));
            });
        } catch (IOException e) {
            return l;
        }
        l.sort(Comparator.comparingLong((Save x) -> -x.modificado()));
        return l;
    }
}
