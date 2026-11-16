package co.edu.javeriana.procesosempresariales.config;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("seguridad.refresco")
public record RefrescoProperties(
        @DefaultValue("7d") Duration duracion,
        @DefaultValue("30d") Duration duracionMaxima,
        @DefaultValue("20s") Duration graciaRotacion,
        @DefaultValue Cookie cookie) {

    public RefrescoProperties {
        exigirPositiva(duracion, "seguridad.refresco.duracion");
        exigirPositiva(duracionMaxima, "seguridad.refresco.duracion-maxima");
        exigirPositiva(graciaRotacion, "seguridad.refresco.gracia-rotacion");
        if (duracion.compareTo(duracionMaxima) > 0) {
            throw new IllegalArgumentException(
                    "seguridad.refresco.duracion no puede superar seguridad.refresco.duracion-maxima");
        }
        if (graciaRotacion.compareTo(duracion) >= 0) {
            throw new IllegalArgumentException(
                    "seguridad.refresco.gracia-rotacion debe ser menor que seguridad.refresco.duracion");
        }
    }

    public record Cookie(
            @DefaultValue("PE_REFRESH") String nombre,
            @DefaultValue("/api/auth") String ruta,
            @DefaultValue("true") boolean segura,
            @DefaultValue("Strict") String sameSite) {

        static final String RUTA_AUTENTICACION = "/api/auth";
        private static final Pattern NOMBRE_VALIDO = Pattern.compile("[A-Za-z0-9_-]+");
        private static final Set<String> NOMBRES_RESERVADOS = Set.of("XSRF-TOKEN", "JSESSIONID");
        private static final Set<String> SAME_SITE_PERMITIDOS = Set.of("Strict", "Lax");

        public Cookie {
            if (nombre == null || !NOMBRE_VALIDO.matcher(nombre).matches()) {
                throw new IllegalArgumentException("seguridad.refresco.cookie.nombre no es un nombre de cookie valido");
            }
            if (NOMBRES_RESERVADOS.contains(nombre.toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "seguridad.refresco.cookie.nombre no puede coincidir con la cookie CSRF ni con la de sesion");
            }
            if (ruta == null || !(ruta.equals(RUTA_AUTENTICACION) || ruta.startsWith(RUTA_AUTENTICACION + "/"))) {
                throw new IllegalArgumentException(
                        "seguridad.refresco.cookie.ruta debe limitarse a " + RUTA_AUTENTICACION);
            }
            if (!SAME_SITE_PERMITIDOS.contains(sameSite)) {
                throw new IllegalArgumentException("seguridad.refresco.cookie.same-site debe ser Strict o Lax");
            }
        }
    }

    private static void exigirPositiva(Duration duracion, String propiedad) {
        if (duracion == null || duracion.isZero() || duracion.isNegative()) {
            throw new IllegalArgumentException(propiedad + " debe ser positiva");
        }
    }
}
