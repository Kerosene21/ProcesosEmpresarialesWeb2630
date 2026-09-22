package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
import co.edu.javeriana.procesosempresariales.domain.NodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.exception.NodoFlujoNoValidoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.GatewayRepository;

@ExtendWith(MockitoExtension.class)
class NodoFlujoResolverTest {

    private static final Long PROCESO_ID = 5L;
    private static final Long POOL_ID = 80L;
    private static final Long ACTIVIDAD_ID = 30L;
    private static final Long GATEWAY_ID = 12L;

    @Mock
    private ActividadRepository actividadRepository;

    @Mock
    private GatewayRepository gatewayRepository;

    private NodoFlujoResolver nodoFlujoResolver;

    @BeforeEach
    void inicializar() {
        nodoFlujoResolver = new NodoFlujoResolver(actividadRepository, gatewayRepository);
    }

    private Proceso proceso() {
        return new Proceso(PROCESO_ID, "Ventas", "Proceso comercial", "Comercial", EstadoProceso.BORRADOR,
                new Empresa(7L, "Alpes Logistica", "900123456-7", "contacto@alpes.com"),
                new Pool(POOL_ID, "Alpes Logistica", List.of()), false);
    }

    private Actividad actividad(boolean activo) {
        return new Actividad(ACTIVIDAD_ID, "Revisar solicitud", TipoActividad.TAREA_USUARIO, proceso(),
                new Lane(11L, "General", new Pool(POOL_ID, "Alpes Logistica", List.of())), 100, 50, activo);
    }

    private Gateway gateway(boolean activo) {
        return new Gateway(GATEWAY_ID, TipoGateway.EXCLUSIVO, proceso(), 300, 120, activo);
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
    void unEventoTodaviaNoSePuedeResolver() {
        assertThatThrownBy(() -> nodoFlujoResolver.resolverActivo(proceso(), TipoNodoFlujo.EVENTO, 1L))
                .isInstanceOf(NodoFlujoNoValidoException.class)
                .hasMessage(NodoFlujoResolver.EVENTO_SIN_MODELO);
    }

    @Test
    void buscarUnEventoDevuelveVacioSinConsultarRepositorios() {
        assertThat(nodoFlujoResolver.buscar(proceso(), TipoNodoFlujo.EVENTO, 1L)).isEmpty();
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
        assertThat(NodoFlujoResolver.clave(TipoNodoFlujo.GATEWAY, GATEWAY_ID)).isEqualTo("GATEWAY:12");
        assertThat(NodoFlujoResolver.referencia(TipoNodoFlujo.GATEWAY, GATEWAY_ID)).isEqualTo("GATEWAY #12");
    }
}
