package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.Gateway;
import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
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
import co.edu.javeriana.procesosempresariales.dto.EditarActividadDto;
import co.edu.javeriana.procesosempresariales.dto.EditarArcoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.exception.ArcoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.CondicionArcoNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.ModeloDeProcesoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.NodoFlujoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.ArcoRepository;
import co.edu.javeriana.procesosempresariales.repository.GatewayRepository;
import co.edu.javeriana.procesosempresariales.repository.LaneRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
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
class ArcosYGatewaysIntegracionTest {

    private static final String ADMIN_ALPES = "admin@alpes-flujo.com";
    private static final String EDITOR_ALPES = "editor@alpes-flujo.com";
    private static final String LECTOR_ALPES = "lector@alpes-flujo.com";
    private static final String ADMIN_ANDES = "admin@andes-flujo.com";
    private static final String PASSWORD = "Clave-Flujo-2026";
    private static final String REVISAR = "Revisar solicitud";
    private static final String APROBAR = "Aprobar solicitud";
    private static final String RECHAZAR = "Rechazar solicitud";
    private static final String CONDICION_APROBADA = "monto alto";
    private static final String CONDICION_RECHAZADA = "monto bajo";

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
    private ArcoRepository arcoRepository;

    @Autowired
    private GatewayRepository gatewayRepository;

    @Autowired
    private LaneRepository laneRepository;

    @Autowired
    private ProcesoRepository procesoRepository;

    private void registrarAlpes() {
        empresaService.registrar(new RegistroEmpresaDto("Alpes Flujo", "905100001-1", ADMIN_ALPES, PASSWORD));
        usuarioService.crear(new CrearUsuarioDto(EDITOR_ALPES, PASSWORD, RolUsuario.EDITOR), ADMIN_ALPES);
        usuarioService.crear(new CrearUsuarioDto(LECTOR_ALPES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ALPES);
    }

    private void registrarAndes() {
        empresaService.registrar(new RegistroEmpresaDto("Andes Flujo", "905100002-2", ADMIN_ANDES, PASSWORD));
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

    private Long laneInicial(ProcesoRespuestaDto proceso, String autor) {
        return actividadService.lanesDelProceso(proceso.getId(), autor).get(0).getId();
    }

    private ActividadRespuestaDto crearActividad(ProcesoRespuestaDto proceso, String nombre, int x, String autor) {
        return actividadService.crear(proceso.getId(),
                new CrearActividadDto(nombre, TipoActividad.TAREA_USUARIO, laneInicial(proceso, autor), x, 40),
                autor);
    }

    private GatewayRespuestaDto crearGateway(ProcesoRespuestaDto proceso, TipoGateway tipo, String autor) {
        return gatewayService.crear(proceso.getId(), new CrearGatewayDto(tipo, 300, 120), autor);
    }

    private ArcoRespuestaDto unirActividades(ProcesoRespuestaDto proceso, Long origenId, Long destinoId,
            String autor) {
        return arcoService.crear(proceso.getId(), new CrearArcoDto(TipoNodoFlujo.ACTIVIDAD, origenId,
                TipoNodoFlujo.ACTIVIDAD, destinoId, null, null), autor);
    }

    private ArcoRespuestaDto salirDelGateway(ProcesoRespuestaDto proceso, Long gatewayId, Long destinoId,
            String condicion, String autor) {
        return arcoService.crear(proceso.getId(), new CrearArcoDto(TipoNodoFlujo.GATEWAY, gatewayId,
                TipoNodoFlujo.ACTIVIDAD, destinoId, null, condicion), autor);
    }

    private Map<Long, String> condiciones(Long arcoId, String condicion) {
        Map<Long, String> mapa = new LinkedHashMap<>();
        mapa.put(arcoId, condicion);
        return mapa;
    }

    @Test
    void elArcoQuedaPersistidoYEnlazadoASuProcesoYASusExtremos() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad(proceso, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 400, ADMIN_ALPES);

        ArcoRespuestaDto creado = unirActividades(proceso, revisar.getId(), aprobar.getId(), EDITOR_ALPES);

        Arco persistido = arcoRepository.findById(creado.getId()).orElseThrow();
        assertThat(persistido.getProceso().getId()).isEqualTo(proceso.getId());
        assertThat(persistido.getOrigenTipo()).isEqualTo(TipoNodoFlujo.ACTIVIDAD);
        assertThat(persistido.getOrigenId()).isEqualTo(revisar.getId());
        assertThat(persistido.getDestinoId()).isEqualTo(aprobar.getId());
        assertThat(persistido.getCondicion()).isNull();
        assertThat(persistido.isActivo()).isTrue();
        assertThat(creado.getOrigenNombre()).isEqualTo(REVISAR);
        assertThat(creado.getDestinoNombre()).isEqualTo(APROBAR);
    }

    @Test
    void noSePuedeRepetirUnArcoActivoEntreLosMismosNodos() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad(proceso, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 400, ADMIN_ALPES);
        unirActividades(proceso, revisar.getId(), aprobar.getId(), ADMIN_ALPES);

        assertThatThrownBy(() -> unirActividades(proceso, revisar.getId(), aprobar.getId(), ADMIN_ALPES))
                .isInstanceOf(ArcoDuplicadoException.class);

        assertThat(arcoRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(proceso.getId())).hasSize(1);
    }

    @Test
    void unArcoEliminadoDejaLibreElParOrigenDestino() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad(proceso, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 400, ADMIN_ALPES);
        ArcoRespuestaDto primero = unirActividades(proceso, revisar.getId(), aprobar.getId(), ADMIN_ALPES);
        arcoService.eliminar(proceso.getId(), primero.getId(), ADMIN_ALPES);

        ArcoRespuestaDto segundo = unirActividades(proceso, revisar.getId(), aprobar.getId(), ADMIN_ALPES);

        assertThat(segundo.getId()).isNotEqualTo(primero.getId());
        assertThat(arcoRepository.findById(primero.getId()).orElseThrow().isActivo()).isFalse();
        assertThat(arcoRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(proceso.getId())).hasSize(1);
    }

    @Test
    void laEliminacionDelArcoConservaLaFilaYLaMarcaComoInactiva() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad(proceso, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 400, ADMIN_ALPES);
        ArcoRespuestaDto creado = unirActividades(proceso, revisar.getId(), aprobar.getId(), ADMIN_ALPES);
        long filasAntes = arcoRepository.count();

        ArcoRespuestaDto eliminado = arcoService.eliminar(proceso.getId(), creado.getId(), ADMIN_ALPES);

        assertThat(arcoRepository.count()).isEqualTo(filasAntes);
        assertThat(arcoRepository.findById(creado.getId()).orElseThrow().isActivo()).isFalse();
        assertThat(eliminado.getAdvertencias()).containsExactly("'" + REVISAR + "' quedó sin arcos de salida",
                "'" + APROBAR + "' quedó sin arcos de entrada");
        assertThatThrownBy(() -> arcoService.eliminar(proceso.getId(), creado.getId(), ADMIN_ALPES))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void unNodoDeOtroProcesoNoSirveComoExtremoDelArco() {
        registrarAlpes();
        ProcesoRespuestaDto ventas = crearProceso("Ventas", ADMIN_ALPES);
        ProcesoRespuestaDto compras = crearProceso("Compras", ADMIN_ALPES);
        ActividadRespuestaDto enVentas = crearActividad(ventas, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto enCompras = crearActividad(compras, APROBAR, 400, ADMIN_ALPES);

        assertThatThrownBy(() -> unirActividades(ventas, enVentas.getId(), enCompras.getId(), ADMIN_ALPES))
                .isInstanceOf(NodoFlujoNoValidoException.class);
    }

    @Test
    void unaEmpresaNoTocaLosArcosNiLosGatewaysDeOtra() {
        registrarAlpes();
        registrarAndes();
        ProcesoRespuestaDto deAndes = crearProceso("Compras Andes", ADMIN_ANDES);
        crearActividad(deAndes, REVISAR, 100, ADMIN_ANDES);

        assertThatThrownBy(() -> gatewayService.crear(deAndes.getId(),
                new CrearGatewayDto(TipoGateway.EXCLUSIVO, 300, 120), ADMIN_ALPES))
                .isInstanceOf(UsuarioSinPermisoException.class);
        assertThatThrownBy(() -> arcoService.consultarActivos(deAndes.getId(), ADMIN_ALPES))
                .isInstanceOf(UsuarioSinPermisoException.class);
    }

    @Test
    void elGatewayQuedaPersistidoConSuTipoSuPosicionYSuProceso() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);

        GatewayRespuestaDto creado = crearGateway(proceso, TipoGateway.EXCLUSIVO, EDITOR_ALPES);

        Gateway persistido = gatewayRepository.findById(creado.getId()).orElseThrow();
        assertThat(persistido.getProceso().getId()).isEqualTo(proceso.getId());
        assertThat(persistido.getTipo()).isEqualTo(TipoGateway.EXCLUSIVO);
        assertThat(persistido.getPosicionX()).isEqualTo(300);
        assertThat(persistido.getPosicionY()).isEqualTo(120);
        assertThat(persistido.isActivo()).isTrue();
        assertThat(creado.getSimbolo()).isEqualTo("X");
        assertThat(creado.getAdvertencias()).anyMatch(texto -> texto.contains("necesita al menos dos"));
    }

    @Test
    void unGatewayExclusivoExigeCondicionEnCadaSalida() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 500, ADMIN_ALPES);
        GatewayRespuestaDto gateway = crearGateway(proceso, TipoGateway.EXCLUSIVO, ADMIN_ALPES);

        assertThatThrownBy(() -> salirDelGateway(proceso, gateway.getId(), aprobar.getId(), null, ADMIN_ALPES))
                .isInstanceOf(CondicionArcoNoValidaException.class);

        ArcoRespuestaDto creado = salirDelGateway(proceso, gateway.getId(), aprobar.getId(), CONDICION_APROBADA,
                ADMIN_ALPES);

        assertThat(arcoRepository.findById(creado.getId()).orElseThrow().getCondicion())
                .isEqualTo(CONDICION_APROBADA);
    }

    @Test
    void cambiarElTipoAParaleloBorraLasCondicionesDeLasSalidas() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 500, ADMIN_ALPES);
        ActividadRespuestaDto rechazar = crearActividad(proceso, RECHAZAR, 500, ADMIN_ALPES);
        GatewayRespuestaDto gateway = crearGateway(proceso, TipoGateway.EXCLUSIVO, ADMIN_ALPES);
        ArcoRespuestaDto haciaAprobar = salirDelGateway(proceso, gateway.getId(), aprobar.getId(),
                CONDICION_APROBADA, ADMIN_ALPES);
        ArcoRespuestaDto haciaRechazar = salirDelGateway(proceso, gateway.getId(), rechazar.getId(),
                CONDICION_RECHAZADA, ADMIN_ALPES);

        GatewayRespuestaDto editado = gatewayService.editar(proceso.getId(), gateway.getId(),
                new EditarGatewayDto(TipoGateway.PARALELO, new LinkedHashMap<>()), EDITOR_ALPES);

        assertThat(editado.getTipo()).isEqualTo(TipoGateway.PARALELO);
        assertThat(editado.getSimbolo()).isEqualTo("+");
        assertThat(arcoRepository.findById(haciaAprobar.getId()).orElseThrow().getCondicion()).isNull();
        assertThat(arcoRepository.findById(haciaRechazar.getId()).orElseThrow().getCondicion()).isNull();
    }

    @Test
    void cambiarElTipoAExclusivoActualizaLasCondicionesDeLasSalidas() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 500, ADMIN_ALPES);
        GatewayRespuestaDto gateway = crearGateway(proceso, TipoGateway.PARALELO, ADMIN_ALPES);
        ArcoRespuestaDto saliente = salirDelGateway(proceso, gateway.getId(), aprobar.getId(), null, ADMIN_ALPES);

        GatewayRespuestaDto editado = gatewayService.editar(proceso.getId(), gateway.getId(),
                new EditarGatewayDto(TipoGateway.EXCLUSIVO, condiciones(saliente.getId(), CONDICION_APROBADA)),
                ADMIN_ALPES);

        assertThat(editado.getTipo()).isEqualTo(TipoGateway.EXCLUSIVO);
        assertThat(arcoRepository.findById(saliente.getId()).orElseThrow().getCondicion())
                .isEqualTo(CONDICION_APROBADA);
    }

    @Test
    void editarUnaActividadConservaSuIdentidadYSusArcos() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad(proceso, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 400, ADMIN_ALPES);
        ArcoRespuestaDto entrante = unirActividades(proceso, aprobar.getId(), revisar.getId(), ADMIN_ALPES);
        ArcoRespuestaDto saliente = unirActividades(proceso, revisar.getId(), aprobar.getId(), ADMIN_ALPES);

        ActividadRespuestaDto editada = actividadService.editar(proceso.getId(), revisar.getId(),
                new EditarActividadDto("Validar solicitud", TipoActividad.TAREA_SISTEMA,
                        laneInicial(proceso, ADMIN_ALPES)), EDITOR_ALPES);

        assertThat(editada.getId()).isEqualTo(revisar.getId());
        assertThat(arcoRepository.findById(entrante.getId()).orElseThrow().isActivo()).isTrue();
        assertThat(arcoRepository.findById(saliente.getId()).orElseThrow().isActivo()).isTrue();
        assertThat(arcoRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(proceso.getId())).hasSize(2);
    }

    @Test
    void cambiarDeLaneConservaLosArcosDeLaActividad() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad(proceso, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 400, ADMIN_ALPES);
        ArcoRespuestaDto arco = unirActividades(proceso, revisar.getId(), aprobar.getId(), ADMIN_ALPES);
        Proceso persistido = procesoRepository.findById(proceso.getId()).orElseThrow();
        Long laneNueva = laneRepository.save(new Lane(null, "Cartera", persistido.poolPropietario(), null, 2, true))
                .getId();

        actividadService.editar(proceso.getId(), revisar.getId(),
                new EditarActividadDto(REVISAR, TipoActividad.TAREA_USUARIO, laneNueva), ADMIN_ALPES);

        Arco conservado = arcoRepository.findById(arco.getId()).orElseThrow();
        assertThat(conservado.isActivo()).isTrue();
        assertThat(conservado.getOrigenId()).isEqualTo(revisar.getId());
        assertThat(conservado.getDestinoId()).isEqualTo(aprobar.getId());
    }

    @Test
    void eliminarUnaActividadDesactivaSusArcosSinBorrarlos() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad(proceso, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 400, ADMIN_ALPES);
        ActividadRespuestaDto rechazar = crearActividad(proceso, RECHAZAR, 700, ADMIN_ALPES);
        ArcoRespuestaDto entrante = unirActividades(proceso, aprobar.getId(), revisar.getId(), ADMIN_ALPES);
        ArcoRespuestaDto saliente = unirActividades(proceso, revisar.getId(), rechazar.getId(), ADMIN_ALPES);
        long filasAntes = arcoRepository.count();

        ActividadRespuestaDto eliminada = actividadService.eliminar(proceso.getId(), revisar.getId(), ADMIN_ALPES);

        assertThat(arcoRepository.count()).isEqualTo(filasAntes);
        assertThat(arcoRepository.findById(entrante.getId()).orElseThrow().isActivo()).isFalse();
        assertThat(arcoRepository.findById(saliente.getId()).orElseThrow().isActivo()).isFalse();
        assertThat(arcoRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(proceso.getId())).isEmpty();
        assertThat(eliminada.getArcosDesactivados()).isEqualTo(2);
        assertThat(eliminada.getAdvertencias()).containsExactlyInAnyOrder(
                "'" + APROBAR + "' quedó sin arcos de salida",
                "'" + RECHAZAR + "' quedó sin arcos de entrada");
    }

    @Test
    void elHistorialRegistraLosArcosYLosGatewaysDelProceso() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad(proceso, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 400, ADMIN_ALPES);
        GatewayRespuestaDto gateway = crearGateway(proceso, TipoGateway.EXCLUSIVO, ADMIN_ALPES);
        ArcoRespuestaDto arco = unirActividades(proceso, revisar.getId(), aprobar.getId(), ADMIN_ALPES);
        arcoService.editar(proceso.getId(), arco.getId(), new EditarArcoDto(TipoNodoFlujo.ACTIVIDAD,
                revisar.getId(), TipoNodoFlujo.ACTIVIDAD, aprobar.getId(), "solicitud completa", null),
                ADMIN_ALPES);
        arcoService.eliminar(proceso.getId(), arco.getId(), ADMIN_ALPES);

        List<String> historial = procesoService.consultarHistorial(proceso.getId(), ADMIN_ALPES).stream()
                .map(HistorialProcesoRespuestaDto::getCambiosRealizados)
                .toList();

        assertThat(historial).contains("gateway creado: EXCLUSIVO #" + gateway.getId() + " en (300, 120)");
        assertThat(historial).contains("arco creado: '" + REVISAR + "' -> '" + APROBAR + "'");
        assertThat(historial).contains("arco '" + REVISAR + "' -> '" + APROBAR
                + "': etiqueta: '' -> 'solicitud completa'");
        assertThat(historial).contains("arco eliminado: '" + REVISAR + "' -> '" + APROBAR + "'");
    }

    @Test
    void unProcesoEliminadoSeConsultaPeroNoAdmiteArcosNiGateways() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad(proceso, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 400, ADMIN_ALPES);
        unirActividades(proceso, revisar.getId(), aprobar.getId(), ADMIN_ALPES);
        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);

        assertThat(arcoService.consultarActivos(proceso.getId(), LECTOR_ALPES)).hasSize(1);
        assertThatThrownBy(() -> unirActividades(proceso, aprobar.getId(), revisar.getId(), ADMIN_ALPES))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> crearGateway(proceso, TipoGateway.PARALELO, ADMIN_ALPES))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void elDetalleDibujaActividadesGatewaysYArcos() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad(proceso, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 500, ADMIN_ALPES);
        GatewayRespuestaDto gateway = crearGateway(proceso, TipoGateway.EXCLUSIVO, ADMIN_ALPES);
        arcoService.crear(proceso.getId(), new CrearArcoDto(TipoNodoFlujo.ACTIVIDAD, revisar.getId(),
                TipoNodoFlujo.GATEWAY, gateway.getId(), "solicitud completa", null), ADMIN_ALPES);
        salirDelGateway(proceso, gateway.getId(), aprobar.getId(), CONDICION_APROBADA, ADMIN_ALPES);
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(get("/procesos/" + proceso.getId()).session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"nodo-gateway\"")))
                .andExpect(content().string(containsString("marker-end=\"url(#punta-de-arco)\"")))
                .andExpect(content().string(containsString("solicitud completa")))
                .andExpect(content().string(containsString(CONDICION_APROBADA)))
                .andExpect(content().string(containsString("Gateway EXCLUSIVO #" + gateway.getId())));
    }

    @Test
    void elLectorVeElFlujoPeroNoLoModifica() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad(proceso, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 400, ADMIN_ALPES);
        unirActividades(proceso, revisar.getId(), aprobar.getId(), ADMIN_ALPES);

        assertThat(arcoService.consultarActivos(proceso.getId(), LECTOR_ALPES)).hasSize(1);
        assertThatThrownBy(() -> unirActividades(proceso, aprobar.getId(), revisar.getId(), LECTOR_ALPES))
                .isInstanceOf(UsuarioSinPermisoException.class);
        assertThatThrownBy(() -> crearGateway(proceso, TipoGateway.PARALELO, LECTOR_ALPES))
                .isInstanceOf(UsuarioSinPermisoException.class);
    }

    @Test
    void elEditorNoEliminaArcosPeroElAdministradorSi() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad(proceso, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 400, ADMIN_ALPES);
        ArcoRespuestaDto arco = unirActividades(proceso, revisar.getId(), aprobar.getId(), EDITOR_ALPES);

        assertThatThrownBy(() -> arcoService.eliminar(proceso.getId(), arco.getId(), EDITOR_ALPES))
                .isInstanceOf(UsuarioSinPermisoException.class);

        arcoService.eliminar(proceso.getId(), arco.getId(), ADMIN_ALPES);

        assertThat(arcoRepository.findById(arco.getId()).orElseThrow().isActivo()).isFalse();
    }

    private ProcesoRespuestaDto publicar(ProcesoRespuestaDto proceso, String autor) {
        return procesoService.editar(proceso.getId(),
                new EditarProcesoDto("Ventas", "Proceso comercial", "Comercial", EstadoProceso.PUBLICADO), autor);
    }

    @Test
    void enBorradorSeToleraUnGatewaySinSalidasYConUnaSolaSalida() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 500, ADMIN_ALPES);
        GatewayRespuestaDto gateway = crearGateway(proceso, TipoGateway.EXCLUSIVO, ADMIN_ALPES);

        assertThat(gatewayRepository.findById(gateway.getId()).orElseThrow().isActivo()).isTrue();

        salirDelGateway(proceso, gateway.getId(), aprobar.getId(), CONDICION_APROBADA, ADMIN_ALPES);

        assertThat(arcoRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(proceso.getId())).hasSize(1);
        assertThat(procesoService.obtener(proceso.getId(), ADMIN_ALPES).getEstado())
                .isEqualTo(EstadoProceso.BORRADOR);
    }

    @Test
    void unGatewayConUnaSolaSalidaImpidePublicarElProceso() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 500, ADMIN_ALPES);
        GatewayRespuestaDto gateway = crearGateway(proceso, TipoGateway.EXCLUSIVO, ADMIN_ALPES);
        salirDelGateway(proceso, gateway.getId(), aprobar.getId(), CONDICION_APROBADA, ADMIN_ALPES);

        assertThatThrownBy(() -> publicar(proceso, ADMIN_ALPES))
                .isInstanceOf(ModeloDeProcesoNoValidoException.class)
                .hasMessageContaining("necesita al menos dos");

        assertThat(procesoService.obtener(proceso.getId(), ADMIN_ALPES).getEstado())
                .isEqualTo(EstadoProceso.BORRADOR);
    }

    @Test
    void unGatewayConDosSalidasCondicionadasPermitePublicarElProceso() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 500, ADMIN_ALPES);
        ActividadRespuestaDto rechazar = crearActividad(proceso, RECHAZAR, 700, ADMIN_ALPES);
        GatewayRespuestaDto gateway = crearGateway(proceso, TipoGateway.EXCLUSIVO, ADMIN_ALPES);
        salirDelGateway(proceso, gateway.getId(), aprobar.getId(), CONDICION_APROBADA, ADMIN_ALPES);
        salirDelGateway(proceso, gateway.getId(), rechazar.getId(), CONDICION_RECHAZADA, ADMIN_ALPES);

        assertThat(publicar(proceso, ADMIN_ALPES).getEstado()).isEqualTo(EstadoProceso.PUBLICADO);
    }

    @Test
    void unProcesoSinGatewaysSePublicaSinRestricciones() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad(proceso, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 400, ADMIN_ALPES);
        unirActividades(proceso, revisar.getId(), aprobar.getId(), ADMIN_ALPES);

        assertThat(publicar(proceso, ADMIN_ALPES).getEstado()).isEqualTo(EstadoProceso.PUBLICADO);
    }

    @Test
    void unGatewayParaleloConDosSalidasSinCondicionSePublica() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 500, ADMIN_ALPES);
        ActividadRespuestaDto rechazar = crearActividad(proceso, RECHAZAR, 700, ADMIN_ALPES);
        GatewayRespuestaDto gateway = crearGateway(proceso, TipoGateway.PARALELO, ADMIN_ALPES);
        salirDelGateway(proceso, gateway.getId(), aprobar.getId(), null, ADMIN_ALPES);
        salirDelGateway(proceso, gateway.getId(), rechazar.getId(), null, ADMIN_ALPES);

        assertThat(publicar(proceso, ADMIN_ALPES).getEstado()).isEqualTo(EstadoProceso.PUBLICADO);
    }

    @Test
    void elDetalleMuestraLasAdvertenciasTrasEliminarUnaActividadConectada() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto revisar = crearActividad(proceso, REVISAR, 100, ADMIN_ALPES);
        ActividadRespuestaDto aprobar = crearActividad(proceso, APROBAR, 400, ADMIN_ALPES);
        unirActividades(proceso, aprobar.getId(), revisar.getId(), ADMIN_ALPES);
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(post("/procesos/" + proceso.getId() + "/actividades/" + revisar.getId() + "/eliminar")
                .session(sesion).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/procesos/" + proceso.getId()))
                .andExpect(flash().attribute("advertencias",
                        List.of("'" + APROBAR + "' quedó sin arcos de salida")));
    }
}
