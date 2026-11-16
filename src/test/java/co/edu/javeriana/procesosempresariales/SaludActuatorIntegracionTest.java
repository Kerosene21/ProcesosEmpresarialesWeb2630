package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SaludActuatorIntegracionTest {

    private static final List<String> SONDAS = List.of("/actuator/health", "/actuator/health/liveness",
            "/actuator/health/readiness");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HealthEndpointGroups grupos;

    @Test
    void lasSondasDeSaludSonPublicasSinDetallesNiSesion() throws Exception {
        for (String sonda : SONDAS) {
            MvcResult resultado = mockMvc.perform(get(sonda))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist())
                    .andExpect(jsonPath("$.details").doesNotExist())
                    .andExpect(cookie().doesNotExist("JSESSIONID"))
                    .andExpect(cookie().doesNotExist("XSRF-TOKEN"))
                    .andReturn();
            assertThat(resultado.getRequest().getSession(false)).as(sonda).isNull();
        }
    }

    @Test
    void laReadinessDependeDeLaBaseDeDatosYLaLivenessNo() {
        assertThat(grupos.get("readiness").isMember("db")).isTrue();
        assertThat(grupos.get("readiness").isMember("readinessState")).isTrue();
        assertThat(grupos.get("liveness").isMember("db")).isFalse();
        assertThat(grupos.get("liveness").isMember("livenessState")).isTrue();
    }

    @Test
    void elRestoDeActuatorNoEstaDisponible() throws Exception {
        for (String ruta : List.of("/actuator", "/actuator/env", "/actuator/info", "/actuator/beans",
                "/actuator/health/db", "/actuator/mappings")) {
            mockMvc.perform(get(ruta))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));
        }
        mockMvc.perform(post("/actuator/health"))
                .andExpect(status().isUnauthorized());
    }
}
