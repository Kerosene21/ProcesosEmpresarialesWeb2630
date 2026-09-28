package co.edu.javeriana.procesosempresariales.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LaneDtoTest {

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

    @Test
    void unaLaneConRolYSinOrdenNoProduceErrores() {
        assertThat(camposConError(new CrearLaneDto(40L, null))).isEmpty();
    }

    @Test
    void unaLaneNuevaExigeRolDeProceso() {
        assertThat(camposConError(new CrearLaneDto(null, 1))).containsExactly("rolProcesoId");
    }

    @Test
    void elOrdenDeUnaLaneNuevaEmpiezaEnUno() {
        assertThat(camposConError(new CrearLaneDto(40L, 0))).containsExactly("orden");
        assertThat(camposConError(new CrearLaneDto(40L, 1))).isEmpty();
    }

    @Test
    void editarUnaLaneExigeRolDeProceso() {
        assertThat(camposConError(new EditarLaneDto(null, null))).containsExactly("rolProcesoId");
    }

    @Test
    void elOrdenAlEditarUnaLaneEmpiezaEnUno() {
        assertThat(camposConError(new EditarLaneDto(40L, 0))).containsExactly("orden");
        assertThat(camposConError(new EditarLaneDto(40L, 3))).isEmpty();
    }

    @Test
    void reordenarExigeAlMenosUnaLane() {
        assertThat(camposConError(new ReordenarLanesDto(List.of()))).containsExactly("lanes");
        assertThat(camposConError(new ReordenarLanesDto(null))).containsExactly("lanes");
    }

    @Test
    void reordenarNoAdmiteIdentificadoresNulos() {
        List<Long> lanes = new ArrayList<>();
        lanes.add(12L);
        lanes.add(null);

        assertThat(camposConError(new ReordenarLanesDto(lanes))).containsExactly("lanes[1].<list element>");
    }

    @Test
    void reordenarConIdentificadoresValidosNoProduceErrores() {
        assertThat(camposConError(new ReordenarLanesDto(List.of(12L, 11L)))).isEmpty();
    }
}
