package formula;

import javax.swing.SwingUtilities;

/** Abre a tela gráfica (Swing) com persistência em SQLite. */
public class AppSwing {
    public static void main(String[] args) {
        Repositorio repo = new Repositorio("formulas.db");
        Exemplos.semear(repo);
        SwingUtilities.invokeLater(() -> new TelaPrincipal(repo).setVisible(true));
    }
}
