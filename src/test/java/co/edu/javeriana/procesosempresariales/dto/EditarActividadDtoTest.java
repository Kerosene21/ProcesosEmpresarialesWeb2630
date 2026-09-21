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

import co.edu.javeriana.procesosempresariales.domain.TipoActividad;

class EditarActividadDtoTest {

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

    private Set<String> camposConError(EditarActividadDto dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private EditarActividadDto formularioValido() {
        return new EditarActividadDto("Revisar solicitud", TipoActividad.TAREA_USUARIO, 11L);
    }

    private String texto(int longitud) {
        return "a".repeat(longitud);
    }

    @Test
    void unFormularioCompletoNoProduceErrores() {
        assertThat(camposConError(formularioValido())).isEmpty();
    }

    @Test
    void elNombreEsObligatorio() {
        EditarActividadDto dto = formularioValido();
        dto.setNombre("   ");

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void elNombreRechazaMasDeCientoCincuentaCaracteres() {
        EditarActividadDto dto = formularioValido();
        dto.setNombre(texto(151));

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void elTipoEsObligatorio() {
        EditarActividadDto dto = formularioValido();
        dto.setTipo(null);

        assertThat(camposConError(dto)).containsExactly("tipo");
    }

    @Test
    void laLaneEsObligatoria() {
        EditarActividadDto dto = formularioValido();
        dto.setLaneId(null);

        assertThat(camposConError(dto)).containsExactly("laneId");
    }

    @Test
    void laEdicionNoPideLaPosicionDeLaActividad() {
        assertThat(camposConError(formularioValido())).isEmpty();
        assertThat(EditarActividadDto.class.getDeclaredFields())
                .extracting(campo -> campo.getName())
                .containsExactlyInAnyOrder("nombre", "tipo", "laneId");
    }

    @Test
    void unFormularioVacioReportaTodosLosCampos() {
        assertThat(camposConError(new EditarActividadDto()))
                .containsExactlyInAnyOrder("nombre", "tipo", "laneId");
    }
}
