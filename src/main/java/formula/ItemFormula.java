package formula;

/** Uma linha da fórmula: item, nome do pai (null na raiz), quantidade e valor próprio. */
public class ItemFormula {
    public long id;          // preenchido quando vem do banco
    public String produto;   // preenchido quando vem do banco
    public String nome;
    public String pai;
    public Long paiId; // vínculo estável no banco; null para raiz ou exemplos
    public int qtd;
    public double valor;

    public ItemFormula(String nome, String pai, int qtd, double valor) {
        this(0, nome, pai, qtd, valor);
    }

    public ItemFormula(long id, String nome, String pai, int qtd, double valor) {
        this.id = id;
        this.nome = nome;
        this.pai = pai;
        this.qtd = qtd;
        this.valor = valor;
    }
}
