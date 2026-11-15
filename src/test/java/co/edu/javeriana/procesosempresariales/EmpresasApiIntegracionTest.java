package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
class EmpresasApiIntegracionTest {

    private static final Pattern CAMPO_CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
    private static final String RUTA_EMPRESAS = "/api/empresas";
    private static final String RUTA_EMPRESA_ACTUAL = "/api/empresas/actual";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";
    private static final String CORREO_ALPES = "admin@alpes-api.com";
    private static final String CORREO_ANDES = "admin@andes-api.com";
    private static final String PASSWORD = "Clave-Api-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private ClientePruebaJwt clienteJwt() {
        return new ClientePruebaJwt(mockMvc);
    }

    private String registro(String nombre, String nit, String correo) {
        return "{\"nombre\":\"" + nombre + "\",\"nit\":\"" + nit + "\",\"correoContacto\":\"" + correo
                + "\",\"passwordAdministrador\":\"" + PASSWORD + "\"}";
    }

    private MvcResult registrar(String nombre, String nit, String correo) throws Exception {
        return mockMvc.perform(post(RUTA_EMPRESAS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registro(nombre, nit, correo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nit").value(nit))
                .andExpect(jsonPath("$.administradorUsername").value(correo))
                .andReturn();
    }

    private AccesoJwt iniciarSesion(String correo) throws Exception {
        return clienteJwt().iniciarSesion(correo, PASSWORD);
    }

    @Test
    void laEmpresaRegistradaPorLaApiPermiteIniciarSesionYConsultarSuEmpresaConBearer() throws Exception {
        registrar("Alpes Api", "902000001-1", CORREO_ALPES);

        AccesoJwt acceso = iniciarSesion(CORREO_ALPES);

        mockMvc.perform(get(ClientePruebaJwt.RUTA_ME).with(acceso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.correo").value(CORREO_ALPES))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.empresaNombre").value("Alpes Api"));
        mockMvc.perform(get(RUTA_EMPRESA_ACTUAL).with(acceso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nit").value("902000001-1"))
                .andExpect(jsonPath("$.administradorUsername").value(CORREO_ALPES));
    }

    @Test
    void elRegistroEsPublicoNoNecesitaTokenCsrfNiCreaSesion() throws Exception {
        MvcResult resultado = registrar("Alpes Api", "902000001-1", CORREO_ALPES);

        assertThat(resultado.getRequest().getSession(false)).isNull();
        assertThat(resultado.getResponse().getCookie("JSESSIONID")).isNull();
        assertThat(usuarioRepository.findByUsername(CORREO_ALPES)).isPresent();
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

        mockMvc.perform(post(RUTA_EMPRESAS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registro("Otra Api", "902000001-1", CORREO_ANDES)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMPRESA_NIT_DUPLICADO"));
    }

    @Test
    void cadaUsuarioSoloConsultaSuPropiaEmpresa() throws Exception {
        registrar("Alpes Api", "902000001-1", CORREO_ALPES);
        registrar("Andes Api", "902000002-2", CORREO_ANDES);

        mockMvc.perform(get(RUTA_EMPRESA_ACTUAL).with(iniciarSesion(CORREO_ALPES)))
                .andExpect(jsonPath("$.nombre").value("Alpes Api"));
        mockMvc.perform(get(RUTA_EMPRESA_ACTUAL).with(iniciarSesion(CORREO_ANDES)))
                .andExpect(jsonPath("$.nombre").value("Andes Api"));
    }

    @Test
    void laConsultaDeLaEmpresaExigeUnBearerVigente() throws Exception {
        registrar("Alpes Api", "902000001-1", CORREO_ALPES);
        AccesoJwt acceso = iniciarSesion(CORREO_ALPES);
        mockMvc.perform(get(RUTA_EMPRESA_ACTUAL).with(acceso)).andExpect(status().isOk());

        mockMvc.perform(clienteJwt().conCsrf(post(ClientePruebaJwt.RUTA_LOGOUT), acceso.refresco()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(RUTA_EMPRESA_ACTUAL).with(acceso))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));
        mockMvc.perform(get(RUTA_EMPRESA_ACTUAL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elLoginWebNoAutenticaLaApiDeEmpresas() throws Exception {
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

        mockMvc.perform(get("/empresas").session(sesion))
                .andExpect(status().isOk());
        mockMvc.perform(get(RUTA_EMPRESA_ACTUAL).session(sesion))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));
        mockMvc.perform(get(ClientePruebaJwt.RUTA_ME).session(sesion))
                .andExpect(status().isUnauthorized());
        assertThat(sesion.isInvalid()).isFalse();
    }
}
