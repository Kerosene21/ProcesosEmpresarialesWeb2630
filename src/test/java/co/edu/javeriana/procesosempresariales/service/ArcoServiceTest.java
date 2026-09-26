package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
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

import co.edu.javeriana.procesosempresariales.domain.Actividad;
import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.Gateway;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.ArcoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearArcoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarArcoDto;
import co.edu.javeriana.procesosempresariales.dto.NodoFlujoDto;
import co.edu.javeriana.procesosempresariales.exception.ArcoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.CondicionArcoNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.FlujoEntrePoolsException;
import co.edu.javeriana.procesosempresariales.exception.NodoFlujoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.ArcoRepository;
import co.edu.javeriana.procesosempresariales.repository.GatewayRepository;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class ArcoServiceTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final String HASH_CREDENCIAL = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final Long PROCESO_ID = 5L;
    private static final Long POOL_ID = 80L;
    private static final Long LANE_ID = 11L;
    private static final Long ACTIVIDAD_ORIGEN = 30L;
    private static final Long ACTIVIDAD_DESTINO = 31L;
    private static final Long GATEWAY_ID = 12L;
    private static final Long ARCO_ID = 60L;
    private static final String NOMBRE_ORIGEN = "Revisar solicitud";
    private static final String NOMBRE_DESTINO = "Aprobar solicitud";
    private static final String ETIQUETA_GATEWAY = "Gateway EXCLUSIVO #12";

    @Mock
    private ArcoRepository arcoRepository;

    @Mock
    private ActividadRepository actividadRepository;

    @Mock
    private GatewayRepository gatewayRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProcesoRepository procesoRepository;

    @Mock
    private HistorialProcesoRepository historialProcesoRepository;

    private ArcoService arcoService;

    @BeforeEach
    void inicializar() {
        NodoFlujoResolver resolver = new NodoFlujoResolver(actividadRepository, gatewayRepository);
        ConexionesService conexiones = new ConexionesService(arcoRepository, resolver);
        UsuarioService usuarios = new UsuarioService(usuarioRepository, new ModelMapper(),
                new BCryptPasswordEncoder());
        AccesoProcesoService acceso = new AccesoProcesoService(usuarios, procesoRepository);
        arcoService = new ArcoService(arcoRepository, acceso, new HistorialProcesoService(historialProcesoRepository),
                resolver, conexiones, new GeometriaArco());
    }

    private Empresa empresa(Long id) {
        return new Empresa(id, "Alpes Logistica", "900123456-7", "contacto@alpes.com");
    }

    private void autenticar(RolUsuario rol) {
        when(usuarioRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(new Usuario(1L, USERNAME, HASH_CREDENCIAL, rol, true,
                        empresa(EMPRESA_PROPIA))));
    }

    private Pool pool() {
        return new Pool(POOL_ID, null, "Alpes Logistica", TipoPool.PROPIETARIO, 1, false, true, null, new ArrayList<>());
    }

    private Proceso proceso(Long empresaId, boolean eliminado) {
        return new Proceso(PROCESO_ID, "Ventas", "Proceso comercial", "Comercial", EstadoProceso.BORRADOR,
                empresa(empresaId), List.of(pool()), eliminado);
    }

    private Proceso existeElProceso(Long empresaId, boolean eliminado) {
        Proceso proceso = proceso(empresaId, eliminado);
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.of(proceso));
        return proceso;
    }

    private Proceso existeElProcesoActivo() {
        return existeElProceso(EMPRESA_PROPIA, false);
    }

    private Actividad actividad(Long id, String nombre, int x, int y, boolean activo) {
        return new Actividad(id, nombre, TipoActividad.TAREA_USUARIO, proceso(EMPRESA_PROPIA, false),
                new Lane(LANE_ID, "General", pool(), null, 1, true), x, y, activo);
    }

    private void existeLaActividad(Long id, String nombre, int x, int y, boolean activo) {
        when(actividadRepository.findByIdAndProcesoId(id, PROCESO_ID))
                .thenReturn(Optional.of(actividad(id, nombre, x, y, activo)));
    }

    private void existenLosDosExtremos() {
        existeLaActividad(ACTIVIDAD_ORIGEN, NOMBRE_ORIGEN, 100, 50, true);
        existeLaActividad(ACTIVIDAD_DESTINO, NOMBRE_DESTINO, 400, 50, true);
    }

    private void existeElGateway(TipoGateway tipo, boolean activo) {
        when(gatewayRepository.findByIdAndProcesoId(GATEWAY_ID, PROCESO_ID)).thenReturn(Optional.of(
                new Gateway(GATEWAY_ID, tipo, proceso(EMPRESA_PROPIA, false), pool(), 300, 40, activo)));
    }

    private void devolverElArcoGuardado() {
        when(arcoRepository.save(any(Arco.class))).thenAnswer(invocacion -> {
            Arco guardado = invocacion.getArgument(0);
            guardado.setId(ARCO_ID);
            return guardado;
        });
    }

    private Arco arcoGuardado() {
        ArgumentCaptor<Arco> capturado = ArgumentCaptor.forClass(Arco.class);
        verify(arcoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private HistorialProceso historialGuardado() {
        ArgumentCaptor<HistorialProceso> capturado = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private Arco arcoEntreActividades(boolean activo) {
        return new Arco(ARCO_ID, proceso(EMPRESA_PROPIA, false), TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ORIGEN,
                TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_DESTINO, null, null, activo);
    }

    private Arco existeElArco(boolean activo) {
        Arco arco = arcoEntreActividades(activo);
        when(arcoRepository.findByIdAndProcesoId(ARCO_ID, PROCESO_ID)).thenReturn(Optional.of(arco));
        return arco;
    }

    private CrearArcoDto formularioCreacion() {
        return new CrearArcoDto(TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ORIGEN, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_DESTINO, null, null);
    }

    private EditarArcoDto formularioEdicion() {
        return new EditarArcoDto(TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ORIGEN, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_DESTINO, null, null);
    }

    @Test
    void crearIdentificaAlUsuarioPorSuUsername() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existenLosDosExtremos();
        devolverElArcoGuardado();

        arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        verify(usuarioRepository).findByUsername(USERNAME);
    }

    @Test
    void crearRechazaUnUsuarioAutenticadoQueNoExiste() {
        when(usuarioRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El usuario autenticado no existe");

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void elAdministradorCreaArcos() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existenLosDosExtremos();
        devolverElArcoGuardado();

        ArcoRespuestaDto creado = arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        assertThat(creado.getId()).isEqualTo(ARCO_ID);
        assertThat(creado.getProcesoId()).isEqualTo(PROCESO_ID);
        assertThat(creado.getOrigenNombre()).isEqualTo(NOMBRE_ORIGEN);
        assertThat(creado.getDestinoNombre()).isEqualTo(NOMBRE_DESTINO);
        assertThat(creado.isActivo()).isTrue();
    }

    @Test
    void elEditorCreaArcos() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        existenLosDosExtremos();
        devolverElArcoGuardado();

        arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        assertThat(arcoGuardado().getOrigenId()).isEqualTo(ACTIVIDAD_ORIGEN);
        assertThat(arcoGuardado().getDestinoId()).isEqualTo(ACTIVIDAD_DESTINO);
    }

    @Test
    void elUsuarioDeSoloLecturaNoCreaArcos() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(ArcoService.SIN_PERMISO_ESCRITURA);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void crearRechazaUnOrigenIgualAlDestino() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeLaActividad(ACTIVIDAD_ORIGEN, NOMBRE_ORIGEN, 100, 50, true);

        CrearArcoDto dto = new CrearArcoDto(TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ORIGEN, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_ORIGEN, null, null);

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, dto, USERNAME))
                .isInstanceOf(NodoFlujoNoValidoException.class)
                .hasMessage(ArcoService.EXTREMOS_IGUALES);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void crearRechazaUnArcoQueYaExisteActivo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existenLosDosExtremos();
        when(arcoRepository.existsByProcesoIdAndOrigenTipoAndOrigenIdAndDestinoTipoAndDestinoIdAndActivoTrue(
                PROCESO_ID, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ORIGEN, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_DESTINO))
                .thenReturn(true);

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(ArcoDuplicadoException.class)
                .hasMessage(ArcoService.ARCO_DUPLICADO);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void crearRechazaUnNodoQueNoPerteneceAlProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        when(actividadRepository.findByIdAndProcesoId(ACTIVIDAD_ORIGEN, PROCESO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(NodoFlujoNoValidoException.class)
                .hasMessage(NodoFlujoResolver.NODO_NO_EXISTE);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void crearRechazaUnNodoInactivo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeLaActividad(ACTIVIDAD_ORIGEN, NOMBRE_ORIGEN, 100, 50, false);

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(NodoFlujoNoValidoException.class)
                .hasMessage(NodoFlujoResolver.NODO_INACTIVO);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void crearRechazaUnGatewayInactivoComoDestino() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeLaActividad(ACTIVIDAD_ORIGEN, NOMBRE_ORIGEN, 100, 50, true);
        existeElGateway(TipoGateway.PARALELO, false);

        CrearArcoDto dto = new CrearArcoDto(TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ORIGEN, TipoNodoFlujo.GATEWAY,
                GATEWAY_ID, null, null);

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, dto, USERNAME))
                .isInstanceOf(NodoFlujoNoValidoException.class)
                .hasMessage(NodoFlujoResolver.NODO_INACTIVO);
    }

    @Test
    void crearRechazaUnProcesoQueNoExiste() {
        autenticar(RolUsuario.ADMINISTRADOR);
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso no existe");
    }

    @Test
    void crearRechazaUnProcesoDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void crearRechazaUnProcesoEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, true);

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void crearRechazaUnEventoMientrasNoExistaEsaEntidad() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();

        CrearArcoDto dto = new CrearArcoDto(TipoNodoFlujo.EVENTO, 1L, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_DESTINO,
                null, null);

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, dto, USERNAME))
                .isInstanceOf(NodoFlujoNoValidoException.class)
                .hasMessage(NodoFlujoResolver.EVENTO_SIN_MODELO);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void crearNormalizaLaEtiquetaAntesDeGuardarla() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existenLosDosExtremos();
        devolverElArcoGuardado();
        CrearArcoDto dto = formularioCreacion();
        dto.setEtiqueta("   solicitud completa   ");

        arcoService.crear(PROCESO_ID, dto, USERNAME);

        assertThat(arcoGuardado().getEtiqueta()).isEqualTo("solicitud completa");
    }

    @Test
    void crearDejaLaCondicionNulaCuandoElOrigenNoEsGateway() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existenLosDosExtremos();
        devolverElArcoGuardado();

        arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        assertThat(arcoGuardado().getCondicion()).isNull();
    }

    @Test
    void crearRechazaUnaCondicionCuandoElOrigenNoEsGateway() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existenLosDosExtremos();
        CrearArcoDto dto = formularioCreacion();
        dto.setCondicion("monto > 100");

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, dto, USERNAME))
                .isInstanceOf(CondicionArcoNoValidaException.class)
                .hasMessage(ArcoService.CONDICION_SIN_GATEWAY);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void unGatewayExclusivoExigeCondicionEnSuArcoDeSalida() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.EXCLUSIVO, true);
        existeLaActividad(ACTIVIDAD_DESTINO, NOMBRE_DESTINO, 400, 50, true);

        CrearArcoDto dto = new CrearArcoDto(TipoNodoFlujo.GATEWAY, GATEWAY_ID, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_DESTINO, null, null);

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, dto, USERNAME))
                .isInstanceOf(CondicionArcoNoValidaException.class)
                .hasMessage(ArcoService.CONDICION_OBLIGATORIA);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void unGatewayInclusivoExigeCondicionEnSuArcoDeSalida() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.INCLUSIVO, true);
        existeLaActividad(ACTIVIDAD_DESTINO, NOMBRE_DESTINO, 400, 50, true);

        CrearArcoDto dto = new CrearArcoDto(TipoNodoFlujo.GATEWAY, GATEWAY_ID, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_DESTINO, null, null);

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, dto, USERNAME))
                .isInstanceOf(CondicionArcoNoValidaException.class)
                .hasMessage(ArcoService.CONDICION_OBLIGATORIA);
    }

    @Test
    void unGatewayExclusivoGuardaLaCondicionRecibida() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.EXCLUSIVO, true);
        existeLaActividad(ACTIVIDAD_DESTINO, NOMBRE_DESTINO, 400, 50, true);
        devolverElArcoGuardado();

        CrearArcoDto dto = new CrearArcoDto(TipoNodoFlujo.GATEWAY, GATEWAY_ID, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_DESTINO, null, "  monto > 100  ");

        ArcoRespuestaDto creado = arcoService.crear(PROCESO_ID, dto, USERNAME);

        assertThat(arcoGuardado().getCondicion()).isEqualTo("monto > 100");
        assertThat(creado.getOrigenNombre()).isEqualTo(ETIQUETA_GATEWAY);
    }

    @Test
    void unGatewayParaleloRechazaUnaCondicion() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.PARALELO, true);
        existeLaActividad(ACTIVIDAD_DESTINO, NOMBRE_DESTINO, 400, 50, true);

        CrearArcoDto dto = new CrearArcoDto(TipoNodoFlujo.GATEWAY, GATEWAY_ID, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_DESTINO, null, "monto > 100");

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, dto, USERNAME))
                .isInstanceOf(CondicionArcoNoValidaException.class)
                .hasMessage(ArcoService.CONDICION_EN_PARALELO);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void crearRegistraElArcoEnElHistorialDelProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        existenLosDosExtremos();
        devolverElArcoGuardado();

        arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        HistorialProceso historial = historialGuardado();
        assertThat(historial.getProceso()).isSameAs(proceso);
        assertThat(historial.getUsuario().getUsername()).isEqualTo(USERNAME);
        assertThat(historial.getCambiosRealizados())
                .isEqualTo("arco creado: 'Revisar solicitud' -> 'Aprobar solicitud'");
        assertThat(historial.getEstadoAnterior()).isEqualTo(EstadoProceso.BORRADOR.name());
    }

    @Test
    void crearCalculaLosExtremosVisualesDelArco() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existenLosDosExtremos();
        devolverElArcoGuardado();

        ArcoRespuestaDto creado = arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        assertThat(creado.getOrigenX()).isEqualTo(165);
        assertThat(creado.getOrigenY()).isEqualTo(73);
        assertThat(creado.getDestinoX()).isEqualTo(400);
        assertThat(creado.getDestinoY()).isEqualTo(73);
    }

    @Test
    void crearAdvierteCuandoElGatewayDeOrigenSeQuedaConUnaSolaSalida() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.EXCLUSIVO, true);
        existeLaActividad(ACTIVIDAD_DESTINO, NOMBRE_DESTINO, 400, 50, true);
        devolverElArcoGuardado();
        when(arcoRepository.findByProcesoIdAndOrigenTipoAndOrigenIdAndActivoTrueOrderByIdAsc(PROCESO_ID,
                TipoNodoFlujo.GATEWAY, GATEWAY_ID)).thenReturn(List.of(arcoEntreActividades(true)));

        CrearArcoDto dto = new CrearArcoDto(TipoNodoFlujo.GATEWAY, GATEWAY_ID, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_DESTINO, null, "monto > 100");

        ArcoRespuestaDto creado = arcoService.crear(PROCESO_ID, dto, USERNAME);

        assertThat(creado.getAdvertencias()).anyMatch(texto -> texto.contains("como divergencia necesita al menos"));
    }

    @Test
    void obtenerDevuelveElArcoDelProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElArco(true);
        existenLosDosExtremos();

        ArcoRespuestaDto arco = arcoService.obtener(PROCESO_ID, ARCO_ID, USERNAME);

        assertThat(arco.getId()).isEqualTo(ARCO_ID);
        assertThat(arco.getOrigenTipo()).isEqualTo(TipoNodoFlujo.ACTIVIDAD);
        assertThat(arco.getDestinoNombre()).isEqualTo(NOMBRE_DESTINO);
    }

    @Test
    void obtenerRechazaUnArcoQueNoEsDeEsteProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        when(arcoRepository.findByIdAndProcesoId(ARCO_ID, PROCESO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> arcoService.obtener(PROCESO_ID, ARCO_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(ArcoService.ARCO_NO_EXISTE);
    }

    @Test
    void consultarActivosDevuelveLosArcosDibujablesDelProceso() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_PROPIA, true);
        when(arcoRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(arcoEntreActividades(true)));
        when(actividadRepository.activasDelProceso(PROCESO_ID))
                .thenReturn(List.of(actividad(ACTIVIDAD_ORIGEN, NOMBRE_ORIGEN, 100, 50, true),
                        actividad(ACTIVIDAD_DESTINO, NOMBRE_DESTINO, 400, 50, true)));

        List<ArcoRespuestaDto> arcos = arcoService.consultarActivos(PROCESO_ID, USERNAME);

        assertThat(arcos).hasSize(1);
        assertThat(arcos.get(0).getOrigenNombre()).isEqualTo(NOMBRE_ORIGEN);
        assertThat(arcos.get(0).getDestinoNombre()).isEqualTo(NOMBRE_DESTINO);
    }

    @Test
    void nodosDelProcesoDevuelveActividadesYGatewaysActivos() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        when(actividadRepository.activasDelProceso(PROCESO_ID))
                .thenReturn(List.of(actividad(ACTIVIDAD_ORIGEN, NOMBRE_ORIGEN, 100, 50, true)));
        when(gatewayRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID)).thenReturn(
                List.of(new Gateway(GATEWAY_ID, TipoGateway.EXCLUSIVO, proceso(EMPRESA_PROPIA, false), pool(), 300, 40,
                        true)));

        List<NodoFlujoDto> nodos = arcoService.nodosDelProceso(PROCESO_ID, USERNAME);

        assertThat(nodos).extracting(NodoFlujoDto::getNombre).containsExactly(NOMBRE_ORIGEN, ETIQUETA_GATEWAY);
        assertThat(nodos).extracting(NodoFlujoDto::getTipo)
                .containsExactly(TipoNodoFlujo.ACTIVIDAD, TipoNodoFlujo.GATEWAY);
    }

    @Test
    void salientesDeDevuelveLosArcosQueNacenDelNodo() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        existenLosDosExtremos();
        when(arcoRepository.findByProcesoIdAndOrigenTipoAndOrigenIdAndActivoTrueOrderByIdAsc(PROCESO_ID,
                TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ORIGEN)).thenReturn(List.of(arcoEntreActividades(true)));

        List<ArcoRespuestaDto> salientes = arcoService.salientesDe(PROCESO_ID, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_ORIGEN, USERNAME);

        assertThat(salientes).hasSize(1);
        assertThat(salientes.get(0).getDestinoNombre()).isEqualTo(NOMBRE_DESTINO);
    }

    @Test
    void editarCambiaElOrigenConservandoElIdentificador() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        Arco arco = existeElArco(true);
        existenLosDosExtremos();
        existeElGateway(TipoGateway.PARALELO, true);

        EditarArcoDto dto = new EditarArcoDto(TipoNodoFlujo.GATEWAY, GATEWAY_ID, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_DESTINO, null, null);

        ArcoRespuestaDto editado = arcoService.editar(PROCESO_ID, ARCO_ID, dto, USERNAME);

        assertThat(editado.getId()).isEqualTo(ARCO_ID);
        assertThat(arco.getOrigenTipo()).isEqualTo(TipoNodoFlujo.GATEWAY);
        assertThat(arco.getOrigenId()).isEqualTo(GATEWAY_ID);
        verify(arcoRepository).save(arco);
    }

    @Test
    void editarCambiaElDestino() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        Arco arco = existeElArco(true);
        existenLosDosExtremos();
        existeLaActividad(32L, "Archivar solicitud", 700, 50, true);

        EditarArcoDto dto = new EditarArcoDto(TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ORIGEN, TipoNodoFlujo.ACTIVIDAD,
                32L, null, null);

        arcoService.editar(PROCESO_ID, ARCO_ID, dto, USERNAME);

        assertThat(arco.getDestinoId()).isEqualTo(32L);
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("arco 'Revisar solicitud' -> 'Aprobar solicitud': destino: 'Aprobar solicitud'"
                        + " -> 'Archivar solicitud'");
    }

    @Test
    void editarCambiaLaEtiqueta() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        Arco arco = existeElArco(true);
        existenLosDosExtremos();

        EditarArcoDto dto = formularioEdicion();
        dto.setEtiqueta("solicitud completa");

        arcoService.editar(PROCESO_ID, ARCO_ID, dto, USERNAME);

        assertThat(arco.getEtiqueta()).isEqualTo("solicitud completa");
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("arco 'Revisar solicitud' -> 'Aprobar solicitud': etiqueta: '' -> 'solicitud completa'");
    }

    @Test
    void editarCambiaLaCondicionCuandoElOrigenEsUnGatewayExclusivo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        Arco arco = new Arco(ARCO_ID, proceso(EMPRESA_PROPIA, false), TipoNodoFlujo.GATEWAY, GATEWAY_ID,
                TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_DESTINO, null, "monto > 100", true);
        when(arcoRepository.findByIdAndProcesoId(ARCO_ID, PROCESO_ID)).thenReturn(Optional.of(arco));
        existeElGateway(TipoGateway.EXCLUSIVO, true);
        existeLaActividad(ACTIVIDAD_DESTINO, NOMBRE_DESTINO, 400, 50, true);

        EditarArcoDto dto = new EditarArcoDto(TipoNodoFlujo.GATEWAY, GATEWAY_ID, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_DESTINO, null, "monto > 500");

        arcoService.editar(PROCESO_ID, ARCO_ID, dto, USERNAME);

        assertThat(arco.getCondicion()).isEqualTo("monto > 500");
        assertThat(historialGuardado().getCambiosRealizados())
                .contains("condicion: 'monto > 100' -> 'monto > 500'");
    }

    @Test
    void editarSinCambiosNoGuardaNiRegistraHistorial() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElArco(true);
        existenLosDosExtremos();

        ArcoRespuestaDto editado = arcoService.editar(PROCESO_ID, ARCO_ID, formularioEdicion(), USERNAME);

        assertThat(editado.getId()).isEqualTo(ARCO_ID);
        verify(arcoRepository, never()).save(any(Arco.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void editarRechazaUnOrigenIgualAlDestino() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElArco(true);
        existeLaActividad(ACTIVIDAD_ORIGEN, NOMBRE_ORIGEN, 100, 50, true);

        EditarArcoDto dto = new EditarArcoDto(TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ORIGEN, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_ORIGEN, null, null);

        assertThatThrownBy(() -> arcoService.editar(PROCESO_ID, ARCO_ID, dto, USERNAME))
                .isInstanceOf(NodoFlujoNoValidoException.class)
                .hasMessage(ArcoService.EXTREMOS_IGUALES);
    }

    @Test
    void editarRechazaUnDuplicadoConOtroArcoActivo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElArco(true);
        existenLosDosExtremos();
        when(arcoRepository.existsByProcesoIdAndOrigenTipoAndOrigenIdAndDestinoTipoAndDestinoIdAndActivoTrueAndIdNot(
                PROCESO_ID, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ORIGEN, TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_DESTINO,
                ARCO_ID)).thenReturn(true);

        assertThatThrownBy(() -> arcoService.editar(PROCESO_ID, ARCO_ID, formularioEdicion(), USERNAME))
                .isInstanceOf(ArcoDuplicadoException.class)
                .hasMessage(ArcoService.ARCO_DUPLICADO);
    }

    @Test
    void elUsuarioDeSoloLecturaNoEditaArcos() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> arcoService.editar(PROCESO_ID, ARCO_ID, formularioEdicion(), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(ArcoService.SIN_PERMISO_ESCRITURA);
    }

    @Test
    void editarRechazaUnArcoYaEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElArco(false);

        assertThatThrownBy(() -> arcoService.editar(PROCESO_ID, ARCO_ID, formularioEdicion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(ArcoService.ARCO_ELIMINADO);
    }

    @Test
    void editarRechazaUnProcesoEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, true);

        assertThatThrownBy(() -> arcoService.editar(PROCESO_ID, ARCO_ID, formularioEdicion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");
    }

    @Test
    void elAdministradorEliminaArcosDeFormaLogica() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        Arco arco = existeElArco(true);
        existenLosDosExtremos();

        ArcoRespuestaDto eliminado = arcoService.eliminar(PROCESO_ID, ARCO_ID, USERNAME);

        assertThat(arco.isActivo()).isFalse();
        assertThat(arco.getId()).isEqualTo(ARCO_ID);
        assertThat(eliminado.isActivo()).isFalse();
        verify(arcoRepository, never()).delete(any(Arco.class));
        verify(arcoRepository, never()).deleteById(anyLong());
        verify(arcoRepository, never()).deleteAll();
    }

    @Test
    void elEditorNoEliminaArcos() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> arcoService.eliminar(PROCESO_ID, ARCO_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(ArcoService.SIN_PERMISO_ELIMINAR);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void elUsuarioDeSoloLecturaNoEliminaArcos() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> arcoService.eliminar(PROCESO_ID, ARCO_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(ArcoService.SIN_PERMISO_ELIMINAR);
    }

    @Test
    void eliminarRegistraLaEliminacionEnElHistorial() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElArco(true);
        existenLosDosExtremos();

        arcoService.eliminar(PROCESO_ID, ARCO_ID, USERNAME);

        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("arco eliminado: 'Revisar solicitud' -> 'Aprobar solicitud'");
    }

    @Test
    void unaSegundaEliminacionSeRechazaComoRecursoInexistente() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElArco(false);

        assertThatThrownBy(() -> arcoService.eliminar(PROCESO_ID, ARCO_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(ArcoService.ARCO_ELIMINADO);

        verify(arcoRepository, never()).save(any(Arco.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void eliminarAdvierteQueLosExtremosQuedanDesconectados() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElArco(true);
        existenLosDosExtremos();

        ArcoRespuestaDto eliminado = arcoService.eliminar(PROCESO_ID, ARCO_ID, USERNAME);

        assertThat(eliminado.getAdvertencias()).containsExactly(
                "'Revisar solicitud' quedó sin arcos de salida",
                "'Aprobar solicitud' quedó sin arcos de entrada");
    }

    @Test
    void obtenerParaEliminarExigeSerAdministradorYNoModificaNada() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> arcoService.obtenerParaEliminar(PROCESO_ID, ARCO_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(ArcoService.SIN_PERMISO_ELIMINAR);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void obtenerParaEliminarAdelantaLaAdvertenciaDeDesconexion() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        Arco arco = existeElArco(true);
        existenLosDosExtremos();
        when(arcoRepository.findByProcesoIdAndOrigenTipoAndOrigenIdAndActivoTrueOrderByIdAsc(PROCESO_ID,
                TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ORIGEN)).thenReturn(List.of(arco));
        when(arcoRepository.findByProcesoIdAndDestinoTipoAndDestinoIdAndActivoTrueOrderByIdAsc(PROCESO_ID,
                TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_DESTINO)).thenReturn(List.of(arco));

        ArcoRespuestaDto confirmacion = arcoService.obtenerParaEliminar(PROCESO_ID, ARCO_ID, USERNAME);

        assertThat(confirmacion.isActivo()).isTrue();
        assertThat(confirmacion.getAdvertencias()).containsExactly(
                "'Revisar solicitud' quedó sin arcos de salida",
                "'Aprobar solicitud' quedó sin arcos de entrada");
        verify(arcoRepository, never()).save(any(Arco.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void eliminarNoAdviertePorUnNodoQueConservaOtrasConexiones() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElArco(true);
        existenLosDosExtremos();
        when(arcoRepository.findByProcesoIdAndOrigenTipoAndOrigenIdAndActivoTrueOrderByIdAsc(PROCESO_ID,
                TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ORIGEN))
                .thenReturn(List.of(new Arco(77L, proceso(EMPRESA_PROPIA, false), TipoNodoFlujo.ACTIVIDAD,
                        ACTIVIDAD_ORIGEN, TipoNodoFlujo.ACTIVIDAD, 32L, null, null, true)));

        ArcoRespuestaDto eliminado = arcoService.eliminar(PROCESO_ID, ARCO_ID, USERNAME);

        assertThat(eliminado.getAdvertencias()).containsExactly("'Aprobar solicitud' quedó sin arcos de entrada");
    }

    @Test
    void editarAvisaTambienSobreElGatewayQueDejaDeSerOrigen() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        Arco arco = new Arco(ARCO_ID, proceso(EMPRESA_PROPIA, false), TipoNodoFlujo.GATEWAY, GATEWAY_ID,
                TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_DESTINO, null, "monto > 100", true);
        when(arcoRepository.findByIdAndProcesoId(ARCO_ID, PROCESO_ID)).thenReturn(Optional.of(arco));
        existeElGateway(TipoGateway.EXCLUSIVO, true);
        existenLosDosExtremos();

        EditarArcoDto dto = new EditarArcoDto(TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ORIGEN, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_DESTINO, null, null);

        ArcoRespuestaDto editado = arcoService.editar(PROCESO_ID, ARCO_ID, dto, USERNAME);

        assertThat(editado.getAdvertencias())
                .anyMatch(texto -> texto.startsWith(ETIQUETA_GATEWAY + " tiene 0 arcos de salida"));
    }

    @Test
    void editarNoRepiteLasAdvertenciasCuandoElGatewayDeOrigenNoCambia() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        Arco arco = new Arco(ARCO_ID, proceso(EMPRESA_PROPIA, false), TipoNodoFlujo.GATEWAY, GATEWAY_ID,
                TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_DESTINO, null, "monto > 100", true);
        when(arcoRepository.findByIdAndProcesoId(ARCO_ID, PROCESO_ID)).thenReturn(Optional.of(arco));
        existeElGateway(TipoGateway.EXCLUSIVO, true);
        existeLaActividad(ACTIVIDAD_DESTINO, NOMBRE_DESTINO, 400, 50, true);

        EditarArcoDto dto = new EditarArcoDto(TipoNodoFlujo.GATEWAY, GATEWAY_ID, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_DESTINO, "ruta principal", "monto > 100");

        ArcoRespuestaDto editado = arcoService.editar(PROCESO_ID, ARCO_ID, dto, USERNAME);

        assertThat(arco.getEtiqueta()).isEqualTo("ruta principal");
        assertThat(editado.getAdvertencias()).hasSize(1);
    }

    @Test
    void unArcoHaciaUnNodoDesaparecidoSeDescribePorSuReferencia() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProcesoActivo();
        when(arcoRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(arcoEntreActividades(true)));
        when(actividadRepository.findByIdAndProcesoId(ACTIVIDAD_ORIGEN, PROCESO_ID)).thenReturn(Optional.empty());
        when(actividadRepository.findByIdAndProcesoId(ACTIVIDAD_DESTINO, PROCESO_ID)).thenReturn(Optional.empty());

        List<ArcoRespuestaDto> arcos = arcoService.consultarActivos(PROCESO_ID, USERNAME);

        assertThat(arcos.get(0).getOrigenNombre()).isEqualTo("ACTIVIDAD #30");
        assertThat(arcos.get(0).getDestinoNombre()).isEqualTo("ACTIVIDAD #31");
        assertThat(arcos.get(0).getOrigenX()).isNull();
    }

    @Test
    void editarRechazaUnaCondicionCuandoElOrigenNoEsGateway() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElArco(true);
        existenLosDosExtremos();
        EditarArcoDto dto = formularioEdicion();
        dto.setCondicion("monto alto");

        assertThatThrownBy(() -> arcoService.editar(PROCESO_ID, ARCO_ID, dto, USERNAME))
                .isInstanceOf(CondicionArcoNoValidaException.class)
                .hasMessage(ArcoService.CONDICION_SIN_GATEWAY);

        verify(arcoRepository, never()).save(any(Arco.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void editarExigeCondicionCuandoElOrigenPasaASerUnGatewayExclusivo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElArco(true);
        existeElGateway(TipoGateway.EXCLUSIVO, true);
        existeLaActividad(ACTIVIDAD_DESTINO, NOMBRE_DESTINO, 400, 50, true);

        EditarArcoDto dto = new EditarArcoDto(TipoNodoFlujo.GATEWAY, GATEWAY_ID, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_DESTINO, null, null);

        assertThatThrownBy(() -> arcoService.editar(PROCESO_ID, ARCO_ID, dto, USERNAME))
                .isInstanceOf(CondicionArcoNoValidaException.class)
                .hasMessage(ArcoService.CONDICION_OBLIGATORIA);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void editarExigeCondicionCuandoElOrigenPasaASerUnGatewayInclusivo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElArco(true);
        existeElGateway(TipoGateway.INCLUSIVO, true);
        existeLaActividad(ACTIVIDAD_DESTINO, NOMBRE_DESTINO, 400, 50, true);

        EditarArcoDto dto = new EditarArcoDto(TipoNodoFlujo.GATEWAY, GATEWAY_ID, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_DESTINO, null, null);

        assertThatThrownBy(() -> arcoService.editar(PROCESO_ID, ARCO_ID, dto, USERNAME))
                .isInstanceOf(CondicionArcoNoValidaException.class)
                .hasMessage(ArcoService.CONDICION_OBLIGATORIA);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void editarRechazaUnaCondicionCuandoElOrigenEsUnGatewayParalelo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElArco(true);
        existeElGateway(TipoGateway.PARALELO, true);
        existeLaActividad(ACTIVIDAD_DESTINO, NOMBRE_DESTINO, 400, 50, true);

        EditarArcoDto dto = new EditarArcoDto(TipoNodoFlujo.GATEWAY, GATEWAY_ID, TipoNodoFlujo.ACTIVIDAD,
                ACTIVIDAD_DESTINO, null, "monto alto");

        assertThatThrownBy(() -> arcoService.editar(PROCESO_ID, ARCO_ID, dto, USERNAME))
                .isInstanceOf(CondicionArcoNoValidaException.class)
                .hasMessage(ArcoService.CONDICION_EN_PARALELO);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void editarLimpiaLaCondicionCuandoElOrigenDejaDeSerUnGateway() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        Arco arco = new Arco(ARCO_ID, proceso(EMPRESA_PROPIA, false), TipoNodoFlujo.GATEWAY, GATEWAY_ID,
                TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_DESTINO, null, "monto alto", true);
        when(arcoRepository.findByIdAndProcesoId(ARCO_ID, PROCESO_ID)).thenReturn(Optional.of(arco));
        existenLosDosExtremos();

        arcoService.editar(PROCESO_ID, ARCO_ID, formularioEdicion(), USERNAME);

        assertThat(arco.getCondicion()).isNull();
        assertThat(arco.getOrigenTipo()).isEqualTo(TipoNodoFlujo.ACTIVIDAD);
        assertThat(historialGuardado().getCambiosRealizados()).contains("condicion: 'monto alto' -> ''");
    }

    private Pool poolDelCliente() {
        return new Pool(90L, null, "Cliente", TipoPool.EXTERNO, 2, false, true, null, new ArrayList<>());
    }

    private void existeLaActividadEnLaLane(Long id, String nombre, Lane lane) {
        when(actividadRepository.findByIdAndProcesoId(id, PROCESO_ID)).thenReturn(Optional.of(new Actividad(id,
                nombre, TipoActividad.TAREA_USUARIO, proceso(EMPRESA_PROPIA, false), lane, 400, 50, true)));
    }

    @Test
    void unFlujoDeSecuenciaEntreLanesDistintasDelMismoPoolSePermite() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        existeLaActividad(ACTIVIDAD_ORIGEN, NOMBRE_ORIGEN, 100, 50, true);
        existeLaActividadEnLaLane(ACTIVIDAD_DESTINO, NOMBRE_DESTINO, new Lane(12L, "Cartera", pool(), null, 2, true));
        devolverElArcoGuardado();

        arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        assertThat(arcoGuardado().getDestinoId()).isEqualTo(ACTIVIDAD_DESTINO);
    }

    @Test
    void unFlujoDeSecuenciaEntreActividadesDePoolsDistintosSeRechaza() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        existeLaActividad(ACTIVIDAD_ORIGEN, NOMBRE_ORIGEN, 100, 50, true);
        existeLaActividadEnLaLane(ACTIVIDAD_DESTINO, NOMBRE_DESTINO,
                new Lane(21L, "Compras", poolDelCliente(), null, 1, true));

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(FlujoEntrePoolsException.class)
                .hasMessage("'" + NOMBRE_ORIGEN + "' y '" + NOMBRE_DESTINO + ArcoService.ENTRE_POOLS);

        verify(arcoRepository, never()).save(any(Arco.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void unFlujoDeSecuenciaDeActividadAGatewayDelMismoPoolSePermite() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        existeLaActividad(ACTIVIDAD_ORIGEN, NOMBRE_ORIGEN, 100, 50, true);
        existeElGateway(TipoGateway.EXCLUSIVO, true);
        devolverElArcoGuardado();

        arcoService.crear(PROCESO_ID, new CrearArcoDto(TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_ORIGEN,
                TipoNodoFlujo.GATEWAY, GATEWAY_ID, null, null), USERNAME);

        assertThat(arcoGuardado().getDestinoTipo()).isEqualTo(TipoNodoFlujo.GATEWAY);
    }

    @Test
    void unFlujoDeSecuenciaDeGatewayAActividadDeOtroPoolSeRechaza() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.EXCLUSIVO, true);
        existeLaActividadEnLaLane(ACTIVIDAD_DESTINO, NOMBRE_DESTINO,
                new Lane(21L, "Compras", poolDelCliente(), null, 1, true));

        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, new CrearArcoDto(TipoNodoFlujo.GATEWAY, GATEWAY_ID,
                TipoNodoFlujo.ACTIVIDAD, ACTIVIDAD_DESTINO, null, "monto alto"), USERNAME))
                .isInstanceOf(FlujoEntrePoolsException.class);

        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void editarUnArcoParaQueCruceDePoolSeRechaza() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        Arco arco = existeElArco(true);
        existeLaActividad(ACTIVIDAD_ORIGEN, NOMBRE_ORIGEN, 100, 50, true);
        existeLaActividadEnLaLane(ACTIVIDAD_DESTINO, NOMBRE_DESTINO,
                new Lane(21L, "Compras", poolDelCliente(), null, 1, true));

        assertThatThrownBy(() -> arcoService.editar(PROCESO_ID, ARCO_ID, formularioEdicion(), USERNAME))
                .isInstanceOf(FlujoEntrePoolsException.class);

        assertThat(arco.getDestinoId()).isEqualTo(ACTIVIDAD_DESTINO);
        verify(arcoRepository, never()).save(any(Arco.class));
    }

    @Test
    void unaEmpresaInvitadaConsultaLosArcosPeroNoLosCrea() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);
        when(procesoRepository.estaCompartidoCon(PROCESO_ID, EMPRESA_PROPIA)).thenReturn(true);
        when(arcoRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID)).thenReturn(List.of());

        assertThat(arcoService.consultarActivos(PROCESO_ID, USERNAME)).isEmpty();
        assertThatThrownBy(() -> arcoService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);
        verify(arcoRepository, never()).save(any(Arco.class));
    }
}
