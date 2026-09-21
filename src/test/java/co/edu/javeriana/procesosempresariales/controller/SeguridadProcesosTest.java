package co.edu.javeriana.procesosempresariales.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.javeriana.procesosempresariales.config.SecurityConfig;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroProcesosDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;

@WebMvcTest(controllers = { ProcesoController.class, AutenticacionController.class })
@Import(SecurityConfig.class)
class SeguridadProcesosTest {

    private static final String USERNAME = "usuario@alpes.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProcesoService procesoService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private ProcesoRespuestaDto proceso() {
        ProcesoRespuestaDto proceso = new ProcesoRespuestaDto();
        proceso.setId(5L);
        proceso.setNombre("Ventas");
        proceso.setDescripcion("Proceso comercial");
        proceso.setCategoria("Comercial");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setPoolId(80L);
        return proceso;
    }

    private void devolverListadoVacio() {
        when(procesoService.consultarProcesos(any(FiltroProcesosDto.class), anyString()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        when(procesoService.categoriasDisponibles(USERNAME)).thenReturn(List.of());
    }

    @Test
    void elListadoDeProcesosExigeSesion() throws Exception {
        mockMvc.perform(get("/procesos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(procesoService, never()).consultarProcesos(any(FiltroProcesosDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void unAdministradorConsultaElListadoDeProcesos() throws Exception {
        devolverListadoVacio();

        mockMvc.perform(get("/procesos")).andExpect(status().isOk());

        verify(procesoService).consultarProcesos(any(FiltroProcesosDto.class), eq(USERNAME));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorConsultaElListadoDeProcesos() throws Exception {
        devolverListadoVacio();

        mockMvc.perform(get("/procesos")).andExpect(status().isOk());

        verify(procesoService).consultarProcesos(any(FiltroProcesosDto.class), eq(USERNAME));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaConsultaElListadoDeProcesos() throws Exception {
        devolverListadoVacio();

        mockMvc.perform(get("/procesos")).andExpect(status().isOk());

        verify(procesoService).consultarProcesos(any(FiltroProcesosDto.class), eq(USERNAME));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaPuedeFiltrarPorInactivosSinPerderElAcceso() throws Exception {
        devolverListadoVacio();

        mockMvc.perform(get("/procesos").param("visibilidad", "INACTIVOS")).andExpect(status().isOk());

        verify(procesoService).consultarProcesos(any(FiltroProcesosDto.class), eq(USERNAME));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void unAdministradorAbreElFormularioDeCreacion() throws Exception {
        mockMvc.perform(get("/procesos/nuevo")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorAbreElFormularioDeCreacion() throws Exception {
        mockMvc.perform(get("/procesos/nuevo")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoAbreElFormularioDeCreacion() throws Exception {
        mockMvc.perform(get("/procesos/nuevo")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void unAdministradorCreaUnProceso() throws Exception {
        when(procesoService.crear(any(CrearProcesoDto.class), anyString())).thenReturn(proceso());

        mockMvc.perform(post("/procesos").with(csrf())
                .param("nombre", "Ventas")
                .param("descripcion", "Proceso comercial")
                .param("categoria", "Comercial"))
                .andExpect(redirectedUrl("/procesos/5"));

        verify(procesoService).crear(any(CrearProcesoDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorCreaUnProceso() throws Exception {
        when(procesoService.crear(any(CrearProcesoDto.class), anyString())).thenReturn(proceso());

        mockMvc.perform(post("/procesos").with(csrf())
                .param("nombre", "Ventas")
                .param("descripcion", "Proceso comercial")
                .param("categoria", "Comercial"))
                .andExpect(redirectedUrl("/procesos/5"));

        verify(procesoService).crear(any(CrearProcesoDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoPuedeCrearUnProceso() throws Exception {
        mockMvc.perform(post("/procesos").with(csrf())
                .param("nombre", "Ventas")
                .param("descripcion", "Proceso comercial")
                .param("categoria", "Comercial"))
                .andExpect(status().isForbidden());

        verify(procesoService, never()).crear(any(CrearProcesoDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void crearUnProcesoSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(post("/procesos")
                .param("nombre", "Ventas")
                .param("descripcion", "Proceso comercial")
                .param("categoria", "Comercial"))
                .andExpect(status().isForbidden());

        verify(procesoService, never()).crear(any(CrearProcesoDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorAbreElFormularioDeEdicion() throws Exception {
        when(procesoService.obtener(5L, USERNAME)).thenReturn(proceso());

        mockMvc.perform(get("/procesos/5/editar")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoAbreElFormularioDeEdicion() throws Exception {
        mockMvc.perform(get("/procesos/5/editar")).andExpect(status().isForbidden());

        verify(procesoService, never()).obtener(anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoPuedeGuardarUnaEdicion() throws Exception {
        mockMvc.perform(post("/procesos/5").with(csrf())
                .param("nombre", "Ventas Corporativas")
                .param("descripcion", "Descripcion actualizada")
                .param("categoria", "Operaciones")
                .param("estado", "PUBLICADO"))
                .andExpect(status().isForbidden());

        verify(procesoService, never()).editar(anyLong(), any(EditarProcesoDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorGuardaUnaEdicion() throws Exception {
        when(procesoService.editar(anyLong(), any(EditarProcesoDto.class), anyString())).thenReturn(proceso());

        mockMvc.perform(post("/procesos/5").with(csrf())
                .param("nombre", "Ventas Corporativas")
                .param("descripcion", "Descripcion actualizada")
                .param("categoria", "Operaciones")
                .param("estado", "PUBLICADO"))
                .andExpect(redirectedUrl("/procesos/5"));

        verify(procesoService).editar(anyLong(), any(EditarProcesoDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaSiPuedeConsultarElDetalleDelProceso() throws Exception {
        when(procesoService.obtener(5L, USERNAME)).thenReturn(proceso());
        when(procesoService.puedeEditar(USERNAME)).thenReturn(false);

        mockMvc.perform(get("/procesos/5")).andExpect(status().isOk());

        verify(procesoService).obtener(5L, USERNAME);
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaSiPuedeConsultarElHistorialDelProceso() throws Exception {
        when(procesoService.obtener(5L, USERNAME)).thenReturn(proceso());
        when(procesoService.consultarHistorial(5L, USERNAME)).thenReturn(List.of());

        mockMvc.perform(get("/procesos/5/historial")).andExpect(status().isOk());

        verify(procesoService).consultarHistorial(5L, USERNAME);
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void unAdministradorEliminaUnProceso() throws Exception {
        when(procesoService.eliminar(5L, USERNAME)).thenReturn(proceso());

        mockMvc.perform(post("/procesos/5/eliminar").with(csrf()))
                .andExpect(redirectedUrl("/procesos/5"));

        verify(procesoService).eliminar(5L, USERNAME);
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorNoPuedeEliminarUnProceso() throws Exception {
        mockMvc.perform(post("/procesos/5/eliminar").with(csrf()))
                .andExpect(status().isForbidden());

        verify(procesoService, never()).eliminar(anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoPuedeEliminarUnProceso() throws Exception {
        mockMvc.perform(post("/procesos/5/eliminar").with(csrf()))
                .andExpect(status().isForbidden());

        verify(procesoService, never()).eliminar(anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void unAdministradorAbreLaPaginaDeConfirmacionSinEliminarNada() throws Exception {
        when(procesoService.obtenerParaEliminar(5L, USERNAME)).thenReturn(proceso());

        mockMvc.perform(get("/procesos/5/eliminar")).andExpect(status().isOk());

        verify(procesoService).obtenerParaEliminar(5L, USERNAME);
        verify(procesoService, never()).eliminar(anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "EDITOR")
    void unEditorNoAbreLaPaginaDeConfirmacion() throws Exception {
        mockMvc.perform(get("/procesos/5/eliminar")).andExpect(status().isForbidden());

        verify(procesoService, never()).obtenerParaEliminar(anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaNoAbreLaPaginaDeConfirmacion() throws Exception {
        mockMvc.perform(get("/procesos/5/eliminar")).andExpect(status().isForbidden());

        verify(procesoService, never()).obtenerParaEliminar(anyLong(), anyString());
    }

    @Test
    void laPaginaDeConfirmacionExigeSesion() throws Exception {
        mockMvc.perform(get("/procesos/5/eliminar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(procesoService, never()).obtenerParaEliminar(anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void eliminarUnProcesoSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(post("/procesos/5/eliminar"))
                .andExpect(status().isForbidden());

        verify(procesoService, never()).eliminar(anyLong(), anyString());
    }

    @Test
    void eliminarUnProcesoExigeSesion() throws Exception {
        mockMvc.perform(post("/procesos/5/eliminar").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(procesoService, never()).eliminar(anyLong(), anyString());
    }

    @Test
    void elHistorialExigeSesion() throws Exception {
        mockMvc.perform(get("/procesos/5/historial"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(procesoService, never()).consultarHistorial(anyLong(), anyString());
    }
}
