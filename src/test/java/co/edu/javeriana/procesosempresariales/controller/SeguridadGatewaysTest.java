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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.dto.ArcoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.EditarGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.ArcoService;
import co.edu.javeriana.procesosempresariales.service.GatewayService;

@WebMvcTest(controllers = { GatewayController.class, GatewayRestController.class, AutenticacionController.class })
@Import(SecurityConfig.class)
class SeguridadGatewaysTest {

    private static final String USERNAME = "usuario@alpes.com";
    private static final String RUTA_NUEVO = "/procesos/5/gateways/nuevo";
    private static final String RUTA_GATEWAYS = "/procesos/5/gateways";
    private static final String RUTA_EDITAR = "/procesos/5/gateways/12/editar";
    private static final String RUTA_GATEWAY = "/procesos/5/gateways/12";
    private static final String RUTA_API_GATEWAYS = "/api/procesos/5/gateways";
    private static final String RUTA_API_GATEWAY = "/api/procesos/5/gateways/12";
    private static final String REDIRECCION_LOGIN = "/login";

    private static final String JSON_CREACION = """
            {"tipo":"EXCLUSIVO","posicionX":300,"posicionY":120}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GatewayService gatewayService;

    @MockitoBean
    private ArcoService arcoService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private GatewayRespuestaDto gateway() {
        GatewayRespuestaDto gateway = new GatewayRespuestaDto();
        gateway.setId(12L);
        gateway.setProcesoId(5L);
        gateway.setTipo(TipoGateway.EXCLUSIVO);
        gateway.setSimbolo("X");
        gateway.setEtiqueta("Gateway EXCLUSIVO #12");
        gateway.setPosicionX(300);
        gateway.setPosicionY(120);
        gateway.setActivo(true);
        return gateway;
    }

    private ArcoRespuestaDto saliente(Long id, String destino, String condicion) {
        ArcoRespuestaDto arco = new ArcoRespuestaDto();
        arco.setId(id);
        arco.setProcesoId(5L);
        arco.setOrigenTipo(TipoNodoFlujo.GATEWAY);
        arco.setOrigenId(12L);
        arco.setDestinoTipo(TipoNodoFlujo.ACTIVIDAD);
        arco.setDestinoNombre(destino);
        arco.setCondicion(condicion);
        arco.setActivo(true);
        return arco;
    }

    private void devolverElGatewayYSusSalidas() {
        GatewayRespuestaDto gateway = gateway();
        gateway.setAdvertencias(List.of("Gateway EXCLUSIVO #12 tiene 1 arco de salida"));
        when(gatewayService.obtener(eq(5L), eq(12L), anyString())).thenReturn(gateway);
        when(arcoService.salientesDe(eq(5L), eq(TipoNodoFlujo.GATEWAY), eq(12L), anyString()))
                .thenReturn(List.of(saliente(61L, "Aprobar solicitud", "monto alto")));
    }

    @Test
    void elFormularioDeCreacionExigeSesion() throws Exception {
        mockMvc.perform(get(RUTA_NUEVO))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));
    }

    @Test
    void laCreacionExigeSesion() throws Exception {
        mockMvc.perform(post(RUTA_GATEWAYS).with(csrf())
                .param("tipo", "EXCLUSIVO")
                .param("posicionX", "300")
                .param("posicionY", "120"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(gatewayService, never()).crear(anyLong(), any(CrearGatewayDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elAdministradorAbreElFormularioDeCreacion() throws Exception {
        mockMvc.perform(get(RUTA_NUEVO)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void elEditorCreaGateways() throws Exception {
        when(gatewayService.crear(eq(5L), any(CrearGatewayDto.class), anyString())).thenReturn(gateway());

        mockMvc.perform(post(RUTA_GATEWAYS).with(csrf())
                .param("tipo", "EXCLUSIVO")
                .param("posicionX", "300")
                .param("posicionY", "120"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/procesos/5"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void elUsuarioDeSoloLecturaNoAbreElFormularioDeCreacion() throws Exception {
        mockMvc.perform(get(RUTA_NUEVO)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void elUsuarioDeSoloLecturaNoCreaGateways() throws Exception {
        mockMvc.perform(post(RUTA_GATEWAYS).with(csrf())
                .param("tipo", "EXCLUSIVO")
                .param("posicionX", "300")
                .param("posicionY", "120"))
                .andExpect(status().isForbidden());

        verify(gatewayService, never()).crear(anyLong(), any(CrearGatewayDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void laCreacionSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(post(RUTA_GATEWAYS)
                .param("tipo", "EXCLUSIVO")
                .param("posicionX", "300")
                .param("posicionY", "120"))
                .andExpect(status().isForbidden());

        verify(gatewayService, never()).crear(anyLong(), any(CrearGatewayDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void elEditorAbreElFormularioDeEdicion() throws Exception {
        devolverElGatewayYSusSalidas();

        mockMvc.perform(get(RUTA_EDITAR)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void elUsuarioDeSoloLecturaNoAbreElFormularioDeEdicion() throws Exception {
        mockMvc.perform(get(RUTA_EDITAR)).andExpect(status().isForbidden());

        verify(gatewayService, never()).obtener(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elAdministradorEditaGateways() throws Exception {
        when(gatewayService.editar(eq(5L), eq(12L), any(EditarGatewayDto.class), anyString()))
                .thenReturn(gateway());

        mockMvc.perform(post(RUTA_GATEWAY).with(csrf()).param("tipo", "PARALELO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/procesos/5"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void elUsuarioDeSoloLecturaNoEditaGateways() throws Exception {
        mockMvc.perform(post(RUTA_GATEWAY).with(csrf()).param("tipo", "PARALELO"))
                .andExpect(status().isForbidden());

        verify(gatewayService, never()).editar(anyLong(), anyLong(), any(EditarGatewayDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void laEdicionSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(post(RUTA_GATEWAY).param("tipo", "PARALELO")).andExpect(status().isForbidden());

        verify(gatewayService, never()).editar(anyLong(), anyLong(), any(EditarGatewayDto.class), anyString());
    }

    @Test
    void laApiDeGatewaysResponde401SinSesion() throws Exception {
        mockMvc.perform(post(RUTA_API_GATEWAYS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));

        verify(gatewayService, never()).crear(anyLong(), any(CrearGatewayDto.class), anyString());
    }

    @Test
    void laApiDeEdicionResponde401SinSesion() throws Exception {
        mockMvc.perform(put(RUTA_API_GATEWAY).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tipo":"PARALELO"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));
    }

    @Test
    void laApiDeEliminacionResponde401SinSesion() throws Exception {
        mockMvc.perform(delete(RUTA_API_GATEWAY).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));

        verify(gatewayService, never()).eliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    void laConsultaPreviaDeEliminacionResponde401SinSesion() throws Exception {
        mockMvc.perform(get(RUTA_API_GATEWAY + "/eliminacion"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));

        verify(gatewayService, never()).obtenerParaEliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void laEliminacionPorApiSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(delete(RUTA_API_GATEWAY)).andExpect(status().isForbidden());

        verify(gatewayService, never()).eliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elAdministradorEliminaGatewaysPorApi() throws Exception {
        mockMvc.perform(delete(RUTA_API_GATEWAY).with(csrf())).andExpect(status().isNoContent());

        verify(gatewayService).eliminar(5L, 12L, USERNAME);
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elFormularioDeEdicionDibujaUnCampoDeCondicionPorCadaSalida() throws Exception {
        devolverElGatewayYSusSalidas();

        mockMvc.perform(get(RUTA_EDITAR))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"condiciones[61]\"")))
                .andExpect(content().string(containsString("Condición hacia Aprobar solicitud")))
                .andExpect(content().string(containsString("monto alto")))
                .andExpect(content().string(containsString("Gateway EXCLUSIVO #12 tiene 1 arco de salida")));
    }
}
