package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import co.edu.javeriana.procesosempresariales.domain.Actividad;
import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.EditarActividadDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.LaneNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.NombreActividadDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.LaneRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class ActividadServiceTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final String HASH_CREDENCIAL = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final Long PROCESO_ID = 5L;
    private static final Long POOL_ID = 80L;
    private static final Long LANE_ID = 11L;
    private static final Long LANE_DESTINO_ID = 12L;
    private static final Long ACTIVIDAD_ID = 30L;
    private static final String NOMBRE_DUPLICADO = "Ya existe una actividad con ese nombre en el proceso";
    private static final String LANE_INVALIDA = "La lane indicada no existe o no pertenece a este proceso";

    @Mock
    private ActividadRepository actividadRepository;

    @Mock
    private ProcesoRepository procesoRepository;

    @Mock
    private LaneRepository laneRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private HistorialProcesoRepository historialProcesoRepository;

    @Mock
    private ConexionesService conexionesService;

    private ActividadService actividadService;

    @BeforeEach
    void inicializar() {
        UsuarioService usuarios = new UsuarioService(usuarioRepository, new ModelMapper(),
                new BCryptPasswordEncoder());
        AccesoProcesoService acceso = new AccesoProcesoService(usuarios, procesoRepository);
        actividadService = new ActividadService(actividadRepository, laneRepository, acceso,
                new HistorialProcesoService(historialProcesoRepository), conexionesService, new ModelMapper());
    }

    private Empresa empresa(Long id) {
        return new Empresa(id, "Alpes Logistica", "900123456-7", "contacto@alpes.com");
    }

    private Usuario usuarioAutenticado(RolUsuario rol) {
        return new Usuario(1L, USERNAME, HASH_CREDENCIAL, rol, true, empresa(EMPRESA_PROPIA));
    }

    private void autenticar(RolUsuario rol) {
        when(usuarioRepository.findByUsername(USERNAME)).thenReturn(Optional.of(usuarioAutenticado(rol)));
    }

    private Pool pool() {
        return new Pool(POOL_ID, "Alpes Logistica", List.of());
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

    private Proceso existeElProcesoActivo() {
        return existeElProceso(EMPRESA_PROPIA, false);
    }

    private Lane lane(Long id, String nombre) {
        return new Lane(id, nombre, pool());
    }

    private void existeLaLane(Long id, String nombre) {
        when(laneRepository.findByIdAndPoolId(id, POOL_ID)).thenReturn(Optional.of(lane(id, nombre)));
    }

    private Actividad actividad(Proceso proceso, Lane lane, boolean activo) {
        return new Actividad(ACTIVIDAD_ID, "Revisar solicitud", TipoActividad.TAREA_USUARIO, proceso, lane, 120, 40,
                activo);
    }

    private Actividad existeLaActividad(Proceso proceso, Lane lane, boolean activo) {
        Actividad actividad = actividad(proceso, lane, activo);
        when(actividadRepository.findByIdAndProcesoId(ACTIVIDAD_ID, PROCESO_ID)).thenReturn(Optional.of(actividad));
        return actividad;
    }

    private void devolverLaActividadGuardada() {
        when(actividadRepository.save(any(Actividad.class))).thenAnswer(invocacion -> {
            Actividad guardada = invocacion.getArgument(0);
            guardada.setId(ACTIVIDAD_ID);
            return guardada;
        });
    }

    private Actividad actividadGuardada() {
        ArgumentCaptor<Actividad> capturada = ArgumentCaptor.forClass(Actividad.class);
        verify(actividadRepository).save(capturada.capture());
        return capturada.getValue();
    }

    private HistorialProceso historialGuardado() {
        ArgumentCaptor<HistorialProceso> capturado = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private CrearActividadDto formularioCreacion() {
        return new CrearActividadDto("Revisar solicitud", TipoActividad.TAREA_USUARIO, LANE_ID, 120, 40);
    }

    private EditarActividadDto formularioEdicion() {
        return new EditarActividadDto("Revisar solicitud", TipoActividad.TAREA_USUARIO, LANE_ID);
    }

    @Test
    void crearIdentificaAlUsuarioPorSuUsername() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeLaLane(LANE_ID, "General");
        devolverLaActividadGuardada();

        actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        verify(usuarioRepository).findByUsername(USERNAME);
    }

    @Test
    void crearRechazaUnUsuarioAutenticadoQueNoExiste() {
        when(usuarioRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El usuario autenticado no existe");

        verify(actividadRepository, never()).save(any(Actividad.class));
    }

    @Test
    void elAdministradorCreaActividades() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeLaLane(LANE_ID, "General");
        devolverLaActividadGuardada();

        ActividadRespuestaDto creada = actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        assertThat(creada.getId()).isEqualTo(ACTIVIDAD_ID);
        assertThat(creada.getProcesoId()).isEqualTo(PROCESO_ID);
        assertThat(creada.getLaneId()).isEqualTo(LANE_ID);
        assertThat(creada.getLaneNombre()).isEqualTo("General");
    }

    @Test
    void elEditorCreaActividades() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        existeLaLane(LANE_ID, "General");
        devolverLaActividadGuardada();

        actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        assertThat(actividadGuardada().getNombre()).isEqualTo("Revisar solicitud");
    }

    @Test
    void elUsuarioDeSoloLecturaNoCreaActividades() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("Solo un administrador o editor puede crear o modificar actividades");

        verify(actividadRepository, never()).save(any(Actividad.class));
    }

    @Test
    void crearNormalizaElNombreAntesDeGuardarlo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeLaLane(LANE_ID, "General");
        devolverLaActividadGuardada();
        CrearActividadDto dto = formularioCreacion();
        dto.setNombre("   Revisar solicitud   ");

        actividadService.crear(PROCESO_ID, dto, USERNAME);

        assertThat(actividadGuardada().getNombre()).isEqualTo("Revisar solicitud");
        verify(actividadRepository).existsByProcesoIdAndNombreIgnoreCase(PROCESO_ID, "Revisar solicitud");
    }

    @Test
    void crearRechazaUnNombreYaUsadoEnElMismoProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        when(actividadRepository.existsByProcesoIdAndNombreIgnoreCase(PROCESO_ID, "Revisar solicitud"))
                .thenReturn(true);

        assertThatThrownBy(() -> actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(NombreActividadDuplicadoException.class)
                .hasMessage(NOMBRE_DUPLICADO);

        verify(actividadRepository, never()).save(any(Actividad.class));
    }

    @Test
    void crearTraduceLaColisionDeLaBaseDeDatosEnNombreDuplicado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeLaLane(LANE_ID, "General");
        when(actividadRepository.save(any(Actividad.class)))
                .thenThrow(new DataIntegrityViolationException("uk_actividad_proceso_nombre"));

        assertThatThrownBy(() -> actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(NombreActividadDuplicadoException.class)
                .hasMessage(NOMBRE_DUPLICADO);

        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void crearRechazaUnProcesoQueNoExiste() {
        autenticar(RolUsuario.ADMINISTRADOR);
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso no existe");
    }

    @Test
    void crearRechazaUnProcesoDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");

        verify(actividadRepository, never()).save(any(Actividad.class));
    }

    @Test
    void crearRechazaUnProcesoEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, true);

        assertThatThrownBy(() -> actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");

        verify(actividadRepository, never()).save(any(Actividad.class));
    }

    @Test
    void crearBuscaLaLaneDentroDelPoolDelProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeLaLane(LANE_ID, "General");
        devolverLaActividadGuardada();

        actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        verify(laneRepository).findByIdAndPoolId(LANE_ID, POOL_ID);
        assertThat(actividadGuardada().getLane().getId()).isEqualTo(LANE_ID);
    }

    @Test
    void crearRechazaUnaLaneQueNoPerteneceAlPoolDelProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        when(laneRepository.findByIdAndPoolId(LANE_ID, POOL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(LaneNoValidaException.class)
                .hasMessage(LANE_INVALIDA);

        verify(actividadRepository, never()).save(any(Actividad.class));
    }

    @Test
    void crearConservaLaPosicionVisualIndicada() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeLaLane(LANE_ID, "General");
        devolverLaActividadGuardada();

        ActividadRespuestaDto creada = actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        Actividad guardada = actividadGuardada();
        assertThat(guardada.getPosicionX()).isEqualTo(120);
        assertThat(guardada.getPosicionY()).isEqualTo(40);
        assertThat(creada.getPosicionX()).isEqualTo(120);
        assertThat(creada.getPosicionY()).isEqualTo(40);
    }

    @Test
    void crearDejaLaActividadVigente() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeLaLane(LANE_ID, "General");
        devolverLaActividadGuardada();

        ActividadRespuestaDto creada = actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        assertThat(actividadGuardada().isActivo()).isTrue();
        assertThat(creada.isActivo()).isTrue();
    }

    @Test
    void crearEnlazaLaActividadConSuProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaLane(LANE_ID, "General");
        devolverLaActividadGuardada();

        actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        assertThat(actividadGuardada().getProceso()).isSameAs(proceso);
    }

    @Test
    void crearRegistraLaCreacionEnElHistorialDelProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaLane(LANE_ID, "General");
        devolverLaActividadGuardada();

        actividadService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        HistorialProceso historial = historialGuardado();
        assertThat(historial.getProceso()).isSameAs(proceso);
        assertThat(historial.getUsuario().getUsername()).isEqualTo(USERNAME);
        assertThat(historial.getCambiosRealizados()).isEqualTo("actividad creada: 'Revisar solicitud'");
        assertThat(historial.getEstadoAnterior()).isEqualTo(EstadoProceso.BORRADOR.name());
        assertThat(historial.getFecha()).isNotNull();
    }

    @Test
    void elAdministradorEditaActividades() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);
        existeLaLane(LANE_ID, "General");
        EditarActividadDto dto = formularioEdicion();
        dto.setNombre("Revisar solicitud del cliente");

        ActividadRespuestaDto editada = actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, dto, USERNAME);

        assertThat(editada.getNombre()).isEqualTo("Revisar solicitud del cliente");
    }

    @Test
    void elEditorEditaActividades() {
        autenticar(RolUsuario.EDITOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);
        existeLaLane(LANE_ID, "General");
        EditarActividadDto dto = formularioEdicion();
        dto.setTipo(TipoActividad.TAREA_SISTEMA);

        actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, dto, USERNAME);

        assertThat(actividadGuardada().getTipo()).isEqualTo(TipoActividad.TAREA_SISTEMA);
    }

    @Test
    void elUsuarioDeSoloLecturaNoEditaActividades() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, formularioEdicion(), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("Solo un administrador o editor puede crear o modificar actividades");

        verify(actividadRepository, never()).save(any(Actividad.class));
    }

    @Test
    void editarNormalizaYCambiaElNombre() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);
        existeLaLane(LANE_ID, "General");
        EditarActividadDto dto = formularioEdicion();
        dto.setNombre("  Revisar solicitud del cliente  ");

        actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, dto, USERNAME);

        assertThat(actividadGuardada().getNombre()).isEqualTo("Revisar solicitud del cliente");
    }

    @Test
    void editarCambiaLaLaneResponsable() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);
        existeLaLane(LANE_DESTINO_ID, "Cartera");
        EditarActividadDto dto = formularioEdicion();
        dto.setLaneId(LANE_DESTINO_ID);

        ActividadRespuestaDto editada = actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, dto, USERNAME);

        assertThat(actividadGuardada().getLane().getId()).isEqualTo(LANE_DESTINO_ID);
        assertThat(editada.getLaneId()).isEqualTo(LANE_DESTINO_ID);
        assertThat(editada.getLaneNombre()).isEqualTo("Cartera");
    }

    @Test
    void editarNoModificaLaPosicionDeLaActividad() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);
        existeLaLane(LANE_DESTINO_ID, "Cartera");
        EditarActividadDto dto = formularioEdicion();
        dto.setLaneId(LANE_DESTINO_ID);

        actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, dto, USERNAME);

        Actividad guardada = actividadGuardada();
        assertThat(guardada.getPosicionX()).isEqualTo(120);
        assertThat(guardada.getPosicionY()).isEqualTo(40);
    }

    @Test
    void editarSinCambiosNoGuardaNiRegistraHistorial() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);
        existeLaLane(LANE_ID, "General");

        ActividadRespuestaDto sinCambios = actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, formularioEdicion(),
                USERNAME);

        assertThat(sinCambios.getNombre()).isEqualTo("Revisar solicitud");
        verify(actividadRepository, never()).save(any(Actividad.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void editarRegistraSoloLosCamposRealmenteModificados() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeLaActividad(proceso(EMPRESA_PROPIA, false), lane(LANE_ID, "General"), true);
        existeLaLane(LANE_DESTINO_ID, "Cartera");
        EditarActividadDto dto = formularioEdicion();
        dto.setLaneId(LANE_DESTINO_ID);

        actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, dto, USERNAME);

        String cambios = historialGuardado().getCambiosRealizados();
        assertThat(cambios).isEqualTo("actividad 'Revisar solicitud': lane: 'General' -> 'Cartera'");
        assertThat(cambios).doesNotContain("nombre").doesNotContain("tipo");
    }

    @Test
    void editarRegistraTodosLosCamposCuandoCambianJuntos() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeLaActividad(proceso(EMPRESA_PROPIA, false), lane(LANE_ID, "General"), true);
        existeLaLane(LANE_DESTINO_ID, "Cartera");
        EditarActividadDto dto = new EditarActividadDto("Validar solicitud", TipoActividad.TAREA_SISTEMA,
                LANE_DESTINO_ID);

        actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, dto, USERNAME);

        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("actividad 'Revisar solicitud': nombre: 'Revisar solicitud' -> 'Validar solicitud'; "
                        + "tipo: 'TAREA_USUARIO' -> 'TAREA_SISTEMA'; lane: 'General' -> 'Cartera'");
    }

    @Test
    void editarRechazaUnNombreYaUsadoPorOtraActividadDelProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);
        when(actividadRepository.existsByProcesoIdAndNombreIgnoreCase(PROCESO_ID, "Validar solicitud"))
                .thenReturn(true);
        EditarActividadDto dto = formularioEdicion();
        dto.setNombre("Validar solicitud");

        assertThatThrownBy(() -> actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, dto, USERNAME))
                .isInstanceOf(NombreActividadDuplicadoException.class)
                .hasMessage(NOMBRE_DUPLICADO);

        verify(actividadRepository, never()).save(any(Actividad.class));
    }

    @Test
    void editarNoBuscaDuplicadosCuandoElNombreNoCambia() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);
        existeLaLane(LANE_DESTINO_ID, "Cartera");
        EditarActividadDto dto = formularioEdicion();
        dto.setLaneId(LANE_DESTINO_ID);

        actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, dto, USERNAME);

        verify(actividadRepository, never()).existsByProcesoIdAndNombreIgnoreCase(anyLong(), eq("Revisar solicitud"));
    }

    @Test
    void editarRechazaUnaActividadYaEliminada() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), false);

        assertThatThrownBy(() -> actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, formularioEdicion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("La actividad ya fue eliminada");

        verify(actividadRepository, never()).save(any(Actividad.class));
    }

    @Test
    void editarRechazaUnaActividadQueNoEsDelProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        when(actividadRepository.findByIdAndProcesoId(ACTIVIDAD_ID, PROCESO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, formularioEdicion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("La actividad no existe en este proceso");
    }

    @Test
    void editarRechazaUnProcesoEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, true);

        assertThatThrownBy(() -> actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, formularioEdicion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");

        verify(actividadRepository, never()).save(any(Actividad.class));
    }

    @Test
    void editarRechazaUnProcesoDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, formularioEdicion(), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");
    }

    @Test
    void editarRechazaUnaLaneQueNoPerteneceAlPoolDelProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);
        when(laneRepository.findByIdAndPoolId(LANE_DESTINO_ID, POOL_ID)).thenReturn(Optional.empty());
        EditarActividadDto dto = formularioEdicion();
        dto.setLaneId(LANE_DESTINO_ID);

        assertThatThrownBy(() -> actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, dto, USERNAME))
                .isInstanceOf(LaneNoValidaException.class)
                .hasMessage(LANE_INVALIDA);

        verify(actividadRepository, never()).save(any(Actividad.class));
    }

    @Test
    void elAdministradorEliminaActividades() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);

        ActividadRespuestaDto eliminada = actividadService.eliminar(PROCESO_ID, ACTIVIDAD_ID, USERNAME);

        assertThat(eliminada.isActivo()).isFalse();
        assertThat(actividadGuardada().isActivo()).isFalse();
    }

    @Test
    void elEditorNoEliminaActividades() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> actividadService.eliminar(PROCESO_ID, ACTIVIDAD_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("Solo un administrador puede eliminar actividades");

        verify(actividadRepository, never()).save(any(Actividad.class));
    }

    @Test
    void elUsuarioDeSoloLecturaNoEliminaActividades() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> actividadService.eliminar(PROCESO_ID, ACTIVIDAD_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("Solo un administrador puede eliminar actividades");
    }

    @Test
    void eliminarNuncaBorraLaFilaDeLaBaseDeDatos() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        Actividad actividad = existeLaActividad(proceso, lane(LANE_ID, "General"), true);

        actividadService.eliminar(PROCESO_ID, ACTIVIDAD_ID, USERNAME);

        assertThat(actividad.getId()).isEqualTo(ACTIVIDAD_ID);
        verify(actividadRepository, never()).delete(any(Actividad.class));
        verify(actividadRepository, never()).deleteById(anyLong());
        verify(actividadRepository, never()).deleteAll();
    }

    @Test
    void eliminarRegistraLaEliminacionEnElHistorialDelProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);

        actividadService.eliminar(PROCESO_ID, ACTIVIDAD_ID, USERNAME);

        HistorialProceso historial = historialGuardado();
        assertThat(historial.getProceso()).isSameAs(proceso);
        assertThat(historial.getCambiosRealizados()).isEqualTo("actividad eliminada: 'Revisar solicitud'");
        assertThat(historial.getEstadoAnterior()).isEqualTo(EstadoProceso.BORRADOR.name());
    }

    @Test
    void unaSegundaEliminacionSeRechazaComoRecursoInexistente() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), false);

        assertThatThrownBy(() -> actividadService.eliminar(PROCESO_ID, ACTIVIDAD_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("La actividad ya fue eliminada");

        verify(actividadRepository, never()).save(any(Actividad.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void eliminarRechazaUnProcesoEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, true);

        assertThatThrownBy(() -> actividadService.eliminar(PROCESO_ID, ACTIVIDAD_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");

        verify(actividadRepository, never()).save(any(Actividad.class));
    }

    @Test
    void eliminarRechazaUnProcesoDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> actividadService.eliminar(PROCESO_ID, ACTIVIDAD_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");
    }

    @Test
    void obtenerParaEliminarDevuelveLaActividadSinTocarla() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);

        ActividadRespuestaDto actividad = actividadService.obtenerParaEliminar(PROCESO_ID, ACTIVIDAD_ID, USERNAME);

        assertThat(actividad.getNombre()).isEqualTo("Revisar solicitud");
        assertThat(actividad.isActivo()).isTrue();
        verify(actividadRepository, never()).save(any(Actividad.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void obtenerParaEliminarExigeRolDeAdministrador() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> actividadService.obtenerParaEliminar(PROCESO_ID, ACTIVIDAD_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("Solo un administrador puede eliminar actividades");
    }

    @Test
    void obtenerDevuelveLaActividadVigenteDelProceso() {
        autenticar(RolUsuario.SOLO_LECTURA);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);

        ActividadRespuestaDto actividad = actividadService.obtener(PROCESO_ID, ACTIVIDAD_ID, USERNAME);

        assertThat(actividad.getId()).isEqualTo(ACTIVIDAD_ID);
        assertThat(actividad.getLaneId()).isEqualTo(LANE_ID);
    }

    @Test
    void obtenerRechazaUnaActividadEliminada() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), false);

        assertThatThrownBy(() -> actividadService.obtener(PROCESO_ID, ACTIVIDAD_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("La actividad ya fue eliminada");
    }

    @Test
    void consultarActivasDevuelveSoloLasActividadesVigentesDelProceso() {
        autenticar(RolUsuario.SOLO_LECTURA);
        Proceso proceso = existeElProcesoActivo();
        when(actividadRepository.activasDelProceso(PROCESO_ID))
                .thenReturn(List.of(actividad(proceso, lane(LANE_ID, "General"), true)));

        List<ActividadRespuestaDto> activas = actividadService.consultarActivas(PROCESO_ID, USERNAME);

        assertThat(activas).hasSize(1);
        assertThat(activas.get(0).getNombre()).isEqualTo("Revisar solicitud");
        assertThat(activas.get(0).getLaneNombre()).isEqualTo("General");
        verify(actividadRepository).activasDelProceso(PROCESO_ID);
    }

    @Test
    void consultarActivasSiguePermitidaEnUnProcesoEliminado() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_PROPIA, true);
        when(actividadRepository.activasDelProceso(PROCESO_ID)).thenReturn(List.of());

        assertThat(actividadService.consultarActivas(PROCESO_ID, USERNAME)).isEmpty();
    }

    @Test
    void consultarActivasRechazaUnProcesoDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> actividadService.consultarActivas(PROCESO_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");
    }

    @Test
    void lanesDelProcesoDevuelveLasBandasDeSuPool() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProcesoActivo();
        when(laneRepository.findByPoolIdOrderByIdAsc(POOL_ID))
                .thenReturn(List.of(lane(LANE_ID, "General"), lane(LANE_DESTINO_ID, "Cartera")));

        List<LaneRespuestaDto> lanes = actividadService.lanesDelProceso(PROCESO_ID, USERNAME);

        assertThat(lanes).extracting(LaneRespuestaDto::getNombre).containsExactly("General", "Cartera");
        assertThat(lanes).extracting(LaneRespuestaDto::getId).containsExactly(LANE_ID, LANE_DESTINO_ID);
    }

    @Test
    void lanesDelProcesoRechazaUnProcesoDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> actividadService.lanesDelProceso(PROCESO_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");
    }

    @Test
    void editarNoTocaLasConexionesDeLaActividad() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        Actividad actividad = existeLaActividad(proceso, lane(LANE_ID, "General"), true);
        existeLaLane(LANE_ID, "General");
        EditarActividadDto dto = formularioEdicion();
        dto.setNombre("Validar solicitud");

        actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, dto, USERNAME);

        assertThat(actividad.getId()).isEqualTo(ACTIVIDAD_ID);
        verifyNoInteractions(conexionesService);
    }

    @Test
    void cambiarDeLaneNoTocaLasConexionesDeLaActividad() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        Actividad actividad = existeLaActividad(proceso, lane(LANE_ID, "General"), true);
        existeLaLane(LANE_DESTINO_ID, "Cartera");
        EditarActividadDto dto = formularioEdicion();
        dto.setLaneId(LANE_DESTINO_ID);

        actividadService.editar(PROCESO_ID, ACTIVIDAD_ID, dto, USERNAME);

        assertThat(actividad.getId()).isEqualTo(ACTIVIDAD_ID);
        assertThat(actividad.getLane().getId()).isEqualTo(LANE_DESTINO_ID);
        verifyNoInteractions(conexionesService);
    }

    @Test
    void eliminarDesactivaLosArcosConectadosALaActividad() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);
        when(conexionesService.desactivarConectadosA(proceso, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID))
                .thenReturn(List.of(new Arco(), new Arco()));

        ActividadRespuestaDto eliminada = actividadService.eliminar(PROCESO_ID, ACTIVIDAD_ID, USERNAME);

        assertThat(eliminada.getArcosDesactivados()).isEqualTo(2);
        verify(conexionesService).desactivarConectadosA(proceso, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID);
    }

    @Test
    void eliminarDejaLasConexionesAfectadasEnElHistorial() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);
        when(conexionesService.desactivarConectadosA(proceso, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID))
                .thenReturn(List.of(new Arco()));

        actividadService.eliminar(PROCESO_ID, ACTIVIDAD_ID, USERNAME);

        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("actividad eliminada: 'Revisar solicitud'; arcos desactivados: 1");
    }

    @Test
    void eliminarSinArcosConectadosNoAgregaNadaAlHistorial() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);

        ActividadRespuestaDto eliminada = actividadService.eliminar(PROCESO_ID, ACTIVIDAD_ID, USERNAME);

        assertThat(eliminada.getArcosDesactivados()).isZero();
        assertThat(historialGuardado().getCambiosRealizados()).isEqualTo("actividad eliminada: 'Revisar solicitud'");
    }

    @Test
    void eliminarDevuelveLaAdvertenciaDeVecinosDesconectados() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existeLaActividad(proceso, lane(LANE_ID, "General"), true);
        List<Arco> desactivados = List.of(new Arco());
        when(conexionesService.desactivarConectadosA(proceso, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ID))
                .thenReturn(desactivados);
        when(conexionesService.advertenciasTrasDesactivar(proceso, desactivados, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_ID)).thenReturn(List.of("'Aprobar solicitud' quedó sin arcos de entrada"));

        ActividadRespuestaDto eliminada = actividadService.eliminar(PROCESO_ID, ACTIVIDAD_ID, USERNAME);

        assertThat(eliminada.getAdvertencias())
                .containsExactly("'Aprobar solicitud' quedó sin arcos de entrada");
    }
}
