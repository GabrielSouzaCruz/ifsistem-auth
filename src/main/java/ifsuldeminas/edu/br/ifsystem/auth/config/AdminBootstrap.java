package ifsuldeminas.edu.br.ifsystem.auth.config;

import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.CriarUsuario;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Cpf;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.repository.UsuarioRepository;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Cria o SERVIDOR de ADMIN_CPF/ADMIN_SENHA se nao existir usuario com esse CPF (ex.: ja migrado pela V2). */
@Component
public class AdminBootstrap implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UsuarioRepository repository;
    private final UsuarioService usuarios;
    private final String cpf;
    private final String senha;
    private final String email;

    public AdminBootstrap(UsuarioRepository repository, UsuarioService usuarios,
                          @Value("${app.admin.cpf:}") String cpf,
                          @Value("${app.admin.senha:}") String senha,
                          @Value("${app.admin.email:secretaria@ifsuldeminas.edu.br}") String email) {
        this.repository = repository;
        this.usuarios = usuarios;
        this.cpf = cpf;
        this.senha = senha;
        this.email = email;
    }

    @Override
    public void run(String... args) {
        if (cpf.isBlank() || senha.isBlank() || repository.existsByCpf(Cpf.normalizar(cpf)))
            return;
        try {
            usuarios.criar(new CriarUsuario("Secretaria", cpf, email, "SER-" + Cpf.normalizar(cpf), senha),
                    Perfil.SERVIDOR);
            log.info("Conta da secretaria criada a partir de ADMIN_CPF");
        } catch (ResponseStatusException e) {
            // Ex.: e-mail padrao ja usado por um usuario migrado. Nao derruba o login de todo mundo por isso.
            log.error("Conta da secretaria NAO criada: {}", e.getReason());
        }
    }
}
