package ifsuldeminas.edu.br.ifsystem.auth.api;

import ifsuldeminas.edu.br.ifsystem.auth.config.AuthProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuracao publica para as telas do auth: URL do portal (o menu da tela de usuarios e fixo). */
@RestController
public class ConfigController {
    private final AuthProperties props;

    public ConfigController(AuthProperties props) {
        this.props = props;
    }

    @GetMapping("/config")
    public Config config() {
        return new Config(props.portalUrl());
    }

    public record Config(String portalUrl) {
    }
}
