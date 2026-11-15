package co.edu.javeriana.procesosempresariales.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class JwtConfigTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfig.class, RelojConfig.class);

    @Test
    void conUnaClaveValidaSeCreanElCodificadorYElDecodificadorConLosValoresPorDefecto() {
        contexto.withPropertyValues("seguridad.jwt.secreto=" + JwtPropertiesTest.secretoAleatorio(48))
                .run(aplicacion -> {
                    assertThat(aplicacion).hasSingleBean(JwtEncoder.class).hasSingleBean(JwtDecoder.class);
                    JwtProperties jwt = aplicacion.getBean(JwtProperties.class);
                    RefrescoProperties refresco = aplicacion.getBean(RefrescoProperties.class);
                    assertThat(jwt.duracionAcceso()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(jwt.toleranciaReloj()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(refresco.duracion()).isEqualTo(Duration.ofDays(7));
                    assertThat(refresco.duracionMaxima()).isEqualTo(Duration.ofDays(30));
                    assertThat(refresco.cookie().nombre()).isEqualTo("PE_REFRESH");
                    assertThat(refresco.cookie().ruta()).isEqualTo("/api/auth");
                    assertThat(refresco.cookie().segura()).isTrue();
                    assertThat(refresco.cookie().sameSite()).isEqualTo("Strict");
                });
    }

    @Test
    void sinClaveElContextoNoArranca() {
        contexto.run(aplicacion -> assertThat(aplicacion).hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("seguridad.jwt.secreto"));
    }

    @Test
    void conUnaClaveCortaElContextoNoArrancaNiMuestraLaClave() {
        String corta = JwtPropertiesTest.secretoAleatorio(16);

        contexto.withPropertyValues("seguridad.jwt.secreto=" + corta)
                .run(aplicacion -> {
                    assertThat(aplicacion).hasFailed();
                    assertThat(aplicacion.getStartupFailure()).rootCause().hasMessageContaining("32 bytes");
                    assertThat(rastro(aplicacion.getStartupFailure())).doesNotContain(corta);
                });
    }

    @Test
    void unaCookieDeRefrescoFueraDeApiAuthImpideArrancar() {
        contexto.withPropertyValues("seguridad.jwt.secreto=" + JwtPropertiesTest.secretoAleatorio(48),
                "seguridad.refresco.cookie.ruta=/")
                .run(aplicacion -> assertThat(aplicacion).hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("/api/auth"));
    }

    private String rastro(Throwable error) {
        StringBuilder texto = new StringBuilder();
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            texto.append(actual).append('\n');
        }
        return texto.toString();
    }
}
