package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.Gateway;
import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.NodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.ArcoRepository;
import co.edu.javeriana.procesosempresariales.repository.GatewayRepository;

@ExtendWith(MockitoExtension.class)
class ConexionesServiceTest {

    private static final Long PROCESO_ID = 5L;
    private static final Long POOL_ID = 80L;
    private static final Long LANE_ID = 11L;
    private static final Long ACTIVIDAD_ID = 30L;
    private static final Long GATEWAY_ID = 12L;

    @Mock
    private ArcoRepository arcoRepository;

    @Mock
    private ActividadRepository actividadRepository;

    @Mock
    private GatewayRepository gatewayRepository;

    private NodoFlujoResolver nodoFlujoResolver;

    private ConexionesService conexionesService;

    @BeforeEach
    void inicializar() {
        nodoFlujoResolver = new NodoFlujoResolver(actividadRepository, gatewayRepository);
        conexionesService = new ConexionesService(arcoRepository, nodoFlujoResolver);
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

    private Arco arco(Long id, Long origenId, Long destinoId, boolean activo) {
        return new Arco(id, proceso(), TipoNodoFlujo.ACTIVIDAD, origenId, TipoNodoFlujo.ACTIVIDAD, destinoId, null,
                null, activo);
    }

    private Arco salienteDelGateway(Long id, String condicion) {
        return new Arco(id, proceso(), TipoNodoFlujo.GATEWAY, GATEWAY_ID, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID,
                null, condicion, true);
    }

    private NodoFlujo gateway(TipoGateway tipo) {
        return nodoFlujoResolver.desdeGateway(new Gateway(GATEWAY_ID, tipo, proceso(), poolPropietario(), 300, 120,
                true));
    }

    private void existeLaActividad(Long id, String nombre) {
        when(actividadRepository.findByIdAndProcesoId(id, PROCESO_ID)).thenReturn(Optional.of(
                new Actividad(id, nombre, TipoActividad.TAREA_USUARIO, proceso(),
                        new Lane(LANE_ID, "General", poolPropietario(), null, 1, true), 100, 50,
                        true)));
    }

    private void devolverSalientes(Long nodoId, List<Arco> arcos) {
        when(arcoRepository.findByProcesoIdAndOrigenTipoAndOrigenIdAndActivoTrueOrderByIdAsc(PROCESO_ID,
                TipoNodoFlujo.ACTIVIDAD, nodoId)).thenReturn(arcos);
    }

    private void devolverEntrantes(Long nodoId, List<Arco> arcos) {
        when(arcoRepository.findByProcesoIdAndDestinoTipoAndDestinoIdAndActivoTrueOrderByIdAsc(PROCESO_ID,
                TipoNodoFlujo.ACTIVIDAD, nodoId)).thenReturn(arcos);
    }

    @Test
    void desactivarConectadosMarcaComoInactivosLosArcosDelNodo() {
        List<Arco> conectados = new ArrayList<>(List.of(arco(60L, ACTIVIDAD_ID, 31L, true),
                arco(61L, 32L, ACTIVIDAD_ID, true)));
        when(arcoRepository.conectadosAlNodo(PROCESO_ID, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID))
                .thenReturn(conectados);

        List<Arco> desactivados = conexionesService.desactivarConectadosA(proceso(), TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_ID);

        assertThat(desactivados).hasSize(2).allMatch(arco -> !arco.isActivo());
        verify(arcoRepository).saveAll(anyList());
    }

    @Test
    void desactivarConectadosNoGuardaNadaCuandoElNodoNoTieneArcos() {
        when(arcoRepository.conectadosAlNodo(PROCESO_ID, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID))
                .thenReturn(List.of());

        assertThat(conexionesService.desactivarConectadosA(proceso(), TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID))
                .isEmpty();

        verify(arcoRepository, never()).saveAll(anyList());
    }

    @Test
    void elNodoQueSeEliminaNoGeneraAdvertenciaSobreSiMismo() {
        existeLaActividad(31L, "Aprobar solicitud");
        devolverEntrantes(31L, List.of());

        List<String> advertencias = conexionesService.advertenciasTrasDesactivar(proceso(),
                List.of(arco(60L, ACTIVIDAD_ID, 31L, false)), TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID);

        assertThat(advertencias).containsExactly("'Aprobar solicitud' quedó sin arcos de entrada");
    }

    @Test
    void unNodoConOtrasConexionesNoGeneraAdvertencia() {
        devolverSalientes(ACTIVIDAD_ID, List.of(arco(61L, ACTIVIDAD_ID, 32L, true)));
        devolverEntrantes(31L, List.of(arco(62L, 33L, 31L, true)));

        List<String> advertencias = conexionesService.advertenciasTrasDesactivar(proceso(),
                List.of(arco(60L, ACTIVIDAD_ID, 31L, false)), null, null);

        assertThat(advertencias).isEmpty();
    }

    @Test
    void unMismoNodoNoSeAdvierteDosVecesAunqueAparezcaEnVariosArcos() {
        existeLaActividad(ACTIVIDAD_ID, "Revisar solicitud");
        existeLaActividad(31L, "Aprobar solicitud");
        existeLaActividad(32L, "Archivar solicitud");
        devolverSalientes(ACTIVIDAD_ID, List.of());
        devolverEntrantes(31L, List.of());
        devolverEntrantes(32L, List.of());

        List<String> advertencias = conexionesService.advertenciasTrasDesactivar(proceso(),
                List.of(arco(60L, ACTIVIDAD_ID, 31L, false), arco(61L, ACTIVIDAD_ID, 32L, false)), null, null);

        assertThat(advertencias).containsExactly("'Revisar solicitud' quedó sin arcos de salida",
                "'Aprobar solicitud' quedó sin arcos de entrada",
                "'Archivar solicitud' quedó sin arcos de entrada");
    }

    @Test
    void unNodoQueYaNoExisteSeDescribePorSuTipoYSuIdentificador() {
        when(actividadRepository.findByIdAndProcesoId(ACTIVIDAD_ID, PROCESO_ID)).thenReturn(Optional.empty());
        when(actividadRepository.findByIdAndProcesoId(31L, PROCESO_ID)).thenReturn(Optional.empty());
        devolverSalientes(ACTIVIDAD_ID, List.of());
        devolverEntrantes(31L, List.of());

        List<String> advertencias = conexionesService.advertenciasTrasDesactivar(proceso(),
                List.of(arco(60L, ACTIVIDAD_ID, 31L, false)), null, null);

        assertThat(advertencias).containsExactly("'ACTIVIDAD #30' quedó sin arcos de salida",
                "'ACTIVIDAD #31' quedó sin arcos de entrada");
    }

    @Test
    void unGatewayParaleloSoloSeRevisaPorElNumeroDeSalidas() {
        when(arcoRepository.findByProcesoIdAndOrigenTipoAndOrigenIdAndActivoTrueOrderByIdAsc(PROCESO_ID,
                TipoNodoFlujo.GATEWAY, GATEWAY_ID))
                .thenReturn(List.of(salienteDelGateway(61L, null), salienteDelGateway(62L, null)));

        assertThat(conexionesService.advertenciasDeGateway(proceso(), gateway(TipoGateway.PARALELO))).isEmpty();
    }

    @Test
    void unGatewayInclusivoAdvierteCuandoUnaSalidaNoTieneCondicion() {
        when(arcoRepository.findByProcesoIdAndOrigenTipoAndOrigenIdAndActivoTrueOrderByIdAsc(PROCESO_ID,
                TipoNodoFlujo.GATEWAY, GATEWAY_ID))
                .thenReturn(List.of(salienteDelGateway(61L, "requiere visto bueno"),
                        salienteDelGateway(62L, "   ")));

        assertThat(conexionesService.advertenciasDeGateway(proceso(), gateway(TipoGateway.INCLUSIVO)))
                .containsExactly("Gateway INCLUSIVO #12 tiene arcos de salida sin condición: un gateway exclusivo"
                        + " o inclusivo la exige en cada salida.");
    }

    @Test
    void unGatewayInclusivoNoPideExclusividadEntreSusCondiciones() {
        when(arcoRepository.findByProcesoIdAndOrigenTipoAndOrigenIdAndActivoTrueOrderByIdAsc(PROCESO_ID,
                TipoNodoFlujo.GATEWAY, GATEWAY_ID))
                .thenReturn(List.of(salienteDelGateway(61L, "monto > 100"), salienteDelGateway(62L,
                        "cliente preferente")));

        assertThat(conexionesService.advertenciasDeGateway(proceso(), gateway(TipoGateway.INCLUSIVO))).isEmpty();
    }

    @Test
    void salientesYEntrantesConsultanLosArcosActivosDelNodo() {
        devolverSalientes(ACTIVIDAD_ID, List.of(arco(60L, ACTIVIDAD_ID, 31L, true)));
        devolverEntrantes(ACTIVIDAD_ID, List.of(arco(61L, 32L, ACTIVIDAD_ID, true)));

        assertThat(conexionesService.salientesActivos(PROCESO_ID, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID)).hasSize(1);
        assertThat(conexionesService.entrantesActivos(PROCESO_ID, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID)).hasSize(1);
    }

    @Test
    void guardarCambiosPersisteLosArcosModificadosSinDesactivarlos() {
        List<Arco> modificados = List.of(salienteDelGateway(61L, "monto > 100"), salienteDelGateway(62L, null));

        conexionesService.guardarCambios(modificados);

        verify(arcoRepository).saveAll(modificados);
        assertThat(modificados).allMatch(Arco::isActivo);
    }

    @Test
    void guardarCambiosSinArcosNoTocaLaPersistencia() {
        conexionesService.guardarCambios(List.of());

        verify(arcoRepository, never()).saveAll(anyList());
    }

    private void devolverSalidasDelGateway(Arco... salidas) {
        when(arcoRepository.findByProcesoIdAndOrigenTipoAndOrigenIdAndActivoTrueOrderByIdAsc(PROCESO_ID,
                TipoNodoFlujo.GATEWAY, GATEWAY_ID)).thenReturn(List.of(salidas));
    }

    private String condicionRepetida(String condicion) {
        return "Gateway EXCLUSIVO #12 repite la condición '" + condicion
                + "' en más de una salida: esas condiciones no son mutuamente excluyentes.";
    }

    @Test
    void unGatewayExclusivoDetectaCondicionesQueSoloDifierenEnVariosEspacios() {
        devolverSalidasDelGateway(salienteDelGateway(61L, "monto > 100"),
                salienteDelGateway(62L, "monto   >     100"));

        assertThat(conexionesService.advertenciasDeGateway(proceso(), gateway(TipoGateway.EXCLUSIVO)))
                .containsExactly(condicionRepetida("monto   >     100"));
    }

    @Test
    void unGatewayExclusivoDetectaCondicionesQueSoloDifierenEnTabuladores() {
        devolverSalidasDelGateway(salienteDelGateway(61L, "monto > 100"), salienteDelGateway(62L, "monto\t>\t100"));

        assertThat(conexionesService.advertenciasDeGateway(proceso(), gateway(TipoGateway.EXCLUSIVO)))
                .containsExactly(condicionRepetida("monto\t>\t100"));
    }

    @Test
    void unGatewayExclusivoDetectaCondicionesQueSoloDifierenEnSaltosDeLinea() {
        devolverSalidasDelGateway(salienteDelGateway(61L, "monto >\n100"), salienteDelGateway(62L, "monto\r\n> 100"));

        assertThat(conexionesService.advertenciasDeGateway(proceso(), gateway(TipoGateway.EXCLUSIVO)))
                .containsExactly(condicionRepetida("monto\r\n> 100"));
    }

    @Test
    void unGatewayExclusivoIgnoraMayusculasYEspaciosExterioresAlCompararCondiciones() {
        devolverSalidasDelGateway(salienteDelGateway(61L, "monto > 100"),
                salienteDelGateway(62L, " \t MONTO > 100 \n"));

        assertThat(conexionesService.advertenciasDeGateway(proceso(), gateway(TipoGateway.EXCLUSIVO)))
                .containsExactly(condicionRepetida("MONTO > 100"));
    }

    @Test
    void unGatewayExclusivoNoConfundeCondicionesDistintasAunqueSeParezcan() {
        devolverSalidasDelGateway(salienteDelGateway(61L, "monto > 100"), salienteDelGateway(62L, "monto > 1000"));

        assertThat(conexionesService.advertenciasDeGateway(proceso(), gateway(TipoGateway.EXCLUSIVO)))
                .containsExactly("Gateway EXCLUSIVO #12: revisa que las condiciones de sus salidas sean mutuamente"
                        + " excluyentes, porque solo una puede cumplirse.");
    }

    @Test
    void conectadosActivosConsultaLosArcosDelNodoEnAmbosSentidos() {
        List<Arco> conectados = List.of(arco(60L, ACTIVIDAD_ID, 31L, true), arco(61L, 29L, ACTIVIDAD_ID, true));
        when(arcoRepository.conectadosAlNodo(PROCESO_ID, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID))
                .thenReturn(conectados);

        assertThat(conexionesService.conectadosActivos(proceso(), TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID))
                .isEqualTo(conectados);
        verify(arcoRepository, never()).saveAll(anyList());
    }

    @Test
    void siSeEliminaUnNodoSusArcosNoCuentanComoConexionesDeLosVecinos() {
        existeLaActividad(29L, "Registrar solicitud");
        devolverSalientes(29L, List.of(arco(60L, 29L, ACTIVIDAD_ID, true)));
        devolverEntrantes(31L, List.of(arco(61L, ACTIVIDAD_ID, 31L, true), arco(62L, 33L, 31L, true)));
        List<Arco> conectados = List.of(arco(60L, 29L, ACTIVIDAD_ID, true), arco(61L, ACTIVIDAD_ID, 31L, true));

        List<String> advertencias = conexionesService.advertenciasSiSeEliminaNodo(proceso(),
                TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID, conectados);

        assertThat(advertencias).containsExactly("'Registrar solicitud' quedó sin arcos de salida");
        assertThat(conectados).allMatch(Arco::isActivo);
    }
}
