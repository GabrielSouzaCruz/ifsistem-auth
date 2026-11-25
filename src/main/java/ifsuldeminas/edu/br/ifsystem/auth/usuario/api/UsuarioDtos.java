package ifsuldeminas.edu.br.ifsystem.auth.usuario.api;

import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.StatusUsuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Usuario;

import java.util.UUID;

public final class UsuarioDtos {
    private UsuarioDtos() {
    }

    /** Dados internos de criacao (cadastro, secretaria e bootstrap). */
    public record CriarUsuario(String nome, String cpf, String email, String matricula, String senha) {
    }

    /** POST /usuarios (secretaria): apenas PROFESSOR ou SERVIDOR. */
    public record CriarPorSecretaria(String cpf, String nome, String email, String senha, Perfil perfil) {
    }

    /** PUT /usuarios/{id}. CPF, matricula e senha nao mudam aqui; perfil nulo mantem o atual. */
    public record Atualizar(String nome, String email, String telefone, String curso, Integer periodo,
                            Perfil perfil) {
    }

    /** PUT /usuarios/{id}/senha: a secretaria digita a nova senha. */
    public record RedefinirSenha(String senha) {
    }

    public record AlterarAtivo(Boolean ativo) {
    }

    public record Resposta(UUID id, String nome, String cpf, String email, String matricula, String telefone,
                           String curso, Integer periodo, Perfil perfil, StatusUsuario status, boolean ativo) {
        public static Resposta de(Usuario u) {
            return new Resposta(u.getId(), u.getNome(), u.getCpf(), u.getEmail(), u.getMatricula(),
                    u.getTelefone(), u.getCurso(), u.getPeriodo(), u.getPerfil(), u.getStatus(), u.isAtivo());
        }
    }
}
