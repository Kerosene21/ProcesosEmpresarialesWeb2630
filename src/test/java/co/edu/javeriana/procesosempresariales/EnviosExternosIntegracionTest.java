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

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.dto.CrearPoolDto;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.service.ComparticionProcesoService;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.PoolService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EnviosExternosIntegracionTest {

    private static final String ADMIN_ALPES = "admin@alpes-envios.com";
    private static final String EDITOR_ALPES = "editor@alpes-envios.com";
    private static final String LECTOR_ALPES = "lector@alpes-envios.com";
    private static final String ADMIN_ANDES = "admin@andes-envios.com";
    private static final String ADMIN_SIERRA = "admin@sierra-envios.com";
    private static final String PASSWORD = "Clave-Envios-2026";
    private static final String CODIGO = "$.codigo";
    private static final String DESPACHO = "Notificar despacho";

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
    private ComparticionProcesoService comparticionProcesoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private Long andesId;
    private ProcesoRespuestaDto proceso;
    private Long poolAlpes;
    private Long pasarela;

    private void prepararProceso() {
        empresaService.registrar(new RegistroEmpresaDto("Alpes Envios", "905700001-1", ADMIN_ALPES, PASSWORD));
        usuarioService.crear(new CrearUsuarioDto(EDITOR_ALPES, PASSWORD, RolUsuario.EDITOR), ADMIN_ALPES);
        usuarioService.crear(new CrearUsuarioDto(LECTOR_ALPES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ALPES);
        andesId = empresaService.registrar(new RegistroEmpresaDto("Andes Envios", "905700002-2", ADMIN_ANDES,
                PASSWORD)).getId();
        empresaService.registrar(new RegistroEmpresaDto("Sierra Envios", "905700003-3", ADMIN_SIERRA, PASSWORD));
        proceso = procesoService.crear(new CrearProcesoDto("Despachos", "Proceso de despachos", "Logistica"),
                ADMIN_ALPES);
        poolAlpes = proceso.getPoolId();
        pasarela = crearPool("Pasarela de pagos", TipoPool.EXTERNO, true, null);
    }

    private Long crearPool(String nombre, TipoPool tipo, boolean cajaNegra, Long empresaId) {
        return poolService.crear(proceso.getId(), new CrearPoolDto(nombre, tipo, cajaNegra, empresaId), ADMIN_ALPES)
                .getId();
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

    private String jsonEnvio(Long origen, Long destino, String tipo, String fallo) {
        return """
                {"nombreMensaje":"%s","poolOrigenId":%d,"poolDestinoId":%d,"tipoDestino":"%s",
                 "datosEnviados":"numeroGuia: texto; fechaDespacho: fecha","momentoProceso":"Al confirmar el despacho",
                 "comportamientoFallo":"%s","posicionX":700,"posicionY":80}
                """.formatted(DESPACHO, origen, destino, tipo, fallo);
    }

    private String jsonEdicion(Long destino, String tipo, String fallo) {
        return """
                {"nombreMensaje":"%s","poolDestinoId":%d,"tipoDestino":"%s","datosEnviados":"numeroGuia: texto",
                 "momentoProceso":"Al entregar el pedido","comportamientoFallo":"%s","claveCorrelacion":"guia"}
                """.formatted(DESPACHO, destino, tipo, fallo);
    }

    private Long crearEnvio(MockHttpSession sesion) throws Exception {
        return idCreado(enviar("POST", ruta("envios-externos"),
                jsonEnvio(poolAlpes, pasarela, "SERVICIO_WEB", "CONTINUAR"), sesion));
    }

    private List<String> historial() {
        return procesoService.consultarHistorial(proceso.getId(), ADMIN_ALPES).stream()
                .map(HistorialProcesoRespuestaDto::getCambiosRealizados)
                .toList();
    }

    private Map<String, Object> filaDelEnvio(Long id) {
        entityManager.flush();
        return jdbcTemplate.queryForMap("select tipo, activo, pool_id, pool_destino_id, tipo_destino,"
                + " datos_enviados, momento_proceso, comportamiento_fallo from evento where id = ?", id);
    }

    @Test
    void unEnvioHaciaUnSistemaExternoCajaNegraSePersisteConSuDocumentacion() throws Exception {
        prepararProceso();

        Long envioId = idCreado(enviar("POST", ruta("envios-externos"),
                jsonEnvio(poolAlpes, pasarela, "SERVICIO_WEB", "RUTA_ERROR"), iniciarSesion(EDITOR_ALPES))
                .andExpect(jsonPath("$.poolDestinoNombre").value("Pasarela de pagos"))
                .andExpect(jsonPath("$.advertencias").isEmpty()));

        Map<String, Object> fila = filaDelEnvio(envioId);
        assertThat(fila).containsEntry("tipo", "ENVIO_EXTERNO").containsEntry("activo", true)
                .containsEntry("tipo_destino", "SERVICIO_WEB").containsEntry("comportamiento_fallo", "RUTA_ERROR")
                .containsEntry("datos_enviados", "numeroGuia: texto; fechaDespacho: fecha")
                .containsEntry("momento_proceso", "Al confirmar el despacho");
        assertThat(((Number) fila.get("pool_destino_id")).longValue()).isEqualTo(pasarela);
        assertThat(historial()).contains("envío externo creado: '" + DESPACHO + "' (pool 'Alpes Envios' -> pool"
                + " 'Pasarela de pagos', SERVICIO_WEB, ante fallo RUTA_ERROR)");
    }

    @Test
    void seDocumentanTodosLosTiposDeDestinoYComportamientosAnteFallo() throws Exception {
        prepararProceso();
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        String[][] combinaciones = { { "CORREO", "CONTINUAR" }, { "SERVICIO_WEB", "RUTA_ERROR" },
                { "COLA", "FINALIZAR" } };

        for (String[] combinacion : combinaciones) {
            enviar("POST", ruta("envios-externos"), jsonEnvio(poolAlpes, pasarela, combinacion[0], combinacion[1]),
                    admin)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tipoDestino").value(combinacion[0]))
                    .andExpect(jsonPath("$.comportamientoFallo").value(combinacion[1]));
        }

        mockMvc.perform(get(ruta("envios-externos")).session(iniciarSesion(LECTOR_ALPES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void soloUnPoolExternoCajaNegraRepresentaAlSistemaExterno() throws Exception {
        prepararProceso();
        Long abierto = crearPool("Banco", TipoPool.EXTERNO, false, null);
        Long socio = crearPool("Andes", TipoPool.PARTICIPANTE, true, andesId);
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);

        enviar("POST", ruta("envios-externos"), jsonEnvio(pasarela, poolAlpes, "CORREO", "CONTINUAR"), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("ENVIO_EXTERNO_NO_VALIDO"));
        enviar("POST", ruta("envios-externos"), jsonEnvio(poolAlpes, socio, "CORREO", "CONTINUAR"), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("ENVIO_EXTERNO_NO_VALIDO"));
        enviar("POST", ruta("envios-externos"), jsonEnvio(poolAlpes, abierto, "CORREO", "CONTINUAR"), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("ENVIO_EXTERNO_NO_VALIDO"));
        enviar("POST", ruta("envios-externos"), jsonEnvio(pasarela, pasarela, "CORREO", "CONTINUAR"), admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("MENSAJE_ENTRE_MISMO_POOL"));
    }

    @Test
    void unPoolDeOtroProcesoNoEsDestinoDeUnEnvio() throws Exception {
        prepararProceso();
        ProcesoRespuestaDto otro = procesoService.crear(new CrearProcesoDto("Cobros", "Proceso de cobros",
                "Finanzas"), ADMIN_ALPES);
        Long ajeno = poolService.crear(otro.getId(), new CrearPoolDto("DIAN", TipoPool.EXTERNO, true, null),
                ADMIN_ALPES).getId();

        enviar("POST", ruta("envios-externos"), jsonEnvio(poolAlpes, ajeno, "CORREO", "CONTINUAR"),
                iniciarSesion(ADMIN_ALPES))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("POOL_MENSAJE_NO_VALIDO"));
    }

    @Test
    void elSistemaExternoCajaNegraNoAdmiteEventosInternos() throws Exception {
        prepararProceso();

        enviar("POST", ruta("message-catches"), """
                {"nombreMensaje":"Pago aprobado","variante":"INICIO","datosEsperados":"x","actividadesUso":"y",
                 "origenExterno":true,"poolId":%d,"posicionX":1,"posicionY":1}
                """.formatted(pasarela), iniciarSesion(ADMIN_ALPES))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("POOL_CAJA_NEGRA"));
    }

    @Test
    void editarUnEnvioRegistraLosCambiosYUnaEdicionSinCambiosNo() throws Exception {
        prepararProceso();
        MockHttpSession editor = iniciarSesion(EDITOR_ALPES);
        Long envioId = crearEnvio(editor);
        Long dian = crearPool("DIAN", TipoPool.EXTERNO, true, null);

        enviar("PUT", ruta("envios-externos/" + envioId), jsonEdicion(dian, "CORREO", "FINALIZAR"), editor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poolDestinoNombre").value("DIAN"));
        int entradas = historial().size();
        enviar("PUT", ruta("envios-externos/" + envioId), jsonEdicion(dian, "CORREO", "FINALIZAR"), editor)
                .andExpect(status().isOk());

        assertThat(historial()).hasSize(entradas).anyMatch(entrada -> entrada.startsWith("envío externo '"
                + DESPACHO + "': pool destino: 'Pasarela de pagos' -> 'DIAN'"));
        assertThat(filaDelEnvio(envioId)).containsEntry("comportamiento_fallo", "FINALIZAR")
                .containsEntry("momento_proceso", "Al entregar el pedido");
    }

    @Test
    void eliminarUnEnvioEsLogicoYLaSegundaEliminacionNoEncuentraNada() throws Exception {
        prepararProceso();
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        Long envioId = crearEnvio(admin);

        mockMvc.perform(get(ruta("envios-externos/" + envioId + "/eliminacion")).session(admin))
                .andExpect(status().isOk());
        enviar("DELETE", ruta("envios-externos/" + envioId), null, iniciarSesion(EDITOR_ALPES))
                .andExpect(status().isForbidden());
        enviar("DELETE", ruta("envios-externos/" + envioId), null, admin).andExpect(status().isNoContent());

        assertThat(filaDelEnvio(envioId)).containsEntry("activo", false);
        assertThat(historial()).contains("envío externo eliminado: '" + DESPACHO + "'");
        enviar("DELETE", ruta("envios-externos/" + envioId), null, admin).andExpect(status().isNotFound());
        mockMvc.perform(get(ruta("envios-externos/" + envioId)).session(admin)).andExpect(status().isNotFound());
    }

    @Test
    void unSistemaExternoConEnviosNoDejaDeSerCajaNegraNiSeElimina() throws Exception {
        prepararProceso();
        MockHttpSession admin = iniciarSesion(ADMIN_ALPES);
        crearEnvio(admin);

        enviar("PUT", ruta("pools/" + pasarela), """
                {"nombre":"Pasarela de pagos","cajaNegra":false}
                """, admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("POOL_NO_VALIDO"));
        enviar("DELETE", ruta("pools/" + pasarela), null, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("POOL_CON_CONTENIDO"));
    }

    @Test
    void unaEmpresaInvitadaConsultaLosEnviosEnSoloLectura() throws Exception {
        prepararProceso();
        Long envioId = crearEnvio(iniciarSesion(ADMIN_ALPES));
        comparticionProcesoService.compartir(proceso.getId(), andesId, ADMIN_ALPES);
        MockHttpSession andes = iniciarSesion(ADMIN_ANDES);

        mockMvc.perform(get(ruta("envios-externos/" + envioId)).session(andes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoDestino").value("SERVICIO_WEB"));
        enviar("PUT", ruta("envios-externos/" + envioId), jsonEdicion(pasarela, "CORREO", "FINALIZAR"), andes)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
        mockMvc.perform(get(ruta("envios-externos")).session(iniciarSesion(ADMIN_SIERRA)))
                .andExpect(status().isForbidden());
    }
}
