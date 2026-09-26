package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import co.edu.javeriana.procesosempresariales.domain.PermisoEstructuraProceso;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.CrearGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.exception.PoolCajaNegraException;
import co.edu.javeriana.procesosempresariales.exception.PoolNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.RolProcesoEnUsoException;
import co.edu.javeriana.procesosempresariales.repository.LaneRepository;
import co.edu.javeriana.procesosempresariales.repository.PermisoEstructuraProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.PoolRepository;
import co.edu.javeriana.procesosempresariales.service.ActividadService;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.GatewayService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;
import co.edu.javeriana.procesosempresariales.service.RolProcesoService;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EstructuraProcesoIntegracionTest {

    private static final String ADMIN_ALPES = "admin@alpes-estructura.com";
    private static final String EDITOR_ALPES = "editor@alpes-estructura.com";
    private static final String LECTOR_ALPES = "lector@alpes-estructura.com";
    private static final String ADMIN_ANDES = "admin@andes-estructura.com";
    private static final String PASSWORD = "Clave-Estructura-2026";
    private static final String ALPES = "Alpes Estructura";
    private static final String ANDES = "Andes Estructura";
    private static final String CODIGO = "$.codigo";

    private static final String JSON_EXTERNO_CAJA_NEGRA = """
            {"nombre":"Cliente","tipo":"EXTERNO","cajaNegra":true}
            """;

    private static final String JSON_EXTERNO = """
            {"nombre":"Banco","tipo":"EXTERNO"}
            """;

    private static final String JSON_EDITOR_PUEDE_TODO_MENOS_CREAR_POOLS = """
            {"crearPool":false,"editarPool":true,"eliminarPool":true,
             "crearLane":true,"editarLane":true,"eliminarLane":true}
            """;

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
    private GatewayService gatewayService;

    @Autowired
    private RolProcesoService rolProcesoService;

    @Autowired
    private PoolRepository poolRepository;

    @Autowired
    private LaneRepository laneRepository;

    @Autowired
    private PermisoEstructuraProcesoRepository permisoEstructuraProcesoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long andesId;

    private void registrarEmpresas() {
        empresaService.registrar(new RegistroEmpresaDto(ALPES, "905400001-1", ADMIN_ALPES, PASSWORD));
        usuarioService.crear(new CrearUsuarioDto(EDITOR_ALPES, PASSWORD, RolUsuario.EDITOR), ADMIN_ALPES);
        usuarioService.crear(new CrearUsuarioDto(LECTOR_ALPES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ALPES);
        andesId = empresaService.registrar(new RegistroEmpresaDto(ANDES, "905400002-2", ADMIN_ANDES, PASSWORD))
                .getId();
    }

    private MockHttpSession iniciarSesion(String correo) throws Exception {
        MvcResult resultado = mockMvc.perform(formLogin().user(correo).password(PASSWORD))
                .andExpect(authenticated().withUsername(correo))
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private ProcesoRespuestaDto crearProceso(String nombre) {
        return procesoService.crear(new CrearProcesoDto(nombre, "Proceso de " + nombre, "Operaciones"), ADMIN_ALPES);
    }

    private String rutaPools(ProcesoRespuestaDto proceso) {
        return "/api/procesos/" + proceso.getId() + "/pools";
    }

    private String rutaLanes(ProcesoRespuestaDto proceso, Long poolId) {
        return rutaPools(proceso) + "/" + poolId + "/lanes";
    }

    private String rutaPermisos(ProcesoRespuestaDto proceso) {
        return "/api/procesos/" + proceso.getId() + "/permisos-estructura";
    }

    private ResultActions enviar(String metodo, String ruta, String json, MockHttpSession sesion) throws Exception {
        MockHttpServletRequestBuilder peticion = switch (metodo) {
            case "POST" -> post(ruta);
            case "PUT" -> put(ruta);
            default -> delete(ruta);
        };
        if (json != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(peticion.session(sesion).with(csrf()));
    }

    private Long idCreado(ResultActions resultado) throws Exception {
        String cuerpo = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private Long crearPool(ProcesoRespuestaDto proceso, String json, MockHttpSession sesion) throws Exception {
        return idCreado(enviar("POST", rutaPools(proceso), json, sesion));
    }

    private String jsonParticipante(String nombre, Long empresaId) {
        return "{\"nombre\":\"" + nombre + "\",\"tipo\":\"PARTICIPANTE\",\"empresaParticipanteId\":" + empresaId + "}";
    }

    private Long crearRol(String nombre) {
        return rolProcesoService.crear(new CrearRolProcesoDto(nombre, "Responsable de " + nombre), ADMIN_ALPES)
                .getId();
    }

    private String jsonLane(Long rolId, Integer orden) {
        return "{\"rolProcesoId\":" + rolId + (orden == null ? "" : ",\"orden\":" + orden) + "}";
    }

    private Long crearLane(ProcesoRespuestaDto proceso, Long poolId, Long rolId, MockHttpSession sesion)
            throws Exception {
        return idCreado(enviar("POST", rutaLanes(proceso, poolId), jsonLane(rolId, null), sesion));
    }

    private Long laneGeneral(ProcesoRespuestaDto proceso) {
        return actividadService.lanesDelProceso(proceso.getId(), ADMIN_ALPES).get(0).getId();
    }

    private ActividadRespuestaDto crearActividad(ProcesoRespuestaDto proceso, String nombre, Long laneId) {
        return actividadService.crear(proceso.getId(),
                new CrearActividadDto(nombre, TipoActividad.TAREA_USUARIO, laneId, 100, 60), ADMIN_ALPES);
    }

    private String jsonArco(Long origenId, Long destinoId) {
        return "{\"origenTipo\":\"ACTIVIDAD\",\"origenId\":" + origenId
                + ",\"destinoTipo\":\"ACTIVIDAD\",\"destinoId\":" + destinoId + "}";
    }

    private List<String> historial(ProcesoRespuestaDto proceso) {
        return procesoService.consultarHistorial(proceso.getId(), ADMIN_ALPES).stream()
                .map(HistorialProcesoRespuestaDto::getCambiosRealizados)
                .toList();
    }

    @Test
    void unProcesoNuevoTieneSoloSuPoolPropietarioConLaLaneGeneral() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession lector = iniciarSesion(LECTOR_ALPES);

        mockMvc.perform(get(rutaPools(proceso)).session(lector))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(proceso.getPoolId()))
                .andExpect(jsonPath("$[0].tipo").value("PROPIETARIO"))
                .andExpect(jsonPath("$[0].orden").value(1))
                .andExpect(jsonPath("$[0].cajaNegra").value(false))
                .andExpect(jsonPath("$[0].empresaNombre").value(ALPES));
        mockMvc.perform(get(rutaLanes(proceso, proceso.getPoolId())).session(lector))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains("General")));
    }

    @Test
    void elAdministradorAgregaPoolsExternoYParticipanteEnOrdenYConHistorial() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);

        crearPool(proceso, JSON_EXTERNO_CAJA_NEGRA, admin);
        crearPool(proceso, jsonParticipante("Distribuidor", andesId), admin);

        mockMvc.perform(get(rutaPools(proceso)).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tipo", contains("PROPIETARIO", "EXTERNO", "PARTICIPANTE")))
                .andExpect(jsonPath("$[*].orden", contains(1, 2, 3)))
                .andExpect(jsonPath("$[1].cajaNegra").value(true))
                .andExpect(jsonPath("$[1].empresaId").doesNotExist())
                .andExpect(jsonPath("$[2].empresaId").value(andesId))
                .andExpect(jsonPath("$[2].empresaNombre").value(ANDES));
        assertThat(historial(proceso)).contains("pool creado: 'Cliente' (EXTERNO, caja negra)",
                "pool creado: 'Distribuidor' (PARTICIPANTE)");
    }

    @Test
    void lasReglasDeCadaTipoDePoolSeValidan() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        Long alpesId = proceso.getEmpresaPropietariaId();

        enviar("POST", rutaPools(proceso), "{\"nombre\":\"Otra\",\"tipo\":\"PROPIETARIO\"}", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("POOL_NO_VALIDO"));
        enviar("POST", rutaPools(proceso), "{\"nombre\":\"Socio\",\"tipo\":\"PARTICIPANTE\"}", admin)
                .andExpect(status().isBadRequest());
        enviar("POST", rutaPools(proceso), jsonParticipante("Socio", alpesId), admin)
                .andExpect(status().isBadRequest());
        enviar("POST", rutaPools(proceso), "{\"nombre\":\"Banco\",\"tipo\":\"EXTERNO\",\"empresaParticipanteId\":"
                + andesId + "}", admin)
                .andExpect(status().isBadRequest());
        enviar("POST", rutaPools(proceso), jsonParticipante("Socio", 987654L), admin)
                .andExpect(status().isNotFound());
        assertThat(poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(proceso.getId())).hasSize(1);
    }

    @Test
    void unPoolDeCajaNegraNoAdmiteLanesNiGateways() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        Long cliente = crearPool(proceso, JSON_EXTERNO_CAJA_NEGRA, admin);
        Long rol = crearRol("Comprador");

        enviar("POST", rutaLanes(proceso, cliente), jsonLane(rol, null), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("POOL_CAJA_NEGRA"));
        mockMvc.perform(get(rutaPools(proceso) + "/" + cliente + "/roles-disponibles").session(admin))
                .andExpect(status().isConflict());
        assertThatThrownBy(() -> gatewayService.crear(proceso.getId(),
                new CrearGatewayDto(TipoGateway.EXCLUSIVO, 300, 100, cliente), ADMIN_ALPES))
                .isInstanceOf(PoolCajaNegraException.class);
        assertThat(poolRepository.lanesActivas(cliente)).isZero();
    }

    @Test
    void unPoolConContenidoNoSeEliminaYUnoVacioSeEliminaDeFormaLogica() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        Long distribuidor = crearPool(proceso, jsonParticipante("Distribuidor", andesId), admin);
        Long banco = crearPool(proceso, JSON_EXTERNO, admin);
        Long lane = crearLane(proceso, distribuidor, crearRol("Despachador"), admin);

        enviar("DELETE", rutaPools(proceso) + "/" + distribuidor, null, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("POOL_CON_CONTENIDO"));
        enviar("PUT", rutaPools(proceso) + "/" + distribuidor,
                "{\"nombre\":\"Distribuidor\",\"cajaNegra\":true,\"empresaParticipanteId\":" + andesId + "}", admin)
                .andExpect(status().isConflict());

        enviar("DELETE", rutaLanes(proceso, distribuidor) + "/" + lane, null, admin)
                .andExpect(status().isNoContent());
        enviar("DELETE", rutaPools(proceso) + "/" + distribuidor, null, admin)
                .andExpect(status().isNoContent());

        Pool eliminado = poolRepository.findById(distribuidor).orElseThrow();
        assertThat(eliminado.isActivo()).isFalse();
        assertThat(poolRepository.findById(banco).orElseThrow().getOrden()).isEqualTo(2);
        mockMvc.perform(get(rutaPools(proceso)).session(admin))
                .andExpect(jsonPath("$[*].nombre", contains(ALPES, "Banco")));
        mockMvc.perform(get(rutaPools(proceso) + "/" + distribuidor).session(admin))
                .andExpect(status().isNotFound());
        assertThat(historial(proceso)).contains("pool eliminado: 'Distribuidor'");
    }

    @Test
    void elPoolPropietarioNoSeEliminaNiSeConvierteEnCajaNegra() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        String rutaPropietario = rutaPools(proceso) + "/" + proceso.getPoolId();

        enviar("DELETE", rutaPropietario, null, admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("POOL_NO_VALIDO"));
        enviar("PUT", rutaPropietario, "{\"nombre\":\"Alpes\",\"cajaNegra\":true}", admin)
                .andExpect(status().isBadRequest());
        enviar("PUT", rutaPropietario, "{\"nombre\":\"Alpes Operaciones\"}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Alpes Operaciones"));

        assertThat(poolRepository.findById(proceso.getPoolId()).orElseThrow().isActivo()).isTrue();
        assertThat(historial(proceso)).contains("pool '" + ALPES + "': nombre: '" + ALPES
                + "' -> 'Alpes Operaciones'");
    }

    @Test
    void lasLanesNuevasExigenRolDeProcesoYSeUbicanEnLaPosicionIndicada() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession editor = iniciarSesion(EDITOR_ALPES);
        Long analista = crearRol("Analista");
        Long tesorero = crearRol("Tesorero");
        String rutaLanes = rutaLanes(proceso, proceso.getPoolId());

        crearLane(proceso, proceso.getPoolId(), analista, editor);
        idCreado(enviar("POST", rutaLanes, jsonLane(tesorero, 1), editor));
        enviar("POST", rutaLanes, "{\"orden\":1}", editor).andExpect(status().isBadRequest());
        enviar("POST", rutaLanes, jsonLane(analista, null), editor)
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("LANE_DUPLICADA"));
        enviar("POST", rutaLanes, jsonLane(crearRol("Auditor"), 9), editor)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("LANE_NO_VALIDA"));

        mockMvc.perform(get(rutaLanes).session(editor))
                .andExpect(jsonPath("$[*].nombre", contains("Tesorero", "General", "Analista")))
                .andExpect(jsonPath("$[*].orden", contains(1, 2, 3)))
                .andExpect(jsonPath("$[0].rolProcesoId").value(tesorero));
        assertThat(historial(proceso)).contains("lane creada en el pool '" + ALPES
                + "': 'Tesorero' en la posición 1");
    }

    @Test
    void renombrarUnaLaneEsAsignarleOtroRolDeProcesoYReordenarPersiste() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        String rutaLanes = rutaLanes(proceso, proceso.getPoolId());
        Long general = laneGeneral(proceso);
        Long cartera = crearLane(proceso, proceso.getPoolId(), crearRol("Cartera"), admin);
        Long analista = crearRol("Analista");

        enviar("PUT", rutaLanes + "/" + general, jsonLane(analista, null), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Analista"))
                .andExpect(jsonPath("$.rolProcesoId").value(analista));
        enviar("PUT", rutaLanes + "/orden", "{\"lanes\":[" + cartera + "," + general + "]}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains("Cartera", "Analista")));
        enviar("PUT", rutaLanes + "/orden", "{\"lanes\":[" + cartera + "]}", admin)
                .andExpect(status().isBadRequest());

        assertThat(laneRepository.findById(cartera).orElseThrow().getOrden()).isEqualTo(1);
        assertThat(laneRepository.findById(general).orElseThrow().getOrden()).isEqualTo(2);
        assertThat(historial(proceso)).contains("lane 'General' del pool '" + ALPES
                + "': rol de proceso: 'General' -> 'Analista'",
                "lanes del pool '" + ALPES + "' reordenadas: 'Cartera', 'Analista'");
    }

    @Test
    void unaLaneConActividadesNoSeEliminaYUnaVaciaSeEliminaDeFormaLogica() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        String rutaLanes = rutaLanes(proceso, proceso.getPoolId());
        Long general = laneGeneral(proceso);
        Long cartera = crearLane(proceso, proceso.getPoolId(), crearRol("Cartera"), admin);
        Long tesoreria = crearLane(proceso, proceso.getPoolId(), crearRol("Tesoreria"), admin);
        crearActividad(proceso, "Revisar solicitud", general);

        enviar("DELETE", rutaLanes + "/" + general, null, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("LANE_CON_ACTIVIDADES"));
        enviar("DELETE", rutaLanes + "/" + cartera, null, admin)
                .andExpect(status().isNoContent());

        assertThat(laneRepository.findById(cartera).orElseThrow().isActivo()).isFalse();
        assertThat(laneRepository.findById(general).orElseThrow().isActivo()).isTrue();
        assertThat(laneRepository.findById(tesoreria).orElseThrow().getOrden()).isEqualTo(2);
        assertThat(historial(proceso)).contains("lane eliminada del pool '" + ALPES + "': 'Cartera'");
    }

    @Test
    void unRolDeProcesoUsadoEnUnaLaneDeUnPoolParticipanteNoSePuedeEliminar() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        Long distribuidor = crearPool(proceso, jsonParticipante("Distribuidor", andesId), admin);
        Long despachador = crearRol("Despachador");
        crearLane(proceso, distribuidor, despachador, admin);

        assertThatThrownBy(() -> rolProcesoService.eliminar(despachador, ADMIN_ALPES))
                .isInstanceOf(RolProcesoEnUsoException.class);
        mockMvc.perform(get(rutaPools(proceso) + "/" + distribuidor + "/roles-disponibles").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains("Despachador")));
    }

    @Test
    void unFlujoDeSecuenciaUneLanesDelMismoPoolPeroNoCruzaPools() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        Long cartera = crearLane(proceso, proceso.getPoolId(), crearRol("Cartera"), admin);
        Long distribuidor = crearPool(proceso, jsonParticipante("Distribuidor", andesId), admin);
        Long despacho = crearLane(proceso, distribuidor, crearRol("Despachador"), admin);
        ActividadRespuestaDto revisar = crearActividad(proceso, "Revisar pedido", laneGeneral(proceso));
        ActividadRespuestaDto cobrar = crearActividad(proceso, "Cobrar pedido", cartera);
        ActividadRespuestaDto despachar = crearActividad(proceso, "Despachar pedido", despacho);
        String rutaArcos = "/api/procesos/" + proceso.getId() + "/arcos";

        assertThat(despachar.getPoolId()).isEqualTo(distribuidor);
        enviar("POST", rutaArcos, jsonArco(revisar.getId(), cobrar.getId()), admin)
                .andExpect(status().isCreated());
        enviar("POST", rutaArcos, jsonArco(cobrar.getId(), despachar.getId()), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("SECUENCIA_ENTRE_POOLS"));

        mockMvc.perform(get(rutaArcos).session(admin))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void conVariosPoolsCadaGatewayIndicaSuPool() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);

        GatewayRespuestaDto enPropietario = gatewayService.crear(proceso.getId(),
                new CrearGatewayDto(TipoGateway.EXCLUSIVO, 200, 80), ADMIN_ALPES);
        Long distribuidor = crearPool(proceso, jsonParticipante("Distribuidor", andesId), admin);

        assertThat(enPropietario.getPoolId()).isEqualTo(proceso.getPoolId());
        assertThatThrownBy(() -> gatewayService.crear(proceso.getId(),
                new CrearGatewayDto(TipoGateway.PARALELO, 200, 80), ADMIN_ALPES))
                .isInstanceOf(PoolNoValidoException.class);
        enviar("POST", "/api/procesos/" + proceso.getId() + "/gateways",
                "{\"tipo\":\"PARALELO\",\"posicionX\":300,\"posicionY\":90,\"poolId\":" + distribuidor + "}", admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.poolId").value(distribuidor));
        enviar("DELETE", rutaPools(proceso) + "/" + distribuidor, null, admin)
                .andExpect(status().isConflict());
    }

    @Test
    void porDefectoElEditorCreaYEditaEstructuraPeroNoLaElimina() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession editor = iniciarSesion(EDITOR_ALPES);

        Long banco = crearPool(proceso, JSON_EXTERNO, editor);
        enviar("PUT", rutaPools(proceso) + "/" + banco, "{\"nombre\":\"Banco central\"}", editor)
                .andExpect(status().isOk());
        Long lane = crearLane(proceso, banco, crearRol("Oficial"), editor);
        enviar("DELETE", rutaLanes(proceso, banco) + "/" + lane, null, editor)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("El rol EDITOR no tiene permiso para eliminar lanes en este"
                        + " proceso"));
        enviar("DELETE", rutaPools(proceso) + "/" + banco, null, editor)
                .andExpect(status().isForbidden());

        mockMvc.perform(get(rutaPermisos(proceso)).session(editor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].rol").value("EDITOR"))
                .andExpect(jsonPath("$[1].crearPool").value(true))
                .andExpect(jsonPath("$[1].eliminarPool").value(false))
                .andExpect(jsonPath("$[1].eliminarLane").value(false));
        assertThat(permisoEstructuraProcesoRepository.findByProcesoIdAndRol(proceso.getId(), RolUsuario.EDITOR))
                .isEmpty();
    }

    @Test
    void elAdministradorConfiguraLosPermisosDelEditorQueQuedanPersistidosPorProceso() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto ventas = crearProceso("Ventas");
        ProcesoRespuestaDto compras = crearProceso("Compras");
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        MockHttpSession editor = iniciarSesion(EDITOR_ALPES);
        Long bancoVentas = crearPool(ventas, JSON_EXTERNO, admin);
        Long bancoCompras = crearPool(compras, JSON_EXTERNO, admin);

        enviar("PUT", rutaPermisos(ventas) + "/EDITOR", JSON_EDITOR_PUEDE_TODO_MENOS_CREAR_POOLS, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].crearPool").value(false))
                .andExpect(jsonPath("$[1].eliminarPool").value(true));

        PermisoEstructuraProceso guardado = permisoEstructuraProcesoRepository
                .findByProcesoIdAndRol(ventas.getId(), RolUsuario.EDITOR).orElseThrow();
        assertThat(guardado.isCrearPool()).isFalse();
        assertThat(guardado.isEliminarPool()).isTrue();
        assertThat(guardado.isEliminarLane()).isTrue();

        enviar("DELETE", rutaPools(ventas) + "/" + bancoVentas, null, editor).andExpect(status().isNoContent());
        enviar("POST", rutaPools(ventas), JSON_EXTERNO_CAJA_NEGRA, editor).andExpect(status().isForbidden());
        enviar("DELETE", rutaPools(compras) + "/" + bancoCompras, null, editor).andExpect(status().isForbidden());
        crearPool(compras, JSON_EXTERNO_CAJA_NEGRA, editor);
        assertThat(historial(ventas)).contains("permisos de estructura del rol EDITOR: crear pools: true -> false;"
                + " eliminar pools: false -> true; eliminar lanes: false -> true");
    }

    @Test
    void elLectorNuncaModificaLaEstructuraYSoloConsulta() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession lector = iniciarSesion(LECTOR_ALPES);

        enviar("POST", rutaPools(proceso), JSON_EXTERNO, lector).andExpect(status().isForbidden());
        enviar("POST", rutaLanes(proceso, proceso.getPoolId()), jsonLane(crearRol("Oficial"), null), lector)
                .andExpect(status().isForbidden());
        enviar("PUT", rutaLanes(proceso, proceso.getPoolId()) + "/orden",
                "{\"lanes\":[" + laneGeneral(proceso) + "]}", lector)
                .andExpect(status().isForbidden());
        enviar("PUT", rutaPermisos(proceso) + "/EDITOR", JSON_EDITOR_PUEDE_TODO_MENOS_CREAR_POOLS, lector)
                .andExpect(status().isForbidden());
        mockMvc.perform(get(rutaPermisos(proceso)).session(lector))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].rol").value("SOLO_LECTURA"))
                .andExpect(jsonPath("$[2].crearPool").value(false));
        assertThat(poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(proceso.getId())).hasSize(1);
    }

    @Test
    void losPermisosFijosDelAdministradorYDelLectorNoSeCambianYElEditorNoConfigura() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);

        enviar("PUT", rutaPermisos(proceso) + "/ADMINISTRADOR", JSON_EDITOR_PUEDE_TODO_MENOS_CREAR_POOLS, admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("PERMISO_ESTRUCTURA_NO_VALIDO"));
        enviar("PUT", rutaPermisos(proceso) + "/SOLO_LECTURA", JSON_EDITOR_PUEDE_TODO_MENOS_CREAR_POOLS, admin)
                .andExpect(status().isBadRequest());
        enviar("PUT", rutaPermisos(proceso) + "/SOLO_LECTURA", """
                {"crearPool":false,"editarPool":false,"eliminarPool":false,
                 "crearLane":false,"editarLane":false,"eliminarLane":false}
                """, admin)
                .andExpect(status().isOk());
        enviar("PUT", rutaPermisos(proceso) + "/EDITOR", JSON_EDITOR_PUEDE_TODO_MENOS_CREAR_POOLS,
                iniciarSesion(EDITOR_ALPES))
                .andExpect(status().isForbidden());

        assertThat(permisoEstructuraProcesoRepository.findByProcesoIdAndRol(proceso.getId(), RolUsuario.EDITOR))
                .isEmpty();
    }

    @Test
    void otraEmpresaNoConsultaNiModificaLaEstructuraDeUnProcesoNoCompartido() throws Exception {
        registrarEmpresas();
        ProcesoRespuestaDto proceso = crearProceso("Ventas");
        MockHttpSession andes = iniciarSesion(ADMIN_ANDES);

        mockMvc.perform(get(rutaPools(proceso)).session(andes)).andExpect(status().isForbidden());
        mockMvc.perform(get(rutaPermisos(proceso)).session(andes)).andExpect(status().isForbidden());
        enviar("POST", rutaPools(proceso), JSON_EXTERNO, andes).andExpect(status().isForbidden());
    }

    @Test
    void elEsquemaIncluyeLasTablasYColumnasDeLaEstructuraDelProceso() {
        assertThat(columnas("pool")).contains("tipo", "orden", "caja_negra", "activo", "empresa_participante_id");
        assertThat(columnas("lane")).contains("pool_id", "rol_proceso_id", "orden", "activo");
        assertThat(columnas("gateway")).contains("pool_id");
        assertThat(columnas("proceso_compartido_empresa")).contains("proceso_id", "empresa_invitada_id", "activo");
        assertThat(columnas("permiso_estructura_proceso")).contains("proceso_id", "rol", "crear_pool",
                "editar_pool", "eliminar_pool", "crear_lane", "editar_lane", "eliminar_lane");
        assertThat(jdbcTemplate.queryForObject("select is_nullable from information_schema.columns"
                + " where table_name = 'lane' and column_name = 'nombre'", String.class)).isEqualTo("YES");
    }

    private List<String> columnas(String tabla) {
        return jdbcTemplate.queryForList("select column_name from information_schema.columns where table_name = ?",
                String.class, tabla);
    }
}
