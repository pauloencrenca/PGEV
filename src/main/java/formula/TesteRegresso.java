package formula;

import java.util.*;

/** Testes extras sem dependências: execute formula.TesteRegresso. */
public class TesteRegresso {
    private static void ok(boolean cond, String msg) { if (!cond) throw new AssertionError(msg); }
    public static void main(String[] args) {
        TreeNode caneta = ArvoreFormula.buildTree(Exemplos.caneta());
        ok(Math.abs(caneta.custoTotal - 7.50) < 0.001, "Total caneta");
        TreeNode pc = ArvoreFormula.buildTree(Exemplos.computador());
        ok(Math.abs(pc.custoTotal - 8080) < 0.001, "Total computador");
        ItemFormula raiz = new ItemFormula(1, "Produto", null, 1, 1);
        ItemFormula a = new ItemFormula(2, "Peça", "Produto", 2, 2); a.paiId = 1L;
        ItemFormula b = new ItemFormula(3, "Peça", "Produto", 1, 3); b.paiId = 1L;
        ItemFormula c = new ItemFormula(4, "Filho", "Peça", 1, 4); c.paiId = 3L;
        TreeNode arvore = ArvoreFormula.buildTree(Arrays.asList(raiz,a,b,c));
        ok(ArvoreFormula.buscarPorId(arvore,4).pai.id == 3, "Pais por ID");
        ok(Math.abs(arvore.custoTotal-12) < 0.001, "Custo árvore com nomes repetidos");
        try { ArvoreFormula.necessidade(arvore, Long.MAX_VALUE); throw new AssertionError("Overflow não detectado"); }
        catch (ArithmeticException esperado) { /* esperado quando há multiplicação de quantidades > 1 */ }
        System.out.println("Testes de regressão concluídos.");
    }
}
