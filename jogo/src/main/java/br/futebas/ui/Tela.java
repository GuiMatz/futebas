package br.futebas.ui;

import javafx.scene.Parent;
import javafx.scene.input.KeyEvent;

/** Uma tela inteira da janela (início, central do técnico, partida...). */
public interface Tela {
    Parent raiz();

    /** Atalhos de teclado da tela. Retorna true se tratou a tecla. */
    default boolean tecla(KeyEvent e) { return false; }

    /** Chamado sempre que a tela aparece. */
    default void aoMostrar() {}
}
