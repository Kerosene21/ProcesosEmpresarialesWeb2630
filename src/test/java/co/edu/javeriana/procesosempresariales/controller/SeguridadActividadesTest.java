package co.edu.javeriana.procesosempresariales.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.javeriana.procesosempresariales.config.SecurityConfig;
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.EditarActividadDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.ActividadService;

@WebMvcTest(controllers = { ActividadController.class, ActividadRestController.class,
        AutenticacionController.class })
@Import(SecurityConfig.class)
class SeguridadActividadesTest {

    private static final String USERNAME = "usuario@alpes.com";
    private static final String RUTA_NUEVA = "/procesos/5/actividades/nueva";
    private static final String RUTA_ACTIVIDADES = "/procesos/5/actividades";
    private static final String RUTA_EDITAR = "/procesos/5/actividades/30/editar";
    private static final String RUTA_ACTIVIDAD = "/procesos/5/actividades/30";
    private static final String RUTA_ELIMINAR = "/procesos/5/actividades/30/eliminar";
    private static final String RUTA_API_ACTIVIDADES = "/api/procesos/5/actividades";
    private static final String RUTA_API_ACTIVIDAD = "/api/procesos/5/actividades/30";
    private static final String REDIRECCION_LOGIN = "/login";

    private static final String JSON_CREACION = """
            {"nombre":"Revisar solicitud","tipo":"TAREA_USUARIO","laneId":11,"posicionX":120,"posicionY":40}
            """;

    private static final String JSON_EDICION = """
            {"nombre":"Validar solicitud","tipo":"TAREA_SISTEMA","laneId":12}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActividadService actividadService;

    @MockitoBean
    private UserDetailsService userDetailsService;

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
        when(actividadService.lanesDelProceso(eq(5L), anyString()))
                .thenReturn(List.of(new LaneRespuestaDto(11L, "General")));
    }

    @Test
    void elFormularioDeCreacionExigeSesion() throws Exception {
        mockMvc.perform(get(RUTA_NUEVA))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(actividadService, never()).lanesDelProceso(anyLong(), anyString());
    }

    @Test
    void laCreacionExigeSesion() throws Exception {
        mockMvc.perform(post(RUTA_ACTIVIDADES).with(csrf())
                .param("nombre", "Revisar solicitud")
                .param("tipo", "TAREA_USUARIO")
                .param("laneId", "11")
                .param("posicionX", "120")
                .param("posicionY", "40"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(actividadService, never()).crear(anyLong(), any(CrearActividadDto.class), anyString());
    }

    @Test
    void elFormularioDeEdicionExigeSesion() throws Exception {
        mockMvc.perform(get(RUTA_EDITAR))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));
    }

    @Test
    void laConfirmacionDeEliminacionExigeSesion() throws Exception {
        mockMvc.perform(get(RUTA_ELIMINAR))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(actividadService, never()).obtenerParaEliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void unAdministradorAbreElFormularioDeCreacion() throws Exception {
        devolverLasLanes();

        mockMvc.perform(get(RUTA_NUEVA)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorAbreElFormularioDeCreacion() throws Exception {
        devolverLasLanes();

        mockMvc.perform(get(RUTA_NUEVA)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoAbreElFormularioDeCreacion() throws Exception {
        mockMvc.perform(get(RUTA_NUEVA)).andExpect(status().isForbidden());

        verify(actividadService, never()).lanesDelProceso(anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void unAdministradorCreaLaActividad() throws Exception {
        when(actividadService.crear(anyLong(), any(CrearActividadDto.class), anyString())).thenReturn(actividad());

        mockMvc.perform(post(RUTA_ACTIVIDADES).with(csrf())
                .param("nombre", "Revisar solicitud")
                .param("tipo", "TAREA_USUARIO")
                .param("laneId", "11")
                .param("posicionX", "120")
                .param("posicionY", "40"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/procesos/5"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorCreaLaActividad() throws Exception {
        when(actividadService.crear(anyLong(), any(CrearActividadDto.class), anyString())).thenReturn(actividad());

        mockMvc.perform(post(RUTA_ACTIVIDADES).with(csrf())
                .param("nombre", "Revisar solicitud")
                .param("tipo", "TAREA_USUARIO")
                .param("laneId", "11")
                .param("posicionX", "120")
                .param("posicionY", "40"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoCreaLaActividad() throws Exception {
        mockMvc.perform(post(RUTA_ACTIVIDADES).with(csrf())
                .param("nombre", "Revisar solicitud")
                .param("tipo", "TAREA_USUARIO")
                .param("laneId", "11")
                .param("posicionX", "120")
                .param("posicionY", "40"))
                .andExpect(status().isForbidden());

        verify(actividadService, never()).crear(anyLong(), any(CrearActividadDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void laCreacionSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(post(RUTA_ACTIVIDADES)
                .param("nombre", "Revisar solicitud")
                .param("tipo", "TAREA_USUARIO")
                .param("laneId", "11")
                .param("posicionX", "120")
                .param("posicionY", "40"))
                .andExpect(status().isForbidden());

        verify(actividadService, never()).crear(anyLong(), any(CrearActividadDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorAbreElFormularioDeEdicion() throws Exception {
        devolverLasLanes();
        when(actividadService.obtener(eq(5L), eq(30L), anyString())).thenReturn(actividad());

        mockMvc.perform(get(RUTA_EDITAR)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoAbreElFormularioDeEdicion() throws Exception {
        mockMvc.perform(get(RUTA_EDITAR)).andExpect(status().isForbidden());

        verify(actividadService, never()).obtener(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorGuardaLaEdicion() throws Exception {
        when(actividadService.editar(anyLong(), anyLong(), any(EditarActividadDto.class), anyString()))
                .thenReturn(actividad());

        mockMvc.perform(post(RUTA_ACTIVIDAD).with(csrf())
                .param("nombre", "Validar solicitud")
                .param("tipo", "TAREA_SISTEMA")
                .param("laneId", "12"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/procesos/5"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoGuardaLaEdicion() throws Exception {
        mockMvc.perform(post(RUTA_ACTIVIDAD).with(csrf())
                .param("nombre", "Validar solicitud")
                .param("tipo", "TAREA_SISTEMA")
                .param("laneId", "12"))
                .andExpect(status().isForbidden());

        verify(actividadService, never()).editar(anyLong(), anyLong(), any(EditarActividadDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void unAdministradorAbreLaConfirmacionDeEliminacion() throws Exception {
        when(actividadService.obtenerParaEliminar(eq(5L), eq(30L), anyString())).thenReturn(actividad());

        mockMvc.perform(get(RUTA_ELIMINAR)).andExpect(status().isOk());

        verify(actividadService, never()).eliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorNoAbreLaConfirmacionDeEliminacion() throws Exception {
        mockMvc.perform(get(RUTA_ELIMINAR)).andExpect(status().isForbidden());

        verify(actividadService, never()).obtenerParaEliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoAbreLaConfirmacionDeEliminacion() throws Exception {
        mockMvc.perform(get(RUTA_ELIMINAR)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void unAdministradorConfirmaLaEliminacion() throws Exception {
        when(actividadService.eliminar(eq(5L), eq(30L), anyString())).thenReturn(actividad());

        mockMvc.perform(post(RUTA_ELIMINAR).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/procesos/5"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorNoConfirmaLaEliminacion() throws Exception {
        mockMvc.perform(post(RUTA_ELIMINAR).with(csrf())).andExpect(status().isForbidden());

        verify(actividadService, never()).eliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoConfirmaLaEliminacion() throws Exception {
        mockMvc.perform(post(RUTA_ELIMINAR).with(csrf())).andExpect(status().isForbidden());

        verify(actividadService, never()).eliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void laEliminacionSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(post(RUTA_ELIMINAR)).andExpect(status().isForbidden());

        verify(actividadService, never()).eliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    void laApiDeCreacionDeActividadesResponde401EnJsonSinAutenticacion() throws Exception {
        mockMvc.perform(post(RUTA_API_ACTIVIDADES).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));

        verify(actividadService, never()).crear(anyLong(), any(CrearActividadDto.class), anyString());
    }

    @Test
    void laApiDeEdicionDeActividadesResponde401EnJsonSinAutenticacion() throws Exception {
        mockMvc.perform(put(RUTA_API_ACTIVIDAD).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));

        verify(actividadService, never()).editar(anyLong(), anyLong(), any(EditarActividadDto.class), anyString());
    }

    @Test
    void laApiDeEliminacionDeActividadesResponde401EnJsonSinAutenticacion() throws Exception {
        mockMvc.perform(delete(RUTA_API_ACTIVIDAD).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));

        verify(actividadService, never()).eliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void laApiDelegaLosPermisosDeNegocioAlServicio() throws Exception {
        when(actividadService.crear(anyLong(), any(CrearActividadDto.class), anyString()))
                .thenReturn(actividad());

        mockMvc.perform(post(RUTA_API_ACTIVIDADES).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isCreated());

        verify(actividadService).crear(eq(5L), any(CrearActividadDto.class), eq(USERNAME));
    }
}
