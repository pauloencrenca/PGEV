package formula;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

/** Tela gráfica: CRUD de produtos/componentes, explosão, produção e implosão. */
public class TelaPrincipal extends JFrame {
    private final Repositorio repo;
    private final JComboBox<String> cbProdutos = new JComboBox<>();
    private final JTree tree = new JTree(new DefaultMutableTreeNode("Nenhum produto"));
    private final JTextArea saida = new JTextArea(10, 60);
    private TreeNode raizAtual;
    private boolean carregando = false;

    public TelaPrincipal(Repositorio repo) {
        super("Fórmula de Produtos - Explosão e Implosão");
        this.repo = repo;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);

        saida.setEditable(false);
        saida.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel topo = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        topo.add(new JLabel("Produto:"));
        topo.add(cbProdutos);
        topo.add(botao("Novo produto", this::novoProduto));
        topo.add(botao("Excluir produto", this::excluirProduto));

        JPanel lateral = new JPanel(new GridLayout(0, 1, 5, 5));
        lateral.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        lateral.add(botao("Adicionar componente", this::adicionar));
        lateral.add(botao("Editar item", this::editar));
        lateral.add(botao("Atualizar preço (implosão)", this::atualizarPreco));
        lateral.add(botao("Excluir item", this::excluir));
        lateral.add(botao("Explosão detalhada", this::explosao));
        lateral.add(botao("Produzir N unidades", this::produzir));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tree), new JScrollPane(saida));
        split.setResizeWeight(0.65);

        add(topo, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(lateral, BorderLayout.EAST);

        cbProdutos.addActionListener(e -> {
            if (!carregando) executar(this::recarregarArvore);
        });
        recarregarProdutos(null);
    }

    // ---------------- Infraestrutura ----------------

    private JButton botao(String texto, Runnable acao) {
        JButton b = new JButton(texto);
        b.addActionListener(e -> executar(acao));
        return b;
    }

    private void executar(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            JOptionPane.showMessageDialog(this, msg, "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static double num(String s) {
        return Double.parseDouble(s.trim().replace(',', '.'));
    }

    private String produtoAtual() {
        Object o = cbProdutos.getSelectedItem();
        if (o == null) throw new IllegalStateException("Cadastre ou selecione um produto.");
        return o.toString();
    }

    private TreeNode selecionado() {
        TreePath tp = tree.getSelectionPath();
        if (tp != null) {
            Object o = ((DefaultMutableTreeNode) tp.getLastPathComponent()).getUserObject();
            if (o instanceof TreeNode) return (TreeNode) o;
        }
        throw new IllegalStateException("Selecione um item na árvore.");
    }

    private void exigirRaiz() {
        if (raizAtual == null) throw new IllegalStateException("Cadastre ou selecione um produto.");
    }

    private String[] dialogoItem(String titulo, String nome, int qtd, double valor) {
        JTextField fn = new JTextField(nome, 15);
        JTextField fq = new JTextField(String.valueOf(qtd));
        JTextField fv = new JTextField(String.valueOf(valor));
        JPanel p = new JPanel(new GridLayout(0, 2, 5, 5));
        p.add(new JLabel("Nome:"));
        p.add(fn);
        p.add(new JLabel("Quantidade:"));
        p.add(fq);
        p.add(new JLabel("Valor próprio (R$):"));
        p.add(fv);
        int r = JOptionPane.showConfirmDialog(this, p, titulo, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return null;
        return new String[]{fn.getText().trim(), fq.getText().trim(), fv.getText().trim()};
    }

    // ---------------- Carga da tela ----------------

    private void recarregarProdutos(String selecionar) {
        carregando = true;
        cbProdutos.removeAllItems();
        for (String p : repo.produtos()) cbProdutos.addItem(p);
        if (selecionar != null) cbProdutos.setSelectedItem(selecionar);
        carregando = false;
        recarregarArvore();
    }

    private void recarregarArvore() {
        Object o = cbProdutos.getSelectedItem();
        if (o == null) {
            raizAtual = null;
            tree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Nenhum produto")));
            return;
        }
        raizAtual = ArvoreFormula.buildTree(repo.itens(o.toString()));   // monta e implode
        tree.setModel(new DefaultTreeModel(converter(raizAtual)));
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private DefaultMutableTreeNode converter(TreeNode n) {
        DefaultMutableTreeNode d = new DefaultMutableTreeNode(n);
        for (TreeNode f : n.filhos) d.add(converter(f));
        return d;
    }

    // ---------------- Ações ----------------

    private void novoProduto() {
        String[] d = dialogoItem("Novo produto (item raiz)", "", 1, 0.0);
        if (d == null) return;
        repo.inserir(d[0], new ItemFormula(d[0], null, 1, num(d[2])));
        recarregarProdutos(d[0]);
    }

    private void excluirProduto() {
        String p = produtoAtual();
        if (JOptionPane.showConfirmDialog(this, "Excluir o produto '" + p + "' inteiro?", "Confirmar",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        repo.excluirProduto(p);
        recarregarProdutos(null);
    }

    private void adicionar() {
        TreeNode pai = selecionado();
        String[] d = dialogoItem("Novo componente de " + pai.nome, "", 1, 0.0);
        if (d == null) return;
        repo.inserir(produtoAtual(), novoFilho(d[0], pai.id, Integer.parseInt(d[1]), num(d[2])));
        recarregarArvore();
    }

    private void editar() {
        TreeNode n = selecionado();
        String[] d = dialogoItem("Editar " + n.nome, n.nome, n.qtd, n.valor);
        if (d == null) return;
        repo.atualizar(n.id, d[0], Integer.parseInt(d[1]), num(d[2]));
        recarregarArvore();
    }

    private static ItemFormula novoFilho(String nome, long paiId, int qtd, double valor) {
        ItemFormula it = new ItemFormula(nome, null, qtd, valor);
        it.paiId = paiId;
        return it;
    }

    private void atualizarPreco() {
        TreeNode n = selecionado();
        String s = JOptionPane.showInputDialog(this, "Novo valor de '" + n.nome + "' (R$):", String.valueOf(n.valor));
        if (s == null) return;
        double antes = raizAtual.custoTotal;
        double valorAntigo = n.valor;
        repo.atualizar(n.id, n.nome, n.qtd, num(s));
        recarregarArvore();   // reconstrói a árvore e reimplode os custos
        saida.setText(String.format("Preço de %s: R$ %.2f -> R$ %.2f%nImplosão: custo de %s passou de R$ %.2f para R$ %.2f%n",
                n.nome, valorAntigo, num(s), raizAtual.nome, antes, raizAtual.custoTotal));
    }

    private void excluir() {
        TreeNode n = selecionado();
        if (JOptionPane.showConfirmDialog(this, "Excluir '" + n.nome + "' e todos os seus componentes?",
                "Confirmar", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        boolean eraRaiz = n.pai == null;
        String produto = produtoAtual();
        repo.excluir(n.id);
        recarregarProdutos(eraRaiz ? null : produto);
    }

    private void explosao() {
        exigirRaiz();
        saida.setText(ArvoreFormula.explosao(raizAtual));
        saida.setCaretPosition(0);
    }

    private void produzir() {
        exigirRaiz();
        String s = JOptionPane.showInputDialog(this, "Quantas unidades de '" + raizAtual.nome + "' deseja produzir?", "100");
        if (s == null) return;
        long un = Long.parseLong(s.trim());
        if (un <= 0) throw new IllegalArgumentException("Informe uma quantidade maior que zero.");
        List<ArvoreFormula.LinhaMaterial> linhas = ArvoreFormula.necessidade(raizAtual, un);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Para produzir %d unidade(s) de %s:%n", un, raizAtual.nome));
        for (ArvoreFormula.LinhaMaterial l : linhas) {
            sb.append(String.format("  %-22s qtd %-8d unit R$ %9.2f  subtotal R$ %12.2f%n",
                    l.nome, l.qtd, l.unitario, l.subtotal));
        }
        sb.append(String.format("  CUSTO DE AQUISIÇÃO TOTAL: R$ %.2f%n", ArvoreFormula.custoTotal(linhas)));
        saida.setText(sb.toString());
        saida.setCaretPosition(0);
    }
}
