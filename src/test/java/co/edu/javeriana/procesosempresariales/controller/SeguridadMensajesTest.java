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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
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
import co.edu.javeriana.procesosempresariales.dto.CrearEnvioExternoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearMessageThrowDto;
import co.edu.javeriana.procesosempresariales.dto.DiagramaProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarMessageCatchDto;
import co.edu.javeriana.procesosempresariales.dto.MessageThrowRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.DiagramaProcesoService;
import co.edu.javeriana.procesosempresariales.service.EnvioExternoService;
import co.edu.javeriana.procesosempresariales.service.MessageCatchService;
import co.edu.javeriana.procesosempresariales.service.MessageThrowService;

@WebMvcTest(controllers = { MessageThrowRestController.class, MessageCatchRestController.class,
        EnvioExternoRestController.class, DiagramaProcesoRestController.class, AutenticacionController.class })
@Import(SecurityConfig.class)
class SeguridadMensajesTest {

    private static final String USERNAME = "usuario@alpes.com";
    private static final String RUTA_THROWS = "/api/procesos/5/message-throws";
    private static final String RUTA_THROW = RUTA_THROWS + "/40";
    private static final String RUTA_CATCH = "/api/procesos/5/message-catches/41";
    private static final String RUTA_ENVIOS = "/api/procesos/5/envios-externos";
    private static final String RUTA_ENVIO = RUTA_ENVIOS + "/42";
    private static final String RUTA_DIAGRAMA = "/api/procesos/5/diagrama";
    private static final String CODIGO = "$.codigo";

    private static final String JSON_THROW = """
            {"nombreMensaje":"Solicitud de pago","contenido":"numeroFactura: texto","poolOrigenId":80,
             "poolDestinoId":90,"posicionX":400,"posicionY":60}
            """;

    private static final String JSON_CATCH = """
            {"nombreMensaje":"Pago confirmado","variante":"INTERMEDIO","datosEsperados":"numeroFactura: texto",
             "actividadesUso":"Registrar pago"}
            """;

    private static final String JSON_ENVIO = """
            {"nombreMensaje":"Notificar despacho","poolOrigenId":80,"poolDestinoId":90,"tipoDestino":"CORREO",
             "datosEnviados":"numeroGuia: texto","momentoProceso":"Al despachar","comportamientoFallo":"CONTINUAR",
             "posicionX":700,"posicionY":80}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageThrowService messageThrowService;

    @MockitoBean
    private MessageCatchService messageCatchService;

    @MockitoBean
    private EnvioExternoService envioExternoService;

    @MockitoBean
    private DiagramaProcesoService diagramaProcesoService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private MessageThrowRespuestaDto messageThrow() {
        MessageThrowRespuestaDto messageThrow = new MessageThrowRespuestaDto();
        messageThrow.setId(40L);
        messageThrow.setProcesoId(5L);
        return messageThrow;
    }

    @Test
    void laConsultaDeMensajesResponde401SinSesion() throws Exception {
        mockMvc.perform(get(RUTA_THROWS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value("USUARIO_NO_AUTORIZADO"));

        verify(messageThrowService, never()).listar(anyLong(), anyString());
    }

    @Test
    void elDiagramaResponde401SinSesion() throws Exception {
        mockMvc.perform(get(RUTA_DIAGRAMA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value("USUARIO_NO_AUTORIZADO"));

        verify(diagramaProcesoService, never()).obtener(anyLong(), anyString());
    }

    @Test
    void laCreacionYLaEliminacionResponden401SinSesion() throws Exception {
        mockMvc.perform(post(RUTA_THROWS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON_THROW))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(RUTA_ENVIO).with(csrf())).andExpect(status().isUnauthorized());

        verify(messageThrowService, never()).crear(anyLong(), any(CrearMessageThrowDto.class), anyString());
        verify(envioExternoService, never()).eliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaConsultaMensajesYDiagrama() throws Exception {
        when(messageThrowService.listar(5L, USERNAME)).thenReturn(List.of(messageThrow()));
        when(diagramaProcesoService.obtener(5L, USERNAME)).thenReturn(new DiagramaProcesoDto());

        mockMvc.perform(get(RUTA_THROWS)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(40));
        mockMvc.perform(get(RUTA_DIAGRAMA)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoCreaMensajesAunqueEnvieElTokenCsrf() throws Exception {
        mockMvc.perform(post(RUTA_THROWS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON_THROW))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));

        verify(messageThrowService, never()).crear(anyLong(), any(CrearMessageThrowDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoEditaCatchesNiDocumentaEnvios() throws Exception {
        mockMvc.perform(put(RUTA_CATCH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON_CATCH))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(RUTA_ENVIOS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON_ENVIO))
                .andExpect(status().isForbidden());

        verify(messageCatchService, never()).editar(anyLong(), anyLong(), any(EditarMessageCatchDto.class),
                anyString());
        verify(envioExternoService, never()).crear(anyLong(), any(CrearEnvioExternoDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorCreaMessageThrow() throws Exception {
        when(messageThrowService.crear(eq(5L), any(CrearMessageThrowDto.class), eq(USERNAME)))
                .thenReturn(messageThrow());

        mockMvc.perform(post(RUTA_THROWS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON_THROW))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorNoEliminaNiConsultaLaEliminacion() throws Exception {
        mockMvc.perform(delete(RUTA_THROW).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
        mockMvc.perform(get(RUTA_CATCH + "/eliminacion")).andExpect(status().isForbidden());

        verify(messageThrowService, never()).eliminar(anyLong(), anyLong(), anyString());
        verify(messageCatchService, never()).obtenerParaEliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elAdministradorConsultaLaEliminacionYElimina() throws Exception {
        when(messageThrowService.obtenerParaEliminar(5L, 40L, USERNAME)).thenReturn(messageThrow());

        mockMvc.perform(get(RUTA_THROW + "/eliminacion")).andExpect(status().isOk());
        mockMvc.perform(delete(RUTA_ENVIO).with(csrf())).andExpect(status().isNoContent());

        verify(envioExternoService).eliminar(5L, 42L, USERNAME);
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void lasApisAnterioresConservanSuRespuestaDeAccesoDenegado() throws Exception {
        mockMvc.perform(post("/api/procesos/5/gateways").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/acceso-denegado"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void laCreacionSinTokenCsrfSeRechazaConCuerpoJson() throws Exception {
        mockMvc.perform(post(RUTA_THROWS).contentType(MediaType.APPLICATION_JSON).content(JSON_THROW))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));

        verify(messageThrowService, never()).crear(anyLong(), any(CrearMessageThrowDto.class), anyString());
    }
}
