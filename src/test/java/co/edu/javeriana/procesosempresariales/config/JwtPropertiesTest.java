package co.edu.javeriana.procesosempresariales.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    private static final String EMISOR = "procesos-empresariales";
    private static final String AUDIENCIA = "procesos-empresariales-api";

    static String secretoAleatorio(int bytes) {
        byte[] clave = new byte[bytes];
        new SecureRandom().nextBytes(clave);
        return Base64.getEncoder().encodeToString(clave);
    }

    private JwtProperties conSecreto(String secreto) {
        return new JwtProperties(secreto, EMISOR, AUDIENCIA, Duration.ofMinutes(15), Duration.ofSeconds(30));
    }

    @Test
    void unaClaveDe32BytesOMasProduceUnaClaveHmacSha256() {
        String secreto = secretoAleatorio(48);

        SecretKey clave = conSecreto(secreto).claveFirma();

        assertThat(clave.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(clave.getEncoded()).isEqualTo(Base64.getDecoder().decode(secreto));
    }

    @Test
    void elSecretoEsObligatorio() {
        assertThatThrownBy(() -> conSecreto(null).claveFirma())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("seguridad.jwt.secreto")
                .hasMessageContaining("obligatoria");
        assertThatThrownBy(() -> conSecreto("   ").claveFirma())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unSecretoQueNoEsBase64SeRechazaSinMostrarSuValor() {
        String secreto = "esto no es base64 %%%%";

        assertThatThrownBy(() -> conSecreto(secreto).claveFirma())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64")
                .hasMessageNotContaining(secreto);
    }

    @Test
    void unaClaveDeMenosDe32BytesSeRechazaSinMostrarSuValor() {
        String secreto = secretoAleatorio(31);

        assertThatThrownBy(() -> conSecreto(secreto).claveFirma())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes")
                .hasMessageNotContaining(secreto);
    }

    @Test
    void laRepresentacionEnTextoOcultaElSecreto() {
        String secreto = secretoAleatorio(48);

        assertThat(conSecreto(secreto).toString()).doesNotContain(secreto).contains("****");
    }

    @Test
    void lasDuracionesYLosIdentificadoresSeValidan() {
        String secreto = secretoAleatorio(32);

        assertThatThrownBy(() -> new JwtProperties(secreto, " ", AUDIENCIA, Duration.ofMinutes(15), Duration.ZERO))
                .hasMessageContaining("emisor");
        assertThatThrownBy(() -> new JwtProperties(secreto, EMISOR, null, Duration.ofMinutes(15), Duration.ZERO))
                .hasMessageContaining("audiencia");
        assertThatThrownBy(() -> new JwtProperties(secreto, EMISOR, AUDIENCIA, Duration.ZERO, Duration.ZERO))
                .hasMessageContaining("duracion-acceso");
        assertThatThrownBy(() -> new JwtProperties(secreto, EMISOR, AUDIENCIA, Duration.ofMinutes(15),
                Duration.ofSeconds(-1)))
                .hasMessageContaining("tolerancia-reloj");
    }
}
