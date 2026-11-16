package co.edu.javeriana.procesosempresariales.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieRefresco {

    private RefrescoProperties.Cookie configuracion;
    private Clock reloj;

    @Autowired
    public CookieRefresco(RefrescoProperties refrescoProperties, Clock reloj) {
        this.configuracion = refrescoProperties.cookie();
        this.reloj = reloj;
    }

    public ResponseCookie crear(String codigo, Instant expiraEn) {
        return base(codigo).maxAge(segundosHasta(expiraEn)).build();
    }

    public long segundosHasta(Instant instante) {
        long milisegundos = Duration.between(reloj.instant(), instante).toMillis();
        return milisegundos <= 0 ? 0 : (milisegundos + 999) / 1000;
    }

    public ResponseCookie eliminar() {
        return base("").maxAge(Duration.ZERO).build();
    }

    public Optional<String> leer(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> configuracion.nombre().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(configuracion.nombre(), valor)
                .httpOnly(true)
                .secure(configuracion.segura())
                .sameSite(configuracion.sameSite())
                .path(configuracion.ruta());
    }
}
