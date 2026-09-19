package co.edu.javeriana.procesosempresariales.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;

class CambiarRolUsuarioDtoTest {

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

    @Test
    void elRolEsObligatorio() {
        CambiarRolUsuarioDto dto = new CambiarRolUsuarioDto(null);

        assertThat(validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet())).containsExactly("rol");
    }

    @Test
    void cualquieraDeLosTresRolesDeAccesoEsValido() {
        for (RolUsuario rol : RolUsuario.values()) {
            assertThat(validator.validate(new CambiarRolUsuarioDto(rol))).isEmpty();
        }
    }
}
