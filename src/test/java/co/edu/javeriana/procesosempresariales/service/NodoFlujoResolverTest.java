package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.javeriana.procesosempresariales.domain.Actividad;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.Gateway;
import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.MessageCatch;
import co.edu.javeriana.procesosempresariales.domain.MessageThrow;
import co.edu.javeriana.procesosempresariales.domain.NodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.domain.TipoEvento;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
import co.edu.javeriana.procesosempresariales.exception.NodoFlujoNoValidoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.EventoRepository;
import co.edu.javeriana.procesosempresariales.repository.GatewayRepository;

@ExtendWith(MockitoExtension.class)
class NodoFlujoResolverTest {

    private static final Long PROCESO_ID = 5L;
    private static final Long POOL_ID = 80L;
    private static final Long ACTIVIDAD_ID = 30L;
    private static final Long GATEWAY_ID = 12L;
    private static final Long EVENTO_ID = 40L;

    @Mock
    private ActividadRepository actividadRepository;

    @Mock
    private GatewayRepository gatewayRepository;

    @Mock
    private EventoRepository eventoRepository;

    private NodoFlujoResolver nodoFlujoResolver;

    @BeforeEach
    void inicializar() {
        nodoFlujoResolver = new NodoFlujoResolver(actividadRepository, gatewayRepository, eventoRepository);
    }

    private Pool poolPropietario() {
        return new Pool(POOL_ID, null, "Alpes Logistica", TipoPool.PROPIETARIO, 1, false, true, null,
                new ArrayList<>());
    }

    private Proceso proceso() {
        return new Proceso(PROCESO_ID, "Ventas", "Proceso comercial", "Comercial", EstadoProceso.BORRADOR,
                new Empresa(7L, "Alpes Logistica", "900123456-7", "contacto@alpes.com"),
                List.of(poolPropietario()), false);
    }

    private Actividad actividad(boolean activo) {
        return new Actividad(ACTIVIDAD_ID, "Revisar solicitud", TipoActividad.TAREA_USUARIO, proceso(),
                new Lane(11L, "General", poolPropietario(), null, 1, true), 100, 50, activo);
    }

    private Gateway gateway(boolean activo) {
        return new Gateway(GATEWAY_ID, TipoGateway.EXCLUSIVO, proceso(), poolPropietario(), 300, 120, activo);
    }

    private MessageThrow messageThrow(boolean activo) {
        MessageThrow messageThrow = new MessageThrow();
        messageThrow.setId(EVENTO_ID);
        messageThrow.setProceso(proceso());
        messageThrow.setPool(poolPropietario());
        messageThrow.setNombreMensaje("Solicitud de pago");
        messageThrow.setPosicionX(500);
        messageThrow.setPosicionY(60);
        messageThrow.setActivo(activo);
        return messageThrow;
    }

    private MessageCatch messageCatch(VarianteMessageCatch variante) {
        MessageCatch messageCatch = new MessageCatch();
        messageCatch.setId(EVENTO_ID);
        messageCatch.setProceso(proceso());
        messageCatch.setPool(poolPropietario());
        messageCatch.setNombreMensaje("Pago recibido");
        messageCatch.setVariante(variante);
        messageCatch.setPosicionX(20);
        messageCatch.setPosicionY(60);
        messageCatch.setActivo(true);
        return messageCatch;
    }

    @Test
    void unaActividadSeConvierteEnNodoDeFlujo() {
        when(actividadRepository.findByIdAndProcesoId(ACTIVIDAD_ID, PROCESO_ID))
                .thenReturn(Optional.of(actividad(true)));

        NodoFlujo nodo = nodoFlujoResolver.resolverActivo(proceso(), TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID);

        assertThat(nodo.nombre()).isEqualTo("Revisar solicitud");
        assertThat(nodo.posicionX()).isEqualTo(100);
        assertThat(nodo.esGateway()).isFalse();
        assertThat(nodo.exigeCondicion()).isFalse();
    }

    @Test
    void unGatewaySeConvierteEnNodoDeFlujoConSuTipo() {
        when(gatewayRepository.findByIdAndProcesoId(GATEWAY_ID, PROCESO_ID)).thenReturn(Optional.of(gateway(true)));

        NodoFlujo nodo = nodoFlujoResolver.resolverActivo(proceso(), TipoNodoFlujo.GATEWAY, GATEWAY_ID);

        assertThat(nodo.nombre()).isEqualTo("Gateway EXCLUSIVO #12");
        assertThat(nodo.tipoGateway()).isEqualTo(TipoGateway.EXCLUSIVO);
        assertThat(nodo.esGateway()).isTrue();
        assertThat(nodo.exigeCondicion()).isTrue();
    }

    @Test
    void unMessageThrowSeConvierteEnNodoDeFlujo() {
        when(eventoRepository.findByIdAndProcesoId(EVENTO_ID, PROCESO_ID)).thenReturn(Optional.of(messageThrow(true)));

        NodoFlujo nodo = nodoFlujoResolver.resolverActivo(proceso(), TipoNodoFlujo.EVENTO, EVENTO_ID);

        assertThat(nodo.tipo()).isEqualTo(TipoNodoFlujo.EVENTO);
        assertThat(nodo.id()).isEqualTo(EVENTO_ID);
        assertThat(nodo.poolId()).isEqualTo(POOL_ID);
        assertThat(nodo.nombre()).isEqualTo("Message Throw: Solicitud de pago");
        assertThat(nodo.tipoEvento()).isEqualTo(TipoEvento.MESSAGE_THROW);
        assertThat(nodo.posicionX()).isEqualTo(500);
        assertThat(nodo.esGateway()).isFalse();
        assertThat(nodo.exigeCondicion()).isFalse();
        assertThat(nodo.admiteEntradas()).isTrue();
    }

    @Test
    void unMessageCatchDeInicioNoAdmiteEntradas() {
        when(eventoRepository.findByIdAndProcesoId(EVENTO_ID, PROCESO_ID))
                .thenReturn(Optional.of(messageCatch(VarianteMessageCatch.INICIO)));

        NodoFlujo nodo = nodoFlujoResolver.resolverActivo(proceso(), TipoNodoFlujo.EVENTO, EVENTO_ID);

        assertThat(nodo.tipoEvento()).isEqualTo(TipoEvento.MESSAGE_CATCH);
        assertThat(nodo.nombre()).isEqualTo("Message Catch: Pago recibido");
        assertThat(nodo.admiteEntradas()).isFalse();
    }

    @Test
    void unMessageCatchIntermedioAdmiteEntradas() {
        when(eventoRepository.findByIdAndProcesoId(EVENTO_ID, PROCESO_ID))
                .thenReturn(Optional.of(messageCatch(VarianteMessageCatch.INTERMEDIO)));

        assertThat(nodoFlujoResolver.resolverActivo(proceso(), TipoNodoFlujo.EVENTO, EVENTO_ID).admiteEntradas())
                .isTrue();
    }

    @Test
    void unEventoInexistenteODeOtroProcesoSeRechaza() {
        when(eventoRepository.findByIdAndProcesoId(EVENTO_ID, PROCESO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nodoFlujoResolver.resolverActivo(proceso(), TipoNodoFlujo.EVENTO, EVENTO_ID))
                .isInstanceOf(NodoFlujoNoValidoException.class)
                .hasMessage(NodoFlujoResolver.NODO_NO_EXISTE);
    }

    @Test
    void unEventoInactivoSeRechaza() {
        when(eventoRepository.findByIdAndProcesoId(EVENTO_ID, PROCESO_ID)).thenReturn(Optional.of(messageThrow(false)));

        assertThatThrownBy(() -> nodoFlujoResolver.resolverActivo(proceso(), TipoNodoFlujo.EVENTO, EVENTO_ID))
                .isInstanceOf(NodoFlujoNoValidoException.class)
                .hasMessage(NodoFlujoResolver.NODO_INACTIVO);
    }

    @Test
    void losNodosActivosIncluyenLosEventos() {
        when(actividadRepository.activasDelProceso(PROCESO_ID)).thenReturn(List.of(actividad(true)));
        when(gatewayRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(gateway(true)));
        when(eventoRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(messageThrow(true)));

        assertThat(nodoFlujoResolver.indiceDeNodosActivos(proceso()))
                .containsOnlyKeys("ACTIVIDAD:30", "GATEWAY:12", "EVENTO:40");
    }

    @Test
    void describirUnEventoUsaSuEtiqueta() {
        when(eventoRepository.findByIdAndProcesoId(EVENTO_ID, PROCESO_ID)).thenReturn(Optional.of(messageThrow(false)));

        assertThat(nodoFlujoResolver.describir(proceso(), TipoNodoFlujo.EVENTO, EVENTO_ID))
                .isEqualTo("Message Throw: Solicitud de pago");
    }

    @Test
    void buscarSinTipoOSinIdentificadorDevuelveVacio() {
        assertThat(nodoFlujoResolver.buscar(proceso(), null, ACTIVIDAD_ID)).isEmpty();
        assertThat(nodoFlujoResolver.buscar(proceso(), TipoNodoFlujo.ACTIVIDAD, null)).isEmpty();
    }

    @Test
    void resolverRechazaUnNodoInactivo() {
        when(gatewayRepository.findByIdAndProcesoId(GATEWAY_ID, PROCESO_ID)).thenReturn(Optional.of(gateway(false)));

        assertThatThrownBy(() -> nodoFlujoResolver.resolverActivo(proceso(), TipoNodoFlujo.GATEWAY, GATEWAY_ID))
                .isInstanceOf(NodoFlujoNoValidoException.class)
                .hasMessage(NodoFlujoResolver.NODO_INACTIVO);
    }

    @Test
    void losNodosActivosReunenActividadesYGateways() {
        when(actividadRepository.activasDelProceso(PROCESO_ID)).thenReturn(List.of(actividad(true)));
        when(gatewayRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(gateway(true)));

        List<NodoFlujo> nodos = nodoFlujoResolver.nodosActivos(proceso());

        assertThat(nodos).extracting(NodoFlujo::nombre)
                .containsExactly("Revisar solicitud", "Gateway EXCLUSIVO #12");
    }

    @Test
    void elIndiceDeNodosSeConstruyePorTipoEIdentificador() {
        when(actividadRepository.activasDelProceso(PROCESO_ID)).thenReturn(List.of(actividad(true)));
        when(gatewayRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(gateway(true)));

        assertThat(nodoFlujoResolver.indiceDeNodosActivos(proceso()))
                .containsOnlyKeys("ACTIVIDAD:30", "GATEWAY:12");
    }

    @Test
    void describirUsaElNombreCuandoElNodoExiste() {
        when(actividadRepository.findByIdAndProcesoId(ACTIVIDAD_ID, PROCESO_ID))
                .thenReturn(Optional.of(actividad(false)));

        assertThat(nodoFlujoResolver.describir(proceso(), TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID))
                .isEqualTo("Revisar solicitud");
    }

    @Test
    void describirCaeEnLaReferenciaCuandoElNodoNoExiste() {
        when(actividadRepository.findByIdAndProcesoId(ACTIVIDAD_ID, PROCESO_ID)).thenReturn(Optional.empty());

        assertThat(nodoFlujoResolver.describir(proceso(), TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID))
                .isEqualTo("ACTIVIDAD #30");
    }

    @Test
    void laClaveYLaReferenciaIdentificanAlNodo() {
        assertThat(nodoFlujoResolver.clave(TipoNodoFlujo.GATEWAY, GATEWAY_ID)).isEqualTo("GATEWAY:12");
        assertThat(nodoFlujoResolver.referencia(TipoNodoFlujo.GATEWAY, GATEWAY_ID)).isEqualTo("GATEWAY #12");
    }
    @Test
    void unaActividadConservaElPoolDeSuLane() {
        NodoFlujo nodo = nodoFlujoResolver.desdeActividad(actividad(true));

        assertThat(nodo.poolId()).isEqualTo(POOL_ID);
    }

    @Test
    void unGatewayConservaElPoolAlQuePertenece() {
        NodoFlujo nodo = nodoFlujoResolver.desdeGateway(gateway(true));

        assertThat(nodo.poolId()).isEqualTo(POOL_ID);
    }

    @Test
    void dosNodosDelMismoPoolPuedenConectarse() {
        NodoFlujo actividad = nodoFlujoResolver.desdeActividad(actividad(true));
        NodoFlujo gateway = nodoFlujoResolver.desdeGateway(gateway(true));

        assertThat(actividad.mismoPool(gateway)).isTrue();
        assertThat(gateway.mismoPool(actividad)).isTrue();
    }

    @Test
    void dosNodosDePoolsDistintosNoEstanEnElMismoPool() {
        NodoFlujo actividad = nodoFlujoResolver.desdeActividad(actividad(true));
        NodoFlujo externo = new NodoFlujo(TipoNodoFlujo.ACTIVIDAD, 31L, 90L, "Enviar orden", 10, 10, true, null);

        assertThat(actividad.mismoPool(externo)).isFalse();
        assertThat(externo.mismoPool(actividad)).isFalse();
    }
}
