package co.edu.javeriana.procesosempresariales.config;

import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("seguridad.jwt")
public record JwtProperties(
        String secreto,
        @DefaultValue("procesos-empresariales") String emisor,
        @DefaultValue("procesos-empresariales-api") String audiencia,
        @DefaultValue("15m") Duration duracionAcceso,
        @DefaultValue("30s") Duration toleranciaReloj) {

    static final int BYTES_MINIMOS_SECRETO = 32;
    static final String ALGORITMO_CLAVE = "HmacSHA256";
    private static final String PROPIEDAD_SECRETO = "seguridad.jwt.secreto (JWT_SECRET)";

    public JwtProperties {
        exigirTexto(emisor, "seguridad.jwt.emisor");
        exigirTexto(audiencia, "seguridad.jwt.audiencia");
        if (duracionAcceso == null || duracionAcceso.isZero() || duracionAcceso.isNegative()) {
            throw new IllegalArgumentException("seguridad.jwt.duracion-acceso debe ser positiva");
        }
        if (toleranciaReloj == null || toleranciaReloj.isNegative()) {
            throw new IllegalArgumentException("seguridad.jwt.tolerancia-reloj no puede ser negativa");
        }
    }

    public SecretKey claveFirma() {
        if (secreto == null || secreto.isBlank()) {
            throw new IllegalStateException("La propiedad " + PROPIEDAD_SECRETO + " es obligatoria");
        }
        byte[] clave;
        try {
            clave = Base64.getDecoder().decode(secreto.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("La propiedad " + PROPIEDAD_SECRETO + " debe estar codificada en Base64");
        }
        if (clave.length < BYTES_MINIMOS_SECRETO) {
            throw new IllegalStateException("La propiedad " + PROPIEDAD_SECRETO + " debe tener al menos "
                    + BYTES_MINIMOS_SECRETO + " bytes (256 bits) para HS256");
        }
        return new SecretKeySpec(clave, ALGORITMO_CLAVE);
    }

    @Override
    public String toString() {
        return "JwtProperties[emisor=" + emisor + ", audiencia=" + audiencia + ", duracionAcceso=" + duracionAcceso
                + ", toleranciaReloj=" + toleranciaReloj + ", secreto=" + (secreto == null ? "null" : "****") + "]";
    }

    private static void exigirTexto(String valor, String propiedad) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(propiedad + " es obligatoria");
        }
    }
}
