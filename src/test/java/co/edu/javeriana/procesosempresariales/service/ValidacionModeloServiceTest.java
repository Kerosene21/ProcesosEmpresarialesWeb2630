package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.Gateway;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.exception.ModeloDeProcesoNoValidoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.ArcoRepository;
import co.edu.javeriana.procesosempresariales.repository.GatewayRepository;

@ExtendWith(MockitoExtension.class)
class ValidacionModeloServiceTest {

    private static final Long PROCESO_ID = 5L;
    private static final Long POOL_ID = 80L;
    private static final Long GATEWAY_ID = 12L;
    private static final Long ACTIVIDAD_UNO = 30L;
    private static final Long ACTIVIDAD_DOS = 31L;
    private static final String ETIQUETA_EXCLUSIVO = "Gateway EXCLUSIVO #12";
    private static final String ETIQUETA_INCLUSIVO = "Gateway INCLUSIVO #12";
    private static final String ETIQUETA_PARALELO = "Gateway PARALELO #12";

    @Mock
    private GatewayRepository gatewayRepository;

    @Mock
    private ArcoRepository arcoRepository;

    @Mock
    private ActividadRepository actividadRepository;

    @Mock
    private AccesoProcesoService accesoProcesoService;

    @Mock
    private HistorialProcesoService historialProcesoService;

    private ValidacionModeloService validacionModeloService;

    @BeforeEach
    void inicializar() {
        NodoFlujoResolver resolver = new NodoFlujoResolver(actividadRepository, gatewayRepository);
        ConexionesService conexiones = new ConexionesService(arcoRepository, resolver);
        GatewayService gatewayService = new GatewayService(gatewayRepository, accesoProcesoService,
                historialProcesoService, conexiones, resolver);
        validacionModeloService = new ValidacionModeloService(gatewayService, conexiones);
    }

    private Proceso proceso() {
        return new Proceso(PROCESO_ID, "Ventas", "Proceso comercial", "Comercial", EstadoProceso.BORRADOR,
                new Empresa(7L, "Alpes Logistica", "900123456-7", "contacto@alpes.com"),
                new Pool(POOL_ID, "Alpes Logistica", List.of()), false);
    }

    private void existeElGateway(TipoGateway tipo) {
        when(gatewayRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(new Gateway(GATEWAY_ID, tipo, proceso(), 300, 120, true)));
    }

    private void sinGateways() {
        when(gatewayRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID)).thenReturn(List.of());
    }

    private Arco saliente(Long id, Long destinoId, String condicion) {
        return new Arco(id, proceso(), TipoNodoFlujo.GATEWAY, GATEWAY_ID, TipoNodoFlujo.ACTIVIDAD, destinoId, null,
                condicion, true);
    }

    private void conSalidas(Arco... salidas) {
        when(arcoRepository.findByProcesoIdAndOrigenTipoAndOrigenIdAndActivoTrueOrderByIdAsc(PROCESO_ID,
                TipoNodoFlujo.GATEWAY, GATEWAY_ID)).thenReturn(List.of(salidas));
    }

    @Test
    void unProcesoSinGatewaysPuedeSalirDeBorrador() {
        sinGateways();

        assertThatCode(() -> validacionModeloService.validarParaSalirDeBorrador(proceso()))
                .doesNotThrowAnyException();
    }

    @Test
    void unGatewaySinSalidasNoImpideSalirDeBorrador() {
        existeElGateway(TipoGateway.EXCLUSIVO);
        conSalidas();

        assertThat(validacionModeloService.problemasDelModelo(proceso())).isEmpty();
    }

    @Test
    void unGatewayConUnaSolaSalidaImpideSalirDeBorrador() {
        existeElGateway(TipoGateway.EXCLUSIVO);
        conSalidas(saliente(61L, ACTIVIDAD_UNO, "monto alto"));

        assertThatThrownBy(() -> validacionModeloService.validarParaSalirDeBorrador(proceso()))
                .isInstanceOf(ModeloDeProcesoNoValidoException.class)
                .hasMessage(ValidacionModeloService.MODELO_INCOMPLETO + ETIQUETA_EXCLUSIVO
                        + ValidacionModeloService.DIVERGENCIA_INCOMPLETA);
    }

    @Test
    void unGatewayExclusivoConDosSalidasCondicionadasPermiteSalirDeBorrador() {
        existeElGateway(TipoGateway.EXCLUSIVO);
        conSalidas(saliente(61L, ACTIVIDAD_UNO, "monto alto"), saliente(62L, ACTIVIDAD_DOS, "monto bajo"));

        assertThatCode(() -> validacionModeloService.validarParaSalirDeBorrador(proceso()))
                .doesNotThrowAnyException();
    }

    @Test
    void unGatewayExclusivoConUnaSalidaSinCondicionImpideSalirDeBorrador() {
        existeElGateway(TipoGateway.EXCLUSIVO);
        conSalidas(saliente(61L, ACTIVIDAD_UNO, "monto alto"), saliente(62L, ACTIVIDAD_DOS, null));

        assertThatThrownBy(() -> validacionModeloService.validarParaSalirDeBorrador(proceso()))
                .isInstanceOf(ModeloDeProcesoNoValidoException.class)
                .hasMessage(ValidacionModeloService.MODELO_INCOMPLETO + ETIQUETA_EXCLUSIVO
                        + ValidacionModeloService.SALIDA_SIN_CONDICION);
    }

    @Test
    void unGatewayInclusivoConUnaSalidaSinCondicionImpideSalirDeBorrador() {
        existeElGateway(TipoGateway.INCLUSIVO);
        conSalidas(saliente(61L, ACTIVIDAD_UNO, "requiere visto bueno"), saliente(62L, ACTIVIDAD_DOS, "   "));

        assertThatThrownBy(() -> validacionModeloService.validarParaSalirDeBorrador(proceso()))
                .isInstanceOf(ModeloDeProcesoNoValidoException.class)
                .hasMessage(ValidacionModeloService.MODELO_INCOMPLETO + ETIQUETA_INCLUSIVO
                        + ValidacionModeloService.SALIDA_SIN_CONDICION);
    }

    @Test
    void unGatewayInclusivoConDosSalidasCondicionadasPermiteSalirDeBorrador() {
        existeElGateway(TipoGateway.INCLUSIVO);
        conSalidas(saliente(61L, ACTIVIDAD_UNO, "requiere visto bueno"),
                saliente(62L, ACTIVIDAD_DOS, "requiere auditoria"));

        assertThat(validacionModeloService.problemasDelModelo(proceso())).isEmpty();
    }

    @Test
    void unGatewayParaleloConCondicionesImpideSalirDeBorrador() {
        existeElGateway(TipoGateway.PARALELO);
        conSalidas(saliente(61L, ACTIVIDAD_UNO, null), saliente(62L, ACTIVIDAD_DOS, "monto alto"));

        assertThatThrownBy(() -> validacionModeloService.validarParaSalirDeBorrador(proceso()))
                .isInstanceOf(ModeloDeProcesoNoValidoException.class)
                .hasMessage(ValidacionModeloService.MODELO_INCOMPLETO + ETIQUETA_PARALELO
                        + ValidacionModeloService.SALIDA_CON_CONDICION);
    }

    @Test
    void unGatewayParaleloSinCondicionesPermiteSalirDeBorrador() {
        existeElGateway(TipoGateway.PARALELO);
        conSalidas(saliente(61L, ACTIVIDAD_UNO, null), saliente(62L, ACTIVIDAD_DOS, null));

        assertThat(validacionModeloService.problemasDelModelo(proceso())).isEmpty();
    }

    @Test
    void unGatewayPuedeAcumularVariosProblemasALaVez() {
        existeElGateway(TipoGateway.EXCLUSIVO);
        conSalidas(saliente(61L, ACTIVIDAD_UNO, null));

        assertThat(validacionModeloService.problemasDelModelo(proceso())).containsExactly(
                ETIQUETA_EXCLUSIVO + ValidacionModeloService.DIVERGENCIA_INCOMPLETA,
                ETIQUETA_EXCLUSIVO + ValidacionModeloService.SALIDA_SIN_CONDICION);
    }
}
