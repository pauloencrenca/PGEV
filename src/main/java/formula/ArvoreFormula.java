package formula;

import java.util.*;

/** Operações sobre a árvore: montagem, explosão, implosão e necessidade de materiais. */
public final class ArvoreFormula {
    private ArvoreFormula() {}

    /** Linha do relatório de produção. */
    public static class LinhaMaterial {
        public final String nome;
        public final long qtd;
        public final double unitario;
        public final double subtotal;

        public LinhaMaterial(String nome, long qtd, double unitario, double subtotal) {
            this.nome = nome;
            this.qtd = qtd;
            this.unitario = unitario;
            this.subtotal = subtotal;
        }
    }

    private static class Acum {
        String nome;
        double unit;
        long qtd;
    }

    // ---------------- Montagem ----------------

    public static TreeNode buildTree(List<ItemFormula> itens) {
        Map<String, List<TreeNode>> porNome = new HashMap<>();
        Map<Long, TreeNode> porId = new HashMap<>();
        List<TreeNode> nos = new ArrayList<>();
        TreeNode raiz = null;
        for (ItemFormula it : itens) {
            if (it.qtd <= 0 || !Double.isFinite(it.valor) || it.valor < 0)
                throw new IllegalArgumentException("Quantidade ou preço inválido: " + it.nome);
            TreeNode n = new TreeNode(it.id, it.nome, it.qtd, it.valor);
            nos.add(n);
            porNome.computeIfAbsent(it.nome, k -> new ArrayList<>()).add(n);
            if (it.id > 0 && porId.put(it.id, n) != null)
                throw new IllegalArgumentException("ID duplicado: " + it.id);
            if (it.pai == null && it.paiId == null) {
                if (raiz != null) throw new IllegalArgumentException("A fórmula tem mais de uma raiz.");
                raiz = n;
            }
        }
        if (raiz == null) throw new IllegalArgumentException("A fórmula não tem raiz.");
        for (int i = 0; i < itens.size(); i++) {
            ItemFormula it = itens.get(i);
            if (it.pai == null && it.paiId == null) continue;
            TreeNode pai;
            if (it.paiId != null) {
                pai = porId.get(it.paiId);
            } else {
                List<TreeNode> candidatos = porNome.getOrDefault(it.pai, Collections.emptyList());
                if (candidatos.size() > 1) throw new IllegalArgumentException("Pai ambíguo: " + it.pai);
                pai = candidatos.isEmpty() ? null : candidatos.get(0);
            }
            if (pai == null) throw new IllegalArgumentException("Pai não encontrado: " + it.pai);
            if (pai == nos.get(i)) throw new IllegalArgumentException("Um item não pode ser seu próprio pai.");
            nos.get(i).pai = pai;
            pai.filhos.add(nos.get(i));
        }
        Set<TreeNode> visitados = Collections.newSetFromMap(new IdentityHashMap<>());
        validarEstrutura(raiz, visitados);
        if (visitados.size() != nos.size()) throw new IllegalArgumentException("Árvore desconectada ou com ciclo.");
        implodir(raiz);
        return raiz;
    }

    private static void validarEstrutura(TreeNode n, Set<TreeNode> visitados) {
        if (!visitados.add(n)) throw new IllegalArgumentException("Ciclo detectado na fórmula.");
        for (TreeNode filho : n.filhos) validarEstrutura(filho, visitados);
    }

    // ---------------- Implosão ----------------

    /** Recalcula o custo de baixo para cima e devolve o custo total do nó. */
    public static double implodir(TreeNode n) {
        double soma = n.valor;
        for (TreeNode f : n.filhos) soma += implodir(f) * f.qtd;
        n.custoTotal = soma;
        return soma;
    }

    public static double calcularTotal(TreeNode raiz) {
        return implodir(raiz);
    }

    /** Atualiza o valor de todo componente com esse nome e reimplode o caminho até a raiz. */
    public static int atualizarValor(TreeNode raiz, String nome, double novoValor) {
        if (!Double.isFinite(novoValor) || novoValor < 0) throw new IllegalArgumentException("Preço inválido.");
        int alterados = 0;
        Deque<TreeNode> pilha = new ArrayDeque<>();
        pilha.push(raiz);
        while (!pilha.isEmpty()) {
            TreeNode n = pilha.pop();
            if (n.nome.equalsIgnoreCase(nome)) {
                n.valor = novoValor;
                alterados++;
                for (TreeNode a = n; a != null; a = a.pai) {
                    double s = a.valor;
                    for (TreeNode f : a.filhos) s += f.custoTotal * f.qtd;
                    a.custoTotal = s;
                }
            }
            pilha.addAll(n.filhos);
        }
        if (alterados > 0) implodir(raiz);
        return alterados;
    }

    public static TreeNode buscarPorId(TreeNode n, long id) {
        if (n.id == id) return n;
        for (TreeNode f : n.filhos) {
            TreeNode r = buscarPorId(f, id);
            if (r != null) return r;
        }
        return null;
    }

    // ---------------- Explosão ----------------

    public static void printTree(TreeNode n, int nivel) {
        System.out.println("   ".repeat(nivel) + (nivel > 0 ? "└─ " : "") + n);
        for (TreeNode f : n.filhos) printTree(f, nivel + 1);
    }

    /** Explosão detalhada, com a quantidade acumulada para produzir 1 unidade do produto. */
    public static String explosao(TreeNode raiz) {
        StringBuilder sb = new StringBuilder();
        explosao(raiz, 0, 1, sb);
        return sb.toString();
    }

    private static void explosao(TreeNode n, int nivel, long acum, StringBuilder sb) {
        long q = Math.multiplyExact(acum, n.qtd);
        sb.append("   ".repeat(nivel)).append(nivel > 0 ? "└─ " : "")
          .append(String.format("%s | qtd por pai: %d | qtd p/ 1 produto: %d | próprio R$ %.2f | total R$ %.2f%n",
                  n.nome, n.qtd, q, n.valor, n.custoTotal));
        for (TreeNode f : n.filhos) explosao(f, nivel + 1, q, sb);
    }

    // ---------------- Necessidade de materiais ----------------

    public static List<LinhaMaterial> necessidade(TreeNode raiz, long unidades) {
        if (unidades <= 0) throw new IllegalArgumentException("Quantidade de produtos deve ser positiva.");
        Map<String, Acum> m = new LinkedHashMap<>();
        acumular(raiz, unidades, m);
        List<LinhaMaterial> r = new ArrayList<>();
        for (Acum a : m.values()) r.add(new LinhaMaterial(a.nome, a.qtd, a.unit, a.qtd * a.unit));
        return r;
    }

    private static void acumular(TreeNode n, long mult, Map<String, Acum> m) {
        long q = Math.multiplyExact(mult, n.qtd);
        String chave = n.nome + "|" + n.valor;   // mesmo nome com preços diferentes fica em linhas separadas
        Acum a = m.get(chave);
        if (a == null) {
            a = new Acum();
            a.nome = n.nome;
            a.unit = n.valor;
            m.put(chave, a);
        }
        a.qtd = Math.addExact(a.qtd, q);
        for (TreeNode f : n.filhos) acumular(f, q, m);
    }

    public static double custoTotal(List<LinhaMaterial> linhas) {
        double s = 0;
        for (LinhaMaterial l : linhas) s += l.subtotal;
        return s;
    }
}
