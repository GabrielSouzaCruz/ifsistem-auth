package ifsuldeminas.edu.br.ifsystem.auth.usuario.service;

import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.Atualizar;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.CriarPorSecretaria;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.CriarUsuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.Pagina;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.Resposta;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Cpf;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.StatusUsuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Usuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private final JdbcOperations jdbc;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder encoder, JdbcOperations jdbc) {
        this.jdbc = jdbc;
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
        return criar(new CriarUsuario(d.nome(), d.cpf(), d.email(), matricula, d.senhaInicial()), d.perfil());
    }

    static final int TAMANHO_PADRAO = 20;
    static final int TAMANHO_MAXIMO = 100;

    /**
     * Busca paginada, ordenada por nome. Excluidos nunca aparecem. {@code q} procura em parte do nome (sem
     * diferenciar maiusculas nem acentos) ou, quando so tem digitos/pontuacao de CPF, em parte do CPF.
     * O filtro e feito em memoria: a base e pequena (uma instituicao) e assim a busca sem acento
     * funciona igual no PostgreSQL e no H2 dos testes.
     */
    public Pagina<Resposta> listar(Perfil perfil, Boolean ativo, String cpf, String q, Integer pagina,
                                   Integer tamanho, String sort) {
        if (sort != null && !sort.isBlank() && !sort.equals("nome"))
            throw erro(HttpStatus.BAD_REQUEST, "sort aceita apenas: nome");
        int pag = pagina == null ? 0 : pagina;
        int tam = tamanho == null ? TAMANHO_PADRAO : tamanho;
        if (pag < 0)
            throw erro(HttpStatus.BAD_REQUEST, "page deve ser 0 ou maior");
        if (tam < 1)
            throw erro(HttpStatus.BAD_REQUEST, "size deve ser 1 ou maior");
        tam = Math.min(tam, TAMANHO_MAXIMO);

        String cpfExato = vazio(cpf) ? null : Cpf.normalizar(cpf);
        String termo = vazio(q) ? null : semAcento(q.trim());
        String termoDigitos = termo == null ? null : termo.replaceAll("[.\\-\\s]", "");
        boolean buscaPorCpf = termoDigitos != null && !termoDigitos.isEmpty() && termoDigitos.matches("\\d+");

        List<Usuario> achados = (perfil == null ? repository.findAll() : repository.findByPerfil(perfil)).stream()
                .filter(u -> u.getStatus() != StatusUsuario.EXCLUIDO)
                .filter(u -> ativo == null || u.isAtivo() == ativo)
                .filter(u -> cpfExato == null || u.getCpf().equals(cpfExato))
                .filter(u -> termo == null
                        || (buscaPorCpf ? u.getCpf().contains(termoDigitos) : semAcento(u.getNome()).contains(termo)))
                .sorted(Comparator.comparing((Usuario u) -> semAcento(u.getNome())).thenComparing(Usuario::getId))
                .toList();

        long total = achados.size();
        int de = (int) Math.min((long) pag * tam, total);
        List<Resposta> conteudo = achados.subList(de, (int) Math.min((long) de + tam, total)).stream()
                .map(Resposta::de).toList();
        return new Pagina<>(conteudo, pag, tam, total, (int) ((total + tam - 1) / tam));
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
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
        Resposta r = Resposta.de(repository.save(u));
        if (!ativo)
            revogarSessoes(id);
        return r;
    }

    public void redefinirSenha(UUID id, String senha) {
        Usuario u = existente(id);
        if (vazio(senha))
            throw erro(HttpStatus.BAD_REQUEST, "Informe a nova senha");
        validarSenha(senha);
        u.redefinirSenha(encoder.encode(senha));
        repository.save(u);
        revogarSessoes(id);
    }

    /** O proprio usuario troca a senha; a senha atual errada da 400 (nao 401, para o front nao deslogar). */
    public void trocarSenha(UUID id, String senhaAtual, String novaSenha) {
        Usuario u = existente(id);
        if (vazio(senhaAtual) || vazio(novaSenha))
            throw erro(HttpStatus.BAD_REQUEST, "Informe a senha atual e a nova senha");
        if (!encoder.matches(senhaAtual, u.getSenhaHash()))
            throw erro(HttpStatus.BAD_REQUEST, "Senha atual incorreta");
        validarSenha(novaSenha);
        u.redefinirSenha(encoder.encode(novaSenha));
        repository.save(u);
        revogarSessoes(id);
    }

    /** Apaga as autorizacoes do usuario: o refresh token deixa de renovar (o access token ja emitido vale ate vencer, 1h). */
    private void revogarSessoes(UUID id) {
        jdbc.update("DELETE FROM oauth2_authorization WHERE principal_name = ?", id.toString());
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
