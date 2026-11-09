package formula;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Servidor web (API REST + front-end HTML) com persistência em SQLite. Acesse http://localhost:8080 */
public class AppWeb {

    public static void main(String[] args) throws Exception {
        Repositorio repo = new Repositorio("formulas.db");
        Exemplos.semear(repo);
        int porta = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(porta), 0);
        server.createContext("/", ex -> tratar(repo, ex));
        server.start();
        System.out.println("Servidor no ar: http://localhost:" + porta);
    }

    private static void tratar(Repositorio repo, HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if (path.equals("/") || path.equals("/index.html")) {
                enviarHtml(ex);
                return;
            }
            if (path.equals("/style.css") || path.equals("/app.js")) {
                enviarRecurso(ex, path.substring(1), path.endsWith(".css") ? "text/css; charset=utf-8" : "application/javascript; charset=utf-8");
                return;
            }
            Map<String, String> p = parametros(ex);
            boolean post = ex.getRequestMethod().equalsIgnoreCase("POST");

            switch (path) {
                case "/api/produtos":
                    responder(ex, 200, jsonLista(repo.produtos()));
                    break;

                case "/api/arvore": {
                    TreeNode raiz = raiz(repo, obrig(p, "produto"));
                    StringBuilder sb = new StringBuilder();
                    json(raiz, sb);
                    responder(ex, 200, sb.toString());
                    break;
                }

                case "/api/producao": {
                    TreeNode raiz = raiz(repo, obrig(p, "produto"));
                    long un = Long.parseLong(obrig(p, "qtd").trim());
                    if (un <= 0) throw new IllegalArgumentException("Informe uma quantidade maior que zero.");
                    List<ArvoreFormula.LinhaMaterial> linhas = ArvoreFormula.necessidade(raiz, un);
                    StringBuilder sb = new StringBuilder("{\"unidades\":" + un + ",\"linhas\":[");
                    for (int i = 0; i < linhas.size(); i++) {
                        ArvoreFormula.LinhaMaterial l = linhas.get(i);
                        if (i > 0) sb.append(',');
                        sb.append("{\"nome\":\"").append(esc(l.nome)).append("\",\"qtd\":").append(l.qtd)
                          .append(",\"unitario\":").append(num(l.unitario))
                          .append(",\"subtotal\":").append(num(l.subtotal)).append('}');
                    }
                    sb.append("],\"total\":").append(num(ArvoreFormula.custoTotal(linhas))).append('}');
                    responder(ex, 200, sb.toString());
                    break;
                }

                case "/api/item": {
                    exigirPost(post);
                    String pai = p.get("pai");
                    if (pai != null && pai.isBlank()) pai = null;
                    ItemFormula novo = new ItemFormula(obrig(p, "nome"), pai,
                            Integer.parseInt(obrig(p, "qtd").trim()), dbl(obrig(p, "valor")));
                    if (p.containsKey("paiId") && !p.get("paiId").isBlank())
                        novo.paiId = Long.parseLong(p.get("paiId").trim());
                    long id = repo.inserir(obrig(p, "produto"), novo);
                    responder(ex, 200, "{\"id\":" + id + "}");
                    break;
                }

                case "/api/item/atualizar":
                    exigirPost(post);
                    repo.atualizar(Long.parseLong(obrig(p, "id").trim()), obrig(p, "nome"),
                            Integer.parseInt(obrig(p, "qtd").trim()), dbl(obrig(p, "valor")));
                    responder(ex, 200, "{\"ok\":true}");
                    break;

                case "/api/item/excluir":
                    exigirPost(post);
                    repo.excluir(Long.parseLong(obrig(p, "id").trim()));
                    responder(ex, 200, "{\"ok\":true}");
                    break;

                case "/api/produto/excluir":
                    exigirPost(post);
                    repo.excluirProduto(obrig(p, "produto"));
                    responder(ex, 200, "{\"ok\":true}");
                    break;

                default:
                    responder(ex, 404, "{\"erro\":\"Não encontrado\"}");
            }
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            responder(ex, 400, "{\"erro\":\"" + esc(msg) + "\"}");
        }
    }

    // ---------------- Utilitários ----------------

    private static TreeNode raiz(Repositorio repo, String produto) {
        List<ItemFormula> itens = repo.itens(produto);
        if (itens.isEmpty()) throw new IllegalArgumentException("Produto não encontrado: " + produto);
        return ArvoreFormula.buildTree(itens);
    }

    private static void exigirPost(boolean post) {
        if (!post) throw new IllegalArgumentException("Use o método POST.");
    }

    private static String obrig(Map<String, String> p, String chave) {
        String v = p.get(chave);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Parâmetro obrigatório: " + chave);
        return v;
    }

    private static double dbl(String s) {
        return Double.parseDouble(s.trim().replace(',', '.'));
    }

    private static Map<String, String> parametros(HttpExchange ex) throws IOException {
        Map<String, String> m = new HashMap<>();
        leitura(ex.getRequestURI().getRawQuery(), m);
        String corpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        leitura(corpo, m);
        return m;
    }

    private static void leitura(String s, Map<String, String> m) {
        if (s == null || s.isEmpty()) return;
        for (String par : s.split("&")) {
            int i = par.indexOf('=');
            String k = i < 0 ? par : par.substring(0, i);
            String v = i < 0 ? "" : par.substring(i + 1);
            m.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
    }

    private static void enviarRecurso(HttpExchange ex, String nome, String tipo) throws IOException {
        try (InputStream in = AppWeb.class.getResourceAsStream("/web/" + nome)) {
            if (in == null) { responder(ex, 404, "{\"erro\":\"Recurso não encontrado\"}"); return; }
            byte[] b = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", tipo);
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        }
    }

    private static void enviarHtml(HttpExchange ex) throws IOException {
        try (InputStream in = AppWeb.class.getResourceAsStream("/web/index.html")) {
            if (in == null) {
                responder(ex, 404, "{\"erro\":\"index.html não encontrado\"}");
                return;
            }
            byte[] b = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        }
    }

    private static void responder(HttpExchange ex, int codigo, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(codigo, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: if (c >= 0x20) sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String jsonLista(List<String> l) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(esc(l.get(i))).append('"');
        }
        return sb.append(']').toString();
    }

    private static void json(TreeNode n, StringBuilder sb) {
        sb.append("{\"id\":").append(n.id)
          .append(",\"nome\":\"").append(esc(n.nome)).append("\"")
          .append(",\"qtd\":").append(n.qtd)
          .append(",\"valor\":").append(num(n.valor))
          .append(",\"total\":").append(num(n.custoTotal))
          .append(",\"filhos\":[");
        for (int i = 0; i < n.filhos.size(); i++) {
            if (i > 0) sb.append(',');
            json(n.filhos.get(i), sb);
        }
        sb.append("]}");
    }
}
