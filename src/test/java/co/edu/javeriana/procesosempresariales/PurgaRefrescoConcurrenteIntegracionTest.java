package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.javeriana.procesosempresariales.domain.FamiliaRefresco;
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
class PurgaRefrescoConcurrenteIntegracionTest {

    private static final String CORREO = "admin@purga-jwt.com";
    private static final String NIT = "905000098-8";
    private static final String PASSWORD = "Clave-Purga-2026";

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

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Instant ahora;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        empresaService.registrar(new RegistroEmpresaDto("Purga JWT", NIT, CORREO, PASSWORD));
        usuario = usuarioRepository.findByUsername(CORREO).orElseThrow();
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("delete from familia_refresco where usuario_id in "
                + "(select id from usuario where username = ?)", CORREO);
        jdbcTemplate.update("delete from usuario where username = ?", CORREO);
        jdbcTemplate.update("delete from empresa where nit = ?", NIT);
    }

    private FamiliaRefresco familia(Instant expiraEn) {
        return familiaRefrescoRepository.saveAndFlush(
                new FamiliaRefresco(null, usuario, ahora.minus(Duration.ofDays(31)), expiraEn, null, null));
    }

    private RefreshToken token(FamiliaRefresco familia, Instant expiraEn) {
        return refreshTokenRepository.saveAndFlush(new RefreshToken(null, familia,
                codigoRefresco.hash(codigoRefresco.generar()), ahora.minus(Duration.ofDays(8)), expiraEn, null));
    }

    @Test
    void mientrasOtraInstanciaTieneElBloqueoNoSePurgaNadaYDespuesSiSePurga() throws Exception {
        FamiliaRefresco antigua = familia(ahora.minus(Duration.ofHours(1)));
        FamiliaRefresco vigente = familia(ahora.plus(Duration.ofDays(20)));
        RefreshToken caducado = token(vigente, ahora.minus(Duration.ofMinutes(1)));
        RefreshToken activo = token(vigente, ahora.plus(Duration.ofDays(7)));
        CountDownLatch bloqueoTomado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);
        ExecutorService otraInstancia = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> bloqueo = otraInstancia.submit(() -> transaccion.execute(estado -> {
                boolean obtenido = familiaRefrescoRepository.bloquearPurga(FamiliaRefrescoService.CLAVE_BLOQUEO_PURGA);
                bloqueoTomado.countDown();
                try {
                    return obtenido && liberar.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }));
            assertThat(bloqueoTomado.await(30, TimeUnit.SECONDS)).isTrue();

            assertThat(familiaRefrescoService.purgarCaducados()).isEmpty();
            assertThat(familiaRefrescoRepository.existsById(antigua.getId())).isTrue();
            assertThat(refreshTokenRepository.existsById(caducado.getId())).isTrue();

            liberar.countDown();
            assertThat(bloqueo.get(30, TimeUnit.SECONDS)).isTrue();

            PurgaRefresco purga = familiaRefrescoService.purgarCaducados().orElseThrow();
            assertThat(purga.tokensEliminados()).isGreaterThanOrEqualTo(1);
            assertThat(purga.familiasEliminadas()).isGreaterThanOrEqualTo(1);
            assertThat(familiaRefrescoRepository.existsById(antigua.getId())).isFalse();
            assertThat(refreshTokenRepository.existsById(caducado.getId())).isFalse();
            assertThat(familiaRefrescoRepository.existsById(vigente.getId())).isTrue();
            assertThat(refreshTokenRepository.existsById(activo.getId())).isTrue();
        } finally {
            liberar.countDown();
            otraInstancia.shutdownNow();
        }
    }

    @Test
    void elBloqueoDeLaPurgaSeLiberaAlTerminarLaTransaccion() {
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);

        Boolean primero = transaccion.execute(
                estado -> familiaRefrescoRepository.bloquearPurga(FamiliaRefrescoService.CLAVE_BLOQUEO_PURGA));
        Boolean segundo = transaccion.execute(
                estado -> familiaRefrescoRepository.bloquearPurga(FamiliaRefrescoService.CLAVE_BLOQUEO_PURGA));

        assertThat(primero).isTrue();
        assertThat(segundo).isTrue();
    }
}
