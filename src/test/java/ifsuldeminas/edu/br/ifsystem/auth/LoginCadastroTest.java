package ifsuldeminas.edu.br.ifsystem.auth;

import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.StatusUsuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Usuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.repository.UsuarioRepository;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "auth.portal-url=https://portal.exemplo.edu.br/")
@AutoConfigureMockMvc
class LoginCadastroTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private UsuarioService usuarios;
    @Autowired
    private UsuarioRepository repositorio;

    private ResultActions login(String cpf, String senha) throws Exception {
        return Apoio.login(mvc, new MockHttpSession(), cpf, senha);
    }

    private ResultActions cadastrar(String cpf, String email, String senha) throws Exception {
        return mvc.perform(post("/cadastro").with(csrf()).param("nome", "Aluno Teste").param("cpf", cpf)
                .param("email", email).param("matricula", "M-" + cpf).param("senha", senha));
    }

    @Test
    void loginCorretoComESemMascara() throws Exception {
        Usuario u = Apoio.criar(usuarios, Perfil.ALUNO);
        login(u.getCpf(), Apoio.SENHA).andExpect(authenticated().withUsername(u.getId().toString()));
        login(Apoio.mascarar(u.getCpf()), Apoio.SENHA).andExpect(authenticated());
    }

    @Test
    void senhaErradaECpfInexistenteRespondemIgual() throws Exception {
        Usuario u = Apoio.criar(usuarios, Perfil.ALUNO);
        login(u.getCpf(), "errada-123").andExpect(unauthenticated()).andExpect(redirectedUrl("/login?erro"));
        login(Apoio.cpf(), Apoio.SENHA).andExpect(unauthenticated()).andExpect(redirectedUrl("/login?erro"));
    }

    @Test
    void qualquerStatusDiferenteDeAtivoNaoEntra() throws Exception {
        for (StatusUsuario status : StatusUsuario.values()) {
            Usuario u = Apoio.criar(usuarios, Perfil.ALUNO);
            u.alterarStatus(status);
            repositorio.save(u);
            if (status == StatusUsuario.ATIVO)
                login(u.getCpf(), Apoio.SENHA).andExpect(authenticated());
            else
                login(u.getCpf(), Apoio.SENHA).andExpect(unauthenticated()).andExpect(redirectedUrl("/login?erro"));
        }
    }

    @Test
    void loginSemCsrfERecusado() throws Exception {
        Usuario u = Apoio.criar(usuarios, Perfil.ALUNO);
        mvc.perform(post("/login").param("cpf", u.getCpf()).param("senha", Apoio.SENHA))
                .andExpect(status().isForbidden()).andExpect(unauthenticated());
    }

    @Test
    void paginaDeLoginTemCsrfCadastroEPortal() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\" value=\"")))
                .andExpect(content().string(containsString("data-mascara=\"cpf\"")))
                .andExpect(content().string(containsString("href=\"/cadastro\"")))
                .andExpect(content().string(containsString("href=\"https://portal.exemplo.edu.br/\"")))
                .andExpect(content().string(not(containsString("{{"))));
        mvc.perform(get("/login?erro")).andExpect(content().string(containsString("CPF ou senha inválidos.")));
    }

    @Test
    void cadastroCriaAlunoComHashEIgnoraPerfilEnviado() throws Exception {
        String cpf = Apoio.cpf();
        mvc.perform(post("/cadastro").with(csrf()).param("nome", "Aluno").param("cpf", Apoio.mascarar(cpf))
                        .param("email", Apoio.email()).param("matricula", "M-" + cpf).param("senha", Apoio.SENHA)
                        .param("perfil", "SERVIDOR"))
                .andExpect(redirectedUrl("/login?cadastrado"));
        Usuario salvo = repositorio.findByCpf(cpf).orElseThrow();
        assertThat(salvo.getPerfil()).isEqualTo(Perfil.ALUNO);
        assertThat(salvo.getSenhaHash()).startsWith("$2");
        login(cpf, Apoio.SENHA).andExpect(authenticated());
    }

    @Test
    void cadastroAplicaAsRegrasDoAproveitamento() throws Exception {
        String cpf = Apoio.cpf();
        cadastrar(cpf, "t" + cpf + "@gmail.com", Apoio.SENHA).andExpect(status().isFound());
        cadastrar(cpf, Apoio.email(), Apoio.SENHA).andExpect(status().isConflict());
        cadastrar(Apoio.cpf(), "T" + cpf + "@GMAIL.com", Apoio.SENHA).andExpect(status().isConflict());
        cadastrar(Apoio.cpf(), "semarroba.com", Apoio.SENHA).andExpect(status().isBadRequest());
        cadastrar(Apoio.cpf(), Apoio.email(), "1234567").andExpect(status().isBadRequest());
        cadastrar(Apoio.cpf(), Apoio.email(), "x".repeat(73)).andExpect(status().isBadRequest());
        cadastrar("11111111111", Apoio.email(), Apoio.SENHA).andExpect(status().isBadRequest());
        cadastrar("52998224726", Apoio.email(), Apoio.SENHA).andExpect(status().isBadRequest());
    }

    @Test
    void cadastroRecusadoEscapaOQueFoiDigitado() throws Exception {
        mvc.perform(post("/cadastro").with(csrf()).param("nome", "\"><script>x</script>{{csrf}}")
                        .param("cpf", "1").param("email", "a@b.c").param("matricula", "1").param("senha", "12345678"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("<script>x"))))
                .andExpect(content().string(containsString("&lt;script&gt;x&lt;/script&gt;{{csrf}}")))
                .andExpect(content().string(containsString("CPF inválido.")));
    }

    @Test
    void healthEArquivosPublicos() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
        mvc.perform(get("/ifsistem-sso.js")).andExpect(status().isOk());
        mvc.perform(get("/exemplo-cliente.html")).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(redirectedUrl("https://portal.exemplo.edu.br/"));
    }
}
