package ifsuldeminas.edu.br.ifsystem.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /config: publico, so com a URL do portal (sem menu); a tela de usuarios nao mostra nada de outros modulos. */
@SpringBootTest(properties = "auth.portal-url=https://portal.exemplo.test")
@AutoConfigureMockMvc
class ConfigTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void configEPublicaEDevolveSoOPortalUrlSemMenu() throws Exception {
        mvc.perform(get("/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portalUrl").value("https://portal.exemplo.test"))
                .andExpect(jsonPath("$.menu").doesNotExist());
    }

    @Test
    void paginaDeUsuariosTemSoOLinkDoPortalEOItemUsuarios() throws Exception {
        mvc.perform(get("/usuarios.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"voltar-portal\"")))
                .andExpect(content().string(containsString("rios</span></a>")))
                .andExpect(content().string(not(containsString("id=\"menus\""))))
                .andExpect(content().string(not(containsString("Aproveitamento"))))
                .andExpect(content().string(not(containsString("aproveitamento"))));
        mvc.perform(get("/js/usuarios.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("fetch('/config')")))
                .andExpect(content().string(not(containsString("aproveitamento"))));
    }
}
