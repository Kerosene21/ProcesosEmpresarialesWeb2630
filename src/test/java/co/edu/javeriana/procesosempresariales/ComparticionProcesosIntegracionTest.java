package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.ProcesoCompartidoEmpresa;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.CrearArcoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.repository.ProcesoCompartidoEmpresaRepository;
import co.edu.javeriana.procesosempresariales.service.ActividadService;
import co.edu.javeriana.procesosempresariales.service.ArcoService;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.GatewayService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ComparticionProcesosIntegracionTest {

    private static final String ADMIN_ALPES = "admin@alpes-comparte.com";
    private static final String EDITOR_ALPES = "editor@alpes-comparte.com";
    private static final String LECTOR_ALPES = "lector@alpes-comparte.com";
    private static final String ADMIN_ANDES = "admin@andes-comparte.com";
    private static final String LECTOR_ANDES = "lector@andes-comparte.com";
    private static final String ADMIN_SIERRA = "admin@sierra-comparte.com";
    private static final String PASSWORD = "Clave-Comparte-2026";
    private static final String ALPES = "Alpes Comparte";
    private static final String ANDES = "Andes Comparte";
    private static final String CODIGO = "$.codigo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProcesoService procesoService;

    @Autowired
    private ActividadService actividadService;

    @Autowired
    private ArcoService arcoService;

    @Autowired
    private GatewayService gatewayService;

    @Autowired
    private ProcesoCompartidoEmpresaRepository procesoCompartidoEmpresaRepository;

    private Long alpesId;
    private Long andesId;

    private void registrarEmpresas() {
        alpesId = empresaService.registrar(new RegistroEmpresaDto(ALPES, "905500001-1", ADMIN_ALPES, PASSWORD))
                .getId();
        usuarioService.crear(new CrearUsuarioDto(EDITOR_ALPES, PASSWORD, RolUsuario.EDITOR), ADMIN_ALPES);
        usuarioService.crear(new CrearUsuarioDto(LECTOR_ALPES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ALPES);
        andesId = empresaService.registrar(new RegistroEmpresaDto(ANDES, "905500002-2", ADMIN_ANDES, PASSWORD))
                .getId();
        usuarioService.crear(new CrearUsuarioDto(LECTOR_ANDES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ANDES);
        empresaService.registrar(new RegistroEmpresaDto("Sierra Comparte", "905500003-3", ADMIN_SIERRA, PASSWORD));
    }

    private MockHttpSession iniciarSesion(String correo) throws Exception {
        MvcResult resultado = mockMvc.perform(formLogin().user(correo).password(PASSWORD))
                .andExpect(authenticated().withUsername(correo))
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private ProcesoRespuestaDto procesoConModelo() {
        ProcesoRespuestaDto proceso = procesoService.crear(
                new CrearProcesoDto("Ventas", "Proceso comercial", "Comercial"), ADMIN_ALPES);
        Long lane = actividadService.lanesDelProceso(proceso.getId(), ADMIN_ALPES).get(0).getId();
        ActividadRespuestaDto revisar = actividadService.crear(proceso.getId(),
                new CrearActividadDto("Revisar pedido", TipoActividad.TAREA_USUARIO, lane, 100, 60), ADMIN_ALPES);
        ActividadRespuestaDto aprobar = actividadService.crear(proceso.getId(),
                new CrearActividadDto("Aprobar pedido", TipoActividad.TAREA_USUARIO, lane, 300, 60), ADMIN_ALPES);
        arcoService.crear(proceso.getId(), new CrearArcoDto(TipoNodoFlujo.ACTIVIDAD, revisar.getId(),
                TipoNodoFlujo.ACTIVIDAD, aprobar.getId(), null, null), ADMIN_ALPES);
        gatewayService.crear(proceso.getId(), new CrearGatewayDto(TipoGateway.EXCLUSIVO, 500, 60), ADMIN_ALPES);
        return proceso;
    }

    private String ruta(ProcesoRespuestaDto proceso) {
        return "/api/procesos/" + proceso.getId();
    }

    private String rutaCompartir(ProcesoRespuestaDto proceso, Long empresaId) {
        return ruta(proceso) + "/compartido-con/" + empresaId;
    }

    private void compartirConAndes(ProcesoRespuestaDto proceso) throws Exception {
        mockMvc.perform(post(rutaCompartir(proceso, andesId)).session(iniciarSesion(ADMIN_ALPES)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empresaId").value(andesId))
                .andExpect(jsonPath("$.nombre").value(ANDES));
    }

    private List<String> historial(ProcesoRespuestaDto proceso) {
        return procesoService.consultarHistorial(proceso.getId(), ADMIN_ALPES).stream()
                .map(HistorialProcesoRespuestaDto::getCambiosRealizados)
                .toList();
    }

    @Test
    void unProcesoEsPrivadoPorDefecto() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = procesoConModelo();
        MockHttpSession andes = iniciarSesion(ADMIN_ANDES);

        mockMvc.perform(get(ruta(proceso)).session(andes))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
        mockMvc.perform(get(ruta(proceso) + "/actividades").session(andes)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/procesos").session(andes).param("alcance", "TODOS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get(ruta(proceso) + "/compartido-con").session(iniciarSesion(LECTOR_ALPES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void elAdministradorComparteElProcesoYQuedaRegistradoEnElHistorial() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = procesoConModelo();

        compartirConAndes(proceso);

        ProcesoCompartidoEmpresa comparticion = procesoCompartidoEmpresaRepository
                .findByProcesoIdAndEmpresaInvitadaId(proceso.getId(), andesId).orElseThrow();
        assertThat(comparticion.isActivo()).isTrue();
        assertThat(historial(proceso)).contains("proceso compartido en solo lectura con '" + ANDES + "'");
        mockMvc.perform(get(ruta(proceso) + "/compartido-con").session(iniciarSesion(EDITOR_ALPES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains(ANDES)));
    }

    @Test
    void niElEditorNiElLectorPuedenCompartir() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = procesoConModelo();

        mockMvc.perform(post(rutaCompartir(proceso, andesId)).session(iniciarSesion(EDITOR_ALPES)).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(rutaCompartir(proceso, andesId)).session(iniciarSesion(LECTOR_ALPES)).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(procesoCompartidoEmpresaRepository.findByProcesoIdAndEmpresaInvitadaId(proceso.getId(), andesId))
                .isEmpty();
    }

    @Test
    void laEmpresaInvitadaConsultaElProcesoCompletoEnSoloLectura() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = procesoConModelo();
        compartirConAndes(proceso);
        MockHttpSession lectorAndes = iniciarSesion(LECTOR_ANDES);

        mockMvc.perform(get(ruta(proceso)).session(lectorAndes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ventas"))
                .andExpect(jsonPath("$.soloLectura").value(true))
                .andExpect(jsonPath("$.empresaPropietariaId").value(alpesId))
                .andExpect(jsonPath("$.empresaPropietariaNombre").value(ALPES));
        mockMvc.perform(get(ruta(proceso) + "/pools").session(lectorAndes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("PROPIETARIO"));
        mockMvc.perform(get(ruta(proceso) + "/pools/" + proceso.getPoolId() + "/lanes").session(lectorAndes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains("General")));
        mockMvc.perform(get(ruta(proceso) + "/actividades").session(lectorAndes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get(ruta(proceso) + "/arcos").session(lectorAndes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get(ruta(proceso) + "/gateways").session(lectorAndes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get(ruta(proceso)).session(iniciarSesion(LECTOR_ALPES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soloLectura").value(false));
    }

    @Test
    void laEmpresaInvitadaNoModificaNadaAunqueSeaAdministradora() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = procesoConModelo();
        compartirConAndes(proceso);
        MockHttpSession andes = iniciarSesion(ADMIN_ANDES);
        Long lane = actividadService.lanesDelProceso(proceso.getId(), ADMIN_ALPES).get(0).getId();

        mockMvc.perform(put(ruta(proceso)).session(andes).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Ventas Andes","descripcion":"x","categoria":"Comercial","estado":"BORRADOR"}
                        """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ruta(proceso) + "/actividades").session(andes).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Intrusa\",\"tipo\":\"TAREA_USUARIO\",\"laneId\":" + lane
                        + ",\"posicionX\":10,\"posicionY\":10}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ruta(proceso) + "/pools").session(andes).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Andes\",\"tipo\":\"EXTERNO\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ruta(proceso) + "/gateways").session(andes).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"PARALELO\",\"posicionX\":10,\"posicionY\":10}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ruta(proceso) + "/permisos-estructura/EDITOR").session(andes).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"crearPool":true,"editarPool":true,"eliminarPool":true,
                         "crearLane":true,"editarLane":true,"eliminarLane":true}
                        """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(rutaCompartir(proceso, andesId)).session(andes).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ruta(proceso)).session(andes).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(get(ruta(proceso) + "/historial").session(andes)).andExpect(status().isForbidden());

        assertThat(procesoService.obtener(proceso.getId(), ADMIN_ALPES).getNombre()).isEqualTo("Ventas");
        assertThat(actividadService.consultarActivas(proceso.getId(), ADMIN_ALPES)).hasSize(2);
    }

    @Test
    void elListadoDistingueProcesosPropiosYCompartidosSegunElAlcance() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = procesoConModelo();
        procesoService.crear(new CrearProcesoDto("Logistica", "Proceso propio de Andes", "Operaciones"), ADMIN_ANDES);
        compartirConAndes(proceso);
        MockHttpSession andes = iniciarSesion(LECTOR_ANDES);

        mockMvc.perform(get("/api/procesos").session(andes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nombre", contains("Logistica")))
                .andExpect(jsonPath("$.content[0].soloLectura").value(false));
        mockMvc.perform(get("/api/procesos").session(andes).param("alcance", "COMPARTIDOS"))
                .andExpect(jsonPath("$.content[*].nombre", contains("Ventas")))
                .andExpect(jsonPath("$.content[0].soloLectura").value(true))
                .andExpect(jsonPath("$.content[0].empresaPropietariaNombre").value(ALPES));
        mockMvc.perform(get("/api/procesos").session(andes).param("alcance", "TODOS"))
                .andExpect(jsonPath("$.content[*].nombre", contains("Logistica", "Ventas")))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void alRetirarElAccesoLaEmpresaInvitadaDejaDeVerElProceso() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = procesoConModelo();
        compartirConAndes(proceso);
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        MockHttpSession andes = iniciarSesion(LECTOR_ANDES);

        mockMvc.perform(delete(rutaCompartir(proceso, andesId)).session(admin).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ruta(proceso)).session(andes)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/procesos").session(andes).param("alcance", "COMPARTIDOS"))
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(delete(rutaCompartir(proceso, andesId)).session(admin).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(procesoCompartidoEmpresaRepository.findByProcesoIdAndEmpresaInvitadaId(proceso.getId(), andesId)
                .orElseThrow().isActivo()).isFalse();
        assertThat(historial(proceso)).contains("se retiró el acceso de '" + ANDES + "' al proceso");

        compartirConAndes(proceso);
        mockMvc.perform(get(ruta(proceso)).session(andes)).andExpect(status().isOk());
        assertThat(procesoCompartidoEmpresaRepository.findByProcesoIdAndActivoTrueOrderByEmpresaInvitadaNombreAsc(
                proceso.getId())).hasSize(1);
    }

    @Test
    void noSeComparteConLaPropiaEmpresaNiDosVecesNiConEmpresasInexistentes() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = procesoConModelo();
        compartirConAndes(proceso);
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(post(rutaCompartir(proceso, alpesId)).session(admin).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("COMPARTICION_NO_VALIDA"));
        mockMvc.perform(post(rutaCompartir(proceso, andesId)).session(admin).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("PROCESO_YA_COMPARTIDO"));
        mockMvc.perform(post(rutaCompartir(proceso, 987654L)).session(admin).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(procesoCompartidoEmpresaRepository.findByProcesoIdAndActivoTrueOrderByEmpresaInvitadaNombreAsc(
                proceso.getId())).hasSize(1);
    }

    @Test
    void unaEmpresaNoInvitadaSigueSinAcceso() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = procesoConModelo();
        compartirConAndes(proceso);
        MockHttpSession sierra = iniciarSesion(ADMIN_SIERRA);

        mockMvc.perform(get(ruta(proceso)).session(sierra)).andExpect(status().isForbidden());
        mockMvc.perform(get(ruta(proceso) + "/arcos").session(sierra)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/procesos").session(sierra).param("alcance", "TODOS"))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void siLaPropietariaEliminaElProcesoLaInvitadaYaNoLoVe() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = procesoConModelo();
        compartirConAndes(proceso);

        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);

        MockHttpSession andes = iniciarSesion(LECTOR_ANDES);
        mockMvc.perform(get(ruta(proceso)).session(andes)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/procesos").session(andes).param("alcance", "COMPARTIDOS"))
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get(ruta(proceso)).session(iniciarSesion(ADMIN_ALPES)))
                .andExpect(status().isOk());
    }
}
