package ifsuldeminas.edu.br.ifsystem.auth;

import com.jayway.jsonpath.JsonPath;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Usuario;
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
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Busca paginada, perfis, senha (inicial, /me/senha), CORS e Swagger da API /usuarios. */
@SpringBootTest
@AutoConfigureMockMvc
class UsuariosBuscaSenhaCorsTest {
    private static final String VERCEL = "https://chaves-ifsistem.vercel.app";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioService usuarios;

    private ResultActions chamar(MockHttpServletRequestBuilder req, String bearer) throws Exception {
        return mvc.perform(req.header("Authorization", bearer));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String corpo) {
        return req.contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private String administrador() throws Exception {
        return Apoio.bearer(mvc, Apoio.criar(usuarios, Perfil.ADMINISTRADOR));
    }

    /** Cria um usuario pela API e devolve o JSON. */
    private String criarPelaApi(String bearer, String nome, String cpf) throws Exception {
        return chamar(json(post("/usuarios"), """
                {"cpf":"%s","nome":"%s","email":"%s","senhaInicial":"%s","perfil":"PROFESSOR"}
                """.formatted(cpf, nome, Apoio.email(), Apoio.SENHA)), bearer)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private static String marca() {
        Random r = new Random();
        StringBuilder sb = new StringBuilder("Zq");
        for (int i = 0; i < 10; i++) sb.append((char) ('a' + r.nextInt(26)));
        return sb.toString();
    }

    // ---------- perfis ----------

    @Test
    void administradorAcessaEAlunoEProfessorRecebem403() throws Exception {
        String admin = administrador();
        chamar(get("/usuarios"), admin).andExpect(status().isOk());
        criarPelaApi(admin, "Criado Pelo Admin", Apoio.cpf());

        for (Perfil p : List.of(Perfil.ALUNO, Perfil.PROFESSOR)) {
            String bearer = Apoio.bearer(mvc, Apoio.criar(usuarios, p));
            chamar(get("/usuarios"), bearer).andExpect(status().isForbidden());
            chamar(get("/usuarios/" + UUID.randomUUID()), bearer).andExpect(status().isForbidden());
            chamar(json(post("/usuarios"), "{}"), bearer).andExpect(status().isForbidden());
            chamar(json(put("/usuarios/" + UUID.randomUUID() + "/senha"), "{\"senha\":\"abcdefgh1\"}"), bearer)
                    .andExpect(status().isForbidden());
        }
    }

    // ---------- busca e paginacao ----------

    @Test
    void filtrosQCpfAtivoEPaginacao() throws Exception {
        String bearer = administrador();
        String marca = marca();
        String cpfA = Apoio.cpf(), cpfB = Apoio.cpf(), cpfC = Apoio.cpf();
        criarPelaApi(bearer, "Zé Ação " + marca + " Alfa", cpfA);
        String b = criarPelaApi(bearer, "José Ação " + marca + " Beta", cpfB);
        criarPelaApi(bearer, "Zélia Ação " + marca + " Gama", cpfC);
        String busca = "/usuarios?q=";

        // q sem diferenciar maiusculas nem acentos
        chamar(get(busca + "ze acao " + marca.toUpperCase()), bearer).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].cpf").value(cpfA));
        chamar(get(busca + "AÇÃO " + marca.toUpperCase()), bearer).andExpect(jsonPath("$.totalElementos").value(3));
        chamar(get(busca + "acao " + marca), bearer).andExpect(jsonPath("$.totalElementos").value(3));

        // q com parte do CPF (aceita mascara) e cpf exato
        chamar(get(busca + cpfB.substring(2, 9)), bearer).andExpect(jsonPath("$.conteudo[0].cpf").value(cpfB));
        chamar(get(busca + Apoio.mascarar(cpfB)), bearer)
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].cpf").value(cpfB));
        chamar(get("/usuarios?cpf=" + cpfC), bearer)
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Zélia Ação " + marca + " Gama"));
        chamar(get("/usuarios?cpf=" + cpfC.substring(0, 10)), bearer).andExpect(jsonPath("$.totalElementos").value(0));

        // ativo + perfil
        chamar(json(patch("/usuarios/" + JsonPath.<String>read(b, "$.id") + "/ativo"), "{\"ativo\":false}"), bearer)
                .andExpect(status().isOk());
        chamar(get(busca + "acao " + marca + "&ativo=false"), bearer)
                .andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.conteudo[0].cpf").value(cpfB));
        chamar(get(busca + "acao " + marca + "&ativo=true"), bearer).andExpect(jsonPath("$.totalElementos").value(2));
        chamar(get(busca + "acao " + marca + "&perfil=ALUNO"), bearer).andExpect(jsonPath("$.totalElementos").value(0));

        // paginacao, ordenada por nome sem acento (Jose, Ze, Zelia)
        chamar(get(busca + marca + "&size=2&page=0&sort=nome"), bearer)
                .andExpect(jsonPath("$.pagina").value(0)).andExpect(jsonPath("$.tamanho").value(2))
                .andExpect(jsonPath("$.totalElementos").value(3)).andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.conteudo.length()").value(2))
                .andExpect(jsonPath("$.conteudo[0].nome").value("José Ação " + marca + " Beta"))
                .andExpect(jsonPath("$.conteudo[1].nome").value("Zé Ação " + marca + " Alfa"));
        chamar(get(busca + marca + "&size=2&page=1"), bearer)
                .andExpect(jsonPath("$.conteudo.length()").value(1))
                .andExpect(jsonPath("$.conteudo[0].nome").value("Zélia Ação " + marca + " Gama"));
        chamar(get(busca + marca + "&size=2&page=5"), bearer)
                .andExpect(jsonPath("$.conteudo.length()").value(0)).andExpect(jsonPath("$.totalElementos").value(3));
    }

    @Test
    void paginacaoTemPadraoMaximoEValidacao() throws Exception {
        String bearer = administrador();
        chamar(get("/usuarios"), bearer).andExpect(jsonPath("$.pagina").value(0)).andExpect(jsonPath("$.tamanho").value(20));
        chamar(get("/usuarios?size=500"), bearer).andExpect(jsonPath("$.tamanho").value(100));
        chamar(get("/usuarios?page=-1"), bearer).andExpect(status().isBadRequest());
        chamar(get("/usuarios?size=0"), bearer).andExpect(status().isBadRequest());
        chamar(get("/usuarios?sort=cpf"), bearer).andExpect(status().isBadRequest());
    }

    // ---------- senha ----------

    @Test
    void senhaInicialEObrigatoriaEPermiteOPrimeiroAcesso() throws Exception {
        String bearer = administrador();
        String cpf = Apoio.cpf();
        String corpo = """
                {"cpf":"%s","nome":"Sem Senha","email":"%s","perfil":"PROFESSOR"%s}
                """;
        chamar(json(post("/usuarios"), corpo.formatted(cpf, Apoio.email(), "")), bearer)
                .andExpect(status().isBadRequest());
        chamar(json(post("/usuarios"), corpo.formatted(cpf, Apoio.email(), ",\"senhaInicial\":\"curta\"")), bearer)
                .andExpect(status().isBadRequest());

        criarPelaApi(bearer, "Com Senha", cpf);
        Apoio.login(mvc, new MockHttpSession(), cpf, Apoio.SENHA).andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("erro"));
    }

    @Test
    void meSenhaComSenhaAtualErradaDa400ESenhaNaoMuda() throws Exception {
        Usuario u = Apoio.criar(usuarios, Perfil.ALUNO);
        String bearer = Apoio.bearer(mvc, u);
        chamar(json(put("/me/senha"), "{\"senhaAtual\":\"errada-123\",\"novaSenha\":\"nova-senha-123\"}"), bearer)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro").value("Senha atual incorreta"));
        chamar(json(put("/me/senha"), "{\"senhaAtual\":\"%s\",\"novaSenha\":\"curta\"}".formatted(Apoio.SENHA)), bearer)
                .andExpect(status().isBadRequest());
        chamar(json(put("/me/senha"), "{}"), bearer).andExpect(status().isBadRequest());
        Apoio.login(mvc, new MockHttpSession(), u.getCpf(), Apoio.SENHA)
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("erro"));
        mvc.perform(json(put("/me/senha"), "{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void meSenhaCertaDa204TrocaASenhaERevogaAsSessoes() throws Exception {
        for (Perfil p : List.of(Perfil.ALUNO, Perfil.PROFESSOR, Perfil.SERVIDOR)) {
            Usuario u = Apoio.criar(usuarios, p);
            String tokens = Apoio.tokens(mvc, new MockHttpSession(), u.getCpf());
            String bearer = "Bearer " + Apoio.campo(tokens, "access_token");
            String refresh = Apoio.campo(tokens, "refresh_token");

            chamar(json(put("/me/senha"), "{\"senhaAtual\":\"%s\",\"novaSenha\":\"nova-senha-123\"}"
                    .formatted(Apoio.SENHA)), bearer).andExpect(status().isNoContent());

            Apoio.renovar(mvc, refresh).andExpect(status().isBadRequest());
            Apoio.login(mvc, new MockHttpSession(), u.getCpf(), Apoio.SENHA).andExpect(redirectedUrl("/login?erro"));
            Apoio.login(mvc, new MockHttpSession(), u.getCpf(), "nova-senha-123")
                    .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("erro"));
        }
    }

    @Test
    void nenhumaRespostaTemSenha() throws Exception {
        String bearer = administrador();
        String criado = criarPelaApi(bearer, "Sem Segredo", Apoio.cpf());
        String id = JsonPath.read(criado, "$.id");
        List<String> respostas = List.of(criado,
                chamar(get("/usuarios/" + id), bearer).andReturn().getResponse().getContentAsString(),
                chamar(get("/usuarios?q=Sem Segredo"), bearer).andReturn().getResponse().getContentAsString(),
                chamar(json(put("/usuarios/" + id), "{\"nome\":\"Sem Segredo\",\"email\":\"%s\"}".formatted(Apoio.email())),
                        bearer).andReturn().getResponse().getContentAsString(),
                chamar(json(patch("/usuarios/" + id + "/ativo"), "{\"ativo\":true}"), bearer)
                        .andReturn().getResponse().getContentAsString());
        for (String r : respostas) {
            assertThat(r).isNotBlank().doesNotContainIgnoringCase("senha").doesNotContain("$2a$").doesNotContain("$2b$");
        }
        chamar(get("/usuarios/" + id), bearer).andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    // ---------- CORS ----------

    @Test
    void preflightDaOrigemDoVercelEmUsuariosEMe() throws Exception {
        for (String caminho : List.of("/usuarios", "/usuarios/" + UUID.randomUUID(), "/me/senha")) {
            for (String metodo : List.of("GET", "POST", "PUT", "PATCH", "DELETE")) {
                mvc.perform(options(caminho).header("Origin", VERCEL)
                                .header("Access-Control-Request-Method", metodo)
                                .header("Access-Control-Request-Headers", "authorization,content-type"))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Access-Control-Allow-Origin", VERCEL))
                        .andExpect(header().string("Access-Control-Allow-Methods", containsString(metodo)))
                        .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization")));
            }
        }
        mvc.perform(options("/usuarios").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/usuarios").header("Origin", "https://intruso.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void respostaReaLTrazOCabecalhoCors() throws Exception {
        chamar(get("/usuarios").header("Origin", VERCEL), administrador())
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", VERCEL));
    }

    // ---------- Swagger ----------

    @Test
    void swaggerDocumentaSoUsuariosEMe() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        String doc = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(content().string(containsString("\"bearerFormat\":\"JWT\"")))
                .andExpect(content().string(containsString("/me/senha")))
                .andExpect(content().string(containsString("senhaInicial")))
                .andExpect(content().string(not(containsString("/health"))))
                .andExpect(content().string(not(containsString("/config"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<java.util.Map<String, Object>>read(doc, "$.paths").keySet())
                .allMatch(p -> p.startsWith("/usuarios") || p.startsWith("/me"));
    }
}
