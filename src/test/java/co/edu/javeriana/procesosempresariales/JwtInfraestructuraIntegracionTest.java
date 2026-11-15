package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.javeriana.procesosempresariales.config.JwtProperties;
import co.edu.javeriana.procesosempresariales.config.RefrescoProperties;
import co.edu.javeriana.procesosempresariales.service.TokenAccesoEmitido;
import co.edu.javeriana.procesosempresariales.service.TokenAccesoService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtInfraestructuraIntegracionTest {

    private static final String CORREO = "admin@alpes.com";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenAccesoService tokenAccesoService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private RefrescoProperties refrescoProperties;

    @Test
    void elPerfilDePruebasArrancaConUnaClaveValidaYLosValoresPorDefecto() {
        assertThat(jwtProperties.claveFirma().getEncoded()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(jwtProperties.duracionAcceso()).isEqualTo(Duration.ofMinutes(15));
        assertThat(refrescoProperties.cookie().nombre()).isEqualTo("PE_REFRESH");
        assertThat(refrescoProperties.cookie().ruta()).isEqualTo("/api/auth");
    }

    @Test
    void elTokenEmitidoSeDecodificaConLaMismaConfiguracion() {
        UUID familia = UUID.randomUUID();
        TokenAccesoEmitido emitido = tokenAccesoService.emitir(CORREO, familia);

        Jwt jwt = tokenAccesoService.decodificar(emitido.valor());

        assertThat(jwt.getSubject()).isEqualTo(CORREO);
        assertThat(tokenAccesoService.familiaDe(jwt)).isEqualTo(familia);
    }

    @Test
    void unBearerFirmadoDeUnaSesionInexistenteNoAutenticaLaApi() throws Exception {
        String token = tokenAccesoService.emitir(CORREO, UUID.randomUUID()).valor();

        mockMvc.perform(get("/api/procesos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));
    }

    @Test
    void laCookieCsrfSigueSiendoLegibleParaLaSpaYDistintaDeLaDeRefresco() throws Exception {
        mockMvc.perform(get("/api/sesion"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(COOKIE_CSRF))
                .andExpect(cookie().path(COOKIE_CSRF, "/"))
                .andExpect(cookie().httpOnly(COOKIE_CSRF, false))
                .andExpect(cookie().doesNotExist(refrescoProperties.cookie().nombre()));
    }
}
