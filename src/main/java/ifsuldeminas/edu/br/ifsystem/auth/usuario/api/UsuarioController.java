package ifsuldeminas.edu.br.ifsystem.auth.usuario.api;

import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.AlterarAtivo;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.Atualizar;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.CriarPorSecretaria;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.RedefinirSenha;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.api.UsuarioDtos.Resposta;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.service.UsuarioService;
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

import java.util.List;
import java.util.UUID;

/** Gestao de usuarios pela secretaria. O acesso (somente SERVIDOR) e imposto no SecurityConfig. */
@RestController
@RequestMapping("/usuarios")
public class UsuarioController {
    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Resposta criar(@RequestBody CriarPorSecretaria dados) {
        return Resposta.de(service.criarPorSecretaria(dados));
    }

    @GetMapping
    public List<Resposta> listar(@RequestParam(required = false) Perfil perfil) {
        return service.listar(perfil);
    }

    @GetMapping("/{id}")
    public Resposta buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @PutMapping("/{id}")
    public Resposta atualizar(@PathVariable UUID id, @RequestBody Atualizar dados, Authentication logado) {
        return service.atualizar(id, dados, UUID.fromString(logado.getName()));
    }

    /** Ativa (true) ou inativa (false); ativar tambem restaura um usuario excluido. */
    @PatchMapping("/{id}/ativo")
    public Resposta alterarAtivo(@PathVariable UUID id, @RequestBody AlterarAtivo dados, Authentication logado) {
        if (dados.ativo() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo ativo obrigatório");
        return service.alterarAtivo(id, dados.ativo(), UUID.fromString(logado.getName()));
    }

    /** Define uma nova senha (digitada pela secretaria); nao exige a senha anterior. */
    @PutMapping("/{id}/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@PathVariable UUID id, @RequestBody RedefinirSenha dados) {
        service.redefinirSenha(id, dados.senha());
    }

    /** Exclusao logica: o id continua existindo porque os outros sistemas o referenciam. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id, Authentication logado) {
        service.excluir(id, UUID.fromString(logado.getName()));
    }
}
