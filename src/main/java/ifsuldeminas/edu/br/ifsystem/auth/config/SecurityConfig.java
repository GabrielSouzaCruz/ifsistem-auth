package ifsuldeminas.edu.br.ifsystem.auth.config;

import ifsuldeminas.edu.br.ifsystem.auth.service.UsuarioDetailsService;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Usuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.service.UsuarioService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Configuration
class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Verifica o status depois da senha: CPF inexistente, senha errada e inativo respondem igual. */
    @Bean
    AuthenticationProvider loginPorCpf(UsuarioDetailsService usuarios, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(usuarios);
        provider.setPasswordEncoder(encoder);
        provider.setPreAuthenticationChecks(u -> {
        });
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return provider;
    }

    /** Necessarios para o logout OIDC conferir a sessao (claim sid). */
    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /** Endpoints do authorization server: /oauth2/*, /.well-known/*, /userinfo e /connect/logout. */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServer(HttpSecurity http, RegisteredClientRepository clientes)
            throws Exception {
        http.oauth2AuthorizationServer(as -> {
            http.securityMatcher(as.getEndpointsMatcher());
            as.oidc(Customizer.withDefaults());
            as.clientAuthentication(c -> c
                    .authenticationConverter(new ClientePublico.Converter())
                    .authenticationProvider(new ClientePublico.Provider(clientes)));
        });
        // So navegacao do navegador vai para o login; um fetch (Accept */*) recebe o erro HTTP.
        MediaTypeRequestMatcher navegador = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        navegador.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        return http.cors(Customizer.withDefaults())
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"), navegador))
                .build();
    }

    /** API de gestao: so SERVIDOR, com o access token do proprio auth. */
    @Bean
    @Order(2)
    SecurityFilterChain api(HttpSecurity http, UsuarioService usuarios) throws Exception {
        return http.securityMatcher("/usuarios", "/usuarios/**")
                .cors(Customizer.withDefaults())
                .csrf(c -> c.disable())
                // Sem trocar o id da sessao de login quando o cookie vem junto: quebraria o sid do logout OIDC.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        .sessionFixation(f -> f.none()))
                .authorizeHttpRequests(a -> a.anyRequest().hasRole(Perfil.SERVIDOR.name()))
                .oauth2ResourceServer(rs -> rs
                        .jwt(j -> j.jwtAuthenticationConverter(usuarioDoToken(usuarios)))
                        .authenticationEntryPoint((req, res, ex) ->
                                json(res, HttpServletResponse.SC_UNAUTHORIZED, "Nao autenticado"))
                        .accessDeniedHandler((req, res, ex) ->
                                json(res, HttpServletResponse.SC_FORBIDDEN, "Acesso negado")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                json(res, HttpServletResponse.SC_UNAUTHORIZED, "Nao autenticado"))
                        .accessDeniedHandler((req, res, ex) ->
                                json(res, HttpServletResponse.SC_FORBIDDEN, "Acesso negado")))
                .build();
    }

    /** Paginas: login, cadastro e arquivos estaticos. */
    @Bean
    @Order(3)
    SecurityFilterChain paginas(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/login", "/cadastro", "/health", "/config", "/error",
                                "/style.css", "/login.js", "/fonts/**", "/img/**", "/favicon.ico",
                                "/ifsistem-sso.js", "/exemplo-cliente.html",
                                "/usuarios.html", "/js/**", "/css/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(f -> f.loginPage("/login").usernameParameter("cpf").passwordParameter("senha")
                        .failureUrl("/login?erro").defaultSuccessUrl("/"))
                .exceptionHandling(e -> e.accessDeniedHandler(sessaoExpirada()))
                .build();
    }

    /**
     * CSRF invalido ou ausente no POST /login (sessao expirada, aba antiga): volta para o login com aviso.
     * A sessao nao e tocada, entao uma requisicao de autorizacao salva continua valendo; se a sessao morreu,
     * o login recomeca do zero. Qualquer outro 403 vai para a pagina de erro.
     */
    private static AccessDeniedHandler sessaoExpirada() {
        return (req, res, ex) -> {
            if (ex instanceof CsrfException && "POST".equals(req.getMethod())
                    && (req.getContextPath() + "/login").equals(req.getRequestURI()))
                res.sendRedirect(req.getContextPath() + "/login?expirado");
            else
                res.sendError(HttpServletResponse.SC_FORBIDDEN);
        };
    }

    /** Navegadores dos sistemas cadastrados chamam o token endpoint e a API de outra origem, sem cookies. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AuthProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.clientes().stream().flatMap(c -> c.redirectUris().stream())
                .map(SecurityConfig::origem).distinct().toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        for (String caminho : List.of("/oauth2/token", "/oauth2/revoke", "/oauth2/jwks", "/userinfo",
                "/.well-known/**", "/usuarios", "/usuarios/**"))
            source.registerCorsConfiguration(caminho, cors);
        return source;
    }

    private static String origem(String uri) {
        URI u = URI.create(uri);
        return u.getScheme() + "://" + u.getAuthority();
    }

    /** Perfil e status vem do banco: quem foi inativado perde o acesso antes de o token vencer. */
    private static Converter<Jwt, AbstractAuthenticationToken> usuarioDoToken(UsuarioService usuarios) {
        return jwt -> {
            Usuario u = usuarios.buscarAtivo(jwt.getSubject())
                    .orElseThrow(() -> new InvalidBearerTokenException("Usuario inexistente ou inativo"));
            return new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_" + u.getPerfil()));
        };
    }

    private static void json(HttpServletResponse res, int status, String erro) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"status\":" + status + ",\"erro\":\"" + erro + "\"}");
    }
}
