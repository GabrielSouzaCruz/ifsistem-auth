package ifsuldeminas.edu.br.ifsystem.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Configuracao "auth.*": issuer, portal, chave RSA e sistemas (clientes OIDC) cadastrados. */
@Validated
@ConfigurationProperties("auth")
public record AuthProperties(@NotNull @Pattern(regexp = "https?://.+", message = "defina AUTH_ISSUER com a URL publica") String issuer,
                             @DefaultValue("") String portalUrl,
                             @DefaultValue Rsa rsa,
                             @NotEmpty List<@Valid Cliente> clientes) {
    public AuthProperties {
        if (issuer != null && issuer.endsWith("/"))
            issuer = issuer.substring(0, issuer.length() - 1);
    }

    /** PEMs da chave. Com obrigatoria=true (profile render) a aplicacao nao sobe sem eles. */
    public record Rsa(@DefaultValue("") String privateKey, @DefaultValue("") String publicKey, boolean obrigatoria) {
    }

    public record Cliente(@NotBlank String clientId,
                          @NotEmpty List<@NotBlank String> redirectUris,
                          @NotNull @DefaultValue List<@NotBlank String> postLogoutRedirectUris) {
    }
}
