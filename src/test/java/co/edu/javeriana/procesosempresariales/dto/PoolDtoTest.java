package co.edu.javeriana.procesosempresariales.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import co.edu.javeriana.procesosempresariales.domain.TipoPool;

class PoolDtoTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void abrirValidador() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        validatorFactory.close();
    }

    private <T> Set<String> camposConError(T dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private CrearPoolDto creacionValida() {
        return new CrearPoolDto("Cliente", TipoPool.EXTERNO, true, null);
    }

    private EditarPoolDto edicionValida() {
        return new EditarPoolDto("Cliente corporativo", false, 99L);
    }

    @Test
    void unaCreacionCompletaNoProduceErrores() {
        assertThat(camposConError(creacionValida())).isEmpty();
    }

    @Test
    void alCrearElNombreEsObligatorio() {
        CrearPoolDto dto = creacionValida();
        dto.setNombre("   ");

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void alCrearElNombreAdmiteHastaCientoCincuentaCaracteres() {
        CrearPoolDto dto = creacionValida();
        dto.setNombre("a".repeat(150));

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void alCrearElNombreNoPuedeSuperarCientoCincuentaCaracteres() {
        CrearPoolDto dto = creacionValida();
        dto.setNombre("a".repeat(151));

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void alCrearElTipoEsObligatorio() {
        CrearPoolDto dto = creacionValida();
        dto.setTipo(null);

        assertThat(camposConError(dto)).containsExactly("tipo");
    }

    @Test
    void alCrearSinIndicarCajaNegraElPoolNoEsCajaNegra() {
        CrearPoolDto dto = creacionValida();
        dto.setCajaNegra(null);

        assertThat(camposConError(dto)).isEmpty();
        assertThat(dto.esCajaNegra()).isFalse();
    }

    @Test
    void alCrearUnaCajaNegraExplicitaSeRespeta() {
        assertThat(creacionValida().esCajaNegra()).isTrue();
    }

    @Test
    void unaEdicionCompletaNoProduceErrores() {
        assertThat(camposConError(edicionValida())).isEmpty();
    }

    @Test
    void alEditarElNombreEsObligatorio() {
        EditarPoolDto dto = edicionValida();
        dto.setNombre(null);

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void alEditarElNombreNoPuedeSuperarCientoCincuentaCaracteres() {
        EditarPoolDto dto = edicionValida();
        dto.setNombre("a".repeat(151));

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void alEditarSinIndicarCajaNegraElPoolNoEsCajaNegra() {
        EditarPoolDto dto = edicionValida();
        dto.setCajaNegra(null);

        assertThat(dto.esCajaNegra()).isFalse();
        dto.setCajaNegra(true);
        assertThat(dto.esCajaNegra()).isTrue();
    }
}
