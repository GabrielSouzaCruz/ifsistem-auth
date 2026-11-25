package ifsuldeminas.edu.br.ifsystem.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentacao da API de usuarios (Swagger UI em /swagger-ui.html). */
@Configuration
class OpenApiConfig {
    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("IFSISTEM Auth — API de usuários")
                        .version("1")
                        .description("Gestão de usuários (/usuarios, perfis SERVIDOR e ADMINISTRADOR) e troca da própria "
                                + "senha (/me/senha). Use o access token do próprio usuário, obtido pelo ifsistem-sso.js."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("Access token do login único (não o id_token)")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
