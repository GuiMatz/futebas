package br.futebas.ui;

/**
 * Ponto de entrada do executável. Fica fora da classe {@code Application} para que o JavaFX
 * rode a partir do classpath (jar comum + jars do JavaFX), como no pacote do jpackage.
 */
public final class Lancador {
    private Lancador() {}

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        App.main(args);
    }
}
