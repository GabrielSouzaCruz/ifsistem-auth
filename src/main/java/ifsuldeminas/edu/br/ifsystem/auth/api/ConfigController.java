package ifsuldeminas.edu.br.ifsystem.auth.api;

import ifsuldeminas.edu.br.ifsystem.auth.config.AuthProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Configuracao publica para as telas do auth: URL do portal e menu unificado (filtrado por perfil no front). */
@RestController
public class ConfigController {
    private final AuthProperties props;

    public ConfigController(AuthProperties props) {
        this.props = props;
    }

    @GetMapping("/config")
    public Config config() {
        return new Config(props.portalUrl(), props.menu());
    }

    public record Config(String portalUrl, List<AuthProperties.Secao> menu) {
    }
}
