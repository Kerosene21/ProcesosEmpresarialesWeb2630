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
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.dto.ConfigurarPermisoEstructuraDto;
import co.edu.javeriana.procesosempresariales.dto.CrearLaneDto;
import co.edu.javeriana.procesosempresariales.dto.CrearPoolDto;
import co.edu.javeriana.procesosempresariales.dto.EmpresaInvitadaDto;
import co.edu.javeriana.procesosempresariales.dto.ReordenarLanesDto;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.service.ComparticionProcesoService;
import co.edu.javeriana.procesosempresariales.service.LaneService;
import co.edu.javeriana.procesosempresariales.service.PermisoEstructuraService;
import co.edu.javeriana.procesosempresariales.service.PoolService;

@WebMvcTest(controllers = { PoolRestController.class, LaneRestController.class,
        ComparticionProcesoRestController.class, PermisoEstructuraRestController.class,
        AutenticacionController.class })
@Import(SecurityConfig.class)
class SeguridadEstructuraProcesoTest {

    private static final String USERNAME = "usuario@alpes.com";
    private static final String RUTA_POOLS = "/api/procesos/5/pools";
    private static final String RUTA_LANES = "/api/procesos/5/pools/80/lanes";
    private static final String RUTA_COMPARTIR = "/api/procesos/5/compartido-con/99";
    private static final String RUTA_PERMISOS = "/api/procesos/5/permisos-estructura";

    private static final String JSON_POOL = """
            {"nombre":"Cliente","tipo":"EXTERNO","cajaNegra":true}
            """;

    private static final String JSON_LANE = """
            {"rolProcesoId":40}
            """;

    private static final String JSON_PERMISOS = """
            {"crearPool":true,"editarPool":true,"eliminarPool":true,
             "crearLane":true,"editarLane":true,"eliminarLane":true}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PoolService poolService;

    @MockitoBean
    private LaneService laneService;

    @MockitoBean
    private ComparticionProcesoService comparticionProcesoService;

    @MockitoBean
    private PermisoEstructuraService permisoEstructuraService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void laConsultaDePoolsResponde401SinSesion() throws Exception {
        mockMvc.perform(get(RUTA_POOLS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));

        verify(poolService, never()).listar(anyLong(), anyString());
    }

    @Test
    void laCreacionDePoolsResponde401SinSesion() throws Exception {
        mockMvc.perform(post(RUTA_POOLS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON_POOL))
                .andExpect(status().isUnauthorized());

        verify(poolService, never()).crear(anyLong(), any(CrearPoolDto.class), anyString());
    }

    @Test
    void laCreacionDeLanesResponde401SinSesion() throws Exception {
        mockMvc.perform(post(RUTA_LANES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON_LANE))
                .andExpect(status().isUnauthorized());

        verify(laneService, never()).crear(anyLong(), anyLong(), any(CrearLaneDto.class), anyString());
    }

    @Test
    void elReordenamientoDeLanesResponde401SinSesion() throws Exception {
        mockMvc.perform(put(RUTA_LANES + "/orden").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lanes":[12,11]}
                        """))
                .andExpect(status().isUnauthorized());

        verify(laneService, never()).reordenar(anyLong(), anyLong(), any(ReordenarLanesDto.class), anyString());
    }

    @Test
    void compartirUnProcesoResponde401SinSesion() throws Exception {
        mockMvc.perform(post(RUTA_COMPARTIR).with(csrf())).andExpect(status().isUnauthorized());

        verify(comparticionProcesoService, never()).compartir(anyLong(), anyLong(), anyString());
    }

    @Test
    void configurarPermisosResponde401SinSesion() throws Exception {
        mockMvc.perform(put(RUTA_PERMISOS + "/EDITOR").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_PERMISOS))
                .andExpect(status().isUnauthorized());

        verify(permisoEstructuraService, never()).configurar(anyLong(), any(RolUsuario.class),
                any(ConfigurarPermisoEstructuraDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaConsultaLosPools() throws Exception {
        when(poolService.listar(5L, USERNAME)).thenReturn(List.of());

        mockMvc.perform(get(RUTA_POOLS)).andExpect(status().isOk());

        verify(poolService).listar(5L, USERNAME);
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoCreaPoolsAunqueEnvieElTokenCsrf() throws Exception {
        when(poolService.crear(anyLong(), any(CrearPoolDto.class), eq(USERNAME)))
                .thenThrow(new UsuarioSinPermisoException("El rol SOLO_LECTURA no tiene permiso para crear pools"));

        mockMvc.perform(post(RUTA_POOLS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON_POOL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void laCreacionDePoolsSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(post(RUTA_POOLS).contentType(MediaType.APPLICATION_JSON).content(JSON_POOL))
                .andExpect(status().isForbidden());

        verify(poolService, never()).crear(anyLong(), any(CrearPoolDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void laEliminacionDeLanesSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(delete(RUTA_LANES + "/12")).andExpect(status().isForbidden());

        verify(laneService, never()).eliminar(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void compartirSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(post(RUTA_COMPARTIR)).andExpect(status().isForbidden());

        verify(comparticionProcesoService, never()).compartir(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elAdministradorCompartePasandoSuIdentidadAlServicio() throws Exception {
        when(comparticionProcesoService.compartir(5L, 99L, USERNAME))
                .thenReturn(new EmpresaInvitadaDto(99L, "Andes Distribucion"));

        mockMvc.perform(post(RUTA_COMPARTIR).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Andes Distribucion"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void laConfiguracionDePermisosSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(put(RUTA_PERMISOS + "/EDITOR").contentType(MediaType.APPLICATION_JSON)
                .content(JSON_PERMISOS))
                .andExpect(status().isForbidden());

        verify(permisoEstructuraService, never()).configurar(anyLong(), any(RolUsuario.class),
                any(ConfigurarPermisoEstructuraDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorConsultaLaMatrizDePermisos() throws Exception {
        when(permisoEstructuraService.consultar(5L, USERNAME)).thenReturn(List.of());

        mockMvc.perform(get(RUTA_PERMISOS)).andExpect(status().isOk());

        verify(permisoEstructuraService).consultar(5L, USERNAME);
    }
}
