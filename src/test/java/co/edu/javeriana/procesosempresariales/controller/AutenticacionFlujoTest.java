package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import co.edu.javeriana.procesosempresariales.config.SecurityConfig;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.EmpresaRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.UsuarioAutenticacionService;

@WebMvcTest(controllers = { AutenticacionController.class, EmpresaController.class })
@Import({ SecurityConfig.class, UsuarioAutenticacionService.class })
class AutenticacionFlujoTest {

    private static final String CORREO = "admin@alpes.com";
    private static final String PASSWORD = "Clave-Inicial-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private EmpresaService empresaService;

    private Empresa empresaPropia() {
        return new Empresa(10L, "Alpes Logistica", "900123456-7", CORREO);
    }

    private void usuarioRegistrado(boolean activo) {
        Usuario usuario = new Usuario(1L, CORREO, passwordEncoder.encode(PASSWORD), RolUsuario.ADMINISTRADOR, activo,
                empresaPropia());
        when(usuarioRepository.findByUsername(CORREO)).thenReturn(Optional.of(usuario));
    }

    private EmpresaRespuestaDto empresaRespuesta() {
        EmpresaRespuestaDto empresa = new EmpresaRespuestaDto();
        empresa.setId(10L);
        empresa.setNombre("Alpes Logistica");
        empresa.setNit("900123456-7");
        empresa.setCorreoContacto(CORREO);
        return empresa;
    }

    @Test
    void elLoginConCredencialesCorrectasAutenticaAlUsuarioYLoLlevaASuEmpresa() throws Exception {
        usuarioRegistrado(true);

        mockMvc.perform(formLogin().user(CORREO).password(PASSWORD))
                .andExpect(authenticated().withUsername(CORREO))
                .andExpect(redirectedUrl("/empresas"));
    }

    @Test
    void elLoginConContrasenaIncorrectaNoAutenticaYVuelveAlLoginConErrorGenerico() throws Exception {
        usuarioRegistrado(true);

        mockMvc.perform(formLogin().user(CORREO).password("otra-contrasena"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void elLoginDeUnUsuarioInexistenteDevuelveElMismoErrorGenerico() throws Exception {
        when(usuarioRepository.findByUsername("desconocido@alpes.com")).thenReturn(Optional.empty());

        mockMvc.perform(formLogin().user("desconocido@alpes.com").password(PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void unUsuarioInactivoNoPuedeIniciarSesion() throws Exception {
        usuarioRegistrado(false);

        mockMvc.perform(formLogin().user(CORREO).password(PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void elLoginAceptaElCorreoConMayusculasYEspacios() throws Exception {
        usuarioRegistrado(true);

        mockMvc.perform(formLogin().user("  Admin@Alpes.COM  ").password(PASSWORD))
                .andExpect(authenticated().withUsername(CORREO));
    }

    @Test
    void elLogoutInvalidaLaSesionYRedirigeAlLoginConAviso() throws Exception {
        usuarioRegistrado(true);

        MvcResult inicio = mockMvc.perform(formLogin().user(CORREO).password(PASSWORD))
                .andExpect(authenticated())
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) inicio.getRequest().getSession(false);
        assertThat(sesion).isNotNull();

        mockMvc.perform(post("/logout").session(sesion).with(csrf()))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?logout"));

        assertThat(sesion.isInvalid()).isTrue();
    }

    @Test
    void trasElLoginElControladorRecibeElUsernameNormalizadoDelUsuarioAutenticado() throws Exception {
        usuarioRegistrado(true);
        when(empresaService.listarVisiblesPara(CORREO)).thenReturn(List.of(empresaRespuesta()));

        MvcResult inicio = mockMvc.perform(formLogin().user("Admin@Alpes.COM").password(PASSWORD))
                .andExpect(authenticated())
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) inicio.getRequest().getSession(false);

        mockMvc.perform(get("/empresas").session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("empresas/lista"));
    }

    @Test
    void trasIniciarSesionElUsuarioVuelveALaRutaProtegidaQueHabiaPedido() throws Exception {
        usuarioRegistrado(true);

        MvcResult bloqueado = mockMvc.perform(get("/empresas/10"))
                .andExpect(redirectedUrl("/login"))
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) bloqueado.getRequest().getSession(false);
        assertThat(sesion).isNotNull();

        mockMvc.perform(post("/login").session(sesion).with(csrf())
                .param("username", CORREO)
                .param("password", PASSWORD))
                .andExpect(authenticated().withUsername(CORREO))
                .andExpect(redirectedUrlPattern("**/empresas/10*"));
    }

    @Test
    void unUsuarioAutenticadoNoPuedeConsultarLaEmpresaDeOtraOrganizacion() throws Exception {
        usuarioRegistrado(true);
        when(empresaService.obtenerParaUsuario(99L, CORREO))
                .thenThrow(new UsuarioSinPermisoException("La empresa consultada no pertenece al usuario autenticado"));

        MvcResult inicio = mockMvc.perform(formLogin().user(CORREO).password(PASSWORD))
                .andExpect(authenticated())
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) inicio.getRequest().getSession(false);

        mockMvc.perform(get("/empresas/99").session(sesion))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/problema"));
    }
}
