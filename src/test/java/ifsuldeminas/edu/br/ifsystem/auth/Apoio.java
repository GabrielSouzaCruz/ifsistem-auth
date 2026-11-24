package ifsuldeminas.edu.br.ifsystem.auth;

import com.jayway.jsonpath.JsonPath;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.CriarUsuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Usuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.service.UsuarioService;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Percorre o fluxo real (authorize -> login -> codigo -> token) com MockMvc. */
final class Apoio {
    static final String SENHA = "senha-segura-1";
    static final String CLIENTE = "aproveitamento";
    static final String CALLBACK = "http://localhost:8080/callback.html";
    static final String VERIFIER = "verificador-de-teste-com-mais-de-quarenta-e-tres-caracteres";

    private Apoio() {
    }

    /** CPF valido aleatorio (so digitos). */
    static String cpf() {
        var r = ThreadLocalRandom.current();
        int[] d = new int[11];
        do {
            for (int i = 0; i < 9; i++) d[i] = r.nextInt(10);
        } while (java.util.Arrays.stream(d, 0, 9).distinct().count() == 1);
        for (int n = 9; n < 11; n++) {
            int soma = 0;
            for (int i = 0; i < n; i++) soma += d[i] * (n + 1 - i);
            d[n] = (soma * 10 % 11) % 10;
        }
        StringBuilder sb = new StringBuilder();
        for (int x : d) sb.append(x);
        return sb.toString();
    }

    static String mascarar(String cpf) {
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    static String email() {
        return "t" + UUID.randomUUID() + "@ifsuldeminas.edu.br";
    }

    static Usuario criar(UsuarioService usuarios, Perfil perfil) {
        return usuarios.criar(new CriarUsuario("José Ação " + perfil, cpf(), email(), "M-" + UUID.randomUUID(),
                SENHA), perfil);
    }

    static String challenge() {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String urlAuthorize() {
        return UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code").queryParam("client_id", CLIENTE)
                .queryParam("redirect_uri", CALLBACK).queryParam("scope", "openid profile")
                .queryParam("state", "estado-123").queryParam("nonce", "nonce-123")
                .queryParam("code_challenge", challenge()).queryParam("code_challenge_method", "S256")
                .encode().build().toUriString();
    }

    static ResultActions login(MockMvc mvc, MockHttpSession sessao, String cpf, String senha) throws Exception {
        return mvc.perform(post("/login").session(sessao).with(csrf()).param("cpf", cpf).param("senha", senha));
    }

    /** Faz o login e devolve o codigo de autorizacao. */
    static String codigo(MockMvc mvc, MockHttpSession sessao, String cpf) throws Exception {
        java.net.URI authorize = java.net.URI.create(urlAuthorize());
        mvc.perform(get(authorize).session(sessao).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
        login(mvc, sessao, cpf, SENHA).andExpect(status().is3xxRedirection());
        String destino = mvc.perform(get(authorize).session(sessao).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        return URLDecoder.decode(UriComponentsBuilder.fromUriString(destino).build().getQueryParams()
                .getFirst("code"), StandardCharsets.UTF_8);
    }

    static ResultActions trocar(MockMvc mvc, String codigo, String verifier) throws Exception {
        return mvc.perform(post("/oauth2/token").param("grant_type", "authorization_code")
                .param("client_id", CLIENTE).param("code", codigo).param("redirect_uri", CALLBACK)
                .param("code_verifier", verifier));
    }

    static ResultActions renovar(MockMvc mvc, String refresh) throws Exception {
        return mvc.perform(post("/oauth2/token").param("grant_type", "refresh_token")
                .param("client_id", CLIENTE).param("refresh_token", refresh));
    }

    /** Fluxo completo; devolve o JSON do token endpoint. */
    static String tokens(MockMvc mvc, MockHttpSession sessao, String cpf) throws Exception {
        MvcResult r = trocar(mvc, codigo(mvc, sessao, cpf), VERIFIER).andExpect(status().isOk()).andReturn();
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    static String bearer(MockMvc mvc, Usuario u) throws Exception {
        return "Bearer " + campo(tokens(mvc, new MockHttpSession(), u.getCpf()), "access_token");
    }

    static String campo(String json, String nome) {
        return JsonPath.read(json, "$." + nome);
    }
}
