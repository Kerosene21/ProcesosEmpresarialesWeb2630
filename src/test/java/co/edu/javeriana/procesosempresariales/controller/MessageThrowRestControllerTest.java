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

import co.edu.javeriana.procesosempresariales.domain.ComportamientoSinCaso;
import co.edu.javeriana.procesosempresariales.dto.CrearMessageThrowDto;
import co.edu.javeriana.procesosempresariales.dto.EditarMessageThrowDto;
import co.edu.javeriana.procesosempresariales.dto.MessageThrowRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.MensajeEntreMismoPoolException;
import co.edu.javeriana.procesosempresariales.exception.PoolMensajeNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.service.MessageThrowService;

@ExtendWith(MockitoExtension.class)
class MessageThrowRestControllerTest {

    private static final String USERNAME = "editor@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA = "/api/procesos/5/message-throws";
    private static final String RUTA_THROW = RUTA + "/40";

    private static final String JSON_CREACION = """
            {"nombreMensaje":"Solicitud de pago","contenido":"numeroFactura: texto","poolOrigenId":80,
             "poolDestinoId":90,"claveCorrelacion":"factura","comportamientoSinCaso":"DESCARTAR",
             "posicionX":400,"posicionY":60}
            """;

    private static final String JSON_EDICION = """
            {"nombreMensaje":"Solicitud de pago","contenido":"numeroFactura: texto","poolDestinoId":91}
            """;

    @Mock
    private MessageThrowService messageThrowService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MessageThrowRestController(messageThrowService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private MessageThrowRespuestaDto messageThrow() {
        MessageThrowRespuestaDto messageThrow = new MessageThrowRespuestaDto();
        messageThrow.setId(40L);
        messageThrow.setProcesoId(5L);
        messageThrow.setNombreMensaje("Solicitud de pago");
        messageThrow.setPoolOrigenId(80L);
        messageThrow.setPoolDestinoId(90L);
        messageThrow.setPoolDestinoNombre("Banco");
        messageThrow.setClaveCorrelacion("factura");
        messageThrow.setComportamientoSinCaso(ComportamientoSinCaso.DESCARTAR);
        messageThrow.setActivo(true);
        return messageThrow;
    }

    @Test
    void listarDevuelveLosMessageThrowDelProceso() throws Exception {
        when(messageThrowService.listar(5L, USERNAME)).thenReturn(List.of(messageThrow()));

        mockMvc.perform(get(RUTA).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(40))
                .andExpect(jsonPath("$[0].poolDestinoNombre").value("Banco"));
    }

    @Test
    void obtenerDevuelveElMessageThrow() throws Exception {
        when(messageThrowService.obtener(5L, 40L, USERNAME)).thenReturn(messageThrow());

        mockMvc.perform(get(RUTA_THROW).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claveCorrelacion").value("factura"))
                .andExpect(jsonPath("$.comportamientoSinCaso").value("DESCARTAR"));
    }

    @Test
    void crearDevuelve201ConLaUbicacionYLasAdvertencias() throws Exception {
        MessageThrowRespuestaDto creado = messageThrow();
        creado.setAdvertencias(List.of("No existe un Message Catch 'Solicitud de pago' en el pool destino 'Banco'"));
        when(messageThrowService.crear(eq(5L), any(CrearMessageThrowDto.class), eq(USERNAME))).thenReturn(creado);

        mockMvc.perform(post(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/procesos/5/message-throws/40"))
                .andExpect(jsonPath("$.advertencias[0]")
                        .value("No existe un Message Catch 'Solicitud de pago' en el pool destino 'Banco'"));

        ArgumentCaptor<CrearMessageThrowDto> capturado = ArgumentCaptor.forClass(CrearMessageThrowDto.class);
        verify(messageThrowService).crear(eq(5L), capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getPoolDestinoId()).isEqualTo(90L);
        assertThat(capturado.getValue().getComportamientoSinCaso()).isEqualTo(ComportamientoSinCaso.DESCARTAR);
    }

    @Test
    void crearDevuelve400CuandoFaltaElNombre() throws Exception {
        mockMvc.perform(post(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON).content("""
                {"contenido":"x","poolOrigenId":80,"poolDestinoId":90,"posicionX":1,"posicionY":1}
                """))
                .andExpect(status().isBadRequest());

        verify(messageThrowService, never()).crear(anyLong(), any(CrearMessageThrowDto.class), anyString());
    }

    @Test
    void crearDevuelve400CuandoOrigenYDestinoSonElMismoPool() throws Exception {
        when(messageThrowService.crear(eq(5L), any(CrearMessageThrowDto.class), eq(USERNAME)))
                .thenThrow(new MensajeEntreMismoPoolException("El pool 'Alpes' es a la vez origen y destino"));

        mockMvc.perform(post(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("MENSAJE_ENTRE_MISMO_POOL"));
    }

    @Test
    void crearDevuelve400CuandoElPoolDestinoNoEsDelDiagrama() throws Exception {
        when(messageThrowService.crear(eq(5L), any(CrearMessageThrowDto.class), eq(USERNAME)))
                .thenThrow(new PoolMensajeNoValidoException("El pool destino no es del diagrama"));

        mockMvc.perform(post(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("POOL_MENSAJE_NO_VALIDO"));
    }

    @Test
    void crearDevuelve403CuandoElRolNoTienePermiso() throws Exception {
        when(messageThrowService.crear(eq(5L), any(CrearMessageThrowDto.class), eq(USERNAME)))
                .thenThrow(new UsuarioSinPermisoException("Solo un administrador o editor puede crear o modificar"
                        + " Message Throw"));

        mockMvc.perform(post(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void editarDevuelve200ConElMessageThrowActualizado() throws Exception {
        when(messageThrowService.editar(eq(5L), eq(40L), any(EditarMessageThrowDto.class), eq(USERNAME)))
                .thenReturn(messageThrow());

        mockMvc.perform(put(RUTA_THROW).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(40));
    }

    @Test
    void editarDevuelve400CuandoElCuerpoEstaIncompleto() throws Exception {
        mockMvc.perform(put(RUTA_THROW).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        verify(messageThrowService, never()).editar(anyLong(), anyLong(), any(EditarMessageThrowDto.class),
                anyString());
    }

    @Test
    void laConsultaPreviaDeEliminacionDevuelveLasAdvertencias() throws Exception {
        MessageThrowRespuestaDto previa = messageThrow();
        previa.setAdvertencias(List.of("Se desactivará 1 arco conectado a Message Throw: Solicitud de pago."));
        when(messageThrowService.obtenerParaEliminar(5L, 40L, USERNAME)).thenReturn(previa);

        mockMvc.perform(get(RUTA_THROW + "/eliminacion").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advertencias[0]")
                        .value("Se desactivará 1 arco conectado a Message Throw: Solicitud de pago."));
    }

    @Test
    void eliminarDevuelve204() throws Exception {
        mockMvc.perform(delete(RUTA_THROW).principal(PRINCIPAL)).andExpect(status().isNoContent());

        verify(messageThrowService).eliminar(5L, 40L, USERNAME);
    }

    @Test
    void unaSegundaEliminacionDevuelve404() throws Exception {
        when(messageThrowService.eliminar(5L, 40L, USERNAME))
                .thenThrow(new RecursoNoEncontradoException("El Message Throw ya fue eliminado"));

        mockMvc.perform(delete(RUTA_THROW).principal(PRINCIPAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }
}
