package co.edu.javeriana.procesosempresariales.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

class CookieRefrescoTest {

    private static final Instant AHORA = Instant.parse("2026-11-15T10:00:00Z");

    private CookieRefresco cookie(boolean segura, String sameSite) {
        RefrescoProperties propiedades = new RefrescoProperties(Duration.ofDays(7), Duration.ofDays(30),
                Duration.ofSeconds(20), new RefrescoProperties.Cookie("PE_REFRESH", "/api/auth", segura, sameSite));
        return new CookieRefresco(propiedades, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void laCookieEsHttpOnlySeguraEstrictaYLimitadaAApiAuth() {
        ResponseCookie creada = cookie(true, "Strict").crear("codigo", AHORA.plus(Duration.ofDays(7)));

        assertThat(creada.getName()).isEqualTo("PE_REFRESH");
        assertThat(creada.getValue()).isEqualTo("codigo");
        assertThat(creada.isHttpOnly()).isTrue();
        assertThat(creada.isSecure()).isTrue();
        assertThat(creada.getSameSite()).isEqualTo("Strict");
        assertThat(creada.getPath()).isEqualTo("/api/auth");
        assertThat(creada.getDomain()).isNull();
        assertThat(creada.getMaxAge()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void secureYSameSiteSeConfiguranParaDesarrolloLocal() {
        ResponseCookie creada = cookie(false, "Lax").crear("codigo", AHORA.plus(Duration.ofHours(1)));

        assertThat(creada.isSecure()).isFalse();
        assertThat(creada.getSameSite()).isEqualTo("Lax");
        assertThat(creada.isHttpOnly()).isTrue();
        assertThat(creada.getMaxAge()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void unaExpiracionPasadaProduceUnaCookieSinVigencia() {
        ResponseCookie creada = cookie(true, "Strict").crear("codigo", AHORA.minusSeconds(1));

        assertThat(creada.getMaxAge()).isEqualTo(Duration.ZERO);
    }

    @Test
    void eliminarVaciaLaCookieConLaMismaRutaYAtributos() {
        ResponseCookie eliminada = cookie(true, "Strict").eliminar();

        assertThat(eliminada.getValue()).isEmpty();
        assertThat(eliminada.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(eliminada.getPath()).isEqualTo("/api/auth");
        assertThat(eliminada.isHttpOnly()).isTrue();
        assertThat(eliminada.isSecure()).isTrue();
    }

    @Test
    void leerSoloDevuelveElValorDeLaCookieDeRefresco() {
        CookieRefresco refresco = cookie(true, "Strict");
        MockHttpServletRequest conCookie = new MockHttpServletRequest();
        conCookie.setCookies(new Cookie("XSRF-TOKEN", "csrf"), new Cookie("PE_REFRESH", "codigo"));
        MockHttpServletRequest vacia = new MockHttpServletRequest();
        vacia.setCookies(new Cookie("PE_REFRESH", " "));

        assertThat(refresco.leer(conCookie)).contains("codigo");
        assertThat(refresco.leer(vacia)).isEmpty();
        assertThat(refresco.leer(new MockHttpServletRequest())).isEmpty();
    }
}
