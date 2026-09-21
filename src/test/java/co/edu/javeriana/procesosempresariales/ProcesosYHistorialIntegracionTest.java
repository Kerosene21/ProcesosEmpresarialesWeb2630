package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
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
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProcesosYHistorialIntegracionTest {

    private static final String ADMIN_ALPES = "admin@alpes-procesos.com";
    private static final String EDITOR_ALPES = "editor@alpes-procesos.com";
    private static final String LECTOR_ALPES = "lector@alpes-procesos.com";
    private static final String ADMIN_ANDES = "admin@andes-procesos.com";
    private static final String PASSWORD = "Clave-Procesos-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProcesoService procesoService;

    @Autowired
    private HistorialProcesoRepository historialProcesoRepository;

    private void registrarAlpes() {
        empresaService.registrar(new RegistroEmpresaDto("Alpes Procesos", "903000001-1", ADMIN_ALPES, PASSWORD));
        usuarioService.crear(new CrearUsuarioDto(EDITOR_ALPES, PASSWORD, RolUsuario.EDITOR), ADMIN_ALPES);
        usuarioService.crear(new CrearUsuarioDto(LECTOR_ALPES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ALPES);
    }

    private void registrarAndes() {
        empresaService.registrar(new RegistroEmpresaDto("Andes Procesos", "903000002-2", ADMIN_ANDES, PASSWORD));
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
    void unEditorAutenticadoCreaUnProcesoYAterrizaEnSuDetalle() throws Exception {
        registrarAlpes();
        MockHttpSession sesion = iniciarSesion(EDITOR_ALPES);

        MvcResult creacion = mockMvc.perform(post("/procesos").session(sesion).with(csrf())
                .param("nombre", "Ventas")
                .param("descripcion", "Proceso comercial")
                .param("categoria", "Comercial"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String destino = creacion.getResponse().getRedirectedUrl();
        assertThat(destino).startsWith("/procesos/").isNotEqualTo("/procesos/nuevo");
        mockMvc.perform(get(destino).session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/proceso"));
    }

    @Test
    void unUsuarioDeSoloLecturaAutenticadoNoPuedeCrearUnProceso() throws Exception {
        registrarAlpes();
        MockHttpSession sesion = iniciarSesion(LECTOR_ALPES);

        mockMvc.perform(post("/procesos").session(sesion).with(csrf())
                .param("nombre", "Ventas")
                .param("descripcion", "Proceso comercial")
                .param("categoria", "Comercial"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unUsuarioDeSoloLecturaAutenticadoNoPuedeEditarUnProceso() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        MockHttpSession sesion = iniciarSesion(LECTOR_ALPES);

        mockMvc.perform(post("/procesos/" + proceso.getId()).session(sesion).with(csrf())
                .param("nombre", "Ventas Corporativas")
                .param("descripcion", "Descripcion actualizada")
                .param("categoria", "Operaciones")
                .param("estado", "PUBLICADO"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unUsuarioDeSoloLecturaSiPuedeConsultarElProcesoYSuHistorial() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        procesoService.editar(proceso.getId(), new EditarProcesoDto("Ventas", "Proceso comercial", "Comercial",
                EstadoProceso.PUBLICADO), EDITOR_ALPES);
        MockHttpSession sesion = iniciarSesion(LECTOR_ALPES);

        mockMvc.perform(get("/procesos/" + proceso.getId()).session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/proceso"));
        mockMvc.perform(get("/procesos/" + proceso.getId() + "/historial").session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/historial"));
    }

    @Test
    void unUsuarioDeLaEmpresaANoPuedeEditarUnProcesoDeLaEmpresaB() throws Exception {
        registrarAlpes();
        registrarAndes();
        ProcesoRespuestaDto procesoDeAndes = crearProceso("Compras", ADMIN_ANDES);
        MockHttpSession sesionAlpes = iniciarSesion(EDITOR_ALPES);

        mockMvc.perform(post("/procesos/" + procesoDeAndes.getId()).session(sesionAlpes).with(csrf())
                .param("nombre", "Compras Intervenidas")
                .param("descripcion", "Descripcion actualizada")
                .param("categoria", "Operaciones")
                .param("estado", "PUBLICADO"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/problema"));
    }

    @Test
    void unUsuarioDeLaEmpresaANoPuedeVerElHistorialDeUnProcesoDeLaEmpresaB() throws Exception {
        registrarAlpes();
        registrarAndes();
        ProcesoRespuestaDto procesoDeAndes = crearProceso("Compras", ADMIN_ANDES);
        procesoService.editar(procesoDeAndes.getId(), new EditarProcesoDto("Compras", "Proceso comercial",
                "Comercial", EstadoProceso.PUBLICADO), ADMIN_ANDES);
        MockHttpSession sesionAlpes = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(get("/procesos/" + procesoDeAndes.getId() + "/historial").session(sesionAlpes))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/problema"));
    }

    @Test
    void elHistorialSeDevuelveDeLaEdicionMasRecienteALaMasAntigua() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        procesoService.editar(proceso.getId(), new EditarProcesoDto("Ventas", "Proceso comercial", "Comercial",
                EstadoProceso.PUBLICADO), EDITOR_ALPES);
        procesoService.editar(proceso.getId(), new EditarProcesoDto("Ventas Corporativas", "Proceso comercial",
                "Comercial", EstadoProceso.PUBLICADO), EDITOR_ALPES);

        List<HistorialProcesoRespuestaDto> historial =
                procesoService.consultarHistorial(proceso.getId(), EDITOR_ALPES);

        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).getCambiosRealizados())
                .isEqualTo("nombre: 'Ventas' -> 'Ventas Corporativas'");
        assertThat(historial.get(1).getCambiosRealizados()).isEqualTo("estado: 'BORRADOR' -> 'PUBLICADO'");
        assertThat(historial.get(0).getFecha()).isAfterOrEqualTo(historial.get(1).getFecha());
        assertThat(historial.get(0).getUsuarioCorreo()).isEqualTo(EDITOR_ALPES);
    }

    @Test
    void unaEdicionSinCambiosNoAgregaEntradasAlHistorial() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);
        long historialAntes = historialProcesoRepository.count();

        procesoService.editar(proceso.getId(), new EditarProcesoDto("Ventas", "Proceso comercial", "Comercial",
                EstadoProceso.BORRADOR), EDITOR_ALPES);

        assertThat(historialProcesoRepository.count()).isEqualTo(historialAntes);
        assertThat(procesoService.consultarHistorial(proceso.getId(), EDITOR_ALPES)).isEmpty();
    }

    @Test
    void elHistorialRegistraUnicamenteLosCamposQueCambiaron() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);

        procesoService.editar(proceso.getId(), new EditarProcesoDto("Ventas", "Proceso comercial", "Operaciones",
                EstadoProceso.BORRADOR), EDITOR_ALPES);

        List<HistorialProcesoRespuestaDto> historial =
                procesoService.consultarHistorial(proceso.getId(), EDITOR_ALPES);
        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).getCambiosRealizados()).isEqualTo("categoria: 'Comercial' -> 'Operaciones'");
        assertThat(historial.get(0).getEstadoAnterior()).isEqualTo("BORRADOR");
        assertThat(historial.get(0).getUsuarioCorreo()).isEqualTo(EDITOR_ALPES);
    }

    @Test
    void elProcesoCreadoPorUnEditorPerteneceALaEmpresaDelUsuarioYNaceEnBorrador() {
        registrarAlpes();

        ProcesoRespuestaDto proceso = crearProceso("Ventas", EDITOR_ALPES);

        assertThat(proceso.getEstado()).isEqualTo(EstadoProceso.BORRADOR);
        assertThat(proceso.getPoolId()).isNotNull();
        assertThat(procesoService.obtener(proceso.getId(), ADMIN_ALPES).getNombre()).isEqualTo("Ventas");
    }
}
