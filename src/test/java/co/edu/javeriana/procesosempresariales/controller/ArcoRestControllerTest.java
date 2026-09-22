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

import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.dto.ArcoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearArcoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarArcoDto;
import co.edu.javeriana.procesosempresariales.exception.ArcoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.CondicionArcoNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.NodoFlujoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.service.ArcoService;

@ExtendWith(MockitoExtension.class)
class ArcoRestControllerTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA_ARCOS = "/api/procesos/5/arcos";
    private static final String RUTA_ARCO = "/api/procesos/5/arcos/60";

    private static final String JSON_CREACION = """
            {"origenTipo":"ACTIVIDAD","origenId":30,"destinoTipo":"ACTIVIDAD","destinoId":31,
             "etiqueta":"solicitud completa"}
            """;

    private static final String JSON_EDICION = """
            {"origenTipo":"ACTIVIDAD","origenId":30,"destinoTipo":"ACTIVIDAD","destinoId":32}
            """;

    private static final String JSON_INCOMPLETO = """
            {"origenTipo":"ACTIVIDAD","destinoTipo":"ACTIVIDAD","destinoId":31}
            """;

    @Mock
    private ArcoService arcoService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ArcoRestController(arcoService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private ArcoRespuestaDto arcoCreado() {
        ArcoRespuestaDto arco = new ArcoRespuestaDto();
        arco.setId(60L);
        arco.setProcesoId(5L);
        arco.setOrigenTipo(TipoNodoFlujo.ACTIVIDAD);
        arco.setOrigenId(30L);
        arco.setOrigenNombre("Revisar solicitud");
        arco.setDestinoTipo(TipoNodoFlujo.ACTIVIDAD);
        arco.setDestinoId(31L);
        arco.setDestinoNombre("Aprobar solicitud");
        arco.setEtiqueta("solicitud completa");
        arco.setActivo(true);
        return arco;
    }

    private CrearArcoDto formularioRecibido() {
        ArgumentCaptor<CrearArcoDto> capturado = ArgumentCaptor.forClass(CrearArcoDto.class);
        verify(arcoService).crear(eq(5L), capturado.capture(), eq(USERNAME));
        return capturado.getValue();
    }

    @Test
    void crearDevuelve201ConLaUbicacionDelArco() throws Exception {
        when(arcoService.crear(eq(5L), any(CrearArcoDto.class), eq(USERNAME))).thenReturn(arcoCreado());

        mockMvc.perform(post(RUTA_ARCOS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/procesos/5/arcos/60"))
                .andExpect(jsonPath("$.id").value(60))
                .andExpect(jsonPath("$.origenNombre").value("Revisar solicitud"))
                .andExpect(jsonPath("$.destinoNombre").value("Aprobar solicitud"));

        assertThat(formularioRecibido().getOrigenId()).isEqualTo(30L);
    }

    @Test
    void crearDevuelve400CuandoElCuerpoEstaIncompleto() throws Exception {
        mockMvc.perform(post(RUTA_ARCOS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_INCOMPLETO))
                .andExpect(status().isBadRequest());

        verify(arcoService, never()).crear(anyLong(), any(CrearArcoDto.class), anyString());
    }

    @Test
    void crearDevuelve400CuandoElNodoNoEsValido() throws Exception {
        when(arcoService.crear(eq(5L), any(CrearArcoDto.class), eq(USERNAME)))
                .thenThrow(new NodoFlujoNoValidoException("El nodo indicado no existe en este proceso"));

        mockMvc.perform(post(RUTA_ARCOS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NODO_NO_VALIDO"));
    }

    @Test
    void crearDevuelve400CuandoLaCondicionNoEsValida() throws Exception {
        when(arcoService.crear(eq(5L), any(CrearArcoDto.class), eq(USERNAME)))
                .thenThrow(new CondicionArcoNoValidaException("Solo los arcos que salen de un gateway llevan"
                        + " condición"));

        mockMvc.perform(post(RUTA_ARCOS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CONDICION_NO_VALIDA"));
    }

    @Test
    void crearDevuelve409CuandoElArcoEstaDuplicado() throws Exception {
        when(arcoService.crear(eq(5L), any(CrearArcoDto.class), eq(USERNAME)))
                .thenThrow(new ArcoDuplicadoException("Ya existe un arco activo entre ese origen y ese destino"));

        mockMvc.perform(post(RUTA_ARCOS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ARCO_DUPLICADO"));
    }

    @Test
    void crearDevuelve403CuandoElRolNoTienePermiso() throws Exception {
        when(arcoService.crear(eq(5L), any(CrearArcoDto.class), eq(USERNAME)))
                .thenThrow(new UsuarioSinPermisoException("Solo un administrador o editor puede crear o modificar"
                        + " arcos"));

        mockMvc.perform(post(RUTA_ARCOS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void crearDevuelve404CuandoElProcesoNoExiste() throws Exception {
        when(arcoService.crear(eq(5L), any(CrearArcoDto.class), eq(USERNAME)))
                .thenThrow(new RecursoNoEncontradoException("El proceso no existe"));

        mockMvc.perform(post(RUTA_ARCOS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void editarDevuelve200ConElArcoActualizado() throws Exception {
        ArcoRespuestaDto editado = arcoCreado();
        editado.setDestinoId(32L);
        editado.setDestinoNombre("Archivar solicitud");
        when(arcoService.editar(eq(5L), eq(60L), any(EditarArcoDto.class), eq(USERNAME))).thenReturn(editado);

        mockMvc.perform(put(RUTA_ARCO).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(60))
                .andExpect(jsonPath("$.destinoNombre").value("Archivar solicitud"));
    }

    @Test
    void editarDevuelve400CuandoElCuerpoEstaIncompleto() throws Exception {
        mockMvc.perform(put(RUTA_ARCO).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_INCOMPLETO))
                .andExpect(status().isBadRequest());

        verify(arcoService, never()).editar(anyLong(), anyLong(), any(EditarArcoDto.class), anyString());
    }

    @Test
    void editarDevuelve404CuandoElArcoNoExiste() throws Exception {
        when(arcoService.editar(eq(5L), eq(60L), any(EditarArcoDto.class), eq(USERNAME)))
                .thenThrow(new RecursoNoEncontradoException("El arco no existe en este proceso"));

        mockMvc.perform(put(RUTA_ARCO).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void eliminarDevuelve204SinCuerpo() throws Exception {
        ArcoRespuestaDto eliminado = arcoCreado();
        eliminado.setActivo(false);
        eliminado.setAdvertencias(List.of("'Revisar solicitud' quedó sin arcos de salida"));
        when(arcoService.eliminar(5L, 60L, USERNAME)).thenReturn(eliminado);

        mockMvc.perform(delete(RUTA_ARCO).principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(arcoService).eliminar(5L, 60L, USERNAME);
    }

    @Test
    void eliminarDevuelve403CuandoElRolNoEsAdministrador() throws Exception {
        when(arcoService.eliminar(5L, 60L, USERNAME))
                .thenThrow(new UsuarioSinPermisoException("Solo un administrador puede eliminar arcos"));

        mockMvc.perform(delete(RUTA_ARCO).principal(PRINCIPAL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void eliminarDevuelve404CuandoElArcoYaFueEliminado() throws Exception {
        when(arcoService.eliminar(5L, 60L, USERNAME))
                .thenThrow(new RecursoNoEncontradoException("El arco ya fue eliminado"));

        mockMvc.perform(delete(RUTA_ARCO).principal(PRINCIPAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }
}
