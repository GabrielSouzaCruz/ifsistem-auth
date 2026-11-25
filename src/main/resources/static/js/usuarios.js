'use strict';

/* Gestao de usuarios pela secretaria. Usa o proprio ifsistem-sso.js (cliente "auth-admin") e a API /usuarios.
 * Acesso so para SERVIDOR; qualquer outro perfil ve "Acesso restrito". Sem prompt/confirm/alert. */

const PERFIL_ROTULO = { ALUNO: 'Aluno', PROFESSOR: 'Professor', SERVIDOR: 'Servidor (secretaria)', ADMINISTRADOR: 'Administrador' };
const PAGINA = location.origin + '/usuarios.html';

IfsistemSSO.init({
    authUrl: location.origin,
    clientId: 'auth-admin',
    redirectUri: PAGINA,
    postLogoutRedirectUri: PAGINA
});

const $ = id => document.getElementById(id);
const app = $('app');
let eu = null;
let todos = [];
let filtroPerfil = '';
let busca = '';

function esc(v) {
    return String(v ?? '').replace(/[&<>"']/g, c =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

const apenasDigitos = v => String(v ?? '').replace(/\D/g, '');

function mascararCpf(valor) {
    const d = apenasDigitos(valor).slice(0, 11);
    return d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
        .replace(/\.(\d{3})(\d)/, '.$1-$2');
}

function iniciais(nome) {
    const p = String(nome || '').trim().split(/\s+/).filter(Boolean);
    return ((p[0] || '')[0] || '').toUpperCase() + ((p.length > 1 ? p[p.length - 1][0] : '') || '').toUpperCase();
}

/* ---------- Aviso (no lugar de alert) ---------- */

let temporizador = null;
function avisar(texto, ok) {
    const el = $('aviso');
    el.className = 'aviso' + (ok ? ' ok' : '');
    el.innerHTML = '<span></span><button type="button" aria-label="Fechar aviso">×</button>';
    el.firstChild.textContent = texto;
    el.lastChild.onclick = () => { el.hidden = true; };
    el.hidden = false;
    clearTimeout(temporizador);
    if (ok) temporizador = setTimeout(() => { el.hidden = true; }, 6000);
}

/* ---------- Modal proprio (no lugar de confirm/prompt) ---------- */

/* Abre um modal com `corpo` (HTML ja escapado). `aoConfirmar` recebe o elemento do modal e devolve uma Promise;
 * se lancar Error, a mensagem aparece dentro do modal e ele continua aberto. Resolve true se concluiu. */
function modal({ titulo, mensagem, corpo = '', textoOk = 'Confirmar', perigo = false, aoConfirmar = async () => {} }) {
    return new Promise(resolve => {
        const anterior = document.activeElement;
        const id = 'modal-' + Math.random().toString(36).slice(2);
        const fundo = document.createElement('div');
        fundo.className = 'modal-fundo';
        fundo.innerHTML = `
            <form class="modal" role="dialog" aria-modal="true" aria-labelledby="${id}" novalidate>
                <h2 id="${id}">${esc(titulo)}</h2>
                ${mensagem ? `<p class="modal-msg">${esc(mensagem)}</p>` : ''}
                <div class="modal-erro" role="alert" hidden></div>
                ${corpo}
                <div class="modal-acoes">
                    <button type="button" class="btn btn-sec" data-modal="cancelar">Cancelar</button>
                    <button type="submit" class="btn ${perigo ? 'btn-perigo' : ''}">${esc(textoOk)}</button>
                </div>
            </form>`;
        document.body.appendChild(fundo);
        const form = fundo.querySelector('form');
        const erro = fundo.querySelector('.modal-erro');
        const ok = form.querySelector('[type=submit]');

        function fechar(resultado) {
            document.removeEventListener('keydown', teclas, true);
            fundo.remove();
            if (anterior && anterior.focus) anterior.focus();
            resolve(resultado);
        }
        function teclas(e) {
            if (e.key === 'Escape') { e.preventDefault(); fechar(false); }
            else if (e.key === 'Tab') {
                const foco = [...form.querySelectorAll('input:not([type=hidden]):not(:disabled), select:not(:disabled), button:not(:disabled)')];
                if (!foco.length) return;
                const i = foco.indexOf(document.activeElement);
                if (e.shiftKey && i <= 0) { e.preventDefault(); foco[foco.length - 1].focus(); }
                else if (!e.shiftKey && i === foco.length - 1) { e.preventDefault(); foco[0].focus(); }
            }
        }
        document.addEventListener('keydown', teclas, true);
        fundo.addEventListener('mousedown', e => { if (e.target === fundo) fechar(false); });
        form.querySelector('[data-modal="cancelar"]').onclick = () => fechar(false);
        form.onsubmit = async e => {
            e.preventDefault();
            erro.hidden = true;
            ok.disabled = true;
            try {
                await aoConfirmar(form);
                fechar(true);
            } catch (ex) {
                erro.textContent = ex.message || 'Não foi possível concluir.';
                erro.hidden = false;
                ok.disabled = false;
                ok.focus();
            }
        };
        (form.querySelector('input:not([type=hidden]), select') || ok).focus();
    });
}

const modalConfirmar = opcoes => modal(opcoes);

/* ---------- API ---------- */

class ErroApi extends Error {
    constructor(status, mensagem) { super(mensagem); this.status = status; }
}

async function api(caminho, opcoes = {}) {
    const { json, ...resto } = opcoes;
    const init = { ...resto };
    if (json !== undefined) {
        init.headers = { 'Content-Type': 'application/json' };
        init.body = JSON.stringify(json);
    }
    let r;
    try {
        r = await IfsistemSSO.fetch(caminho, init);
    } catch (e) {
        if (e.message === 'Não autenticado.') { IfsistemSSO.login(); throw new ErroApi(401, 'Sessão expirada.'); }
        throw new ErroApi(0, 'Falha de comunicação com o servidor.');
    }
    if (r.status === 401) { IfsistemSSO.login(); throw new ErroApi(401, 'Sessão expirada.'); }
    if (r.status === 204) return null;
    let corpo = null;
    try { corpo = await r.json(); } catch (e) { /* sem corpo */ }
    if (!r.ok) {
        const msg = r.status === 403 ? 'Você não tem permissão para esta ação.'
            : (corpo && corpo.erro) || 'Erro ' + r.status + '.';
        throw new ErroApi(r.status, msg);
    }
    return corpo;
}

/* ---------- Tela ---------- */

function selo(u) {
    const ativo = u.ativo;
    return `<span class="status ${ativo ? 'st-DEFERIDA' : 'st-INDEFERIDA'}">${ativo ? 'Ativo' : 'Inativo'}</span>`;
}

function filtrados() {
    const texto = busca.trim().toLowerCase();
    const digitos = apenasDigitos(texto);
    return todos.filter(u => !texto
        || u.nome.toLowerCase().includes(texto)
        || (digitos && apenasDigitos(u.cpf).includes(digitos)));
}

function desenharLinhas() {
    const lista = filtrados();
    $('contador').textContent = lista.length;
    $('linhas').innerHTML = lista.length === 0
        ? '<tr><td colspan="6" class="vazio">Nenhum usuário encontrado.</td></tr>'
        : lista.map(u => `
            <tr>
                <td data-rotulo="Nome"><strong>${esc(u.nome)}</strong></td>
                <td data-rotulo="CPF">${esc(mascararCpf(u.cpf))}</td>
                <td data-rotulo="E-mail">${esc(u.email)}</td>
                <td data-rotulo="Perfil">${esc(PERFIL_ROTULO[u.perfil] || u.perfil)}</td>
                <td data-rotulo="Situação">${selo(u)}</td>
                <td class="celula-acoes"><span class="acoes-linha">
                    <button type="button" class="btn btn-sec btn-mini" data-acao="editar" data-id="${esc(u.id)}">Editar</button>
                    <button type="button" class="btn btn-sec btn-mini" data-acao="senha" data-id="${esc(u.id)}">Redefinir senha</button>
                    ${u.id === eu.id
                        ? '<span class="muted pequeno">Você</span>'
                        : `<button type="button" class="btn btn-mini ${u.ativo ? 'btn-perigo' : 'btn-sec'}" data-acao="ativo" data-id="${esc(u.id)}">${u.ativo ? 'Inativar' : 'Ativar'}</button>`}
                </span></td>
            </tr>`).join('');
}

async function carregar() {
    // A API e paginada (maximo 100 por pagina); a tela filtra no navegador, entao junta todas as paginas.
    const base = '/usuarios?size=100&sort=nome' + (filtroPerfil ? '&perfil=' + encodeURIComponent(filtroPerfil) : '');
    let pagina = 0, r;
    todos = [];
    do {
        r = await api(base + '&page=' + pagina++);
        todos.push(...r.conteudo);
    } while (pagina < r.totalPaginas);
    todos.sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
    desenharLinhas();
}

function montarTela() {
    app.innerHTML = `
        <section class="card card-tabela">
            <div class="card-cab">
                <h3>Usuários <span class="contador" id="contador">0</span></h3>
                <div class="barra-filtros">
                    <label class="busca">Buscar por nome ou CPF
                        <input id="busca" type="search" placeholder="Nome ou CPF" autocomplete="off"></label>
                    <label>Perfil
                        <select id="filtro-perfil">
                            <option value="">Todos</option>
                            ${Object.keys(PERFIL_ROTULO).map(k => `<option value="${k}">${esc(PERFIL_ROTULO[k])}</option>`).join('')}
                        </select></label>
                    <button type="button" class="btn" id="btn-novo">Novo usuário</button>
                </div>
            </div>
            <div class="tabela-wrap">
                <table class="responsiva">
                    <thead><tr><th>Nome</th><th>CPF</th><th>E-mail</th><th>Perfil</th><th>Situação</th><th><span hidden>Ações</span></th></tr></thead>
                    <tbody id="linhas"></tbody>
                </table>
            </div>
        </section>
        <p class="muted pequeno">Alunos se cadastram sozinhos na tela de login. Aqui a secretaria cria professores e servidores.</p>`;

    $('busca').oninput = e => { busca = e.target.value; desenharLinhas(); };
    $('filtro-perfil').onchange = e => {
        filtroPerfil = e.target.value;
        carregar().catch(falha);
    };
    $('btn-novo').onclick = novoUsuario;
    $('linhas').onclick = e => {
        const b = e.target.closest('button[data-acao]');
        if (!b) return;
        const u = todos.find(x => x.id === b.dataset.id);
        if (!u) return;
        ({ editar: editarUsuario, senha: redefinirSenha, ativo: alternarAtivo })[b.dataset.acao](u);
    };
}

function falha(e) {
    avisar(e instanceof ErroApi ? e.message : 'Falha de comunicação com o servidor.');
}

/* Executa a escrita, recarrega a lista e avisa o resultado. */
async function aplicar(escrita, sucesso) {
    await escrita();
    avisar(sucesso, true);
    try { await carregar(); } catch (e) { falha(e); }
}

function campoSenha(id, rotulo) {
    return `
        <div class="campo">
            <label for="${id}">${esc(rotulo)}</label>
            <input id="${id}" name="senha" type="password" autocomplete="new-password" minlength="8" maxlength="72">
            <label class="mostrar-senha"><input type="checkbox" data-mostrar="${id}"> Mostrar senha</label>
            <p class="dica">Mínimo de 8 caracteres.</p>
        </div>`;
}

function ligarMostrarSenha(form) {
    form.querySelectorAll('[data-mostrar]').forEach(c => {
        c.onchange = () => { form.querySelector('#' + c.dataset.mostrar).type = c.checked ? 'text' : 'password'; };
    });
}

function validarSenha(s) {
    if (s.length < 8) throw new Error('A senha deve ter ao menos 8 caracteres.');
    if (new TextEncoder().encode(s).length > 72) throw new Error('A senha é longa demais (máximo de 72 caracteres).');
}

function novoUsuario() {
    const abertura = modal({
        titulo: 'Novo usuário',
        textoOk: 'Criar usuário',
        corpo: `
            <div class="campo"><label for="n-nome">Nome completo</label><input id="n-nome" name="nome" autocomplete="off"></div>
            <div class="campo-linha">
                <div class="campo"><label for="n-cpf">CPF</label><input id="n-cpf" name="cpf" inputmode="numeric" placeholder="000.000.000-00" autocomplete="off"></div>
                <div class="campo"><label for="n-perfil">Perfil</label>
                    <select id="n-perfil" name="perfil">
                        <option value="PROFESSOR">Professor</option>
                        <option value="SERVIDOR">Servidor (secretaria)</option>
                    </select></div>
            </div>
            <div class="campo"><label for="n-email">E-mail institucional</label>
                <input id="n-email" name="email" type="email" placeholder="nome@ifsuldeminas.edu.br" autocomplete="off"></div>
            ${campoSenha('n-senha', 'Senha inicial')}`,
        aoConfirmar: async form => {
            const v = nome => form.elements[nome].value.trim();
            const senha = form.elements.senha.value;
            if (!v('nome') || !v('cpf') || !v('email') || !senha) throw new Error('Preencha todos os campos.');
            validarSenha(senha);
            await aplicar(() => api('/usuarios', { method: 'POST', json: {
                nome: v('nome'), cpf: v('cpf'), email: v('email'), senhaInicial: senha, perfil: v('perfil')
            } }), 'Usuário criado.');
        }
    });
    const cpf = document.querySelector('#n-cpf');
    cpf.addEventListener('input', () => { cpf.value = mascararCpf(cpf.value); });
    ligarMostrarSenha(cpf.form);
    return abertura;
}

function editarUsuario(u) {
    const proprio = u.id === eu.id;
    const fixo = proprio || u.perfil === 'ADMINISTRADOR';
    const perfis = fixo ? [u.perfil] : ['ALUNO', 'PROFESSOR', 'SERVIDOR'];
    modal({
        titulo: 'Editar usuário',
        textoOk: 'Salvar',
        corpo: `
            <div class="campo"><label for="e-nome">Nome completo</label><input id="e-nome" name="nome" value="${esc(u.nome)}" autocomplete="off"></div>
            <div class="campo"><label for="e-cpf">CPF</label><input id="e-cpf" value="${esc(mascararCpf(u.cpf))}" disabled></div>
            <div class="campo"><label for="e-email">E-mail</label><input id="e-email" name="email" type="email" value="${esc(u.email)}" autocomplete="off"></div>
            <div class="campo"><label for="e-tel">Telefone (opcional)</label><input id="e-tel" name="telefone" value="${esc(u.telefone || '')}" autocomplete="off"></div>
            <div class="campo"><label for="e-perfil">Perfil</label>
                <select id="e-perfil" name="perfil" ${fixo ? 'disabled' : ''}>
                    ${perfis.map(p => `<option value="${p}" ${p === u.perfil ? 'selected' : ''}>${esc(PERFIL_ROTULO[p] || p)}</option>`).join('')}
                </select>
                ${fixo ? `<p class="dica">${proprio ? 'Você não pode trocar o perfil da própria conta.' : 'O perfil Administrador não pode ser alterado aqui.'}</p>` : ''}
            </div>`,
        aoConfirmar: async form => {
            const nome = form.elements.nome.value.trim();
            const email = form.elements.email.value.trim();
            if (!nome || !email) throw new Error('Nome e e-mail são obrigatórios.');
            // O PUT substitui telefone, curso e periodo: reenvia o que ja existe para nao apagar.
            await aplicar(() => api('/usuarios/' + u.id, { method: 'PUT', json: {
                nome, email, telefone: form.elements.telefone.value.trim() || null,
                curso: u.curso, periodo: u.periodo, perfil: fixo ? null : form.elements.perfil.value
            } }), 'Usuário atualizado.');
        }
    });
}

function alternarAtivo(u) {
    const ativar = !u.ativo;
    modal({
        titulo: ativar ? 'Ativar usuário' : 'Inativar usuário',
        mensagem: ativar
            ? `Ativar ${u.nome}? A pessoa voltará a conseguir entrar nos sistemas.`
            : `Inativar ${u.nome}? A pessoa não conseguirá mais entrar nos sistemas.`,
        textoOk: ativar ? 'Ativar' : 'Inativar',
        perigo: !ativar,
        aoConfirmar: () => aplicar(() => api('/usuarios/' + u.id + '/ativo', { method: 'PATCH', json: { ativo: ativar } }),
            ativar ? 'Usuário ativado.' : 'Usuário inativado.')
    });
}

function redefinirSenha(u) {
    const abertura = modal({
        titulo: 'Redefinir senha',
        mensagem: `Digite a nova senha de ${u.nome}. Informe-a à pessoa por um canal seguro.`,
        textoOk: 'Redefinir senha',
        corpo: campoSenha('s-senha', 'Nova senha'),
        aoConfirmar: async form => {
            const senha = form.elements.senha.value;
            validarSenha(senha);
            await aplicar(() => api('/usuarios/' + u.id + '/senha', { method: 'PUT', json: { senha } }),
                'Senha redefinida.');
        }
    });
    ligarMostrarSenha(document.querySelector('#s-senha').form);
    return abertura;
}

/* ---------- Portal ---------- */

/* So http(s) vira link; qualquer outra coisa (ex.: javascript:) e descartada. */
function urlSegura(texto) {
    try {
        const u = new URL(texto, location.href);
        return /^https?:$/.test(u.protocol) ? u : null;
    } catch (e) { return null; }
}

/* O menu e fixo no HTML ("Usuarios"); o config so traz a URL do portal. Se falhar, o link fica escondido. */
async function carregarConfig() {
    try {
        const r = await fetch('/config');
        if (!r.ok) return { portalUrl: '' };
        const c = await r.json();
        return { portalUrl: c.portalUrl || '' };
    } catch (e) {
        return { portalUrl: '' };
    }
}

function montarMenu(config) {
    const portal = urlSegura(config.portalUrl);
    const voltar = $('voltar-portal');
    if (portal && config.portalUrl.trim() !== '#') { voltar.href = portal.href; voltar.hidden = false; }
}

/* ---------- Moldura ---------- */

function menuGaveta() {
    const botao = $('btn-menu'), overlay = $('overlay'), lateral = $('lateral');
    const aberta = () => document.body.classList.contains('menu-aberto');
    function fechar(devolverFoco) {
        if (!aberta()) return;
        document.body.classList.remove('menu-aberto');
        overlay.hidden = true;
        botao.setAttribute('aria-expanded', 'false');
        botao.setAttribute('aria-label', 'Abrir menu');
        if (devolverFoco) botao.focus({ preventScroll: true });
    }
    function abrir() {
        document.body.classList.add('menu-aberto');
        overlay.hidden = false;
        botao.setAttribute('aria-expanded', 'true');
        botao.setAttribute('aria-label', 'Fechar menu');
        const primeiro = lateral.querySelector('.menu a');
        if (primeiro) primeiro.focus({ preventScroll: true });
    }
    botao.addEventListener('click', () => (aberta() ? fechar(true) : abrir()));
    overlay.addEventListener('click', () => fechar(true));
    document.addEventListener('keydown', e => { if (e.key === 'Escape' && aberta()) fechar(true); });
    lateral.addEventListener('click', e => { if (e.target.closest('.menu a, .marca, #btn-sair')) fechar(false); });
    const larga = window.matchMedia('(min-width: 1025px)');
    if (larga.addEventListener) larga.addEventListener('change', () => { if (larga.matches) fechar(false); });
}

function montarMoldura(u, config) {
    document.body.classList.remove('sem-sessao');
    $('usuario-nome').textContent = u.nome;
    $('usuario-papel').textContent = 'Secretaria';
    $('usuario-iniciais').textContent = iniciais(u.nome);
    const agora = new Date();
    $('hoje').textContent = agora.toLocaleDateString('pt-BR', { day: 'numeric', month: 'long', year: 'numeric' })
        + ' • ' + agora.toLocaleDateString('pt-BR', { weekday: 'long' });
    $('btn-sair').onclick = () => IfsistemSSO.logout();
    montarMenu(config);
    menuGaveta();
}

function telaRestrita() {
    document.body.classList.add('sem-sessao');
    app.innerHTML = `
        <section class="card acesso-restrito">
            <h1>Acesso restrito</h1>
            <p>Esta área é exclusiva da secretaria. Seu perfil não tem permissão para gerenciar usuários.</p>
            <div class="acoes"><button type="button" class="btn" id="restrito-sair">Sair</button></div>
        </section>`;
    $('restrito-sair').onclick = () => IfsistemSSO.logout();
}

function telaErroLogin(mensagem) {
    document.body.classList.add('sem-sessao');
    app.innerHTML = `
        <section class="card acesso-restrito">
            <h1>Não foi possível entrar</h1>
            <p id="erro-login"></p>
            <div class="acoes"><button type="button" class="btn" id="tentar">Tentar novamente</button></div>
        </section>`;
    $('erro-login').textContent = mensagem;
    $('tentar').onclick = () => IfsistemSSO.login();
}

(async () => {
    try {
        await IfsistemSSO.handleCallback();
        await IfsistemSSO.getToken(); // renova ou descarta um token vencido
    } catch (e) {
        telaErroLogin(e.message);
        return;
    }
    const u = IfsistemSSO.usuario();
    if (!u) return IfsistemSSO.login();
    if (u.perfil !== 'SERVIDOR' && u.perfil !== 'ADMINISTRADOR') return telaRestrita();
    eu = u;
    montarMoldura(u, await carregarConfig());
    montarTela();
    try { await carregar(); } catch (e) {
        if (e.status === 403) return telaRestrita();
        falha(e);
    }
})();
