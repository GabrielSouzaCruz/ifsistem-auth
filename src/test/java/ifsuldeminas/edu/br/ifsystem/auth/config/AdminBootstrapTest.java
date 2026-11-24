package ifsuldeminas.edu.br.ifsystem.auth.config;

import ifsuldeminas.edu.br.ifsystem.auth.usuario.domain.Perfil;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.repository.UsuarioRepository;
import ifsuldeminas.edu.br.ifsystem.auth.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {
    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final UsuarioService usuarios = mock(UsuarioService.class);

    private AdminBootstrap com(String cpf, String senha) {
        return new AdminBootstrap(repo, usuarios, cpf, senha, "secretaria@ifsuldeminas.edu.br");
    }

    @Test
    void criaOServidorQuandoAsVariaveisExistem() {
        com("529.982.247-25", "senha-forte-1").run();
        verify(usuarios).criar(any(), eq(Perfil.SERVIDOR));
    }

    @Test
    void naoFazNadaSeOCpfJaExisteOuSemVariaveis() {
        when(repo.existsByCpf("52998224725")).thenReturn(true);
        com("529.982.247-25", "senha-forte-1").run();
        com("", "").run();
        verify(usuarios, never()).criar(any(), any());
    }

    @Test
    void conflitoDeEmailNaoDerrubaAAplicacao() {
        when(usuarios.criar(any(), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "x"));
        assertThatCode(() -> com("52998224725", "senha-forte-1").run()).doesNotThrowAnyException();
    }
}
