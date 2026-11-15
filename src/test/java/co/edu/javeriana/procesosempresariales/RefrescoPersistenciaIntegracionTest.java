package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.FamiliaRefresco;
import co.edu.javeriana.procesosempresariales.domain.MotivoRevocacion;
import co.edu.javeriana.procesosempresariales.domain.RefreshToken;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.repository.FamiliaRefrescoRepository;
import co.edu.javeriana.procesosempresariales.repository.RefreshTokenRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;
import co.edu.javeriana.procesosempresariales.service.CodigoRefresco;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.FamiliaRefrescoService;
import co.edu.javeriana.procesosempresariales.service.PurgaRefresco;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefrescoPersistenciaIntegracionTest {

    private static final String CORREO_ALPES = "admin@alpes-refresco.com";
    private static final String CORREO_ANDES = "admin@andes-refresco.com";
    private static final String PASSWORD = "Clave-Refresco-2026";

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private FamiliaRefrescoRepository familiaRefrescoRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private FamiliaRefrescoService familiaRefrescoService;

    @Autowired
    private CodigoRefresco codigoRefresco;

    private Instant ahora;
    private Usuario alpes;
    private Usuario andes;

    @BeforeEach
    void preparar() {
        ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        alpes = registrar("Alpes Refresco", "904000001-1", CORREO_ALPES);
        andes = registrar("Andes Refresco", "904000002-2", CORREO_ANDES);
    }

    private Usuario registrar(String nombre, String nit, String correo) {
        empresaService.registrar(new RegistroEmpresaDto(nombre, nit, correo, PASSWORD));
        return usuarioRepository.findByUsername(correo).orElseThrow();
    }

    private FamiliaRefresco familia(Usuario usuario, Instant expiraEn) {
        return familiaRefrescoRepository.saveAndFlush(
                new FamiliaRefresco(null, usuario, ahora.minus(Duration.ofDays(1)), expiraEn, null, null));
    }

    private RefreshToken token(FamiliaRefresco familia, String hash, Instant expiraEn) {
        return refreshTokenRepository.saveAndFlush(
                new RefreshToken(null, familia, hash, ahora.minus(Duration.ofHours(1)), expiraEn, null));
    }

    private RefreshToken token(FamiliaRefresco familia, Instant expiraEn) {
        return token(familia, codigoRefresco.hash(codigoRefresco.generar()), expiraEn);
    }

    @Test
    void seGuardaLaFamiliaConIdentificadorUuidYSoloElHashDelToken() {
        String codigo = codigoRefresco.generar();
        FamiliaRefresco familia = familia(alpes, ahora.plus(Duration.ofDays(30)));
        token(familia, codigoRefresco.hash(codigo), ahora.plus(Duration.ofDays(7)));

        RefreshToken guardado = refreshTokenRepository.buscarParaRotar(codigoRefresco.hash(codigo)).orElseThrow();

        assertThat(familia.getId()).isNotNull();
        assertThat(guardado.getTokenHash()).hasSize(64).isNotEqualTo(codigo).doesNotContain(codigo);
        assertThat(guardado.getFamilia().getId()).isEqualTo(familia.getId());
        assertThat(guardado.getFamilia().getUsuario().getUsername()).isEqualTo(CORREO_ALPES);
        assertThat(guardado.getUsadoEn()).isNull();
        assertThat(refreshTokenRepository.buscarParaRotar(codigo)).isEmpty();
    }

    @Test
    void elHashDeCadaRefreshTokenEsUnico() {
        FamiliaRefresco familia = familia(alpes, ahora.plus(Duration.ofDays(30)));
        String hash = codigoRefresco.hash(codigoRefresco.generar());
        token(familia, hash, ahora.plus(Duration.ofDays(7)));

        assertThatThrownBy(() -> token(familia, hash, ahora.plus(Duration.ofDays(7))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void revocarUnaFamiliaEsIdempotenteYConservaElPrimerMotivo() {
        FamiliaRefresco familia = familia(alpes, ahora.plus(Duration.ofDays(30)));

        assertThat(familiaRefrescoService.estaRevocada(familia.getId())).isFalse();
        assertThat(familiaRefrescoService.revocar(familia.getId(), MotivoRevocacion.REUTILIZACION_DETECTADA)).isTrue();
        assertThat(familiaRefrescoService.revocar(familia.getId(), MotivoRevocacion.CIERRE_SESION)).isFalse();

        FamiliaRefresco revocada = familiaRefrescoRepository.findById(familia.getId()).orElseThrow();
        assertThat(revocada.getRevocadaEn()).isNotNull();
        assertThat(revocada.getMotivoRevocacion()).isEqualTo(MotivoRevocacion.REUTILIZACION_DETECTADA);
        assertThat(familiaRefrescoService.estaRevocada(familia.getId())).isTrue();
    }

    @Test
    void revocarLasFamiliasDeUnUsuarioNoAfectaALasDeOtraEmpresa() {
        FamiliaRefresco primera = familia(alpes, ahora.plus(Duration.ofDays(30)));
        FamiliaRefresco segunda = familia(alpes, ahora.plus(Duration.ofDays(30)));
        FamiliaRefresco ajena = familia(andes, ahora.plus(Duration.ofDays(30)));

        int revocadas = familiaRefrescoService.revocarDelUsuario(alpes.getId(), MotivoRevocacion.USUARIO_DESACTIVADO);

        assertThat(revocadas).isEqualTo(2);
        assertThat(familiaRefrescoService.estaRevocada(primera.getId())).isTrue();
        assertThat(familiaRefrescoService.estaRevocada(segunda.getId())).isTrue();
        assertThat(familiaRefrescoService.estaRevocada(ajena.getId())).isFalse();
        assertThat(familiaRefrescoRepository.findById(primera.getId()).orElseThrow().getMotivoRevocacion())
                .isEqualTo(MotivoRevocacion.USUARIO_DESACTIVADO);
    }

    @Test
    void unaFamiliaDesconocidaSeConsideraRevocada() {
        assertThat(familiaRefrescoService.estaRevocada(UUID.randomUUID())).isTrue();
    }

    @Test
    void laPurgaEliminaLoCaducadoYConservaLaRevocacionMientrasPuedaHaberTokensDeAccesoVigentes() {
        FamiliaRefresco revocadaReciente = familia(alpes, ahora.minus(Duration.ofMinutes(5)));
        RefreshToken caducadoDeRevocada = token(revocadaReciente, ahora.minus(Duration.ofMinutes(5)));
        familiaRefrescoService.revocar(revocadaReciente.getId(), MotivoRevocacion.CIERRE_SESION);
        FamiliaRefresco antigua = familia(alpes, ahora.minus(Duration.ofHours(1)));
        RefreshToken deAntigua = token(antigua, ahora.minus(Duration.ofHours(1)));
        FamiliaRefresco activa = familia(andes, ahora.plus(Duration.ofDays(30)));
        RefreshToken vigente = token(activa, ahora.plus(Duration.ofDays(7)));
        RefreshToken rotadoCaducado = token(activa, ahora.minus(Duration.ofMinutes(1)));

        PurgaRefresco purga = familiaRefrescoService.purgarCaducados();

        assertThat(purga.tokensEliminados()).isEqualTo(3);
        assertThat(purga.familiasEliminadas()).isEqualTo(1);
        assertThat(familiaRefrescoRepository.findById(revocadaReciente.getId())).isPresent();
        assertThat(familiaRefrescoService.estaRevocada(revocadaReciente.getId())).isTrue();
        assertThat(familiaRefrescoRepository.findById(antigua.getId())).isEmpty();
        assertThat(familiaRefrescoService.estaRevocada(antigua.getId())).isTrue();
        assertThat(familiaRefrescoService.estaRevocada(activa.getId())).isFalse();
        assertThat(refreshTokenRepository.findById(vigente.getId())).isPresent();
        assertThat(refreshTokenRepository.findById(caducadoDeRevocada.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(deAntigua.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(rotadoCaducado.getId())).isEmpty();
    }

    @Test
    void eliminarUnaFamiliaEliminaSusRefreshTokensEnCascada() {
        FamiliaRefresco antigua = familia(alpes, ahora.minus(Duration.ofDays(2)));
        RefreshToken vigenteEnApariencia = token(antigua, ahora.plus(Duration.ofDays(1)));

        int eliminadas = familiaRefrescoRepository.eliminarExpiradasAntesDe(ahora.minus(Duration.ofDays(1)));

        assertThat(eliminadas).isEqualTo(1);
        assertThat(refreshTokenRepository.findById(vigenteEnApariencia.getId())).isEmpty();
    }
}
