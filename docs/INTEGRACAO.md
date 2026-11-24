# Integração com o login único do IFSISTEM

Para equipes que vão usar o `ifsistem-auth` no lugar do próprio login. O sistema não vê mais a senha: o usuário entra
em `https://ifsistem-auth.onrender.com` e volta com um token JWT que o seu backend valida.

```
front  --login()-->  ifsistem-auth (CPF e senha)  --código-->  callback.html  --código + PKCE-->  token
front  --Authorization: Bearer <token>-->  seu backend  --confere a assinatura com a chave pública do auth-->  ok
```

Você precisa de três coisas: o cadastro do sistema (1), o backend validando o token (2) e o front (3).

## 1. Pedir o cadastro do sistema (clientId)

Abra uma issue ou um PR no repositório `ifsistem-auth` com:

| Campo | Exemplo |
|---|---|
| `clientId` (minúsculas, sem espaço) | `biblioteca` |
| `redirectUris` (produção e local) | `https://biblioteca.onrender.com/callback.html`, `http://localhost:8080/callback.html` |
| `postLogoutRedirectUris` | `https://biblioteca.onrender.com/`, `http://localhost:8080/` |

No PR, acrescente ao fim do `src/main/resources/application.properties`, com o próximo índice livre:

```properties
auth.clientes[2].client-id=biblioteca
auth.clientes[2].redirect-uris=https://biblioteca.onrender.com/callback.html,http://localhost:8080/callback.html
auth.clientes[2].post-logout-redirect-uris=https://biblioteca.onrender.com/,http://localhost:8080/
```

- As URIs são comparadas caractere por caractere, inclusive a barra final.
- Não há secret: o cliente é público e o PKCE é obrigatório (o script já faz).
- As origens das `redirectUris` são liberadas no CORS automaticamente.

## 2. Backend (Spring Boot)

Dependência (Boot 4; no Boot 3 o nome é `spring-boot-starter-oauth2-resource-server`):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
</dependency>
```

`application.properties`:

```properties
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${AUTH_URL:https://ifsistem-auth.onrender.com}/oauth2/jwks
# Recomendado: também confere quem emitiu o token. Com o jwk-set-uri acima, não há chamada ao auth na subida.
spring.security.oauth2.resourceserver.jwt.issuer-uri=${AUTH_URL:https://ifsistem-auth.onrender.com}
```

Classe que transforma a claim `perfil` em `ROLE_<PERFIL>` (copie e ajuste o pacote e as rotas):

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SegurancaConfig {

    /** perfil=ALUNO vira ROLE_ALUNO: use hasRole("ALUNO"). Sem perfil, o usuário não ganha papel nenhum. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String perfil = jwt.getClaimAsString("perfil");
            return perfil == null ? AuthorityUtils.NO_AUTHORITIES
                    : AuthorityUtils.createAuthorityList("ROLE_" + perfil);
        });
        return converter;
    }

    @Bean
    SecurityFilterChain seguranca(HttpSecurity http, JwtAuthenticationConverter perfis) throws Exception {
        return http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/index.html", "/callback.html", "/health").permitAll()
                        .requestMatchers("/admin/**").hasRole("SERVIDOR")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(j -> j.jwtAuthenticationConverter(perfis)))
                .build();
    }
}
```

No controller:

```java
@GetMapping("/meus-dados")
Map<String, Object> meusDados(@AuthenticationPrincipal Jwt jwt) {
    UUID usuarioId = UUID.fromString(jwt.getSubject());   // mesmo id da tabela usuarios de hoje
    return Map.of("id", usuarioId, "cpf", jwt.getClaimAsString("cpf"),
            "nome", jwt.getClaimAsString("nome"), "perfil", jwt.getClaimAsString("perfil"));
}
```

### O que vem no token

| Claim | Conteúdo |
|---|---|
| `sub` | id (UUID) do usuário, o mesmo da antiga `public.usuarios` |
| `cpf` | 11 dígitos, sem máscara |
| `nome` | nome completo |
| `perfil` | `ALUNO`, `PROFESSOR`, `SERVIDOR` ou `ADMINISTRADOR` |
| `iss`, `exp` | emissor e validade |

O access token (RS256) dura 1 hora e o refresh token, 8 horas. O refresh é de uso único: cada renovação entrega outro,
também válido por 8 horas. Um usuário inativado não renova mais, mas o access token que ele já tem vale no seu backend
até vencer. Envie ao backend só o `access_token`, nunca o `id_token`.

## 3. Front

Copie `https://ifsistem-auth.onrender.com/ifsistem-sso.js` para o seu projeto (assim a página não depende de o auth
estar acordado para carregar) ou referencie direto.

`sso.js` (configuração única, incluída em todas as páginas depois do script):

```js
const local = location.hostname === 'localhost';
IfsistemSSO.init({
    authUrl: local ? 'http://localhost:9000' : 'https://ifsistem-auth.onrender.com',
    clientId: 'biblioteca',
    redirectUri: location.origin + '/callback.html',
    postLogoutRedirectUri: location.origin + '/'
});
```

`callback.html`:

```html
<!doctype html>
<meta charset="utf-8">
<p id="msg">Entrando…</p>
<script src="/ifsistem-sso.js"></script>
<script src="/sso.js"></script>
<script>
    IfsistemSSO.handleCallback()
        .then(() => location.replace('/index.html'))
        .catch(e => { document.getElementById('msg').textContent = e.message; });
</script>
```

Nas páginas protegidas:

```js
if (!await IfsistemSSO.getToken()) {
    await IfsistemSSO.login();                               // vai para o login e volta em callback.html
} else {
    const { id, cpf, nome, perfil } = IfsistemSSO.usuario(); // só para exibir: quem autoriza é o backend
    const resp = await IfsistemSSO.fetch('/meus-dados');     // Authorization: Bearer automático
}
document.getElementById('sair').onclick = () => IfsistemSSO.logout();
```

| Função | O que faz |
|---|---|
| `init({authUrl, clientId, redirectUri, postLogoutRedirectUri?})` | Configura. Sem `postLogoutRedirectUri`, usa a raiz da `redirectUri`. |
| `login()` | Redireciona para o login, com PKCE, `state` e `nonce`. |
| `handleCallback()` | Na `redirectUri`: confere o retorno e troca o código pelo token. Devolve o usuário, ou `null` se a URL não é um retorno. |
| `getToken()` | Access token válido, renovado quando falta menos de 30 s. `null` = precisa de `login()`. |
| `fetch(url, opcoes)` | `fetch` com Bearer. Use só com URLs dos seus backends. Rejeita se não há sessão. |
| `usuario()` | `{id, cpf, nome, perfil}` lidos do token, ou `null`. |
| `logout()` | Revoga o refresh token, limpa a aba e encerra a sessão no auth. |

O token fica em `sessionStorage`: some ao fechar a aba. Uma aba nova chama `login()`, e quem ainda tem sessão no auth
volta sem digitar a senha. Como qualquer script da página consegue ler o token, não injete HTML vindo do usuário.

Exemplo funcionando: `https://ifsistem-auth.onrender.com/exemplo-cliente.html`.

## 4. Testar localmente

Suba o auth na porta 9000 (veja o [README](../README.md)) e o seu sistema com `AUTH_URL=http://localhost:9000`.

| Sintoma | Causa provável |
|---|---|
| 400 no `/oauth2/authorize` | `clientId` ou `redirectUri` diferente do cadastro |
| Erro de CORS no `/oauth2/token` | a origem da página não está em nenhuma `redirectUri` cadastrada |
| "Retorno de login inválido (state)" | o login começou em outra aba, ou `localhost` foi misturado com `127.0.0.1` |
| 401 no seu backend | `jwk-set-uri`/`issuer-uri` errados, token vencido ou `id_token` no lugar do `access_token` |
| 403 no seu backend | perfil sem permissão, ou o converter não está ligado no `oauth2ResourceServer` |
| 400 no logout | `postLogoutRedirectUri` diferente do cadastro |

## 5. Usuários

Os usuários são mantidos no auth, pela API `/usuarios` (somente `SERVIDOR`, com o access token): `GET /usuarios?perfil=`,
`GET /usuarios/{id}`, `POST /usuarios`, `PUT /usuarios/{id}`, `PATCH /usuarios/{id}/ativo` e `DELETE /usuarios/{id}`
(exclusão lógica). A migração copiou os usuários do aproveitamento **uma única vez**: contas criadas ou senhas trocadas lá
depois disso não chegam ao auth.
