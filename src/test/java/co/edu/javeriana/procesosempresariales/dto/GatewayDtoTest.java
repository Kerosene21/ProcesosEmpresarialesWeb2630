package co.edu.javeriana.procesosempresariales.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import co.edu.javeriana.procesosempresariales.domain.TipoGateway;

class GatewayDtoTest {

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

    private CrearGatewayDto formularioDeCreacion() {
        return new CrearGatewayDto(TipoGateway.EXCLUSIVO, 300, 120);
    }

    @Test
    void unFormularioDeCreacionCompletoNoProduceErrores() {
        assertThat(camposConError(formularioDeCreacion())).isEmpty();
    }

    @Test
    void elTipoDeGatewayEsObligatorioAlCrear() {
        CrearGatewayDto dto = formularioDeCreacion();
        dto.setTipo(null);

        assertThat(camposConError(dto)).containsExactly("tipo");
    }

    @Test
    void laPosicionEsObligatoriaAlCrear() {
        CrearGatewayDto dto = formularioDeCreacion();
        dto.setPosicionX(null);
        dto.setPosicionY(null);

        assertThat(camposConError(dto)).containsExactlyInAnyOrder("posicionX", "posicionY");
    }

    @Test
    void laPosicionAdmiteValoresNegativos() {
        CrearGatewayDto dto = new CrearGatewayDto(TipoGateway.PARALELO, -10, -20);

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void elTipoDeGatewayEsObligatorioAlEditar() {
        assertThat(camposConError(new EditarGatewayDto())).containsExactly("tipo");
    }

    @Test
    void elFormularioDeEdicionNaceConUnMapaDeCondicionesVacio() {
        assertThat(new EditarGatewayDto().getCondiciones()).isEmpty();
    }

    @Test
    void elFormularioDeEdicionAceptaCondicionesPorArco() {
        Map<Long, String> condiciones = new LinkedHashMap<>();
        condiciones.put(61L, "monto > 100");
        EditarGatewayDto dto = new EditarGatewayDto(TipoGateway.EXCLUSIVO, condiciones);

        assertThat(camposConError(dto)).isEmpty();
        assertThat(dto.getCondiciones()).containsEntry(61L, "monto > 100");
    }

    @Test
    void cadaTipoDeGatewayTieneSuSimbolo() {
        assertThat(TipoGateway.EXCLUSIVO.getSimbolo()).isEqualTo("X");
        assertThat(TipoGateway.PARALELO.getSimbolo()).isEqualTo("+");
        assertThat(TipoGateway.INCLUSIVO.getSimbolo()).isEqualTo("O");
    }

    @Test
    void soloElGatewayParaleloNoExigeCondicion() {
        assertThat(TipoGateway.EXCLUSIVO.exigeCondicion()).isTrue();
        assertThat(TipoGateway.INCLUSIVO.exigeCondicion()).isTrue();
        assertThat(TipoGateway.PARALELO.exigeCondicion()).isFalse();
    }
}
