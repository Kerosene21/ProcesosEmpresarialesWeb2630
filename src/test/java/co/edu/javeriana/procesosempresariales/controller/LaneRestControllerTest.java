package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.edu.javeriana.procesosempresariales.dto.CrearLaneDto;
import co.edu.javeriana.procesosempresariales.dto.EditarLaneDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ReordenarLanesDto;
import co.edu.javeriana.procesosempresariales.exception.LaneConActividadesException;
import co.edu.javeriana.procesosempresariales.exception.LaneDuplicadaException;
import co.edu.javeriana.procesosempresariales.exception.PoolCajaNegraException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.service.LaneService;

@ExtendWith(MockitoExtension.class)
class LaneRestControllerTest {

    private static final String USERNAME = "editor@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA_LANES = "/api/procesos/5/pools/80/lanes";
    private static final String RUTA_LANE = "/api/procesos/5/pools/80/lanes/12";

    private static final String JSON_LANE = """
            {"rolProcesoId":40,"orden":2}
            """;

    @Mock
    private LaneService laneService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LaneRestController(laneService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private LaneRespuestaDto lane(Long id, String nombre, int orden) {
        LaneRespuestaDto lane = new LaneRespuestaDto(id, nombre);
        lane.setRolProcesoId(40L);
        lane.setPoolId(80L);
        lane.setOrden(orden);
        return lane;
    }

    @Test
    void listarDevuelveLasLanesDelPoolEnOrden() throws Exception {
        when(laneService.listar(5L, 80L, USERNAME))
                .thenReturn(List.of(lane(11L, "General", 1), lane(12L, "Analista de credito", 2)));

        mockMvc.perform(get(RUTA_LANES).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orden").value(1))
                .andExpect(jsonPath("$[1].nombre").value("Analista de credito"))
                .andExpect(jsonPath("$[1].rolProcesoId").value(40))
                .andExpect(jsonPath("$[1].poolId").value(80));
    }

    @Test
    void crearDevuelve201ConLaUbicacionDeLaLane() throws Exception {
        when(laneService.crear(anyLong(), anyLong(), any(CrearLaneDto.class), anyString()))
                .thenReturn(lane(12L, "Analista de credito", 2));

        mockMvc.perform(post(RUTA_LANES).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_LANE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost" + RUTA_LANE))
                .andExpect(jsonPath("$.nombre").value("Analista de credito"));

        ArgumentCaptor<CrearLaneDto> capturado = ArgumentCaptor.forClass(CrearLaneDto.class);
        verify(laneService).crear(eq(5L), eq(80L), capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getRolProcesoId()).isEqualTo(40L);
        assertThat(capturado.getValue().getOrden()).isEqualTo(2);
    }

    @Test
    void crearSinRolDeProcesoDevuelve400YNoLlamaAlServicio() throws Exception {
        mockMvc.perform(post(RUTA_LANES).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orden":2}
                        """))
                .andExpect(status().isBadRequest());

        verify(laneService, never()).crear(anyLong(), anyLong(), any(CrearLaneDto.class), anyString());
    }

    @Test
    void crearConOrdenMenorAUnoDevuelve400() throws Exception {
        mockMvc.perform(post(RUTA_LANES).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"rolProcesoId":40,"orden":0}
                        """))
                .andExpect(status().isBadRequest());

        verify(laneService, never()).crear(anyLong(), anyLong(), any(CrearLaneDto.class), anyString());
    }

    @Test
    void crearUnaLaneRepetidaDevuelve409() throws Exception {
        when(laneService.crear(anyLong(), anyLong(), any(CrearLaneDto.class), anyString()))
                .thenThrow(new LaneDuplicadaException("El rol ya tiene una lane en este pool"));

        mockMvc.perform(post(RUTA_LANES).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_LANE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LANE_DUPLICADA"));
    }

    @Test
    void crearEnUnPoolDeCajaNegraDevuelve409() throws Exception {
        when(laneService.crear(anyLong(), anyLong(), any(CrearLaneDto.class), anyString()))
                .thenThrow(new PoolCajaNegraException("El pool es una caja negra"));

        mockMvc.perform(post(RUTA_LANES).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_LANE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("POOL_CAJA_NEGRA"));
    }

    @Test
    void editarDevuelveLaLaneActualizada() throws Exception {
        when(laneService.editar(anyLong(), anyLong(), anyLong(), any(EditarLaneDto.class), anyString()))
                .thenReturn(lane(12L, "Tesorero", 2));

        mockMvc.perform(put(RUTA_LANE).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_LANE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Tesorero"));

        verify(laneService).editar(eq(5L), eq(80L), eq(12L), any(EditarLaneDto.class), eq(USERNAME));
    }

    @Test
    void editarSinRolDeProcesoDevuelve400() throws Exception {
        mockMvc.perform(put(RUTA_LANE).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        verify(laneService, never()).editar(anyLong(), anyLong(), anyLong(), any(EditarLaneDto.class), anyString());
    }

    @Test
    void reordenarDevuelveLasLanesEnElNuevoOrden() throws Exception {
        when(laneService.reordenar(anyLong(), anyLong(), any(ReordenarLanesDto.class), anyString()))
                .thenReturn(List.of(lane(12L, "Analista de credito", 1), lane(11L, "General", 2)));

        mockMvc.perform(put(RUTA_LANES + "/orden").principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lanes":[12,11]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(12))
                .andExpect(jsonPath("$[1].orden").value(2));

        ArgumentCaptor<ReordenarLanesDto> capturado = ArgumentCaptor.forClass(ReordenarLanesDto.class);
        verify(laneService).reordenar(eq(5L), eq(80L), capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getLanes()).containsExactly(12L, 11L);
    }

    @Test
    void reordenarSinLanesDevuelve400() throws Exception {
        mockMvc.perform(put(RUTA_LANES + "/orden").principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lanes":[]}
                        """))
                .andExpect(status().isBadRequest());

        verify(laneService, never()).reordenar(anyLong(), anyLong(), any(ReordenarLanesDto.class), anyString());
    }

    @Test
    void reordenarConUnIdentificadorNuloDevuelve400() throws Exception {
        mockMvc.perform(put(RUTA_LANES + "/orden").principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lanes":[12,null]}
                        """))
                .andExpect(status().isBadRequest());

        verify(laneService, never()).reordenar(anyLong(), anyLong(), any(ReordenarLanesDto.class), anyString());
    }

    @Test
    void eliminarDevuelve204() throws Exception {
        mockMvc.perform(delete(RUTA_LANE).principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(laneService).eliminar(5L, 80L, 12L, USERNAME);
    }

    @Test
    void eliminarUnaLaneConActividadesDevuelve409() throws Exception {
        when(laneService.eliminar(5L, 80L, 12L, USERNAME))
                .thenThrow(new LaneConActividadesException("La lane tiene actividades activas"));

        mockMvc.perform(delete(RUTA_LANE).principal(PRINCIPAL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("LANE_CON_ACTIVIDADES"));
    }

    @Test
    void eliminarSinPermisoDeEstructuraDevuelve403() throws Exception {
        when(laneService.eliminar(5L, 80L, 12L, USERNAME))
                .thenThrow(new UsuarioSinPermisoException("El rol EDITOR no tiene permiso para eliminar lanes"));

        mockMvc.perform(delete(RUTA_LANE).principal(PRINCIPAL))
                .andExpect(status().isForbidden());
    }
}
