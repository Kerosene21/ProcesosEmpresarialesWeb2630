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

class CrearArcoDtoTest {

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

    private Set<String> camposConError(CrearArcoDto dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private CrearArcoDto formularioValido() {
        return new CrearArcoDto(TipoNodoFlujo.ACTIVIDAD, 30L, TipoNodoFlujo.ACTIVIDAD, 31L, null, null);
    }

    private String texto(int longitud) {
        return "a".repeat(longitud);
    }

    @Test
    void unFormularioCompletoNoProduceErrores() {
        assertThat(camposConError(formularioValido())).isEmpty();
    }

    @Test
    void laEtiquetaYLaCondicionSonOpcionales() {
        CrearArcoDto dto = formularioValido();
        dto.setEtiqueta("solicitud completa");
        dto.setCondicion("monto > 100");

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void elTipoDelOrigenEsObligatorio() {
        CrearArcoDto dto = formularioValido();
        dto.setOrigenTipo(null);

        assertThat(camposConError(dto)).containsExactly("origenTipo");
    }

    @Test
    void elOrigenEsObligatorio() {
        CrearArcoDto dto = formularioValido();
        dto.setOrigenId(null);

        assertThat(camposConError(dto)).containsExactly("origenId");
    }

    @Test
    void elTipoDelDestinoEsObligatorio() {
        CrearArcoDto dto = formularioValido();
        dto.setDestinoTipo(null);

        assertThat(camposConError(dto)).containsExactly("destinoTipo");
    }

    @Test
    void elDestinoEsObligatorio() {
        CrearArcoDto dto = formularioValido();
        dto.setDestinoId(null);

        assertThat(camposConError(dto)).containsExactly("destinoId");
    }

    @Test
    void laEtiquetaNoSuperaCientoCincuentaCaracteres() {
        CrearArcoDto dto = formularioValido();
        dto.setEtiqueta(texto(151));

        assertThat(camposConError(dto)).containsExactly("etiqueta");
    }

    @Test
    void laCondicionNoSuperaDoscientosCaracteres() {
        CrearArcoDto dto = formularioValido();
        dto.setCondicion(texto(201));

        assertThat(camposConError(dto)).containsExactly("condicion");
    }

    @Test
    void unFormularioVacioSenalaLosCuatroExtremos() {
        assertThat(camposConError(new CrearArcoDto()))
                .containsExactlyInAnyOrder("origenTipo", "origenId", "destinoTipo", "destinoId");
    }

    @Test
    void elGatewayTambienPuedeSerOrigenYDestino() {
        CrearArcoDto dto = new CrearArcoDto(TipoNodoFlujo.GATEWAY, 12L, TipoNodoFlujo.GATEWAY, 13L, null,
                "monto > 100");

        assertThat(camposConError(dto)).isEmpty();
    }
}
