package co.edu.javeriana.procesosempresariales.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.javeriana.procesosempresariales.config.SecurityConfig;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.dto.CambiarRolUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.UsuarioRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@WebMvcTest(controllers = { UsuarioController.class, AutenticacionController.class })
@Import(SecurityConfig.class)
class SeguridadUsuariosTest {

    private static final String ADMINISTRADOR = "admin@alpes.com";
    private static final String NUEVO_CORREO = "editor@alpes.com";
    private static final String PASSWORD = "Clave-Usuario-2026";
    private static final String REDIRECCION_LOGIN = "/login";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioService usuarioService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private UsuarioRespuestaDto usuario(Long id, String correo, RolUsuario rol, boolean activo) {
        UsuarioRespuestaDto dto = new UsuarioRespuestaDto();
        dto.setId(id);
        dto.setCorreo(correo);
        dto.setRol(rol);
        dto.setActivo(activo);
        return dto;
    }

    @Test
    void elListadoDeUsuariosExigeSesion() throws Exception {
        mockMvc.perform(get("/usuarios"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(usuarioService, never()).listarDeMiEmpresa(anyString());
    }

    @Test
    void elFormularioDeCreacionDeUsuariosExigeSesion() throws Exception {
        mockMvc.perform(get("/usuarios/nuevo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));
    }

    @Test
    void crearUsuariosExigeSesion() throws Exception {
        mockMvc.perform(post("/usuarios").with(csrf())
                .param("correo", NUEVO_CORREO)
                .param("password", PASSWORD)
                .param("rol", "EDITOR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(usuarioService, never()).crear(any(CrearUsuarioDto.class), anyString());
    }

    @Test
    void desactivarUsuariosExigeSesion() throws Exception {
        mockMvc.perform(post("/usuarios/2/desactivar").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(usuarioService, never()).desactivar(anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = ADMINISTRADOR, roles = "ADMINISTRADOR")
    void unAdministradorAccedeAlListadoDeUsuarios() throws Exception {
        when(usuarioService.listarDeMiEmpresa(ADMINISTRADOR))
                .thenReturn(List.of(usuario(1L, ADMINISTRADOR, RolUsuario.ADMINISTRADOR, true)));

        mockMvc.perform(get("/usuarios")).andExpect(status().isOk());

        verify(usuarioService).listarDeMiEmpresa(ADMINISTRADOR);
    }

    @Test
    @WithMockUser(username = NUEVO_CORREO, roles = "EDITOR")
    void unEditorRecibe403AlEntrarAlListadoDeUsuarios() throws Exception {
        mockMvc.perform(get("/usuarios")).andExpect(status().isForbidden());

        verify(usuarioService, never()).listarDeMiEmpresa(anyString());
    }

    @Test
    @WithMockUser(username = NUEVO_CORREO, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaRecibe403AlEntrarAlListadoDeUsuarios() throws Exception {
        mockMvc.perform(get("/usuarios")).andExpect(status().isForbidden());

        verify(usuarioService, never()).listarDeMiEmpresa(anyString());
    }

    @Test
    @WithMockUser(username = NUEVO_CORREO, roles = "EDITOR")
    void unEditorRecibe403AlAbrirElFormularioDeCreacion() throws Exception {
        mockMvc.perform(get("/usuarios/nuevo")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = NUEVO_CORREO, roles = "EDITOR")
    void elAccesoDenegadoSeAtiendeConLaPaginaDeProblemaYNoConUnaPantallaEnBlanco() throws Exception {
        mockMvc.perform(get("/usuarios"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/acceso-denegado"));
    }

    @Test
    @WithMockUser(username = NUEVO_CORREO, roles = "EDITOR")
    void unEditorRecibe403AlIntentarCrearUnUsuario() throws Exception {
        mockMvc.perform(post("/usuarios").with(csrf())
                .param("correo", "otro@alpes.com")
                .param("password", PASSWORD)
                .param("rol", "ADMINISTRADOR"))
                .andExpect(status().isForbidden());

        verify(usuarioService, never()).crear(any(CrearUsuarioDto.class), anyString());
    }

    @Test
    @WithMockUser(username = NUEVO_CORREO, roles = "EDITOR")
    void unEditorRecibe403AlIntentarCambiarUnRol() throws Exception {
        mockMvc.perform(post("/usuarios/2").with(csrf()).param("rol", "ADMINISTRADOR"))
                .andExpect(status().isForbidden());

        verify(usuarioService, never()).cambiarRol(anyLong(), any(CambiarRolUsuarioDto.class), anyString());
    }

    @Test
    @WithMockUser(username = NUEVO_CORREO, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaRecibe403AlIntentarDesactivar() throws Exception {
        mockMvc.perform(post("/usuarios/2/desactivar").with(csrf()))
                .andExpect(status().isForbidden());

        verify(usuarioService, never()).desactivar(anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = ADMINISTRADOR, roles = "ADMINISTRADOR")
    void unAdministradorCreaUnUsuarioEnviandoElTokenCsrf() throws Exception {
        when(usuarioService.crear(any(CrearUsuarioDto.class), anyString()))
                .thenReturn(usuario(2L, NUEVO_CORREO, RolUsuario.EDITOR, true));

        mockMvc.perform(post("/usuarios").with(csrf())
                .param("correo", NUEVO_CORREO)
                .param("password", PASSWORD)
                .param("rol", "EDITOR"))
                .andExpect(redirectedUrl("/usuarios"));

        verify(usuarioService).crear(any(CrearUsuarioDto.class), anyString());
    }

    @Test
    @WithMockUser(username = ADMINISTRADOR, roles = "ADMINISTRADOR")
    void crearUnUsuarioSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(post("/usuarios")
                .param("correo", NUEVO_CORREO)
                .param("password", PASSWORD)
                .param("rol", "EDITOR"))
                .andExpect(status().isForbidden());

        verify(usuarioService, never()).crear(any(CrearUsuarioDto.class), anyString());
    }

    @Test
    @WithMockUser(username = ADMINISTRADOR, roles = "ADMINISTRADOR")
    void unAdministradorDesactivaUnUsuarioEnviandoElTokenCsrf() throws Exception {
        when(usuarioService.desactivar(2L, ADMINISTRADOR))
                .thenReturn(usuario(2L, NUEVO_CORREO, RolUsuario.EDITOR, false));

        mockMvc.perform(post("/usuarios/2/desactivar").with(csrf()))
                .andExpect(redirectedUrl("/usuarios"));

        verify(usuarioService).desactivar(2L, ADMINISTRADOR);
    }

    @Test
    @WithMockUser(username = ADMINISTRADOR, roles = "ADMINISTRADOR")
    void desactivarUnUsuarioSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(post("/usuarios/2/desactivar"))
                .andExpect(status().isForbidden());

        verify(usuarioService, never()).desactivar(anyLong(), anyString());
    }
}
