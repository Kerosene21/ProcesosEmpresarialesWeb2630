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

import co.edu.javeriana.procesosempresariales.domain.ComportamientoFallo;
import co.edu.javeriana.procesosempresariales.domain.ComportamientoSinCaso;
import co.edu.javeriana.procesosempresariales.domain.TipoDestinoExterno;
import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;

class MensajesDtoTest {

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

    private CrearMessageThrowDto throwCompleto() {
        return new CrearMessageThrowDto("Solicitud de pago", "numeroFactura: texto", 80L, 90L, "factura",
                ComportamientoSinCaso.DESCARTAR, 400, 60);
    }

    private CrearMessageCatchDto catchCompleto() {
        return new CrearMessageCatchDto("Pago confirmado", VarianteMessageCatch.INTERMEDIO, "numeroFactura: texto",
                "Registrar pago", false, "factura", ComportamientoSinCaso.DESCARTAR, 80L, 40, 60);
    }

    private CrearEnvioExternoDto envioCompleto() {
        return new CrearEnvioExternoDto("Notificar despacho", 80L, 90L, TipoDestinoExterno.CORREO,
                "numeroGuia: texto", "Al confirmar el despacho", ComportamientoFallo.CONTINUAR, null, 700, 80);
    }

    @Test
    void losFormulariosCompletosNoProducenErrores() {
        assertThat(camposConError(throwCompleto())).isEmpty();
        assertThat(camposConError(catchCompleto())).isEmpty();
        assertThat(camposConError(envioCompleto())).isEmpty();
        assertThat(camposConError(new EditarMessageThrowDto("Pago", "valor: decimal", 90L, null, null))).isEmpty();
        assertThat(camposConError(new EditarMessageCatchDto("Pago", VarianteMessageCatch.INICIO, "valor: decimal",
                "Registrar pago", null, null, null))).isEmpty();
        assertThat(camposConError(new EditarEnvioExternoDto("Notificar", 90L, TipoDestinoExterno.COLA,
                "guia: texto", "Al despachar", ComportamientoFallo.FINALIZAR, "guia"))).isEmpty();
    }

    @Test
    void elMessageThrowExigeNombreContenidoPoolsYPosicion() {
        assertThat(camposConError(new CrearMessageThrowDto())).containsExactlyInAnyOrder("nombreMensaje",
                "contenido", "poolOrigenId", "poolDestinoId", "posicionX", "posicionY");
        assertThat(camposConError(new EditarMessageThrowDto())).containsExactlyInAnyOrder("nombreMensaje",
                "contenido", "poolDestinoId");
    }

    @Test
    void laClaveYElComportamientoSinCasoSonOpcionales() {
        CrearMessageThrowDto dto = throwCompleto();
        dto.setClaveCorrelacion(null);
        dto.setComportamientoSinCaso(null);

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void losLimitesDeLongitudSeValidan() {
        CrearMessageThrowDto dto = throwCompleto();
        dto.setNombreMensaje("n".repeat(151));
        dto.setContenido("c".repeat(2001));
        dto.setClaveCorrelacion("k".repeat(101));

        assertThat(camposConError(dto)).containsExactlyInAnyOrder("nombreMensaje", "contenido", "claveCorrelacion");
    }

    @Test
    void unNombreEnBlancoNoEsValido() {
        CrearMessageThrowDto dto = throwCompleto();
        dto.setNombreMensaje("   ");

        assertThat(camposConError(dto)).containsExactly("nombreMensaje");
    }

    @Test
    void elMessageCatchExigeNombreVarianteDatosActividadesYPosicion() {
        assertThat(camposConError(new CrearMessageCatchDto())).containsExactlyInAnyOrder("nombreMensaje",
                "variante", "datosEsperados", "actividadesUso", "posicionX", "posicionY");
        assertThat(camposConError(new EditarMessageCatchDto())).containsExactlyInAnyOrder("nombreMensaje",
                "variante", "datosEsperados", "actividadesUso");
    }

    @Test
    void elPoolDelCatchEsOpcionalComoEnLosDemasNodos() {
        CrearMessageCatchDto dto = catchCompleto();
        dto.setPoolId(null);

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void elOrigenExternoSinValorEquivaleAFalso() {
        CrearMessageCatchDto creacion = catchCompleto();
        creacion.setOrigenExterno(null);
        EditarMessageCatchDto edicion = new EditarMessageCatchDto();
        edicion.setOrigenExterno(true);

        assertThat(creacion.esOrigenExterno()).isFalse();
        assertThat(edicion.esOrigenExterno()).isTrue();
        assertThat(new EditarMessageCatchDto().esOrigenExterno()).isFalse();
    }

    @Test
    void elEnvioExternoExigeSusDatosDocumentales() {
        assertThat(camposConError(new CrearEnvioExternoDto())).containsExactlyInAnyOrder("nombreMensaje",
                "poolOrigenId", "poolDestinoId", "tipoDestino", "datosEnviados", "momentoProceso",
                "comportamientoFallo", "posicionX", "posicionY");
        assertThat(camposConError(new EditarEnvioExternoDto())).containsExactlyInAnyOrder("nombreMensaje",
                "poolDestinoId", "tipoDestino", "datosEnviados", "momentoProceso", "comportamientoFallo");
    }

    @Test
    void elMomentoDelProcesoTieneUnLimiteDeLongitud() {
        CrearEnvioExternoDto dto = envioCompleto();
        dto.setMomentoProceso("m".repeat(301));
        dto.setDatosEnviados("d".repeat(2001));

        assertThat(camposConError(dto)).containsExactlyInAnyOrder("momentoProceso", "datosEnviados");
    }
}
