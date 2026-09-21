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

class CrearActividadDtoTest {

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

    private Set<String> camposConError(CrearActividadDto dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private CrearActividadDto formularioValido() {
        return new CrearActividadDto("Revisar solicitud", TipoActividad.TAREA_USUARIO, 11L, 120, 40);
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
        CrearActividadDto dto = formularioValido();
        dto.setNombre("   ");

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void elNombreNoPuedeFaltar() {
        CrearActividadDto dto = formularioValido();
        dto.setNombre(null);

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void elNombreAceptaElLimiteDeCiencuentaYCien() {
        CrearActividadDto dto = formularioValido();
        dto.setNombre(texto(150));

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void elNombreRechazaMasDeCientoCincuentaCaracteres() {
        CrearActividadDto dto = formularioValido();
        dto.setNombre(texto(151));

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void elTipoEsObligatorio() {
        CrearActividadDto dto = formularioValido();
        dto.setTipo(null);

        assertThat(camposConError(dto)).containsExactly("tipo");
    }

    @Test
    void laLaneEsObligatoria() {
        CrearActividadDto dto = formularioValido();
        dto.setLaneId(null);

        assertThat(camposConError(dto)).containsExactly("laneId");
    }

    @Test
    void laPosicionHorizontalEsObligatoria() {
        CrearActividadDto dto = formularioValido();
        dto.setPosicionX(null);

        assertThat(camposConError(dto)).containsExactly("posicionX");
    }

    @Test
    void laPosicionVerticalEsObligatoria() {
        CrearActividadDto dto = formularioValido();
        dto.setPosicionY(null);

        assertThat(camposConError(dto)).containsExactly("posicionY");
    }

    @Test
    void laPosicionAceptaElOrigenDelLienzo() {
        CrearActividadDto dto = formularioValido();
        dto.setPosicionX(0);
        dto.setPosicionY(0);

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void unFormularioVacioReportaTodosLosCampos() {
        assertThat(camposConError(new CrearActividadDto()))
                .containsExactlyInAnyOrder("nombre", "tipo", "laneId", "posicionX", "posicionY");
    }
}
