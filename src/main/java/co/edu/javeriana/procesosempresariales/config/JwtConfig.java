package co.edu.javeriana.procesosempresariales.config;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties({ JwtProperties.class, RefrescoProperties.class })
public class JwtConfig {

    public static final MacAlgorithm ALGORITMO = MacAlgorithm.HS256;
    public static final String CLAIM_FAMILIA = "sid";

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties propiedades) {
        return NimbusJwtEncoder.withSecretKey(propiedades.claveFirma()).algorithm(ALGORITMO).build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties propiedades, Clock reloj) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(propiedades.claveFirma())
                .macAlgorithm(ALGORITMO)
                .validateType(false)
                .build();
        decodificador.setJwtValidator(validador(propiedades, reloj));
        return decodificador;
    }

    private OAuth2TokenValidator<Jwt> validador(JwtProperties propiedades, Clock reloj) {
        JwtTimestampValidator vigencia = new JwtTimestampValidator(propiedades.toleranciaReloj());
        vigencia.setClock(reloj);
        vigencia.setAllowEmptyExpiryClaim(false);
        return new DelegatingOAuth2TokenValidator<>(List.of(
                JwtTypeValidator.jwt(),
                vigencia,
                new JwtIssuerValidator(propiedades.emisor()),
                new JwtAudienceValidator(propiedades.audiencia()),
                new JwtClaimValidator<Object>(JwtClaimNames.SUB, JwtConfig::conTexto),
                new JwtClaimValidator<Object>(JwtClaimNames.JTI, JwtConfig::conTexto),
                new JwtClaimValidator<Object>(CLAIM_FAMILIA, esUuid())));
    }

    private static boolean conTexto(Object valor) {
        return valor instanceof String texto && !texto.isBlank();
    }

    private static Predicate<Object> esUuid() {
        return valor -> {
            if (!conTexto(valor)) {
                return false;
            }
            try {
                return UUID.fromString((String) valor).toString().equals(valor);
            } catch (IllegalArgumentException e) {
                return false;
            }
        };
    }
}
