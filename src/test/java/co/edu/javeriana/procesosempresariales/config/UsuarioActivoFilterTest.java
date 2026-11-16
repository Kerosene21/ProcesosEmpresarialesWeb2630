package co.edu.javeriana.procesosempresariales.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class UsuarioActivoFilterTest {

    private static final String CORREO = "editor@alpes.com";
    private static final List<SimpleGrantedAuthority> ROL_EDITOR = List.of(new SimpleGrantedAuthority("ROLE_EDITOR"));

    @Mock
    private UserDetailsService userDetailsService;

    private UsuarioActivoFilter filtro;
    private MockHttpServletRequest peticion;
    private MockHttpSession sesion;
    private MockFilterChain cadena;

    @BeforeEach
    void preparar() {
        filtro = new UsuarioActivoFilter(userDetailsService);
        sesion = new MockHttpSession();
        peticion = new MockHttpServletRequest("GET", "/procesos");
        peticion.setSession(sesion);
        cadena = new MockFilterChain();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private Authentication autenticado() {
        Authentication autenticacion = UsernamePasswordAuthenticationToken.authenticated(CORREO, null, ROL_EDITOR);
        SecurityContextHolder.getContext().setAuthentication(autenticacion);
        return autenticacion;
    }

    private void registrado(boolean activo) {
        when(userDetailsService.loadUserByUsername(CORREO)).thenReturn(User.withUsername(CORREO)
                .password("{bcrypt}hash")
                .disabled(!activo)
                .authorities(ROL_EDITOR)
                .build());
    }

    private void filtrar() throws Exception {
        filtro.doFilter(peticion, new MockHttpServletResponse(), cadena);
    }

    @Test
    void unUsuarioActivoConservaSuSesionYSuAutenticacion() throws Exception {
        Authentication autenticacion = autenticado();
        registrado(true);

        filtrar();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(autenticacion);
        assertThat(sesion.isInvalid()).isFalse();
        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    void unUsuarioDesactivadoPierdeLaSesionYContinuaComoAnonimo() throws Exception {
        autenticado();
        registrado(false);

        filtrar();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(sesion.isInvalid()).isTrue();
        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    void unUsuarioQueYaNoExistePierdeLaSesion() throws Exception {
        autenticado();
        when(userDetailsService.loadUserByUsername(CORREO)).thenThrow(new UsernameNotFoundException("no existe"));

        filtrar();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(sesion.isInvalid()).isTrue();
    }

    @Test
    void siElServicioNoDevuelveUsuarioSeCierraLaSesion() throws Exception {
        autenticado();
        when(userDetailsService.loadUserByUsername(CORREO)).thenReturn(null);

        filtrar();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(sesion.isInvalid()).isTrue();
    }

    @Test
    void unaAutenticacionSinCompletarNoConsultaUsuarios() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated(CORREO, "clave"));

        filtrar();

        verify(userDetailsService, never()).loadUserByUsername(anyString());
        assertThat(sesion.isInvalid()).isFalse();
    }

    @Test
    void sinAutenticacionNoConsultaUsuarios() throws Exception {
        filtrar();

        verify(userDetailsService, never()).loadUserByUsername(anyString());
        assertThat(sesion.isInvalid()).isFalse();
        assertThat(cadena.getRequest()).isNotNull();
    }

    @Test
    void unaAutenticacionAnonimaNoConsultaUsuarios() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("clave", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        filtrar();

        verify(userDetailsService, never()).loadUserByUsername(anyString());
        assertThat(sesion.isInvalid()).isFalse();
    }
}
