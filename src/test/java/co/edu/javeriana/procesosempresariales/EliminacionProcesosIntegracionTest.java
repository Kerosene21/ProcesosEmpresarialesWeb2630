package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.exception.NombreProcesoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EliminacionProcesosIntegracionTest {

    private static final String ADMIN_ALPES = "admin@alpes-eliminar.com";
    private static final String EDITOR_ALPES = "editor@alpes-eliminar.com";
    private static final String LECTOR_ALPES = "lector@alpes-eliminar.com";
    private static final String ADMIN_ANDES = "admin@andes-eliminar.com";
    private static final String PASSWORD = "Clave-Eliminar-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProcesoService procesoService;

    @Autowired
    private ProcesoRepository procesoRepository;

    @Autowired
    private HistorialProcesoRepository historialProcesoRepository;

    private void registrarAlpes() {
        empresaService.registrar(new RegistroEmpresaDto("Alpes Eliminar", "904000001-1", ADMIN_ALPES, PASSWORD));
        usuarioService.crear(new CrearUsuarioDto(EDITOR_ALPES, PASSWORD, RolUsuario.EDITOR), ADMIN_ALPES);
        usuarioService.crear(new CrearUsuarioDto(LECTOR_ALPES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ALPES);
    }

    private void registrarAndes() {
        empresaService.registrar(new RegistroEmpresaDto("Andes Eliminar", "904000002-2", ADMIN_ANDES, PASSWORD));
    }

    private MockHttpSession iniciarSesion(String correo) throws Exception {
        MvcResult resultado = mockMvc.perform(formLogin().user(correo).password(PASSWORD))
                .andExpect(authenticated().withUsername(correo))
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private ProcesoRespuestaDto crearProceso(String nombre, String autor) {
        return procesoService.crear(new CrearProcesoDto(nombre, "Proceso comercial", "Comercial"), autor);
    }

    @Test
    void elProcesoEliminadoSigueEnLaTablaMarcadoComoEliminado() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        long procesosAntes = procesoRepository.count();

        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);

        assertThat(procesoRepository.count()).isEqualTo(procesosAntes);
        Proceso persistido = procesoRepository.findById(proceso.getId()).orElseThrow();
        assertThat(persistido.isEliminado()).isTrue();
        assertThat(persistido.getNombre()).isEqualTo("Ventas");
        assertThat(persistido.getEstado()).isEqualTo(EstadoProceso.BORRADOR);
    }

    @Test
    void eliminarUnProcesoNoArrastraSuPoolNiCambiaSuEmpresa() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        Proceso antes = procesoRepository.findById(proceso.getId()).orElseThrow();
        Long poolId = antes.getPool().getId();
        Long empresaId = antes.getEmpresa().getId();

        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);

        Proceso despues = procesoRepository.findById(proceso.getId()).orElseThrow();
        assertThat(despues.getPool()).isNotNull();
        assertThat(despues.getPool().getId()).isEqualTo(poolId);
        assertThat(despues.getEmpresa().getId()).isEqualTo(empresaId);
    }

    @Test
    void eliminarConservaElHistorialAnteriorYAgregaLaEntradaDeEliminacion() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        procesoService.editar(proceso.getId(), new EditarProcesoDto("Ventas", "Proceso comercial", "Comercial",
                EstadoProceso.PUBLICADO), EDITOR_ALPES);
        long historialAntes = historialProcesoRepository.count();
        assertThat(historialAntes).isPositive();

        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);

        assertThat(historialProcesoRepository.count()).isEqualTo(historialAntes + 1);
        List<HistorialProcesoRespuestaDto> historial =
                procesoService.consultarHistorial(proceso.getId(), ADMIN_ALPES);
        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).getCambiosRealizados()).isEqualTo("proceso eliminado");
        assertThat(historial.get(0).getUsuarioCorreo()).isEqualTo(ADMIN_ALPES);
        assertThat(historial.get(0).getEstadoAnterior()).isEqualTo("PUBLICADO");
        assertThat(historial.get(1).getCambiosRealizados()).isEqualTo("estado: 'BORRADOR' -> 'PUBLICADO'");
        assertThat(historial.get(1).getUsuarioCorreo()).isEqualTo(EDITOR_ALPES);
    }

    @Test
    void elHistorialDeUnProcesoEliminadoSigueSiendoConsultablePorLaWeb() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);
        MockHttpSession sesion = iniciarSesion(LECTOR_ALPES);

        mockMvc.perform(get("/procesos/" + proceso.getId() + "/historial").session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/historial"));
        mockMvc.perform(get("/procesos/" + proceso.getId()).session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/proceso"));
    }

    @Test
    void unAdministradorConfirmaYEliminaDesdeLaWeb() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(get("/procesos/" + proceso.getId() + "/eliminar").session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/confirmareliminacion"));
        assertThat(procesoRepository.findById(proceso.getId()).orElseThrow().isEliminado()).isFalse();

        mockMvc.perform(post("/procesos/" + proceso.getId() + "/eliminar").session(sesion).with(csrf()))
                .andExpect(redirectedUrl("/procesos/" + proceso.getId()));

        assertThat(procesoRepository.findById(proceso.getId()).orElseThrow().isEliminado()).isTrue();
    }

    @Test
    void abrirLaConfirmacionYNoConfirmarDejaElProcesoIntacto() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        long historialAntes = historialProcesoRepository.count();
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(get("/procesos/" + proceso.getId() + "/eliminar").session(sesion))
                .andExpect(status().isOk());
        mockMvc.perform(get("/procesos/" + proceso.getId()).session(sesion))
                .andExpect(status().isOk());

        assertThat(procesoRepository.findById(proceso.getId()).orElseThrow().isEliminado()).isFalse();
        assertThat(historialProcesoRepository.count()).isEqualTo(historialAntes);
    }

    @Test
    void unEditorNoPuedeAbrirLaConfirmacionNiEliminarDesdeLaWeb() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        MockHttpSession sesion = iniciarSesion(EDITOR_ALPES);

        mockMvc.perform(get("/procesos/" + proceso.getId() + "/eliminar").session(sesion))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/procesos/" + proceso.getId() + "/eliminar").session(sesion).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(procesoRepository.findById(proceso.getId()).orElseThrow().isEliminado()).isFalse();
    }

    @Test
    void unUsuarioDeSoloLecturaNoPuedeAbrirLaConfirmacionNiEliminarDesdeLaWeb() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        MockHttpSession sesion = iniciarSesion(LECTOR_ALPES);

        mockMvc.perform(get("/procesos/" + proceso.getId() + "/eliminar").session(sesion))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/procesos/" + proceso.getId() + "/eliminar").session(sesion).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(procesoRepository.findById(proceso.getId()).orElseThrow().isEliminado()).isFalse();
    }

    @Test
    void laConfirmacionDeUnProcesoYaEliminadoNoSeMuestra() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(get("/procesos/" + proceso.getId() + "/eliminar").session(sesion))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/problema"));
    }

    @Test
    void eliminarSinTokenCsrfSeRechazaAunqueSeaAdministrador() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(post("/procesos/" + proceso.getId() + "/eliminar").session(sesion))
                .andExpect(status().isForbidden());

        assertThat(procesoRepository.findById(proceso.getId()).orElseThrow().isEliminado()).isFalse();
    }

    @Test
    void laApiEliminaLogicamenteYDevuelveDoscientosCuatroParaElAdministrador() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        long procesosAntes = procesoRepository.count();
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(delete("/api/procesos/" + proceso.getId()).session(sesion).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(procesoRepository.count()).isEqualTo(procesosAntes);
        assertThat(procesoRepository.findById(proceso.getId()).orElseThrow().isEliminado()).isTrue();
    }

    @Test
    void laApiRechazaLaEliminacionDeUnEditorConTrescientosTres() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        MockHttpSession sesion = iniciarSesion(EDITOR_ALPES);

        mockMvc.perform(delete("/api/procesos/" + proceso.getId()).session(sesion).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(procesoRepository.findById(proceso.getId()).orElseThrow().isEliminado()).isFalse();
    }

    @Test
    void laApiRechazaLaEliminacionDeUnUsuarioDeSoloLecturaConTrescientosTres() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        MockHttpSession sesion = iniciarSesion(LECTOR_ALPES);

        mockMvc.perform(delete("/api/procesos/" + proceso.getId()).session(sesion).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(procesoRepository.findById(proceso.getId()).orElseThrow().isEliminado()).isFalse();
    }

    @Test
    void elAdministradorDeLaEmpresaANoPuedeEliminarUnProcesoDeLaEmpresaB() throws Exception {
        registrarAlpes();
        registrarAndes();
        ProcesoRespuestaDto procesoDeAndes = crearProceso("Compras", ADMIN_ANDES);
        MockHttpSession sesionAlpes = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(post("/procesos/" + procesoDeAndes.getId() + "/eliminar")
                .session(sesionAlpes).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/problema"));

        assertThat(procesoRepository.findById(procesoDeAndes.getId()).orElseThrow().isEliminado()).isFalse();
    }

    @Test
    void elServicioRechazaLaEliminacionCruzadaEntreEmpresas() {
        registrarAlpes();
        registrarAndes();
        ProcesoRespuestaDto procesoDeAndes = crearProceso("Compras", ADMIN_ANDES);

        assertThatThrownBy(() -> procesoService.eliminar(procesoDeAndes.getId(), ADMIN_ALPES))
                .isInstanceOf(UsuarioSinPermisoException.class);

        assertThat(procesoRepository.findById(procesoDeAndes.getId()).orElseThrow().isEliminado()).isFalse();
    }

    @Test
    void elServicioRechazaLaEliminacionDeUnEditorDeLaMismaEmpresa() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);

        assertThatThrownBy(() -> procesoService.eliminar(proceso.getId(), EDITOR_ALPES))
                .isInstanceOf(UsuarioSinPermisoException.class);

        assertThat(procesoRepository.findById(proceso.getId()).orElseThrow().isEliminado()).isFalse();
        assertThat(historialProcesoRepository.count()).isZero();
    }

    @Test
    void eliminarDosVecesRechazaLaSegundaYNoDuplicaElHistorial() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);
        long historialTrasLaPrimera = historialProcesoRepository.count();

        assertThatThrownBy(() -> procesoService.eliminar(proceso.getId(), ADMIN_ALPES))
                .isInstanceOf(RecursoNoEncontradoException.class);

        assertThat(historialProcesoRepository.count()).isEqualTo(historialTrasLaPrimera);
    }

    @Test
    void unProcesoEliminadoYaNoSePuedeEditar() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);

        assertThatThrownBy(() -> procesoService.editar(proceso.getId(),
                new EditarProcesoDto("Ventas Corporativas", "Otra descripcion", "Operaciones",
                        EstadoProceso.PUBLICADO),
                EDITOR_ALPES))
                .isInstanceOf(RecursoNoEncontradoException.class);

        assertThat(procesoRepository.findById(proceso.getId()).orElseThrow().getNombre()).isEqualTo("Ventas");
    }

    @Test
    void elNombreDeUnProcesoEliminadoSigueReservadoEnLaEmpresa() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);

        assertThatThrownBy(() -> crearProceso("Ventas", EDITOR_ALPES))
                .isInstanceOf(NombreProcesoDuplicadoException.class);
    }

    @Test
    void otraEmpresaSiPuedeUsarElNombreDeUnProcesoEliminadoEnLaEmpresaVecina() {
        registrarAlpes();
        registrarAndes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);

        ProcesoRespuestaDto deAndes = crearProceso("Ventas", ADMIN_ANDES);

        assertThat(deAndes.getId()).isNotEqualTo(proceso.getId());
        assertThat(deAndes.isEliminado()).isFalse();
    }
}
