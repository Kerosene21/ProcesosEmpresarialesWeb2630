package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolProceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.LaneNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.HistorialRolProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.LaneRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.RolProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.RolProcesoSpecifications;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class LaneServiceTest {

    private static final String USERNAME = "editor@alpes.com";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final Long PROCESO_ID = 5L;
    private static final Long POOL_ID = 80L;
    private static final Long LANE_ID = 11L;
    private static final Long ROL_ID = 40L;
    private static final String ANALISTA = "Analista de credito";

    @Mock
    private LaneRepository laneRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProcesoRepository procesoRepository;

    @Mock
    private HistorialProcesoRepository historialProcesoRepository;

    @Mock
    private RolProcesoRepository rolProcesoRepository;

    @Mock
    private HistorialRolProcesoRepository historialRolProcesoRepository;

    private LaneService laneService;

    @BeforeEach
    void inicializar() {
        UsuarioService usuarios = new UsuarioService(usuarioRepository, new ModelMapper(),
                new BCryptPasswordEncoder());
        AccesoProcesoService acceso = new AccesoProcesoService(usuarios, procesoRepository);
        RolProcesoService roles = new RolProcesoService(rolProcesoRepository, historialRolProcesoRepository,
                new RolProcesoSpecifications(), acceso);
        laneService = new LaneService(laneRepository, acceso, roles,
                new HistorialProcesoService(historialProcesoRepository));
    }

    private Empresa empresa(Long id) {
        return new Empresa(id, "Empresa " + id, "900123456-" + id, "contacto" + id + "@alpes.com");
    }

    private void autenticar(RolUsuario rol) {
        when(usuarioRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(new Usuario(1L, USERNAME, HASH, rol, true, empresa(EMPRESA_PROPIA))));
    }

    private Pool pool() {
        return new Pool(POOL_ID, "Empresa 7", List.of());
    }

    private Proceso proceso(Long empresaId, boolean eliminado) {
        return new Proceso(PROCESO_ID, "Ventas", "Proceso comercial", "Comercial", EstadoProceso.BORRADOR,
                empresa(empresaId), pool(), eliminado);
    }

    private Proceso existeElProceso(Long empresaId, boolean eliminado) {
        Proceso proceso = proceso(empresaId, eliminado);
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.of(proceso));
        return proceso;
    }

    private Lane existeLaLane() {
        Lane lane = new Lane(LANE_ID, "General", pool());
        when(laneRepository.findByIdAndPoolId(LANE_ID, POOL_ID)).thenReturn(Optional.of(lane));
        return lane;
    }

    private RolProceso existeElRol(Long empresaId, boolean activo) {
        RolProceso rol = new RolProceso(ROL_ID, ANALISTA, "Evalua el riesgo", empresa(empresaId), activo);
        when(rolProcesoRepository.findById(ROL_ID)).thenReturn(Optional.of(rol));
        return rol;
    }

    private void noSeModificoNada() {
        verify(laneRepository, never()).save(any(Lane.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void laLaneDelProcesoSeBuscaDentroDeSuPool() {
        Lane lane = existeLaLane();

        assertThat(laneService.laneDelProceso(LANE_ID, proceso(EMPRESA_PROPIA, false))).isSameAs(lane);
    }

    @Test
    void unaLaneDeOtroPoolNoEsValida() {
        when(laneRepository.findByIdAndPoolId(LANE_ID, POOL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> laneService.laneDelProceso(LANE_ID, proceso(EMPRESA_PROPIA, false)))
                .isInstanceOf(LaneNoValidaException.class)
                .hasMessage(LaneService.LANE_INVALIDA);
    }

    @Test
    void lasLanesSinRolConservanSuPropioNombre() {
        when(laneRepository.findByPoolIdOrderByIdAsc(POOL_ID)).thenReturn(List.of(new Lane(LANE_ID, "General",
                pool())));

        List<LaneRespuestaDto> lanes = laneService.lanesDe(proceso(EMPRESA_PROPIA, false));

        assertThat(lanes).hasSize(1);
        assertThat(lanes.get(0).getId()).isEqualTo(LANE_ID);
        assertThat(lanes.get(0).getNombre()).isEqualTo("General");
        assertThat(lanes.get(0).getRolProcesoId()).isNull();
    }

    @Test
    void unaLaneConRolMuestraElNombreActualDelRol() {
        RolProceso rol = new RolProceso(ROL_ID, ANALISTA, "Evalua el riesgo", empresa(EMPRESA_PROPIA), true);
        when(laneRepository.findByPoolIdOrderByIdAsc(POOL_ID))
                .thenReturn(List.of(new Lane(LANE_ID, "General", pool(), rol)));

        LaneRespuestaDto lane = laneService.lanesDe(proceso(EMPRESA_PROPIA, false)).get(0);

        assertThat(lane.getNombre()).isEqualTo(ANALISTA);
        assertThat(lane.getRolProcesoId()).isEqualTo(ROL_ID);
    }

    @Test
    void elEditorAsignaUnRolDeSuEmpresaAUnaLane() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        Lane lane = existeLaLane();
        RolProceso rol = existeElRol(EMPRESA_PROPIA, true);

        LaneRespuestaDto asignada = laneService.asignarRolProceso(PROCESO_ID, LANE_ID, ROL_ID, USERNAME);

        assertThat(lane.getRolProceso()).isSameAs(rol);
        assertThat(lane.getNombre()).isEqualTo("General");
        assertThat(asignada.getNombre()).isEqualTo(ANALISTA);
        assertThat(asignada.getRolProcesoId()).isEqualTo(ROL_ID);
        verify(laneRepository).save(lane);
    }

    @Test
    void asignarUnRolQuedaEnElHistorialDelProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProceso(EMPRESA_PROPIA, false);
        existeLaLane();
        existeElRol(EMPRESA_PROPIA, true);

        laneService.asignarRolProceso(PROCESO_ID, LANE_ID, ROL_ID, USERNAME);

        ArgumentCaptor<HistorialProceso> capturado = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(capturado.capture());
        assertThat(capturado.getValue().getProceso()).isSameAs(proceso);
        assertThat(capturado.getValue().getCambiosRealizados())
                .isEqualTo("lane 'General': rol de proceso -> '" + ANALISTA + "'");
    }

    @Test
    void renombrarElRolSeReflejaEnLaLaneSinActualizarCopias() {
        autenticar(RolUsuario.EDITOR);
        Proceso proceso = existeElProceso(EMPRESA_PROPIA, false);
        Lane lane = existeLaLane();
        RolProceso rol = existeElRol(EMPRESA_PROPIA, true);
        laneService.asignarRolProceso(PROCESO_ID, LANE_ID, ROL_ID, USERNAME);
        when(laneRepository.findByPoolIdOrderByIdAsc(POOL_ID)).thenReturn(List.of(lane));

        rol.setNombre("Analista senior");

        assertThat(laneService.lanesDe(proceso)).extracting(LaneRespuestaDto::getNombre)
                .containsExactly("Analista senior");
        assertThat(lane.getNombre()).isEqualTo("General");
        verify(laneRepository, times(1)).save(any(Lane.class));
    }

    @Test
    void reasignarElMismoRolNoModificaNada() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        Lane lane = existeLaLane();
        RolProceso rol = existeElRol(EMPRESA_PROPIA, true);
        lane.setRolProceso(rol);

        LaneRespuestaDto respuesta = laneService.asignarRolProceso(PROCESO_ID, LANE_ID, ROL_ID, USERNAME);

        assertThat(respuesta.getRolProcesoId()).isEqualTo(ROL_ID);
        noSeModificoNada();
    }

    @Test
    void reasignarLaLaneAOtroRolCambiaLaReferenciaYQuedaEnElHistorial() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        Lane lane = existeLaLane();
        lane.setRolProceso(new RolProceso(41L, "Supervisor", "Aprueba excepciones", empresa(EMPRESA_PROPIA), true));
        RolProceso analista = existeElRol(EMPRESA_PROPIA, true);

        LaneRespuestaDto respuesta = laneService.asignarRolProceso(PROCESO_ID, LANE_ID, ROL_ID, USERNAME);

        assertThat(lane.getRolProceso()).isSameAs(analista);
        assertThat(respuesta.getNombre()).isEqualTo(ANALISTA);
        ArgumentCaptor<HistorialProceso> capturado = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(capturado.capture());
        assertThat(capturado.getValue().getCambiosRealizados())
                .isEqualTo("lane 'Supervisor': rol de proceso -> '" + ANALISTA + "'");
    }

    @Test
    void noSePuedeUsarUnRolDeOtraEmpresaEnUnaLane() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        Lane lane = existeLaLane();
        existeElRol(EMPRESA_AJENA, true);

        assertThatThrownBy(() -> laneService.asignarRolProceso(PROCESO_ID, LANE_ID, ROL_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(RolProcesoService.ROL_AJENO);

        assertThat(lane.getRolProceso()).isNull();
        noSeModificoNada();
    }

    @Test
    void noSePuedeUsarUnRolEliminadoEnUnaLane() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeLaLane();
        existeElRol(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> laneService.asignarRolProceso(PROCESO_ID, LANE_ID, ROL_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(RolProcesoService.ROL_ELIMINADO);

        noSeModificoNada();
    }

    @Test
    void noSeAsignanRolesEnLanesDeUnProcesoDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> laneService.asignarRolProceso(PROCESO_ID, LANE_ID, ROL_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        noSeModificoNada();
        verify(rolProcesoRepository, never()).findById(anyLong());
    }

    @Test
    void noSeAsignanRolesEnUnProcesoEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, true);

        assertThatThrownBy(() -> laneService.asignarRolProceso(PROCESO_ID, LANE_ID, ROL_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");

        noSeModificoNada();
    }

    @Test
    void elUsuarioDeSoloLecturaNoAsignaRolesALanes() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> laneService.asignarRolProceso(PROCESO_ID, LANE_ID, ROL_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(LaneService.SIN_PERMISO_ESCRITURA);

        noSeModificoNada();
    }

    @Test
    void noSeAsignaUnRolAUnaLaneQueNoEsDelProceso() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        when(laneRepository.findByIdAndPoolId(LANE_ID, POOL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> laneService.asignarRolProceso(PROCESO_ID, LANE_ID, ROL_ID, USERNAME))
                .isInstanceOf(LaneNoValidaException.class);

        noSeModificoNada();
    }
}
