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

import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;

class MoverNodoDtoTest {

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

    private Set<String> camposConError(Object dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void unaPosicionDentroDelLienzoEsValidaIncluidosSusLimites() {
        assertThat(camposConError(new MoverNodoDto(0, 0))).isEmpty();
        assertThat(camposConError(new MoverNodoDto(MoverNodoDto.POSICION_MAXIMA, MoverNodoDto.POSICION_MAXIMA)))
                .isEmpty();
    }

    @Test
    void lasCoordenadasSonObligatorias() {
        assertThat(camposConError(new MoverNodoDto())).containsExactlyInAnyOrder("posicionX", "posicionY");
    }

    @Test
    void lasCoordenadasNegativasOFueraDeRangoSeRechazan() {
        assertThat(camposConError(new MoverNodoDto(-1, 40))).containsExactly("posicionX");
        assertThat(camposConError(new MoverNodoDto(40, MoverNodoDto.POSICION_MAXIMA + 1))).containsExactly("posicionY");
    }

    @Test
    void moverUnaActividadExigeLaLaneYLasCoordenadas() {
        assertThat(camposConError(new MoverActividadDto(11L, 120, 180))).isEmpty();
        assertThat(camposConError(new MoverActividadDto()))
                .containsExactlyInAnyOrder("laneId", "posicionX", "posicionY");
        assertThat(camposConError(new MoverActividadDto(11L, -5, 50001)))
                .containsExactlyInAnyOrder("posicionX", "posicionY");
    }

    @Test
    void recolocarExigeAlMenosUnNodoYValidaCadaUno() {
        PosicionNodoDto valido = new PosicionNodoDto(TipoNodoFlujo.EVENTO, 41L, 40, 200);
        PosicionNodoDto incompleto = new PosicionNodoDto(null, null, -1, 60);

        assertThat(camposConError(new RecolocarNodosDto(new ArrayList<>(List.of(valido))))).isEmpty();
        assertThat(camposConError(new RecolocarNodosDto())).containsExactly("nodos");
        assertThat(camposConError(new RecolocarNodosDto(new ArrayList<>(List.of(valido, incompleto)))))
                .containsExactlyInAnyOrder("nodos[1].tipo", "nodos[1].id", "nodos[1].posicionX");
    }
}
