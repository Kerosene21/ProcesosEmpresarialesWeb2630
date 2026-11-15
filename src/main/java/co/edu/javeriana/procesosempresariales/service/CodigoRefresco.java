package co.edu.javeriana.procesosempresariales.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class CodigoRefresco {

    static final int BYTES_CODIGO = 32;
    private static final String ALGORITMO_HASH = "SHA-256";

    private final SecureRandom aleatorio = new SecureRandom();

    public String generar() {
        byte[] bytes = new byte[BYTES_CODIGO];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("El codigo de refresco es obligatorio");
        }
        try {
            byte[] resumen = MessageDigest.getInstance(ALGORITMO_HASH).digest(codigo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no ofrece " + ALGORITMO_HASH, e);
        }
    }
}
