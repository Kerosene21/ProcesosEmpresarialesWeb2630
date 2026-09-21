package co.edu.javeriana.procesosempresariales.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.ActividadService;
import co.edu.javeriana.procesosempresariales.service.ArcoService;
import co.edu.javeriana.procesosempresariales.service.GatewayService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;

@WebMvcTest(controllers = ProcesoController.class)
@Import(SecurityConfig.class)
class DiagramaProcesoTest {

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

    private ProcesoRespuestaDto proceso(boolean eliminado) {
        ProcesoRespuestaDto proceso = new ProcesoRespuestaDto();
        proceso.setId(5L);
        proceso.setNombre("Ventas");
        proceso.setDescripcion("Proceso comercial");
        proceso.setCategoria("Comercial");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setPoolId(80L);
        proceso.setPoolNombre("Alpes Logistica");
        proceso.setEliminado(eliminado);
        return proceso;
    }

    private ActividadRespuestaDto actividad(Long id, String nombre, Long laneId, int x, int y) {
        ActividadRespuestaDto actividad = new ActividadRespuestaDto();
        actividad.setId(id);
        actividad.setNombre(nombre);
        actividad.setTipo(TipoActividad.TAREA_USUARIO);
        actividad.setProcesoId(5L);
        actividad.setLaneId(laneId);
        actividad.setPosicionX(x);
        actividad.setPosicionY(y);
        actividad.setActivo(true);
        return actividad;
    }

    private void devolverElProceso(boolean eliminado) {
        when(procesoService.obtener(eq(5L), anyString())).thenReturn(proceso(eliminado));
    }

    private void devolverElDiagramaCompleto() {
        when(actividadService.lanesDelProceso(eq(5L), anyString()))
                .thenReturn(List.of(new LaneRespuestaDto(11L, "General"), new LaneRespuestaDto(12L, "Cartera")));
        when(actividadService.consultarActivas(eq(5L), anyString()))
                .thenReturn(List.of(actividad(30L, "Revisar solicitud", 11L, 120, 40),
                        actividad(31L, "Cobrar factura", 12L, 200, 80)));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elDiagramaDibujaElPoolSusLanesYSusActividades() throws Exception {
        devolverElProceso(false);
        devolverElDiagramaCompleto();
        when(procesoService.puedeEditar(anyString())).thenReturn(true);
        when(procesoService.puedeEliminar(anyString())).thenReturn(true);

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/proceso"))
                .andExpect(content().string(containsString("Alpes Logistica")))
                .andExpect(content().string(containsString("General")))
                .andExpect(content().string(containsString("Cartera")))
                .andExpect(content().string(containsString("Revisar solicitud")))
                .andExpect(content().string(containsString("Cobrar factura")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void cadaActividadSeDibujaEnLaPosicionQueTieneGuardada() throws Exception {
        devolverElProceso(false);
        devolverElDiagramaCompleto();
        when(procesoService.puedeEditar(anyString())).thenReturn(true);
        when(procesoService.puedeEliminar(anyString())).thenReturn(true);

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("left:120px; top:40px")))
                .andExpect(content().string(containsString("left:200px; top:80px")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elAdministradorVeLasAccionesDeCadaActividadYLaDeAgregar() throws Exception {
        devolverElProceso(false);
        devolverElDiagramaCompleto();
        when(procesoService.puedeEditar(anyString())).thenReturn(true);
        when(procesoService.puedeEliminar(anyString())).thenReturn(true);

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/procesos/5/actividades/nueva")))
                .andExpect(content().string(containsString("/procesos/5/actividades/30/editar")))
                .andExpect(content().string(containsString("/procesos/5/actividades/30/eliminar")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void elUsuarioDeSoloLecturaVeElDiagramaSinAccionesDeEdicion() throws Exception {
        devolverElProceso(false);
        devolverElDiagramaCompleto();
        when(procesoService.puedeEditar(anyString())).thenReturn(false);
        when(procesoService.puedeEliminar(anyString())).thenReturn(false);

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Revisar solicitud")))
                .andExpect(content().string(not(containsString("/procesos/5/actividades/nueva"))))
                .andExpect(content().string(not(containsString("/procesos/5/actividades/30/editar"))))
                .andExpect(content().string(not(containsString("/procesos/5/actividades/30/eliminar"))));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void unProcesoEliminadoMuestraElDiagramaSinAccionesSobreSusActividades() throws Exception {
        devolverElProceso(true);
        devolverElDiagramaCompleto();
        when(procesoService.puedeEditar(anyString())).thenReturn(true);
        when(procesoService.puedeEliminar(anyString())).thenReturn(true);

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Revisar solicitud")))
                .andExpect(content().string(not(containsString("/procesos/5/actividades/nueva"))))
                .andExpect(content().string(not(containsString("/procesos/5/actividades/30/editar"))));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void unProcesoSinActividadesLoDiceEnLugarDeDibujarlas() throws Exception {
        devolverElProceso(false);
        when(actividadService.lanesDelProceso(eq(5L), anyString()))
                .thenReturn(List.of(new LaneRespuestaDto(11L, "General")));
        when(actividadService.consultarActivas(eq(5L), anyString())).thenReturn(List.of());
        when(procesoService.puedeEditar(anyString())).thenReturn(true);
        when(procesoService.puedeEliminar(anyString())).thenReturn(true);

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("General")))
                .andExpect(content().string(containsString("El diagrama todavía no tiene actividades")));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void unPoolSinLanesLoAdvierteEnElDiagrama() throws Exception {
        devolverElProceso(false);
        when(actividadService.lanesDelProceso(eq(5L), anyString())).thenReturn(List.of());
        when(actividadService.consultarActivas(eq(5L), anyString())).thenReturn(List.of());
        when(procesoService.puedeEditar(anyString())).thenReturn(true);
        when(procesoService.puedeEliminar(anyString())).thenReturn(true);

        mockMvc.perform(get(DETALLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El pool todavía no tiene lanes")));
    }
}
