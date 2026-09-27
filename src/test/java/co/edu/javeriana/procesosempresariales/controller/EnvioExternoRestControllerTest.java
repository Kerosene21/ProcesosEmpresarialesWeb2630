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

import co.edu.javeriana.procesosempresariales.domain.ComportamientoFallo;
import co.edu.javeriana.procesosempresariales.domain.TipoDestinoExterno;
import co.edu.javeriana.procesosempresariales.dto.CrearEnvioExternoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarEnvioExternoDto;
import co.edu.javeriana.procesosempresariales.dto.EnvioExternoRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.EnvioExternoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.PoolCajaNegraException;
import co.edu.javeriana.procesosempresariales.service.EnvioExternoService;

@ExtendWith(MockitoExtension.class)
class EnvioExternoRestControllerTest {

    private static final String USERNAME = "editor@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA = "/api/procesos/5/envios-externos";
    private static final String RUTA_ENVIO = RUTA + "/42";

    private static final String JSON_CREACION = """
            {"nombreMensaje":"Notificar despacho","poolOrigenId":80,"poolDestinoId":90,"tipoDestino":"COLA",
             "datosEnviados":"numeroGuia: texto","momentoProceso":"Al confirmar el despacho",
             "comportamientoFallo":"RUTA_ERROR","posicionX":700,"posicionY":80}
            """;

    private static final String JSON_EDICION = """
            {"nombreMensaje":"Notificar despacho","poolDestinoId":90,"tipoDestino":"CORREO",
             "datosEnviados":"numeroGuia: texto","momentoProceso":"Al confirmar el despacho",
             "comportamientoFallo":"FINALIZAR"}
            """;

    @Mock
    private EnvioExternoService envioExternoService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EnvioExternoRestController(envioExternoService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private EnvioExternoRespuestaDto envio() {
        EnvioExternoRespuestaDto envio = new EnvioExternoRespuestaDto();
        envio.setId(42L);
        envio.setProcesoId(5L);
        envio.setNombreMensaje("Notificar despacho");
        envio.setPoolOrigenId(80L);
        envio.setPoolDestinoId(90L);
        envio.setTipoDestino(TipoDestinoExterno.COLA);
        envio.setDatosEnviados("numeroGuia: texto");
        envio.setMomentoProceso("Al confirmar el despacho");
        envio.setComportamientoFallo(ComportamientoFallo.RUTA_ERROR);
        envio.setActivo(true);
        return envio;
    }

    @Test
    void listarYObtenerDevuelvenLosEnviosDocumentados() throws Exception {
        when(envioExternoService.listar(5L, USERNAME)).thenReturn(List.of(envio()));
        when(envioExternoService.obtener(5L, 42L, USERNAME)).thenReturn(envio());

        mockMvc.perform(get(RUTA).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipoDestino").value("COLA"));
        mockMvc.perform(get(RUTA_ENVIO).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comportamientoFallo").value("RUTA_ERROR"))
                .andExpect(jsonPath("$.momentoProceso").value("Al confirmar el despacho"));
    }

    @Test
    void crearDevuelve201ConLaUbicacionDelEnvio() throws Exception {
        when(envioExternoService.crear(eq(5L), any(CrearEnvioExternoDto.class), eq(USERNAME))).thenReturn(envio());

        mockMvc.perform(post(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/procesos/5/envios-externos/42"));

        ArgumentCaptor<CrearEnvioExternoDto> capturado = ArgumentCaptor.forClass(CrearEnvioExternoDto.class);
        verify(envioExternoService).crear(eq(5L), capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getTipoDestino()).isEqualTo(TipoDestinoExterno.COLA);
        assertThat(capturado.getValue().getComportamientoFallo()).isEqualTo(ComportamientoFallo.RUTA_ERROR);
    }

    @Test
    void crearDevuelve400ConUnTipoDeDestinoDesconocido() throws Exception {
        mockMvc.perform(post(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION.replace("COLA", "FAX")))
                .andExpect(status().isBadRequest());

        verify(envioExternoService, never()).crear(anyLong(), any(CrearEnvioExternoDto.class), anyString());
    }

    @Test
    void crearDevuelve400CuandoElDestinoNoEsUnSistemaExterno() throws Exception {
        when(envioExternoService.crear(eq(5L), any(CrearEnvioExternoDto.class), eq(USERNAME)))
                .thenThrow(new EnvioExternoNoValidoException("El pool 'Alpes' es de tipo PROPIETARIO"));

        mockMvc.perform(post(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ENVIO_EXTERNO_NO_VALIDO"));
    }

    @Test
    void crearDevuelve409CuandoElOrigenEsUnaCajaNegra() throws Exception {
        when(envioExternoService.crear(eq(5L), any(CrearEnvioExternoDto.class), eq(USERNAME)))
                .thenThrow(new PoolCajaNegraException("El pool 'Pasarela' es una caja negra"));

        mockMvc.perform(post(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON).content(JSON_CREACION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("POOL_CAJA_NEGRA"));
    }

    @Test
    void editarDevuelve200() throws Exception {
        when(envioExternoService.editar(eq(5L), eq(42L), any(EditarEnvioExternoDto.class), eq(USERNAME)))
                .thenReturn(envio());

        mockMvc.perform(put(RUTA_ENVIO).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void laConsultaPreviaYLaEliminacionSiguenElPatronDeConfirmacion() throws Exception {
        when(envioExternoService.obtenerParaEliminar(5L, 42L, USERNAME)).thenReturn(envio());

        mockMvc.perform(get(RUTA_ENVIO + "/eliminacion").principal(PRINCIPAL)).andExpect(status().isOk());
        mockMvc.perform(delete(RUTA_ENVIO).principal(PRINCIPAL)).andExpect(status().isNoContent());

        verify(envioExternoService).eliminar(5L, 42L, USERNAME);
    }
}
