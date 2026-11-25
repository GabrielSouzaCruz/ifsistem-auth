# API de usuários do IFSISTEM (auth)

Para equipes de outros módulos (chaves, biblioteca, etc.) que precisam **listar, buscar e administrar usuários** a partir do
próprio front. Documentação interativa: **[Swagger UI](https://ifsistem-auth.onrender.com/swagger-ui.html)**
(local: `http://localhost:9000/swagger-ui.html`).

Antes de tudo, o seu sistema precisa estar cadastrado como cliente do login único (veja o [INTEGRACAO.md](INTEGRACAO.md)).

## 1. URL base

| Ambiente | URL base |
|---|---|
| Produção | `https://ifsistem-auth.onrender.com` |
| Local | `http://localhost:9000` |

Todos os caminhos abaixo partem dela (ex.: `https://ifsistem-auth.onrender.com/usuarios`). As requisições e respostas são
JSON em UTF-8. O servidor gratuito do Render dorme: a primeira chamada depois de um tempo parado pode demorar.

## 2. Autenticação

- Envie `Authorization: Bearer <access_token>` em toda chamada.
- O token é **o do próprio usuário logado**, obtido pelo `ifsistem-sso.js` (`IfsistemSSO.fetch` já coloca o cabeçalho e
  renova o token). **Não há escopo extra** nem chave de serviço: quem pode o quê depende do perfil do usuário no banco.
- Use o `access_token`, nunca o `id_token` (dá 401).
- O perfil e o status são conferidos no banco a cada chamada: quem foi inativado ou rebaixado perde o acesso na hora, mesmo
  com o token ainda dentro da validade (1 hora).

### Perfis

| Perfil | `/usuarios/**` | `/me/**` |
|---|---|---|
| `SERVIDOR` | sim | sim |
| `ADMINISTRADOR` | sim | sim |
| `PROFESSOR`, `ALUNO` | **403** | sim |

## 3. Endpoints

| Método | Caminho | Perfis | Códigos de resposta |
|---|---|---|---|
| `GET` | `/usuarios` | SERVIDOR, ADMINISTRADOR | 200, 400 (page/size/sort inválidos), 401, 403 |
| `GET` | `/usuarios/{id}` | SERVIDOR, ADMINISTRADOR | 200, 401, 403, 404 |
| `POST` | `/usuarios` | SERVIDOR, ADMINISTRADOR | 201, 400, 401, 403, 409 |
| `PUT` | `/usuarios/{id}` | SERVIDOR, ADMINISTRADOR | 200, 400, 401, 403, 404, 409 |
| `PATCH` | `/usuarios/{id}/ativo` | SERVIDOR, ADMINISTRADOR | 200, 400, 401, 403, 404, 409 |
| `PUT` | `/usuarios/{id}/senha` | SERVIDOR, ADMINISTRADOR | 204, 400, 401, 403, 404 |
| `DELETE` | `/usuarios/{id}` | SERVIDOR, ADMINISTRADOR | 204, 401, 403, 404, 409 |
| `PUT` | `/me/senha` | qualquer usuário autenticado | 204, 400, 401 |

Significado dos códigos:

| Código | Quando |
|---|---|
| 200 / 201 / 204 | Sucesso (204 não tem corpo; 201 devolve o usuário criado) |
| 400 | Dado ausente ou inválido (CPF, e-mail, senha fora de 8–72 caracteres, perfil não permitido, senha atual errada, `page`/`size`/`sort`) |
| 401 | Sem token, token inválido/vencido, `id_token` no lugar do `access_token`, ou usuário inativado |
| 403 | Perfil sem permissão (ex.: `ALUNO` em `/usuarios`) |
| 404 | Usuário não existe |
| 409 | CPF, e-mail ou matrícula já cadastrados; ou tentar inativar, excluir ou trocar o perfil **da própria conta** |

Erros vêm sempre assim: `{"status": 400, "erro": "A senha deve ter ao menos 8 caracteres"}`.

## 4. Objeto usuário

A resposta **nunca** traz senha nem hash.

```json
{
  "id": "3f2b8c1e-5a4d-4e7b-9c10-2d6f8a7b1c34",
  "nome": "Maria da Silva",
  "cpf": "52998224725",
  "email": "maria.silva@ifsuldeminas.edu.br",
  "matricula": "PRO-52998224725",
  "telefone": null,
  "curso": null,
  "periodo": null,
  "perfil": "PROFESSOR",
  "status": "ATIVO",
  "ativo": true
}
```

| Campo | Observação |
|---|---|
| `id` | UUID estável; é o claim `sub` do token (veja a seção 8) |
| `cpf` | só dígitos |
| `perfil` | `ALUNO`, `PROFESSOR`, `SERVIDOR` ou `ADMINISTRADOR` |
| `status` | `ATIVO`, `INATIVO` ou `EXCLUIDO` (excluídos não aparecem nas buscas) |
| `ativo` | `true` quando `status` é `ATIVO` (pode entrar) |
| `telefone`, `curso`, `periodo` | podem ser `null` |

## 5. Operações, campos e exemplos

### Buscar — `GET /usuarios`

Resposta `200`:

```json
{
  "conteudo": [ { "id": "3f2b8c1e-5a4d-4e7b-9c10-2d6f8a7b1c34", "nome": "Maria da Silva", "...": "..." } ],
  "pagina": 0,
  "tamanho": 20,
  "totalElementos": 42,
  "totalPaginas": 3
}
```

Filtros, todos opcionais e combinados com **E** (`GET /usuarios?perfil=PROFESSOR&ativo=true&q=maria&page=0&size=20`):

| Parâmetro | Descrição |
|---|---|
| `perfil` | `ALUNO`, `PROFESSOR`, `SERVIDOR` ou `ADMINISTRADOR` |
| `ativo` | `true` = pode entrar; `false` = inativos |
| `cpf` | CPF **exato**, só dígitos (`52998224725`) |
| `q` | parte do **nome** (sem diferenciar maiúsculas nem acentos: `jose` acha "José") **ou parte do CPF** (quando o texto só tem dígitos, `.` e `-`) |
| `page` | número da página, **começa em 0** (padrão 0) |
| `size` | itens por página: padrão **20**, máximo **100** (valor maior é reduzido a 100; `tamanho` na resposta mostra o valor usado) |
| `sort` | só `nome` (padrão). Ordena sem diferenciar acentos. Outro valor dá 400 |

Excluídos nunca aparecem. Página além do fim devolve `conteudo` vazio, com os totais corretos.

### Buscar um — `GET /usuarios/{id}`

Devolve o objeto usuário. `404` se o id não existe (inclusive excluídos continuam consultáveis por id).

### Criar — `POST /usuarios`

Cria um `PROFESSOR` ou `SERVIDOR` (a matrícula é gerada pelo auth). Alunos se cadastram sozinhos pela tela de cadastro.

| Campo | Obrigatório | Regra |
|---|---|---|
| `cpf` | sim | válido, com ou sem máscara |
| `nome` | sim | |
| `email` | sim | único; guardado em minúsculas |
| `senhaInicial` | sim | 8 a 72 caracteres |
| `perfil` | sim | `PROFESSOR` ou `SERVIDOR` |

```json
{
  "cpf": "529.982.247-25",
  "nome": "Maria da Silva",
  "email": "maria.silva@ifsuldeminas.edu.br",
  "senhaInicial": "troque-me-123",
  "perfil": "PROFESSOR"
}
```

Resposta `201`: o objeto usuário (sem senha).

### Atualizar — `PUT /usuarios/{id}`

| Campo | Obrigatório | Regra |
|---|---|---|
| `nome` | sim | |
| `email` | sim | único |
| `telefone`, `curso`, `periodo` | não | omitidos viram `null` (o PUT substitui estes campos) |
| `perfil` | não | omitido mantém o atual. Não aceita `ADMINISTRADOR` (nem sair dele) e não pode ser trocado na própria conta (409) |

CPF, matrícula e senha não mudam por aqui.

```json
{
  "nome": "Maria da Silva Souza",
  "email": "maria.souza@ifsuldeminas.edu.br",
  "telefone": "(35) 99999-0000",
  "curso": "Engenharia de Software",
  "periodo": 3,
  "perfil": "SERVIDOR"
}
```

Resposta `200`: o usuário atualizado.

### Ativar ou inativar — `PATCH /usuarios/{id}/ativo`

```json
{ "ativo": false }
```

Obrigatório: `ativo` (boolean). `false` inativa e **revoga as sessões**; `true` reativa (inclusive um usuário excluído).
Não vale para a própria conta (409). Resposta `200`: o usuário com o novo `status`.

### Redefinir a senha de outro — `PUT /usuarios/{id}/senha`

```json
{ "senha": "nova-senha-123" }
```

Obrigatório: `senha` (8 a 72 caracteres). Não exige a senha anterior. Revoga as sessões do usuário. Resposta `204`.

### Trocar a própria senha — `PUT /me/senha`

Para **qualquer** usuário autenticado, de qualquer perfil.

```json
{ "senhaAtual": "troque-me-123", "novaSenha": "uma-senha-nova-456" }
```

Obrigatórios: `senhaAtual` e `novaSenha` (8 a 72 caracteres). Resposta `204`. **Senha atual errada devolve `400`** (e não 401,
para o seu front não interpretar como sessão vencida e deslogar a pessoa). Revoga as sessões.

### Excluir — `DELETE /usuarios/{id}`

Exclusão lógica; resposta `204`. Não vale para a própria conta (409).

## 6. CORS

O navegador pode chamar `/usuarios/**` e `/me/**` direto, de outra origem. São aceitas as **origens tiradas das
`redirectUris` de todos os clientes cadastrados** (ex.: `https://chaves-ifsistem.vercel.app`, `http://localhost:5173`),
com os métodos `GET`, `POST`, `PUT`, `PATCH` e `DELETE` e os cabeçalhos `Authorization` e `Content-Type`. O preflight
(`OPTIONS`) é respondido sem token. Origem não cadastrada recebe `403` no preflight.
Sem cookies: use só o Bearer (não use `credentials: 'include'`).

Para liberar a origem de um sistema novo, cadastre-o como cliente (INTEGRACAO.md, seção 1).

## 7. Ciclo de vida da conta

1. **Criar com senha inicial.** A secretaria faz `POST /usuarios` com `senhaInicial` e passa a senha à pessoa por um canal
   seguro.
2. **Primeiro acesso.** A pessoa entra na tela de login do auth com CPF e a senha inicial. O auth **não obriga** a troca:
   o seu front pode (e deve) orientar a pessoa a trocar a senha logo depois.
3. **Troca pelo próprio usuário.** `PUT /me/senha` com `senhaAtual` e `novaSenha`. Revoga as sessões (refresh tokens): o
   token em uso continua valendo até vencer (1 hora) e depois a pessoa entra de novo com a senha nova.
4. **Redefinição pela secretaria.** Esqueceu a senha? `PUT /usuarios/{id}/senha`. Também revoga as sessões.
5. **Inativação.** `PATCH /usuarios/{id}/ativo` com `false`: a pessoa não entra mais, as sessões são revogadas e as
   chamadas com o token antigo passam a dar 401 de imediato. Reativar com `true` não revoga nada.
6. **Exclusão lógica.** `DELETE /usuarios/{id}`: o registro permanece no banco, some das buscas e não entra mais. O `id`
   continua existindo, então o que o seu sistema guardou (chaves, empréstimos, etc.) não fica órfão. Se preciso,
   `PATCH .../ativo` com `true` restaura.

## 8. O `id` é o `sub` e é estável

O `id` do usuário é um UUID gerado na criação e **nunca muda**: não muda ao editar nome, e-mail ou perfil, nem ao inativar,
excluir ou restaurar. É o mesmo valor do claim `sub` do token (e do `id` de `IfsistemSSO.usuario()`). Use-o como chave
estrangeira no seu banco; não use CPF, e-mail ou matrícula para isso.

## 9. Exemplo com `IfsistemSSO.fetch`

Com o `IfsistemSSO` já configurado e o usuário logado (INTEGRACAO.md, seção 3):

```js
const AUTH = 'https://ifsistem-auth.onrender.com';

async function api(caminho, opcoes = {}) {
    const resp = await IfsistemSSO.fetch(AUTH + caminho, {
        ...opcoes,
        headers: { 'Content-Type': 'application/json' },
        body: opcoes.json ? JSON.stringify(opcoes.json) : undefined
    });
    if (resp.status === 204) return null;
    const dados = await resp.json();
    if (!resp.ok) throw new Error(dados.erro);       // 400/403/404/409: {status, erro}
    return dados;
}

// Buscar professores ativos pelo nome (sem acento), 10 por página
const pagina = await api('/usuarios?perfil=PROFESSOR&ativo=true&q=' + encodeURIComponent('jose') + '&size=10');
pagina.conteudo.forEach(u => console.log(u.id, u.nome));

// Criar
const novo = await api('/usuarios', { method: 'POST', json: {
    cpf: '52998224725', nome: 'Maria da Silva', email: 'maria.silva@ifsuldeminas.edu.br',
    senhaInicial: 'troque-me-123', perfil: 'PROFESSOR'
} });

// Qualquer usuário trocando a própria senha
await api('/me/senha', { method: 'PUT', json: { senhaAtual: 'troque-me-123', novaSenha: 'uma-senha-nova-456' } });
```

Não esqueça de tratar `401` (refaça o login com `IfsistemSSO.login()`) e `403` (perfil sem permissão).

## 10. Swagger

[`/swagger-ui.html`](https://ifsistem-auth.onrender.com/swagger-ui.html) mostra só `/usuarios/**` e `/me/**`, com exemplos
de request e response. Clique em **Authorize** e cole o access token (esquema *bearer JWT*) para testar. Para obter um
token de teste, no console do navegador de uma página sua já logada rode `await IfsistemSSO.getToken()`. A especificação em JSON está em `/v3/api-docs`.
