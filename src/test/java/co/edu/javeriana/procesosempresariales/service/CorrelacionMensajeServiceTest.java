package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoSinCaso;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.MessageCatch;
import co.edu.javeriana.procesosempresariales.domain.MessageThrow;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
import co.edu.javeriana.procesosempresariales.repository.MessageCatchRepository;
import co.edu.javeriana.procesosempresariales.repository.MessageThrowRepository;

@ExtendWith(MockitoExtension.class)
class CorrelacionMensajeServiceTest {

    private static final Long PROCESO_ID = 5L;
    private static final String PAGO = "Pago confirmado";

    @Mock
    private MessageThrowRepository messageThrowRepository;

    @Mock
    private MessageCatchRepository messageCatchRepository;

    private CorrelacionMensajeService correlacionMensajeService;

    private final Pool alpes = new Pool(80L, null, "Alpes", TipoPool.PROPIETARIO, 1, false, true, null,
            new ArrayList<>());
    private final Pool banco = new Pool(90L, null, "Banco", TipoPool.PARTICIPANTE, 2, false, true, null,
            new ArrayList<>());
    private final Proceso proceso = new Proceso(PROCESO_ID, "Compras", "Proceso de compras", "Operaciones",
            EstadoProceso.BORRADOR, new Empresa(7L, "Alpes", "900123456-7", "contacto@alpes.com"),
            List.of(alpes, banco), false);

    @BeforeEach
    void inicializar() {
        correlacionMensajeService = new CorrelacionMensajeService(messageThrowRepository, messageCatchRepository);
    }

    private MessageThrow messageThrow(Long id, String nombre, String clave) {
        MessageThrow messageThrow = new MessageThrow();
        messageThrow.setId(id);
        messageThrow.setProceso(proceso);
        messageThrow.setPool(alpes);
        messageThrow.setPoolDestino(banco);
        messageThrow.setNombreMensaje(nombre);
        messageThrow.setClaveCorrelacion(clave);
        messageThrow.setActivo(true);
        return messageThrow;
    }

    private MessageCatch messageCatch(Long id, Pool pool, String nombre, String clave,
            VarianteMessageCatch variante) {
        MessageCatch messageCatch = new MessageCatch();
        messageCatch.setId(id);
        messageCatch.setProceso(proceso);
        messageCatch.setPool(pool);
        messageCatch.setNombreMensaje(nombre);
        messageCatch.setClaveCorrelacion(clave);
        messageCatch.setVariante(variante);
        messageCatch.setComportamientoSinCaso(ComportamientoSinCaso.DESCARTAR);
        messageCatch.setOrigenExterno(false);
        messageCatch.setActivo(true);
        return messageCatch;
    }

    private MessageCatch catchIntermedioEnElBanco(Long id, String nombre, String clave) {
        return messageCatch(id, banco, nombre, clave, VarianteMessageCatch.INTERMEDIO);
    }

    private void losThrowsSon(MessageThrow... throwsDelProceso) {
        when(messageThrowRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(throwsDelProceso));
    }

    private void losCatchesSon(MessageCatch... catchesDelProceso) {
        when(messageCatchRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(catchesDelProceso));
    }

    @Test
    void unThrowConCatchHomologoYMismaClaveQuedaCorrelacionado() {
        MessageThrow messageThrow = messageThrow(1L, PAGO, "factura");
        losThrowsSon(messageThrow);
        losCatchesSon(catchIntermedioEnElBanco(10L, PAGO, "factura"));

        AnalisisCorrelacion analisis = correlacionMensajeService.analizarThrow(messageThrow);

        assertThat(analisis.homologoId()).isEqualTo(10L);
        assertThat(analisis.advertencias()).isEmpty();
    }

    @Test
    void laCorrelacionIgnoraEspaciosYMayusculasEnNombreYClave() {
        MessageThrow messageThrow = messageThrow(1L, "  pago   CONFIRMADO ", " FACTURA ");
        losThrowsSon(messageThrow);
        losCatchesSon(catchIntermedioEnElBanco(10L, PAGO, "factura"));

        assertThat(correlacionMensajeService.analizarThrow(messageThrow).homologoId()).isEqualTo(10L);
    }

    @Test
    void unThrowSinCatchEnElPoolDestinoGeneraAdvertencia() {
        MessageThrow messageThrow = messageThrow(1L, PAGO, "factura");
        losThrowsSon(messageThrow);
        losCatchesSon(messageCatch(10L, alpes, PAGO, "factura", VarianteMessageCatch.INTERMEDIO));

        AnalisisCorrelacion analisis = correlacionMensajeService.analizarThrow(messageThrow);

        assertThat(analisis.homologoId()).isNull();
        assertThat(analisis.advertencias()).containsExactly("No existe un Message Catch '" + PAGO
                + "' en el pool destino 'Banco': el mensaje no tiene un receptor modelado.");
    }

    @Test
    void clavesDistintasEntreHomologosGeneranAdvertencia() {
        MessageThrow messageThrow = messageThrow(1L, PAGO, "factura");
        losThrowsSon(messageThrow);
        losCatchesSon(catchIntermedioEnElBanco(10L, PAGO, "radicado"));

        AnalisisCorrelacion analisis = correlacionMensajeService.analizarThrow(messageThrow);

        assertThat(analisis.homologoId()).isNull();
        assertThat(analisis.advertencias()).containsExactly("Clave de correlación incoherente entre el Message"
                + " Throw '" + PAGO + "' y el Message Catch '" + PAGO + "' del pool 'Banco': el Throw usa 'factura'"
                + " y el Catch usa 'radicado'.");
    }

    @Test
    void unaClaveAusenteEnUnoDeLosHomologosEsIncoherente() {
        MessageThrow messageThrow = messageThrow(1L, PAGO, null);
        losThrowsSon(messageThrow);
        losCatchesSon(catchIntermedioEnElBanco(10L, PAGO, "factura"));

        assertThat(correlacionMensajeService.analizarThrow(messageThrow).advertencias())
                .singleElement().asString().contains("el Throw usa ninguna y el Catch usa 'factura'");
    }

    @Test
    void entreVariosHomologosSeEligeElDeClaveCoherente() {
        MessageThrow messageThrow = messageThrow(1L, PAGO, "factura");
        losThrowsSon(messageThrow);
        losCatchesSon(catchIntermedioEnElBanco(10L, PAGO, "radicado"), catchIntermedioEnElBanco(11L, PAGO, "factura"));

        AnalisisCorrelacion analisis = correlacionMensajeService.analizarThrow(messageThrow);

        assertThat(analisis.homologoId()).isEqualTo(11L);
        assertThat(analisis.advertencias()).isEmpty();
    }

    @Test
    void comportamientosSinCasoDistintosEntreHomologosGeneranAdvertencia() {
        MessageThrow messageThrow = messageThrow(1L, PAGO, "factura");
        messageThrow.setComportamientoSinCaso(ComportamientoSinCaso.INICIAR_NUEVO_CASO);
        losThrowsSon(messageThrow);
        losCatchesSon(catchIntermedioEnElBanco(10L, PAGO, "factura"));

        assertThat(correlacionMensajeService.analizarThrow(messageThrow).advertencias()).containsExactly(
                "El Message Throw '" + PAGO + "' documenta INICIAR_NUEVO_CASO para mensajes sin caso en espera y su"
                        + " Message Catch homólogo documenta DESCARTAR.");
    }

    @Test
    void comportamientosSinCasoIgualesEntreHomologosNoGeneranAdvertencia() {
        MessageThrow messageThrow = messageThrow(1L, PAGO, "factura");
        messageThrow.setComportamientoSinCaso(ComportamientoSinCaso.DESCARTAR);
        losThrowsSon(messageThrow);
        losCatchesSon(catchIntermedioEnElBanco(10L, PAGO, "factura"));

        assertThat(correlacionMensajeService.analizarThrow(messageThrow).advertencias()).isEmpty();
    }

    @Test
    void dosThrowsConMismoNombreYClaveSonAmbiguos() {
        MessageThrow messageThrow = messageThrow(1L, PAGO, "factura");
        losThrowsSon(messageThrow, messageThrow(2L, "PAGO confirmado", " Factura"), messageThrow(3L, PAGO, "nit"));
        losCatchesSon(catchIntermedioEnElBanco(10L, PAGO, "factura"));

        assertThat(correlacionMensajeService.analizarThrow(messageThrow).advertencias()).containsExactly(
                "Ambigüedad de correlación: otro Message Throw del proceso comparte el nombre '" + PAGO
                        + "' y la clave de correlación 'factura'.");
    }

    @Test
    void variosCatchesSinClaveConElMismoNombreSonAmbiguos() {
        MessageCatch messageCatch = messageCatch(10L, alpes, PAGO, null, VarianteMessageCatch.INICIO);
        messageCatch.setOrigenExterno(true);
        losThrowsSon();
        losCatchesSon(messageCatch, messageCatch(11L, banco, PAGO, null, VarianteMessageCatch.INICIO),
                messageCatch(12L, alpes, PAGO, " ", VarianteMessageCatch.INICIO));

        assertThat(correlacionMensajeService.analizarCatch(messageCatch).advertencias()).containsExactly(
                "Ambigüedad de correlación: otros 2 Message Catch del proceso comparten el nombre '" + PAGO
                        + "' sin clave de correlación.");
    }

    @Test
    void unCatchConThrowHomologoCoherenteQuedaCorrelacionado() {
        MessageCatch messageCatch = catchIntermedioEnElBanco(10L, PAGO, "factura");
        losThrowsSon(messageThrow(1L, PAGO, "factura"));
        losCatchesSon(messageCatch);

        AnalisisCorrelacion analisis = correlacionMensajeService.analizarCatch(messageCatch);

        assertThat(analisis.homologoId()).isEqualTo(1L);
        assertThat(analisis.advertencias()).isEmpty();
    }

    @Test
    void unCatchSinThrowHomologoNiOrigenExternoGeneraAdvertencia() {
        MessageCatch messageCatch = catchIntermedioEnElBanco(10L, PAGO, "factura");
        losThrowsSon(messageThrow(1L, "Pago rechazado", "factura"));
        losCatchesSon(messageCatch);

        assertThat(correlacionMensajeService.analizarCatch(messageCatch).advertencias()).containsExactly(
                "No existe un Message Throw '" + PAGO + "' dirigido al pool 'Banco': indica el Throw homólogo o"
                        + " declara el origen como externo.");
    }

    @Test
    void unCatchConClaveIncoherenteGeneraAdvertencia() {
        MessageCatch messageCatch = catchIntermedioEnElBanco(10L, PAGO, "radicado");
        losThrowsSon(messageThrow(1L, PAGO, "factura"));
        losCatchesSon(messageCatch);

        assertThat(correlacionMensajeService.analizarCatch(messageCatch).advertencias())
                .singleElement().asString().startsWith("Clave de correlación incoherente");
    }

    @Test
    void unCatchDeOrigenExternoNoExigeUnThrowInterno() {
        MessageCatch messageCatch = catchIntermedioEnElBanco(10L, PAGO, "factura");
        messageCatch.setOrigenExterno(true);
        losThrowsSon();
        losCatchesSon(messageCatch);

        AnalisisCorrelacion analisis = correlacionMensajeService.analizarCatch(messageCatch);

        assertThat(analisis.homologoId()).isNull();
        assertThat(analisis.advertencias()).isEmpty();
    }

    @Test
    void unCatchIntermedioSinClaveGeneraAdvertencia() {
        MessageCatch messageCatch = catchIntermedioEnElBanco(10L, PAGO, null);
        messageCatch.setOrigenExterno(true);
        losThrowsSon();
        losCatchesSon(messageCatch);

        assertThat(correlacionMensajeService.analizarCatch(messageCatch).advertencias()).containsExactly(
                "El Message Catch intermedio '" + PAGO + "' no declara clave de correlación: no se puede identificar"
                        + " el caso en espera que recibe el mensaje.");
    }

    @Test
    void unCatchIntermedioSinComportamientoSinCasoGeneraAdvertencia() {
        MessageCatch messageCatch = catchIntermedioEnElBanco(10L, PAGO, "factura");
        messageCatch.setOrigenExterno(true);
        messageCatch.setComportamientoSinCaso(null);
        losThrowsSon();
        losCatchesSon(messageCatch);

        assertThat(correlacionMensajeService.analizarCatch(messageCatch).advertencias()).containsExactly(
                "El Message Catch intermedio '" + PAGO + "' no documenta qué ocurre si llega un mensaje sin caso en"
                        + " espera.");
    }

    @Test
    void unCatchDeInicioSinClaveNoGeneraAdvertenciaDeClave() {
        MessageCatch messageCatch = messageCatch(10L, banco, PAGO, null, VarianteMessageCatch.INICIO);
        messageCatch.setComportamientoSinCaso(ComportamientoSinCaso.INICIAR_NUEVO_CASO);
        losThrowsSon(messageThrow(1L, PAGO, null));
        losCatchesSon(messageCatch);

        AnalisisCorrelacion analisis = correlacionMensajeService.analizarCatch(messageCatch);

        assertThat(analisis.homologoId()).isEqualTo(1L);
        assertThat(analisis.advertencias()).isEmpty();
    }

    @Test
    void elCatchAdviertePorComportamientoIncoherenteConSuThrow() {
        MessageCatch messageCatch = catchIntermedioEnElBanco(10L, PAGO, "factura");
        MessageThrow messageThrow = messageThrow(1L, PAGO, "factura");
        messageThrow.setComportamientoSinCaso(ComportamientoSinCaso.INICIAR_NUEVO_CASO);
        losThrowsSon(messageThrow);
        losCatchesSon(messageCatch);

        assertThat(correlacionMensajeService.analizarCatch(messageCatch).advertencias())
                .singleElement().asString().contains("documenta INICIAR_NUEVO_CASO");
    }

    @Test
    void eliminarElUnicoThrowDejaAlCatchSinHomologo() {
        MessageThrow messageThrow = messageThrow(1L, PAGO, "factura");
        losThrowsSon(messageThrow);
        losCatchesSon(catchIntermedioEnElBanco(10L, PAGO, "factura"));

        assertThat(correlacionMensajeService.advertenciasSiSeEliminaThrow(messageThrow)).containsExactly(
                "El Message Catch '" + PAGO + "' del pool 'Banco' quedará sin Message Throw homólogo.");
    }

    @Test
    void eliminarUnThrowNoAdviertePorCatchesQueConservanOtroHomologoOSonExternos() {
        MessageThrow messageThrow = messageThrow(1L, PAGO, "factura");
        MessageCatch externo = catchIntermedioEnElBanco(11L, "Pago rechazado", "factura");
        externo.setOrigenExterno(true);
        losThrowsSon(messageThrow, messageThrow(2L, PAGO, "factura"), messageThrow(3L, "Pago rechazado", null));
        losCatchesSon(catchIntermedioEnElBanco(10L, PAGO, "factura"), externo);

        assertThat(correlacionMensajeService.advertenciasSiSeEliminaThrow(messageThrow)).isEmpty();
    }

    @Test
    void eliminarElUnicoCatchDejaAlThrowSinHomologo() {
        MessageCatch messageCatch = catchIntermedioEnElBanco(10L, PAGO, "factura");
        losThrowsSon(messageThrow(1L, PAGO, "factura"));
        losCatchesSon(messageCatch);

        assertThat(correlacionMensajeService.advertenciasSiSeEliminaCatch(messageCatch)).containsExactly(
                "El Message Throw '" + PAGO + "' quedará sin Message Catch homólogo en el pool 'Banco'.");
    }

    @Test
    void eliminarUnCatchNoAdviertePorThrowsQueConservanOtroCatch() {
        MessageCatch messageCatch = catchIntermedioEnElBanco(10L, PAGO, "factura");
        losThrowsSon(messageThrow(1L, PAGO, "factura"));
        losCatchesSon(messageCatch, catchIntermedioEnElBanco(11L, PAGO, "factura"));

        assertThat(correlacionMensajeService.advertenciasSiSeEliminaCatch(messageCatch)).isEmpty();
    }

    @Test
    void laCorrelacionSoloConsultaElModeloYNoEjecutaNada() {
        MessageThrow messageThrow = messageThrow(1L, PAGO, "factura");
        losThrowsSon(messageThrow);
        losCatchesSon(catchIntermedioEnElBanco(10L, PAGO, "factura"));

        correlacionMensajeService.analizarThrow(messageThrow);

        verify(messageThrowRepository, never()).save(any(MessageThrow.class));
        verify(messageCatchRepository, never()).save(any(MessageCatch.class));
        assertThat(messageThrow.isActivo()).isTrue();
    }
}
