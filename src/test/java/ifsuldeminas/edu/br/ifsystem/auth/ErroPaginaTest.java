package ifsuldeminas.edu.br.ifsystem.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Servidor real (o 404/400 vem do container, que MockMvc nao encaminha para /error). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "auth.portal-url=https://portal.exemplo.edu.br/")
@AutoConfigureMockMvc
class ErroPaginaTest {
    @LocalServerPort
    private int porta;
    @Autowired
    private MockMvc mvc;

    private HttpResponse<String> get(String caminho, String accept) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Accept", accept).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void csrfInvalidoNoLoginVoltaParaLoginExpirado() throws Exception {
        mvc.perform(post("/login").param("cpf", "1").param("senha", "x").param("_csrf", "invalido"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login?expirado"));
        mvc.perform(post("/login").param("cpf", "1").param("senha", "x"))
                .andExpect(redirectedUrl("/login?expirado"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/login?expirado"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Sua sessão expirou. Entre novamente.")));
    }

    @Test
    void notFoundDevolvePaginaPropria() throws Exception {
        HttpResponse<String> r = get("/img/nao-existe.png", "text/html");
        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(r.body()).contains("Página não encontrada", "Voltar ao Portal", "https://portal.exemplo.edu.br/")
                .doesNotContain("Whitelabel", "{{", "Exception");
    }

    @Test
    void authorizeComSistemaDesconhecidoExplicaOProblema() throws Exception {
        HttpResponse<String> r = get("/oauth2/authorize?response_type=code&client_id=naoexiste"
                + "&redirect_uri=https://x.com/cb&scope=openid&code_challenge=abc&code_challenge_method=S256",
                "text/html");
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).contains("Sistema não cadastrado ou endereço de retorno inválido. Avise a equipe responsável.",
                "Voltar ao Portal").doesNotContain("Whitelabel");
    }

    @Test
    void semAcceptHtmlRespondeJson() throws Exception {
        HttpResponse<String> r = get("/img/nao-existe.png", "application/json");
        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(r.body()).contains("\"status\":404");
    }
}
