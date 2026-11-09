package formula;

import java.util.ArrayList;
import java.util.List;

/** Nó da árvore genérica: pode ter qualquer número de filhos. */
public class TreeNode {
    public long id;
    public String nome;
    public int qtd;             // quantidade deste item para UMA unidade do pai
    public double valor;        // custo próprio do item
    public double custoTotal;   // valor + soma(filho.custoTotal * filho.qtd)
    public TreeNode pai;
    public List<TreeNode> filhos = new ArrayList<>();

    public TreeNode(long id, String nome, int qtd, double valor) {
        this.id = id;
        this.nome = nome;
        this.qtd = qtd;
        this.valor = valor;
    }

    public TreeNode(String nome, int qtd, double valor) {
        this(0, nome, qtd, valor);
    }

    @Override
    public String toString() {
        return String.format("%s  [qtd %d | próprio R$ %.2f | total R$ %.2f]", nome, qtd, valor, custoTotal);
    }
}
