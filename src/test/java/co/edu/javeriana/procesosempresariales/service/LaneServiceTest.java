package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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
import co.edu.javeriana.procesosempresariales.domain.PermisoEstructuraProceso;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolProceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CrearLaneDto;
import co.edu.javeriana.procesosempresariales.dto.EditarLaneDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ReordenarLanesDto;
import co.edu.javeriana.procesosempresariales.exception.LaneConActividadesException;
import co.edu.javeriana.procesosempresariales.exception.LaneDuplicadaException;
import co.edu.javeriana.procesosempresariales.exception.LaneNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.PoolCajaNegraException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.HistorialRolProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.LaneRepository;
import co.edu.javeriana.procesosempresariales.repository.PermisoEstructuraProcesoRepository;
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
    private static final Long ROL_ID = 40L;
    private static final String ANALISTA = "Analista de credito";
    private static final String POOL = "Empresa 7";

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

    @Mock
    private PermisoEstructuraProcesoRepository permisoEstructuraProcesoRepository;

    @Mock
    private PoolService poolService;

    private LaneService laneService;

    private Pool pool;

    @BeforeEach
    void inicializar() {
        UsuarioService usuarios = new UsuarioService(usuarioRepository, new ModelMapper(),
                new BCryptPasswordEncoder());
        AccesoProcesoService acceso = new AccesoProcesoService(usuarios, procesoRepository);
        HistorialProcesoService historial = new HistorialProcesoService(historialProcesoRepository);
        RolProcesoService roles = new RolProcesoService(rolProcesoRepository, historialRolProcesoRepository,
                new RolProcesoSpecifications(), acceso);
        PermisoEstructuraService permisos = new PermisoEstructuraService(permisoEstructuraProcesoRepository, acceso,
                historial);
        laneService = new LaneService(laneRepository, acceso, roles, historial, poolService, permisos);
        pool = new Pool(POOL_ID, null, POOL, TipoPool.PROPIETARIO, 1, false, true, null, new ArrayList<>());
    }

    private Empresa empresa(Long id) {
        return new Empresa(id, "Empresa " + id, "900123456-" + id, "contacto" + id + "@alpes.com");
    }

    private void autenticar(RolUsuario rol) {
        when(usuarioRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(new Usuario(1L, USERNAME, HASH, rol, true, empresa(EMPRESA_PROPIA))));
    }

    private Proceso existeElProceso(Long empresaId, boolean eliminado) {
        Proceso proceso = new Proceso(PROCESO_ID, "Ventas", "Proceso comercial", "Comercial",
                EstadoProceso.BORRADOR, empresa(empresaId), List.of(pool), eliminado);
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.of(proceso));
        return proceso;
    }

    private void existeElPool() {
        when(poolService.poolActivoDelProceso(eq(POOL_ID), any(Proceso.class))).thenReturn(pool);
    }

    private void existeElPoolConElementos() {
        when(poolService.poolConElementos(eq(POOL_ID), any(Proceso.class))).thenReturn(pool);
    }

    private RolProceso rol(Long id, String nombre, Long empresaId, boolean activo) {
        return new RolProceso(id, nombre, "Responsabilidad", empresa(empresaId), activo);
    }

    private RolProceso existeElRol(Long empresaId, boolean activo) {
        RolProceso rol = rol(ROL_ID, ANALISTA, empresaId, activo);
        when(rolProcesoRepository.findById(ROL_ID)).thenReturn(Optional.of(rol));
        return rol;
    }

    private Lane lane(Long id, String nombre, int orden) {
        return new Lane(id, nombre, pool, null, orden, true);
    }

    private List<Lane> existenLasLanes(Lane... lanes) {
        List<Lane> lista = List.of(lanes);
        when(laneRepository.findByPoolIdAndActivoTrueOrderByOrdenAscIdAsc(POOL_ID)).thenReturn(lista);
        return lista;
    }

    private Lane existeEnElPool(Lane lane) {
        when(laneRepository.findByIdAndPoolIdAndActivoTrue(lane.getId(), POOL_ID)).thenReturn(Optional.of(lane));
        return lane;
    }

    private void editorConPermisos(boolean crear, boolean editar, boolean eliminar) {
        when(permisoEstructuraProcesoRepository.findByProcesoIdAndRol(PROCESO_ID, RolUsuario.EDITOR))
                .thenReturn(Optional.of(new PermisoEstructuraProceso(1L, null, RolUsuario.EDITOR, true, true, false,
                        crear, editar, eliminar)));
    }

    @SuppressWarnings("unchecked")
    private List<Lane> lanesGuardadas() {
        ArgumentCaptor<List<Lane>> capturadas = ArgumentCaptor.forClass(List.class);
        verify(laneRepository).saveAll(capturadas.capture());
        return capturadas.getValue();
    }

    private HistorialProceso historialGuardado() {
        ArgumentCaptor<HistorialProceso> capturado = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private void noSeModificoNada() {
        verify(laneRepository, never()).saveAll(anyList());
        verify(laneRepository, never()).save(any(Lane.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void laLaneDeUnaActividadSeBuscaEntreLasLanesActivasDeLosPoolsDelProceso() {
        Lane general = lane(11L, "General", 1);
        when(laneRepository.findByIdAndPoolProcesoIdAndActivoTrueAndPoolActivoTrue(11L, PROCESO_ID))
                .thenReturn(Optional.of(general));
        Proceso proceso = new Proceso(PROCESO_ID, "Ventas", "d", "c", EstadoProceso.BORRADOR,
                empresa(EMPRESA_PROPIA), List.of(pool), false);

        assertThat(laneService.laneDelProceso(11L, proceso)).isSameAs(general);
    }

    @Test
    void unaLaneEliminadaODeOtroProcesoNoSirveParaUnaActividad() {
        Proceso proceso = new Proceso(PROCESO_ID, "Ventas", "d", "c", EstadoProceso.BORRADOR,
                empresa(EMPRESA_PROPIA), List.of(pool), false);

        assertThatThrownBy(() -> laneService.laneDelProceso(11L, proceso))
                .isInstanceOf(LaneNoValidaException.class)
                .hasMessage(LaneService.LANE_INVALIDA);
    }

    @Test
    void lasLanesDelProcesoMuestranElNombreFuncionalSuPoolYSuOrden() {
        Lane general = lane(11L, "General", 1);
        Lane conRol = new Lane(12L, null, pool, rol(ROL_ID, ANALISTA, EMPRESA_PROPIA, true), 2, true);
        when(laneRepository.activasDelProceso(PROCESO_ID)).thenReturn(List.of(general, conRol));
        Proceso proceso = new Proceso(PROCESO_ID, "Ventas", "d", "c", EstadoProceso.BORRADOR,
                empresa(EMPRESA_PROPIA), List.of(pool), false);

        List<LaneRespuestaDto> lanes = laneService.lanesDe(proceso);

        assertThat(lanes).extracting(LaneRespuestaDto::getNombre).containsExactly("General", ANALISTA);
        assertThat(lanes).extracting(LaneRespuestaDto::getRolProcesoId).containsExactly(null, ROL_ID);
        assertThat(lanes).extracting(LaneRespuestaDto::getPoolId).containsExactly(POOL_ID, POOL_ID);
        assertThat(lanes).extracting(LaneRespuestaDto::getOrden).containsExactly(1, 2);
    }

    @Test
    void elLectorListaLasLanesDeUnPoolDeSuProceso() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        existenLasLanes(lane(11L, "General", 1), lane(12L, "Cartera", 2));

        List<LaneRespuestaDto> lanes = laneService.listar(PROCESO_ID, POOL_ID, USERNAME);

        assertThat(lanes).extracting(LaneRespuestaDto::getNombre).containsExactly("General", "Cartera");
    }

    @Test
    void unaEmpresaInvitadaListaLasLanesDelProcesoCompartido() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_AJENA, false);
        when(procesoRepository.estaCompartidoCon(PROCESO_ID, EMPRESA_PROPIA)).thenReturn(true);
        existeElPool();
        existenLasLanes(lane(11L, "General", 1));

        assertThat(laneService.listar(PROCESO_ID, POOL_ID, USERNAME)).hasSize(1);
    }

    @Test
    void unaEmpresaNoInvitadaNoListaLasLanesDeOtroProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> laneService.listar(PROCESO_ID, POOL_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");

        verify(poolService, never()).poolActivoDelProceso(anyLong(), any(Proceso.class));
    }

    @Test
    void elEditorCreaUnaLaneConRolAlFinalDelPoolSinCopiarElNombre() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPoolConElementos();
        RolProceso analista = existeElRol(EMPRESA_PROPIA, true);
        Lane general = lane(11L, "General", 1);
        existenLasLanes(general);

        LaneRespuestaDto creada = laneService.crear(PROCESO_ID, POOL_ID, new CrearLaneDto(ROL_ID, null), USERNAME);

        List<Lane> guardadas = lanesGuardadas();
        assertThat(guardadas).hasSize(2);
        Lane nueva = guardadas.get(1);
        assertThat(nueva.getNombre()).isNull();
        assertThat(nueva.getRolProceso()).isSameAs(analista);
        assertThat(nueva.getPool()).isSameAs(pool);
        assertThat(nueva.isActivo()).isTrue();
        assertThat(nueva.getOrden()).isEqualTo(2);
        assertThat(general.getOrden()).isEqualTo(1);
        assertThat(creada.getNombre()).isEqualTo(ANALISTA);
        assertThat(creada.getRolProcesoId()).isEqualTo(ROL_ID);
        assertThat(creada.getPoolId()).isEqualTo(POOL_ID);
        assertThat(creada.getOrden()).isEqualTo(2);
    }

    @Test
    void crearUnaLaneEnUnaPosicionDesplazaLasSiguientes() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPoolConElementos();
        existeElRol(EMPRESA_PROPIA, true);
        Lane primera = lane(11L, "General", 1);
        Lane segunda = lane(12L, "Cartera", 2);
        existenLasLanes(primera, segunda);

        laneService.crear(PROCESO_ID, POOL_ID, new CrearLaneDto(ROL_ID, 1), USERNAME);

        List<Lane> guardadas = lanesGuardadas();
        assertThat(guardadas.get(0).getRolProceso().getId()).isEqualTo(ROL_ID);
        assertThat(guardadas).extracting(Lane::getOrden).containsExactly(1, 2, 3);
        assertThat(primera.getOrden()).isEqualTo(2);
        assertThat(segunda.getOrden()).isEqualTo(3);
        verify(permisoEstructuraProcesoRepository, never()).findByProcesoIdAndRol(anyLong(), any(RolUsuario.class));
    }

    @Test
    void crearUnaLaneQuedaEnElHistorialDelProceso() {
        autenticar(RolUsuario.EDITOR);
        Proceso proceso = existeElProceso(EMPRESA_PROPIA, false);
        existeElPoolConElementos();
        existeElRol(EMPRESA_PROPIA, true);
        existenLasLanes(lane(11L, "General", 1));

        laneService.crear(PROCESO_ID, POOL_ID, new CrearLaneDto(ROL_ID, null), USERNAME);

        HistorialProceso historial = historialGuardado();
        assertThat(historial.getProceso()).isSameAs(proceso);
        assertThat(historial.getCambiosRealizados())
                .isEqualTo("lane creada en el pool '" + POOL + "': '" + ANALISTA + "' en la posición 2");
    }

    @Test
    void crearRechazaUnRolDeProcesoDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPoolConElementos();
        existeElRol(EMPRESA_AJENA, true);

        assertThatThrownBy(() -> laneService.crear(PROCESO_ID, POOL_ID, new CrearLaneDto(ROL_ID, null), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(RolProcesoService.ROL_AJENO);

        noSeModificoNada();
    }

    @Test
    void crearRechazaUnRolDeProcesoEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPoolConElementos();
        existeElRol(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> laneService.crear(PROCESO_ID, POOL_ID, new CrearLaneDto(ROL_ID, null), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(RolProcesoService.ROL_ELIMINADO);

        noSeModificoNada();
    }

    @Test
    void crearRechazaUnRolQueYaTieneLaneEnElPool() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPoolConElementos();
        existeElRol(EMPRESA_PROPIA, true);
        when(laneRepository.existsByPoolIdAndRolProcesoIdAndActivoTrue(POOL_ID, ROL_ID)).thenReturn(true);

        assertThatThrownBy(() -> laneService.crear(PROCESO_ID, POOL_ID, new CrearLaneDto(ROL_ID, null), USERNAME))
                .isInstanceOf(LaneDuplicadaException.class)
                .hasMessage(LaneService.ROL_REPETIDO);

        noSeModificoNada();
    }

    @Test
    void crearRechazaUnaPosicionFueraDeRango() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPoolConElementos();
        existeElRol(EMPRESA_PROPIA, true);
        existenLasLanes(lane(11L, "General", 1));

        assertThatThrownBy(() -> laneService.crear(PROCESO_ID, POOL_ID, new CrearLaneDto(ROL_ID, 3), USERNAME))
                .isInstanceOf(LaneNoValidaException.class)
                .hasMessage(LaneService.ORDEN_FUERA_DE_RANGO + 2);

        noSeModificoNada();
    }

    @Test
    void unPoolCajaNegraNoAdmiteLanes() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        when(poolService.poolConElementos(eq(POOL_ID), any(Proceso.class)))
                .thenThrow(new PoolCajaNegraException("El pool 'Cliente'" + PoolService.CAJA_NEGRA));

        assertThatThrownBy(() -> laneService.crear(PROCESO_ID, POOL_ID, new CrearLaneDto(ROL_ID, null), USERNAME))
                .isInstanceOf(PoolCajaNegraException.class);

        noSeModificoNada();
        verify(rolProcesoRepository, never()).findById(anyLong());
    }

    @Test
    void elUsuarioDeSoloLecturaNuncaCreaLanes() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> laneService.crear(PROCESO_ID, POOL_ID, new CrearLaneDto(ROL_ID, null), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El rol SOLO_LECTURA no tiene permiso para crear lanes en este proceso");

        noSeModificoNada();
    }

    @Test
    void unEditorSinPermisoConfiguradoNoCreaLanes() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        editorConPermisos(false, true, false);

        assertThatThrownBy(() -> laneService.crear(PROCESO_ID, POOL_ID, new CrearLaneDto(ROL_ID, null), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El rol EDITOR no tiene permiso para crear lanes en este proceso");

        noSeModificoNada();
    }

    @Test
    void unaEmpresaInvitadaNoCreaLanesAunqueElProcesoEsteCompartido() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> laneService.crear(PROCESO_ID, POOL_ID, new CrearLaneDto(ROL_ID, null), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");

        noSeModificoNada();
        verify(procesoRepository, never()).estaCompartidoCon(anyLong(), anyLong());
    }

    @Test
    void noSeCreanLanesEnUnProcesoEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, true);

        assertThatThrownBy(() -> laneService.crear(PROCESO_ID, POOL_ID, new CrearLaneDto(ROL_ID, null), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");

        noSeModificoNada();
    }

    @Test
    void asignarUnRolALaLaneGeneralHaceQueSuNombreFuncionalSeaElDelRol() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        Lane general = existeEnElPool(lane(11L, "General", 1));
        RolProceso analista = existeElRol(EMPRESA_PROPIA, true);

        LaneRespuestaDto editada = laneService.editar(PROCESO_ID, POOL_ID, 11L, new EditarLaneDto(ROL_ID, null),
                USERNAME);

        assertThat(general.getRolProceso()).isSameAs(analista);
        assertThat(general.getNombre()).isEqualTo("General");
        assertThat(editada.getNombre()).isEqualTo(ANALISTA);
        assertThat(lanesGuardadas()).containsExactly(general);
        assertThat(historialGuardado().getCambiosRealizados()).isEqualTo("lane 'General' del pool '" + POOL
                + "': rol de proceso: 'General' -> '" + ANALISTA + "'");
    }

    @Test
    void renombrarElRolSeReflejaEnLaLaneSinActualizarCopias() {
        autenticar(RolUsuario.EDITOR);
        Proceso proceso = existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        Lane general = existeEnElPool(lane(11L, "General", 1));
        RolProceso analista = existeElRol(EMPRESA_PROPIA, true);
        laneService.editar(PROCESO_ID, POOL_ID, 11L, new EditarLaneDto(ROL_ID, null), USERNAME);
        when(laneRepository.activasDelProceso(PROCESO_ID)).thenReturn(List.of(general));

        analista.setNombre("Analista senior");

        assertThat(laneService.lanesDe(proceso)).extracting(LaneRespuestaDto::getNombre)
                .containsExactly("Analista senior");
        assertThat(general.getNombre()).isEqualTo("General");
        verify(laneRepository, times(1)).saveAll(anyList());
    }

    @Test
    void editarConElMismoRolYSinOrdenNoCambiaNada() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        RolProceso analista = existeElRol(EMPRESA_PROPIA, true);
        existeEnElPool(new Lane(11L, null, pool, analista, 1, true));

        LaneRespuestaDto respuesta = laneService.editar(PROCESO_ID, POOL_ID, 11L, new EditarLaneDto(ROL_ID, 1),
                USERNAME);

        assertThat(respuesta.getNombre()).isEqualTo(ANALISTA);
        noSeModificoNada();
    }

    @Test
    void moverLaPrimeraLaneAlFinal() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        RolProceso analista = existeElRol(EMPRESA_PROPIA, true);
        Lane a = new Lane(11L, null, pool, analista, 1, true);
        Lane b = lane(12L, "B", 2);
        Lane c = lane(13L, "C", 3);
        existeEnElPool(a);
        existenLasLanes(a, b, c);

        laneService.editar(PROCESO_ID, POOL_ID, 11L, new EditarLaneDto(ROL_ID, 3), USERNAME);

        assertThat(lanesGuardadas()).containsExactly(b, c, a);
        assertThat(List.of(b.getOrden(), c.getOrden(), a.getOrden())).containsExactly(1, 2, 3);
        assertThat(historialGuardado().getCambiosRealizados()).endsWith("orden: 1 -> 3");
    }

    @Test
    void moverLaUltimaLaneAlInicio() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        RolProceso analista = existeElRol(EMPRESA_PROPIA, true);
        Lane a = lane(11L, "A", 1);
        Lane b = lane(12L, "B", 2);
        Lane c = new Lane(13L, null, pool, analista, 3, true);
        existeEnElPool(c);
        existenLasLanes(a, b, c);

        laneService.editar(PROCESO_ID, POOL_ID, 13L, new EditarLaneDto(ROL_ID, 1), USERNAME);

        assertThat(lanesGuardadas()).containsExactly(c, a, b);
        assertThat(List.of(c.getOrden(), a.getOrden(), b.getOrden())).containsExactly(1, 2, 3);
    }

    @Test
    void moverUnaLaneAUnaPosicionIntermediaSinDuplicarPosiciones() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        RolProceso analista = existeElRol(EMPRESA_PROPIA, true);
        Lane a = new Lane(11L, null, pool, analista, 1, true);
        Lane b = lane(12L, "B", 2);
        Lane c = lane(13L, "C", 3);
        existeEnElPool(a);
        existenLasLanes(a, b, c);

        laneService.editar(PROCESO_ID, POOL_ID, 11L, new EditarLaneDto(ROL_ID, 2), USERNAME);

        List<Lane> guardadas = lanesGuardadas();
        assertThat(guardadas).containsExactly(b, a, c);
        assertThat(guardadas).extracting(Lane::getOrden).containsExactly(1, 2, 3).doesNotHaveDuplicates();
    }

    @Test
    void editarRechazaUnOrdenFueraDeRango() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        RolProceso analista = existeElRol(EMPRESA_PROPIA, true);
        Lane a = existeEnElPool(new Lane(11L, null, pool, analista, 1, true));
        existenLasLanes(a, lane(12L, "B", 2));

        assertThatThrownBy(() -> laneService.editar(PROCESO_ID, POOL_ID, 11L, new EditarLaneDto(ROL_ID, 3),
                USERNAME))
                .isInstanceOf(LaneNoValidaException.class)
                .hasMessage(LaneService.ORDEN_FUERA_DE_RANGO + 2);

        noSeModificoNada();
    }

    @Test
    void editarRechazaUnRolQueYaUsaOtraLaneDelPool() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        existeEnElPool(lane(11L, "General", 1));
        existeElRol(EMPRESA_PROPIA, true);
        when(laneRepository.existsByPoolIdAndRolProcesoIdAndActivoTrueAndIdNot(POOL_ID, ROL_ID, 11L))
                .thenReturn(true);

        assertThatThrownBy(() -> laneService.editar(PROCESO_ID, POOL_ID, 11L, new EditarLaneDto(ROL_ID, null),
                USERNAME))
                .isInstanceOf(LaneDuplicadaException.class);

        noSeModificoNada();
    }

    @Test
    void editarUnaLaneQueNoEstaEnElPoolInformaQueNoExiste() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();

        assertThatThrownBy(() -> laneService.editar(PROCESO_ID, POOL_ID, 11L, new EditarLaneDto(ROL_ID, null),
                USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(LaneService.LANE_NO_EXISTE);
    }

    @Test
    void elUsuarioDeSoloLecturaNoEditaLanes() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> laneService.editar(PROCESO_ID, POOL_ID, 11L, new EditarLaneDto(ROL_ID, null),
                USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        noSeModificoNada();
    }

    @Test
    void reordenarAsignaPosicionesConsecutivasSegunLaLista() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        Lane a = lane(11L, "A", 1);
        Lane b = lane(12L, "B", 2);
        Lane c = lane(13L, "C", 3);
        existenLasLanes(a, b, c);

        List<LaneRespuestaDto> nuevas = laneService.reordenar(PROCESO_ID, POOL_ID,
                new ReordenarLanesDto(List.of(13L, 11L, 12L)), USERNAME);

        assertThat(nuevas).extracting(LaneRespuestaDto::getId).containsExactly(13L, 11L, 12L);
        assertThat(nuevas).extracting(LaneRespuestaDto::getOrden).containsExactly(1, 2, 3);
        assertThat(lanesGuardadas()).containsExactly(c, a, b);
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("lanes del pool '" + POOL + "' reordenadas: 'C', 'A', 'B'");
    }

    @Test
    void reordenarRechazaUnaListaIncompleta() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        existenLasLanes(lane(11L, "A", 1), lane(12L, "B", 2), lane(13L, "C", 3));

        assertThatThrownBy(() -> laneService.reordenar(PROCESO_ID, POOL_ID,
                new ReordenarLanesDto(List.of(11L, 12L)), USERNAME))
                .isInstanceOf(LaneNoValidaException.class)
                .hasMessage(LaneService.ORDEN_INCOMPLETO);

        noSeModificoNada();
    }

    @Test
    void reordenarRechazaLanesRepetidas() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        existenLasLanes(lane(11L, "A", 1), lane(12L, "B", 2), lane(13L, "C", 3));

        assertThatThrownBy(() -> laneService.reordenar(PROCESO_ID, POOL_ID,
                new ReordenarLanesDto(List.of(11L, 11L, 12L)), USERNAME))
                .isInstanceOf(LaneNoValidaException.class);

        noSeModificoNada();
    }

    @Test
    void reordenarRechazaLanesDeOtroPool() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        existenLasLanes(lane(11L, "A", 1), lane(12L, "B", 2));

        assertThatThrownBy(() -> laneService.reordenar(PROCESO_ID, POOL_ID,
                new ReordenarLanesDto(List.of(11L, 99L)), USERNAME))
                .isInstanceOf(LaneNoValidaException.class);

        noSeModificoNada();
    }

    @Test
    void reordenarConElMismoOrdenNoRegistraHistorial() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        existenLasLanes(lane(11L, "A", 1), lane(12L, "B", 2));

        List<LaneRespuestaDto> iguales = laneService.reordenar(PROCESO_ID, POOL_ID,
                new ReordenarLanesDto(List.of(11L, 12L)), USERNAME);

        assertThat(iguales).extracting(LaneRespuestaDto::getOrden).containsExactly(1, 2);
        noSeModificoNada();
    }

    @Test
    void elAdministradorEliminaUnaLaneVaciaDeFormaLogicaYRenumeraLasDemas() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        Lane a = lane(11L, "A", 1);
        Lane b = existeEnElPool(lane(12L, "B", 2));
        Lane c = lane(13L, "C", 3);
        existenLasLanes(a, b, c);

        LaneRespuestaDto eliminada = laneService.eliminar(PROCESO_ID, POOL_ID, 12L, USERNAME);

        assertThat(b.isActivo()).isFalse();
        assertThat(eliminada.getId()).isEqualTo(12L);
        assertThat(a.getOrden()).isEqualTo(1);
        assertThat(c.getOrden()).isEqualTo(2);
        assertThat(lanesGuardadas()).containsExactly(a, c, b);
        verify(laneRepository, never()).delete(any(Lane.class));
        verify(laneRepository, never()).deleteById(anyLong());
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("lane eliminada del pool '" + POOL + "': 'B'");
    }

    @Test
    void unaLaneConActividadesActivasNoSeElimina() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();
        Lane b = existeEnElPool(lane(12L, "B", 2));
        when(laneRepository.actividadesActivas(12L)).thenReturn(2L);

        assertThatThrownBy(() -> laneService.eliminar(PROCESO_ID, POOL_ID, 12L, USERNAME))
                .isInstanceOf(LaneConActividadesException.class)
                .hasMessageContaining("tiene 2 actividades activas");

        assertThat(b.isActivo()).isTrue();
        noSeModificoNada();
    }

    @Test
    void unaLaneYaEliminadaNoSeEliminaDeNuevo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existeElPool();

        assertThatThrownBy(() -> laneService.eliminar(PROCESO_ID, POOL_ID, 12L, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(LaneService.LANE_NO_EXISTE);

        noSeModificoNada();
    }

    @Test
    void elEditorNoEliminaLanesConLaPoliticaPorDefecto() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> laneService.eliminar(PROCESO_ID, POOL_ID, 12L, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El rol EDITOR no tiene permiso para eliminar lanes en este proceso");

        noSeModificoNada();
    }

    @Test
    void unEditorConPermisoConfiguradoEliminaLanes() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        editorConPermisos(true, true, true);
        existeElPool();
        Lane b = existeEnElPool(lane(12L, "B", 1));
        existenLasLanes(b);

        laneService.eliminar(PROCESO_ID, POOL_ID, 12L, USERNAME);

        assertThat(b.isActivo()).isFalse();
    }
}
