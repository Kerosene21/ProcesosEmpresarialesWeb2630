package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class UsuarioAutenticacionServiceTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";

    @Mock
    private UsuarioRepository usuarioRepository;

    private UsuarioAutenticacionService usuarioAutenticacionService;

    @BeforeEach
    void inicializar() {
        usuarioAutenticacionService = new UsuarioAutenticacionService(usuarioRepository);
    }

    private Usuario usuario(String password, boolean activo, RolUsuario rol) {
        Empresa empresa = new Empresa(7L, "Alpes Logistica", "900123456-7", USERNAME);
        return new Usuario(1L, USERNAME, password, rol, activo, empresa);
    }

    private void existeEnLaBase(Usuario usuario) {
        when(usuarioRepository.findByUsername(USERNAME)).thenReturn(Optional.of(usuario));
    }

    @Test
    void unUsuarioActivoSeCargaConSuIdentidadYSuHashAlmacenado() {
        existeEnLaBase(usuario(HASH, true, RolUsuario.ADMINISTRADOR));

        UserDetails detalles = usuarioAutenticacionService.loadUserByUsername(USERNAME);

        assertThat(detalles.getUsername()).isEqualTo(USERNAME);
        assertThat(detalles.getPassword()).isEqualTo(HASH);
        assertThat(detalles.isEnabled()).isTrue();
    }

    @Test
    void elRolDelUsuarioSeExponeComoAutoridadConElPrefijoDeSpringSecurity() {
        existeEnLaBase(usuario(HASH, true, RolUsuario.EDITOR));

        UserDetails detalles = usuarioAutenticacionService.loadUserByUsername(USERNAME);

        assertThat(detalles.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_EDITOR");
    }

    @Test
    void unUsuarioInactivoSeCargaComoDeshabilitado() {
        existeEnLaBase(usuario(HASH, false, RolUsuario.ADMINISTRADOR));

        UserDetails detalles = usuarioAutenticacionService.loadUserByUsername(USERNAME);

        assertThat(detalles.isEnabled()).isFalse();
    }

    @Test
    void unUsuarioInexistenteProduceUnErrorQueNoRevelaSiElCorreoExiste() {
        when(usuarioRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioAutenticacionService.loadUserByUsername(USERNAME))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Credenciales invalidas");
    }

    @Test
    void elUsernameSeNormalizaAntesDeBuscarloParaQueElCorreoNoDependaDeMayusculas() {
        existeEnLaBase(usuario(HASH, true, RolUsuario.ADMINISTRADOR));

        UserDetails detalles = usuarioAutenticacionService.loadUserByUsername("  Admin@Alpes.COM  ");

        assertThat(detalles.getUsername()).isEqualTo(USERNAME);
    }

    @Test
    void unUsuarioSinContrasenaAlmacenadaNoPuedeAutenticarse() {
        existeEnLaBase(usuario(null, true, RolUsuario.ADMINISTRADOR));

        assertThatThrownBy(() -> usuarioAutenticacionService.loadUserByUsername(USERNAME))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Credenciales invalidas");
    }

    @Test
    void unUsernameNuloProduceElMismoErrorGenerico() {
        assertThatThrownBy(() -> usuarioAutenticacionService.loadUserByUsername(null))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Credenciales invalidas");
    }
}
