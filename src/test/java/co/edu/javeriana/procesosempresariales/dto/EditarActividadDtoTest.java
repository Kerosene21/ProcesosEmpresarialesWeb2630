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
    void laEdicionNoExigeLaPosicionDeLaActividad() {
        assertThat(camposConError(formularioValido())).isEmpty();
        assertThat(formularioValido().cambiaPosicion()).isFalse();
    }

    @Test
    void laPosicionOpcionalSeIndicaCompleta() {
        EditarActividadDto dto = formularioValido();
        dto.setPosicionX(120);

        assertThat(camposConError(dto)).containsExactly("posicionCompleta");

        dto.setPosicionY(180);
        assertThat(camposConError(dto)).isEmpty();
        assertThat(dto.cambiaPosicion()).isTrue();
    }

    @Test
    void laPosicionOpcionalNoAdmiteCoordenadasFueraDelLienzo() {
        EditarActividadDto dto = new EditarActividadDto("Revisar solicitud", TipoActividad.TAREA_USUARIO, 11L, -1,
                50001);

        assertThat(camposConError(dto)).containsExactlyInAnyOrder("posicionX", "posicionY");
    }

    @Test
    void unFormularioVacioReportaTodosLosCampos() {
        assertThat(camposConError(new EditarActividadDto()))
                .containsExactlyInAnyOrder("nombre", "tipo", "laneId");
    }
}
