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

import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;

class EditarArcoDtoTest {

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

    private Set<String> camposConError(EditarArcoDto dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private EditarArcoDto formularioValido() {
        return new EditarArcoDto(TipoNodoFlujo.ACTIVIDAD, 30L, TipoNodoFlujo.ACTIVIDAD, 31L, "solicitud completa",
                null);
    }

    @Test
    void unFormularioCompletoNoProduceErrores() {
        assertThat(camposConError(formularioValido())).isEmpty();
    }

    @Test
    void elOrigenEsObligatorio() {
        EditarArcoDto dto = formularioValido();
        dto.setOrigenId(null);

        assertThat(camposConError(dto)).containsExactly("origenId");
    }

    @Test
    void elDestinoEsObligatorio() {
        EditarArcoDto dto = formularioValido();
        dto.setDestinoId(null);

        assertThat(camposConError(dto)).containsExactly("destinoId");
    }

    @Test
    void laEtiquetaNoSuperaCientoCincuentaCaracteres() {
        EditarArcoDto dto = formularioValido();
        dto.setEtiqueta("a".repeat(151));

        assertThat(camposConError(dto)).containsExactly("etiqueta");
    }

    @Test
    void laCondicionNoSuperaDoscientosCaracteres() {
        EditarArcoDto dto = formularioValido();
        dto.setCondicion("a".repeat(201));

        assertThat(camposConError(dto)).containsExactly("condicion");
    }

    @Test
    void unFormularioVacioSenalaLosCuatroExtremos() {
        assertThat(camposConError(new EditarArcoDto()))
                .containsExactlyInAnyOrder("origenTipo", "origenId", "destinoTipo", "destinoId");
    }
}
