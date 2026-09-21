package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.EditarActividadDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.ActividadService;

@ExtendWith(MockitoExtension.class)
class ActividadControllerTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA_NUEVA = "/procesos/5/actividades/nueva";
    private static final String RUTA_ACTIVIDADES = "/procesos/5/actividades";
    private static final String RUTA_EDITAR = "/procesos/5/actividades/30/editar";
    private static final String RUTA_ACTIVIDAD = "/procesos/5/actividades/30";
    private static final String RUTA_ELIMINAR = "/procesos/5/actividades/30/eliminar";
    private static final String DETALLE_DEL_PROCESO = "/procesos/5";

    @Mock
    private ActividadService actividadService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ActividadController(actividadService)).build();
    }

    private ActividadRespuestaDto actividad() {
        ActividadRespuestaDto actividad = new ActividadRespuestaDto();
        actividad.setId(30L);
        actividad.setNombre("Revisar solicitud");
        actividad.setTipo(TipoActividad.TAREA_USUARIO);
        actividad.setProcesoId(5L);
        actividad.setLaneId(11L);
        actividad.setLaneNombre("General");
        actividad.setPosicionX(120);
        actividad.setPosicionY(40);
        actividad.setActivo(true);
        return actividad;
    }

    private void devolverLasLanes() {
        when(actividadService.lanesDelProceso(5L, USERNAME))
                .thenReturn(List.of(new LaneRespuestaDto(11L, "General"), new LaneRespuestaDto(12L, "Cartera")));
    }

    @Test
    void elFormularioDeCreacionOfreceLasLanesYLosTiposDelProceso() throws Exception {
        devolverLasLanes();

        mockMvc.perform(get(RUTA_NUEVA).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("actividades/formulario"))
                .andExpect(model().attribute("procesoId", 5L))
                .andExpect(model().attributeExists("actividad", "lanes", "tipos"));
    }

    @Test
    void elFormularioDeCreacionNoModificaNada() throws Exception {
        devolverLasLanes();

        mockMvc.perform(get(RUTA_NUEVA).principal(PRINCIPAL)).andExpect(status().isOk());

        verify(actividadService, never()).crear(anyLong(), any(CrearActividadDto.class), anyString());
    }

    @Test
    void crearEnviaElFormularioAlServicioYVuelveAlProceso() throws Exception {
        when(actividadService.crear(anyLong(), any(CrearActividadDto.class), anyString())).thenReturn(actividad());

        mockMvc.perform(post(RUTA_ACTIVIDADES).principal(PRINCIPAL)
                .param("nombre", "Revisar solicitud")
                .param("tipo", "TAREA_USUARIO")
                .param("laneId", "11")
                .param("posicionX", "120")
                .param("posicionY", "40"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETALLE_DEL_PROCESO))
                .andExpect(flash().attributeExists("mensaje"));

        ArgumentCaptor<CrearActividadDto> capturado = ArgumentCaptor.forClass(CrearActividadDto.class);
        verify(actividadService).crear(eq(5L), capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getPosicionX()).isEqualTo(120);
        assertThat(capturado.getValue().getPosicionY()).isEqualTo(40);
    }

    @Test
    void crearVuelveAlFormularioCuandoFaltanDatos() throws Exception {
        devolverLasLanes();

        mockMvc.perform(post(RUTA_ACTIVIDADES).principal(PRINCIPAL)
                .param("nombre", "   ")
                .param("tipo", "TAREA_USUARIO")
                .param("laneId", "11")
                .param("posicionX", "120")
                .param("posicionY", "40"))
                .andExpect(status().isOk())
                .andExpect(view().name("actividades/formulario"))
                .andExpect(model().attributeExists("lanes", "tipos"));

        verify(actividadService, never()).crear(anyLong(), any(CrearActividadDto.class), anyString());
    }

    @Test
    void elFormularioDeEdicionLlegaConLosDatosActuales() throws Exception {
        devolverLasLanes();
        when(actividadService.obtener(5L, 30L, USERNAME)).thenReturn(actividad());

        MvcResult resultado = mockMvc.perform(get(RUTA_EDITAR).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("actividades/formularioeditar"))
                .andExpect(model().attribute("actividadId", 30L))
                .andReturn();

        EditarActividadDto formulario = (EditarActividadDto) resultado.getModelAndView().getModel().get("actividad");
        assertThat(formulario.getNombre()).isEqualTo("Revisar solicitud");
        assertThat(formulario.getTipo()).isEqualTo(TipoActividad.TAREA_USUARIO);
        assertThat(formulario.getLaneId()).isEqualTo(11L);
    }

    @Test
    void actualizarEnviaElFormularioAlServicioYVuelveAlProceso() throws Exception {
        when(actividadService.editar(anyLong(), anyLong(), any(EditarActividadDto.class), anyString()))
                .thenReturn(actividad());

        mockMvc.perform(post(RUTA_ACTIVIDAD).principal(PRINCIPAL)
                .param("nombre", "Validar solicitud")
                .param("tipo", "TAREA_SISTEMA")
                .param("laneId", "12"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETALLE_DEL_PROCESO))
                .andExpect(flash().attributeExists("mensaje"));

        ArgumentCaptor<EditarActividadDto> capturado = ArgumentCaptor.forClass(EditarActividadDto.class);
        verify(actividadService).editar(eq(5L), eq(30L), capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getLaneId()).isEqualTo(12L);
    }

    @Test
    void actualizarVuelveAlFormularioCuandoFaltanDatos() throws Exception {
        devolverLasLanes();

        mockMvc.perform(post(RUTA_ACTIVIDAD).principal(PRINCIPAL)
                .param("nombre", "   ")
                .param("tipo", "TAREA_SISTEMA")
                .param("laneId", "12"))
                .andExpect(status().isOk())
                .andExpect(view().name("actividades/formularioeditar"))
                .andExpect(model().attribute("actividadId", 30L));

        verify(actividadService, never()).editar(anyLong(), anyLong(), any(EditarActividadDto.class), anyString());
    }

    @Test
    void laConfirmacionMuestraLaActividadQueSeVaAEliminar() throws Exception {
        when(actividadService.obtenerParaEliminar(5L, 30L, USERNAME)).thenReturn(actividad());

        mockMvc.perform(get(RUTA_ELIMINAR).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("actividades/confirmareliminacion"))
                .andExpect(model().attribute("procesoId", 5L))
                .andExpect(model().attributeExists("actividad"));
    }

    @Test
    void laConfirmacionNoEliminaNada() throws Exception {
        when(actividadService.obtenerParaEliminar(5L, 30L, USERNAME)).thenReturn(actividad());

        mockMvc.perform(get(RUTA_ELIMINAR).principal(PRINCIPAL)).andExpect(status().isOk());

        verify(actividadService, never()).eliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    void elEnvioDeLaConfirmacionEliminaLaActividadYVuelveAlProceso() throws Exception {
        when(actividadService.eliminar(5L, 30L, USERNAME)).thenReturn(actividad());

        mockMvc.perform(post(RUTA_ELIMINAR).principal(PRINCIPAL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETALLE_DEL_PROCESO))
                .andExpect(flash().attributeExists("mensaje"));

        verify(actividadService).eliminar(5L, 30L, USERNAME);
    }

    @Test
    void eliminarLlevaLasAdvertenciasDeDesconexionAlDetalleDelProceso() throws Exception {
        ActividadRespuestaDto eliminada = actividad();
        eliminada.setActivo(false);
        eliminada.setArcosDesactivados(2);
        eliminada.setAdvertencias(List.of("'Aprobar solicitud' quedó sin arcos de entrada",
                "'Cobrar factura' quedó sin arcos de salida"));
        when(actividadService.eliminar(5L, 30L, USERNAME)).thenReturn(eliminada);

        mockMvc.perform(post(RUTA_ELIMINAR).principal(PRINCIPAL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETALLE_DEL_PROCESO))
                .andExpect(flash().attributeExists("mensaje"))
                .andExpect(flash().attribute("advertencias",
                        List.of("'Aprobar solicitud' quedó sin arcos de entrada",
                                "'Cobrar factura' quedó sin arcos de salida")));
    }

    @Test
    void eliminarSinArcosConectadosNoAnadeAdvertencias() throws Exception {
        when(actividadService.eliminar(5L, 30L, USERNAME)).thenReturn(actividad());

        mockMvc.perform(post(RUTA_ELIMINAR).principal(PRINCIPAL))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("advertencias", List.of()));
    }
}
