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
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.dto.ArcoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearArcoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarArcoDto;
import co.edu.javeriana.procesosempresariales.dto.NodoFlujoDto;
import co.edu.javeriana.procesosempresariales.service.ArcoService;

@WebMvcTest(controllers = { ArcoController.class, ArcoRestController.class, AutenticacionController.class })
@Import(SecurityConfig.class)
class SeguridadArcosTest {

    private static final String USERNAME = "usuario@alpes.com";
    private static final String RUTA_NUEVO = "/procesos/5/arcos/nuevo";
    private static final String RUTA_ARCOS = "/procesos/5/arcos";
    private static final String RUTA_EDITAR = "/procesos/5/arcos/60/editar";
    private static final String RUTA_ARCO = "/procesos/5/arcos/60";
    private static final String RUTA_ELIMINAR = "/procesos/5/arcos/60/eliminar";
    private static final String RUTA_API_ARCOS = "/api/procesos/5/arcos";
    private static final String RUTA_API_ARCO = "/api/procesos/5/arcos/60";
    private static final String REDIRECCION_LOGIN = "/login";

    private static final String JSON_CREACION = """
            {"origenTipo":"ACTIVIDAD","origenId":30,"destinoTipo":"ACTIVIDAD","destinoId":31}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArcoService arcoService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private ArcoRespuestaDto arco() {
        ArcoRespuestaDto arco = new ArcoRespuestaDto();
        arco.setId(60L);
        arco.setProcesoId(5L);
        arco.setOrigenTipo(TipoNodoFlujo.ACTIVIDAD);
        arco.setOrigenId(30L);
        arco.setOrigenNombre("Revisar solicitud");
        arco.setDestinoTipo(TipoNodoFlujo.ACTIVIDAD);
        arco.setDestinoId(31L);
        arco.setDestinoNombre("Aprobar solicitud");
        arco.setActivo(true);
        return arco;
    }

    private void devolverLosNodos() {
        when(arcoService.nodosDelProceso(eq(5L), anyString()))
                .thenReturn(List.of(new NodoFlujoDto(TipoNodoFlujo.ACTIVIDAD, 30L, "Revisar solicitud")));
    }

    @Test
    void elFormularioDeCreacionExigeSesion() throws Exception {
        mockMvc.perform(get(RUTA_NUEVO))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(arcoService, never()).nodosDelProceso(anyLong(), anyString());
    }

    @Test
    void laCreacionExigeSesion() throws Exception {
        mockMvc.perform(post(RUTA_ARCOS).with(csrf())
                .param("origenTipo", "ACTIVIDAD")
                .param("origenId", "30")
                .param("destinoTipo", "ACTIVIDAD")
                .param("destinoId", "31"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(arcoService, never()).crear(anyLong(), any(CrearArcoDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elAdministradorAbreElFormularioDeCreacion() throws Exception {
        devolverLosNodos();

        mockMvc.perform(get(RUTA_NUEVO)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void elEditorAbreElFormularioDeCreacion() throws Exception {
        devolverLosNodos();

        mockMvc.perform(get(RUTA_NUEVO)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void elUsuarioDeSoloLecturaNoAbreElFormularioDeCreacion() throws Exception {
        mockMvc.perform(get(RUTA_NUEVO)).andExpect(status().isForbidden());

        verify(arcoService, never()).nodosDelProceso(anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void elEditorCreaArcos() throws Exception {
        when(arcoService.crear(eq(5L), any(CrearArcoDto.class), anyString())).thenReturn(arco());

        mockMvc.perform(post(RUTA_ARCOS).with(csrf())
                .param("origenTipo", "ACTIVIDAD")
                .param("origenId", "30")
                .param("destinoTipo", "ACTIVIDAD")
                .param("destinoId", "31"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/procesos/5"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void elUsuarioDeSoloLecturaNoCreaArcos() throws Exception {
        mockMvc.perform(post(RUTA_ARCOS).with(csrf())
                .param("origenTipo", "ACTIVIDAD")
                .param("origenId", "30")
                .param("destinoTipo", "ACTIVIDAD")
                .param("destinoId", "31"))
                .andExpect(status().isForbidden());

        verify(arcoService, never()).crear(anyLong(), any(CrearArcoDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void laCreacionSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(post(RUTA_ARCOS)
                .param("origenTipo", "ACTIVIDAD")
                .param("origenId", "30")
                .param("destinoTipo", "ACTIVIDAD")
                .param("destinoId", "31"))
                .andExpect(status().isForbidden());

        verify(arcoService, never()).crear(anyLong(), any(CrearArcoDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void elEditorAbreElFormularioDeEdicion() throws Exception {
        devolverLosNodos();
        when(arcoService.obtener(eq(5L), eq(60L), anyString())).thenReturn(arco());

        mockMvc.perform(get(RUTA_EDITAR)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void elUsuarioDeSoloLecturaNoAbreElFormularioDeEdicion() throws Exception {
        mockMvc.perform(get(RUTA_EDITAR)).andExpect(status().isForbidden());

        verify(arcoService, never()).obtener(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void elUsuarioDeSoloLecturaNoEditaArcos() throws Exception {
        mockMvc.perform(post(RUTA_ARCO).with(csrf())
                .param("origenTipo", "ACTIVIDAD")
                .param("origenId", "30")
                .param("destinoTipo", "ACTIVIDAD")
                .param("destinoId", "31"))
                .andExpect(status().isForbidden());

        verify(arcoService, never()).editar(anyLong(), anyLong(), any(EditarArcoDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elAdministradorAbreLaConfirmacionDeEliminacion() throws Exception {
        ArcoRespuestaDto arco = arco();
        arco.setEtiqueta("solicitud completa");
        arco.setAdvertencias(List.of("'Revisar solicitud' quedó sin arcos de salida"));
        when(arcoService.obtenerParaEliminar(eq(5L), eq(60L), anyString())).thenReturn(arco);

        mockMvc.perform(get(RUTA_ELIMINAR))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Revisar solicitud")))
                .andExpect(content().string(containsString("solicitud completa")))
                .andExpect(content().string(containsString("quedó sin arcos de salida")))
                .andExpect(content().string(containsString("Sin condición")));

        verify(arcoService, never()).eliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void elEditorNoAbreLaConfirmacionDeEliminacion() throws Exception {
        mockMvc.perform(get(RUTA_ELIMINAR)).andExpect(status().isForbidden());

        verify(arcoService, never()).obtenerParaEliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void elEditorNoEliminaArcos() throws Exception {
        mockMvc.perform(post(RUTA_ELIMINAR).with(csrf())).andExpect(status().isForbidden());

        verify(arcoService, never()).eliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elAdministradorEliminaArcos() throws Exception {
        ArcoRespuestaDto eliminado = arco();
        eliminado.setActivo(false);
        when(arcoService.eliminar(eq(5L), eq(60L), anyString())).thenReturn(eliminado);

        mockMvc.perform(post(RUTA_ELIMINAR).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/procesos/5"));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void laEliminacionSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(post(RUTA_ELIMINAR)).andExpect(status().isForbidden());

        verify(arcoService, never()).eliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    void laApiDeArcosResponde401SinSesion() throws Exception {
        mockMvc.perform(post(RUTA_API_ARCOS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));

        verify(arcoService, never()).crear(anyLong(), any(CrearArcoDto.class), anyString());
    }

    @Test
    void laApiDeEdicionResponde401SinSesion() throws Exception {
        mockMvc.perform(put(RUTA_API_ARCO).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));
    }

    @Test
    void laApiDeEliminacionResponde401SinSesion() throws Exception {
        mockMvc.perform(delete(RUTA_API_ARCO).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));

        verify(arcoService, never()).eliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void laApiDelegaElPermisoEnElServicio() throws Exception {
        when(arcoService.crear(eq(5L), any(CrearArcoDto.class), anyString())).thenReturn(arco());

        mockMvc.perform(post(RUTA_API_ARCOS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isCreated());
    }
}
