package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import co.edu.javeriana.procesosempresariales.config.SecurityConfig;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.SesionDto;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;
import co.edu.javeriana.procesosempresariales.service.SesionService;
import co.edu.javeriana.procesosempresariales.service.UsuarioAutenticacionService;

@WebMvcTest(controllers = { SesionRestController.class, ProcesoRestController.class, AutenticacionController.class,
        EmpresaController.class })
@Import({ SecurityConfig.class, UsuarioAutenticacionService.class })
class SesionRestControllerTest {

    private static final String RUTA_SESION = "/api/sesion";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";
    private static final String CABECERA_CSRF = "X-XSRF-TOKEN";
    private static final String CORREO = "admin@alpes.com";
    private static final String PASSWORD = "Clave-Inicial-2026";
    private static final String CODIGO = "$.codigo";
    private static final Pattern CAMPO_CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
    private static final String JSON_PROCESO = """
            {"nombre":"Ventas","descripcion":"Proceso comercial","categoria":"Comercial"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private SesionService sesionService;

    @MockitoBean
    private ProcesoService procesoService;

    @MockitoBean
    private EmpresaService empresaService;

    private void usuarioRegistrado(boolean activo) {
        Empresa empresa = new Empresa(10L, "Alpes Logistica", "900123456-7", CORREO);
        Usuario usuario = new Usuario(1L, CORREO, passwordEncoder.encode(PASSWORD), RolUsuario.ADMINISTRADOR, activo,
                empresa);
        when(usuarioRepository.findByUsername(CORREO)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByUsername("nadie@alpes.com")).thenReturn(Optional.empty());
    }

    private SesionDto sesionAutenticada() {
        SesionDto sesion = new SesionDto();
        sesion.setAutenticado(true);
        sesion.setCorreo(CORREO);
        sesion.setRol(RolUsuario.ADMINISTRADOR);
        sesion.setEmpresaId(10L);
        sesion.setEmpresaNombre("Alpes Logistica");
        return sesion;
    }

    private ProcesoRespuestaDto procesoCreado() {
        ProcesoRespuestaDto proceso = new ProcesoRespuestaDto();
        proceso.setId(5L);
        proceso.setNombre("Ventas");
        proceso.setEstado(EstadoProceso.BORRADOR);
        return proceso;
    }

    private Cookie tokenCsrf(MockHttpSession sesion) throws Exception {
        MvcResult resultado = mockMvc.perform(get(RUTA_SESION).session(sesion))
                .andExpect(status().isOk())
                .andReturn();
        Cookie token = resultado.getResponse().getCookie(COOKIE_CSRF);
        assertThat(token).isNotNull();
        return token;
    }

    private MockHttpSession iniciarSesion(MockHttpSession sesion) throws Exception {
        Cookie token = tokenCsrf(sesion);
        MvcResult resultado = mockMvc.perform(post(RUTA_SESION).session(sesion)
                .cookie(token)
                .header(CABECERA_CSRF, token.getValue())
                .param("correo", CORREO)
                .param("password", PASSWORD))
                .andExpect(status().isNoContent())
                .andExpect(authenticated().withUsername(CORREO).withRoles("ADMINISTRADOR"))
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    @Test
    void sinSesionLaConsultaRespondeAnonimoYEmiteLaCookieCsrfLegibleDesdeJavaScript() throws Exception {
        mockMvc.perform(get(RUTA_SESION))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.autenticado").value(false))
                .andExpect(jsonPath("$.correo").doesNotExist())
                .andExpect(jsonPath("$.rol").doesNotExist())
                .andExpect(cookie().exists(COOKIE_CSRF))
                .andExpect(cookie().httpOnly(COOKIE_CSRF, false))
                .andExpect(cookie().path(COOKIE_CSRF, "/"));

        verify(sesionService, never()).consultar(anyString());
    }

    @Test
    void elInicioDeSesionConCredencialesCorrectasPersisteElContextoYRenuevaElTokenCsrf() throws Exception {
        usuarioRegistrado(true);
        when(sesionService.consultar(CORREO)).thenReturn(sesionAutenticada());
        MockHttpSession sesion = new MockHttpSession();
        Cookie tokenAnonimo = tokenCsrf(sesion);

        MvcResult inicio = mockMvc.perform(post(RUTA_SESION).session(sesion)
                .cookie(tokenAnonimo)
                .header(CABECERA_CSRF, tokenAnonimo.getValue())
                .param("correo", CORREO)
                .param("password", PASSWORD))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE_CSRF, 0))
                .andReturn();
        MockHttpSession autenticada = (MockHttpSession) inicio.getRequest().getSession(false);

        MvcResult consulta = mockMvc.perform(get(RUTA_SESION).session(autenticada))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.correo").value(CORREO))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.empresaId").value(10))
                .andExpect(jsonPath("$.empresaNombre").value("Alpes Logistica"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        Cookie tokenAutenticado = consulta.getResponse().getCookie(COOKIE_CSRF);
        assertThat(tokenAutenticado).isNotNull();
        assertThat(tokenAutenticado.getValue()).isNotEqualTo(tokenAnonimo.getValue());
        assertThat(consulta.getResponse().getContentAsString()).doesNotContain(PASSWORD);
    }

    @Test
    void unaContrasenaIncorrectaRespondeUnErrorGenericoSinAutenticar() throws Exception {
        usuarioRegistrado(true);
        MockHttpSession sesion = new MockHttpSession();
        Cookie token = tokenCsrf(sesion);

        mockMvc.perform(post(RUTA_SESION).session(sesion)
                .cookie(token)
                .header(CABECERA_CSRF, token.getValue())
                .param("correo", CORREO)
                .param("password", "otra-contrasena"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.mensaje").value("Correo o contraseña incorrectos"))
                .andExpect(unauthenticated());
    }

    @Test
    void unUsuarioInexistenteRecibeExactamenteElMismoErrorQueUnaContrasenaIncorrecta() throws Exception {
        usuarioRegistrado(true);
        MockHttpSession sesion = new MockHttpSession();
        Cookie token = tokenCsrf(sesion);

        String inexistente = mockMvc.perform(post(RUTA_SESION).session(sesion)
                .cookie(token)
                .header(CABECERA_CSRF, token.getValue())
                .param("correo", "nadie@alpes.com")
                .param("password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated())
                .andReturn().getResponse().getContentAsString();
        String incorrecta = mockMvc.perform(post(RUTA_SESION).session(sesion)
                .cookie(token)
                .header(CABECERA_CSRF, token.getValue())
                .param("correo", CORREO)
                .param("password", "otra-contrasena"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(inexistente).isEqualTo(incorrecta);
    }

    @Test
    void unUsuarioDesactivadoNoIniciaSesion() throws Exception {
        usuarioRegistrado(false);
        MockHttpSession sesion = new MockHttpSession();
        Cookie token = tokenCsrf(sesion);

        mockMvc.perform(post(RUTA_SESION).session(sesion)
                .cookie(token)
                .header(CABECERA_CSRF, token.getValue())
                .param("correo", CORREO)
                .param("password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value("CREDENCIALES_INVALIDAS"))
                .andExpect(unauthenticated());
    }

    @Test
    void elInicioDeSesionSinTokenCsrfSeRechazaConJson() throws Exception {
        usuarioRegistrado(true);

        mockMvc.perform(post(RUTA_SESION).param("correo", CORREO).param("password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"))
                .andExpect(unauthenticated());
    }

    @Test
    void unTokenCsrfQueNoCoincideConLaCookieSeRechaza() throws Exception {
        usuarioRegistrado(true);
        MockHttpSession sesion = new MockHttpSession();
        Cookie token = tokenCsrf(sesion);

        mockMvc.perform(post(RUTA_SESION).session(sesion)
                .cookie(token)
                .header(CABECERA_CSRF, "token-alterado")
                .param("correo", CORREO)
                .param("password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }

    @Test
    void laSesionIniciadaPermiteEscribirEnLaApiConElTokenDeLaCookie() throws Exception {
        usuarioRegistrado(true);
        when(procesoService.crear(any(CrearProcesoDto.class), eq(CORREO))).thenReturn(procesoCreado());
        MockHttpSession sesion = iniciarSesion(new MockHttpSession());
        Cookie token = tokenCsrf(sesion);

        mockMvc.perform(post("/api/procesos").session(sesion)
                .cookie(token)
                .header(CABECERA_CSRF, token.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_PROCESO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void laSesionIniciadaNoEscribeEnLaApiSinElTokenCsrf() throws Exception {
        usuarioRegistrado(true);
        MockHttpSession sesion = iniciarSesion(new MockHttpSession());

        mockMvc.perform(post("/api/procesos").session(sesion)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_PROCESO))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));

        verify(procesoService, never()).crear(any(CrearProcesoDto.class), anyString());
    }

    @Test
    void elCierreDeSesionInvalidaLaSesionYBorraLasCookies() throws Exception {
        usuarioRegistrado(true);
        MockHttpSession sesion = iniciarSesion(new MockHttpSession());
        Cookie token = tokenCsrf(sesion);

        mockMvc.perform(delete(RUTA_SESION).session(sesion)
                .cookie(token)
                .header(CABECERA_CSRF, token.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(cookie().maxAge(COOKIE_CSRF, 0))
                .andExpect(unauthenticated());

        assertThat(sesion.isInvalid()).isTrue();
        mockMvc.perform(get(RUTA_SESION))
                .andExpect(jsonPath("$.autenticado").value(false));
    }

    @Test
    void elCierreDeSesionSinTokenCsrfSeRechazaYLaSesionSigueActiva() throws Exception {
        usuarioRegistrado(true);
        when(sesionService.consultar(CORREO)).thenReturn(sesionAutenticada());
        MockHttpSession sesion = iniciarSesion(new MockHttpSession());

        mockMvc.perform(delete(RUTA_SESION).session(sesion))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));

        assertThat(sesion.isInvalid()).isFalse();
        mockMvc.perform(get(RUTA_SESION).session(sesion))
                .andExpect(jsonPath("$.autenticado").value(true));
    }

    @Test
    void laApiProtegidaSigueRespondiendo401SinSesion() throws Exception {
        mockMvc.perform(get("/api/procesos/5"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value("USUARIO_NO_AUTORIZADO"));
    }

    private MvcResult enviarLoginMvc(String password) throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        MvcResult pagina = mockMvc.perform(get("/login").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andReturn();
        Cookie token = pagina.getResponse().getCookie(COOKIE_CSRF);
        Matcher campo = CAMPO_CSRF.matcher(pagina.getResponse().getContentAsString());
        assertThat(token).isNotNull();
        assertThat(campo.find()).isTrue();
        assertThat(campo.group(1)).isNotEqualTo(token.getValue());
        return mockMvc.perform(post("/login").session(sesion)
                .cookie(token)
                .param("_csrf", campo.group(1))
                .param("username", CORREO)
                .param("password", password))
                .andReturn();
    }

    @Test
    void elLoginMvcSigueFuncionandoConElTokenEnmascaradoDelCampoOculto() throws Exception {
        usuarioRegistrado(true);

        MvcResult resultado = enviarLoginMvc(PASSWORD);

        assertThat(resultado.getResponse().getRedirectedUrl()).isEqualTo("/empresas");
        mockMvc.perform(get(RUTA_SESION).session((MockHttpSession) resultado.getRequest().getSession(false)))
                .andExpect(authenticated().withUsername(CORREO));
    }

    @Test
    void elLoginMvcConContrasenaIncorrectaVuelveAlFormularioConError() throws Exception {
        usuarioRegistrado(true);

        MvcResult resultado = enviarLoginMvc("otra-contrasena");

        assertThat(resultado.getResponse().getRedirectedUrl()).isEqualTo("/login?error");
    }
}
