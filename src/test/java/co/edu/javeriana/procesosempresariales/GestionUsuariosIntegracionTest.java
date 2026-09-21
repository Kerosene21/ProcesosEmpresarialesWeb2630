package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CambiarRolUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EmpresaRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.dto.UsuarioRespuestaDto;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GestionUsuariosIntegracionTest {

    private static final String ADMIN_ALPES = "admin@alpes-usuarios.com";
    private static final String ADMIN_ANDES = "admin@andes-usuarios.com";
    private static final String EDITOR_ALPES = "editor@alpes-usuarios.com";
    private static final String EDITOR_ANDES = "editor@andes-usuarios.com";
    private static final String PASSWORD = "Clave-Usuarios-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProcesoService procesoService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProcesoRepository procesoRepository;

    @Autowired
    private HistorialProcesoRepository historialProcesoRepository;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private EmpresaRespuestaDto registrarAlpes() {
        return empresaService.registrar(
                new RegistroEmpresaDto("Alpes Usuarios", "902000001-1", ADMIN_ALPES, PASSWORD));
    }

    private EmpresaRespuestaDto registrarAndes() {
        return empresaService.registrar(
                new RegistroEmpresaDto("Andes Usuarios", "902000002-2", ADMIN_ANDES, PASSWORD));
    }

    private UsuarioRespuestaDto crearEditor(String correo, String administrador) {
        return usuarioService.crear(new CrearUsuarioDto(correo, PASSWORD, RolUsuario.EDITOR), administrador);
    }

    private MockHttpSession iniciarSesion(String correo) throws Exception {
        MvcResult resultado = mockMvc.perform(formLogin().user(correo).password(PASSWORD))
                .andExpect(authenticated().withUsername(correo))
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    @Test
    void unUsuarioCreadoPorElAdministradorPuedeIniciarSesionConSusCredenciales() throws Exception {
        registrarAlpes();

        crearEditor(EDITOR_ALPES, ADMIN_ALPES);

        mockMvc.perform(formLogin().user(EDITOR_ALPES).password(PASSWORD))
                .andExpect(authenticated().withUsername(EDITOR_ALPES))
                .andExpect(redirectedUrl("/empresas"));
    }

    @Test
    void laContrasenaDelUsuarioCreadoQuedaHasheadaEnLaBaseDeDatos() {
        registrarAlpes();

        crearEditor(EDITOR_ALPES, ADMIN_ALPES);

        Usuario editor = usuarioRepository.findByUsername(EDITOR_ALPES).orElseThrow();
        assertThat(editor.getPassword()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, editor.getPassword())).isTrue();
        assertThat(editor.isActivo()).isTrue();
        assertThat(editor.getEmpresa().getNit()).isEqualTo("902000001-1");
    }

    @Test
    void elUsuarioDesactivadoYaNoPuedeIniciarSesion() throws Exception {
        registrarAlpes();
        UsuarioRespuestaDto editor = crearEditor(EDITOR_ALPES, ADMIN_ALPES);
        mockMvc.perform(formLogin().user(EDITOR_ALPES).password(PASSWORD)).andExpect(authenticated());

        usuarioService.desactivar(editor.getId(), ADMIN_ALPES);

        mockMvc.perform(formLogin().user(EDITOR_ALPES).password(PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void elUsuarioDesactivadoSigueEnLaBaseDeDatosConActivoEnFalso() {
        registrarAlpes();
        UsuarioRespuestaDto editor = crearEditor(EDITOR_ALPES, ADMIN_ALPES);
        long usuariosAntes = usuarioRepository.count();

        usuarioService.desactivar(editor.getId(), ADMIN_ALPES);

        assertThat(usuarioRepository.count()).isEqualTo(usuariosAntes);
        Usuario persistido = usuarioRepository.findByUsername(EDITOR_ALPES).orElseThrow();
        assertThat(persistido.isActivo()).isFalse();
        assertThat(persistido.getId()).isEqualTo(editor.getId());
        assertThat(persistido.getRol()).isEqualTo(RolUsuario.EDITOR);
    }

    @Test
    void desactivarUnUsuarioNoEliminaLosProcesosDeLaEmpresa() {
        EmpresaRespuestaDto alpes = registrarAlpes();
        UsuarioRespuestaDto editor = crearEditor(EDITOR_ALPES, ADMIN_ALPES);
        ProcesoRespuestaDto proceso = procesoService.crear(
                new CrearProcesoDto("Ventas", "Proceso comercial", "Comercial"), EDITOR_ALPES);
        long procesosAntes = procesoRepository.count();

        usuarioService.desactivar(editor.getId(), ADMIN_ALPES);

        assertThat(procesoRepository.count()).isEqualTo(procesosAntes);
        assertThat(procesoRepository.findById(proceso.getId())).isPresent();
        assertThat(procesoRepository.findById(proceso.getId()).orElseThrow().getEmpresa().getId())
                .isEqualTo(alpes.getId());
    }

    @Test
    void desactivarUnUsuarioNoEliminaSuHistorialDeEdiciones() {
        registrarAlpes();
        UsuarioRespuestaDto editor = crearEditor(EDITOR_ALPES, ADMIN_ALPES);
        ProcesoRespuestaDto proceso = procesoService.crear(
                new CrearProcesoDto("Ventas", "Proceso comercial", "Comercial"), EDITOR_ALPES);
        procesoService.editar(proceso.getId(),
                new EditarProcesoDto("Ventas Corporativas", "Descripcion actualizada", "Operaciones",
                        EstadoProceso.PUBLICADO),
                EDITOR_ALPES);
        long historialAntes = historialProcesoRepository.count();
        assertThat(historialAntes).isPositive();

        usuarioService.desactivar(editor.getId(), ADMIN_ALPES);

        assertThat(historialProcesoRepository.count()).isEqualTo(historialAntes);
        List<HistorialProceso> historial = historialProcesoRepository.findAll();
        assertThat(historial).isNotEmpty();
        assertThat(historial.get(0).getUsuario().getId()).isEqualTo(editor.getId());
        assertThat(historial.get(0).getProceso().getId()).isEqualTo(proceso.getId());
    }

    @Test
    void elProcesoSigueDisponibleParaElRestoDeLaEmpresaTrasDesactivarASuEditor() throws Exception {
        registrarAlpes();
        UsuarioRespuestaDto editor = crearEditor(EDITOR_ALPES, ADMIN_ALPES);
        ProcesoRespuestaDto proceso = procesoService.crear(
                new CrearProcesoDto("Ventas", "Proceso comercial", "Comercial"), EDITOR_ALPES);

        usuarioService.desactivar(editor.getId(), ADMIN_ALPES);
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(get("/procesos/" + proceso.getId()).session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/proceso"));
    }

    @Test
    void elAdministradorSoloVeLosUsuariosDeSuPropiaEmpresa() {
        registrarAlpes();
        registrarAndes();
        crearEditor(EDITOR_ALPES, ADMIN_ALPES);
        crearEditor(EDITOR_ANDES, ADMIN_ANDES);

        List<UsuarioRespuestaDto> deAlpes = usuarioService.listarDeMiEmpresa(ADMIN_ALPES);
        List<UsuarioRespuestaDto> deAndes = usuarioService.listarDeMiEmpresa(ADMIN_ANDES);

        assertThat(deAlpes).extracting(UsuarioRespuestaDto::getCorreo)
                .containsExactlyInAnyOrder(ADMIN_ALPES, EDITOR_ALPES);
        assertThat(deAndes).extracting(UsuarioRespuestaDto::getCorreo)
                .containsExactlyInAnyOrder(ADMIN_ANDES, EDITOR_ANDES);
    }

    @Test
    void elAdministradorDeUnaEmpresaNoPuedeConsultarUnUsuarioDeOtra() throws Exception {
        registrarAlpes();
        registrarAndes();
        UsuarioRespuestaDto editorAndes = crearEditor(EDITOR_ANDES, ADMIN_ANDES);
        MockHttpSession sesionAlpes = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(get("/usuarios/" + editorAndes.getId()).session(sesionAlpes))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/problema"));
    }

    @Test
    void elAdministradorDeUnaEmpresaNoPuedeDesactivarUnUsuarioDeOtra() throws Exception {
        registrarAlpes();
        registrarAndes();
        UsuarioRespuestaDto editorAndes = crearEditor(EDITOR_ANDES, ADMIN_ANDES);
        MockHttpSession sesionAlpes = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(post("/usuarios/" + editorAndes.getId() + "/desactivar")
                .session(sesionAlpes).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(usuarioRepository.findByUsername(EDITOR_ANDES).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void elAdministradorDeUnaEmpresaNoPuedeCambiarElRolDeUnUsuarioDeOtra() {
        registrarAlpes();
        registrarAndes();
        UsuarioRespuestaDto editorAndes = crearEditor(EDITOR_ANDES, ADMIN_ANDES);

        assertThatThrownBy(() -> usuarioService.cambiarRol(editorAndes.getId(),
                new CambiarRolUsuarioDto(RolUsuario.ADMINISTRADOR), ADMIN_ALPES))
                .isInstanceOf(co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException.class);

        assertThat(usuarioRepository.findByUsername(EDITOR_ANDES).orElseThrow().getRol())
                .isEqualTo(RolUsuario.EDITOR);
    }

    @Test
    void elUsuarioCreadoRecibeLasAutoridadesDeSuRolDeAcceso() {
        registrarAlpes();
        usuarioService.crear(new CrearUsuarioDto(EDITOR_ALPES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ALPES);

        UserDetails detalles = userDetailsService.loadUserByUsername(EDITOR_ALPES);

        assertThat(detalles.getUsername()).isEqualTo(EDITOR_ALPES);
        assertThat(detalles.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_SOLO_LECTURA");
        assertThat(detalles.isEnabled()).isTrue();
    }

    @Test
    void elCorreoDeUsuarioEsUnicoGlobalmenteEnLaBaseDeDatos() {
        registrarAlpes();
        registrarAndes();
        crearEditor(EDITOR_ALPES, ADMIN_ALPES);
        Usuario duplicado = new Usuario(null, EDITOR_ALPES, passwordEncoder.encode(PASSWORD), RolUsuario.EDITOR,
                true, usuarioRepository.findByUsername(ADMIN_ANDES).orElseThrow().getEmpresa());

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unUsuarioSinEmpresaNoSePuedePersistir() {
        Usuario sinEmpresa = new Usuario(null, "huerfano@alpes-usuarios.com", passwordEncoder.encode(PASSWORD),
                RolUsuario.EDITOR, true, null);

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(sinEmpresa))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
