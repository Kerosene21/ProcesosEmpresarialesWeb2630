package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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

import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.dto.CrearGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.EditarGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.CondicionArcoNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.service.GatewayService;

@ExtendWith(MockitoExtension.class)
class GatewayRestControllerTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA_GATEWAYS = "/api/procesos/5/gateways";
    private static final String RUTA_GATEWAY = "/api/procesos/5/gateways/12";

    private static final String JSON_CREACION = """
            {"tipo":"EXCLUSIVO","posicionX":300,"posicionY":120}
            """;

    private static final String JSON_EDICION = """
            {"tipo":"PARALELO","condiciones":{}}
            """;

    private static final String JSON_INCOMPLETO = """
            {"posicionX":300}
            """;

    @Mock
    private GatewayService gatewayService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GatewayRestController(gatewayService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private GatewayRespuestaDto gateway(TipoGateway tipo) {
        GatewayRespuestaDto gateway = new GatewayRespuestaDto();
        gateway.setId(12L);
        gateway.setProcesoId(5L);
        gateway.setTipo(tipo);
        gateway.setSimbolo(tipo.getSimbolo());
        gateway.setEtiqueta("Gateway " + tipo + " #12");
        gateway.setPosicionX(300);
        gateway.setPosicionY(120);
        gateway.setActivo(true);
        return gateway;
    }

    private CrearGatewayDto formularioRecibido() {
        ArgumentCaptor<CrearGatewayDto> capturado = ArgumentCaptor.forClass(CrearGatewayDto.class);
        verify(gatewayService).crear(eq(5L), capturado.capture(), eq(USERNAME));
        return capturado.getValue();
    }

    @Test
    void crearDevuelve201ConLaUbicacionDelGateway() throws Exception {
        GatewayRespuestaDto creado = gateway(TipoGateway.EXCLUSIVO);
        creado.setAdvertencias(List.of("Gateway EXCLUSIVO #12 tiene 0 arcos de salida"));
        when(gatewayService.crear(eq(5L), any(CrearGatewayDto.class), eq(USERNAME))).thenReturn(creado);

        mockMvc.perform(post(RUTA_GATEWAYS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/procesos/5/gateways/12"))
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.simbolo").value("X"))
                .andExpect(jsonPath("$.advertencias[0]").value("Gateway EXCLUSIVO #12 tiene 0 arcos de salida"));

        assertThat(formularioRecibido().getPosicionX()).isEqualTo(300);
    }

    @Test
    void crearDevuelve400CuandoElCuerpoEstaIncompleto() throws Exception {
        mockMvc.perform(post(RUTA_GATEWAYS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_INCOMPLETO))
                .andExpect(status().isBadRequest());

        verify(gatewayService, never()).crear(anyLong(), any(CrearGatewayDto.class), anyString());
    }

    @Test
    void crearDevuelve403CuandoElRolNoTienePermiso() throws Exception {
        when(gatewayService.crear(eq(5L), any(CrearGatewayDto.class), eq(USERNAME)))
                .thenThrow(new UsuarioSinPermisoException("Solo un administrador o editor puede crear o modificar"
                        + " gateways"));

        mockMvc.perform(post(RUTA_GATEWAYS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void crearDevuelve404CuandoElProcesoFueEliminado() throws Exception {
        when(gatewayService.crear(eq(5L), any(CrearGatewayDto.class), eq(USERNAME)))
                .thenThrow(new RecursoNoEncontradoException("El proceso ya fue eliminado"));

        mockMvc.perform(post(RUTA_GATEWAYS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void editarDevuelve200ConElGatewayActualizado() throws Exception {
        when(gatewayService.editar(eq(5L), eq(12L), any(EditarGatewayDto.class), eq(USERNAME)))
                .thenReturn(gateway(TipoGateway.PARALELO));

        mockMvc.perform(put(RUTA_GATEWAY).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("PARALELO"))
                .andExpect(jsonPath("$.simbolo").value("+"));
    }

    @Test
    void editarDevuelve400CuandoFaltaElTipo() throws Exception {
        mockMvc.perform(put(RUTA_GATEWAY).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_INCOMPLETO))
                .andExpect(status().isBadRequest());

        verify(gatewayService, never()).editar(anyLong(), anyLong(), any(EditarGatewayDto.class), anyString());
    }

    @Test
    void editarDevuelve400CuandoUnaSalidaSeQuedaSinCondicion() throws Exception {
        when(gatewayService.editar(eq(5L), eq(12L), any(EditarGatewayDto.class), eq(USERNAME)))
                .thenThrow(new CondicionArcoNoValidaException("Cada arco de salida de un gateway exclusivo o"
                        + " inclusivo necesita condición"));

        mockMvc.perform(put(RUTA_GATEWAY).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CONDICION_NO_VALIDA"));
    }

    @Test
    void editarDevuelve404CuandoElGatewayNoExiste() throws Exception {
        when(gatewayService.editar(eq(5L), eq(12L), any(EditarGatewayDto.class), eq(USERNAME)))
                .thenThrow(new RecursoNoEncontradoException("El gateway no existe en este proceso"));

        mockMvc.perform(put(RUTA_GATEWAY).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }
}
