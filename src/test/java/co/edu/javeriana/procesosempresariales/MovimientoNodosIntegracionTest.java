package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoSinCaso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.CrearArcoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.CrearLaneDto;
import co.edu.javeriana.procesosempresariales.dto.CrearMessageThrowDto;
import co.edu.javeriana.procesosempresariales.dto.CrearPoolDto;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.MessageThrowRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.service.ActividadService;
import co.edu.javeriana.procesosempresariales.service.ArcoService;
import co.edu.javeriana.procesosempresariales.service.ComparticionProcesoService;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.GatewayService;
import co.edu.javeriana.procesosempresariales.service.LaneService;
import co.edu.javeriana.procesosempresariales.service.MessageThrowService;
import co.edu.javeriana.procesosempresariales.service.PoolService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;
import co.edu.javeriana.procesosempresariales.service.RolProcesoService;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MovimientoNodosIntegracionTest {

    private static final String ADMIN_ALPES = "admin@alpes-movimiento.com";
    private static final String EDITOR_ALPES = "editor@alpes-movimiento.com";
    private static final String LECTOR_ALPES = "lector@alpes-movimiento.com";
    private static final String ADMIN_ANDES = "admin@andes-movimiento.com";
    private static final String PASSWORD = "Clave-Movimiento-2026";
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
    private RolProcesoService rolProcesoService;

    @Autowired
    private LaneService laneService;

    @Autowired
    private PoolService poolService;

    @Autowired
    private MessageThrowService messageThrowService;

    @Autowired
    private ComparticionProcesoService comparticionProcesoService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long andesId;

    private record Modelo(ProcesoRespuestaDto proceso, Long general, Long analista, ActividadRespuestaDto registrar,
            ActividadRespuestaDto evaluar, GatewayRespuestaDto gateway) {
    }

    private void registrarEmpresas() {
        empresaService.registrar(new RegistroEmpresaDto("Alpes Movimiento", "905700001-1", ADMIN_ALPES, PASSWORD));
        usuarioService.crear(new CrearUsuarioDto(EDITOR_ALPES, PASSWORD, RolUsuario.EDITOR), ADMIN_ALPES);
        usuarioService.crear(new CrearUsuarioDto(LECTOR_ALPES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ALPES);
        andesId = empresaService.registrar(new RegistroEmpresaDto("Andes Movimiento", "905700002-2", ADMIN_ANDES,
                PASSWORD)).getId();
    }

    private MockHttpSession iniciarSesion(String correo) throws Exception {
        MvcResult resultado = mockMvc.perform(formLogin().user(correo).password(PASSWORD))
                .andExpect(authenticated().withUsername(correo))
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private Modelo modeloConDosLanes() {
        ProcesoRespuestaDto proceso = procesoService.crear(
                new CrearProcesoDto("Solicitudes", "Atencion de solicitudes", "Servicio"), ADMIN_ALPES);
        Long general = actividadService.lanesDelProceso(proceso.getId(), ADMIN_ALPES).get(0).getId();
        Long rol = rolProcesoService.crear(new CrearRolProcesoDto("Analista de solicitudes", "Evalua solicitudes"),
                ADMIN_ALPES).getId();
        Long analista = laneService.crear(proceso.getId(), proceso.getPoolId(), new CrearLaneDto(rol, null),
                ADMIN_ALPES).getId();
        ActividadRespuestaDto registrar = actividadService.crear(proceso.getId(),
                new CrearActividadDto("Registrar solicitud", TipoActividad.TAREA_USUARIO, general, 100, 60),
                ADMIN_ALPES);
        ActividadRespuestaDto evaluar = actividadService.crear(proceso.getId(),
                new CrearActividadDto("Evaluar solicitud", TipoActividad.TAREA_USUARIO, general, 300, 60),
                ADMIN_ALPES);
        arcoService.crear(proceso.getId(), new CrearArcoDto(TipoNodoFlujo.ACTIVIDAD, registrar.getId(),
                TipoNodoFlujo.ACTIVIDAD, evaluar.getId(), null, null), ADMIN_ALPES);
        GatewayRespuestaDto gateway = gatewayService.crear(proceso.getId(),
                new CrearGatewayDto(TipoGateway.EXCLUSIVO, 500, 60), ADMIN_ALPES);
        return new Modelo(proceso, general, analista, registrar, evaluar, gateway);
    }

    private String ruta(Modelo modelo) {
        return "/api/procesos/" + modelo.proceso().getId();
    }

    private String rutaActividad(Modelo modelo, ActividadRespuestaDto actividad) {
        return ruta(modelo) + "/actividades/" + actividad.getId();
    }

    private ResultActions moverActividad(Modelo modelo, ActividadRespuestaDto actividad, Long laneId, int x, int y,
            MockHttpSession sesion) throws Exception {
        return mockMvc.perform(put(rutaActividad(modelo, actividad) + "/posicion").session(sesion).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"laneId\":" + laneId + ",\"posicionX\":" + x + ",\"posicionY\":" + y + "}"));
    }

    private Map<String, Object> filaGuardada(String tabla, Long id) {
        entityManager.flush();
        entityManager.clear();
        return jdbcTemplate.queryForMap("SELECT * FROM " + tabla + " WHERE id = ?", id);
    }

    private List<String> historial(Modelo modelo) {
        return procesoService.consultarHistorial(modelo.proceso().getId(), ADMIN_ALPES).stream()
                .map(HistorialProcesoRespuestaDto::getCambiosRealizados)
                .toList();
    }

    @Test
    void bajarDeLaneConLaPosicionGuardaAmbasCosasYElDiagramaLasConservaAlRecargar() throws Exception {
        registrarEmpresas();
        Modelo modelo = modeloConDosLanes();
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(put(rutaActividad(modelo, modelo.registrar())).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Registrar solicitud\",\"tipo\":\"TAREA_USUARIO\",\"laneId\":" + modelo.analista()
                        + ",\"posicionX\":100,\"posicionY\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.laneId").value(modelo.analista()))
                .andExpect(jsonPath("$.posicionY").value(200));

        Map<String, Object> fila = filaGuardada("actividad", modelo.registrar().getId());
        assertThat(((Number) fila.get("lane_id")).longValue()).isEqualTo(modelo.analista());
        assertThat(fila.get("posicion_x")).isEqualTo(100);
        assertThat(fila.get("posicion_y")).isEqualTo(200);
        mockMvc.perform(get(ruta(modelo) + "/diagrama").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actividades[?(@.nombre == 'Registrar solicitud')].laneId")
                        .value(modelo.analista().intValue()))
                .andExpect(jsonPath("$.actividades[?(@.nombre == 'Registrar solicitud')].posicionY").value(200))
                .andExpect(jsonPath("$.arcos[0].origenX").value(165))
                .andExpect(jsonPath("$.arcos[0].origenY").value(223));
        assertThat(historial(modelo)).contains("actividad 'Registrar solicitud': lane: 'General' -> 'Analista de "
                + "solicitudes'; posicion: (100, 60) -> (100, 200)");
    }

    @Test
    void subirDeLaneYMoverDentroDeLaMismaLaneSePersistenConElEndpointDeMovimiento() throws Exception {
        registrarEmpresas();
        Modelo modelo = modeloConDosLanes();
        MockHttpSession editor = iniciarSesion(EDITOR_ALPES);
        moverActividad(modelo, modelo.evaluar(), modelo.analista(), 300, 200, editor).andExpect(status().isOk());

        moverActividad(modelo, modelo.evaluar(), modelo.general(), 320, 70, editor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.laneId").value(modelo.general()));
        moverActividad(modelo, modelo.registrar(), modelo.general(), 40, 70, editor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posicionX").value(40));

        Map<String, Object> evaluar = filaGuardada("actividad", modelo.evaluar().getId());
        assertThat(((Number) evaluar.get("lane_id")).longValue()).isEqualTo(modelo.general());
        assertThat(evaluar.get("posicion_y")).isEqualTo(70);
        assertThat(filaGuardada("actividad", modelo.registrar().getId()).get("posicion_x")).isEqualTo(40);
        assertThat(historial(modelo)).contains(
                "actividad 'Evaluar solicitud': lane: 'Analista de solicitudes' -> 'General'; posicion: (300, 200) -> "
                        + "(320, 70)",
                "actividad 'Registrar solicitud': posicion: (100, 60) -> (40, 70)");
    }

    @Test
    void moverUnGatewayPersisteSuPosicionYLosArcosSeRecalculan() throws Exception {
        registrarEmpresas();
        Modelo modelo = modeloConDosLanes();
        arcoService.crear(modelo.proceso().getId(), new CrearArcoDto(TipoNodoFlujo.ACTIVIDAD,
                modelo.evaluar().getId(), TipoNodoFlujo.GATEWAY, modelo.gateway().getId(), null, null), ADMIN_ALPES);
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(put(ruta(modelo) + "/gateways/" + modelo.gateway().getId() + "/posicion").session(admin)
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"posicionX\":560,\"posicionY\":190}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posicionX").value(560));

        Map<String, Object> fila = filaGuardada("gateway", modelo.gateway().getId());
        assertThat(fila.get("posicion_x")).isEqualTo(560);
        assertThat(fila.get("posicion_y")).isEqualTo(190);
        mockMvc.perform(get(ruta(modelo) + "/arcos").session(admin))
                .andExpect(jsonPath("$[?(@.destinoTipo == 'GATEWAY')].origenX").value(365))
                .andExpect(jsonPath("$[?(@.destinoTipo == 'GATEWAY')].destinoX").value(560));
        assertThat(historial(modelo)).contains("gateway #" + modelo.gateway().getId() + ": posicion: (500, 60) -> "
                + "(560, 190)");
    }

    @Test
    void unaActividadConArcosNoPasaAUnaLaneDeOtroPool() throws Exception {
        registrarEmpresas();
        Modelo modelo = modeloConDosLanes();
        Long distribuidor = poolService.crear(modelo.proceso().getId(),
                new CrearPoolDto("Distribuidor", TipoPool.PARTICIPANTE, false, andesId), ADMIN_ALPES).getId();
        Long rol = rolProcesoService.crear(new CrearRolProcesoDto("Despachador", "Despacha pedidos"), ADMIN_ALPES)
                .getId();
        Long despacho = laneService.crear(modelo.proceso().getId(), distribuidor, new CrearLaneDto(rol, null),
                ADMIN_ALPES).getId();
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);

        moverActividad(modelo, modelo.registrar(), despacho, 100, 420, admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("SECUENCIA_ENTRE_POOLS"));
        mockMvc.perform(put(rutaActividad(modelo, modelo.evaluar())).session(admin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Evaluar solicitud\",\"tipo\":\"TAREA_USUARIO\",\"laneId\":" + despacho
                        + ",\"posicionX\":300,\"posicionY\":420}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("SECUENCIA_ENTRE_POOLS"));

        Map<String, Object> fila = filaGuardada("actividad", modelo.registrar().getId());
        assertThat(((Number) fila.get("lane_id")).longValue()).isEqualTo(modelo.general());
        assertThat(fila.get("posicion_y")).isEqualTo(60);
    }

    @Test
    void elLectorLaEmpresaInvitadaYUnaPeticionSinCsrfNoMuevenNodos() throws Exception {
        registrarEmpresas();
        Modelo modelo = modeloConDosLanes();
        comparticionProcesoService.compartir(modelo.proceso().getId(), andesId, ADMIN_ALPES);
        MockHttpSession lector = iniciarSesion(LECTOR_ALPES);
        MockHttpSession andes = iniciarSesion(ADMIN_ANDES);
        String rutaGateway = ruta(modelo) + "/gateways/" + modelo.gateway().getId() + "/posicion";

        moverActividad(modelo, modelo.registrar(), modelo.general(), 200, 70, lector)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
        moverActividad(modelo, modelo.registrar(), modelo.general(), 200, 70, andes)
                .andExpect(status().isForbidden());
        mockMvc.perform(put(rutaGateway).session(andes).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"posicionX\":600,\"posicionY\":60}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(rutaActividad(modelo, modelo.registrar()) + "/posicion")
                .session(iniciarSesion(ADMIN_ALPES)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"laneId\":" + modelo.general() + ",\"posicionX\":200,\"posicionY\":70}"))
                .andExpect(status().isForbidden());

        assertThat(filaGuardada("actividad", modelo.registrar().getId()).get("posicion_x")).isEqualTo(100);
        assertThat(filaGuardada("gateway", modelo.gateway().getId()).get("posicion_x")).isEqualTo(500);
        assertThat(historial(modelo)).noneMatch(cambio -> cambio.contains("posicion"));
    }

    @Test
    void unProcesoEliminadoNoAdmiteMovimientosYLasCoordenadasInvalidasSeRechazan() throws Exception {
        registrarEmpresas();
        Modelo modelo = modeloConDosLanes();
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);

        moverActividad(modelo, modelo.registrar(), modelo.general(), -20, 70, admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("DATOS_NO_VALIDOS"))
                .andExpect(jsonPath("$.campos.posicionX").exists());
        moverActividad(modelo, modelo.registrar(), modelo.general(), 200, 50001, admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.posicionY").exists());
        moverActividad(modelo, modelo.registrar(), 987654L, 200, 70, admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("LANE_NO_VALIDA"));

        procesoService.eliminar(modelo.proceso().getId(), ADMIN_ALPES);

        moverActividad(modelo, modelo.registrar(), modelo.general(), 200, 70, admin)
                .andExpect(status().isNotFound());
        mockMvc.perform(put(ruta(modelo) + "/gateways/" + modelo.gateway().getId() + "/posicion").session(admin)
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"posicionX\":600,\"posicionY\":60}"))
                .andExpect(status().isNotFound());
        assertThat(filaGuardada("actividad", modelo.registrar().getId()).get("posicion_x")).isEqualTo(100);
    }

    @Test
    void moverUnMessageThrowConservaSuPoolYSuFlujoDeMensaje() throws Exception {
        registrarEmpresas();
        Modelo modelo = modeloConDosLanes();
        Long banco = poolService.crear(modelo.proceso().getId(),
                new CrearPoolDto("Banco", TipoPool.EXTERNO, true, null), ADMIN_ALPES).getId();
        MessageThrowRespuestaDto solicitud = messageThrowService.crear(modelo.proceso().getId(),
                new CrearMessageThrowDto("Solicitud de pago", "valor: decimal", modelo.proceso().getPoolId(), banco,
                        null, ComportamientoSinCaso.DESCARTAR, 700, 70), ADMIN_ALPES);
        MockHttpSession editor = iniciarSesion(EDITOR_ALPES);

        mockMvc.perform(put(ruta(modelo) + "/message-throws/" + solicitud.getId() + "/posicion").session(editor)
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"posicionX\":760,\"posicionY\":80}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poolOrigenId").value(modelo.proceso().getPoolId()))
                .andExpect(jsonPath("$.poolDestinoId").value(banco));

        Map<String, Object> fila = filaGuardada("evento", solicitud.getId());
        assertThat(fila.get("posicion_x")).isEqualTo(760);
        assertThat(fila.get("posicion_y")).isEqualTo(80);
        mockMvc.perform(get(ruta(modelo) + "/diagrama").session(editor))
                .andExpect(jsonPath("$.flujosMensaje[0].eventoId").value(solicitud.getId()))
                .andExpect(jsonPath("$.flujosMensaje[0].poolDestinoId").value(banco));
        assertThat(historial(modelo)).contains("message throw 'Solicitud de pago': posicion: (700, 70) -> (760, 80)");
    }

    @Test
    void laRecolocacionDeVariosNodosEsAtomicaYQuedaEnElHistorial() throws Exception {
        registrarEmpresas();
        Modelo modelo = modeloConDosLanes();
        MockHttpSession editor = iniciarSesion(EDITOR_ALPES);
        String ruta = ruta(modelo) + "/diagrama/posiciones";
        String registrar = "{\"tipo\":\"ACTIVIDAD\",\"id\":" + modelo.registrar().getId()
                + ",\"posicionX\":100,\"posicionY\":200}";
        String gateway = "{\"tipo\":\"GATEWAY\",\"id\":" + modelo.gateway().getId()
                + ",\"posicionX\":500,\"posicionY\":210}";

        mockMvc.perform(put(ruta).session(editor).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nodos\":[" + registrar + ",{\"tipo\":\"GATEWAY\",\"id\":987654,"
                        + "\"posicionX\":10,\"posicionY\":10}]}"))
                .andExpect(status().isNotFound());
        assertThat(filaGuardada("actividad", modelo.registrar().getId()).get("posicion_y")).isEqualTo(60);

        mockMvc.perform(put(ruta).session(iniciarSesion(LECTOR_ALPES)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nodos\":[" + registrar + "]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(ruta).session(editor).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nodos\":[" + registrar + "," + gateway + "]}"))
                .andExpect(status().isNoContent());

        Map<String, Object> actividad = filaGuardada("actividad", modelo.registrar().getId());
        assertThat(actividad.get("posicion_y")).isEqualTo(200);
        assertThat(((Number) actividad.get("lane_id")).longValue()).isEqualTo(modelo.general());
        assertThat(filaGuardada("gateway", modelo.gateway().getId()).get("posicion_y")).isEqualTo(210);
        assertThat(historial(modelo)).contains("elementos del diagrama recolocados: 2");
    }
}
