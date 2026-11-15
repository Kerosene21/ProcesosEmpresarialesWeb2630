package co.edu.javeriana.procesosempresariales.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import co.edu.javeriana.procesosempresariales.config.JwtConfig;
import co.edu.javeriana.procesosempresariales.config.JwtProperties;

@Service
public class TokenAccesoService {

    private static final String TIPO_TOKEN = "JWT";

    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;
    private JwtProperties propiedades;
    private Clock reloj;

    @Autowired
    public TokenAccesoService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, JwtProperties propiedades, Clock reloj) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    public TokenAccesoEmitido emitir(String correo, UUID familiaId) {
        return emitir(correo, familiaId, null);
    }

    public TokenAccesoEmitido emitir(String correo, UUID familiaId, Instant expiraComoMaximo) {
        if (correo == null || correo.isBlank() || familiaId == null) {
            throw new IllegalArgumentException("El token de acceso requiere el correo del usuario y su familia");
        }
        Instant emitidoEn = reloj.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiraEn = emitidoEn.plus(propiedades.duracionAcceso());
        if (expiraComoMaximo != null && expiraComoMaximo.isBefore(expiraEn)) {
            expiraEn = expiraComoMaximo.truncatedTo(ChronoUnit.SECONDS);
        }
        if (!expiraEn.isAfter(emitidoEn)) {
            throw new IllegalArgumentException("El token de acceso debe expirar despues de emitirse");
        }
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(propiedades.emisor())
                .audience(List.of(propiedades.audiencia()))
                .subject(correo)
                .id(UUID.randomUUID().toString())
                .issuedAt(emitidoEn)
                .expiresAt(expiraEn)
                .claim(JwtConfig.CLAIM_FAMILIA, familiaId.toString())
                .build();
        JwsHeader cabecera = JwsHeader.with(JwtConfig.ALGORITMO).type(TIPO_TOKEN).build();
        String valor = jwtEncoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new TokenAccesoEmitido(valor, emitidoEn, expiraEn);
    }

    public Duration duracionAcceso() {
        return propiedades.duracionAcceso();
    }

    public Jwt decodificar(String token) {
        return jwtDecoder.decode(token);
    }

    public UUID familiaDe(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString(JwtConfig.CLAIM_FAMILIA));
    }
}
