package ifsuldeminas.edu.br.ifsystem.auth.usuario.api;

import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.StatusUsuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public final class UsuarioDtos {
    private UsuarioDtos() {
    }

    /** Dados internos de criacao (cadastro, secretaria e bootstrap). */
    public record CriarUsuario(String nome, String cpf, String email, String matricula, String senha) {
    }

    @Schema(description = "POST /usuarios. Cria um PROFESSOR ou SERVIDOR; a matricula e gerada pelo auth.")
    public record CriarPorSecretaria(
            @Schema(description = "CPF valido, com ou sem mascara", example = "529.982.247-25", requiredMode = Schema.RequiredMode.REQUIRED) String cpf,
            @Schema(example = "Maria da Silva", requiredMode = Schema.RequiredMode.REQUIRED) String nome,
            @Schema(example = "maria.silva@ifsuldeminas.edu.br", requiredMode = Schema.RequiredMode.REQUIRED) String email,
            @Schema(description = "Senha com que a pessoa faz o primeiro acesso (8 a 72 caracteres). Nunca volta nas respostas.",
                    example = "troque-me-123", minLength = 8, maxLength = 72, requiredMode = Schema.RequiredMode.REQUIRED) String senhaInicial,
            @Schema(description = "Somente PROFESSOR ou SERVIDOR", example = "PROFESSOR", requiredMode = Schema.RequiredMode.REQUIRED) Perfil perfil) {
    }

    @Schema(description = "PUT /usuarios/{id}. CPF, matricula e senha nao mudam aqui; perfil omitido mantem o atual.")
    public record Atualizar(
            @Schema(example = "Maria da Silva Souza", requiredMode = Schema.RequiredMode.REQUIRED) String nome,
            @Schema(example = "maria.souza@ifsuldeminas.edu.br", requiredMode = Schema.RequiredMode.REQUIRED) String email,
            @Schema(example = "(35) 99999-0000") String telefone,
            @Schema(example = "Engenharia de Software") String curso,
            @Schema(example = "3") Integer periodo,
            @Schema(description = "Opcional. Nao aceita ADMINISTRADOR, nem a troca do proprio perfil.", example = "SERVIDOR") Perfil perfil) {
    }

    @Schema(description = "PUT /usuarios/{id}/senha: a secretaria define a nova senha (nao exige a anterior).")
    public record RedefinirSenha(
            @Schema(example = "nova-senha-123", minLength = 8, maxLength = 72, requiredMode = Schema.RequiredMode.REQUIRED) String senha) {
    }

    @Schema(description = "PUT /me/senha: o proprio usuario troca a senha.")
    public record TrocarSenha(
            @Schema(example = "troque-me-123", requiredMode = Schema.RequiredMode.REQUIRED) String senhaAtual,
            @Schema(example = "uma-senha-nova-456", minLength = 8, maxLength = 72, requiredMode = Schema.RequiredMode.REQUIRED) String novaSenha) {
    }

    @Schema(description = "PATCH /usuarios/{id}/ativo")
    public record AlterarAtivo(
            @Schema(description = "false inativa (e revoga as sessoes); true reativa, inclusive um excluido",
                    example = "false", requiredMode = Schema.RequiredMode.REQUIRED) Boolean ativo) {
    }

    @Schema(description = "Usuario. Nunca traz senha nem hash.")
    public record Resposta(
            @Schema(description = "Identificador estavel; e o claim sub do token", example = "3f2b8c1e-5a4d-4e7b-9c10-2d6f8a7b1c34") UUID id,
            @Schema(example = "Maria da Silva") String nome,
            @Schema(description = "So digitos", example = "52998224725") String cpf,
            @Schema(example = "maria.silva@ifsuldeminas.edu.br") String email,
            @Schema(example = "PRO-52998224725") String matricula,
            @Schema(example = "(35) 99999-0000", nullable = true) String telefone,
            @Schema(example = "Engenharia de Software", nullable = true) String curso,
            @Schema(example = "3", nullable = true) Integer periodo,
            @Schema(example = "PROFESSOR") Perfil perfil,
            @Schema(example = "ATIVO") StatusUsuario status,
            @Schema(description = "true quando status = ATIVO (pode entrar)", example = "true") boolean ativo) {
        public static Resposta de(Usuario u) {
            return new Resposta(u.getId(), u.getNome(), u.getCpf(), u.getEmail(), u.getMatricula(),
                    u.getTelefone(), u.getCurso(), u.getPeriodo(), u.getPerfil(), u.getStatus(), u.isAtivo());
        }
    }

    @Schema(description = "Pagina de resultados de GET /usuarios")
    public record Pagina<T>(
            List<T> conteudo,
            @Schema(description = "Numero da pagina, comecando em 0", example = "0") int pagina,
            @Schema(description = "Tamanho efetivo da pagina (maximo 100)", example = "20") int tamanho,
            @Schema(description = "Total de usuarios que atendem aos filtros", example = "42") long totalElementos,
            @Schema(example = "3") int totalPaginas) {
    }

    @Schema(description = "Erro padrao da API")
    public record Erro(@Schema(example = "400") int status,
                       @Schema(example = "A senha deve ter ao menos 8 caracteres") String erro) {
    }
}
