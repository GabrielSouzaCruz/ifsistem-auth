package ifsuldeminas.edu.br.ifsystem.auth;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Usuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.repository.UsuarioRepository;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import static ifsuldeminas.edu.br.ifsystem.auth.Apoio.campo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fluxo OIDC real: Flyway + H2 (modo PostgreSQL) + JdbcOAuth2AuthorizationService. */
@SpringBootTest
@AutoConfigureMockMvc
class FluxoOidcTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioService usuarios;
    @Autowired
    private UsuarioRepository repositorio;
    @Autowired
    private JdbcTemplate jdbc;

    private JWTClaimsSet claims(String jwt) throws Exception {
        return SignedJWT.parse(jwt).getJWTClaimsSet();
    }

    @Test
    void semSessaoOAuthorizeMandaParaOLoginEDepoisVolta() throws Exception {
        Usuario u = Apoio.criar(usuarios, Perfil.ALUNO);
        MockHttpSession sessao = new MockHttpSession();
        mvc.perform(get(URI.create(Apoio.urlAuthorize())).session(sessao).accept(MediaType.TEXT_HTML))
                .andExpect(redirectedUrl("/login"));
        Apoio.login(mvc, sessao, u.getCpf(), Apoio.SENHA)
                .andExpect(header().string("Location", containsString("/oauth2/authorize?response_type=code")));
    }

    @Test
    void fluxoCompletoEmiteTokenRs256ComAsClaims() throws Exception {
        Usuario u = Apoio.criar(usuarios, Perfil.PROFESSOR);
        Instant antes = Instant.now();
        String json = Apoio.tokens(mvc, new MockHttpSession(), u.getCpf());

        SignedJWT jwt = SignedJWT.parse(campo(json, "access_token"));
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        JWTClaimsSet c = jwt.getJWTClaimsSet();
        assertThat(c.getIssuer()).isEqualTo("http://localhost:9000");
        assertThat(c.getSubject()).isEqualTo(u.getId().toString());
        assertThat(c.getStringClaim("cpf")).isEqualTo(u.getCpf());
        assertThat(c.getStringClaim("nome")).isEqualTo("José Ação PROFESSOR");
        assertThat(c.getStringClaim("perfil")).isEqualTo("PROFESSOR");
        assertThat(Duration.between(c.getIssueTime().toInstant(), c.getExpirationTime().toInstant()))
                .isEqualTo(Duration.ofHours(1));

        JWTClaimsSet id = claims(campo(json, "id_token"));
        assertThat(id.getStringClaim("nonce")).isEqualTo("nonce-123");
        assertThat(id.getStringClaim("perfil")).isEqualTo("PROFESSOR");

        java.sql.Timestamp expira = jdbc.queryForObject(
                "select refresh_token_expires_at from oauth2_authorization where principal_name = ?",
                java.sql.Timestamp.class, u.getId().toString());
        assertThat(expira.toInstant()).isBetween(antes.plus(Duration.ofHours(8)).minusSeconds(5),
                Instant.now().plus(Duration.ofHours(8)).plusSeconds(5));
    }

    @Test
    void assinaturaConfereComOJwksQueNaoExpoeAChavePrivada() throws Exception {
        String token = campo(Apoio.tokens(mvc, new MockHttpSession(),
                Apoio.criar(usuarios, Perfil.ALUNO).getCpf()), "access_token");
        String jwks = mvc.perform(get("/oauth2/jwks")).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        SignedJWT jwt = SignedJWT.parse(token);
        var chave = JWKSet.parse(jwks).getKeyByKeyId(jwt.getHeader().getKeyID()).toRSAKey();
        assertThat(jwt.verify(new RSASSAVerifier(chave))).isTrue();
    }

    @Test
    void descobertaAnunciaEndpointsEPkce() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration")).andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:9000"))
                .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost:9000/oauth2/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value("http://localhost:9000/oauth2/token"))
                .andExpect(jsonPath("$.jwks_uri").value("http://localhost:9000/oauth2/jwks"))
                .andExpect(jsonPath("$.end_session_endpoint").value("http://localhost:9000/connect/logout"))
                .andExpect(jsonPath("$.code_challenge_methods_supported", hasItem("S256")));
    }

    @Test
    void pkceEObrigatorioNoAuthorizeENaTroca() throws Exception {
        Usuario u = Apoio.criar(usuarios, Perfil.ALUNO);
        MockHttpSession sessao = new MockHttpSession();
        String codigo = Apoio.codigo(mvc, sessao, u.getCpf());

        String semPkce = Apoio.urlAuthorize().replaceAll("&code_challenge.*", "");
        mvc.perform(get(URI.create(semPkce)).session(sessao).accept(MediaType.TEXT_HTML))
                .andExpect(redirectedUrlPattern(Apoio.CALLBACK + "?error=invalid_request**"));

        mvc.perform(post("/oauth2/token").param("grant_type", "authorization_code")
                        .param("client_id", Apoio.CLIENTE).param("code", codigo)
                        .param("redirect_uri", Apoio.CALLBACK))
                .andExpect(status().isUnauthorized());
        Apoio.trocar(mvc, codigo, "verificador-errado-verificador-errado-verificador")
                .andExpect(status().isBadRequest());
    }

    /** O atalho de autenticacao do refresh nao pode servir para trocar um codigo sem code_verifier. */
    @Test
    void refreshNaoServeParaBurlarOPkce() throws Exception {
        String codigo = Apoio.codigo(mvc, new MockHttpSession(), Apoio.criar(usuarios, Perfil.ALUNO).getCpf());
        mvc.perform(post(URI.create("/oauth2/token?grant_type=refresh_token&refresh_token=x"))
                        .param("grant_type", "authorization_code").param("client_id", Apoio.CLIENTE)
                        .param("code", codigo).param("redirect_uri", Apoio.CALLBACK))
                .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.access_token").doesNotExist());
        mvc.perform(post("/oauth2/token").param("grant_type", "refresh_token", "authorization_code")
                        .param("refresh_token", "x").param("client_id", Apoio.CLIENTE)
                        .param("code", codigo).param("redirect_uri", Apoio.CALLBACK))
                .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.access_token").doesNotExist());
        mvc.perform(post("/oauth2/token").param("grant_type", "refresh_token").param("refresh_token", "x")
                        .param("client_id", Apoio.CLIENTE).param("code", codigo)
                        .param("redirect_uri", Apoio.CALLBACK))
                .andExpect(status().is4xxClientError()).andExpect(jsonPath("$.access_token").doesNotExist());
    }

    @Test
    void redirectNaoCadastradoERecusado() throws Exception {
        String url = Apoio.urlAuthorize().replaceAll("redirect_uri=[^&]+", "redirect_uri=https://malicioso.exemplo/");
        mvc.perform(get(URI.create(url)).accept(MediaType.TEXT_HTML)).andExpect(status().isBadRequest());
    }

    @Test
    void refreshRenovaEOTokenAntigoDeixaDeValer() throws Exception {
        Usuario u = Apoio.criar(usuarios, Perfil.ALUNO);
        String json = Apoio.tokens(mvc, new MockHttpSession(), u.getCpf());
        String novo = Apoio.renovar(mvc, campo(json, "refresh_token")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(campo(novo, "refresh_token")).isNotEqualTo(campo(json, "refresh_token"));
        assertThat(claims(campo(novo, "access_token")).getStringClaim("cpf")).isEqualTo(u.getCpf());
        Apoio.renovar(mvc, campo(json, "refresh_token")).andExpect(status().isBadRequest());
        Apoio.renovar(mvc, campo(novo, "refresh_token")).andExpect(status().isOk());
    }

    @Test
    void usuarioInativadoNaoRenovaNemTrocaCodigo() throws Exception {
        Usuario u = Apoio.criar(usuarios, Perfil.ALUNO);
        MockHttpSession sessao = new MockHttpSession();
        String json = Apoio.tokens(mvc, sessao, u.getCpf());
        String codigo = Apoio.codigo(mvc, new MockHttpSession(), u.getCpf());
        u.definirAtivo(false);
        repositorio.save(u);
        Apoio.renovar(mvc, campo(json, "refresh_token")).andExpect(status().isBadRequest());
        Apoio.trocar(mvc, codigo, Apoio.VERIFIER).andExpect(status().isBadRequest());
    }

    @Test
    void revogarInvalidaORefreshToken() throws Exception {
        String refresh = campo(Apoio.tokens(mvc, new MockHttpSession(),
                Apoio.criar(usuarios, Perfil.ALUNO).getCpf()), "refresh_token");
        mvc.perform(post("/oauth2/revoke").param("client_id", Apoio.CLIENTE).param("token", refresh))
                .andExpect(status().isOk());
        Apoio.renovar(mvc, refresh).andExpect(status().isBadRequest());
    }

    /** Cliente publico nao ganha acesso a outros endpoints so com o client_id. */
    @Test
    void clientIdSozinhoNaoAutenticaNaIntrospeccao() throws Exception {
        mvc.perform(post("/oauth2/introspect").param("client_id", Apoio.CLIENTE).param("token", "x"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void logoutOidcEncerraASessaoEVoltaParaOSistema() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        String json = Apoio.tokens(mvc, sessao, Apoio.criar(usuarios, Perfil.ALUNO).getCpf());
        // Como no script: o token ja foi renovado e o refresh e revogado antes do logout.
        json = Apoio.renovar(mvc, campo(json, "refresh_token")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(post("/oauth2/revoke").param("client_id", Apoio.CLIENTE)
                .param("token", campo(json, "refresh_token"))).andExpect(status().isOk());
        mvc.perform(get("/connect/logout").session(sessao).queryParam("id_token_hint", campo(json, "id_token"))
                        .queryParam("client_id", Apoio.CLIENTE)
                        .queryParam("post_logout_redirect_uri", "http://localhost:8080/"))
                .andExpect(redirectedUrl("http://localhost:8080/"));
        assertThat(sessao.isInvalid()).isTrue();
    }

    /** Na mesma origem o navegador manda o cookie junto do Bearer: a API nao pode mexer na sessao de login. */
    @Test
    void chamarAApiComOCookieNaoQuebraOLogout() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        String json = Apoio.tokens(mvc, sessao, Apoio.criar(usuarios, Perfil.SERVIDOR).getCpf());
        String idAntes = sessao.getId();
        mvc.perform(get("/usuarios").session(sessao).header("Authorization", "Bearer " + campo(json, "access_token")))
                .andExpect(status().isOk());
        assertThat(sessao.getId()).isEqualTo(idAntes);
        mvc.perform(get("/connect/logout").session(sessao).queryParam("id_token_hint", campo(json, "id_token"))
                        .queryParam("client_id", Apoio.CLIENTE)
                        .queryParam("post_logout_redirect_uri", "http://localhost:8080/"))
                .andExpect(redirectedUrl("http://localhost:8080/"));
    }

    @Test
    void logoutNaoRedirecionaParaUriNaoCadastrada() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        String json = Apoio.tokens(mvc, sessao, Apoio.criar(usuarios, Perfil.ALUNO).getCpf());
        mvc.perform(get("/connect/logout").session(sessao).queryParam("id_token_hint", campo(json, "id_token"))
                        .queryParam("client_id", Apoio.CLIENTE)
                        .queryParam("post_logout_redirect_uri", "https://malicioso.exemplo/"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void corsSoParaAOrigemDosSistemasCadastrados() throws Exception {
        mvc.perform(options("/oauth2/token").header("Origin", "http://localhost:8080")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"));
        mvc.perform(options("/oauth2/token").header("Origin", "https://malicioso.exemplo")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Autowired
    private ifsuldeminas.edu.br.ifsystem.auth.config.AuthProperties props;

    @Test
    void clienteChavesRegistradoECorsLiberaVercelELocalhost() throws Exception {
        var chaves = props.clientes().stream().filter(c -> c.clientId().equals("chaves")).findFirst().orElseThrow();
        assertThat(chaves.redirectUris()).containsExactlyInAnyOrder(
                "https://chaves-ifsistem.vercel.app/callback.html", "http://localhost:5173/callback.html");
        assertThat(chaves.postLogoutRedirectUris()).containsExactlyInAnyOrder(
                "https://chaves-ifsistem.vercel.app/", "http://localhost:5173/");
        for (String origem : new String[]{"https://chaves-ifsistem.vercel.app", "http://localhost:5173"})
            mvc.perform(options("/oauth2/token").header("Origin", origem)
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origem));
    }

    @Test
    void ssoSegundaAutorizacaoNaMesmaSessaoNaoPedeSenha()throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        Apoio.tokens(mvc, sessao, Apoio.criar(usuarios, Perfil.ALUNO).getCpf());
        mvc.perform(get(URI.create(Apoio.urlAuthorize())).session(sessao).accept(MediaType.TEXT_HTML))
                .andExpect(redirectedUrlPattern(Apoio.CALLBACK + "?code=**"))
                .andExpect(authenticated());
    }
}
