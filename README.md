# Projeto: Fórmula de Produtos (Árvore Genérica) — Explosão e Implosão

Java 25 LTS. Estrutura de arquivos:

| Arquivo | Função |
|---|---|
| `ItemFormula.java` | Linha da fórmula (nome, pai, quantidade, valor) |
| `TreeNode.java` | Nó da árvore genérica (lista de filhos) |
| `ArvoreFormula.java` | `buildTree`, implosão, atualização de preço, explosão, necessidade de materiais |
| `Exemplos.java` | Dados dos Testes 1 (caneta) e 2 (computador) |
| `TesteConsole.java` | Executa os Testes 1 e 2 no console (sem banco) |
| `Repositorio.java` | Persistência SQLite + CRUD |
| `TelaPrincipal.java` / `AppSwing.java` | Tela gráfica Swing |
| `AppWeb.java` + `resources/web/index.html` | Front-end HTML + API REST (requisito avançado) |

## Regra de cálculo
`custoTotal(nó) = valor próprio + Σ (custoTotal(filho) × quantidade do filho)`

Resultados esperados: **Caneta = R$ 7,50** e **Computador = R$ 8.080,00**.

## Como executar

### 1) Testes no console (só precisa do JDK)
```
mkdir out
javac -encoding UTF-8 -d out src/main/java/formula/*.java
java -cp out formula.TesteConsole
```

### 2) Tela gráfica e web (precisam do Maven, que baixa o driver SQLite)
```
mvn -q compile exec:java -Dexec.mainClass=formula.AppSwing
mvn -q compile exec:java -Dexec.mainClass=formula.AppWeb     # abre em http://localhost:8080
```
O banco `formulas.db` é criado na pasta atual e já vem populado com a caneta e o computador.

## Funcionalidades
- **Cadastrar fórmula**: criar produto (raiz) e adicionar componentes em qualquer nível (CRUD completo).
- **Explosão**: árvore na tela e "Explosão detalhada" com quantidade acumulada.
- **Produção**: informa N unidades e lista a quantidade de cada componente e o custo de aquisição.
- **Atualizar preço + implosão**: altera o valor de um item; os custos são recalculados de baixo para cima.

## Observações
- Itens com o mesmo nome podem existir em ramos diferentes (ex.: "Plástico"), mas um item que **tem filhos** precisa ter nome único dentro do produto.
- Na produção, itens de mesmo nome com preços diferentes aparecem em linhas separadas.
- No enunciado do Teste 2, o código usa `itens` em vez de `itensPC` e imprime "caneta"; aqui isso foi corrigido.

## Revisão de 06/11/2026

- Componentes agora são ligados pelo `pai_id` (ID do registro), inclusive quando têm nomes repetidos.
- A inicialização migra automaticamente bancos antigos que tinham apenas a coluna `pai` (nome). Faça backup de `formulas.db` antes de atualizar. Relações antigas cujo nome do pai seja ambíguo precisam de ajuste manual.
- A edição de nome atualiza somente o nome descritivo dos filhos do ID editado.
- A interface web e a interface Swing criam componentes com `paiId`.
- Preços não finitos e quantidades inválidas são rejeitados; multiplicações de quantidade detectam estouro numérico.
- Para conferir os testes em console: `mvn -q exec:java -Dexec.mainClass=formula.TesteConsole`.
- Testes adicionais: `mvn -q exec:java -Dexec.mainClass=formula.TesteRegresso`.
- Inicie a interface web com `mvn -q exec:java -Dexec.mainClass=formula.AppWeb` e acesse `http://localhost:8080`.

**Nota:** os testes Java de console foram executados com `javac`/`java`. A execução integrada do SQLite e da interface web requer Maven e o driver JDBC, indisponíveis no ambiente de revisão.
