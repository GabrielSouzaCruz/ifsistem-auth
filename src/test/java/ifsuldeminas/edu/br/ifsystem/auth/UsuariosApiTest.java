package ifsuldeminas.edu.br.ifsystem.auth;

import com.jayway.jsonpath.JsonPath;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.StatusUsuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Usuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.repository.UsuarioRepository;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UsuariosApiTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioService usuarios;
    @Autowired
    private UsuarioRepository repositorio;

    private ResultActions chamar(MockHttpServletRequestBuilder req, String bearer) throws Exception {
        return mvc.perform(req.header("Authorization", bearer));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String corpo) {
        return req.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private String servidor() throws Exception {
        return Apoio.bearer(mvc, Apoio.criar(usuarios, Perfil.SERVIDOR));
    }

    @Test
    void semTokenOuComTokenInvalidoDa401() throws Exception {
        mvc.perform(get("/usuarios")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        chamar(get("/usuarios"), "Bearer lixo.lixo.lixo").andExpect(status().isUnauthorized());
    }

    @Test
    void soServidorAcessa() throws Exception {
        chamar(get("/usuarios"), servidor()).andExpect(status().isOk());
        for (Perfil p : List.of(Perfil.ALUNO, Perfil.PROFESSOR)) {
            String bearer = Apoio.bearer(mvc, Apoio.criar(usuarios, p));
            chamar(get("/usuarios"), bearer).andExpect(status().isForbidden());
            chamar(json(post("/usuarios"), "{}"), bearer).andExpect(status().isForbidden());
            chamar(delete("/usuarios/" + UUID.randomUUID()), bearer).andExpect(status().isForbidden());
        }
    }

    @Test
    void idTokenNaoVale() throws Exception {
        Usuario u = Apoio.criar(usuarios, Perfil.SERVIDOR);
        String idToken = Apoio.campo(Apoio.tokens(mvc, new MockHttpSession(), u.getCpf()), "id_token");
        chamar(get("/usuarios"), "Bearer " + idToken).andExpect(status().isUnauthorized());
    }

    @Test
    void servidorInativadoOuRebaixadoPerdeOAcessoAntesDoTokenVencer() throws Exception {
        Usuario u = Apoio.criar(usuarios, Perfil.SERVIDOR);
        String bearer = Apoio.bearer(mvc, u);
        u.alterarPerfil(Perfil.PROFESSOR);
        u = repositorio.save(u);
        chamar(get("/usuarios"), bearer).andExpect(status().isForbidden());
        u.definirAtivo(false);
        repositorio.save(u);
        chamar(get("/usuarios"), bearer).andExpect(status().isUnauthorized());
    }

    @Test
    void crudCompleto() throws Exception {
        String bearer = servidor();
        String cpf = Apoio.cpf();
        String criado = chamar(json(post("/usuarios"), """
                {"cpf":"%s","nome":"Prof Teste","email":"%s","senha":"%s","perfil":"PROFESSOR"}
                """.formatted(cpf, Apoio.email(), Apoio.SENHA)), bearer)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.perfil").value("PROFESSOR"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String url = "/usuarios/" + JsonPath.<String>read(criado, "$.id");

        chamar(get(url), bearer).andExpect(status().isOk()).andExpect(jsonPath("$.cpf").value(cpf));
        String lista = chamar(get("/usuarios?perfil=PROFESSOR"), bearer).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(lista, "$[*].perfil")).containsOnly("PROFESSOR");

        chamar(json(put(url), "{\"nome\":\"Novo Nome\",\"email\":\"%s\",\"perfil\":\"SERVIDOR\"}"
                .formatted(Apoio.email())), bearer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Novo Nome"))
                .andExpect(jsonPath("$.perfil").value("SERVIDOR"));

        chamar(json(patch(url + "/ativo"), "{\"ativo\":false}"), bearer)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INATIVO"));
        chamar(json(patch(url + "/ativo"), "{\"ativo\":true}"), bearer)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ATIVO"));

        chamar(delete(url), bearer).andExpect(status().isNoContent());
        assertThat(repositorio.findByCpf(cpf).orElseThrow().getStatus()).isEqualTo(StatusUsuario.EXCLUIDO);
        lista = chamar(get("/usuarios"), bearer).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(lista, "$[*].cpf")).doesNotContain(cpf);
    }

    @Test
    void validacoesDaApi() throws Exception {
        String bearer = servidor();
        chamar(json(post("/usuarios"), """
                {"cpf":"%s","nome":"X","email":"%s","senha":"%s","perfil":"ALUNO"}
                """.formatted(Apoio.cpf(), Apoio.email(), Apoio.SENHA)), bearer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Perfil deve ser PROFESSOR ou SERVIDOR"));
        chamar(get("/usuarios/" + UUID.randomUUID()), bearer).andExpect(status().isNotFound());

        Usuario a = Apoio.criar(usuarios, Perfil.ALUNO);
        Usuario b = Apoio.criar(usuarios, Perfil.ALUNO);
        chamar(json(put("/usuarios/" + a.getId()), "{\"nome\":\"A\",\"email\":\"%s\"}".formatted(b.getEmail())),
                bearer).andExpect(status().isConflict());
        chamar(json(put("/usuarios/" + a.getId()), "{\"nome\":\"A\",\"email\":\"%s\",\"perfil\":\"ADMINISTRADOR\"}"
                .formatted(a.getEmail())), bearer).andExpect(status().isBadRequest());
    }

    @Test
    void redefinirSenhaTrocaASenhaDeLogin() throws Exception {
        String bearer = servidor();
        Usuario prof = Apoio.criar(usuarios, Perfil.PROFESSOR);
        String url = "/usuarios/" + prof.getId() + "/senha";

        chamar(json(put(url), "{\"senha\":\"nova-senha-123\"}"), bearer).andExpect(status().isNoContent());

        Apoio.login(mvc, new MockHttpSession(), prof.getCpf(), Apoio.SENHA)
                .andExpect(redirectedUrl("/login?erro"));
        Apoio.login(mvc, new MockHttpSession(), prof.getCpf(), "nova-senha-123")
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("erro"));
    }

    @Test
    void redefinirSenhaEInativarRevogamORefreshToken() throws Exception {
        String bearer = servidor();
        for (String acao : List.of("senha", "ativo")) {
            Usuario u = Apoio.criar(usuarios, Perfil.PROFESSOR);
            String refresh = Apoio.campo(Apoio.tokens(mvc, new MockHttpSession(), u.getCpf()), "refresh_token");
            String url = "/usuarios/" + u.getId() + "/" + acao;
            if (acao.equals("senha"))
                chamar(json(put(url), "{\"senha\":\"nova-senha-123\"}"), bearer).andExpect(status().isNoContent());
            else
                chamar(json(patch(url), "{\"ativo\":false}"), bearer).andExpect(status().isOk());
            Apoio.renovar(mvc, refresh).andExpect(status().isBadRequest());
        }
    }

    @Test
    void ativarNaoRevogaSessoes() throws Exception {
        String bearer = servidor();
        Usuario u = Apoio.criar(usuarios, Perfil.PROFESSOR);
        String refresh = Apoio.campo(Apoio.tokens(mvc, new MockHttpSession(), u.getCpf()), "refresh_token");
        chamar(json(patch("/usuarios/" + u.getId() + "/ativo"), "{\"ativo\":true}"), bearer)
                .andExpect(status().isOk());
        Apoio.renovar(mvc, refresh).andExpect(status().isOk());
    }

    @Test
    void redefinirSenhaValidaOsDados() throws Exception {
        String bearer = servidor();
        String url = "/usuarios/" + Apoio.criar(usuarios, Perfil.PROFESSOR).getId() + "/senha";
        chamar(json(put(url), "{\"senha\":\"curta\"}"), bearer).andExpect(status().isBadRequest());
        chamar(json(put(url), "{}"), bearer).andExpect(status().isBadRequest());
        chamar(json(put(url), "{\"senha\":\"" + "x".repeat(73) + "\"}"), bearer).andExpect(status().isBadRequest());
        chamar(json(put("/usuarios/" + UUID.randomUUID() + "/senha"), "{\"senha\":\"nova-senha-123\"}"), bearer)
                .andExpect(status().isNotFound());
    }

    @Test
    void redefinirSenhaExigeServidor() throws Exception {
        Usuario alvo = Apoio.criar(usuarios, Perfil.PROFESSOR);
        String url = "/usuarios/" + alvo.getId() + "/senha";
        String corpo = "{\"senha\":\"nova-senha-123\"}";

        mvc.perform(json(put(url), corpo)).andExpect(status().isUnauthorized());
        for (Perfil p : List.of(Perfil.ALUNO, Perfil.PROFESSOR)) {
            chamar(json(put(url), corpo), Apoio.bearer(mvc, Apoio.criar(usuarios, p)))
                    .andExpect(status().isForbidden());
        }
        // A senha continua a antiga: nada foi alterado pelos pedidos negados.
        Apoio.login(mvc, new MockHttpSession(), alvo.getCpf(), Apoio.SENHA)
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("erro"));
    }

    @Test
    void servidorNaoInativaExcluiNemRebaixaASiMesmo() throws Exception {
        Usuario u = Apoio.criar(usuarios, Perfil.SERVIDOR);
        String bearer = Apoio.bearer(mvc, u);
        String url = "/usuarios/" + u.getId();
        chamar(json(patch(url + "/ativo"), "{\"ativo\":false}"), bearer).andExpect(status().isConflict());
        chamar(delete(url), bearer).andExpect(status().isConflict());
        chamar(json(put(url), "{\"nome\":\"A\",\"email\":\"%s\",\"perfil\":\"ALUNO\"}".formatted(u.getEmail())),
                bearer).andExpect(status().isConflict());
    }
}
