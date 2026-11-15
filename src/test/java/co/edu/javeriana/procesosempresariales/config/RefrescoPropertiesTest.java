package co.edu.javeriana.procesosempresariales.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RefrescoPropertiesTest {

    private static final Duration SIETE_DIAS = Duration.ofDays(7);
    private static final Duration TREINTA_DIAS = Duration.ofDays(30);
    private static final Duration GRACIA = Duration.ofSeconds(20);

    private RefrescoProperties.Cookie cookie(String nombre, String ruta, String sameSite) {
        return new RefrescoProperties.Cookie(nombre, ruta, true, sameSite);
    }

    private RefrescoProperties.Cookie cookieValida() {
        return cookie("PE_REFRESH", "/api/auth", "Strict");
    }

    @Test
    void unaConfiguracionCoherenteSeAcepta() {
        RefrescoProperties propiedades = new RefrescoProperties(SIETE_DIAS, TREINTA_DIAS, GRACIA, cookieValida());

        assertThat(propiedades.cookie().nombre()).isEqualTo("PE_REFRESH");
        assertThat(propiedades.cookie().ruta()).isEqualTo("/api/auth");
        assertThat(propiedades.cookie().segura()).isTrue();
    }

    @Test
    void lasDuracionesDebenSerPositivasYCoherentes() {
        assertThatThrownBy(() -> new RefrescoProperties(Duration.ZERO, TREINTA_DIAS, GRACIA, cookieValida()))
                .hasMessageContaining("seguridad.refresco.duracion");
        assertThatThrownBy(() -> new RefrescoProperties(Duration.ofDays(31), TREINTA_DIAS, GRACIA, cookieValida()))
                .hasMessageContaining("duracion-maxima");
        assertThatThrownBy(() -> new RefrescoProperties(SIETE_DIAS, TREINTA_DIAS, SIETE_DIAS, cookieValida()))
                .hasMessageContaining("gracia-rotacion");
        assertThatThrownBy(() -> new RefrescoProperties(SIETE_DIAS, TREINTA_DIAS, Duration.ofSeconds(-1),
                cookieValida()))
                .hasMessageContaining("gracia-rotacion");
    }

    @Test
    void laCookieDeRefrescoSoloPuedeEnviarseALosEndpointsDeAutenticacion() {
        assertThat(cookie("PE_REFRESH", "/api/auth/refresh", "Strict").ruta()).isEqualTo("/api/auth/refresh");
        assertThatThrownBy(() -> cookie("PE_REFRESH", "/", "Strict"))
                .hasMessageContaining("/api/auth");
        assertThatThrownBy(() -> cookie("PE_REFRESH", "/api", "Strict"))
                .hasMessageContaining("/api/auth");
        assertThatThrownBy(() -> cookie("PE_REFRESH", "/api/authx", "Strict"))
                .hasMessageContaining("/api/auth");
    }

    @Test
    void laCookieDeRefrescoNoPuedeConfundirseConLasCookiesCsrfNiDeSesion() {
        assertThatThrownBy(() -> cookie("XSRF-TOKEN", "/api/auth", "Strict"))
                .hasMessageContaining("CSRF");
        assertThatThrownBy(() -> cookie("jsessionid", "/api/auth", "Strict"))
                .hasMessageContaining("sesion");
        assertThatThrownBy(() -> cookie("PE REFRESH", "/api/auth", "Strict"))
                .hasMessageContaining("nombre");
    }

    @Test
    void sameSiteSoloAdmiteStrictOLax() {
        assertThat(cookie("PE_REFRESH", "/api/auth", "Lax").sameSite()).isEqualTo("Lax");
        assertThatThrownBy(() -> cookie("PE_REFRESH", "/api/auth", "None"))
                .hasMessageContaining("same-site");
    }
}
