package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
class SesionYEmpresasApiIntegracionTest {

    private static final Pattern CAMPO_CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
    private static final String RUTA_SESION = "/api/sesion";
    private static final String RUTA_EMPRESAS = "/api/empresas";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";
    private static final String CABECERA_CSRF = "X-XSRF-TOKEN";
    private static final String CORREO_ALPES = "admin@alpes-api.com";
    private static final String CORREO_ANDES = "admin@andes-api.com";
    private static final String PASSWORD = "Clave-Api-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Cookie tokenCsrf(MockHttpSession sesion) throws Exception {
        MvcResult resultado = mockMvc.perform(get(RUTA_SESION).session(sesion)).andReturn();
        return resultado.getResponse().getCookie(COOKIE_CSRF);
    }

    private String registro(String nombre, String nit, String correo) {
        return "{\"nombre\":\"" + nombre + "\",\"nit\":\"" + nit + "\",\"correoContacto\":\"" + correo
                + "\",\"passwordAdministrador\":\"" + PASSWORD + "\"}";
    }

    private void registrar(String nombre, String nit, String correo) throws Exception {
        MockHttpSession anonima = new MockHttpSession();
        Cookie token = tokenCsrf(anonima);
        mockMvc.perform(post(RUTA_EMPRESAS).session(anonima)
                .cookie(token)
                .header(CABECERA_CSRF, token.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registro(nombre, nit, correo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nit").value(nit))
                .andExpect(jsonPath("$.administradorUsername").value(correo));
    }

    private MockHttpSession iniciarSesion(String correo, String password, int estadoEsperado) throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        Cookie token = tokenCsrf(sesion);
        MvcResult resultado = mockMvc.perform(post(RUTA_SESION).session(sesion)
                .cookie(token)
                .header(CABECERA_CSRF, token.getValue())
                .param("correo", correo)
                .param("password", password))
                .andExpect(status().is(estadoEsperado))
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    @Test
    void laEmpresaRegistradaPorLaApiPermiteIniciarSesionYConsultarSuSesion() throws Exception {
        registrar("Alpes Api", "902000001-1", CORREO_ALPES);

        MockHttpSession sesion = iniciarSesion(CORREO_ALPES, PASSWORD, 204);

        mockMvc.perform(get(RUTA_SESION).session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.correo").value(CORREO_ALPES))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.empresaNombre").value("Alpes Api"));
        mockMvc.perform(get(RUTA_EMPRESAS + "/actual").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nit").value("902000001-1"))
                .andExpect(jsonPath("$.administradorUsername").value(CORREO_ALPES));
    }

    @Test
    void elRegistroPorLaApiGuardaLaContrasenaCifradaConBcrypt() throws Exception {
        registrar("Alpes Api", "902000001-1", CORREO_ALPES);

        Usuario administrador = usuarioRepository.findByUsername(CORREO_ALPES).orElseThrow();
        assertThat(administrador.getPassword()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(administrador.getRol()).isEqualTo(RolUsuario.ADMINISTRADOR);
    }

    @Test
    void unNitYaRegistradoSeRechazaConConflicto() throws Exception {
        registrar("Alpes Api", "902000001-1", CORREO_ALPES);
        MockHttpSession anonima = new MockHttpSession();
        Cookie token = tokenCsrf(anonima);

        mockMvc.perform(post(RUTA_EMPRESAS).session(anonima)
                .cookie(token)
                .header(CABECERA_CSRF, token.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registro("Otra Api", "902000001-1", CORREO_ANDES)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMPRESA_NIT_DUPLICADO"));
    }

    @Test
    void cadaUsuarioSoloConsultaSuPropiaEmpresa() throws Exception {
        registrar("Alpes Api", "902000001-1", CORREO_ALPES);
        registrar("Andes Api", "902000002-2", CORREO_ANDES);

        mockMvc.perform(get(RUTA_EMPRESAS + "/actual").session(iniciarSesion(CORREO_ALPES, PASSWORD, 204)))
                .andExpect(jsonPath("$.nombre").value("Alpes Api"));
        mockMvc.perform(get(RUTA_EMPRESAS + "/actual").session(iniciarSesion(CORREO_ANDES, PASSWORD, 204)))
                .andExpect(jsonPath("$.nombre").value("Andes Api"));
    }

    @Test
    void unaContrasenaIncorrectaNoAbreSesion() throws Exception {
        registrar("Alpes Api", "902000001-1", CORREO_ALPES);

        MockHttpSession sesion = iniciarSesion(CORREO_ALPES, "otra-contrasena", 401);

        mockMvc.perform(get(RUTA_SESION).session(sesion))
                .andExpect(jsonPath("$.autenticado").value(false));
        mockMvc.perform(get("/api/procesos").session(sesion))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elCierreDeSesionPorLaApiImpideSeguirConsultandoLaApi() throws Exception {
        registrar("Alpes Api", "902000001-1", CORREO_ALPES);
        MockHttpSession sesion = iniciarSesion(CORREO_ALPES, PASSWORD, 204);
        mockMvc.perform(get("/api/procesos").session(sesion)).andExpect(status().isOk());
        Cookie token = tokenCsrf(sesion);

        mockMvc.perform(delete(RUTA_SESION).session(sesion)
                .cookie(token)
                .header(CABECERA_CSRF, token.getValue()))
                .andExpect(status().isNoContent());

        assertThat(sesion.isInvalid()).isTrue();
        mockMvc.perform(get("/api/procesos")).andExpect(status().isUnauthorized());
    }

    @Test
    void elLoginMvcYLaApiCompartenLaMismaSesion() throws Exception {
        registrar("Alpes Api", "902000001-1", CORREO_ALPES);
        MockHttpSession sesion = new MockHttpSession();
        MvcResult pagina = mockMvc.perform(get("/login").session(sesion)).andReturn();
        Matcher campo = CAMPO_CSRF.matcher(pagina.getResponse().getContentAsString());
        assertThat(campo.find()).isTrue();

        mockMvc.perform(post("/login").session(sesion)
                .cookie(pagina.getResponse().getCookie(COOKIE_CSRF))
                .param("_csrf", campo.group(1))
                .param("username", CORREO_ALPES)
                .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get(RUTA_SESION).session(sesion))
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.correo").value(CORREO_ALPES));
    }
}
