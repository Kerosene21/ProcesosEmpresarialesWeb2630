package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
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
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.CrearGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.CrearPoolDto;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.NodoFlujoDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.service.ActividadService;
import co.edu.javeriana.procesosempresariales.service.ArcoService;
import co.edu.javeriana.procesosempresariales.service.ComparticionProcesoService;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.GatewayService;
import co.edu.javeriana.procesosempresariales.service.PoolService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EventosEnDiagramaIntegracionTest {

    private static final String ADMIN_ALPES = "admin@alpes-diagrama.com";
    private static final String ADMIN_ANDES = "admin@andes-diagrama.com";
    private static final String PASSWORD = "Clave-Diagrama-2026";
    private static final String CODIGO = "$.codigo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private ProcesoService procesoService;

    @Autowired
    private PoolService poolService;

    @Autowired
    private ActividadService actividadService;

    @Autowired
    private GatewayService gatewayService;

    @Autowired
    private ArcoService arcoService;

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
    private Long pasarela;
    private Long revisar;
    private Long gateway;
    private MockHttpSession admin;

    private void prepararProceso() throws Exception {
        empresaService.registrar(new RegistroEmpresaDto("Alpes Diagrama", "905800001-1", ADMIN_ALPES, PASSWORD));
        andesId = empresaService.registrar(new RegistroEmpresaDto("Andes Diagrama", "905800002-2", ADMIN_ANDES,
                PASSWORD)).getId();
        proceso = procesoService.crear(new CrearProcesoDto("Compras", "Proceso de compras", "Operaciones"),
                ADMIN_ALPES);
        poolAlpes = proceso.getPoolId();
        poolProveedor = poolService.crear(proceso.getId(), new CrearPoolDto("Proveedor", TipoPool.EXTERNO, false,
                null), ADMIN_ALPES).getId();
        pasarela = poolService.crear(proceso.getId(), new CrearPoolDto("Pasarela", TipoPool.EXTERNO, true, null),
                ADMIN_ALPES).getId();
        Long lane = actividadService.lanesDelProceso(proceso.getId(), ADMIN_ALPES).get(0).getId();
        revisar = actividadService.crear(proceso.getId(), new CrearActividadDto("Revisar factura",
                TipoActividad.TAREA_USUARIO, lane, 200, 60), ADMIN_ALPES).getId();
        gateway = gatewayService.crear(proceso.getId(), new CrearGatewayDto(TipoGateway.PARALELO, 400, 60,
                poolAlpes), ADMIN_ALPES).getId();
        admin = iniciarSesion(ADMIN_ALPES);
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

    private ResultActions enviar(String metodo, String ruta, String json) throws Exception {
        MockHttpServletRequestBuilder peticion = switch (metodo) {
            case "POST" -> post(ruta);
            case "PUT" -> put(ruta);
            default -> delete(ruta);
        };
        if (json != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(peticion.session(admin).with(csrf()));
    }

    private Long idCreado(ResultActions resultado) throws Exception {
        String cuerpo = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private Long crearThrow(Long origen, Long destino) throws Exception {
        return idCreado(enviar("POST", ruta("message-throws"), """
                {"nombreMensaje":"Orden de compra","contenido":"numeroOrden: texto","poolOrigenId":%d,
                 "poolDestinoId":%d,"claveCorrelacion":"orden","posicionX":600,"posicionY":60}
                """.formatted(origen, destino)));
    }

    private Long crearCatch(Long pool, String variante) throws Exception {
        return idCreado(enviar("POST", ruta("message-catches"), """
                {"nombreMensaje":"Orden de compra","variante":"%s","datosEsperados":"numeroOrden: texto",
                 "actividadesUso":"Preparar pedido","claveCorrelacion":"orden","comportamientoSinCaso":"%s",
                 "poolId":%d,"posicionX":40,"posicionY":60}
                """.formatted(variante, "INICIO".equals(variante) ? "INICIAR_NUEVO_CASO" : "DESCARTAR", pool)));
    }

    private Long crearEnvio() throws Exception {
        return idCreado(enviar("POST", ruta("envios-externos"), """
                {"nombreMensaje":"Notificar pago","poolOrigenId":%d,"poolDestinoId":%d,"tipoDestino":"SERVICIO_WEB",
                 "datosEnviados":"valor: decimal","momentoProceso":"Al aprobar la factura",
                 "comportamientoFallo":"CONTINUAR","posicionX":800,"posicionY":60}
                """.formatted(poolAlpes, pasarela)));
    }

    private ResultActions crearArco(String origenTipo, Long origenId, String destinoTipo, Long destinoId)
            throws Exception {
        return enviar("POST", ruta("arcos"), """
                {"origenTipo":"%s","origenId":%d,"destinoTipo":"%s","destinoId":%d}
                """.formatted(origenTipo, origenId, destinoTipo, destinoId));
    }

    private long arcosActivosConEvento(Long eventoId) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("""
                select count(*) from arco where activo = true
                and ((origen_tipo = 'EVENTO' and origen_id = ?) or (destino_tipo = 'EVENTO' and destino_id = ?))
                """, Long.class, eventoId, eventoId);
    }

    @Test
    void losEventosSeConectanConFlujosDeSecuenciaEnTodasLasDirecciones() throws Exception {
        prepararProceso();
        Long inicio = crearCatch(poolAlpes, "INICIO");
        Long orden = crearThrow(poolAlpes, poolProveedor);
        Long envio = crearEnvio();

        crearArco("EVENTO", inicio, "ACTIVIDAD", revisar).andExpect(status().isCreated());
        crearArco("ACTIVIDAD", revisar, "GATEWAY", gateway).andExpect(status().isCreated());
        crearArco("GATEWAY", gateway, "EVENTO", orden).andExpect(status().isCreated());
        crearArco("GATEWAY", gateway, "EVENTO", envio).andExpect(status().isCreated());
        crearArco("EVENTO", orden, "EVENTO", envio)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origenNombre").value("Message Throw: Orden de compra"))
                .andExpect(jsonPath("$.destinoNombre").value("Envío externo: Notificar pago"));
        crearArco("EVENTO", envio, "GATEWAY", gateway).andExpect(status().isCreated());

        assertThat(arcosActivosConEvento(orden)).isEqualTo(2);
        assertThat(arcosActivosConEvento(envio)).isEqualTo(3);
        List<NodoFlujoDto> nodos = arcoService.nodosDelProceso(proceso.getId(), ADMIN_ALPES);
        assertThat(nodos).filteredOn(nodo -> nodo.getTipo() == TipoNodoFlujo.EVENTO)
                .extracting(NodoFlujoDto::getId).containsExactlyInAnyOrder(inicio, orden, envio);
    }

    @Test
    void unFlujoDeSecuenciaNoConectaUnEventoDeOtroPool() throws Exception {
        prepararProceso();
        Long catchDelProveedor = crearCatch(poolProveedor, "INTERMEDIO");

        crearArco("ACTIVIDAD", revisar, "EVENTO", catchDelProveedor)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("SECUENCIA_ENTRE_POOLS"));
    }

    @Test
    void unArcoNoConectaUnEventoInexistenteNiEliminado() throws Exception {
        prepararProceso();
        Long orden = crearThrow(poolAlpes, poolProveedor);
        enviar("DELETE", ruta("message-throws/" + orden), null).andExpect(status().isNoContent());

        crearArco("ACTIVIDAD", revisar, "EVENTO", orden)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("NODO_NO_VALIDO"));
        crearArco("ACTIVIDAD", revisar, "EVENTO", 999_999L)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("NODO_NO_VALIDO"));
    }

    @Test
    void eliminarUnEventoDesactivaSusFlujosDeSecuencia() throws Exception {
        prepararProceso();
        Long orden = crearThrow(poolAlpes, poolProveedor);
        crearArco("ACTIVIDAD", revisar, "EVENTO", orden).andExpect(status().isCreated());
        crearArco("EVENTO", orden, "GATEWAY", gateway).andExpect(status().isCreated());

        mockMvc.perform(get(ruta("message-throws/" + orden + "/eliminacion")).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advertencias[0]")
                        .value("Se desactivarán 2 arcos conectados a Message Throw: Orden de compra."));
        enviar("DELETE", ruta("message-throws/" + orden), null).andExpect(status().isNoContent());

        assertThat(arcosActivosConEvento(orden)).isZero();
        assertThat(procesoService.consultarHistorial(proceso.getId(), ADMIN_ALPES))
                .anyMatch(entrada -> entrada.getCambiosRealizados()
                        .equals("message throw eliminado: 'Orden de compra'; arcos desactivados: 2"));
    }

    @Test
    void elDiagramaCompletoIncluyeEventosYFlujosDeMensaje() throws Exception {
        prepararProceso();
        Long catchDelProveedor = crearCatch(poolProveedor, "INTERMEDIO");
        Long orden = crearThrow(poolAlpes, poolProveedor);
        Long envio = crearEnvio();
        crearArco("ACTIVIDAD", revisar, "EVENTO", orden).andExpect(status().isCreated());
        comparticionProcesoService.compartir(proceso.getId(), andesId, ADMIN_ALPES);

        mockMvc.perform(get(ruta("diagrama")).session(iniciarSesion(ADMIN_ANDES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procesoId").value(proceso.getId()))
                .andExpect(jsonPath("$.pools", hasSize(3)))
                .andExpect(jsonPath("$.lanes", hasSize(1)))
                .andExpect(jsonPath("$.actividades", hasSize(1)))
                .andExpect(jsonPath("$.gateways", hasSize(1)))
                .andExpect(jsonPath("$.arcos", hasSize(1)))
                .andExpect(jsonPath("$.arcos[0].destinoTipo").value("EVENTO"))
                .andExpect(jsonPath("$.messageThrows[0].id").value(orden))
                .andExpect(jsonPath("$.messageCatches[0].id").value(catchDelProveedor))
                .andExpect(jsonPath("$.enviosExternos[0].id").value(envio))
                .andExpect(jsonPath("$.flujosMensaje", hasSize(2)))
                .andExpect(jsonPath("$.flujosMensaje[0].tipoEvento").value("MESSAGE_THROW"))
                .andExpect(jsonPath("$.flujosMensaje[0].poolOrigenId").value(poolAlpes))
                .andExpect(jsonPath("$.flujosMensaje[0].poolDestinoId").value(poolProveedor))
                .andExpect(jsonPath("$.flujosMensaje[0].catchDestinoId").value(catchDelProveedor))
                .andExpect(jsonPath("$.flujosMensaje[1].tipoEvento").value("ENVIO_EXTERNO"))
                .andExpect(jsonPath("$.flujosMensaje[1].poolDestinoId").value(pasarela));
    }

    @Test
    void elDiagramaDeUnProcesoNoCompartidoNoSeConsulta() throws Exception {
        prepararProceso();

        mockMvc.perform(get(ruta("diagrama")).session(iniciarSesion(ADMIN_ANDES)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void unPoolConEventosNoSeEliminaNiSeConvierteEnCajaNegra() throws Exception {
        prepararProceso();
        crearCatch(poolProveedor, "INTERMEDIO");

        enviar("DELETE", ruta("pools/" + poolProveedor), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("POOL_CON_CONTENIDO"));
        enviar("PUT", ruta("pools/" + poolProveedor), """
                {"nombre":"Proveedor","cajaNegra":true}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("POOL_CON_CONTENIDO"));
    }

    @Test
    void unPoolDestinoDeUnFlujoDeMensajeNoSeElimina() throws Exception {
        prepararProceso();
        Long aduana = poolService.crear(proceso.getId(), new CrearPoolDto("Aduana", TipoPool.EXTERNO, true, null),
                ADMIN_ALPES).getId();
        crearThrow(poolAlpes, aduana);

        enviar("DELETE", ruta("pools/" + aduana), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("POOL_CON_CONTENIDO"));
    }
}
