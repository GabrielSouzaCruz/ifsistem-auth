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

### Deploy pelo Blueprint

1. No Render: **New → Blueprint** e escolha este repositório (lê o `render.yaml`: serviço `ifsistem-auth`, Docker, plano free, região Ohio).
2. Preencha as variáveis `sync: false`: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `AUTH_RSA_PRIVATE_KEY`, `AUTH_RSA_PUBLIC_KEY`, `ADMIN_CPF`, `ADMIN_SENHA`, `ADMIN_EMAIL`.
3. As demais (`SPRING_PROFILES_ACTIVE`, `DB_SCHEMA`, `AUTH_ISSUER`, `PORTAL_URL`, `JAVA_OPTS`) já vêm com valor fixo no Blueprint.
4. Gere as chaves RSA com os comandos `openssl` abaixo.

Variáveis de ambiente do profile `render`:

| Variável | Obrigatória | Descrição |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | sim | `render` |
| `PORT` | sim | Definida pelo próprio Render |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | sim | Mesmo Supabase do aproveitamento, pelo **Session pooler** (porta 5432) |
| `DB_SCHEMA` | não | Schema das tabelas. Padrão `ifsistem_auth` (criado pelo Flyway); veja o aviso abaixo |
| `AUTH_ISSUER` | sim | URL pública, ex.: `https://ifsistem-auth.onrender.com` |
| `AUTH_RSA_PRIVATE_KEY` | sim | PEM PKCS#8 (`BEGIN PRIVATE KEY`) |
| `AUTH_RSA_PUBLIC_KEY` | sim | PEM X.509 (`BEGIN PUBLIC KEY`), par da anterior |
| `PORTAL_URL` | recomendada | Destino de "← Portal IFSISTEM" |
| `ADMIN_CPF`, `ADMIN_SENHA` | recomendadas | Criam um SERVIDOR na subida se o CPF ainda não existir |
| `ADMIN_EMAIL` | não | Padrão `secretaria@ifsuldeminas.edu.br` |

**Pool de conexões:** o profile `render` limita o HikariCP a 4 conexões por instância (`minimum-idle=1`, `idle-timeout=60s`, `max-lifetime=10min`). O limite de **15 conexões do Session pooler do Supabase vale para a soma de todos os serviços** (ifsistem-auth + aproveitamento, mais instâncias extras durante um deploy); mantenha a soma dos `maximum-pool-size` abaixo disso.
| `JAVA_OPTS` | não | Ex.: `-XX:MaxRAMPercentage=75` |

> **Supabase e o schema `auth`:** o Supabase já tem um schema `auth`, do serviço de login dele, no qual o usuário
> `postgres` não pode criar tabelas. Por isso o padrão é `ifsistem_auth`; não use `auth`. O Flyway cria
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
