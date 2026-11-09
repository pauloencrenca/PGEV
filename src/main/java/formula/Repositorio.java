package formula;

import java.sql.*;
import java.util.*;

/** SQLite: relações por IDs, com migração de bases antigas que usavam nome do pai. */
public class Repositorio {
    private final String url;
    public Repositorio(String arquivo) {
        url = "jdbc:sqlite:" + arquivo;
        try { Class.forName("org.sqlite.JDBC"); }
        catch (ClassNotFoundException e) { throw new IllegalStateException("Driver SQLite não encontrado. Use Maven.", e); }
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS item (id INTEGER PRIMARY KEY AUTOINCREMENT, produto TEXT NOT NULL, nome TEXT NOT NULL, pai TEXT, qtd INTEGER NOT NULL, valor REAL NOT NULL, pai_id INTEGER)");
            boolean temPaiId = false;
            try (ResultSet rs = s.executeQuery("PRAGMA table_info(item)")) {
                while (rs.next()) if ("pai_id".equals(rs.getString("name"))) temPaiId = true;
            }
            if (!temPaiId) s.execute("ALTER TABLE item ADD COLUMN pai_id INTEGER");
            migrar(c);
            s.execute("CREATE INDEX IF NOT EXISTS idx_item_produto ON item(produto)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_item_pai_id ON item(pai_id)");
        } catch (SQLException e) { throw new RuntimeException("Falha ao iniciar banco: " + e.getMessage(), e); }
    }
    private Connection conn() throws SQLException { return DriverManager.getConnection(url); }

    private void migrar(Connection c) throws SQLException {
        // Para registros legados, só converte nomes com pai único; ambiguidade exige correção manual.
        try (Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE item SET pai_id = (SELECT p.id FROM item p WHERE p.produto = item.produto AND p.nome = item.pai) " +
                "WHERE pai IS NOT NULL AND pai_id IS NULL AND " +
                "(SELECT COUNT(*) FROM item p WHERE p.produto = item.produto AND p.nome = item.pai) = 1");
        }
    }
    public List<String> produtos() {
        List<String> r = new ArrayList<>();
        try (Connection c = conn(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT DISTINCT produto FROM item ORDER BY produto")) {
            while (rs.next()) r.add(rs.getString(1));
        } catch (SQLException e) { throw new RuntimeException(e); }
        return r;
    }
    public List<ItemFormula> itens(String produto) {
        List<ItemFormula> r = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT id,nome,pai,qtd,valor,pai_id FROM item WHERE produto=? ORDER BY id")) {
            ps.setString(1, produto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemFormula it = new ItemFormula(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getDouble(5));
                    long pid = rs.getLong(6);
                    if (!rs.wasNull()) it.paiId = pid;
                    it.produto = produto;
                    r.add(it);
                }
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return r;
    }
    public ItemFormula buscarItem(long id) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("SELECT produto,nome,pai,qtd,valor,pai_id FROM item WHERE id=?")) {
            ps.setLong(1,id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                ItemFormula it = new ItemFormula(id,rs.getString(2),rs.getString(3),rs.getInt(4),rs.getDouble(5));
                it.produto = rs.getString(1);
                long pid = rs.getLong(6); if (!rs.wasNull()) it.paiId = pid;
                return it;
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    private static void validar(String produto,String nome,int qtd,double valor) {
        if (produto == null || produto.isBlank()) throw new IllegalArgumentException("Informe o produto.");
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Informe o nome do item.");
        if (qtd <= 0) throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        if (!Double.isFinite(valor) || valor < 0) throw new IllegalArgumentException("O valor deve ser finito e não negativo.");
    }
    public long inserir(String produto, ItemFormula it) {
        validar(produto,it.nome,it.qtd,it.valor);
        produto = produto.trim();
        List<ItemFormula> atuais = itens(produto);
        Long paiId = it.paiId;
        String paiNome = it.pai;
        if (paiId == null && paiNome == null) {
            if (!atuais.isEmpty()) throw new IllegalArgumentException("O produto já possui item raiz.");
        } else if (paiId != null) {
            ItemFormula pai = buscarItem(paiId);
            if (pai == null || !pai.produto.equals(produto)) throw new IllegalArgumentException("ID de pai não encontrado neste produto.");
            paiNome = pai.nome;
        } else {
            final String nomeBuscado = paiNome;
            List<ItemFormula> encontrados = atuais.stream().filter(x -> x.nome.equals(nomeBuscado)).toList();
            if (encontrados.size() != 1) throw new IllegalArgumentException("Pai inexistente ou ambíguo: " + paiNome);
            paiId = encontrados.get(0).id;
        }
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("INSERT INTO item(produto,nome,pai,qtd,valor,pai_id) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1,produto); ps.setString(2,it.nome.trim()); ps.setString(3,paiNome);
            ps.setInt(4,it.qtd); ps.setDouble(5,it.valor);
            if (paiId == null) ps.setNull(6,Types.INTEGER); else ps.setLong(6,paiId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
            throw new SQLException("Não foi possível obter o ID criado.");
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    public void cadastrarFormula(String produto,List<ItemFormula> itens) {
        if (!itens(produto).isEmpty()) throw new IllegalArgumentException("Produto já cadastrado.");
        // Dados de exemplo seguem a ordem pais antes de filhos.
        List<Long> criados = new ArrayList<>();
        try {
            for (ItemFormula it : itens) criados.add(inserir(produto,it));
        } catch (RuntimeException e) {
            for (int i=criados.size()-1;i>=0;i--) excluir(criados.get(i));
            throw e;
        }
    }
    public void atualizar(long id,String nome,int qtd,double valor) {
        ItemFormula antigo = buscarItem(id);
        if (antigo == null) throw new IllegalArgumentException("Item não encontrado.");
        validar(antigo.produto,nome,qtd,valor);
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("UPDATE item SET nome=?,qtd=?,valor=? WHERE id=?")) {
                    ps.setString(1,nome.trim());ps.setInt(2,qtd);ps.setDouble(3,valor);ps.setLong(4,id);ps.executeUpdate();
                }
                // Apenas filhos deste ID recebem o novo nome descritivo do pai.
                try (PreparedStatement ps = c.prepareStatement("UPDATE item SET pai=? WHERE produto=? AND pai_id=?")) {
                    ps.setString(1,nome.trim());ps.setString(2,antigo.produto);ps.setLong(3,id);ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    public void excluir(long id) {
        ItemFormula it = buscarItem(id);
        if (it == null) return;
        TreeNode raiz = ArvoreFormula.buildTree(itens(it.produto));
        TreeNode alvo = ArvoreFormula.buscarPorId(raiz,id);
        if (alvo == null) throw new IllegalStateException("Item fora da árvore.");
        List<Long> ids = new ArrayList<>(); coletar(alvo,ids);
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM item WHERE id=?")) {
                for (long x:ids) { ps.setLong(1,x);ps.executeUpdate(); }
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
    private static void coletar(TreeNode n,List<Long> ids) { ids.add(n.id);for(TreeNode f:n.filhos) coletar(f,ids); }
    public void excluirProduto(String produto) {
        try (Connection c=conn();PreparedStatement ps=c.prepareStatement("DELETE FROM item WHERE produto=?")) {
            ps.setString(1,produto);ps.executeUpdate();
        } catch(SQLException e) { throw new RuntimeException(e); }
    }
}
