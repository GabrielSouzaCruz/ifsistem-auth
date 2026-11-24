package ifsuldeminas.edu.br.ifsystem.auth.service;

import ifsuldeminas.edu.br.ifsystem.auth.usuario.service.UsuarioService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Login por CPF. O "username" do principal e o id do usuario: assim o "sub" do token e o mesmo id que os
 * sistemas ja guardam, e o CPF nao vai para o principal_name das tabelas do authorization server.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {
    private final UsuarioService usuarios;

    public UsuarioDetailsService(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String cpf) {
        return usuarios.buscarPorCpf(cpf)
                .map(u -> User.withUsername(u.getId().toString()).password(u.getSenhaHash())
                        .roles(u.getPerfil().name()).disabled(!u.isAtivo()).build())
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
    }
}
