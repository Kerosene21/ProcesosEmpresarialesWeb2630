package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
class UsuariosApiIntegracionTest {

    private static final String RUTA_SESION = "/api/sesion";
    private static final String RUTA_EMPRESAS = "/api/empresas";
    private static final String RUTA_USUARIOS = "/api/usuarios";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";
    private static final String CABECERA_CSRF = "X-XSRF-TOKEN";
    private static final String CORREO_ALPES = "admin@alpes-usuarios.com";
    private static final String CORREO_ANDES = "admin@andes-usuarios.com";
    private static final String CORREO_EDITOR = "editor@alpes-usuarios.com";
    private static final String PASSWORD = "Clave-Usuarios-2026";
    private static final String CODIGO = "$.codigo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Cookie tokenCsrf(MockHttpSession sesion) throws Exception {
        MvcResult resultado = mockMvc.perform(get(RUTA_SESION).session(sesion)).andReturn();
        return resultado.getResponse().getCookie(COOKIE_CSRF);
    }

    private MockHttpServletRequestBuilder conCsrf(MockHttpServletRequestBuilder peticion, MockHttpSession sesion)
            throws Exception {
        Cookie token = tokenCsrf(sesion);
        return peticion.session(sesion).cookie(token).header(CABECERA_CSRF, token.getValue());
    }

    private void registrar(String nombre, String nit, String correo) throws Exception {
        MockHttpSession anonima = new MockHttpSession();
        mockMvc.perform(conCsrf(post(RUTA_EMPRESAS), anonima)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"" + nombre + "\",\"nit\":\"" + nit + "\",\"correoContacto\":\"" + correo
                        + "\",\"passwordAdministrador\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
    }

    private MockHttpSession iniciarSesion(String correo, int estadoEsperado) throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        MvcResult resultado = mockMvc.perform(conCsrf(post(RUTA_SESION), sesion)
                .param("correo", correo)
                .param("password", PASSWORD))
                .andExpect(status().is(estadoEsperado))
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private String usuario(String correo, String rol) {
        return "{\"correo\":\"" + correo + "\",\"password\":\"" + PASSWORD + "\",\"rol\":\"" + rol + "\"}";
    }

    private long crearUsuario(MockHttpSession administrador, String correo, String rol) throws Exception {
        MvcResult resultado = mockMvc.perform(conCsrf(post(RUTA_USUARIOS), administrador)
                .contentType(MediaType.APPLICATION_JSON)
                .content(usuario(correo, rol)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private MockHttpSession administradorDeAlpes() throws Exception {
        registrar("Alpes Usuarios", "903000001-1", CORREO_ALPES);
        return iniciarSesion(CORREO_ALPES, 204);
    }

    private Usuario guardado(String correo) {
        return usuarioRepository.findByUsername(correo).orElseThrow();
    }

    @Test
    void sinSesionLaApiDeUsuariosResponde401() throws Exception {
        mockMvc.perform(get(RUTA_USUARIOS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value("USUARIO_NO_AUTORIZADO"));
    }

    @Test
    void elAdministradorCreaUnUsuarioQueIniciaSesionYNuncaSeExponeLaContrasena() throws Exception {
        MockHttpSession administrador = administradorDeAlpes();

        mockMvc.perform(conCsrf(post(RUTA_USUARIOS), administrador)
                .contentType(MediaType.APPLICATION_JSON)
                .content(usuario("Editor@Alpes-Usuarios.com", "EDITOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correo").value(CORREO_EDITOR))
                .andExpect(jsonPath("$.rol").value("EDITOR"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(guardado(CORREO_EDITOR).getPassword()).isNotEqualTo(PASSWORD).startsWith("$2");
        iniciarSesion(CORREO_EDITOR, 204);
        mockMvc.perform(get(RUTA_USUARIOS).session(administrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].correo", contains(CORREO_ALPES, CORREO_EDITOR)))
                .andExpect(jsonPath("$[*].password").isEmpty());
    }

    @Test
    void laEscrituraSinTokenCsrfSeRechaza() throws Exception {
        MockHttpSession administrador = administradorDeAlpes();

        mockMvc.perform(post(RUTA_USUARIOS).session(administrador)
                .contentType(MediaType.APPLICATION_JSON)
                .content(usuario(CORREO_EDITOR, "EDITOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));

        assertThat(usuarioRepository.findByUsername(CORREO_EDITOR)).isEmpty();
    }

    @Test
    void unEditorNoPuedeConsultarNiCrearUsuarios() throws Exception {
        crearUsuario(administradorDeAlpes(), CORREO_EDITOR, "EDITOR");
        MockHttpSession editor = iniciarSesion(CORREO_EDITOR, 204);

        mockMvc.perform(get(RUTA_USUARIOS).session(editor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
        mockMvc.perform(conCsrf(post(RUTA_USUARIOS), editor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(usuario("otro@alpes-usuarios.com", "ADMINISTRADOR")))
                .andExpect(status().isForbidden());

        assertThat(usuarioRepository.findByUsername("otro@alpes-usuarios.com")).isEmpty();
    }

    @Test
    void elCorreoEsUnicoEntreEmpresasYLosDatosSeValidan() throws Exception {
        registrar("Alpes Usuarios", "903000001-1", CORREO_ALPES);
        registrar("Andes Usuarios", "903000002-2", CORREO_ANDES);
        MockHttpSession andes = iniciarSesion(CORREO_ANDES, 204);

        mockMvc.perform(conCsrf(post(RUTA_USUARIOS), andes)
                .contentType(MediaType.APPLICATION_JSON)
                .content(usuario(CORREO_ALPES, "EDITOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("USUARIO_CORREO_EN_USO"));
        mockMvc.perform(conCsrf(post(RUTA_USUARIOS), andes)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"no-es-correo\",\"password\":\"corta\",\"rol\":\"EDITOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("DATOS_NO_VALIDOS"))
                .andExpect(jsonPath("$.campos.correo").exists())
                .andExpect(jsonPath("$.campos.password").exists());
    }

    @Test
    void unAdministradorNoVeNiModificaUsuariosDeOtraEmpresa() throws Exception {
        long editor = crearUsuario(administradorDeAlpes(), CORREO_EDITOR, "EDITOR");
        registrar("Andes Usuarios", "903000002-2", CORREO_ANDES);
        MockHttpSession andes = iniciarSesion(CORREO_ANDES, 204);

        mockMvc.perform(get(RUTA_USUARIOS).session(andes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].correo", containsInAnyOrder(CORREO_ANDES)));
        mockMvc.perform(get(RUTA_USUARIOS + "/" + editor).session(andes))
                .andExpect(status().isNotFound());
        mockMvc.perform(conCsrf(put(RUTA_USUARIOS + "/" + editor + "/rol"), andes)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(conCsrf(post(RUTA_USUARIOS + "/" + editor + "/desactivacion"), andes))
                .andExpect(status().isNotFound());

        Usuario intacto = guardado(CORREO_EDITOR);
        assertThat(intacto.getRol()).isEqualTo(RolUsuario.EDITOR);
        assertThat(intacto.isActivo()).isTrue();
    }

    @Test
    void elAdministradorCambiaElRolDeOtroUsuarioPeroNoElSuyo() throws Exception {
        MockHttpSession administrador = administradorDeAlpes();
        long editor = crearUsuario(administrador, CORREO_EDITOR, "EDITOR");
        long propio = guardado(CORREO_ALPES).getId();

        mockMvc.perform(conCsrf(put(RUTA_USUARIOS + "/" + editor + "/rol"), administrador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"SOLO_LECTURA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("SOLO_LECTURA"));
        mockMvc.perform(conCsrf(put(RUTA_USUARIOS + "/" + propio + "/rol"), administrador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"EDITOR\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));

        assertThat(guardado(CORREO_EDITOR).getRol()).isEqualTo(RolUsuario.SOLO_LECTURA);
        assertThat(guardado(CORREO_ALPES).getRol()).isEqualTo(RolUsuario.ADMINISTRADOR);
    }

    @Test
    void laDesactivacionEsLogicaEImpideIniciarSesionPeroNoSobreLaPropiaCuenta() throws Exception {
        MockHttpSession administrador = administradorDeAlpes();
        long editor = crearUsuario(administrador, CORREO_EDITOR, "EDITOR");
        long propio = guardado(CORREO_ALPES).getId();

        mockMvc.perform(conCsrf(post(RUTA_USUARIOS + "/" + editor + "/desactivacion"), administrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        mockMvc.perform(conCsrf(post(RUTA_USUARIOS + "/" + propio + "/desactivacion"), administrador))
                .andExpect(status().isForbidden());

        assertThat(guardado(CORREO_EDITOR).isActivo()).isFalse();
        assertThat(guardado(CORREO_ALPES).isActivo()).isTrue();
        iniciarSesion(CORREO_EDITOR, 401);
        mockMvc.perform(get(RUTA_USUARIOS + "/" + editor).session(administrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }
}
