package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ArcoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.CrearArcoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.exception.ModeloDeProcesoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.NodoFlujoNoValidoException;
import co.edu.javeriana.procesosempresariales.repository.ArcoRepository;
import co.edu.javeriana.procesosempresariales.repository.GatewayRepository;
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
class EliminacionGatewaysIntegracionTest {

    private static final String ADMIN_ALPES = "admin@alpes-gateway.com";
    private static final String EDITOR_ALPES = "editor@alpes-gateway.com";
    private static final String LECTOR_ALPES = "lector@alpes-gateway.com";
    private static final String ADMIN_ANDES = "admin@andes-gateway.com";
    private static final String PASSWORD = "Clave-Gateway-2026";

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
    private GatewayRepository gatewayRepository;

    @Autowired
    private ArcoRepository arcoRepository;

    private ProcesoRespuestaDto proceso;
    private GatewayRespuestaDto gateway;
    private List<ArcoRespuestaDto> arcos;

    private void registrarEmpresas() {
        empresaService.registrar(new RegistroEmpresaDto("Alpes Gateway", "905200001-1", ADMIN_ALPES, PASSWORD));
        usuarioService.crear(new CrearUsuarioDto(EDITOR_ALPES, PASSWORD, RolUsuario.EDITOR), ADMIN_ALPES);
        usuarioService.crear(new CrearUsuarioDto(LECTOR_ALPES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ALPES);
        empresaService.registrar(new RegistroEmpresaDto("Andes Gateway", "905200002-2", ADMIN_ANDES, PASSWORD));
    }

    private MockHttpSession iniciarSesion(String correo) throws Exception {
        MvcResult resultado = mockMvc.perform(formLogin().user(correo).password(PASSWORD))
                .andExpect(authenticated().withUsername(correo))
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private ActividadRespuestaDto crearActividad(String nombre, int x) {
        Long laneId = actividadService.lanesDelProceso(proceso.getId(), ADMIN_ALPES).get(0).getId();
        return actividadService.crear(proceso.getId(),
                new CrearActividadDto(nombre, TipoActividad.TAREA_USUARIO, laneId, x, 40), ADMIN_ALPES);
    }

    private ArcoRespuestaDto unir(TipoNodoFlujo origenTipo, Long origenId, Long destinoId, String condicion) {
        return arcoService.crear(proceso.getId(), new CrearArcoDto(origenTipo, origenId, TipoNodoFlujo.ACTIVIDAD,
                destinoId, null, condicion), ADMIN_ALPES);
    }

    private ArcoRespuestaDto entrarAlGateway(Long origenId) {
        return arcoService.crear(proceso.getId(), new CrearArcoDto(TipoNodoFlujo.ACTIVIDAD, origenId,
                TipoNodoFlujo.GATEWAY, gateway.getId(), null, null), ADMIN_ALPES);
    }

    private void modelarUnaDecision() {
        registrarEmpresas();
        proceso = procesoService.crear(new CrearProcesoDto("Credito", "Aprobacion de creditos", "Finanzas"),
                ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad("Revisar solicitud", 100);
        ActividadRespuestaDto aprobar = crearActividad("Aprobar solicitud", 500);
        ActividadRespuestaDto rechazar = crearActividad("Rechazar solicitud", 700);
        gateway = gatewayService.crear(proceso.getId(), new CrearGatewayDto(TipoGateway.EXCLUSIVO, 300, 120),
                ADMIN_ALPES);
        arcos = List.of(entrarAlGateway(revisar.getId()),
                unir(TipoNodoFlujo.GATEWAY, gateway.getId(), aprobar.getId(), "monto alto"),
                unir(TipoNodoFlujo.GATEWAY, gateway.getId(), rechazar.getId(), "monto bajo"));
    }

    private String rutaGateway() {
        return "/api/procesos/" + proceso.getId() + "/gateways/" + gateway.getId();
    }

    private String etiqueta() {
        return "Gateway EXCLUSIVO #" + gateway.getId();
    }

    private boolean gatewayActivo() {
        return gatewayRepository.findById(gateway.getId()).orElseThrow().isActivo();
    }

    @Test
    void elAdministradorEliminaElGatewayPorApiSinBorrarloFisicamente() throws Exception {
        modelarUnaDecision();
        long gatewaysAntes = gatewayRepository.count();

        mockMvc.perform(delete(rutaGateway()).session(iniciarSesion(ADMIN_ALPES)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(gatewayRepository.count()).isEqualTo(gatewaysAntes);
        assertThat(gatewayActivo()).isFalse();
        assertThat(gatewayService.consultarActivos(proceso.getId(), ADMIN_ALPES)).isEmpty();
    }

    @Test
    void losArcosConectadosQuedanInactivosSinBorrarse() throws Exception {
        modelarUnaDecision();
        long arcosAntes = arcoRepository.count();

        mockMvc.perform(delete(rutaGateway()).session(iniciarSesion(ADMIN_ALPES)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(arcoRepository.count()).isEqualTo(arcosAntes);
        for (ArcoRespuestaDto arco : arcos) {
            assertThat(arcoRepository.findById(arco.getId()).orElseThrow().isActivo()).isFalse();
        }
        assertThat(arcoService.consultarActivos(proceso.getId(), ADMIN_ALPES)).isEmpty();
    }

    @Test
    void laEliminacionQuedaEnElHistorialDelProceso() throws Exception {
        modelarUnaDecision();

        mockMvc.perform(delete(rutaGateway()).session(iniciarSesion(ADMIN_ALPES)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(procesoService.consultarHistorial(proceso.getId(), ADMIN_ALPES).get(0).getCambiosRealizados())
                .isEqualTo("gateway eliminado: " + etiqueta() + "; arcos desactivados: 3");
        assertThat(procesoService.consultarHistorial(proceso.getId(), ADMIN_ALPES).get(0).getUsuarioCorreo())
                .isEqualTo(ADMIN_ALPES);
    }

    @Test
    void eliminarAdviertequeLaRamificacionQuedaSinPuntoDeDecision() {
        modelarUnaDecision();

        GatewayRespuestaDto eliminado = gatewayService.eliminar(proceso.getId(), gateway.getId(), ADMIN_ALPES);

        assertThat(eliminado.isActivo()).isFalse();
        assertThat(eliminado.getArcosDesactivados()).isEqualTo(3);
        assertThat(eliminado.getAdvertencias()).containsExactly(
                etiqueta() + " deja de decidir el flujo hacia 'Aprobar solicitud', 'Rechazar solicitud': la"
                        + " ramificación queda sin punto de decisión y el flujo se rompe.",
                "'Revisar solicitud' quedó sin arcos de salida",
                "'Aprobar solicitud' quedó sin arcos de entrada",
                "'Rechazar solicitud' quedó sin arcos de entrada");
    }

    @Test
    void laConsultaPreviaMuestraElImpactoSinModificarNada() throws Exception {
        modelarUnaDecision();

        mockMvc.perform(get(rutaGateway() + "/eliminacion").session(iniciarSesion(ADMIN_ALPES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(gateway.getId()))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.advertencias[0]")
                        .value("Se desactivarán 3 arcos conectados a " + etiqueta() + "."))
                .andExpect(jsonPath("$.advertencias[2]").value("'Revisar solicitud' quedó sin arcos de salida"));

        assertThat(gatewayActivo()).isTrue();
        assertThat(arcoService.consultarActivos(proceso.getId(), ADMIN_ALPES)).hasSize(3);
    }

    @Test
    void elEditorYElLectorNoEliminanGateways() throws Exception {
        modelarUnaDecision();

        mockMvc.perform(delete(rutaGateway()).session(iniciarSesion(EDITOR_ALPES)).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
        mockMvc.perform(delete(rutaGateway()).session(iniciarSesion(LECTOR_ALPES)).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(rutaGateway() + "/eliminacion").session(iniciarSesion(EDITOR_ALPES)))
                .andExpect(status().isForbidden());

        assertThat(gatewayActivo()).isTrue();
        assertThat(arcoService.consultarActivos(proceso.getId(), ADMIN_ALPES)).hasSize(3);
    }

    @Test
    void unGatewayYaEliminadoNoSeEliminaDosVeces() throws Exception {
        modelarUnaDecision();
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);
        mockMvc.perform(delete(rutaGateway()).session(sesion).with(csrf())).andExpect(status().isNoContent());

        mockMvc.perform(delete(rutaGateway()).session(sesion).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("El gateway ya fue eliminado"));
    }

    @Test
    void otraEmpresaNoEliminaElGateway() throws Exception {
        modelarUnaDecision();

        mockMvc.perform(delete(rutaGateway()).session(iniciarSesion(ADMIN_ANDES)).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));

        assertThat(gatewayActivo()).isTrue();
    }

    @Test
    void unGatewayInexistenteDevuelve404() throws Exception {
        modelarUnaDecision();

        mockMvc.perform(delete("/api/procesos/" + proceso.getId() + "/gateways/999999")
                .session(iniciarSesion(ADMIN_ALPES)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("El gateway no existe en este proceso"));
    }

    @Test
    void noSeEliminanGatewaysDeUnProcesoEliminado() throws Exception {
        modelarUnaDecision();
        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);

        mockMvc.perform(delete(rutaGateway()).session(iniciarSesion(ADMIN_ALPES)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("El proceso ya fue eliminado"));

        assertThat(gatewayActivo()).isTrue();
    }

    @Test
    void elGatewayEliminadoDejaDeBloquearLaPublicacionYNoAdmiteArcosNuevos() {
        registrarEmpresas();
        proceso = procesoService.crear(new CrearProcesoDto("Compras", "Gestion de compras", "Operaciones"),
                ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad("Aprobar compra", 500);
        gateway = gatewayService.crear(proceso.getId(), new CrearGatewayDto(TipoGateway.EXCLUSIVO, 300, 120),
                ADMIN_ALPES);
        unir(TipoNodoFlujo.GATEWAY, gateway.getId(), aprobar.getId(), "monto alto");
        EditarProcesoDto publicar = new EditarProcesoDto("Compras", "Gestion de compras", "Operaciones",
                EstadoProceso.PUBLICADO);

        assertThatThrownBy(() -> procesoService.editar(proceso.getId(), publicar, ADMIN_ALPES))
                .isInstanceOf(ModeloDeProcesoNoValidoException.class);

        gatewayService.eliminar(proceso.getId(), gateway.getId(), ADMIN_ALPES);

        assertThat(procesoService.editar(proceso.getId(), publicar, ADMIN_ALPES).getEstado())
                .isEqualTo(EstadoProceso.PUBLICADO);
        assertThatThrownBy(() -> unir(TipoNodoFlujo.GATEWAY, gateway.getId(), aprobar.getId(), "monto bajo"))
                .isInstanceOf(NodoFlujoNoValidoException.class);
    }
}
