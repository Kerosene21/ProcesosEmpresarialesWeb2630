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

import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.dto.CrearPoolDto;
import co.edu.javeriana.procesosempresariales.dto.EditarPoolDto;
import co.edu.javeriana.procesosempresariales.dto.PoolRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.PoolConContenidoException;
import co.edu.javeriana.procesosempresariales.exception.PoolNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.service.PoolService;

@ExtendWith(MockitoExtension.class)
class PoolRestControllerTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA_POOLS = "/api/procesos/5/pools";
    private static final String RUTA_POOL = "/api/procesos/5/pools/90";

    private static final String JSON_CREACION = """
            {"nombre":"Cliente","tipo":"EXTERNO","cajaNegra":true}
            """;

    private static final String JSON_EDICION = """
            {"nombre":"Cliente corporativo","cajaNegra":false,"empresaParticipanteId":99}
            """;

    @Mock
    private PoolService poolService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PoolRestController(poolService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private PoolRespuestaDto pool(String nombre, TipoPool tipo, boolean cajaNegra) {
        PoolRespuestaDto pool = new PoolRespuestaDto();
        pool.setId(90L);
        pool.setProcesoId(5L);
        pool.setNombre(nombre);
        pool.setTipo(tipo);
        pool.setOrden(2);
        pool.setCajaNegra(cajaNegra);
        pool.setActivo(true);
        return pool;
    }

    @Test
    void listarDevuelveLosPoolsDelProceso() throws Exception {
        PoolRespuestaDto propietario = pool("Alpes Logistica", TipoPool.PROPIETARIO, false);
        propietario.setId(80L);
        propietario.setOrden(1);
        when(poolService.listar(5L, USERNAME))
                .thenReturn(List.of(propietario, pool("Cliente", TipoPool.EXTERNO, true)));

        mockMvc.perform(get(RUTA_POOLS).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("PROPIETARIO"))
                .andExpect(jsonPath("$[1].nombre").value("Cliente"))
                .andExpect(jsonPath("$[1].cajaNegra").value(true));
    }

    @Test
    void obtenerDevuelveElPoolSolicitado() throws Exception {
        when(poolService.obtener(5L, 90L, USERNAME)).thenReturn(pool("Cliente", TipoPool.EXTERNO, true));

        mockMvc.perform(get(RUTA_POOL).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(90))
                .andExpect(jsonPath("$.orden").value(2));
    }

    @Test
    void obtenerUnPoolInexistenteDevuelve404() throws Exception {
        when(poolService.obtener(5L, 90L, USERNAME)).thenThrow(new RecursoNoEncontradoException("El pool no existe"));

        mockMvc.perform(get(RUTA_POOL).principal(PRINCIPAL))
                .andExpect(status().isNotFound());
    }

    @Test
    void crearDevuelve201ConLaUbicacionDelPool() throws Exception {
        when(poolService.crear(anyLong(), any(CrearPoolDto.class), anyString()))
                .thenReturn(pool("Cliente", TipoPool.EXTERNO, true));

        mockMvc.perform(post(RUTA_POOLS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost" + RUTA_POOL))
                .andExpect(jsonPath("$.tipo").value("EXTERNO"));

        ArgumentCaptor<CrearPoolDto> capturado = ArgumentCaptor.forClass(CrearPoolDto.class);
        verify(poolService).crear(eq(5L), capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getNombre()).isEqualTo("Cliente");
        assertThat(capturado.getValue().getTipo()).isEqualTo(TipoPool.EXTERNO);
        assertThat(capturado.getValue().esCajaNegra()).isTrue();
        assertThat(capturado.getValue().getEmpresaParticipanteId()).isNull();
    }

    @Test
    void crearSinNombreDevuelve400YNoLlamaAlServicio() throws Exception {
        mockMvc.perform(post(RUTA_POOLS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"  ","tipo":"EXTERNO"}
                        """))
                .andExpect(status().isBadRequest());

        verify(poolService, never()).crear(anyLong(), any(CrearPoolDto.class), anyString());
    }

    @Test
    void crearSinTipoDevuelve400YNoLlamaAlServicio() throws Exception {
        mockMvc.perform(post(RUTA_POOLS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Cliente"}
                        """))
                .andExpect(status().isBadRequest());

        verify(poolService, never()).crear(anyLong(), any(CrearPoolDto.class), anyString());
    }

    @Test
    void crearUnSegundoPoolPropietarioDevuelve400() throws Exception {
        when(poolService.crear(anyLong(), any(CrearPoolDto.class), anyString()))
                .thenThrow(new PoolNoValidoException("El proceso ya tiene un pool propietario"));

        mockMvc.perform(post(RUTA_POOLS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Otra","tipo":"PROPIETARIO"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("POOL_NO_VALIDO"))
                .andExpect(jsonPath("$.mensaje").value("El proceso ya tiene un pool propietario"));
    }

    @Test
    void crearSinPermisoDeEstructuraDevuelve403() throws Exception {
        when(poolService.crear(anyLong(), any(CrearPoolDto.class), anyString()))
                .thenThrow(new UsuarioSinPermisoException("El rol SOLO_LECTURA no tiene permiso para crear pools"));

        mockMvc.perform(post(RUTA_POOLS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isForbidden());
    }

    @Test
    void editarDevuelveElPoolActualizado() throws Exception {
        when(poolService.editar(anyLong(), anyLong(), any(EditarPoolDto.class), anyString()))
                .thenReturn(pool("Cliente corporativo", TipoPool.PARTICIPANTE, false));

        mockMvc.perform(put(RUTA_POOL).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Cliente corporativo"));

        ArgumentCaptor<EditarPoolDto> capturado = ArgumentCaptor.forClass(EditarPoolDto.class);
        verify(poolService).editar(eq(5L), eq(90L), capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getNombre()).isEqualTo("Cliente corporativo");
        assertThat(capturado.getValue().esCajaNegra()).isFalse();
        assertThat(capturado.getValue().getEmpresaParticipanteId()).isEqualTo(99L);
    }

    @Test
    void editarSinNombreDevuelve400() throws Exception {
        mockMvc.perform(put(RUTA_POOL).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"cajaNegra":true}
                        """))
                .andExpect(status().isBadRequest());

        verify(poolService, never()).editar(anyLong(), anyLong(), any(EditarPoolDto.class), anyString());
    }

    @Test
    void eliminarDevuelve204() throws Exception {
        mockMvc.perform(delete(RUTA_POOL).principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(poolService).eliminar(5L, 90L, USERNAME);
    }

    @Test
    void eliminarUnPoolConContenidoDevuelve409() throws Exception {
        when(poolService.eliminar(5L, 90L, USERNAME))
                .thenThrow(new PoolConContenidoException("El pool tiene lanes activas"));

        mockMvc.perform(delete(RUTA_POOL).principal(PRINCIPAL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("POOL_CON_CONTENIDO"));
    }

    @Test
    void rolesDisponiblesDevuelveLosRolesDeProcesoActivosDeLaEmpresa() throws Exception {
        RolProcesoRespuestaDto rol = new RolProcesoRespuestaDto();
        rol.setId(40L);
        rol.setNombre("Analista de credito");
        rol.setActivo(true);
        when(poolService.rolesDisponibles(5L, 90L, USERNAME)).thenReturn(List.of(rol));

        mockMvc.perform(get(RUTA_POOL + "/roles-disponibles").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(40))
                .andExpect(jsonPath("$[0].nombre").value("Analista de credito"));
    }
    @Test
    void crearSinIndicarCajaNegraLlegaAlServicioComoPoolVisible() throws Exception {
        when(poolService.crear(anyLong(), any(CrearPoolDto.class), anyString()))
                .thenReturn(pool("Proveedor", TipoPool.PARTICIPANTE, false));

        mockMvc.perform(post(RUTA_POOLS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Proveedor","tipo":"PARTICIPANTE","empresaParticipanteId":99}
                        """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CrearPoolDto> capturado = ArgumentCaptor.forClass(CrearPoolDto.class);
        verify(poolService).crear(eq(5L), capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().esCajaNegra()).isFalse();
        assertThat(capturado.getValue().getEmpresaParticipanteId()).isEqualTo(99L);
    }
}
