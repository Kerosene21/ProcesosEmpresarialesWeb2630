package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = ClassMode.BEFORE_CLASS)
class UsuariosApiIntegracionTest {

    private static final String RUTA_EMPRESAS = "/api/empresas";
    private static final String RUTA_USUARIOS = "/api/usuarios";
    private static final String RUTA_PROCESOS = "/api/procesos";
    private static final String CORREO_ALPES = "admin@alpes-usuarios.com";
    private static final String CORREO_ANDES = "admin@andes-usuarios.com";
    private static final String CORREO_EDITOR = "editor@alpes-usuarios.com";
    private static final String CORREO_EDITOR_DOS = "editor2@alpes-usuarios.com";
    private static final String CORREO_LECTURA = "lectura@alpes-usuarios.com";
    private static final String PASSWORD = "Clave-Usuarios-2026";
    private static final String CODIGO = "$.codigo";
    private static final String NO_AUTORIZADO = "USUARIO_NO_AUTORIZADO";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProcesoRepository procesoRepository;

    private ClientePruebaJwt clienteJwt() {
        return new ClientePruebaJwt(mockMvc);
    }

    private void registrar(String nombre, String nit, String correo) throws Exception {
        mockMvc.perform(post(RUTA_EMPRESAS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"" + nombre + "\",\"nit\":\"" + nit + "\",\"correoContacto\":\"" + correo
                        + "\",\"passwordAdministrador\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
    }

    private AccesoJwt iniciarSesion(String correo) throws Exception {
        return clienteJwt().iniciarSesion(correo, PASSWORD);
    }

    private void loginRechazado(String correo) throws Exception {
        MvcResult resultado = clienteJwt().intentarLogin(correo, PASSWORD);
        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(resultado.getResponse().getContentAsString()).contains("CREDENCIALES_INVALIDAS");
    }

    private int estadoDelRefresco(AccesoJwt acceso) throws Exception {
        return clienteJwt().intentarRefrescar(acceso.refresco()).getResponse().getStatus();
    }

    private String usuario(String correo, String rol) {
        return "{\"correo\":\"" + correo + "\",\"password\":\"" + PASSWORD + "\",\"rol\":\"" + rol + "\"}";
    }

    private long crearUsuario(AccesoJwt administrador, String correo, String rol) throws Exception {
        MvcResult resultado = mockMvc.perform(post(RUTA_USUARIOS).with(administrador)
                .contentType(MediaType.APPLICATION_JSON)
                .content(usuario(correo, rol)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private AccesoJwt administradorDeAlpes() throws Exception {
        registrar("Alpes Usuarios", "903000001-1", CORREO_ALPES);
        return iniciarSesion(CORREO_ALPES);
    }

    private Usuario guardado(String correo) {
        return usuarioRepository.findByUsername(correo).orElseThrow();
    }

    private String proceso(String nombre) {
        return "{\"nombre\":\"" + nombre + "\",\"descripcion\":\"Proceso comercial\",\"categoria\":\"Comercial\"}";
    }

    private long idCreado(ResultActions resultado) throws Exception {
        String cuerpo = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private long crearProceso(AccesoJwt acceso, String nombre) throws Exception {
        return idCreado(mockMvc.perform(post(RUTA_PROCESOS).with(acceso)
                .contentType(MediaType.APPLICATION_JSON)
                .content(proceso(nombre))));
    }

    private void desactivar(AccesoJwt administrador, long usuarioId) throws Exception {
        mockMvc.perform(post(RUTA_USUARIOS + "/" + usuarioId + "/desactivacion").with(administrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    private MockHttpSession sesionDeNavegador(String correo) throws Exception {
        return clienteJwt().sesionDeNavegador(correo, PASSWORD);
    }

    @Test
    void sinSesionLaApiDeUsuariosResponde401() throws Exception {
        mockMvc.perform(get(RUTA_USUARIOS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value("USUARIO_NO_AUTORIZADO"));
    }

    @Test
    void elAdministradorCreaUnUsuarioQueIniciaSesionYNuncaSeExponeLaContrasena() throws Exception {
        AccesoJwt administrador = administradorDeAlpes();

        mockMvc.perform(post(RUTA_USUARIOS).with(administrador)
                .contentType(MediaType.APPLICATION_JSON)
                .content(usuario("Editor@Alpes-Usuarios.com", "EDITOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correo").value(CORREO_EDITOR))
                .andExpect(jsonPath("$.rol").value("EDITOR"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(guardado(CORREO_EDITOR).getPassword()).isNotEqualTo(PASSWORD).startsWith("$2");
        iniciarSesion(CORREO_EDITOR);
        mockMvc.perform(get(RUTA_USUARIOS).with(administrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].correo", contains(CORREO_ALPES, CORREO_EDITOR)))
                .andExpect(jsonPath("$[*].password").isEmpty());
    }

    @Test
    void laEscrituraExigeElBearerPeroYaNoElTokenCsrf() throws Exception {
        AccesoJwt administrador = administradorDeAlpes();

        mockMvc.perform(clienteJwt().conCsrf(post(RUTA_USUARIOS))
                .contentType(MediaType.APPLICATION_JSON)
                .content(usuario(CORREO_EDITOR, "EDITOR")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value(NO_AUTORIZADO));
        assertThat(usuarioRepository.findByUsername(CORREO_EDITOR)).isEmpty();

        mockMvc.perform(post(RUTA_USUARIOS).with(administrador)
                .contentType(MediaType.APPLICATION_JSON)
                .content(usuario(CORREO_EDITOR, "EDITOR")))
                .andExpect(status().isCreated());
        assertThat(guardado(CORREO_EDITOR).getRol()).isEqualTo(RolUsuario.EDITOR);
    }

    @Test
    void unEditorNoPuedeConsultarNiCrearUsuarios() throws Exception {
        crearUsuario(administradorDeAlpes(), CORREO_EDITOR, "EDITOR");
        AccesoJwt editor = iniciarSesion(CORREO_EDITOR);

        mockMvc.perform(get(RUTA_USUARIOS).with(editor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
        mockMvc.perform(post(RUTA_USUARIOS).with(editor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(usuario("otro@alpes-usuarios.com", "ADMINISTRADOR")))
                .andExpect(status().isForbidden());

        assertThat(usuarioRepository.findByUsername("otro@alpes-usuarios.com")).isEmpty();
    }

    @Test
    void elCorreoEsUnicoEntreEmpresasYLosDatosSeValidan() throws Exception {
        registrar("Alpes Usuarios", "903000001-1", CORREO_ALPES);
        registrar("Andes Usuarios", "903000002-2", CORREO_ANDES);
        AccesoJwt andes = iniciarSesion(CORREO_ANDES);

        mockMvc.perform(post(RUTA_USUARIOS).with(andes)
                .contentType(MediaType.APPLICATION_JSON)
                .content(usuario(CORREO_ALPES, "EDITOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("USUARIO_CORREO_EN_USO"));
        mockMvc.perform(post(RUTA_USUARIOS).with(andes)
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
        AccesoJwt andes = iniciarSesion(CORREO_ANDES);

        mockMvc.perform(get(RUTA_USUARIOS).with(andes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].correo", containsInAnyOrder(CORREO_ANDES)));
        mockMvc.perform(get(RUTA_USUARIOS + "/" + editor).with(andes))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(RUTA_USUARIOS + "/" + editor + "/rol").with(andes)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(RUTA_USUARIOS + "/" + editor + "/desactivacion").with(andes))
                .andExpect(status().isNotFound());

        Usuario intacto = guardado(CORREO_EDITOR);
        assertThat(intacto.getRol()).isEqualTo(RolUsuario.EDITOR);
        assertThat(intacto.isActivo()).isTrue();
    }

    @Test
    void elAdministradorCambiaElRolDeOtroUsuarioPeroNoElSuyo() throws Exception {
        AccesoJwt administrador = administradorDeAlpes();
        long editor = crearUsuario(administrador, CORREO_EDITOR, "EDITOR");
        long propio = guardado(CORREO_ALPES).getId();

        mockMvc.perform(put(RUTA_USUARIOS + "/" + editor + "/rol").with(administrador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"SOLO_LECTURA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("SOLO_LECTURA"));
        mockMvc.perform(put(RUTA_USUARIOS + "/" + propio + "/rol").with(administrador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"EDITOR\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));

        assertThat(guardado(CORREO_EDITOR).getRol()).isEqualTo(RolUsuario.SOLO_LECTURA);
        assertThat(guardado(CORREO_ALPES).getRol()).isEqualTo(RolUsuario.ADMINISTRADOR);
    }

    @Test
    void laDesactivacionEsLogicaEImpideIniciarSesionPeroNoSobreLaPropiaCuenta() throws Exception {
        AccesoJwt administrador = administradorDeAlpes();
        long editor = crearUsuario(administrador, CORREO_EDITOR, "EDITOR");
        long propio = guardado(CORREO_ALPES).getId();

        mockMvc.perform(post(RUTA_USUARIOS + "/" + editor + "/desactivacion").with(administrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        mockMvc.perform(post(RUTA_USUARIOS + "/" + propio + "/desactivacion").with(administrador))
                .andExpect(status().isForbidden());

        assertThat(guardado(CORREO_EDITOR).isActivo()).isFalse();
        assertThat(guardado(CORREO_ALPES).isActivo()).isTrue();
        loginRechazado(CORREO_EDITOR);
        mockMvc.perform(get(RUTA_USUARIOS + "/" + editor).with(administrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void losTokensEmitidosAntesDeLaDesactivacionDejanDeLeerModificarYRenovarse() throws Exception {
        AccesoJwt administrador = administradorDeAlpes();
        long editor = crearUsuario(administrador, CORREO_EDITOR, "EDITOR");
        long empresa = guardado(CORREO_ALPES).getEmpresa().getId();
        AccesoJwt escritorio = iniciarSesion(CORREO_EDITOR);
        AccesoJwt movil = iniciarSesion(CORREO_EDITOR);
        long compras = crearProceso(escritorio, "Compras");
        mockMvc.perform(get(RUTA_PROCESOS + "/" + compras).with(movil))
                .andExpect(status().isOk());

        desactivar(administrador, editor);

        mockMvc.perform(post(RUTA_PROCESOS).with(escritorio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(proceso("Ventas")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value(NO_AUTORIZADO));
        mockMvc.perform(put(RUTA_PROCESOS + "/" + compras).with(escritorio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(proceso("Compras modificadas")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value(NO_AUTORIZADO));
        mockMvc.perform(get(RUTA_PROCESOS + "/" + compras).with(movil))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value(NO_AUTORIZADO));
        mockMvc.perform(get(ClientePruebaJwt.RUTA_ME).with(movil))
                .andExpect(status().isUnauthorized());

        assertThat(estadoDelRefresco(escritorio)).isEqualTo(401);
        assertThat(estadoDelRefresco(movil)).isEqualTo(401);
        assertThat(procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(empresa, "Ventas")).isFalse();
        assertThat(procesoRepository.findById(compras).orElseThrow().getNombre()).isEqualTo("Compras");
        loginRechazado(CORREO_EDITOR);
    }

    @Test
    void lasSesionesWebAbiertasAntesDeLaDesactivacionDejanDeLeerYModificar() throws Exception {
        AccesoJwt administrador = administradorDeAlpes();
        long editor = crearUsuario(administrador, CORREO_EDITOR, "EDITOR");
        long empresa = guardado(CORREO_ALPES).getEmpresa().getId();
        long compras = crearProceso(administrador, "Compras");
        MockHttpSession listado = sesionDeNavegador(CORREO_EDITOR);
        MockHttpSession detalle = sesionDeNavegador(CORREO_EDITOR);
        MockHttpSession creacion = sesionDeNavegador(CORREO_EDITOR);
        mockMvc.perform(get("/procesos/" + compras).session(detalle))
                .andExpect(status().isOk());

        desactivar(administrador, editor);

        mockMvc.perform(get("/procesos").session(listado))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/procesos/" + compras).session(detalle))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/procesos").session(creacion).with(csrf())
                .param("nombre", "Ventas")
                .param("descripcion", "Proceso comercial")
                .param("categoria", "Comercial"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        assertThat(listado.isInvalid()).isTrue();
        assertThat(detalle.isInvalid()).isTrue();
        assertThat(creacion.isInvalid()).isTrue();
        assertThat(procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(empresa, "Ventas")).isFalse();
        mockMvc.perform(formLogin().user(CORREO_EDITOR).password(PASSWORD))
                .andExpect(unauthenticated());
        loginRechazado(CORREO_EDITOR);
    }

    @Test
    void desactivarAUnUsuarioNoAfectaLasSesionesNiLosRolesDeLosDemas() throws Exception {
        AccesoJwt administrador = administradorDeAlpes();
        long editor = crearUsuario(administrador, CORREO_EDITOR, "EDITOR");
        crearUsuario(administrador, CORREO_EDITOR_DOS, "EDITOR");
        crearUsuario(administrador, CORREO_LECTURA, "SOLO_LECTURA");
        long empresa = guardado(CORREO_ALPES).getEmpresa().getId();
        AccesoJwt editorDos = iniciarSesion(CORREO_EDITOR_DOS);
        AccesoJwt lectura = iniciarSesion(CORREO_LECTURA);

        desactivar(administrador, editor);

        mockMvc.perform(get(RUTA_USUARIOS).with(administrador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].correo",
                        containsInAnyOrder(CORREO_ALPES, CORREO_EDITOR, CORREO_EDITOR_DOS, CORREO_LECTURA)));
        crearProceso(editorDos, "Compras");
        mockMvc.perform(get(RUTA_USUARIOS).with(editorDos))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
        mockMvc.perform(get(RUTA_PROCESOS).with(lectura))
                .andExpect(status().isOk());
        mockMvc.perform(post(RUTA_PROCESOS).with(lectura)
                .contentType(MediaType.APPLICATION_JSON)
                .content(proceso("Ventas")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
        mockMvc.perform(get(ClientePruebaJwt.RUTA_ME).with(lectura))
                .andExpect(jsonPath("$.autenticado").value(true))
                .andExpect(jsonPath("$.rol").value("SOLO_LECTURA"));

        for (AccesoJwt acceso : List.of(administrador, editorDos, lectura)) {
            assertThat(estadoDelRefresco(acceso)).as("refresco de %s", acceso.correo()).isEqualTo(200);
        }
        assertThat(procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(empresa, "Compras")).isTrue();
        assertThat(procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(empresa, "Ventas")).isFalse();
    }
}
