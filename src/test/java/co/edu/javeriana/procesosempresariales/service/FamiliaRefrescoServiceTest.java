package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.javeriana.procesosempresariales.config.JwtProperties;
import co.edu.javeriana.procesosempresariales.domain.MotivoRevocacion;
import co.edu.javeriana.procesosempresariales.repository.FamiliaRefrescoRepository;
import co.edu.javeriana.procesosempresariales.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class FamiliaRefrescoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-15T10:00:00Z");

    @Mock
    private FamiliaRefrescoRepository familiaRefrescoRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private FamiliaRefrescoService servicio;

    @BeforeEach
    void preparar() {
        JwtProperties jwt = new JwtProperties("no-se-usa", "emisor", "audiencia", Duration.ofMinutes(15),
                Duration.ofSeconds(30));
        servicio = new FamiliaRefrescoService(familiaRefrescoRepository, refreshTokenRepository, jwt,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void unaFamiliaSinRevocarNoEstaRevocada() {
        UUID familia = UUID.randomUUID();
        when(familiaRefrescoRepository.existsByIdAndRevocadaEnIsNull(familia)).thenReturn(true);

        assertThat(servicio.estaRevocada(familia)).isFalse();
    }

    @Test
    void unaFamiliaRevocadaODesconocidaSeConsideraRevocada() {
        UUID familia = UUID.randomUUID();
        when(familiaRefrescoRepository.existsByIdAndRevocadaEnIsNull(familia)).thenReturn(false);

        assertThat(servicio.estaRevocada(familia)).isTrue();
        assertThat(servicio.estaRevocada(null)).isTrue();
    }

    @Test
    void revocarRegistraElInstanteDelRelojYElMotivo() {
        UUID familia = UUID.randomUUID();
        when(familiaRefrescoRepository.revocar(familia, MotivoRevocacion.CIERRE_SESION, AHORA)).thenReturn(1);

        assertThat(servicio.revocar(familia, MotivoRevocacion.CIERRE_SESION)).isTrue();
    }

    @Test
    void revocarUnaFamiliaYaRevocadaNoCambiaNada() {
        UUID familia = UUID.randomUUID();
        when(familiaRefrescoRepository.revocar(familia, MotivoRevocacion.CIERRE_SESION, AHORA)).thenReturn(0);

        assertThat(servicio.revocar(familia, MotivoRevocacion.CIERRE_SESION)).isFalse();
    }

    @Test
    void revocarExigeUnMotivo() {
        UUID familia = UUID.randomUUID();

        assertThatThrownBy(() -> servicio.revocar(familia, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.revocarDelUsuario(1L, null)).isInstanceOf(IllegalArgumentException.class);
        verify(familiaRefrescoRepository, never()).revocar(any(), any(), any());
    }

    @Test
    void revocarDelUsuarioDevuelveCuantasFamiliasCerro() {
        when(familiaRefrescoRepository.revocarDelUsuario(7L, MotivoRevocacion.USUARIO_DESACTIVADO, AHORA))
                .thenReturn(2);

        assertThat(servicio.revocarDelUsuario(7L, MotivoRevocacion.USUARIO_DESACTIVADO)).isEqualTo(2);
    }

    @Test
    void laPurgaConservaLasFamiliasMientrasPuedaHaberTokensDeAccesoVigentes() {
        Instant limiteFamilias = AHORA.minus(Duration.ofMinutes(15)).minusSeconds(30);
        when(familiaRefrescoRepository.bloquearPurga(FamiliaRefrescoService.CLAVE_BLOQUEO_PURGA)).thenReturn(true);
        when(refreshTokenRepository.eliminarExpiradosAntesDe(AHORA)).thenReturn(3);
        when(familiaRefrescoRepository.eliminarExpiradasAntesDe(limiteFamilias)).thenReturn(1);

        assertThat(servicio.purgarCaducados()).contains(new PurgaRefresco(3, 1));
        assertThat(servicio.limiteConservacionFamilias(AHORA)).isEqualTo(limiteFamilias);
    }

    @Test
    void siOtraInstanciaTieneElBloqueoLaPurgaNoEliminaNada() {
        when(familiaRefrescoRepository.bloquearPurga(FamiliaRefrescoService.CLAVE_BLOQUEO_PURGA)).thenReturn(false);

        assertThat(servicio.purgarCaducados()).isEmpty();

        verify(familiaRefrescoRepository, never()).eliminarExpiradasAntesDe(any());
        verifyNoInteractions(refreshTokenRepository);
        verify(familiaRefrescoRepository).bloquearPurga(FamiliaRefrescoService.CLAVE_BLOQUEO_PURGA);
    }

    @Test
    void consultarElEstadoNoTocaLosRefreshTokens() {
        servicio.estaRevocada(UUID.randomUUID());

        verifyNoInteractions(refreshTokenRepository);
    }
}
