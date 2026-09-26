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

class ConfigurarPermisoEstructuraDtoTest {

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

    private Set<String> camposConError(ConfigurarPermisoEstructuraDto dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void unaConfiguracionCompletaNoProduceErrores() {
        assertThat(camposConError(new ConfigurarPermisoEstructuraDto(true, true, false, true, true, false)))
                .isEmpty();
    }

    @Test
    void cadaPermisoDebeIndicarseExplicitamente() {
        assertThat(camposConError(new ConfigurarPermisoEstructuraDto()))
                .containsExactlyInAnyOrder("crearPool", "editarPool", "eliminarPool", "crearLane", "editarLane",
                        "eliminarLane");
    }

    @Test
    void unSoloPermisoOmitidoSeReportaPorSuNombre() {
        assertThat(camposConError(new ConfigurarPermisoEstructuraDto(true, true, null, true, true, false)))
                .containsExactly("eliminarPool");
    }
}
