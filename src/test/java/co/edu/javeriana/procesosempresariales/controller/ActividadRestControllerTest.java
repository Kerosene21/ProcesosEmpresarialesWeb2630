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

import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.EditarActividadDto;
import co.edu.javeriana.procesosempresariales.exception.LaneNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.NombreActividadDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.service.ActividadService;

@ExtendWith(MockitoExtension.class)
class ActividadRestControllerTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA_ACTIVIDADES = "/api/procesos/5/actividades";
    private static final String RUTA_ACTIVIDAD = "/api/procesos/5/actividades/30";

    private static final String JSON_CREACION = """
            {"nombre":"Revisar solicitud","tipo":"TAREA_USUARIO","laneId":11,"posicionX":120,"posicionY":40}
            """;

    private static final String JSON_EDICION = """
            {"nombre":"Validar solicitud","tipo":"TAREA_SISTEMA","laneId":12}
            """;

    @Mock
    private ActividadService actividadService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ActividadRestController(actividadService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private ActividadRespuestaDto actividadCreada() {
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

    private ActividadRespuestaDto actividadEditada() {
        ActividadRespuestaDto actividad = actividadCreada();
        actividad.setNombre("Validar solicitud");
        actividad.setTipo(TipoActividad.TAREA_SISTEMA);
        actividad.setLaneId(12L);
        actividad.setLaneNombre("Cartera");
        return actividad;
    }

    @Test
    void crearDevuelveDoscientosUnoConLaUbicacionDeLaNuevaActividad() throws Exception {
        when(actividadService.crear(anyLong(), any(CrearActividadDto.class), anyString()))
                .thenReturn(actividadCreada());

        mockMvc.perform(post(RUTA_ACTIVIDADES).principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost" + RUTA_ACTIVIDAD))
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.laneId").value(11))
                .andExpect(jsonPath("$.laneNombre").value("General"))
                .andExpect(jsonPath("$.posicionX").value(120))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void crearEntregaAlServicioElProcesoDeLaRutaYElUsuarioAutenticado() throws Exception {
        when(actividadService.crear(anyLong(), any(CrearActividadDto.class), anyString()))
                .thenReturn(actividadCreada());

        mockMvc.perform(post(RUTA_ACTIVIDADES).principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isCreated());

        ArgumentCaptor<CrearActividadDto> capturado = ArgumentCaptor.forClass(CrearActividadDto.class);
        verify(actividadService).crear(eq(5L), capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getNombre()).isEqualTo("Revisar solicitud");
        assertThat(capturado.getValue().getLaneId()).isEqualTo(11L);
    }

    @Test
    void crearRechazaUnCuerpoIncompletoConCuatrocientos() throws Exception {
        mockMvc.perform(post(RUTA_ACTIVIDADES).principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(actividadService, never()).crear(anyLong(), any(CrearActividadDto.class), anyString());
    }

    @Test
    void crearDevuelveCuatrocientosNueveCuandoElNombreYaExiste() throws Exception {
        when(actividadService.crear(anyLong(), any(CrearActividadDto.class), anyString()))
                .thenThrow(new NombreActividadDuplicadoException("Ya existe una actividad con ese nombre"));

        mockMvc.perform(post(RUTA_ACTIVIDADES).principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ACTIVIDAD_NOMBRE_DUPLICADO"));
    }

    @Test
    void crearDevuelveCuatrocientosCuandoLaLaneNoEsValida() throws Exception {
        when(actividadService.crear(anyLong(), any(CrearActividadDto.class), anyString()))
                .thenThrow(new LaneNoValidaException("La lane indicada no pertenece a este proceso"));

        mockMvc.perform(post(RUTA_ACTIVIDADES).principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("LANE_NO_VALIDA"));
    }

    @Test
    void crearDevuelveCuatrocientosTresCuandoElUsuarioNoTienePermiso() throws Exception {
        when(actividadService.crear(anyLong(), any(CrearActividadDto.class), anyString()))
                .thenThrow(new UsuarioSinPermisoException("Solo un administrador o editor puede crear actividades"));

        mockMvc.perform(post(RUTA_ACTIVIDADES).principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void crearDevuelveCuatrocientosCuatroCuandoElProcesoNoExisteOEstaEliminado() throws Exception {
        when(actividadService.crear(anyLong(), any(CrearActividadDto.class), anyString()))
                .thenThrow(new RecursoNoEncontradoException("El proceso ya fue eliminado"));

        mockMvc.perform(post(RUTA_ACTIVIDADES).principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("El proceso ya fue eliminado"));
    }

    @Test
    void editarDevuelveDoscientosConLaActividadActualizada() throws Exception {
        when(actividadService.editar(anyLong(), anyLong(), any(EditarActividadDto.class), anyString()))
                .thenReturn(actividadEditada());

        mockMvc.perform(put(RUTA_ACTIVIDAD).principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_EDICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Validar solicitud"))
                .andExpect(jsonPath("$.tipo").value("TAREA_SISTEMA"))
                .andExpect(jsonPath("$.laneId").value(12));
    }

    @Test
    void editarEntregaAlServicioLosIdentificadoresDeLaRuta() throws Exception {
        when(actividadService.editar(anyLong(), anyLong(), any(EditarActividadDto.class), anyString()))
                .thenReturn(actividadEditada());

        mockMvc.perform(put(RUTA_ACTIVIDAD).principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_EDICION))
                .andExpect(status().isOk());

        verify(actividadService).editar(eq(5L), eq(30L), any(EditarActividadDto.class), eq(USERNAME));
    }

    @Test
    void editarDevuelveCuatrocientosNueveCuandoElNombreYaExiste() throws Exception {
        when(actividadService.editar(anyLong(), anyLong(), any(EditarActividadDto.class), anyString()))
                .thenThrow(new NombreActividadDuplicadoException("Ya existe una actividad con ese nombre"));

        mockMvc.perform(put(RUTA_ACTIVIDAD).principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_EDICION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ACTIVIDAD_NOMBRE_DUPLICADO"));
    }

    @Test
    void editarDevuelveCuatrocientosCuatroCuandoLaActividadNoExiste() throws Exception {
        when(actividadService.editar(anyLong(), anyLong(), any(EditarActividadDto.class), anyString()))
                .thenThrow(new RecursoNoEncontradoException("La actividad no existe en este proceso"));

        mockMvc.perform(put(RUTA_ACTIVIDAD).principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_EDICION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void editarDevuelveCuatrocientosTresCuandoElUsuarioNoTienePermiso() throws Exception {
        when(actividadService.editar(anyLong(), anyLong(), any(EditarActividadDto.class), anyString()))
                .thenThrow(new UsuarioSinPermisoException("El proceso no pertenece a la empresa del usuario"));

        mockMvc.perform(put(RUTA_ACTIVIDAD).principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON).content(JSON_EDICION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void editarRechazaUnCuerpoIncompletoConCuatrocientos() throws Exception {
        mockMvc.perform(put(RUTA_ACTIVIDAD).principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(actividadService, never()).editar(anyLong(), anyLong(), any(EditarActividadDto.class), anyString());
    }

    @Test
    void eliminarDevuelveDoscientosCuatroSinCuerpo() throws Exception {
        mockMvc.perform(delete(RUTA_ACTIVIDAD).principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(actividadService).eliminar(5L, 30L, USERNAME);
    }

    @Test
    void eliminarDevuelveCuatrocientosTresCuandoElUsuarioNoEsAdministrador() throws Exception {
        when(actividadService.eliminar(anyLong(), anyLong(), anyString()))
                .thenThrow(new UsuarioSinPermisoException("Solo un administrador puede eliminar actividades"));

        mockMvc.perform(delete(RUTA_ACTIVIDAD).principal(PRINCIPAL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void eliminarDevuelveCuatrocientosCuatroCuandoLaActividadYaFueEliminada() throws Exception {
        when(actividadService.eliminar(anyLong(), anyLong(), anyString()))
                .thenThrow(new RecursoNoEncontradoException("La actividad ya fue eliminada"));

        mockMvc.perform(delete(RUTA_ACTIVIDAD).principal(PRINCIPAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("La actividad ya fue eliminada"));
    }
    @Test
    void listarDevuelveLasActividadesVigentesConSuPool() throws Exception {
        ActividadRespuestaDto actividad = actividadCreada();
        actividad.setPoolId(80L);
        when(actividadService.consultarActivas(5L, USERNAME)).thenReturn(List.of(actividad));

        mockMvc.perform(get(RUTA_ACTIVIDADES).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(30))
                .andExpect(jsonPath("$[0].poolId").value(80))
                .andExpect(jsonPath("$[0].laneNombre").value("General"));
    }

    @Test
    void obtenerDevuelveLaActividadSolicitada() throws Exception {
        when(actividadService.obtener(5L, 30L, USERNAME)).thenReturn(actividadCreada());

        mockMvc.perform(get(RUTA_ACTIVIDAD).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Revisar solicitud"));
    }

    @Test
    void listarLasActividadesDeUnProcesoAjenoNoCompartidoDevuelve403() throws Exception {
        when(actividadService.consultarActivas(5L, USERNAME))
                .thenThrow(new UsuarioSinPermisoException("El proceso no pertenece a la empresa del usuario"));

        mockMvc.perform(get(RUTA_ACTIVIDADES).principal(PRINCIPAL))
                .andExpect(status().isForbidden());
    }
}
