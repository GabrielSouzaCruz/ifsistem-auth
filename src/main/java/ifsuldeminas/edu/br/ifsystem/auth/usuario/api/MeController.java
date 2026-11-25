package ifsuldeminas.edu.br.ifsystem.auth.usuario.api;

import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.Erro;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.TrocarSenha;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Operacoes do proprio usuario autenticado (qualquer perfil). */
@RestController
@RequestMapping("/me")
@Tag(name = "Minha conta", description = "Para qualquer usuário autenticado, de qualquer perfil.")
public class MeController {
    private final UsuarioService service;

    public MeController(UsuarioService service) {
        this.service = service;
    }

    @PutMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Trocar a própria senha", description = "Revoga as sessões (refresh tokens). O access token em uso vale até vencer (1 h).")
    @ApiResponse(responseCode = "204", description = "Senha trocada")
    @ApiResponse(responseCode = "400", description = "Senha atual incorreta, ou nova senha fora de 8–72 caracteres", content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou usuário inativo", content = @Content(schema = @Schema(implementation = Erro.class)))
    public void trocarSenha(@RequestBody TrocarSenha dados, Authentication logado) {
        service.trocarSenha(UUID.fromString(logado.getName()), dados.senhaAtual(), dados.novaSenha());
    }
}
