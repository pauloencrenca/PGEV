package formula;

import java.util.List;

/** Testes 1 e 2 do enunciado em modo console (não usa banco de dados). */
public class TesteConsole {

    public static void main(String[] args) {
        // TESTE 1 - Caneta
        rodar("caneta", Exemplos.caneta(), 7.50, 100, "Pigmento", 0.60, 7.80);
        System.out.println("\n==========================================\n");
        // TESTE 2 - Computador
        rodar("computador", Exemplos.computador(), 8080.00, 10, "CPU", 1200.00, 8280.00);
    }

    static void rodar(String nome, List<ItemFormula> itens, double esperado, int unidades,
                      String componente, double novoPreco, double esperadoApos) {
        TreeNode raiz = ArvoreFormula.buildTree(itens);

        System.out.println("Árvore da fórmula do(a) " + nome + ":");
        ArvoreFormula.printTree(raiz, 0);

        double total = ArvoreFormula.calcularTotal(raiz);
        System.out.printf("%nValor total do(a) %s: R$ %.2f%n", nome, total);
        System.out.println(Math.abs(total - esperado) < 0.001 ? "[OK] total esperado" : "[FALHOU] esperado " + esperado);

        System.out.println("\nExplosão detalhada:");
        System.out.print(ArvoreFormula.explosao(raiz));

        List<ArvoreFormula.LinhaMaterial> linhas = ArvoreFormula.necessidade(raiz, unidades);
        System.out.println("\nPara produzir " + unidades + " unidade(s):");
        for (ArvoreFormula.LinhaMaterial l : linhas) {
            System.out.printf("  %-22s qtd %-6d unit R$ %8.2f  subtotal R$ %10.2f%n",
                    l.nome, l.qtd, l.unitario, l.subtotal);
        }
        double custo = ArvoreFormula.custoTotal(linhas);
        System.out.printf("  CUSTO DE AQUISIÇÃO TOTAL: R$ %.2f%n", custo);
        System.out.println(Math.abs(custo - esperado * unidades) < 0.001 ? "[OK] custo da produção" : "[FALHOU] custo da produção");

        ArvoreFormula.atualizarValor(raiz, componente, novoPreco);
        System.out.printf("%nApós atualizar %s para R$ %.2f (implosão): R$ %.2f%n", componente, novoPreco, raiz.custoTotal);
        System.out.println(Math.abs(raiz.custoTotal - esperadoApos) < 0.001 ? "[OK] implosão" : "[FALHOU] esperado " + esperadoApos);
    }
}
