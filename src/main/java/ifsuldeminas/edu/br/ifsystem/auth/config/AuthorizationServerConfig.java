package ifsuldeminas.edu.br.ifsystem.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Usuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.service.UsuarioService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Clientes, tokens, chave e persistencia do authorization server. */
@Configuration
class AuthorizationServerConfig {

    @Bean
    AuthorizationServerSettings authorizationServerSettings(AuthProperties props) {
        return AuthorizationServerSettings.builder().issuer(props.issuer()).build();
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(AuthProperties props) {
        return new InMemoryRegisteredClientRepository(
                props.clientes().stream().map(AuthorizationServerConfig::cliente).toList());
    }

    /** Publico, PKCE obrigatorio, sem tela de consentimento. O id fixo mantem validas as autorizacoes salvas. */
    private static RegisteredClient cliente(AuthProperties.Cliente c) {
        return RegisteredClient.withId(c.clientId())
                .clientId(c.clientId())
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(c.redirectUris()))
                .postLogoutRedirectUris(uris -> uris.addAll(c.postLogoutRedirectUris()))
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder().requireProofKey(true)
                        .requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .refreshTokenTimeToLive(Duration.ofHours(8))
                        .reuseRefreshTokens(false)
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256).build())
                .build();
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcOperations jdbc, RegisteredClientRepository clientes) {
        return new JdbcOAuth2AuthorizationService(jdbc, clientes);
    }

    @Bean
    OAuth2AuthorizationConsentService consentService(JdbcOperations jdbc, RegisteredClientRepository clientes) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clientes);
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(AuthProperties props) {
        return new ImmutableJWKSet<>(new JWKSet(ChaveRsa.carregar(props.rsa())));
    }

    /** Usado pela propria API /usuarios. Exigir "scope" recusa id_token usado como access token. */
    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource, AuthProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                JwtValidators.createDefaultWithIssuer(props.issuer()),
                new JwtClaimValidator<>("scope", Objects::nonNull))));
        return decoder;
    }

    @Bean
    OAuth2TokenGenerator<?> tokenGenerator(JWKSource<SecurityContext> jwkSource,
                                           OAuth2TokenCustomizer<JwtEncodingContext> claims) {
        JwtGenerator jwt = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        jwt.setJwtCustomizer(claims);
        return new DelegatingOAuth2TokenGenerator(jwt, new ClientePublico.GeradorRefreshToken());
    }

    /**
     * Claims cpf, nome e perfil no access token e no id_token, sempre lidas do banco. Todo token (codigo ou
     * refresh) passa por aqui, entao um usuario inativado nao recebe nem renova token.
     */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> claimsDoUsuario(UsuarioService usuarios) {
        return context -> {
            Usuario u = usuarios.buscarAtivo(context.getPrincipal().getName())
                    .orElseThrow(() -> new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT));
            context.getClaims().claim("cpf", u.getCpf()).claim("nome", u.getNome())
                    .claim("perfil", u.getPerfil().name());
        };
    }
}
