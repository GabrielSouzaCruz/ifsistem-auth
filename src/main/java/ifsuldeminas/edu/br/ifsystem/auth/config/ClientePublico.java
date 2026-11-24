package ifsuldeminas.edu.br.ifsystem.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Refresh token para cliente publico (SPA). O Spring Authorization Server nao emite nem aceita por padrao;
 * aqui o refresh e de uso unico (rotacao, ver TokenSettings) e o cliente se identifica so pelo client_id nas
 * duas operacoes em que o proprio refresh token e a credencial: renovar e revogar.
 * A troca do codigo continua passando pela validacao padrao de PKCE.
 */
final class ClientePublico {
    private ClientePublico() {
    }

    /** Tipo proprio para o provider abaixo nunca atender a requisicao com PKCE. */
    static final class Token extends OAuth2ClientAuthenticationToken {
        Token(String clientId) {
            super(clientId, ClientAuthenticationMethod.NONE, null, Map.of());
        }
    }

    static final class Converter implements AuthenticationConverter {
        private static final RequestMatcher TOKEN = caminho("/oauth2/token");
        private static final RequestMatcher REVOKE = caminho("/oauth2/revoke");

        private static RequestMatcher caminho(String caminho) {
            return PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, caminho);
        }

        @Override
        public Authentication convert(HttpServletRequest request) {
            // Parametro na query ou repetido poderia fazer este conversor e o grant lerem valores diferentes
            // e, com isso, trocar um codigo sem code_verifier.
            if (request.getQueryString() != null
                    || request.getHeader("Authorization") != null
                    || !ausente(request, "client_secret") || !ausente(request, "client_assertion")
                    || !ausente(request, "code") || !ausente(request, "code_verifier"))
                return null;
            boolean renovar = TOKEN.matches(request) && unico(request, "refresh_token") != null
                    && AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(unico(request, "grant_type"));
            boolean revogar = REVOKE.matches(request) && unico(request, "token") != null;
            String clientId = unico(request, "client_id");
            return clientId != null && (renovar || revogar) ? new Token(clientId) : null;
        }

        private static boolean ausente(HttpServletRequest request, String nome) {
            return request.getParameterValues(nome) == null;
        }

        private static String unico(HttpServletRequest request, String nome) {
            String[] valores = request.getParameterValues(nome);
            return valores != null && valores.length == 1 && !valores[0].isBlank() ? valores[0] : null;
        }
    }

    static final class Provider implements AuthenticationProvider {
        private final RegisteredClientRepository clientes;

        Provider(RegisteredClientRepository clientes) {
            this.clientes = clientes;
        }

        @Override
        public Authentication authenticate(Authentication authentication) {
            RegisteredClient cliente = clientes.findByClientId(authentication.getPrincipal().toString());
            if (cliente == null
                    || !cliente.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE))
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
            return new OAuth2ClientAuthenticationToken(cliente, ClientAuthenticationMethod.NONE, null);
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return Token.class.isAssignableFrom(authentication);
        }
    }

    static final class GeradorRefreshToken implements OAuth2TokenGenerator<OAuth2RefreshToken> {
        private final StringKeyGenerator gerador =
                new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96);

        @Override
        public OAuth2RefreshToken generate(OAuth2TokenContext context) {
            if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType()))
                return null;
            Instant agora = Instant.now();
            return new OAuth2RefreshToken(gerador.generateKey(), agora,
                    agora.plus(context.getRegisteredClient().getTokenSettings().getRefreshTokenTimeToLive()));
        }
    }
}
