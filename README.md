# ifsistem-auth

Login único do IFSISTEM: servidor OAuth2/OIDC (Spring Authorization Server, Spring Boot 4.1, Java 21) com login por CPF.

Para integrar um sistema, leia [docs/INTEGRACAO.md](docs/INTEGRACAO.md).

## Rodar localmente

Sem Postgres, com H2 em memória no modo PostgreSQL (o H2 só existe no classpath de teste, por isso `test-run`):

```
ADMIN_CPF=52998224725 ADMIN_SENHA=senha-local-1 ./mvnw spring-boot:test-run
```

No PowerShell: `$env:ADMIN_CPF='52998224725'; $env:ADMIN_SENHA='senha-local-1'; .\mvnw.cmd spring-boot:test-run`

Abra http://localhost:9000/exemplo-cliente.html e entre com esse CPF e senha.

Com Postgres: `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` (padrões `ifsystem`, `postgres`, `123`) e `./mvnw spring-boot:run`.

Testes: `./mvnw test`.

## Produção (Render)

Profile `render`. Variáveis de ambiente:

| Variável | Obrigatória | Descrição |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | sim | `render` |
| `PORT` | sim | Definida pelo próprio Render |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | sim | Mesmo Supabase do aproveitamento, pelo **Session pooler** (porta 5432) |
| `DB_SCHEMA` | **sim no Supabase** | Schema das tabelas. Padrão `auth`; veja o aviso abaixo |
| `AUTH_ISSUER` | sim | URL pública, ex.: `https://ifsistem-auth.onrender.com` |
| `AUTH_RSA_PRIVATE_KEY` | sim | PEM PKCS#8 (`BEGIN PRIVATE KEY`) |
| `AUTH_RSA_PUBLIC_KEY` | sim | PEM X.509 (`BEGIN PUBLIC KEY`), par da anterior |
| `PORTAL_URL` | recomendada | Destino de "← Portal IFSISTEM" |
| `ADMIN_CPF`, `ADMIN_SENHA` | recomendadas | Criam um SERVIDOR na subida se o CPF ainda não existir |
| `ADMIN_EMAIL` | não | Padrão `secretaria@ifsuldeminas.edu.br` |
| `JAVA_OPTS` | não | Ex.: `-XX:MaxRAMPercentage=75` |

> **Supabase e o schema `auth`:** o Supabase já tem um schema `auth`, do serviço de login dele, no qual o usuário
> `postgres` não pode criar tabelas. Com o padrão, o Flyway falha na subida. Use `DB_SCHEMA=ifsistem_auth`; o Flyway cria
> o schema.

Gerar a chave (fora da pasta do repositório; nunca versione):

```
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out auth-privada.pem
openssl pkey -in auth-privada.pem -pubout -out auth-publica.pem
```

Cole o conteúdo inteiro de cada arquivo na variável (quebras de linha reais ou escritas como `\n`). Trocar a chave invalida
os tokens já emitidos.

## Banco

Flyway, no schema `DB_SCHEMA`; o Hibernate só valida (`ddl-auto=validate`).

| Versão | O que faz |
|---|---|
| V1 | Tabela `usuarios`, com o mesmo modelo do aproveitamento |
| V2 (Java) | Copia `public.usuarios` mantendo id, cpf, senha, perfil e status. Só se a tabela existir e só quem ainda não existe (id, cpf, e-mail ou matrícula). Roda uma vez |
| V3 | `oauth2_authorization` e `oauth2_authorization_consent` |

A V2 é em Java porque o teste dela roda no H2, que não tem os blocos `DO` do Postgres.

Toda mudança de entidade exige uma nova migração. Nunca edite uma já aplicada.
