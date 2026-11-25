package ifsuldeminas.edu.br.ifsystem.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;

/** GET /config: publico, com a URL do portal e o menu unificado (2 secoes). */
@SpringBootTest(properties = "auth.portal-url=https://portal.exemplo.test")
@AutoConfigureMockMvc
class ConfigTest {
    private static final String APROVEITAMENTO = "https://aproveitamento-ifsistem.onrender.com/aproveitamento.html";

    @Autowired
    private MockMvc mvc;

    @Test
    void configEPublicaEDevolvePortalEMenuComDuasSecoes() throws Exception {
        String json = mvc.perform(get("/config")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json).contains("Fila de solicitações", "Área da secretaria", "Usuários");
        mvc.perform(get("/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portalUrl").value("https://portal.exemplo.test"))
                .andExpect(jsonPath("$.menu.length()").value(2))
                .andExpect(jsonPath("$.menu[0].titulo").value("Aproveitamento"))
                .andExpect(jsonPath("$.menu[0].itens.length()").value(3))
                .andExpect(jsonPath("$.menu[0].itens[0].icone").value("lista"))
                .andExpect(jsonPath("$.menu[0].itens[0].url").value(APROVEITAMENTO + "#/"))
                .andExpect(jsonPath("$.menu[0].itens[0].perfis[0]").value("SERVIDOR"))
                .andExpect(jsonPath("$.menu[0].itens[1].nome").value("Cursos e disciplinas"))
                .andExpect(jsonPath("$.menu[0].itens[1].url").value(APROVEITAMENTO + "#/cursos"))
                .andExpect(jsonPath("$.menu[0].itens[2].nome").value("Coordenadores"))
                .andExpect(jsonPath("$.menu[0].itens[2].url").value(APROVEITAMENTO + "#/coordenadores"))
                .andExpect(jsonPath("$.menu[1].itens[0].url").value("/usuarios.html"))
                .andExpect(jsonPath("$.menu[1].itens[0].perfis[0]").value("SERVIDOR"));
    }

    @Test
    void paginaDeUsuariosTemOLinkDoPortalEOContainerDoMenu() throws Exception {
        mvc.perform(get("/usuarios.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"voltar-portal\"")))
                .andExpect(content().string(containsString("id=\"menus\"")));
        mvc.perform(get("/js/usuarios.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("fetch('/config')")));
    }
}
