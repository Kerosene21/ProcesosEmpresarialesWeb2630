package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CodigoRefrescoTest {

    private final CodigoRefresco codigoRefresco = new CodigoRefresco();

    @Test
    void generaCodigosOpacosDe256BitsSeguroParaCookies() {
        String codigo = codigoRefresco.generar();

        assertThat(codigo).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(codigo)).hasSize(CodigoRefresco.BYTES_CODIGO);
    }

    @Test
    void losCodigosGeneradosNoSeRepiten() {
        Set<String> codigos = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codigos.add(codigoRefresco.generar());
        }

        assertThat(codigos).hasSize(1000);
    }

    @Test
    void elHashEsSha256EnHexadecimalYDeterminista() {
        assertThat(codigoRefresco.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

        String codigo = codigoRefresco.generar();
        String hash = codigoRefresco.hash(codigo);

        assertThat(hash).hasSize(64).matches("[0-9a-f]+").isEqualTo(codigoRefresco.hash(codigo)).isNotEqualTo(codigo);
    }

    @Test
    void elHashExigeUnCodigo() {
        assertThatThrownBy(() -> codigoRefresco.hash(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codigoRefresco.hash(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
