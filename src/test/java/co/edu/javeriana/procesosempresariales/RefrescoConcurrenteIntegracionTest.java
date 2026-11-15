package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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

import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.service.AutenticacionJwtService;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.FamiliaRefrescoService;
import co.edu.javeriana.procesosempresariales.service.SesionJwt;
import co.edu.javeriana.procesosempresariales.service.TokenAccesoService;

@SpringBootTest
@ActiveProfiles("test")
class RefrescoConcurrenteIntegracionTest {

    private static final String CORREO = "admin@concurrencia-jwt.com";
    private static final String NIT = "905000099-9";
    private static final String PASSWORD = "Clave-Concurrencia-2026";
    private static final int PETICIONES = 4;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private AutenticacionJwtService autenticacionJwtService;

    @Autowired
    private FamiliaRefrescoService familiaRefrescoService;

    @Autowired
    private TokenAccesoService tokenAccesoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void preparar() {
        empresaService.registrar(new RegistroEmpresaDto("Concurrencia JWT", NIT, CORREO, PASSWORD));
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("delete from familia_refresco where usuario_id in "
                + "(select id from usuario where username = ?)", CORREO);
        jdbcTemplate.update("delete from usuario where username = ?", CORREO);
        jdbcTemplate.update("delete from empresa where nit = ?", NIT);
    }

    @Test
    void variosRefrescosSimultaneosConLaMismaCookieRotanUnaSolaVezSinRevocarLaSesion() throws Exception {
        SesionJwt inicial = autenticacionJwtService.iniciarSesion(CORREO, PASSWORD);
        UUID familia = tokenAccesoService.familiaDe(tokenAccesoService.decodificar(inicial.acceso().valor()));
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(PETICIONES);
        List<Future<SesionJwt>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < PETICIONES; i++) {
                Callable<SesionJwt> refresco = () -> {
                    salida.await();
                    return autenticacionJwtService.refrescar(inicial.codigoRefresco());
                };
                resultados.add(hilos.submit(refresco));
            }
            salida.countDown();
            List<SesionJwt> sesiones = new ArrayList<>();
            for (Future<SesionJwt> resultado : resultados) {
                sesiones.add(resultado.get(30, TimeUnit.SECONDS));
            }

            assertThat(sesiones).hasSize(PETICIONES);
            assertThat(sesiones).filteredOn(SesionJwt::renuevaRefresco).hasSize(1);
            assertThat(sesiones).allSatisfy(sesion -> assertThat(tokenAccesoService
                    .familiaDe(tokenAccesoService.decodificar(sesion.acceso().valor()))).isEqualTo(familia));
            assertThat(familiaRefrescoService.estaRevocada(familia)).isFalse();
            assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token where familia_id = ?",
                    Integer.class, familia)).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from refresh_token where familia_id = ? and usado_en is null",
                    Integer.class, familia)).isEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
    }
}
