package ifsuldeminas.edu.br.ifsystem.auth.usuario.service;

import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.Atualizar;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.CriarPorSecretaria;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.CriarUsuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.Resposta;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Cpf;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.StatusUsuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Usuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Mesmas regras de cadastro do aproveitamento-ifsistem. */
@Service
public class UsuarioService {
    static final int SENHA_MINIMA = 8;
    /** Limite do BCrypt, em bytes. */
    static final int SENHA_MAXIMA_BYTES = 72;
    private static final Pattern FORMATO_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    /** Aceita CPF com ou sem mascara. */
    public Optional<Usuario> buscarPorCpf(String cpf) {
        return repository.findByCpf(Cpf.normalizar(cpf));
    }

    /** Usuario existente e ativo; consultado a cada token emitido e a cada chamada da API. */
    public Optional<Usuario> buscarAtivo(String id) {
        try {
            return repository.findById(UUID.fromString(id)).filter(Usuario::isAtivo);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Usuario criar(CriarUsuario d, Perfil perfil) {
        if (vazio(d.nome()) || vazio(d.cpf()) || vazio(d.email()) || vazio(d.matricula()) || vazio(d.senha()))
            throw erro(HttpStatus.BAD_REQUEST, "Preencha todos os campos obrigatórios");
        validarSenha(d.senha());
        if (!Cpf.valido(d.cpf()))
            throw erro(HttpStatus.BAD_REQUEST, "CPF inválido");
        String cpf = Cpf.normalizar(d.cpf());
        String email = normalizarEmail(d.email());
        String matricula = d.matricula().trim();
        if (repository.existsByCpf(cpf) || repository.existsByEmail(email) || repository.existsByMatricula(matricula))
            throw erro(HttpStatus.CONFLICT, "CPF, e-mail ou matrícula já cadastrados");
        return repository.save(new Usuario(d.nome().trim(), cpf, email, matricula, encoder.encode(d.senha()),
                null, null, null, perfil));
    }

    /** Criacao pela secretaria: PROFESSOR ou SERVIDOR; a matricula e gerada a partir do CPF. */
    public Usuario criarPorSecretaria(CriarPorSecretaria d) {
        if (d.perfil() != Perfil.PROFESSOR && d.perfil() != Perfil.SERVIDOR)
            throw erro(HttpStatus.BAD_REQUEST, "Perfil deve ser PROFESSOR ou SERVIDOR");
        String matricula = d.perfil().name().substring(0, 3) + "-" + Cpf.normalizar(d.cpf());
        return criar(new CriarUsuario(d.nome(), d.cpf(), d.email(), matricula, d.senha()), d.perfil());
    }

    /** Excluidos nao aparecem na listagem. */
    public List<Resposta> listar(Perfil perfil) {
        return (perfil == null ? repository.findAll() : repository.findByPerfil(perfil)).stream()
                .filter(u -> u.getStatus() != StatusUsuario.EXCLUIDO)
                .map(Resposta::de).toList();
    }

    public Resposta buscar(UUID id) {
        return Resposta.de(existente(id));
    }

    public Resposta atualizar(UUID id, Atualizar d, UUID logado) {
        Usuario u = existente(id);
        if (vazio(d.nome()) || vazio(d.email()))
            throw erro(HttpStatus.BAD_REQUEST, "Nome e e-mail são obrigatórios");
        String email = normalizarEmail(d.email());
        if (repository.existsByEmailAndIdNot(email, id))
            throw erro(HttpStatus.CONFLICT, "E-mail já cadastrado");
        if (d.perfil() != null && d.perfil() != u.getPerfil()) {
            if (d.perfil() == Perfil.ADMINISTRADOR || u.getPerfil() == Perfil.ADMINISTRADOR)
                throw erro(HttpStatus.BAD_REQUEST, "O perfil ADMINISTRADOR não pode ser alterado aqui");
            naoAlterarASiMesmo(id, logado);
            u.alterarPerfil(d.perfil());
        }
        u.atualizar(d.nome().trim(), email, d.telefone(), d.curso(), d.periodo());
        return Resposta.de(repository.save(u));
    }

    public Resposta alterarAtivo(UUID id, boolean ativo, UUID logado) {
        Usuario u = existente(id);
        if (!ativo)
            naoAlterarASiMesmo(id, logado);
        u.definirAtivo(ativo);
        return Resposta.de(repository.save(u));
    }

    public void redefinirSenha(UUID id, String senha) {
        Usuario u = existente(id);
        if (vazio(senha))
            throw erro(HttpStatus.BAD_REQUEST, "Informe a nova senha");
        validarSenha(senha);
        u.redefinirSenha(encoder.encode(senha));
        repository.save(u);
    }

    private static void validarSenha(String senha) {
        if (senha.length() < SENHA_MINIMA)
            throw erro(HttpStatus.BAD_REQUEST, "A senha deve ter ao menos " + SENHA_MINIMA + " caracteres");
        if (senha.getBytes(StandardCharsets.UTF_8).length > SENHA_MAXIMA_BYTES)
            throw erro(HttpStatus.BAD_REQUEST, "A senha é longa demais (máximo de 72 caracteres)");
    }

    public void excluir(UUID id, UUID logado) {
        Usuario u = existente(id);
        naoAlterarASiMesmo(id, logado);
        u.alterarStatus(StatusUsuario.EXCLUIDO);
        repository.save(u);
    }

    private Usuario existente(UUID id) {
        return repository.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
    }

    /** Evita que o unico SERVIDOR se tranque para fora. */
    private static void naoAlterarASiMesmo(UUID id, UUID logado) {
        if (id.equals(logado))
            throw erro(HttpStatus.CONFLICT, "Você não pode inativar, excluir ou trocar o perfil da própria conta");
    }

    private static String normalizarEmail(String email) {
        String e = email.trim().toLowerCase();
        if (!FORMATO_EMAIL.matcher(e).matches())
            throw erro(HttpStatus.BAD_REQUEST, "E-mail inválido");
        return e;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static ResponseStatusException erro(HttpStatus status, String mensagem) {
        return new ResponseStatusException(status, mensagem);
    }
}
