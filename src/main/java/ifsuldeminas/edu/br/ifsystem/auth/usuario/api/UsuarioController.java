package ifsuldeminas.edu.br.ifsystem.auth.usuario.api;

import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.AlterarAtivo;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.Atualizar;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.CriarPorSecretaria;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.Erro;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.Pagina;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.RedefinirSenha;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.Resposta;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** Gestao de usuarios. O acesso (SERVIDOR ou ADMINISTRADOR) e imposto no SecurityConfig. */
@RestController
@RequestMapping("/usuarios")
@Tag(name = "Usuários", description = "Gestão de usuários. Exige perfil SERVIDOR ou ADMINISTRADOR.")
public class UsuarioController {
    private static final String NAO_AUTENTICADO = "Token ausente, inválido ou usuário inativo";
    private static final String SEM_PERMISSAO = "Perfil sem permissão (só SERVIDOR e ADMINISTRADOR)";

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar usuário (PROFESSOR ou SERVIDOR) com senha inicial")
    @ApiResponse(responseCode = "201", description = "Criado")
    @ApiResponse(responseCode = "400", description = "Campo obrigatório ausente, CPF/e-mail inválido, senha fora de 8–72 caracteres ou perfil não permitido",
            content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "401", description = NAO_AUTENTICADO, content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "403", description = SEM_PERMISSAO, content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "409", description = "CPF, e-mail ou matrícula já cadastrados",
            content = @Content(schema = @Schema(implementation = Erro.class)))
    public Resposta criar(@RequestBody CriarPorSecretaria dados) {
        return Resposta.de(service.criarPorSecretaria(dados));
    }

    @GetMapping
    @Operation(summary = "Buscar usuários (paginado, ordenado por nome)",
            description = "Usuários excluídos nunca aparecem. Todos os filtros são opcionais e se combinam (E).")
    @ApiResponse(responseCode = "200", description = "Página de usuários")
    @ApiResponse(responseCode = "400", description = "page, size ou sort inválidos", content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "401", description = NAO_AUTENTICADO, content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "403", description = SEM_PERMISSAO, content = @Content(schema = @Schema(implementation = Erro.class)))
    public Pagina<Resposta> listar(
            @Parameter(description = "Perfil exato") @RequestParam(required = false) Perfil perfil,
            @Parameter(description = "true = pode entrar; false = inativo") @RequestParam(required = false) Boolean ativo,
            @Parameter(description = "CPF exato, só dígitos", example = "52998224725") @RequestParam(required = false) String cpf,
            @Parameter(description = "Parte do nome (sem diferenciar maiúsculas e acentos) ou parte do CPF", example = "jose") @RequestParam(required = false) String q,
            @Parameter(description = "Página, começando em 0") @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Tamanho da página (padrão 20, máximo 100)") @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "Ordenação; só existe nome", schema = @Schema(allowableValues = "nome", defaultValue = "nome"))
            @RequestParam(required = false) String sort) {
        return service.listar(perfil, ativo, cpf, q, page, size, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar um usuário pelo id (o mesmo sub do token)")
    @ApiResponse(responseCode = "200", description = "Usuário")
    @ApiResponse(responseCode = "401", description = NAO_AUTENTICADO, content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "403", description = SEM_PERMISSAO, content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado", content = @Content(schema = @Schema(implementation = Erro.class)))
    public Resposta buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar nome, e-mail, telefone, curso, período e perfil")
    @ApiResponse(responseCode = "200", description = "Atualizado")
    @ApiResponse(responseCode = "400", description = "Nome/e-mail ausentes ou inválidos, ou perfil ADMINISTRADOR envolvido", content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "401", description = NAO_AUTENTICADO, content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "403", description = SEM_PERMISSAO, content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado", content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado, ou tentativa de trocar o próprio perfil", content = @Content(schema = @Schema(implementation = Erro.class)))
    public Resposta atualizar(@PathVariable UUID id, @RequestBody Atualizar dados, Authentication logado) {
        return service.atualizar(id, dados, UUID.fromString(logado.getName()));
    }

    /** Ativa (true) ou inativa (false); ativar tambem restaura um usuario excluido. */
    @PatchMapping("/{id}/ativo")
    @Operation(summary = "Ativar ou inativar", description = "Inativar revoga as sessões (refresh tokens). Ativar também restaura um usuário excluído.")
    @ApiResponse(responseCode = "200", description = "Usuário com o novo status")
    @ApiResponse(responseCode = "400", description = "Campo ativo ausente", content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "401", description = NAO_AUTENTICADO, content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "403", description = SEM_PERMISSAO, content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado", content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "409", description = "Não é possível inativar a própria conta", content = @Content(schema = @Schema(implementation = Erro.class)))
    public Resposta alterarAtivo(@PathVariable UUID id, @RequestBody AlterarAtivo dados, Authentication logado) {
        if (dados.ativo() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo ativo obrigatório");
        return service.alterarAtivo(id, dados.ativo(), UUID.fromString(logado.getName()));
    }

    /** Define uma nova senha (digitada pela secretaria); nao exige a senha anterior. */
    @PutMapping("/{id}/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Redefinir a senha de outro usuário", description = "Não exige a senha anterior. Revoga as sessões do usuário.")
    @ApiResponse(responseCode = "204", description = "Senha redefinida")
    @ApiResponse(responseCode = "400", description = "Senha ausente ou fora de 8–72 caracteres", content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "401", description = NAO_AUTENTICADO, content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "403", description = SEM_PERMISSAO, content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado", content = @Content(schema = @Schema(implementation = Erro.class)))
    public void redefinirSenha(@PathVariable UUID id, @RequestBody RedefinirSenha dados) {
        service.redefinirSenha(id, dados.senha());
    }

    /** Exclusao logica: o id continua existindo porque os outros sistemas o referenciam. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir (exclusão lógica)", description = "O id continua existindo; o usuário some das buscas e não entra mais.")
    @ApiResponse(responseCode = "204", description = "Excluído")
    @ApiResponse(responseCode = "401", description = NAO_AUTENTICADO, content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "403", description = SEM_PERMISSAO, content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado", content = @Content(schema = @Schema(implementation = Erro.class)))
    @ApiResponse(responseCode = "409", description = "Não é possível excluir a própria conta", content = @Content(schema = @Schema(implementation = Erro.class)))
    public void excluir(@PathVariable UUID id, Authentication logado) {
        service.excluir(id, UUID.fromString(logado.getName()));
    }
}
