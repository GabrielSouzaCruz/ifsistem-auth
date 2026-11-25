package ifsuldeminas.edu.br.ifsystem.auth.api;

import ifsuldeminas.edu.br.ifsystem.auth.config.AuthProperties;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Substitui a Whitelabel Error Page: HTML amigavel para navegador, JSON {status, erro} para o resto. Sem stack trace. */
@RestController
class ErroPaginaController implements ErrorController {
    private static final Pattern MARCADOR = Pattern.compile("\\{\\{(\\w+)}}");
    private static final String CSP = "default-src 'self'; base-uri 'none'; frame-ancestors 'none'";
    private static final String CADASTRO_INVALIDO =
            "Sistema não cadastrado ou endereço de retorno inválido. Avise a equipe responsável.";

    private final String portalUrl;
    private final String html = ler("paginas/erro.html");

    ErroPaginaController(AuthProperties props) {
        this.portalUrl = props.portalUrl().isBlank() ? "/" : props.portalUrl();
    }

    @RequestMapping("/error")
    ResponseEntity<?> erro(HttpServletRequest req) {
        Object atributo = req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int codigo = atributo instanceof Integer i && i >= 400 && i < 600 ? i : 500;
        String origem = String.valueOf(req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        String titulo;
        String mensagem;
        if (codigo == 400 && origem.startsWith("/oauth2/authorize")) {
            titulo = "Não foi possível entrar";
            mensagem = CADASTRO_INVALIDO;
        } else if (codigo == 400 || (codigo < 500 && codigo != 403 && codigo != 404 && codigo != 401)) {
            titulo = "Requisição inválida";
            mensagem = "Não foi possível processar o pedido. Volte e tente novamente.";
        } else if (codigo == 403 || codigo == 401) {
            titulo = "Acesso negado";
            mensagem = "Você não tem permissão para acessar esta página.";
        } else if (codigo == 404) {
            titulo = "Página não encontrada";
            mensagem = "O endereço acessado não existe ou foi movido.";
        } else {
            titulo = "Algo deu errado";
            mensagem = "Tivemos um problema inesperado. Tente novamente em instantes.";
        }
        HttpStatus status = HttpStatus.resolve(codigo) != null ? HttpStatus.valueOf(codigo) : HttpStatus.INTERNAL_SERVER_ERROR;
        String accept = req.getHeader("Accept");
        if (accept == null || !accept.contains("text/html"))
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("status", codigo, "erro", titulo));
        Map<String, String> valores = Map.of("codigo", String.valueOf(codigo), "titulo", titulo,
                "mensagem", mensagem, "portalUrl", portalUrl);
        String corpo = MARCADOR.matcher(html).replaceAll(m -> Matcher.quoteReplacement(
                HtmlUtils.htmlEscape(valores.getOrDefault(m.group(1), ""), "UTF-8")));
        return ResponseEntity.status(status).contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .header("Content-Security-Policy", CSP).body(corpo);
    }

    private static String ler(String caminho) {
        try {
            return new ClassPathResource(caminho).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Pagina ausente: " + caminho, e);
        }
    }
}
