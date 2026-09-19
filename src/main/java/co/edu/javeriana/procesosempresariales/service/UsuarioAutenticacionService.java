package co.edu.javeriana.procesosempresariales.service;

import java.util.Locale;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@Service
public class UsuarioAutenticacionService implements UserDetailsService {

    private static final String ERROR_GENERICO = "Credenciales invalidas";
    private static final String PREFIJO_ROL = "ROLE_";

    private final UsuarioRepository usuarioRepository;

    public UsuarioAutenticacionService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        String identidad = normalizar(username);
        if (identidad.isEmpty()) {
            throw new UsernameNotFoundException(ERROR_GENERICO);
        }
        Usuario usuario = usuarioRepository.findByUsername(identidad)
                .orElseThrow(() -> new UsernameNotFoundException(ERROR_GENERICO));
        if (usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            throw new UsernameNotFoundException(ERROR_GENERICO);
        }
        return User.withUsername(usuario.getUsername())
                .password(usuario.getPassword())
                .disabled(!usuario.isActivo())
                .authorities(new SimpleGrantedAuthority(PREFIJO_ROL + usuario.getRol().name()))
                .build();
    }

    private String normalizar(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
