package co.edu.javeriana.procesosempresariales.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import co.edu.javeriana.procesosempresariales.service.FamiliaRefrescoService;

@ExtendWith(MockitoExtension.class)
class AutenticadorJwtTest {

    private static final String CORREO = "editor@alpes.com";

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private FamiliaRefrescoService familiaRefrescoService;

    private AutenticadorJwt autenticador;
    private UUID familia;

    @BeforeEach
    void preparar() {
        autenticador = new AutenticadorJwt(userDetailsService, familiaRefrescoService);
        familia = UUID.randomUUID();
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(CORREO)
                .claim(JwtConfig.CLAIM_FAMILIA, familia.toString())
                .claim("rol", "ADMINISTRADOR")
                .claim("empresaId", 99)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
    }

    private void usuario(boolean activo, String rol) {
        when(userDetailsService.loadUserByUsername(CORREO))
                .thenReturn(User.withUsername(CORREO).password("hash").roles(rol).disabled(!activo).build());
    }

    @Test
    void unUsuarioActivoConSesionVigenteSeAutenticaConElCorreoYLosRolesDeLaBaseDeDatos() {
        when(familiaRefrescoService.estaActivaPara(familia, CORREO)).thenReturn(true);
        usuario(true, "EDITOR");

        AbstractAuthenticationToken autenticacion = autenticador.autenticar(jwt());

        assertThat(autenticacion.getName()).isEqualTo(CORREO);
        assertThat(autenticacion.isAuthenticated()).isTrue();
        assertThat(autenticacion.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_EDITOR");
    }

    @Test
    void unaFamiliaRevocadaOAjenaInvalidaElTokenSinConsultarAlUsuario() {
        when(familiaRefrescoService.estaActivaPara(familia, CORREO)).thenReturn(false);

        assertThatThrownBy(() -> autenticador.autenticar(jwt())).isInstanceOf(InvalidBearerTokenException.class);
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    void unUsuarioDesactivadoInvalidaElToken() {
        when(familiaRefrescoService.estaActivaPara(familia, CORREO)).thenReturn(true);
        usuario(false, "EDITOR");

        assertThatThrownBy(() -> autenticador.autenticar(jwt())).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void unUsuarioQueYaNoExisteInvalidaElToken() {
        when(familiaRefrescoService.estaActivaPara(familia, CORREO)).thenReturn(true);
        when(userDetailsService.loadUserByUsername(CORREO)).thenThrow(new UsernameNotFoundException("no existe"));

        assertThatThrownBy(() -> autenticador.autenticar(jwt())).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void siElServicioNoDevuelveUsuarioElTokenSeInvalida() {
        when(familiaRefrescoService.estaActivaPara(familia, CORREO)).thenReturn(true);
        when(userDetailsService.loadUserByUsername(CORREO)).thenReturn(null);

        assertThatThrownBy(() -> autenticador.autenticar(jwt())).isInstanceOf(InvalidBearerTokenException.class);
    }
}
