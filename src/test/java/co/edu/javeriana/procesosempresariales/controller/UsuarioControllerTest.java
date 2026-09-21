package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.dto.CambiarRolUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.UsuarioRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

    private static final String ADMINISTRADOR = "admin@alpes.com";
    private static final String NUEVO_CORREO = "editor@alpes.com";
    private static final String PASSWORD = "Clave-Usuario-2026";
    private static final Principal PRINCIPAL = () -> ADMINISTRADOR;

    @Mock
    private UsuarioService usuarioService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UsuarioController(usuarioService)).build();
    }

    private UsuarioRespuestaDto usuario(Long id, String correo, RolUsuario rol, boolean activo) {
        UsuarioRespuestaDto dto = new UsuarioRespuestaDto();
        dto.setId(id);
        dto.setCorreo(correo);
        dto.setRol(rol);
        dto.setActivo(activo);
        return dto;
    }

    @Test
    void elListadoMuestraLosUsuariosDeLaEmpresaDelAdministrador() throws Exception {
        when(usuarioService.listarDeMiEmpresa(ADMINISTRADOR)).thenReturn(List.of(
                usuario(1L, ADMINISTRADOR, RolUsuario.ADMINISTRADOR, true),
                usuario(2L, NUEVO_CORREO, RolUsuario.EDITOR, false)));

        MvcResult resultado = mockMvc.perform(get("/usuarios").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("usuarios/lista"))
                .andExpect(model().attribute("correoAutenticado", ADMINISTRADOR))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<UsuarioRespuestaDto> usuarios =
                (List<UsuarioRespuestaDto>) resultado.getModelAndView().getModel().get("usuarios");
        assertThat(usuarios).hasSize(2);
        assertThat(usuarios).extracting(UsuarioRespuestaDto::getCorreo).containsExactly(ADMINISTRADOR, NUEVO_CORREO);
        assertThat(usuarios).extracting(UsuarioRespuestaDto::isActivo).containsExactly(true, false);
    }

    @Test
    void elFormularioDeCreacionSeAbreVacioYConLosTresRolesDeAcceso() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/usuarios/nuevo").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("usuarios/formulario"))
                .andExpect(model().attribute("roles", RolUsuario.values()))
                .andReturn();

        CrearUsuarioDto usuario = (CrearUsuarioDto) resultado.getModelAndView().getModel().get("usuario");
        assertThat(usuario).isNotNull();
        assertThat(usuario.getCorreo()).isNull();
        assertThat(usuario.getPassword()).isNull();
    }

    @Test
    void crearUnUsuarioValidoDelegaEnElServicioYVuelveAlListado() throws Exception {
        when(usuarioService.crear(any(CrearUsuarioDto.class), anyString()))
                .thenReturn(usuario(2L, NUEVO_CORREO, RolUsuario.EDITOR, true));

        mockMvc.perform(post("/usuarios").principal(PRINCIPAL)
                .param("correo", NUEVO_CORREO)
                .param("password", PASSWORD)
                .param("rol", "EDITOR"))
                .andExpect(redirectedUrl("/usuarios"))
                .andExpect(flash().attributeExists("mensaje"));

        ArgumentCaptor<CrearUsuarioDto> enviado = ArgumentCaptor.forClass(CrearUsuarioDto.class);
        verify(usuarioService).crear(enviado.capture(), eq(ADMINISTRADOR));
        assertThat(enviado.getValue().getCorreo()).isEqualTo(NUEVO_CORREO);
        assertThat(enviado.getValue().getRol()).isEqualTo(RolUsuario.EDITOR);
    }

    @Test
    void crearVuelveAlFormularioCuandoLaValidacionFalla() throws Exception {
        mockMvc.perform(post("/usuarios").principal(PRINCIPAL)
                .param("correo", "")
                .param("password", "")
                .param("rol", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("usuarios/formulario"))
                .andExpect(model().attributeExists("roles"))
                .andExpect(model().attributeHasFieldErrors("usuario", "correo", "password", "rol"));

        verify(usuarioService, never()).crear(any(CrearUsuarioDto.class), anyString());
    }

    @Test
    void crearSenalaElCampoCorreoCuandoYaEstaEnUso() throws Exception {
        when(usuarioService.crear(any(CrearUsuarioDto.class), anyString()))
                .thenThrow(new CorreoAdministradorEnUsoException(
                        "El correo editor@alpes.com ya esta asociado a otro usuario"));

        mockMvc.perform(post("/usuarios").principal(PRINCIPAL)
                .param("correo", NUEVO_CORREO)
                .param("password", PASSWORD)
                .param("rol", "EDITOR"))
                .andExpect(status().isOk())
                .andExpect(view().name("usuarios/formulario"))
                .andExpect(model().attributeExists("roles"))
                .andExpect(model().attributeHasFieldErrors("usuario", "correo"));
    }

    @Test
    void elDetalleMuestraElUsuarioSolicitado() throws Exception {
        when(usuarioService.obtener(2L, ADMINISTRADOR)).thenReturn(usuario(2L, NUEVO_CORREO, RolUsuario.EDITOR, true));

        MvcResult resultado = mockMvc.perform(get("/usuarios/2").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("usuarios/detalle"))
                .andReturn();

        UsuarioRespuestaDto usuario = (UsuarioRespuestaDto) resultado.getModelAndView().getModel().get("usuario");
        assertThat(usuario.getCorreo()).isEqualTo(NUEVO_CORREO);
        assertThat(usuario.getRol()).isEqualTo(RolUsuario.EDITOR);
        assertThat(usuario.isActivo()).isTrue();
    }

    @Test
    void elFormularioDeCambioDeRolSePrecargaConElRolActual() throws Exception {
        when(usuarioService.obtener(2L, ADMINISTRADOR)).thenReturn(usuario(2L, NUEVO_CORREO, RolUsuario.EDITOR, true));

        MvcResult resultado = mockMvc.perform(get("/usuarios/2/editar").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("usuarios/formularioeditar"))
                .andExpect(model().attribute("usuarioId", 2L))
                .andExpect(model().attribute("correo", NUEVO_CORREO))
                .andExpect(model().attribute("roles", RolUsuario.values()))
                .andReturn();

        CambiarRolUsuarioDto dto = (CambiarRolUsuarioDto) resultado.getModelAndView().getModel().get("usuario");
        assertThat(dto.getRol()).isEqualTo(RolUsuario.EDITOR);
    }

    @Test
    void cambiarElRolDelegaEnElServicioYVuelveAlListado() throws Exception {
        when(usuarioService.obtener(2L, ADMINISTRADOR)).thenReturn(usuario(2L, NUEVO_CORREO, RolUsuario.EDITOR, true));
        when(usuarioService.cambiarRol(anyLong(), any(CambiarRolUsuarioDto.class), anyString()))
                .thenReturn(usuario(2L, NUEVO_CORREO, RolUsuario.ADMINISTRADOR, true));

        mockMvc.perform(post("/usuarios/2").principal(PRINCIPAL)
                .param("rol", "ADMINISTRADOR"))
                .andExpect(redirectedUrl("/usuarios"))
                .andExpect(flash().attributeExists("mensaje"));

        ArgumentCaptor<CambiarRolUsuarioDto> enviado = ArgumentCaptor.forClass(CambiarRolUsuarioDto.class);
        verify(usuarioService).cambiarRol(eq(2L), enviado.capture(), eq(ADMINISTRADOR));
        assertThat(enviado.getValue().getRol()).isEqualTo(RolUsuario.ADMINISTRADOR);
    }

    @Test
    void cambiarElRolComprobandoLaEmpresaAntesDeValidarElFormulario() throws Exception {
        when(usuarioService.obtener(2L, ADMINISTRADOR)).thenReturn(usuario(2L, NUEVO_CORREO, RolUsuario.EDITOR, true));

        mockMvc.perform(post("/usuarios/2").principal(PRINCIPAL)
                .param("rol", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("usuarios/formularioeditar"))
                .andExpect(model().attribute("usuarioId", 2L))
                .andExpect(model().attribute("correo", NUEVO_CORREO))
                .andExpect(model().attributeExists("roles"));

        verify(usuarioService).obtener(2L, ADMINISTRADOR);
        verify(usuarioService, never()).cambiarRol(anyLong(), any(CambiarRolUsuarioDto.class), anyString());
    }

    @Test
    void desactivarDelegaEnElServicioYVuelveAlListadoConElAviso() throws Exception {
        when(usuarioService.desactivar(2L, ADMINISTRADOR))
                .thenReturn(usuario(2L, NUEVO_CORREO, RolUsuario.EDITOR, false));

        mockMvc.perform(post("/usuarios/2/desactivar").principal(PRINCIPAL))
                .andExpect(redirectedUrl("/usuarios"))
                .andExpect(flash().attributeExists("mensaje"));

        verify(usuarioService).desactivar(2L, ADMINISTRADOR);
    }
}
