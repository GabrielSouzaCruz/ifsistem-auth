package ifsuldeminas.edu.br.ifsystem.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tela de usuarios da secretaria: cliente auth-admin e paginas publicas (a protecao esta na API). */
@SpringBootTest
@AutoConfigureMockMvc
class PaginaUsuariosTest {
    private static final String REDIRECT = "http://localhost:9000/usuarios.html";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RegisteredClientRepository clientes;

    @Test
    void clienteAuthAdminEstaRegistradoComoPublicoComPkce() {
        RegisteredClient c = clientes.findByClientId("auth-admin");
        assertThat(c).isNotNull();
        assertThat(c.getRedirectUris()).containsExactly(REDIRECT);
        assertThat(c.getPostLogoutRedirectUris()).containsExactly(REDIRECT);
        assertThat(c.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(c.getClientSettings().isRequireProofKey()).isTrue();
    }

    @Test
    void authorizeDoAuthAdminVaiParaOLogin() throws Exception {
        mvc.perform(get(java.net.URI.create(authorize(REDIRECT))).accept(MediaType.TEXT_HTML)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void authAdminNaoAceitaRedirectDeOutraPagina() throws Exception {
        mvc.perform(get(java.net.URI.create(authorize("http://localhost:9000/outra.html"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paginaEAssetsSaoPublicosMasApiContinuaProtegida() throws Exception {
        mvc.perform(get("/usuarios.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("js/usuarios.js")))
                .andExpect(content().string(containsString("ifsistem-sso.js")));
        mvc.perform(get("/js/usuarios.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("clientId: 'auth-admin'")));
        mvc.perform(get("/css/usuarios.css")).andExpect(status().isOk());
        mvc.perform(get("/ifsistem-sso.js")).andExpect(status().isOk());

        mvc.perform(get("/usuarios")).andExpect(status().isUnauthorized());
    }

    private static String authorize(String redirectUri) {
        return UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code").queryParam("client_id", "auth-admin")
                .queryParam("redirect_uri", redirectUri).queryParam("scope", "openid profile")
                .queryParam("state", "estado-123").queryParam("nonce", "nonce-123")
                .queryParam("code_challenge", Apoio.challenge()).queryParam("code_challenge_method", "S256")
                .encode().build().toUriString();
    }
}
