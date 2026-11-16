package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.javeriana.procesosempresariales.config.RefrescoProperties;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.FamiliaRefresco;
import co.edu.javeriana.procesosempresariales.domain.MotivoRevocacion;
import co.edu.javeriana.procesosempresariales.domain.RefreshToken;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.SesionDto;
import co.edu.javeriana.procesosempresariales.exception.CredencialesInvalidasException;
import co.edu.javeriana.procesosempresariales.exception.SesionExpiradaException;
import co.edu.javeriana.procesosempresariales.repository.FamiliaRefrescoRepository;
import co.edu.javeriana.procesosempresariales.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class AutenticacionJwtServiceTest {

    private static final String CORREO = "editor@alpes.com";
    private static final String PASSWORD = "Clave-Editor-2026";
    private static final Instant AHORA = Instant.parse("2026-11-15T10:00:00Z");
    private static final Duration QUINCE_MINUTOS = Duration.ofMinutes(15);

    private static final PasswordEncoder CODIFICADOR = new BCryptPasswordEncoder(4);
    private static final String HASH = CODIFICADOR.encode(PASSWORD);

    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private UsuarioService usuarioService;
    @Mock
    private SesionService sesionService;
    @Mock
    private TokenAccesoService tokenAccesoService;
    @Mock
    private FamiliaRefrescoRepository familiaRefrescoRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private FamiliaRefrescoService familiaRefrescoService;

    private final CodigoRefresco codigoRefresco = new CodigoRefresco();
    private AutenticacionJwtService servicio;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        RefrescoProperties refresco = new RefrescoProperties(Duration.ofDays(7), Duration.ofDays(30),
                Duration.ofSeconds(20), new RefrescoProperties.Cookie("PE_REFRESH", "/api/auth", true, "Strict"));
        servicio = new AutenticacionJwtService(userDetailsService, CODIFICADOR, usuarioService, sesionService,
                tokenAccesoService, codigoRefresco, familiaRefrescoRepository, refreshTokenRepository,
                familiaRefrescoService, refresco, Clock.fixed(AHORA, ZoneOffset.UTC));
        Empresa empresa = new Empresa(10L, "Alpes", "900123456-7", "admin@alpes.com");
        usuario = new Usuario(2L, CORREO, HASH, RolUsuario.EDITOR, true, empresa);
    }

    private void credenciales(boolean activo) {
        when(userDetailsService.loadUserByUsername(CORREO))
                .thenReturn(User.withUsername(CORREO).password(HASH).roles("EDITOR").disabled(!activo).build());
    }

    private void emiteAcceso() {
        when(tokenAccesoService.emitir(eq(CORREO), any(UUID.class)))
                .thenReturn(new TokenAccesoEmitido("jwt", AHORA, AHORA.plus(QUINCE_MINUTOS)));
        when(sesionService.consultar(CORREO)).thenReturn(new SesionDto());
    }

    private FamiliaRefresco familia(Instant expiraEn) {
        return new FamiliaRefresco(UUID.randomUUID(), usuario, AHORA.minus(Duration.ofDays(1)), expiraEn, null, null);
    }

    private String presentado(FamiliaRefresco familia, Instant expiraEn, Instant usadoEn) {
        String codigo = codigoRefresco.generar();
        RefreshToken token = new RefreshToken(1L, familia, codigoRefresco.hash(codigo),
                AHORA.minus(Duration.ofHours(1)), expiraEn, usadoEn);
        when(refreshTokenRepository.buscarParaRotar(codigoRefresco.hash(codigo))).thenReturn(Optional.of(token));
        return codigo;
    }

    private RefreshToken guardado() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void elLoginCreaUnaFamiliaDe30DiasYUnRefreshDe7DiasGuardandoSoloSuHash() {
        credenciales(true);
        emiteAcceso();
        when(usuarioService.usuarioAutenticado(CORREO)).thenReturn(usuario);
        UUID id = UUID.randomUUID();
        when(familiaRefrescoRepository.save(any(FamiliaRefresco.class))).thenAnswer(invocacion -> {
            FamiliaRefresco nueva = invocacion.getArgument(0);
            nueva.setId(id);
            return nueva;
        });

        SesionJwt sesion = servicio.iniciarSesion(CORREO, PASSWORD);

        ArgumentCaptor<FamiliaRefresco> familia = ArgumentCaptor.forClass(FamiliaRefresco.class);
        verify(familiaRefrescoRepository).save(familia.capture());
        assertThat(familia.getValue().getUsuario()).isSameAs(usuario);
        assertThat(familia.getValue().getCreadaEn()).isEqualTo(AHORA);
        assertThat(familia.getValue().getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofDays(30)));
        RefreshToken token = guardado();
        assertThat(token.getTokenHash()).isEqualTo(codigoRefresco.hash(sesion.codigoRefresco()))
                .isNotEqualTo(sesion.codigoRefresco());
        assertThat(token.getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofDays(7)));
        assertThat(sesion.refrescoExpiraEn()).isEqualTo(token.getExpiraEn());
        assertThat(sesion.renuevaRefresco()).isTrue();
        verify(tokenAccesoService).emitir(CORREO, id);
    }

    @Test
    void unaContrasenaIncorrectaNoCreaSesion() {
        credenciales(true);

        assertThatThrownBy(() -> servicio.iniciarSesion(CORREO, "otra-clave"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage(AutenticacionJwtService.CREDENCIALES_INVALIDAS);
        verifyNoInteractions(familiaRefrescoRepository, refreshTokenRepository, tokenAccesoService);
    }

    @Test
    void unUsuarioInexistenteODesactivadoRecibeElMismoErrorGenerico() {
        when(userDetailsService.loadUserByUsername("nadie@alpes.com"))
                .thenThrow(new UsernameNotFoundException("no existe"));

        assertThatThrownBy(() -> servicio.iniciarSesion("nadie@alpes.com", PASSWORD))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage(AutenticacionJwtService.CREDENCIALES_INVALIDAS);

        credenciales(false);
        assertThatThrownBy(() -> servicio.iniciarSesion(CORREO, PASSWORD))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage(AutenticacionJwtService.CREDENCIALES_INVALIDAS);
        verifyNoInteractions(familiaRefrescoRepository);
    }

    @Test
    void elRefreshRotaElTokenYEmiteUnoNuevoDeLaMismaFamilia() {
        emiteAcceso();
        FamiliaRefresco familia = familia(AHORA.plus(Duration.ofDays(20)));
        String codigo = presentado(familia, AHORA.plus(Duration.ofDays(3)), null);

        SesionJwt sesion = servicio.refrescar(codigo);

        RefreshToken nuevo = guardado();
        assertThat(sesion.renuevaRefresco()).isTrue();
        assertThat(sesion.codigoRefresco()).isNotEqualTo(codigo);
        assertThat(nuevo.getFamilia()).isSameAs(familia);
        assertThat(nuevo.getEmitidoEn()).isEqualTo(AHORA);
        assertThat(nuevo.getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofDays(7)));
        verify(tokenAccesoService).emitir(CORREO, familia.getId());
        verify(familiaRefrescoService, never()).revocar(any(), any());
    }

    @Test
    void elRefreshNuncaSuperaLaExpiracionAbsolutaDeLaFamilia() {
        emiteAcceso();
        FamiliaRefresco familia = familia(AHORA.plus(Duration.ofDays(2)));
        String codigo = presentado(familia, AHORA.plus(Duration.ofDays(1)), null);

        SesionJwt sesion = servicio.refrescar(codigo);

        assertThat(guardado().getExpiraEn()).isEqualTo(familia.getExpiraEn());
        assertThat(sesion.refrescoExpiraEn()).isEqualTo(familia.getExpiraEn());
    }

    @Test
    void unCodigoVacioODesconocidoNoRenuevaNada() {
        assertThatThrownBy(() -> servicio.refrescar(" ")).isInstanceOf(SesionExpiradaException.class);
        assertThatThrownBy(() -> servicio.refrescar(null)).isInstanceOf(SesionExpiradaException.class);
        when(refreshTokenRepository.buscarParaRotar(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.refrescar(codigoRefresco.generar()))
                .isInstanceOf(SesionExpiradaException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void unaFamiliaRevocadaOVencidaNoRenuevaNada() {
        FamiliaRefresco revocada = familia(AHORA.plus(Duration.ofDays(20)));
        revocada.setRevocadaEn(AHORA.minusSeconds(60));
        revocada.setMotivoRevocacion(MotivoRevocacion.CIERRE_SESION);
        String deRevocada = presentado(revocada, AHORA.plus(Duration.ofDays(3)), null);
        String deVencida = presentado(familia(AHORA), AHORA.plus(Duration.ofDays(3)), null);

        assertThatThrownBy(() -> servicio.refrescar(deRevocada)).isInstanceOf(SesionExpiradaException.class);
        assertThatThrownBy(() -> servicio.refrescar(deVencida)).isInstanceOf(SesionExpiradaException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void unRefreshVencidoNoSeRotaNiRevocaLaFamilia() {
        String codigo = presentado(familia(AHORA.plus(Duration.ofDays(20))), AHORA, null);

        assertThatThrownBy(() -> servicio.refrescar(codigo)).isInstanceOf(SesionExpiradaException.class);
        verify(refreshTokenRepository, never()).save(any());
        verify(familiaRefrescoService, never()).revocar(any(), any());
    }

    @Test
    void unUsuarioDesactivadoPierdeLaFamiliaAlIntentarRenovar() {
        usuario.setActivo(false);
        FamiliaRefresco familia = familia(AHORA.plus(Duration.ofDays(20)));
        String codigo = presentado(familia, AHORA.plus(Duration.ofDays(3)), null);

        assertThatThrownBy(() -> servicio.refrescar(codigo)).isInstanceOf(SesionExpiradaException.class);
        verify(familiaRefrescoService).revocar(familia.getId(), MotivoRevocacion.USUARIO_DESACTIVADO);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void reutilizarUnTokenRotadoFueraDeLaGraciaRevocaLaFamilia() {
        FamiliaRefresco familia = familia(AHORA.plus(Duration.ofDays(20)));
        String codigo = presentado(familia, AHORA.plus(Duration.ofDays(3)), AHORA.minusSeconds(21));

        assertThatThrownBy(() -> servicio.refrescar(codigo)).isInstanceOf(SesionExpiradaException.class);
        verify(familiaRefrescoService).revocar(familia.getId(), MotivoRevocacion.REUTILIZACION_DETECTADA);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void dentroDeLaGraciaSoloSeEntregaUnAccesoQueNoProlongaLaSesion() {
        FamiliaRefresco familia = familia(AHORA.plus(Duration.ofDays(20)));
        Instant rotadoEn = AHORA.minusSeconds(5);
        String codigo = presentado(familia, AHORA.plus(Duration.ofDays(3)), rotadoEn);
        when(refreshTokenRepository.existsByFamiliaIdAndUsadoEnIsNullAndEmitidoEnGreaterThanEqual(familia.getId(),
                rotadoEn)).thenReturn(true);
        when(tokenAccesoService.duracionAcceso()).thenReturn(QUINCE_MINUTOS);
        when(tokenAccesoService.emitir(CORREO, familia.getId(), rotadoEn.plus(QUINCE_MINUTOS)))
                .thenReturn(new TokenAccesoEmitido("jwt", AHORA, rotadoEn.plus(QUINCE_MINUTOS)));
        when(sesionService.consultar(CORREO)).thenReturn(new SesionDto());

        SesionJwt sesion = servicio.refrescar(codigo);

        assertThat(sesion.renuevaRefresco()).isFalse();
        assertThat(sesion.codigoRefresco()).isNull();
        assertThat(sesion.acceso().expiraEn()).isEqualTo(rotadoEn.plus(QUINCE_MINUTOS));
        assertThat(sesion.acceso().duracionEnSegundos()).isLessThan(QUINCE_MINUTOS.toSeconds());
        verify(refreshTokenRepository, never()).save(any());
        verify(familiaRefrescoService, never()).revocar(any(), any());
    }

    @Test
    void dentroDeLaGraciaPeroConElSucesorYaUsadoSeTrataComoReutilizacion() {
        FamiliaRefresco familia = familia(AHORA.plus(Duration.ofDays(20)));
        Instant rotadoEn = AHORA.minusSeconds(5);
        String codigo = presentado(familia, AHORA.plus(Duration.ofDays(3)), rotadoEn);
        when(refreshTokenRepository.existsByFamiliaIdAndUsadoEnIsNullAndEmitidoEnGreaterThanEqual(familia.getId(),
                rotadoEn)).thenReturn(false);

        assertThatThrownBy(() -> servicio.refrescar(codigo)).isInstanceOf(SesionExpiradaException.class);
        verify(familiaRefrescoService).revocar(familia.getId(), MotivoRevocacion.REUTILIZACION_DETECTADA);
    }

    @Test
    void cerrarSesionRevocaLaFamiliaDelCodigo() {
        FamiliaRefresco familia = familia(AHORA.plus(Duration.ofDays(20)));
        String codigo = presentado(familia, AHORA.plus(Duration.ofDays(3)), null);

        servicio.cerrarSesion(codigo);

        verify(familiaRefrescoService).revocar(familia.getId(), MotivoRevocacion.CIERRE_SESION);
    }

    @Test
    void cerrarSesionSinCodigoOConUnCodigoDesconocidoNoFalla() {
        when(refreshTokenRepository.buscarParaRotar(anyString())).thenReturn(Optional.empty());

        servicio.cerrarSesion(null);
        servicio.cerrarSesion(" ");
        servicio.cerrarSesion(codigoRefresco.generar());

        verify(familiaRefrescoService, never()).revocar(any(), any());
    }
}
