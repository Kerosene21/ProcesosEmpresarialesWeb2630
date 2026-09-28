package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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

import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.CrearPoolDto;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.service.ActividadService;
import co.edu.javeriana.procesosempresariales.service.ComparticionProcesoService;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.PoolService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MensajesEntreProcesosIntegracionTest {

    private static final String ADMIN_ALPES = "admin@alpes-mensajes.com";
    private static final String EDITOR_ALPES = "editor@alpes-mensajes.com";
    private static final String LECTOR_ALPES = "lector@alpes-mensajes.com";
    private static final String ADMIN_ANDES = "admin@andes-mensajes.com";
    private static final String ADMIN_SIERRA = "admin@sierra-mensajes.com";
    private static final String PASSWORD = "Clave-Mensajes-2026";
    private static final String CODIGO = "$.codigo";
    private static final String ADVERTENCIAS = "$.advertencias";
    private static final String PAGO = "Pago confirmado";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProcesoService procesoService;

    @Autowired
    private PoolService poolService;

    @Autowired
    private ActividadService actividadService;

    @Autowired
    private ComparticionProcesoService comparticionProcesoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private Long andesId;
    private ProcesoRespuestaDto proceso;
    private Long poolAlpes;
    private Long poolProveedor;

    private void prepararProceso() {
        empresaService.registrar(new RegistroEmpresaDto("Alpes Mensajes", "905600001-1", ADMIN_ALPES, PASSWORD));
        usuarioService.crear(new CrearUsuarioDto(EDITOR_ALPES, PASSWORD, RolUsuario.EDITOR), ADMIN_ALPES);
        usuarioService.crear(new CrearUsuarioDto(LECTOR_ALPES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ALPES);
        andesId = empresaService.registrar(new RegistroEmpresaDto("Andes Mensajes", "905600002-2", ADMIN_ANDES,
                PASSWORD)).getId();
        empresaService.registrar(new RegistroEmpresaDto("Sierra Mensajes", "905600003-3", ADMIN_SIERRA, PASSWORD));
        proceso = procesoService.crear(new CrearProcesoDto("Compras", "Proceso de compras", "Operaciones"),
                ADMIN_ALPES);
        poolAlpes = proceso.getPoolId();
        poolProveedor = poolService.crear(proceso.getId(), new CrearPoolDto("Proveedor", TipoPool.EXTERNO, false,
                null), ADMIN_ALPES).getId();
    }

    private MockHttpSession iniciarSesion(String correo) throws Exception {
        MvcResult resultado = mockMvc.perform(formLogin().user(correo).password(PASSWORD))
                .andExpect(authenticated().withUsername(correo))
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private String ruta(String recurso) {
        return "/api/procesos/" + proceso.getId() + "/" + recurso;
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

    private String jsonThrow(String nombre, Long origen, Long destino, String clave) {
        return """
                {"nombreMensaje":"%s","contenido":"numeroFactura: texto; valor: decimal","poolOrigenId":%d,
                 "poolDestinoId":%d,%s"posicionX":400,"posicionY":60}
                """.formatted(nombre, origen, destino, clave == null ? "" : "\"claveCorrelacion\":\"" + clave + "\",");
    }

    private String jsonCatch(String nombre, String variante, Long pool, String clave, boolean externo,
            String comportamiento) {
        return """
                {"nombreMensaje":"%s","variante":"%s","datosEsperados":"numeroFactura: texto",
                 "actividadesUso":"Registrar pago","origenExterno":%s,"poolId":%d,%s%s"posicionX":40,"posicionY":60}
                """.formatted(nombre, variante, externo, pool,
                clave == null ? "" : "\"claveCorrelacion\":\"" + clave + "\",",
                comportamiento == null ? "" : "\"comportamientoSinCaso\":\"" + comportamiento + "\",");
    }

    private Long crearThrow(String nombre, String clave, MockHttpSession sesion) throws Exception {
        return idCreado(enviar("POST", ruta("message-throws"), jsonThrow(nombre, poolAlpes, poolProveedor, clave),
                sesion));
    }

    private Long crearCatch(String json, MockHttpSession sesion) throws Exception {
        return idCreado(enviar("POST", ruta("message-catches"), json, sesion));
    }

    private List<String> historial() {
        return procesoService.consultarHistorial(proceso.getId(), ADMIN_ALPES).stream()
                .map(HistorialProcesoRespuestaDto::getCambiosRealizados)
                .toList();
    }

    private Map<String, Object> filaDelEvento(Long id) {
        entityManager.flush();
        return jdbcTemplate.queryForMap("select tipo, activo, pool_id, pool_destino_id, clave_correlacion,"
                + " comportamiento_sin_caso, variante, origen_externo from evento where id = ?", id);
    }

    @Test
    void unThrowYSuCatchHomologoSePersistenYQuedanCorrelacionados() throws Exception {
        prepararProceso();
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);

        String creado = enviar("POST", ruta("message-throws"), jsonThrow(PAGO, poolAlpes, poolProveedor, "factura"),
                admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.poolOrigenNombre").value("Alpes Mensajes"))
                .andExpect(jsonPath("$.poolDestinoNombre").value("Proveedor"))
                .andExpect(jsonPath(ADVERTENCIAS + "[0]", containsString("No existe un Message Catch")))
                .andReturn().getResponse().getContentAsString();
        Long throwId = ((Number) JsonPath.read(creado, "$.id")).longValue();

        Long catchId = crearCatch(jsonCatch("PAGO  confirmado", "INTERMEDIO", poolProveedor, " Factura ", false,
                "DESCARTAR"), iniciarSesion(EDITOR_ALPES));

        mockMvc.perform(get(ruta("message-throws/" + throwId)).session(iniciarSesion(LECTOR_ALPES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catchHomologoId").value(catchId))
                .andExpect(jsonPath(ADVERTENCIAS).isEmpty());
        mockMvc.perform(get(ruta("message-catches/" + catchId)).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.throwHomologoId").value(throwId))
                .andExpect(jsonPath("$.claveCorrelacion").value("Factura"))
                .andExpect(jsonPath(ADVERTENCIAS).isEmpty());

        Map<String, Object> filaThrow = filaDelEvento(throwId);
        assertThat(filaThrow).containsEntry("tipo", "MESSAGE_THROW").containsEntry("clave_correlacion", "factura");
        assertThat(((Number) filaThrow.get("pool_id")).longValue()).isEqualTo(poolAlpes);
        assertThat(((Number) filaThrow.get("pool_destino_id")).longValue()).isEqualTo(poolProveedor);
        Map<String, Object> filaCatch = filaDelEvento(catchId);
        assertThat(filaCatch).containsEntry("tipo", "MESSAGE_CATCH").containsEntry("variante", "INTERMEDIO")
                .containsEntry("comportamiento_sin_caso", "DESCARTAR").containsEntry("origen_externo", false);
        assertThat(filaCatch.get("pool_destino_id")).isNull();
        assertThat(historial()).contains("message throw creado: '" + PAGO + "' (pool 'Alpes Mensajes' -> pool"
                + " 'Proveedor')", "message catch creado: 'PAGO  confirmado' (INTERMEDIO en el pool 'Proveedor')");
    }

    @Test
    void laTablaDeEventosDeclaraLasLlavesForaneasHaciaPoolYProceso() {
        List<String> referenciadas = jdbcTemplate.queryForList("""
                select ccu.table_name || '.' || kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name
                join information_schema.constraint_column_usage ccu on tc.constraint_name = ccu.constraint_name
                where tc.table_name = 'evento' and tc.constraint_type = 'FOREIGN KEY'
                """, String.class);

        assertThat(referenciadas).contains("pool.pool_id", "pool.pool_destino_id", "proceso.proceso_id");
    }

    @Test
    void unFlujoDeMensajeDentroDelMismoPoolSeRechaza() throws Exception {
        prepararProceso();

        enviar("POST", ruta("message-throws"), jsonThrow(PAGO, poolAlpes, poolAlpes, null),
                iniciarSesion(EDITOR_ALPES))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("MENSAJE_ENTRE_MISMO_POOL"));
    }

    @Test
    void unPoolDeOtroProcesoNuncaEsExtremoDeUnFlujoDeMensaje() throws Exception {
        prepararProceso();
        ProcesoRespuestaDto otro = procesoService.crear(new CrearProcesoDto("Ventas", "Proceso de ventas",
                "Comercial"), ADMIN_ALPES);
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);

        enviar("POST", ruta("message-throws"), jsonThrow(PAGO, poolAlpes, otro.getPoolId(), null), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("POOL_MENSAJE_NO_VALIDO"));
        enviar("POST", ruta("message-throws"), jsonThrow(PAGO, otro.getPoolId(), poolProveedor, null), admin)
                .andExpect(status().isNotFound());
        enviar("POST", ruta("message-catches"), jsonCatch(PAGO, "INICIO", otro.getPoolId(), null, true, null), admin)
                .andExpect(status().isNotFound());
    }

    @Test
    void unPoolEliminadoNoRecibeMensajes() throws Exception {
        prepararProceso();
        Long vacio = poolService.crear(proceso.getId(), new CrearPoolDto("Aduana", TipoPool.EXTERNO, true, null),
                ADMIN_ALPES).getId();
        poolService.eliminar(proceso.getId(), vacio, ADMIN_ALPES);

        enviar("POST", ruta("message-throws"), jsonThrow(PAGO, poolAlpes, vacio, null), iniciarSesion(ADMIN_ALPES))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("POOL_MENSAJE_NO_VALIDO"));
    }

    @Test
    void elUsuarioDeSoloLecturaConsultaPeroNoModelaMensajes() throws Exception {
        prepararProceso();
        crearThrow(PAGO, null, iniciarSesion(ADMIN_ALPES));
        MockHttpSession lector = iniciarSesion(LECTOR_ALPES);

        mockMvc.perform(get(ruta("message-throws")).session(lector))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        enviar("POST", ruta("message-throws"), jsonThrow(PAGO, poolAlpes, poolProveedor, null), lector)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
        enviar("POST", ruta("message-catches"), jsonCatch(PAGO, "INICIO", poolAlpes, null, true, null), lector)
                .andExpect(status().isForbidden());
    }

    @Test
    void editarUnThrowSoloRegistraLosCambiosReales() throws Exception {
        prepararProceso();
        MockHttpSession editor = iniciarSesion(EDITOR_ALPES);
        Long throwId = crearThrow(PAGO, "factura", editor);
        String edicion = """
                {"nombreMensaje":"%s","contenido":"numeroFactura: texto; valor: decimal","poolDestinoId":%d,
                 "claveCorrelacion":"radicado","comportamientoSinCaso":"INICIAR_NUEVO_CASO"}
                """.formatted(PAGO, poolProveedor);

        enviar("PUT", ruta("message-throws/" + throwId), edicion, editor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claveCorrelacion").value("radicado"));
        int entradas = historial().size();
        enviar("PUT", ruta("message-throws/" + throwId), edicion, editor).andExpect(status().isOk());

        assertThat(historial()).hasSize(entradas).contains("message throw '" + PAGO + "': clave de correlacion:"
                + " 'factura' -> 'radicado'; comportamiento sin caso: '' -> 'INICIAR_NUEVO_CASO'");
    }

    @Test
    void eliminarUnThrowEsLogicoConConfirmacionYHistorial() throws Exception {
        prepararProceso();
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        Long throwId = crearThrow(PAGO, "factura", admin);
        crearCatch(jsonCatch(PAGO, "INTERMEDIO", poolProveedor, "factura", false, "DESCARTAR"), admin);

        mockMvc.perform(get(ruta("message-throws/" + throwId + "/eliminacion")).session(iniciarSesion(EDITOR_ALPES)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ruta("message-throws/" + throwId + "/eliminacion")).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ADVERTENCIAS, hasItem("El Message Catch '" + PAGO + "' del pool 'Proveedor'"
                        + " quedará sin Message Throw homólogo.")));
        enviar("DELETE", ruta("message-throws/" + throwId), null, iniciarSesion(EDITOR_ALPES))
                .andExpect(status().isForbidden());
        enviar("DELETE", ruta("message-throws/" + throwId), null, admin).andExpect(status().isNoContent());

        assertThat(filaDelEvento(throwId)).containsEntry("activo", false);
        assertThat(historial()).contains("message throw eliminado: '" + PAGO + "'");
        mockMvc.perform(get(ruta("message-throws")).session(admin)).andExpect(jsonPath("$").isEmpty());
        enviar("DELETE", ruta("message-throws/" + throwId), null, admin)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(CODIGO).value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void clavesIncoherentesYCatchIntermedioSinClaveGeneranAdvertencias() throws Exception {
        prepararProceso();
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        crearThrow(PAGO, "factura", admin);

        enviar("POST", ruta("message-catches"), jsonCatch(PAGO, "INTERMEDIO", poolProveedor, "radicado", false,
                "DESCARTAR"), admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath(ADVERTENCIAS + "[0]", containsString("Clave de correlación incoherente")));
        enviar("POST", ruta("message-catches"), jsonCatch("Orden despachada", "INTERMEDIO", poolAlpes, null, true,
                null), admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath(ADVERTENCIAS, hasSize(2)))
                .andExpect(jsonPath(ADVERTENCIAS + "[0]", containsString("no declara clave de correlación")));
    }

    @Test
    void dosMensajesConMismoNombreYClaveSonAmbiguos() throws Exception {
        prepararProceso();
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        crearThrow(PAGO, "factura", admin);

        enviar("POST", ruta("message-throws"), jsonThrow("pago CONFIRMADO", poolAlpes, poolProveedor, " FACTURA "),
                admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath(ADVERTENCIAS, hasItem("Ambigüedad de correlación: otro Message Throw del proceso"
                        + " comparte el nombre 'pago CONFIRMADO' y la clave de correlación 'FACTURA'.")));
    }

    @Test
    void losMensajesDeOtroProcesoNoGeneranAmbiguedad() throws Exception {
        prepararProceso();
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        ProcesoRespuestaDto ventas = procesoService.crear(new CrearProcesoDto("Ventas", "Proceso de ventas",
                "Comercial"), ADMIN_ALPES);
        Long proveedorDeVentas = poolService.crear(ventas.getId(), new CrearPoolDto("Proveedor", TipoPool.EXTERNO,
                false, null), ADMIN_ALPES).getId();
        enviar("POST", "/api/procesos/" + ventas.getId() + "/message-throws",
                jsonThrow(PAGO, ventas.getPoolId(), proveedorDeVentas, "factura"), admin)
                .andExpect(status().isCreated());

        enviar("POST", ruta("message-throws"), jsonThrow(PAGO, poolAlpes, poolProveedor, "factura"), admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath(ADVERTENCIAS, hasSize(1)))
                .andExpect(jsonPath(ADVERTENCIAS + "[0]", containsString("No existe un Message Catch")));
    }

    @Test
    void unaEmpresaAjenaNoModificaLosMensajesDeOtraEmpresa() throws Exception {
        prepararProceso();
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        Long throwId = crearThrow(PAGO, "factura", admin);
        Long catchId = crearCatch(jsonCatch(PAGO, "INTERMEDIO", poolProveedor, "factura", false, "DESCARTAR"), admin);
        MockHttpSession sierra = iniciarSesion(ADMIN_SIERRA);

        enviar("PUT", ruta("message-throws/" + throwId), """
                {"nombreMensaje":"Orden alterada","contenido":"x","poolDestinoId":%d}
                """.formatted(poolProveedor), sierra)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
        enviar("DELETE", ruta("message-catches/" + catchId), null, sierra).andExpect(status().isForbidden());
        enviar("POST", ruta("message-throws"), jsonThrow(PAGO, poolAlpes, poolProveedor, null), sierra)
                .andExpect(status().isForbidden());

        assertThat(filaDelEvento(throwId)).containsEntry("activo", true);
        assertThat(filaDelEvento(catchId)).containsEntry("activo", true);
        assertThat(jdbcTemplate.queryForObject("select nombre_mensaje from evento where id = ?", String.class,
                throwId)).isEqualTo(PAGO);
    }

    @Test
    void unCatchDeInicioNoPuedeDescartarMensajesSinCaso() throws Exception {
        prepararProceso();

        enviar("POST", ruta("message-catches"), jsonCatch(PAGO, "INICIO", poolAlpes, null, true, "DESCARTAR"),
                iniciarSesion(ADMIN_ALPES))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("CORRELACION_NO_VALIDA"));
    }

    @Test
    void unCatchDeInicioNoAdmiteFlujosDeSecuenciaEntrantes() throws Exception {
        prepararProceso();
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        Long lane = actividadService.lanesDelProceso(proceso.getId(), ADMIN_ALPES).get(0).getId();
        Long registrar = actividadService.crear(proceso.getId(), new CrearActividadDto("Registrar pago",
                TipoActividad.TAREA_USUARIO, lane, 200, 60), ADMIN_ALPES).getId();
        Long inicio = crearCatch(jsonCatch("Pedido recibido", "INICIO", poolAlpes, null, true, null), admin);
        Long intermedio = crearCatch(jsonCatch(PAGO, "INTERMEDIO", poolAlpes, "factura", true, "DESCARTAR"), admin);

        enviar("POST", ruta("arcos"), arco("ACTIVIDAD", registrar, "EVENTO", inicio), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("CATCH_INICIO_CON_ENTRADA"));
        enviar("POST", ruta("arcos"), arco("EVENTO", inicio, "ACTIVIDAD", registrar), admin)
                .andExpect(status().isCreated());
        enviar("POST", ruta("arcos"), arco("ACTIVIDAD", registrar, "EVENTO", intermedio), admin)
                .andExpect(status().isCreated());
        enviar("PUT", ruta("message-catches/" + intermedio), """
                {"nombreMensaje":"%s","variante":"INICIO","datosEsperados":"numeroFactura: texto",
                 "actividadesUso":"Registrar pago","origenExterno":true}
                """.formatted(PAGO), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("CATCH_INICIO_CON_ENTRADA"));

        assertThat(filaDelEvento(intermedio)).containsEntry("variante", "INTERMEDIO");
    }

    private String arco(String origenTipo, Long origenId, String destinoTipo, Long destinoId) {
        return """
                {"origenTipo":"%s","origenId":%d,"destinoTipo":"%s","destinoId":%d}
                """.formatted(origenTipo, origenId, destinoTipo, destinoId);
    }

    @Test
    void unaEmpresaInvitadaConsultaLosMensajesPeroNoLosModifica() throws Exception {
        prepararProceso();
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        Long throwId = crearThrow(PAGO, "factura", admin);
        Long catchId = crearCatch(jsonCatch(PAGO, "INTERMEDIO", poolProveedor, "factura", false, "DESCARTAR"), admin);
        comparticionProcesoService.compartir(proceso.getId(), andesId, ADMIN_ALPES);
        MockHttpSession andes = iniciarSesion(ADMIN_ANDES);

        mockMvc.perform(get(ruta("message-throws/" + throwId)).session(andes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catchHomologoId").value(catchId));
        mockMvc.perform(get(ruta("message-catches")).session(andes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        enviar("POST", ruta("message-throws"), jsonThrow(PAGO, poolAlpes, poolProveedor, null), andes)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
        enviar("DELETE", ruta("message-catches/" + catchId), null, andes).andExpect(status().isForbidden());
        assertThat(filaDelEvento(catchId)).containsEntry("activo", true);
    }

    @Test
    void unaEmpresaNoInvitadaNoConsultaLosMensajes() throws Exception {
        prepararProceso();
        crearThrow(PAGO, null, iniciarSesion(ADMIN_ALPES));
        MockHttpSession sierra = iniciarSesion(ADMIN_SIERRA);

        mockMvc.perform(get(ruta("message-throws")).session(sierra))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
        mockMvc.perform(get(ruta("message-catches")).session(sierra)).andExpect(status().isForbidden());
    }
}
