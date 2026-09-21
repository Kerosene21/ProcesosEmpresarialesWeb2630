package co.edu.javeriana.procesosempresariales.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.javeriana.procesosempresariales.config.SecurityConfig;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ArcoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.ActividadService;
import co.edu.javeriana.procesosempresariales.service.ArcoService;
import co.edu.javeriana.procesosempresariales.service.GatewayService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;

@WebMvcTest(controllers = ProcesoController.class)
@Import(SecurityConfig.class)
class DiagramaFlujoTest {

    private static final String USERNAME = "usuario@alpes.com";
    private static final String DETALLE = "/procesos/5";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcesoService procesoService;

    @MockitoBean
    private ActividadService actividadService;

    @MockitoBean
    private ArcoService arcoService;

    @MockitoBean
    private GatewayService gatewayService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private ProcesoRespuestaDto proceso() {
        ProcesoRespuestaDto proceso = new ProcesoRespuestaDto();
        proceso.setId(5L);
        proceso.setNombre("Ventas");
        proceso.setDescripcion("Proceso comercial");
        proceso.setCategoria("Comercial");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setPoolId(80L);
        proceso.setPoolNombre("Alpes Logistica");
        return proceso;
    }

    private ActividadRespuestaDto actividad(Long id, String nombre, int x, int y) {
        ActividadRespuestaDto actividad = new ActividadRespuestaDto();
        actividad.setId(id);
        actividad.setNombre(nombre);
        actividad.setTipo(TipoActividad.TAREA_USUARIO);
        actividad.setProcesoId(5L);
        actividad.setLaneId(11L);
        actividad.setPosicionX(x);
        actividad.setPosicionY(y);
        actividad.setActivo(true);
        return actividad;
    }

    private GatewayRespuestaDto gateway(TipoGateway tipo, int x, int y) {
        GatewayRespuestaDto gateway = new GatewayRespuestaDto();
        gateway.setId(12L);
        gateway.setProcesoId(5L);
        gateway.setTipo(tipo);
        gateway.setSimbolo(tipo.getSimbolo());
        gateway.setEtiqueta("Gateway " + tipo + " #12");
        gateway.setPosicionX(x);
        gateway.setPosicionY(y);
        gateway.setActivo(true);
        return gateway;
    }

    private ArcoRespuestaDto arco() {
        ArcoRespuestaDto arco = new ArcoRespuestaDto();
        arco.setId(60L);
        arco.setProcesoId(5L);
        arco.setOrigenTipo(TipoNodoFlujo.ACTIVIDAD);
        arco.setOrigenId(30L);
        arco.setOrigenNombre("Revisar solicitud");
        arco.setDestinoTipo(TipoNodoFlujo.GATEWAY);
        arco.setDestinoId(12L);
        arco.setDestinoNombre("Gateway EXCLUSIVO #12");
        arco.setEtiqueta("solicitud completa");
        arco.setActivo(true);
        arco.setOrigenX(165);
        arco.setOrigenY(73);
        arco.setDestinoX(377);
        arco.setDestinoY(123);
        return arco;
    }

    private void devolverElDiagrama(List<GatewayRespuestaDto> gateways, List<ArcoRespuestaDto> arcos,
            List<String> consistencia) {
        when(procesoService.obtener(eq(5L), anyString())).thenReturn(proceso());
        when(procesoService.puedeEditar(anyString())).thenReturn(true);
        when(procesoService.puedeEliminar(anyString())).thenReturn(true);
        when(actividadService.lanesDelProceso(eq(5L), anyString()))
                .thenReturn(List.of(new LaneRespuestaDto(11L, "General")));
        when(actividadService.consultarActivas(eq(5L), anyString()))
                .thenReturn(List.of(actividad(30L, "Revisar solicitud", 100, 50)));
        when(gatewayService.consultarActivos(eq(5L), anyString())).thenReturn(gateways);
        when(arcoService.consultarActivos(eq(5L), anyString())).thenReturn(arcos);
        when(gatewayService.advertenciasDelProceso(eq(5L), anyString())).thenReturn(consistencia);
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elDiagramaDibujaElArcoComoUnaLineaConPuntaSolida() throws Exception {
        devolverElDiagrama(List.of(gateway(TipoGateway.EXCLUSIVO, 300, 100)), List.of(arco()), List.of());

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/proceso"))
                .andExpect(content().string(containsString("marker-end=\"url(#punta-de-arco)\"")))
                .andExpect(content().string(containsString("x1=\"165\"")))
                .andExpect(content().string(containsString("y1=\"73\"")))
                .andExpect(content().string(containsString("x2=\"377\"")))
                .andExpect(content().string(containsString("y2=\"123\"")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elDiagramaDibujaElGatewayComoUnRomboConSuSimbolo() throws Exception {
        devolverElDiagrama(List.of(gateway(TipoGateway.EXCLUSIVO, 300, 100)), List.of(arco()), List.of());

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"nodo-gateway\"")))
                .andExpect(content().string(containsString("points=\"323,100 346,123 323,146 300,123\"")))
                .andExpect(content().string(containsString("class=\"gateway-simbolo\"")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elSimboloDelGatewayCambiaConSuTipo() throws Exception {
        devolverElDiagrama(List.of(gateway(TipoGateway.PARALELO, 300, 100)), List.of(), List.of());

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PARALELO")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elDiagramaDibujaLaActividadEnElLienzoDelFlujo() throws Exception {
        devolverElDiagrama(List.of(), List.of(), List.of());

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"nodo-actividad\"")))
                .andExpect(content().string(containsString("x=\"100\"")))
                .andExpect(content().string(containsString("y=\"50\"")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elDiagramaMuestraLaEtiquetaDelArco() throws Exception {
        devolverElDiagrama(List.of(gateway(TipoGateway.EXCLUSIVO, 300, 100)), List.of(arco()), List.of());

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("solicitud completa")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elDiagramaMuestraLasAdvertenciasDeConsistencia() throws Exception {
        devolverElDiagrama(List.of(gateway(TipoGateway.EXCLUSIVO, 300, 100)), List.of(arco()),
                List.of("Gateway EXCLUSIVO #12 tiene 1 arco de salida: como divergencia necesita al menos dos."));

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Advertencias de consistencia del modelo")))
                .andExpect(content().string(containsString("como divergencia necesita al menos dos")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elAdministradorVeLasAccionesSobreArcosYGateways() throws Exception {
        devolverElDiagrama(List.of(gateway(TipoGateway.EXCLUSIVO, 300, 100)), List.of(arco()), List.of());

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/procesos/5/arcos/nuevo")))
                .andExpect(content().string(containsString("/procesos/5/gateways/nuevo")))
                .andExpect(content().string(containsString("/procesos/5/arcos/60/editar")))
                .andExpect(content().string(containsString("/procesos/5/arcos/60/eliminar")))
                .andExpect(content().string(containsString("/procesos/5/gateways/12/editar")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void elUsuarioDeSoloLecturaVeElFlujoSinAccionesDeEdicion() throws Exception {
        devolverElDiagrama(List.of(gateway(TipoGateway.EXCLUSIVO, 300, 100)), List.of(arco()), List.of());
        when(procesoService.puedeEditar(anyString())).thenReturn(false);
        when(procesoService.puedeEliminar(anyString())).thenReturn(false);

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"nodo-gateway\"")))
                .andExpect(content().string(not(containsString("/procesos/5/arcos/nuevo"))))
                .andExpect(content().string(not(containsString("/procesos/5/gateways/nuevo"))))
                .andExpect(content().string(not(containsString("/procesos/5/arcos/60/editar"))));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void unProcesoSinGatewaysNiArcosLoDiceEnLugarDeDibujarlos() throws Exception {
        devolverElDiagrama(List.of(), List.of(), List.of());

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El flujo todavía no tiene gateways ni arcos")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elDiagramaSigueDiciendoQueLosEventosNoExisten() throws Exception {
        devolverElDiagrama(List.of(), List.of(), List.of());

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Los eventos todavía no forman parte del modelo")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elDetalleMuestraLasAdvertenciasQueLleganTrasEliminarUnArco() throws Exception {
        devolverElDiagrama(List.of(), List.of(), List.of());

        mockMvc.perform(get(DETALLE)
                .flashAttr("mensaje", "El arco Revisar solicitud hacia Aprobar solicitud quedo eliminado")
                .flashAttr("advertencias", List.of("'Revisar solicitud' quedó sin arcos de salida",
                        "'Aprobar solicitud' quedó sin arcos de entrada")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Advertencias de la última operación")))
                .andExpect(content().string(containsString("Revisar solicitud&#39; quedó sin arcos de salida")))
                .andExpect(content().string(containsString("Aprobar solicitud&#39; quedó sin arcos de entrada")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elDetalleMuestraLasAdvertenciasQueLleganTrasEliminarUnaActividad() throws Exception {
        devolverElDiagrama(List.of(), List.of(), List.of());

        mockMvc.perform(get(DETALLE)
                .flashAttr("mensaje", "La actividad Revisar solicitud quedo eliminada y conserva su historial")
                .flashAttr("advertencias", List.of("'Cobrar factura' quedó sin arcos de entrada")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Advertencias de la última operación")))
                .andExpect(content().string(containsString("Cobrar factura&#39; quedó sin arcos de entrada")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elDetalleNoDibujaElBloqueDeAdvertenciasCuandoNoLasHay() throws Exception {
        devolverElDiagrama(List.of(), List.of(), List.of());

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Advertencias de la última operación"))));
    }
}
