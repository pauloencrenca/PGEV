
const $ = s => document.querySelector(s);
const brl = v => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
let produtoAtual = null;
let totalAtual = null;

async function api(url, dados) {
  const opts = dados ? { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(dados) } : {};
  const r = await fetch(url, opts);
  const j = await r.json();
  if (!r.ok) throw new Error(j.erro || 'Erro');
  return j;
}

async function acao(fn) {
  try { await fn(); } catch (e) { alert(e.message); }
}

function btn(texto, fn) {
  const b = document.createElement('button');
  b.textContent = texto;
  b.onclick = () => acao(fn);
  return b;
}

async function carregarProdutos(selecionar) {
  const lista = await api('/api/produtos');
  const sel = $('#produto');
  sel.innerHTML = '';
  lista.forEach(p => { const o = document.createElement('option'); o.value = o.textContent = p; sel.append(o); });
  if (selecionar && lista.includes(selecionar)) sel.value = selecionar;
  produtoAtual = lista.length ? sel.value : null;
  $('#metricProduto').textContent = produtoAtual || 'Nenhum produto';
  await carregarArvore();
}

async function carregarArvore() {
  const ul = $('#arvore');
  ul.innerHTML = '';
  $('#producao').innerHTML = '<div class="empty"><span class="empty-icon">▤</span><strong>Seu planejamento começa aqui</strong><span>Informe uma quantidade e clique em simular para visualizar o detalhamento.</span></div>';
  if (!produtoAtual) { totalAtual = null; $('#metricCusto').textContent = '—'; $('#metricComponentes').textContent = '0'; ul.innerHTML = '<li class="empty">Nenhum produto cadastrado. Clique em Novo produto para começar.</li>'; return; }
  const raiz = await api('/api/arvore?produto=' + encodeURIComponent(produtoAtual));
  totalAtual = raiz.total;
  $('#metricProduto').textContent = produtoAtual;
  $('#metricCusto').textContent = brl(totalAtual);
  const contar = n => 1 + (n.filhos || []).reduce((s, f) => s + contar(f), 0);
  $('#metricComponentes').textContent = String(contar(raiz) - 1);
  ul.append(renderNo(raiz));
}

function renderNo(n) {
  const li = document.createElement('li');
  const linha = document.createElement('div');
  linha.className = 'linha';
  const txt = document.createElement('span');
  txt.textContent = `${n.nome} (qtd ${n.qtd}) — próprio ${brl(n.valor)} | total ${brl(n.total)}`;
  linha.append(txt,
    btn('+ filho', () => addFilho(n)),
    btn('editar', () => editar(n)),
    btn('preço', () => preco(n)),
    btn('excluir', () => excluir(n)));
  li.append(linha);
  if (n.filhos.length) {
    const ul = document.createElement('ul');
    n.filhos.forEach(f => ul.append(renderNo(f)));
    li.append(ul);
  }
  return li;
}

async function novoProduto() {
  const nome = prompt('Nome do produto (item raiz):');
  if (!nome) return;
  const valor = prompt('Valor próprio do item raiz (R$):', '0');
  if (valor === null) return;
  await api('/api/item', { produto: nome, pai: '', nome, qtd: 1, valor });
  await carregarProdutos(nome);
}

async function excluirProduto() {
  if (!produtoAtual || !confirm(`Excluir o produto "${produtoAtual}" inteiro?`)) return;
  await api('/api/produto/excluir', { produto: produtoAtual });
  $('#msg').textContent = '';
  await carregarProdutos();
}

async function addFilho(pai) {
  const nome = prompt(`Nome do novo componente de "${pai.nome}":`);
  if (!nome) return;
  const qtd = prompt('Quantidade:', '1');
  if (qtd === null) return;
  const valor = prompt('Valor próprio (R$):', '0');
  if (valor === null) return;
  await api('/api/item', { produto: produtoAtual, paiId: pai.id, nome, qtd, valor });
  await carregarArvore();
}

async function editar(n) {
  const nome = prompt('Nome:', n.nome);
  if (!nome) return;
  const qtd = prompt('Quantidade:', n.qtd);
  if (qtd === null) return;
  const valor = prompt('Valor próprio (R$):', n.valor);
  if (valor === null) return;
  await api('/api/item/atualizar', { id: n.id, nome, qtd, valor });
  await carregarArvore();
}

async function preco(n) {
  const valor = prompt(`Novo valor de "${n.nome}" (R$):`, n.valor);
  if (valor === null) return;
  const antes = totalAtual;
  await api('/api/item/atualizar', { id: n.id, nome: n.nome, qtd: n.qtd, valor });
  await carregarArvore();   // o servidor remonta a árvore e reimplode os custos
  $('#msg').textContent = `Implosão: custo do produto passou de ${brl(antes)} para ${brl(totalAtual)}.`;
}

async function excluir(n) {
  if (!confirm(`Excluir "${n.nome}" e todos os seus componentes?`)) return;
  await api('/api/item/excluir', { id: n.id });
  await carregarProdutos(produtoAtual);
}

async function produzir() {
  if (!produtoAtual) return;
  const r = await api(`/api/producao?produto=${encodeURIComponent(produtoAtual)}&qtd=${encodeURIComponent($('#qtd').value)}`);
  const t = document.createElement('table');
  t.innerHTML = '<tr><th>Componente</th><th class="n">Quantidade</th><th class="n">Valor unitário</th><th class="n">Subtotal</th></tr>';
  r.linhas.forEach(l => {
    const tr = document.createElement('tr');
    [l.nome, l.qtd, brl(l.unitario), brl(l.subtotal)].forEach((v, i) => {
      const td = document.createElement('td');
      if (i > 0) td.className = 'n';
      td.textContent = v;
      tr.append(td);
    });
    t.append(tr);
  });
  const rodape = document.createElement('tr');
  rodape.innerHTML = '<th colspan="3">Custo de aquisição total (' + r.unidades + ' un.)</th>';
  const th = document.createElement('th');
  th.className = 'n';
  th.textContent = brl(r.total);
  rodape.append(th);
  t.append(rodape);
  $('#producao').innerHTML = '';
  $('#producao').append(t);
}

$('#produto').onchange = e => acao(async () => { produtoAtual = e.target.value; $('#msg').textContent = ''; await carregarArvore(); });
$('#btnNovo').onclick = () => acao(novoProduto);
$('#btnExcluirProduto').onclick = () => acao(excluirProduto);
$('#btnProduzir').onclick = () => acao(produzir);
acao(() => carregarProdutos());
