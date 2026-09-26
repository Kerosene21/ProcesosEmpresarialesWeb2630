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

class CrearRolProcesoDtoTest {

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

    private Set<String> camposConError(CrearRolProcesoDto dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private CrearRolProcesoDto formularioValido() {
        return new CrearRolProcesoDto("Analista de credito", "Evalua el riesgo de cada solicitud");
    }

    @Test
    void unFormularioCompletoNoProduceErrores() {
        assertThat(camposConError(formularioValido())).isEmpty();
    }

    @Test
    void elNombreEsObligatorio() {
        CrearRolProcesoDto dto = formularioValido();
        dto.setNombre("   ");

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void elNombreNoPuedeSerNulo() {
        CrearRolProcesoDto dto = formularioValido();
        dto.setNombre(null);

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void elNombreAdmiteHastaCienCaracteres() {
        CrearRolProcesoDto dto = formularioValido();
        dto.setNombre("a".repeat(100));

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void elNombreNoPuedeSuperarCienCaracteres() {
        CrearRolProcesoDto dto = formularioValido();
        dto.setNombre("a".repeat(101));

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void laDescripcionEsObligatoria() {
        CrearRolProcesoDto dto = formularioValido();
        dto.setDescripcion("   ");

        assertThat(camposConError(dto)).containsExactly("descripcion");
    }

    @Test
    void laDescripcionNoPuedeSerNula() {
        CrearRolProcesoDto dto = formularioValido();
        dto.setDescripcion(null);

        assertThat(camposConError(dto)).containsExactly("descripcion");
    }

    @Test
    void laDescripcionAdmiteHastaQuinientosCaracteres() {
        CrearRolProcesoDto dto = formularioValido();
        dto.setDescripcion("a".repeat(500));

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void laDescripcionNoPuedeSuperarQuinientosCaracteres() {
        CrearRolProcesoDto dto = formularioValido();
        dto.setDescripcion("a".repeat(501));

        assertThat(camposConError(dto)).containsExactly("descripcion");
    }
}
