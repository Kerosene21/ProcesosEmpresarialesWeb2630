package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Actividad;
import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.EditarActividadDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.exception.LaneNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.NombreActividadDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.LaneRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.service.ActividadService;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ActividadesIntegracionTest {

    private static final String ADMIN_ALPES = "admin@alpes-actividades.com";
    private static final String EDITOR_ALPES = "editor@alpes-actividades.com";
    private static final String LECTOR_ALPES = "lector@alpes-actividades.com";
    private static final String ADMIN_ANDES = "admin@andes-actividades.com";
    private static final String PASSWORD = "Clave-Actividades-2026";
    private static final String ACTIVIDAD_EN_EL_DIAGRAMA =
            "<span class=\"actividad-nombre\">Revisar solicitud</span>";
    private static final String INICIO_DE_LANE = "<div class=\"lane\">";

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
    private ActividadRepository actividadRepository;

    @Autowired
    private LaneRepository laneRepository;

    @Autowired
    private ProcesoRepository procesoRepository;

    private void registrarAlpes() {
        empresaService.registrar(new RegistroEmpresaDto("Alpes Actividades", "905000001-1", ADMIN_ALPES, PASSWORD));
        usuarioService.crear(new CrearUsuarioDto(EDITOR_ALPES, PASSWORD, RolUsuario.EDITOR), ADMIN_ALPES);
        usuarioService.crear(new CrearUsuarioDto(LECTOR_ALPES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ALPES);
    }

    private void registrarAndes() {
        empresaService.registrar(new RegistroEmpresaDto("Andes Actividades", "905000002-2", ADMIN_ANDES, PASSWORD));
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

    private Long agregarLane(ProcesoRespuestaDto proceso, String nombre) {
        Proceso persistido = procesoRepository.findById(proceso.getId()).orElseThrow();
        Pool propietario = persistido.poolPropietario();
        int orden = laneRepository.findByPoolIdAndActivoTrueOrderByOrdenAscIdAsc(propietario.getId()).size() + 1;
        return laneRepository.save(new Lane(null, nombre, propietario, null, orden, true)).getId();
    }

    private ActividadRespuestaDto crearActividad(ProcesoRespuestaDto proceso, String nombre, Long laneId,
            String autor) {
        return actividadService.crear(proceso.getId(),
                new CrearActividadDto(nombre, TipoActividad.TAREA_USUARIO, laneId, 120, 40), autor);
    }

    private String bloqueDeLaLane(String html, String nombreLane) {
        return Arrays.stream(html.split(INICIO_DE_LANE))
                .filter(bloque -> bloque.contains(">" + nombreLane + "</span>"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("El diagrama no dibuja la lane " + nombreLane));
    }

    @Test
    void cadaProcesoNaceConUnaLaneInicialDentroDeSuPool() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);

        List<LaneRespuestaDto> lanes = actividadService.lanesDelProceso(proceso.getId(), ADMIN_ALPES);

        assertThat(lanes).hasSize(1);
        assertThat(lanes.get(0).getNombre()).isEqualTo("General");
        Proceso persistido = procesoRepository.findById(proceso.getId()).orElseThrow();
        assertThat(laneRepository.findByIdAndPoolIdAndActivoTrue(lanes.get(0).getId(),
                persistido.poolPropietario().getId())).isPresent();
    }

    @Test
    void laActividadQuedaEnlazadaASuProcesoYASuLaneDentroDelPool() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        Long laneId = laneInicial(proceso, ADMIN_ALPES);

        ActividadRespuestaDto creada = crearActividad(proceso, "Revisar solicitud", laneId, EDITOR_ALPES);

        Actividad persistida = actividadRepository.findById(creada.getId()).orElseThrow();
        Proceso procesoPersistido = procesoRepository.findById(proceso.getId()).orElseThrow();
        assertThat(persistida.getProceso().getId()).isEqualTo(proceso.getId());
        assertThat(persistida.getLane().getId()).isEqualTo(laneId);
        assertThat(persistida.getLane().getPool().getId())
                .isEqualTo(procesoPersistido.poolPropietario().getId());
        assertThat(persistida.getPosicionX()).isEqualTo(120);
        assertThat(persistida.getPosicionY()).isEqualTo(40);
        assertThat(persistida.isActivo()).isTrue();
    }

    @Test
    void elNombreDeLaActividadNoSeRepiteDentroDelMismoProceso() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        Long laneId = laneInicial(proceso, ADMIN_ALPES);
        crearActividad(proceso, "Revisar solicitud", laneId, ADMIN_ALPES);

        assertThatThrownBy(() -> crearActividad(proceso, "revisar SOLICITUD", laneId, ADMIN_ALPES))
                .isInstanceOf(NombreActividadDuplicadoException.class);
    }

    @Test
    void laBaseDeDatosTambienImpideElNombreRepetidoDentroDelProceso() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        Long laneId = laneInicial(proceso, ADMIN_ALPES);
        crearActividad(proceso, "Revisar solicitud", laneId, ADMIN_ALPES);
        Proceso persistido = procesoRepository.findById(proceso.getId()).orElseThrow();
        Lane lane = laneRepository.findById(laneId).orElseThrow();
        Actividad duplicada = new Actividad(null, "Revisar solicitud", TipoActividad.TAREA_MANUAL, persistido, lane,
                200, 60, true);

        assertThatThrownBy(() -> actividadRepository.saveAndFlush(duplicada))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elMismoNombreDeActividadSiEsValidoEnProcesosDistintos() {
        registrarAlpes();
        ProcesoRespuestaDto ventas = crearProceso("Ventas", ADMIN_ALPES);
        ProcesoRespuestaDto compras = crearProceso("Compras", ADMIN_ALPES);

        crearActividad(ventas, "Revisar solicitud", laneInicial(ventas, ADMIN_ALPES), ADMIN_ALPES);
        ActividadRespuestaDto enCompras = crearActividad(compras, "Revisar solicitud",
                laneInicial(compras, ADMIN_ALPES), ADMIN_ALPES);

        assertThat(enCompras.getProcesoId()).isEqualTo(compras.getId());
        assertThat(actividadRepository.activasDelProceso(ventas.getId())).hasSize(1);
        assertThat(actividadRepository.activasDelProceso(compras.getId())).hasSize(1);
    }

    @Test
    void cambiarDeLaneMueveLaActividadDeBandaSinPerderSuIdentidad() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        Long laneOrigen = laneInicial(proceso, ADMIN_ALPES);
        Long laneDestino = agregarLane(proceso, "Cartera");
        ActividadRespuestaDto creada = crearActividad(proceso, "Revisar solicitud", laneOrigen, EDITOR_ALPES);

        ActividadRespuestaDto editada = actividadService.editar(proceso.getId(), creada.getId(),
                new EditarActividadDto("Revisar solicitud", TipoActividad.TAREA_USUARIO, laneDestino), EDITOR_ALPES);

        assertThat(editada.getId()).isEqualTo(creada.getId());
        assertThat(editada.getLaneId()).isEqualTo(laneDestino);
        assertThat(actividadRepository.findById(creada.getId()).orElseThrow().getLane().getNombre())
                .isEqualTo("Cartera");
    }

    @Test
    void unaLaneDeOtroProcesoNoSirveParaUbicarLaActividad() {
        registrarAlpes();
        ProcesoRespuestaDto ventas = crearProceso("Ventas", ADMIN_ALPES);
        ProcesoRespuestaDto compras = crearProceso("Compras", ADMIN_ALPES);
        Long laneDeCompras = laneInicial(compras, ADMIN_ALPES);

        assertThatThrownBy(() -> crearActividad(ventas, "Revisar solicitud", laneDeCompras, ADMIN_ALPES))
                .isInstanceOf(LaneNoValidaException.class);
    }

    @Test
    void laEliminacionConservaLaFilaYLaMarcaComoInactiva() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto creada = crearActividad(proceso, "Revisar solicitud",
                laneInicial(proceso, ADMIN_ALPES), ADMIN_ALPES);
        long actividadesAntes = actividadRepository.count();

        actividadService.eliminar(proceso.getId(), creada.getId(), ADMIN_ALPES);

        assertThat(actividadRepository.count()).isEqualTo(actividadesAntes);
        Actividad persistida = actividadRepository.findById(creada.getId()).orElseThrow();
        assertThat(persistida.isActivo()).isFalse();
        assertThat(persistida.getNombre()).isEqualTo("Revisar solicitud");
        assertThat(actividadRepository.activasDelProceso(proceso.getId())).isEmpty();
    }

    @Test
    void laSegundaEliminacionDeLaMismaActividadSeRechaza() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto creada = crearActividad(proceso, "Revisar solicitud",
                laneInicial(proceso, ADMIN_ALPES), ADMIN_ALPES);
        actividadService.eliminar(proceso.getId(), creada.getId(), ADMIN_ALPES);

        assertThatThrownBy(() -> actividadService.eliminar(proceso.getId(), creada.getId(), ADMIN_ALPES))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("La actividad ya fue eliminada");
    }

    @Test
    void cadaOperacionSobreActividadesDejaSuHuellaEnElHistorialDelProceso() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        Long laneOrigen = laneInicial(proceso, ADMIN_ALPES);
        Long laneDestino = agregarLane(proceso, "Cartera");
        ActividadRespuestaDto creada = crearActividad(proceso, "Revisar solicitud", laneOrigen, EDITOR_ALPES);
        actividadService.editar(proceso.getId(), creada.getId(),
                new EditarActividadDto("Validar solicitud", TipoActividad.TAREA_USUARIO, laneDestino), EDITOR_ALPES);
        actividadService.eliminar(proceso.getId(), creada.getId(), ADMIN_ALPES);

        List<HistorialProcesoRespuestaDto> historial = procesoService.consultarHistorial(proceso.getId(),
                LECTOR_ALPES);

        assertThat(historial).hasSize(3);
        assertThat(historial.get(0).getCambiosRealizados()).isEqualTo("actividad eliminada: 'Validar solicitud'");
        assertThat(historial.get(0).getUsuarioCorreo()).isEqualTo(ADMIN_ALPES);
        assertThat(historial.get(1).getCambiosRealizados())
                .isEqualTo("actividad 'Revisar solicitud': nombre: 'Revisar solicitud' -> 'Validar solicitud'; "
                        + "lane: 'General' -> 'Cartera'");
        assertThat(historial.get(2).getCambiosRealizados()).isEqualTo("actividad creada: 'Revisar solicitud'");
        assertThat(historial.get(2).getUsuarioCorreo()).isEqualTo(EDITOR_ALPES);
    }

    @Test
    void unaEdicionSinCambiosNoAgregaEntradasAlHistorial() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        Long laneId = laneInicial(proceso, ADMIN_ALPES);
        ActividadRespuestaDto creada = crearActividad(proceso, "Revisar solicitud", laneId, EDITOR_ALPES);

        actividadService.editar(proceso.getId(), creada.getId(),
                new EditarActividadDto("Revisar solicitud", TipoActividad.TAREA_USUARIO, laneId), EDITOR_ALPES);

        assertThat(procesoService.consultarHistorial(proceso.getId(), ADMIN_ALPES)).hasSize(1);
    }

    @Test
    void unProcesoEliminadoNoAdmiteCambiosEnSusActividades() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        Long laneId = laneInicial(proceso, ADMIN_ALPES);
        ActividadRespuestaDto creada = crearActividad(proceso, "Revisar solicitud", laneId, ADMIN_ALPES);
        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);

        assertThatThrownBy(() -> crearActividad(proceso, "Otra actividad", laneId, ADMIN_ALPES))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");
        assertThatThrownBy(() -> actividadService.editar(proceso.getId(), creada.getId(),
                new EditarActividadDto("Validar solicitud", TipoActividad.TAREA_SISTEMA, laneId), ADMIN_ALPES))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> actividadService.eliminar(proceso.getId(), creada.getId(), ADMIN_ALPES))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void lasActividadesDeUnProcesoEliminadoSiguenSiendoConsultables() {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        crearActividad(proceso, "Revisar solicitud", laneInicial(proceso, ADMIN_ALPES), ADMIN_ALPES);
        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);

        assertThat(actividadService.consultarActivas(proceso.getId(), LECTOR_ALPES)).hasSize(1);
    }

    @Test
    void unaEmpresaNoTocaLasActividadesDeOtraEmpresa() {
        registrarAlpes();
        registrarAndes();
        ProcesoRespuestaDto deAlpes = crearProceso("Ventas", ADMIN_ALPES);
        Long laneDeAlpes = laneInicial(deAlpes, ADMIN_ALPES);
        ActividadRespuestaDto creada = crearActividad(deAlpes, "Revisar solicitud", laneDeAlpes, ADMIN_ALPES);

        assertThatThrownBy(() -> crearActividad(deAlpes, "Otra actividad", laneDeAlpes, ADMIN_ANDES))
                .isInstanceOf(UsuarioSinPermisoException.class);
        assertThatThrownBy(() -> actividadService.editar(deAlpes.getId(), creada.getId(),
                new EditarActividadDto("Validar solicitud", TipoActividad.TAREA_SISTEMA, laneDeAlpes), ADMIN_ANDES))
                .isInstanceOf(UsuarioSinPermisoException.class);
        assertThatThrownBy(() -> actividadService.eliminar(deAlpes.getId(), creada.getId(), ADMIN_ANDES))
                .isInstanceOf(UsuarioSinPermisoException.class);
        assertThatThrownBy(() -> actividadService.consultarActivas(deAlpes.getId(), ADMIN_ANDES))
                .isInstanceOf(UsuarioSinPermisoException.class);
    }

    @Test
    void unaEmpresaNoPuedeUsarUnaLaneDelPoolDeOtraEmpresa() {
        registrarAlpes();
        registrarAndes();
        ProcesoRespuestaDto deAlpes = crearProceso("Ventas", ADMIN_ALPES);
        ProcesoRespuestaDto deAndes = crearProceso("Ventas", ADMIN_ANDES);
        Long laneDeAlpes = laneInicial(deAlpes, ADMIN_ALPES);

        assertThatThrownBy(() -> crearActividad(deAndes, "Revisar solicitud", laneDeAlpes, ADMIN_ANDES))
                .isInstanceOf(LaneNoValidaException.class);
    }

    @Test
    void elDiagramaDelDetalleMuestraLaActividadCreadaYDejaDeMostrarlaAlEliminarla() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        Long laneId = laneInicial(proceso, ADMIN_ALPES);
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);
        String detalle = "/procesos/" + proceso.getId();

        mockMvc.perform(post(detalle + "/actividades").session(sesion).with(csrf())
                .param("nombre", "Revisar solicitud")
                .param("tipo", "TAREA_USUARIO")
                .param("laneId", String.valueOf(laneId))
                .param("posicionX", "120")
                .param("posicionY", "40"))
                .andExpect(redirectedUrl(detalle));

        mockMvc.perform(get(detalle).session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/proceso"))
                .andExpect(content().string(containsString(ACTIVIDAD_EN_EL_DIAGRAMA)))
                .andExpect(content().string(containsString("General")));

        Long actividadId = actividadRepository.activasDelProceso(proceso.getId()).get(0).getId();
        mockMvc.perform(get(detalle + "/actividades/" + actividadId + "/eliminar").session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("actividades/confirmareliminacion"));
        assertThat(actividadRepository.findById(actividadId).orElseThrow().isActivo()).isTrue();

        mockMvc.perform(post(detalle + "/actividades/" + actividadId + "/eliminar").session(sesion).with(csrf()))
                .andExpect(redirectedUrl(detalle));

        mockMvc.perform(get(detalle).session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(ACTIVIDAD_EN_EL_DIAGRAMA))))
                .andExpect(content().string(containsString("El diagrama todavía no tiene actividades")))
                .andExpect(content().string(containsString("La actividad Revisar solicitud quedo eliminada")));
    }

    @Test
    void elCambioDeLaneSeVeEnElDiagramaDelDetalle() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        Long laneOrigen = laneInicial(proceso, ADMIN_ALPES);
        Long laneDestino = agregarLane(proceso, "Cartera");
        ActividadRespuestaDto creada = crearActividad(proceso, "Revisar solicitud", laneOrigen, EDITOR_ALPES);
        MockHttpSession sesion = iniciarSesion(EDITOR_ALPES);
        String detalle = "/procesos/" + proceso.getId();

        mockMvc.perform(post(detalle + "/actividades/" + creada.getId()).session(sesion).with(csrf())
                .param("nombre", "Revisar solicitud")
                .param("tipo", "TAREA_USUARIO")
                .param("laneId", String.valueOf(laneDestino)))
                .andExpect(redirectedUrl(detalle));

        assertThat(actividadService.consultarActivas(proceso.getId(), EDITOR_ALPES).get(0).getLaneNombre())
                .isEqualTo("Cartera");
        String diagrama = mockMvc.perform(get(detalle).session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(bloqueDeLaLane(diagrama, "Cartera")).contains(ACTIVIDAD_EN_EL_DIAGRAMA);
        assertThat(bloqueDeLaLane(diagrama, "General")).doesNotContain(ACTIVIDAD_EN_EL_DIAGRAMA);
    }

    @Test
    void elUsuarioDeSoloLecturaVeElDiagramaPeroNoLoModificaDesdeLaWeb() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        Long laneId = laneInicial(proceso, ADMIN_ALPES);
        crearActividad(proceso, "Revisar solicitud", laneId, ADMIN_ALPES);
        MockHttpSession sesion = iniciarSesion(LECTOR_ALPES);
        String detalle = "/procesos/" + proceso.getId();

        mockMvc.perform(get(detalle).session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Revisar solicitud")));
        mockMvc.perform(get(detalle + "/actividades/nueva").session(sesion))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(detalle + "/actividades").session(sesion).with(csrf())
                .param("nombre", "Otra actividad")
                .param("tipo", "TAREA_USUARIO")
                .param("laneId", String.valueOf(laneId))
                .param("posicionX", "10")
                .param("posicionY", "10"))
                .andExpect(status().isForbidden());

        assertThat(actividadRepository.activasDelProceso(proceso.getId())).hasSize(1);
    }

    @Test
    void elEditorNoLlegaALaConfirmacionDeEliminacionDesdeLaWeb() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        ActividadRespuestaDto creada = crearActividad(proceso, "Revisar solicitud",
                laneInicial(proceso, ADMIN_ALPES), ADMIN_ALPES);
        MockHttpSession sesion = iniciarSesion(EDITOR_ALPES);
        String ruta = "/procesos/" + proceso.getId() + "/actividades/" + creada.getId() + "/eliminar";

        mockMvc.perform(get(ruta).session(sesion)).andExpect(status().isForbidden());
        mockMvc.perform(post(ruta).session(sesion).with(csrf())).andExpect(status().isForbidden());

        assertThat(actividadRepository.findById(creada.getId()).orElseThrow().isActivo()).isTrue();
    }
}
