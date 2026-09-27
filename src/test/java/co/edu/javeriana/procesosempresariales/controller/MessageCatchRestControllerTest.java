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

import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
import co.edu.javeriana.procesosempresariales.dto.CrearMessageCatchDto;
import co.edu.javeriana.procesosempresariales.dto.EditarMessageCatchDto;
import co.edu.javeriana.procesosempresariales.dto.MessageCatchRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.CatchInicioConEntradaException;
import co.edu.javeriana.procesosempresariales.exception.CorrelacionNoValidaException;
import co.edu.javeriana.procesosempresariales.service.MessageCatchService;

@ExtendWith(MockitoExtension.class)
class MessageCatchRestControllerTest {

    private static final String USERNAME = "editor@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA = "/api/procesos/5/message-catches";
    private static final String RUTA_CATCH = RUTA + "/41";

    private static final String JSON_CREACION = """
            {"nombreMensaje":"Pago confirmado","variante":"INTERMEDIO","datosEsperados":"numeroFactura: texto",
             "actividadesUso":"Registrar pago","origenExterno":true,"claveCorrelacion":"factura",
             "comportamientoSinCaso":"DESCARTAR","poolId":80,"posicionX":40,"posicionY":60}
            """;

    private static final String JSON_EDICION = """
            {"nombreMensaje":"Pago confirmado","variante":"INICIO","datosEsperados":"numeroFactura: texto",
             "actividadesUso":"Registrar pago"}
            """;

    @Mock
    private MessageCatchService messageCatchService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MessageCatchRestController(messageCatchService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private MessageCatchRespuestaDto messageCatch() {
        MessageCatchRespuestaDto messageCatch = new MessageCatchRespuestaDto();
        messageCatch.setId(41L);
        messageCatch.setProcesoId(5L);
        messageCatch.setNombreMensaje("Pago confirmado");
        messageCatch.setVariante(VarianteMessageCatch.INTERMEDIO);
        messageCatch.setPoolId(80L);
        messageCatch.setOrigenExterno(true);
        messageCatch.setActivo(true);
        return messageCatch;
    }

    @Test
    void listarYObtenerDevuelvenLosMessageCatch() throws Exception {
        when(messageCatchService.listar(5L, USERNAME)).thenReturn(List.of(messageCatch()));
        when(messageCatchService.obtener(5L, 41L, USERNAME)).thenReturn(messageCatch());

        mockMvc.perform(get(RUTA).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].variante").value("INTERMEDIO"));
        mockMvc.perform(get(RUTA_CATCH).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origenExterno").value(true));
    }

    @Test
    void crearDevuelve201ConLaUbicacionDelCatch() throws Exception {
        when(messageCatchService.crear(eq(5L), any(CrearMessageCatchDto.class), eq(USERNAME)))
                .thenReturn(messageCatch());

        mockMvc.perform(post(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/procesos/5/message-catches/41"));

        ArgumentCaptor<CrearMessageCatchDto> capturado = ArgumentCaptor.forClass(CrearMessageCatchDto.class);
        verify(messageCatchService).crear(eq(5L), capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().esOrigenExterno()).isTrue();
        assertThat(capturado.getValue().getVariante()).isEqualTo(VarianteMessageCatch.INTERMEDIO);
    }

    @Test
    void crearDevuelve400SinVariante() throws Exception {
        mockMvc.perform(post(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON).content("""
                {"nombreMensaje":"Pago","datosEsperados":"x","actividadesUso":"y","posicionX":1,"posicionY":1}
                """))
                .andExpect(status().isBadRequest());

        verify(messageCatchService, never()).crear(anyLong(), any(CrearMessageCatchDto.class), anyString());
    }

    @Test
    void crearDevuelve400CuandoUnInicioQuiereDescartarMensajes() throws Exception {
        when(messageCatchService.crear(eq(5L), any(CrearMessageCatchDto.class), eq(USERNAME)))
                .thenThrow(new CorrelacionNoValidaException("Un Message Catch de inicio siempre inicia un caso"));

        mockMvc.perform(post(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CORRELACION_NO_VALIDA"));
    }

    @Test
    void editarDevuelve400CuandoElInicioTendriaEntradas() throws Exception {
        when(messageCatchService.editar(eq(5L), eq(41L), any(EditarMessageCatchDto.class), eq(USERNAME)))
                .thenThrow(new CatchInicioConEntradaException("El Message Catch tiene flujos entrantes"));

        mockMvc.perform(put(RUTA_CATCH).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CATCH_INICIO_CON_ENTRADA"));
    }

    @Test
    void editarDevuelve200ConElCatchActualizado() throws Exception {
        when(messageCatchService.editar(eq(5L), eq(41L), any(EditarMessageCatchDto.class), eq(USERNAME)))
                .thenReturn(messageCatch());

        mockMvc.perform(put(RUTA_CATCH).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(41));
    }

    @Test
    void laConsultaPreviaYLaEliminacionSiguenElPatronDeConfirmacion() throws Exception {
        MessageCatchRespuestaDto previa = messageCatch();
        previa.setAdvertencias(List.of("El Message Throw 'Pago confirmado' quedará sin Message Catch homólogo"));
        when(messageCatchService.obtenerParaEliminar(5L, 41L, USERNAME)).thenReturn(previa);

        mockMvc.perform(get(RUTA_CATCH + "/eliminacion").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advertencias[0]")
                        .value("El Message Throw 'Pago confirmado' quedará sin Message Catch homólogo"));
        mockMvc.perform(delete(RUTA_CATCH).principal(PRINCIPAL)).andExpect(status().isNoContent());

        verify(messageCatchService).eliminar(5L, 41L, USERNAME);
    }
}
