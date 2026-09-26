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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import co.edu.javeriana.procesosempresariales.domain.AccionRolProceso;
import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.RolProceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.EditarLaneDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialRolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.LaneRepository;
import co.edu.javeriana.procesosempresariales.repository.RolProcesoRepository;
import co.edu.javeriana.procesosempresariales.service.ActividadService;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.LaneService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;
import co.edu.javeriana.procesosempresariales.service.RolProcesoService;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RolesProcesoIntegracionTest {

    private static final String ADMIN_ALPES = "admin@alpes-roles.com";
    private static final String EDITOR_ALPES = "editor@alpes-roles.com";
    private static final String LECTOR_ALPES = "lector@alpes-roles.com";
    private static final String ADMIN_ANDES = "admin@andes-roles.com";
    private static final String PASSWORD = "Clave-Roles-2026";
    private static final String RUTA_ROLES = "/api/roles-proceso";
    private static final String ANALISTA = "Analista de credito";
    private static final String DESCRIPCION = "Evalua el riesgo de cada solicitud";

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
    private RolProcesoService rolProcesoService;

    @Autowired
    private LaneService laneService;

    @Autowired
    private RolProcesoRepository rolProcesoRepository;

    @Autowired
    private LaneRepository laneRepository;

    private void registrarEmpresas() {
        empresaService.registrar(new RegistroEmpresaDto("Alpes Roles", "905300001-1", ADMIN_ALPES, PASSWORD));
        usuarioService.crear(new CrearUsuarioDto(EDITOR_ALPES, PASSWORD, RolUsuario.EDITOR), ADMIN_ALPES);
        usuarioService.crear(new CrearUsuarioDto(LECTOR_ALPES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ALPES);
        empresaService.registrar(new RegistroEmpresaDto("Andes Roles", "905300002-2", ADMIN_ANDES, PASSWORD));
    }

    private MockHttpSession iniciarSesion(String correo) throws Exception {
        MvcResult resultado = mockMvc.perform(formLogin().user(correo).password(PASSWORD))
                .andExpect(authenticated().withUsername(correo))
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private String json(String nombre, String descripcion) {
        return "{\"nombre\":\"" + nombre + "\",\"descripcion\":\"" + descripcion + "\"}";
    }

    private RolProcesoRespuestaDto crearRol(String nombre, String autor) {
        return rolProcesoService.crear(new CrearRolProcesoDto(nombre, DESCRIPCION), autor);
    }

    private String rutaRol(Long rolId) {
        return RUTA_ROLES + "/" + rolId;
    }

    private ProcesoRespuestaDto crearProceso(String nombre, String autor) {
        return procesoService.crear(new CrearProcesoDto(nombre, "Proceso de " + nombre, "Finanzas"), autor);
    }

    private Long laneGeneral(ProcesoRespuestaDto proceso, String autor) {
        return actividadService.lanesDelProceso(proceso.getId(), autor).get(0).getId();
    }

    private Long usarEnLaneGeneral(ProcesoRespuestaDto proceso, Long rolId, String autor) {
        Long laneId = laneGeneral(proceso, autor);
        laneService.editar(proceso.getId(), proceso.getPoolId(), laneId, new EditarLaneDto(rolId, null), autor);
        return laneId;
    }

    private RolProceso persistido(Long rolId) {
        return rolProcesoRepository.findById(rolId).orElseThrow();
    }

    @Test
    void elAdministradorCreaUnRolQueQuedaPersistidoYActivoEnSuEmpresa() throws Exception {
        registrarEmpresas();

        MvcResult resultado = mockMvc.perform(post(RUTA_ROLES).session(iniciarSesion(ADMIN_ALPES)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(" " + ANALISTA + " ", DESCRIPCION)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value(ANALISTA))
                .andExpect(jsonPath("$.activo").value(true))
                .andReturn();

        Long rolId = ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
        assertThat(resultado.getResponse().getHeader("Location")).endsWith(rutaRol(rolId));
        RolProceso rol = persistido(rolId);
        assertThat(rol.getNombre()).isEqualTo(ANALISTA);
        assertThat(rol.getDescripcion()).isEqualTo(DESCRIPCION);
        assertThat(rol.isActivo()).isTrue();
        assertThat(rol.getEmpresa().getNit()).isEqualTo("905300001-1");
        List<HistorialRolProcesoRespuestaDto> historial = rolProcesoService.consultarHistorial(rolId, ADMIN_ALPES);
        assertThat(historial).extracting(HistorialRolProcesoRespuestaDto::getAccion)
                .containsExactly(AccionRolProceso.CREACION);
        assertThat(historial.get(0).getUsuarioCorreo()).isEqualTo(ADMIN_ALPES);
    }

    @Test
    void elEditorYElLectorNoCreanRoles() throws Exception {
        registrarEmpresas();
        long rolesAntes = rolProcesoRepository.count();

        mockMvc.perform(post(RUTA_ROLES).session(iniciarSesion(EDITOR_ALPES)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(ANALISTA, DESCRIPCION)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
        mockMvc.perform(post(RUTA_ROLES).session(iniciarSesion(LECTOR_ALPES)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(ANALISTA, DESCRIPCION)))
                .andExpect(status().isForbidden());

        assertThat(rolProcesoRepository.count()).isEqualTo(rolesAntes);
    }

    @Test
    void unRolSinNombreOSinDescripcionSeRechaza() throws Exception {
        registrarEmpresas();
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(post(RUTA_ROLES).session(sesion).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json("   ", DESCRIPCION)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(RUTA_ROLES).session(sesion).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(ANALISTA, " ")))
                .andExpect(status().isBadRequest());

        assertThat(rolProcesoRepository.count()).isZero();
    }

    @Test
    void elNombreNoSeRepiteEnLaMismaEmpresaAunqueCambienLasMayusculas() throws Exception {
        registrarEmpresas();
        crearRol(ANALISTA, ADMIN_ALPES);

        mockMvc.perform(post(RUTA_ROLES).session(iniciarSesion(ADMIN_ALPES)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json("ANALISTA DE CREDITO", DESCRIPCION)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ROL_PROCESO_NOMBRE_DUPLICADO"));

        assertThat(rolProcesoRepository.count()).isEqualTo(1);
    }

    @Test
    void dosEmpresasPuedenUsarElMismoNombreDeRol() throws Exception {
        registrarEmpresas();
        RolProcesoRespuestaDto deAlpes = crearRol(ANALISTA, ADMIN_ALPES);

        mockMvc.perform(post(RUTA_ROLES).session(iniciarSesion(ADMIN_ANDES)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(ANALISTA, DESCRIPCION)))
                .andExpect(status().isCreated());

        assertThat(rolProcesoRepository.findAll()).extracting(rol -> rol.getEmpresa().getNit())
                .containsExactlyInAnyOrder("905300001-1", "905300002-2");
        assertThat(persistido(deAlpes.getId()).getEmpresa().getNit()).isEqualTo("905300001-1");
    }

    @Test
    void laBaseDeDatosImpideRepetirElNombreDentroDeLaEmpresa() {
        registrarEmpresas();
        RolProceso existente = persistido(crearRol(ANALISTA, ADMIN_ALPES).getId());
        RolProceso duplicado = new RolProceso(null, ANALISTA, DESCRIPCION, existente.getEmpresa(), true);

        assertThatThrownBy(() -> rolProcesoRepository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elEditorRenombraElRolYLaLaneMuestraElNombreNuevoSinCopiarlo() throws Exception {
        registrarEmpresas();
        RolProcesoRespuestaDto rol = crearRol(ANALISTA, ADMIN_ALPES);
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        Long laneId = usarEnLaneGeneral(proceso, rol.getId(), ADMIN_ALPES);
        ActividadRespuestaDto actividad = actividadService.crear(proceso.getId(),
                new CrearActividadDto("Evaluar riesgo", TipoActividad.TAREA_USUARIO, laneId, 100, 40), ADMIN_ALPES);
        assertThat(actividad.getLaneNombre()).isEqualTo(ANALISTA);

        mockMvc.perform(put(rutaRol(rol.getId())).session(iniciarSesion(EDITOR_ALPES)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json("Analista senior", DESCRIPCION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rol.getId()))
                .andExpect(jsonPath("$.nombre").value("Analista senior"));

        Lane lane = laneRepository.findById(laneId).orElseThrow();
        assertThat(lane.getRolProceso().getId()).isEqualTo(rol.getId());
        assertThat(lane.getNombre()).isEqualTo("General");
        assertThat(persistido(rol.getId()).getEmpresa().getNit()).isEqualTo("905300001-1");
        List<LaneRespuestaDto> lanes = actividadService.lanesDelProceso(proceso.getId(), LECTOR_ALPES);
        assertThat(lanes).extracting(LaneRespuestaDto::getNombre).containsExactly("Analista senior");
        assertThat(lanes).extracting(LaneRespuestaDto::getRolProcesoId).containsExactly(rol.getId());
        assertThat(actividadService.obtener(proceso.getId(), actividad.getId(), LECTOR_ALPES).getLaneNombre())
                .isEqualTo("Analista senior");
        assertThat(rolProcesoService.consultarHistorial(rol.getId(), ADMIN_ALPES).get(0).getCambiosRealizados())
                .isEqualTo("nombre: '" + ANALISTA + "' -> 'Analista senior'");
    }

    @Test
    void unaEdicionSinCambiosNoAgregaHistorial() throws Exception {
        registrarEmpresas();
        RolProcesoRespuestaDto rol = crearRol(ANALISTA, ADMIN_ALPES);

        mockMvc.perform(put(rutaRol(rol.getId())).session(iniciarSesion(ADMIN_ALPES)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(" " + ANALISTA, DESCRIPCION + " ")))
                .andExpect(status().isOk());

        assertThat(rolProcesoService.consultarHistorial(rol.getId(), ADMIN_ALPES)).hasSize(1);
    }

    @Test
    void elNuevoNombreNoPuedeRepetirOtroRolYElLectorNoEdita() throws Exception {
        registrarEmpresas();
        RolProcesoRespuestaDto rol = crearRol(ANALISTA, ADMIN_ALPES);
        crearRol("Supervisor", ADMIN_ALPES);

        mockMvc.perform(put(rutaRol(rol.getId())).session(iniciarSesion(ADMIN_ALPES)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json("supervisor", DESCRIPCION)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ROL_PROCESO_NOMBRE_DUPLICADO"));
        mockMvc.perform(put(rutaRol(rol.getId())).session(iniciarSesion(LECTOR_ALPES)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json("Otro", DESCRIPCION)))
                .andExpect(status().isForbidden());

        assertThat(persistido(rol.getId()).getNombre()).isEqualTo(ANALISTA);
    }

    @Test
    void otraEmpresaNoConsultaNiEditaNiEliminaElRol() throws Exception {
        registrarEmpresas();
        RolProcesoRespuestaDto rol = crearRol(ANALISTA, ADMIN_ALPES);
        MockHttpSession andes = iniciarSesion(ADMIN_ANDES);

        mockMvc.perform(get(rutaRol(rol.getId())).session(andes)).andExpect(status().isForbidden());
        mockMvc.perform(get(rutaRol(rol.getId()) + "/historial").session(andes)).andExpect(status().isForbidden());
        mockMvc.perform(put(rutaRol(rol.getId())).session(andes).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json("Robado", DESCRIPCION)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("El rol de proceso no pertenece a la empresa del usuario"));
        mockMvc.perform(delete(rutaRol(rol.getId())).session(andes).with(csrf())).andExpect(status().isForbidden());

        RolProceso intacto = persistido(rol.getId());
        assertThat(intacto.getNombre()).isEqualTo(ANALISTA);
        assertThat(intacto.isActivo()).isTrue();
    }

    @Test
    void otraEmpresaNoPuedeUsarElRolEnSusLanes() {
        registrarEmpresas();
        RolProcesoRespuestaDto rolAlpes = crearRol(ANALISTA, ADMIN_ALPES);
        ProcesoRespuestaDto procesoAndes = crearProceso("Ventas", ADMIN_ANDES);
        Long laneAndes = laneGeneral(procesoAndes, ADMIN_ANDES);

        assertThatThrownBy(() -> laneService.editar(procesoAndes.getId(), procesoAndes.getPoolId(), laneAndes,
                new EditarLaneDto(rolAlpes.getId(), null), ADMIN_ANDES))
                .isInstanceOf(UsuarioSinPermisoException.class);

        assertThat(laneRepository.findById(laneAndes).orElseThrow().getRolProceso()).isNull();
    }

    @Test
    void elAdministradorEliminaUnRolLibreDeFormaLogicaUnaSolaVez() throws Exception {
        registrarEmpresas();
        RolProcesoRespuestaDto rol = crearRol(ANALISTA, ADMIN_ALPES);
        long rolesAntes = rolProcesoRepository.count();
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(get(rutaRol(rol.getId()) + "/eliminacion").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enUso").value(false))
                .andExpect(jsonPath("$.puedeEliminar").value(true));
        mockMvc.perform(delete(rutaRol(rol.getId())).session(sesion).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(rolProcesoRepository.count()).isEqualTo(rolesAntes);
        assertThat(persistido(rol.getId()).isActivo()).isFalse();
        assertThat(rolProcesoService.consultarHistorial(rol.getId(), ADMIN_ALPES))
                .extracting(HistorialRolProcesoRespuestaDto::getAccion)
                .containsExactly(AccionRolProceso.ELIMINACION, AccionRolProceso.CREACION);
        mockMvc.perform(delete(rutaRol(rol.getId())).session(sesion).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("El rol de proceso ya fue eliminado"));
    }

    @Test
    void elEditorYElLectorNoEliminanRoles() throws Exception {
        registrarEmpresas();
        RolProcesoRespuestaDto rol = crearRol(ANALISTA, ADMIN_ALPES);

        mockMvc.perform(delete(rutaRol(rol.getId())).session(iniciarSesion(EDITOR_ALPES)).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(rutaRol(rol.getId())).session(iniciarSesion(LECTOR_ALPES)).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(persistido(rol.getId()).isActivo()).isTrue();
    }

    @Test
    void unRolUsadoPorLanesNoSeEliminaEIndicaLosProcesosDondeSeUsa() throws Exception {
        registrarEmpresas();
        RolProcesoRespuestaDto rol = crearRol(ANALISTA, ADMIN_ALPES);
        usarEnLaneGeneral(crearProceso("Ventas", ADMIN_ALPES), rol.getId(), ADMIN_ALPES);
        usarEnLaneGeneral(crearProceso("Compras", EDITOR_ALPES), rol.getId(), EDITOR_ALPES);
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);

        mockMvc.perform(get(rutaRol(rol.getId()) + "/eliminacion").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enUso").value(true))
                .andExpect(jsonPath("$.puedeEliminar").value(false))
                .andExpect(jsonPath("$.procesos[*].nombre", contains("Compras", "Ventas")));
        mockMvc.perform(delete(rutaRol(rol.getId())).session(sesion).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ROL_PROCESO_EN_USO"))
                .andExpect(jsonPath("$.procesos", contains("Compras", "Ventas")))
                .andExpect(jsonPath("$.mensaje").value("El rol de proceso '" + ANALISTA + "' no se puede eliminar"
                        + " porque lo usan lanes de los procesos: Compras, Ventas"));

        assertThat(persistido(rol.getId()).isActivo()).isTrue();
    }

    @Test
    void unRolUsadoSoloEnProcesosEliminadosSePuedeEliminar() throws Exception {
        registrarEmpresas();
        RolProcesoRespuestaDto rol = crearRol(ANALISTA, ADMIN_ALPES);
        ProcesoRespuestaDto proceso = crearProceso("Ventas", ADMIN_ALPES);
        Long laneId = usarEnLaneGeneral(proceso, rol.getId(), ADMIN_ALPES);
        procesoService.eliminar(proceso.getId(), ADMIN_ALPES);

        mockMvc.perform(delete(rutaRol(rol.getId())).session(iniciarSesion(ADMIN_ALPES)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(persistido(rol.getId()).isActivo()).isFalse();
        assertThat(laneRepository.findById(laneId).orElseThrow().nombreFuncional()).isEqualTo(ANALISTA);
    }

    @Test
    void laConsultaSoloMuestraLosRolesActivosDeLaEmpresaDelUsuario() throws Exception {
        registrarEmpresas();
        crearRol(ANALISTA, ADMIN_ALPES);
        crearRol("Supervisor", ADMIN_ALPES);
        RolProcesoRespuestaDto auditor = crearRol("Auditor", ADMIN_ALPES);
        rolProcesoService.eliminar(auditor.getId(), ADMIN_ALPES);
        crearRol("Tesorero", ADMIN_ANDES);

        mockMvc.perform(get(RUTA_ROLES).session(iniciarSesion(LECTOR_ALPES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nombre", contains(ANALISTA, "Supervisor")))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void laConsultaDeInactivosYTodosSirveParaAuditoria() throws Exception {
        registrarEmpresas();
        crearRol(ANALISTA, ADMIN_ALPES);
        RolProcesoRespuestaDto auditor = crearRol("Auditor", ADMIN_ALPES);
        rolProcesoService.eliminar(auditor.getId(), ADMIN_ALPES);
        MockHttpSession sesion = iniciarSesion(LECTOR_ALPES);

        mockMvc.perform(get(RUTA_ROLES).session(sesion).param("visibilidad", "INACTIVOS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nombre", contains("Auditor")))
                .andExpect(jsonPath("$.content[0].activo").value(false));
        mockMvc.perform(get(RUTA_ROLES).session(sesion).param("visibilidad", "TODOS"))
                .andExpect(jsonPath("$.content[*].nombre", contains(ANALISTA, "Auditor")));
    }

    @Test
    void laConsultaBuscaPorNombreParcialSinDistinguirMayusculas() throws Exception {
        registrarEmpresas();
        crearRol(ANALISTA, ADMIN_ALPES);
        crearRol("Analista de riesgo", ADMIN_ALPES);
        crearRol("Supervisor", ADMIN_ALPES);

        mockMvc.perform(get(RUTA_ROLES).session(iniciarSesion(EDITOR_ALPES)).param("q", " ANALISTA "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nombre", contains(ANALISTA, "Analista de riesgo")));
    }

    @Test
    void laConsultaSePaginaDeDiezEnDiez() throws Exception {
        registrarEmpresas();
        for (int numero = 10; numero < 22; numero++) {
            crearRol("Rol " + numero, ADMIN_ALPES);
        }
        MockHttpSession sesion = iniciarSesion(LECTOR_ALPES);

        mockMvc.perform(get(RUTA_ROLES).session(sesion))
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.content[0].nombre").value("Rol 10"))
                .andExpect(jsonPath("$.page.totalElements").value(12))
                .andExpect(jsonPath("$.page.totalPages").value(2));
        mockMvc.perform(get(RUTA_ROLES).session(sesion).param("page", "1"))
                .andExpect(jsonPath("$.content[*].nombre", contains("Rol 20", "Rol 21")))
                .andExpect(jsonPath("$.page.number").value(1));
    }

    @Test
    void laConsultaIndicaElUsoLosProcesosYSiCadaRolPuedeEliminarse() throws Exception {
        registrarEmpresas();
        RolProcesoRespuestaDto analista = crearRol(ANALISTA, ADMIN_ALPES);
        crearRol("Supervisor", ADMIN_ALPES);
        ProcesoRespuestaDto ventas = crearProceso("Ventas", ADMIN_ALPES);
        Long laneId = usarEnLaneGeneral(ventas, analista.getId(), ADMIN_ALPES);
        actividadService.crear(ventas.getId(),
                new CrearActividadDto("Evaluar riesgo", TipoActividad.TAREA_USUARIO, laneId, 100, 40), ADMIN_ALPES);

        mockMvc.perform(get(RUTA_ROLES).session(iniciarSesion(ADMIN_ALPES)))
                .andExpect(jsonPath("$.content[0].nombre").value(ANALISTA))
                .andExpect(jsonPath("$.content[0].enUso").value(true))
                .andExpect(jsonPath("$.content[0].puedeEliminar").value(false))
                .andExpect(jsonPath("$.content[0].procesos[0].id").value(ventas.getId()))
                .andExpect(jsonPath("$.content[0].procesos[0].nombre").value("Ventas"))
                .andExpect(jsonPath("$.content[0].procesos[0].lanes").value(1))
                .andExpect(jsonPath("$.content[0].procesos[0].actividadesActivas").value(1))
                .andExpect(jsonPath("$.content[1].nombre").value("Supervisor"))
                .andExpect(jsonPath("$.content[1].enUso").value(false))
                .andExpect(jsonPath("$.content[1].procesos", hasSize(0)))
                .andExpect(jsonPath("$.content[1].puedeEliminar").value(true));
        mockMvc.perform(get(RUTA_ROLES).session(iniciarSesion(LECTOR_ALPES)))
                .andExpect(jsonPath("$.content[1].puedeEliminar").value(false));
    }
}
