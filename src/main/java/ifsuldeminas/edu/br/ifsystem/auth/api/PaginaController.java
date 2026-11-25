package ifsuldeminas.edu.br.ifsystem.auth.api;

import ifsuldeminas.edu.br.ifsystem.auth.config.AuthProperties;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.CriarUsuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.service.UsuarioService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Paginas de login e cadastro. O POST /login e do Spring Security. */
@RestController
public class PaginaController {
    private static final Pattern MARCADOR = Pattern.compile("\\{\\{(\\w+)}}");
    private static final String CSP = "default-src 'self'; base-uri 'none'; frame-ancestors 'none'";

    private final UsuarioService usuarios;
    private final String portalUrl;
    private final String htmlLogin = ler("paginas/login.html");
    private final String htmlCadastro = ler("paginas/cadastro.html");

    public PaginaController(UsuarioService usuarios, AuthProperties props) {
        this.usuarios = usuarios;
        this.portalUrl = props.portalUrl().isBlank() ? "/" : props.portalUrl();
    }

    /** Sem sistema de origem: vai para o portal ou, se PORTAL_URL nao foi definida, para a pagina de exemplo. */
    @GetMapping("/")
    public ResponseEntity<Void> inicio() {
        return redirecionar("/".equals(portalUrl) ? "/exemplo-cliente.html" : portalUrl);
    }

    @GetMapping("/login")
    public ResponseEntity<String> login(@RequestParam Map<String, String> params, CsrfToken csrf) {
        if (params.containsKey("erro"))
            return pagina(HttpStatus.OK, htmlLogin, Map.of(), csrf, "CPF ou senha inválidos.", false);
        if (params.containsKey("expirado"))
            return pagina(HttpStatus.OK, htmlLogin, Map.of(), csrf, "Sua sessão expirou. Entre novamente.", false);
        if (params.containsKey("cadastrado"))
            return pagina(HttpStatus.OK, htmlLogin, Map.of(), csrf, "Conta criada. Entre com CPF e senha.", true);
        return pagina(HttpStatus.OK, htmlLogin, Map.of(), csrf, "", false);
    }

    @GetMapping("/cadastro")
    public ResponseEntity<String> cadastro(CsrfToken csrf) {
        return pagina(HttpStatus.OK, htmlCadastro, Map.of(), csrf, "", false);
    }

    /** Autocadastro: sempre cria um ALUNO. A sessao guarda o sistema de origem para depois do login. */
    @PostMapping("/cadastro")
    public ResponseEntity<?> cadastrar(@RequestParam Map<String, String> d, CsrfToken csrf) {
        try {
            usuarios.criar(new CriarUsuario(d.get("nome"), d.get("cpf"), d.get("email"), d.get("matricula"),
                    d.get("senha")), Perfil.ALUNO);
            return redirecionar("/login?cadastrado");
        } catch (ResponseStatusException e) {
            return pagina(e.getStatusCode(), htmlCadastro, d, csrf, e.getReason() + ".", false);
        }
    }

    private ResponseEntity<String> pagina(HttpStatusCode status, String html, Map<String, String> dados,
                                          CsrfToken csrf, String aviso, boolean ok) {
        Map<String, String> valores = new HashMap<>(dados);
        valores.put("csrfNome", csrf.getParameterName());
        valores.put("csrf", csrf.getToken());
        valores.put("portalUrl", portalUrl);
        valores.put("aviso", aviso);
        valores.put("avisoClasse", ok ? "aviso ok" : "aviso");
        valores.put("avisoHidden", aviso.isEmpty() ? "hidden" : "");
        // Uma unica passada e tudo escapado: valor digitado pelo usuario nunca vira marcador nem HTML.
        String corpo = MARCADOR.matcher(html).replaceAll(m ->
                Matcher.quoteReplacement(HtmlUtils.htmlEscape(valores.getOrDefault(m.group(1), ""), "UTF-8")));
        return ResponseEntity.status(status).contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .header("Content-Security-Policy", CSP).body(corpo);
    }

    private static ResponseEntity<Void> redirecionar(String destino) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, URI.create(destino).toString())
                .build();
    }

    private static String ler(String caminho) {
        try {
            return new ClassPathResource(caminho).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Pagina ausente: " + caminho, e);
        }
    }
}
