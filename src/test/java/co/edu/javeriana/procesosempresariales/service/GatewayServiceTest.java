package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.Gateway;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CrearGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.EditarGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.CondicionArcoNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.ArcoRepository;
import co.edu.javeriana.procesosempresariales.repository.GatewayRepository;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class GatewayServiceTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final String HASH_CREDENCIAL = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final Long PROCESO_ID = 5L;
    private static final Long POOL_ID = 80L;
    private static final Long GATEWAY_ID = 12L;
    private static final Long ARCO_UNO = 61L;
    private static final Long ARCO_DOS = 62L;
    private static final Long ACTIVIDAD_UNO = 30L;
    private static final Long ACTIVIDAD_DOS = 31L;

    @Mock
    private GatewayRepository gatewayRepository;

    @Mock
    private ArcoRepository arcoRepository;

    @Mock
    private ActividadRepository actividadRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProcesoRepository procesoRepository;

    @Mock
    private HistorialProcesoRepository historialProcesoRepository;

    private GatewayService gatewayService;

    @BeforeEach
    void inicializar() {
        NodoFlujoResolver resolver = new NodoFlujoResolver(actividadRepository, gatewayRepository);
        ConexionesService conexiones = new ConexionesService(arcoRepository, resolver);
        UsuarioService usuarios = new UsuarioService(usuarioRepository, new ModelMapper(),
                new BCryptPasswordEncoder());
        AccesoProcesoService acceso = new AccesoProcesoService(usuarios, procesoRepository);
        gatewayService = new GatewayService(gatewayRepository, acceso,
                new HistorialProcesoService(historialProcesoRepository), conexiones, resolver);
    }

    private Empresa empresa(Long id) {
        return new Empresa(id, "Alpes Logistica", "900123456-7", "contacto@alpes.com");
    }

    private void autenticar(RolUsuario rol) {
        when(usuarioRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(new Usuario(1L, USERNAME, HASH_CREDENCIAL, rol, true,
                        empresa(EMPRESA_PROPIA))));
    }

    private Proceso proceso(Long empresaId, boolean eliminado) {
        return new Proceso(PROCESO_ID, "Ventas", "Proceso comercial", "Comercial", EstadoProceso.BORRADOR,
                empresa(empresaId), new Pool(POOL_ID, "Alpes Logistica", List.of()), eliminado);
    }

    private Proceso existeElProceso(Long empresaId, boolean eliminado) {
        Proceso proceso = proceso(empresaId, eliminado);
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.of(proceso));
        return proceso;
    }

    private Proceso existeElProcesoActivo() {
        return existeElProceso(EMPRESA_PROPIA, false);
    }

    private Gateway gateway(TipoGateway tipo, boolean activo) {
        return new Gateway(GATEWAY_ID, tipo, proceso(EMPRESA_PROPIA, false), 300, 120, activo);
    }

    private Gateway existeElGateway(TipoGateway tipo, boolean activo) {
        Gateway gateway = gateway(tipo, activo);
        when(gatewayRepository.findByIdAndProcesoId(GATEWAY_ID, PROCESO_ID)).thenReturn(Optional.of(gateway));
        return gateway;
    }

    private void devolverElGatewayGuardado() {
        when(gatewayRepository.save(any(Gateway.class))).thenAnswer(invocacion -> {
            Gateway guardado = invocacion.getArgument(0);
            guardado.setId(GATEWAY_ID);
            return guardado;
        });
    }

    private Arco saliente(Long id, Long destinoId, String condicion) {
        return new Arco(id, proceso(EMPRESA_PROPIA, false), TipoNodoFlujo.GATEWAY, GATEWAY_ID,
                TipoNodoFlujo.ACTIVIDAD, destinoId, null, condicion, true);
    }

    private List<Arco> existenLasSalidas(Arco... salidas) {
        List<Arco> lista = List.of(salidas);
        when(arcoRepository.findByProcesoIdAndOrigenTipoAndOrigenIdAndActivoTrueOrderByIdAsc(PROCESO_ID,
                TipoNodoFlujo.GATEWAY, GATEWAY_ID)).thenReturn(lista);
        return lista;
    }

    private Gateway gatewayGuardado() {
        ArgumentCaptor<Gateway> capturado = ArgumentCaptor.forClass(Gateway.class);
        verify(gatewayRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private HistorialProceso historialGuardado() {
        ArgumentCaptor<HistorialProceso> capturado = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private CrearGatewayDto formularioCreacion(TipoGateway tipo) {
        return new CrearGatewayDto(tipo, 300, 120);
    }

    private EditarGatewayDto formularioEdicion(TipoGateway tipo, Map<Long, String> condiciones) {
        return new EditarGatewayDto(tipo, condiciones);
    }

    private Map<Long, String> condiciones(Long arcoId, String condicion) {
        Map<Long, String> mapa = new LinkedHashMap<>();
        mapa.put(arcoId, condicion);
        return mapa;
    }

    @Test
    void elAdministradorCreaGateways() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        devolverElGatewayGuardado();

        GatewayRespuestaDto creado = gatewayService.crear(PROCESO_ID, formularioCreacion(TipoGateway.EXCLUSIVO),
                USERNAME);

        assertThat(creado.getId()).isEqualTo(GATEWAY_ID);
        assertThat(creado.getProcesoId()).isEqualTo(PROCESO_ID);
        assertThat(creado.getTipo()).isEqualTo(TipoGateway.EXCLUSIVO);
        assertThat(creado.isActivo()).isTrue();
    }

    @Test
    void elEditorCreaGateways() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        devolverElGatewayGuardado();

        gatewayService.crear(PROCESO_ID, formularioCreacion(TipoGateway.PARALELO), USERNAME);

        assertThat(gatewayGuardado().getTipo()).isEqualTo(TipoGateway.PARALELO);
        assertThat(gatewayGuardado().isActivo()).isTrue();
    }

    @Test
    void elUsuarioDeSoloLecturaNoCreaGateways() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> gatewayService.crear(PROCESO_ID, formularioCreacion(TipoGateway.EXCLUSIVO),
                USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(GatewayService.SIN_PERMISO_ESCRITURA);

        verify(gatewayRepository, never()).save(any(Gateway.class));
    }

    @Test
    void elTipoExclusivoSeDibujaConUnaEquis() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        devolverElGatewayGuardado();

        GatewayRespuestaDto creado = gatewayService.crear(PROCESO_ID, formularioCreacion(TipoGateway.EXCLUSIVO),
                USERNAME);

        assertThat(creado.getSimbolo()).isEqualTo("X");
        assertThat(creado.getEtiqueta()).isEqualTo("Gateway EXCLUSIVO #12");
    }

    @Test
    void elTipoParaleloSeDibujaConUnaSuma() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        devolverElGatewayGuardado();

        GatewayRespuestaDto creado = gatewayService.crear(PROCESO_ID, formularioCreacion(TipoGateway.PARALELO),
                USERNAME);

        assertThat(creado.getSimbolo()).isEqualTo("+");
    }

    @Test
    void elTipoInclusivoSeDibujaConUnCirculo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        devolverElGatewayGuardado();

        GatewayRespuestaDto creado = gatewayService.crear(PROCESO_ID, formularioCreacion(TipoGateway.INCLUSIVO),
                USERNAME);

        assertThat(creado.getSimbolo()).isEqualTo("O");
    }

    @Test
    void crearGuardaLaPosicionIndicada() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        devolverElGatewayGuardado();

        GatewayRespuestaDto creado = gatewayService.crear(PROCESO_ID, new CrearGatewayDto(TipoGateway.PARALELO, 420,
                260), USERNAME);

        assertThat(creado.getPosicionX()).isEqualTo(420);
        assertThat(creado.getPosicionY()).isEqualTo(260);
    }

    @Test
    void crearRechazaUnProcesoEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, true);

        assertThatThrownBy(() -> gatewayService.crear(PROCESO_ID, formularioCreacion(TipoGateway.EXCLUSIVO),
                USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");

        verify(gatewayRepository, never()).save(any(Gateway.class));
    }

    @Test
    void crearRechazaUnProcesoDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> gatewayService.crear(PROCESO_ID, formularioCreacion(TipoGateway.EXCLUSIVO),
                USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");

        verify(gatewayRepository, never()).save(any(Gateway.class));
    }

    @Test
    void crearRegistraElGatewayEnElHistorialDelProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProcesoActivo();
        devolverElGatewayGuardado();

        gatewayService.crear(PROCESO_ID, formularioCreacion(TipoGateway.EXCLUSIVO), USERNAME);

        HistorialProceso historial = historialGuardado();
        assertThat(historial.getProceso()).isSameAs(proceso);
        assertThat(historial.getCambiosRealizados()).isEqualTo("gateway creado: EXCLUSIVO #12 en (300, 120)");
        assertThat(historial.getEstadoAnterior()).isEqualTo(EstadoProceso.BORRADOR.name());
    }

    @Test
    void unGatewayReciencreadoAdvierteDeDivergenciaIncompleta() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        devolverElGatewayGuardado();

        GatewayRespuestaDto creado = gatewayService.crear(PROCESO_ID, formularioCreacion(TipoGateway.EXCLUSIVO),
                USERNAME);

        assertThat(creado.getAdvertencias())
                .containsExactly("Gateway EXCLUSIVO #12 tiene 0 arcos de salida: como divergencia necesita al"
                        + " menos dos.");
    }

    @Test
    void obtenerDevuelveElGatewayConSusAdvertencias() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.PARALELO, true);
        existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, null));

        GatewayRespuestaDto gateway = gatewayService.obtener(PROCESO_ID, GATEWAY_ID, USERNAME);

        assertThat(gateway.getTipo()).isEqualTo(TipoGateway.PARALELO);
        assertThat(gateway.getAdvertencias())
                .containsExactly("Gateway PARALELO #12 tiene 1 arco de salida: como divergencia necesita al"
                        + " menos dos.");
    }

    @Test
    void obtenerRechazaUnGatewayQueNoEsDeEsteProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        when(gatewayRepository.findByIdAndProcesoId(GATEWAY_ID, PROCESO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gatewayService.obtener(PROCESO_ID, GATEWAY_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(GatewayService.GATEWAY_NO_EXISTE);
    }

    @Test
    void obtenerRechazaUnGatewayInactivo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.EXCLUSIVO, false);

        assertThatThrownBy(() -> gatewayService.obtener(PROCESO_ID, GATEWAY_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(GatewayService.GATEWAY_ELIMINADO);
    }

    @Test
    void consultarActivosDevuelveLosGatewaysDibujables() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProcesoActivo();
        when(gatewayRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(gateway(TipoGateway.INCLUSIVO, true)));

        List<GatewayRespuestaDto> gateways = gatewayService.consultarActivos(PROCESO_ID, USERNAME);

        assertThat(gateways).hasSize(1);
        assertThat(gateways.get(0).getSimbolo()).isEqualTo("O");
        assertThat(gateways.get(0).getPosicionX()).isEqualTo(300);
    }

    @Test
    void advertenciasDelProcesoRecorreLosGatewaysActivos() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProcesoActivo();
        when(gatewayRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(gateway(TipoGateway.EXCLUSIVO, true)));
        existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, "monto > 100"));

        List<String> advertencias = gatewayService.advertenciasDelProceso(PROCESO_ID, USERNAME);

        assertThat(advertencias).containsExactly("Gateway EXCLUSIVO #12 tiene 1 arco de salida: como divergencia"
                + " necesita al menos dos.");
    }

    @Test
    void editarCambiaElTipoYLoRegistraEnElHistorial() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        Gateway gateway = existeElGateway(TipoGateway.EXCLUSIVO, true);
        existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, "monto > 100"));

        GatewayRespuestaDto editado = gatewayService.editar(PROCESO_ID, GATEWAY_ID,
                formularioEdicion(TipoGateway.PARALELO, new LinkedHashMap<>()), USERNAME);

        assertThat(gateway.getTipo()).isEqualTo(TipoGateway.PARALELO);
        assertThat(editado.getSimbolo()).isEqualTo("+");
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("gateway #12: tipo: 'EXCLUSIVO' -> 'PARALELO'; condiciones eliminadas en 1 arco de"
                        + " salida");
    }

    @Test
    void pasarAParaleloEliminaLasCondicionesDeLosArcosSalientes() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.EXCLUSIVO, true);
        List<Arco> salientes = existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, "monto > 100"),
                saliente(ARCO_DOS, ACTIVIDAD_DOS, "monto <= 100"));

        gatewayService.editar(PROCESO_ID, GATEWAY_ID, formularioEdicion(TipoGateway.PARALELO, new LinkedHashMap<>()),
                USERNAME);

        assertThat(salientes).isNotEmpty().allMatch(arco -> arco.getCondicion() == null);
        verify(arcoRepository).saveAll(anyList());
        assertThat(historialGuardado().getCambiosRealizados()).contains("condiciones eliminadas en 2 arcos de salida");
    }

    @Test
    void pasarAExclusivoExigeCondicionEnCadaArcoSaliente() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.PARALELO, true);
        existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, null), saliente(ARCO_DOS, ACTIVIDAD_DOS, null));

        EditarGatewayDto dto = formularioEdicion(TipoGateway.EXCLUSIVO, condiciones(ARCO_UNO, "monto > 100"));

        assertThatThrownBy(() -> gatewayService.editar(PROCESO_ID, GATEWAY_ID, dto, USERNAME))
                .isInstanceOf(CondicionArcoNoValidaException.class)
                .hasMessage(GatewayService.SALIDA_SIN_CONDICION);

        verify(gatewayRepository, never()).save(any(Gateway.class));
    }

    @Test
    void pasarAExclusivoActualizaLasCondicionesRecibidas() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.PARALELO, true);
        List<Arco> salientes = existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, null),
                saliente(ARCO_DOS, ACTIVIDAD_DOS, null));
        Map<Long, String> condiciones = condiciones(ARCO_UNO, "monto > 100");
        condiciones.put(ARCO_DOS, "monto <= 100");

        GatewayRespuestaDto editado = gatewayService.editar(PROCESO_ID, GATEWAY_ID,
                formularioEdicion(TipoGateway.EXCLUSIVO, condiciones), USERNAME);

        assertThat(salientes.get(0).getCondicion()).isEqualTo("monto > 100");
        assertThat(salientes.get(1).getCondicion()).isEqualTo("monto <= 100");
        assertThat(editado.getTipo()).isEqualTo(TipoGateway.EXCLUSIVO);
        assertThat(historialGuardado().getCambiosRealizados())
                .contains("condiciones actualizadas en 2 arcos de salida");
    }

    @Test
    void pasarAExclusivoAdvierteQueLasCondicionesDebenSerExcluyentes() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.PARALELO, true);
        existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, "monto > 100"),
                saliente(ARCO_DOS, ACTIVIDAD_DOS, "monto <= 100"));

        GatewayRespuestaDto editado = gatewayService.editar(PROCESO_ID, GATEWAY_ID,
                formularioEdicion(TipoGateway.EXCLUSIVO, new LinkedHashMap<>()), USERNAME);

        assertThat(editado.getAdvertencias()).containsExactly("Gateway EXCLUSIVO #12: revisa que las condiciones de"
                + " sus salidas sean mutuamente excluyentes, porque solo una puede cumplirse.");
    }

    @Test
    void dosCondicionesIgualesSeSenalanComoNoExcluyentes() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.PARALELO, true);
        existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, "Monto  >  100"),
                saliente(ARCO_DOS, ACTIVIDAD_DOS, "monto > 100"));

        GatewayRespuestaDto editado = gatewayService.editar(PROCESO_ID, GATEWAY_ID,
                formularioEdicion(TipoGateway.EXCLUSIVO, new LinkedHashMap<>()), USERNAME);

        assertThat(editado.getAdvertencias()).containsExactly("Gateway EXCLUSIVO #12 repite la condición"
                + " 'monto > 100' en más de una salida: esas condiciones no son mutuamente excluyentes.");
    }

    @Test
    void pasarAInclusivoExigeCondicionEnCadaArcoSaliente() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.PARALELO, true);
        existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, null));

        EditarGatewayDto dto = formularioEdicion(TipoGateway.INCLUSIVO, new LinkedHashMap<>());

        assertThatThrownBy(() -> gatewayService.editar(PROCESO_ID, GATEWAY_ID, dto, USERNAME))
                .isInstanceOf(CondicionArcoNoValidaException.class)
                .hasMessage(GatewayService.SALIDA_SIN_CONDICION);
    }

    @Test
    void pasarAInclusivoAceptaLasCondicionesRecibidas() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        Gateway gateway = existeElGateway(TipoGateway.PARALELO, true);
        List<Arco> salientes = existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, null));

        gatewayService.editar(PROCESO_ID, GATEWAY_ID,
                formularioEdicion(TipoGateway.INCLUSIVO, condiciones(ARCO_UNO, "requiere visto bueno")), USERNAME);

        assertThat(gateway.getTipo()).isEqualTo(TipoGateway.INCLUSIVO);
        assertThat(salientes.get(0).getCondicion()).isEqualTo("requiere visto bueno");
    }

    @Test
    void editarSinCambiosNoGuardaNiRegistraHistorial() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.EXCLUSIVO, true);
        existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, "monto > 100"));

        GatewayRespuestaDto editado = gatewayService.editar(PROCESO_ID, GATEWAY_ID,
                formularioEdicion(TipoGateway.EXCLUSIVO, condiciones(ARCO_UNO, "monto > 100")), USERNAME);

        assertThat(editado.getTipo()).isEqualTo(TipoGateway.EXCLUSIVO);
        verify(gatewayRepository, never()).save(any(Gateway.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void editarSinSalidasNoBloqueaElCambioDeTipo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        Gateway gateway = existeElGateway(TipoGateway.PARALELO, true);

        GatewayRespuestaDto editado = gatewayService.editar(PROCESO_ID, GATEWAY_ID,
                formularioEdicion(TipoGateway.EXCLUSIVO, new LinkedHashMap<>()), USERNAME);

        assertThat(gateway.getTipo()).isEqualTo(TipoGateway.EXCLUSIVO);
        assertThat(editado.getAdvertencias())
                .containsExactly("Gateway EXCLUSIVO #12 tiene 0 arcos de salida: como divergencia necesita al"
                        + " menos dos.");
    }

    @Test
    void elUsuarioDeSoloLecturaNoEditaGateways() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> gatewayService.editar(PROCESO_ID, GATEWAY_ID,
                formularioEdicion(TipoGateway.PARALELO, new LinkedHashMap<>()), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(GatewayService.SIN_PERMISO_ESCRITURA);

        verify(gatewayRepository, never()).save(any(Gateway.class));
    }

    @Test
    void editarRechazaUnProcesoEliminado() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, true);

        assertThatThrownBy(() -> gatewayService.editar(PROCESO_ID, GATEWAY_ID,
                formularioEdicion(TipoGateway.PARALELO, new LinkedHashMap<>()), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");
    }

    @Test
    void editarRechazaUnGatewayDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> gatewayService.editar(PROCESO_ID, GATEWAY_ID,
                formularioEdicion(TipoGateway.PARALELO, new LinkedHashMap<>()), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");
    }

    @Test
    void pasarAParaleloIgnoraLasSalidasQueYaNoTienenCondicion() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        Gateway gateway = existeElGateway(TipoGateway.INCLUSIVO, true);
        List<Arco> salientes = existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, null),
                saliente(ARCO_DOS, ACTIVIDAD_DOS, "monto > 100"));

        gatewayService.editar(PROCESO_ID, GATEWAY_ID, formularioEdicion(TipoGateway.PARALELO, new LinkedHashMap<>()),
                USERNAME);

        assertThat(gateway.getTipo()).isEqualTo(TipoGateway.PARALELO);
        assertThat(salientes).isNotEmpty().allMatch(arco -> arco.getCondicion() == null);
        assertThat(historialGuardado().getCambiosRealizados()).contains("condiciones eliminadas en 1 arco de salida");
    }

    @Test
    void unaCondicionEnBlancoSeTrataComoCondicionAusente() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.PARALELO, true);
        existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, null));

        EditarGatewayDto dto = formularioEdicion(TipoGateway.EXCLUSIVO, condiciones(ARCO_UNO, "   "));

        assertThatThrownBy(() -> gatewayService.editar(PROCESO_ID, GATEWAY_ID, dto, USERNAME))
                .isInstanceOf(CondicionArcoNoValidaException.class)
                .hasMessage(GatewayService.SALIDA_SIN_CONDICION);
    }

    @Test
    void editarSoloLasCondicionesSinCambiarElTipoActualizaLosArcosSalientes() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        Gateway gateway = existeElGateway(TipoGateway.EXCLUSIVO, true);
        List<Arco> salientes = existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, "monto > 100"),
                saliente(ARCO_DOS, ACTIVIDAD_DOS, "monto <= 100"));
        Map<Long, String> condiciones = condiciones(ARCO_UNO, "monto > 500");
        condiciones.put(ARCO_DOS, "monto <= 500");

        GatewayRespuestaDto editado = gatewayService.editar(PROCESO_ID, GATEWAY_ID,
                formularioEdicion(TipoGateway.EXCLUSIVO, condiciones), USERNAME);

        assertThat(gateway.getTipo()).isEqualTo(TipoGateway.EXCLUSIVO);
        assertThat(editado.getTipo()).isEqualTo(TipoGateway.EXCLUSIVO);
        assertThat(salientes.get(0).getCondicion()).isEqualTo("monto > 500");
        assertThat(salientes.get(1).getCondicion()).isEqualTo("monto <= 500");
        verify(arcoRepository).saveAll(anyList());
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("gateway #12: condiciones actualizadas en 2 arcos de salida");
    }

    @Test
    void editarUnaSolaCondicionDejaIntactaLaOtraSalida() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElGateway(TipoGateway.INCLUSIVO, true);
        List<Arco> salientes = existenLasSalidas(saliente(ARCO_UNO, ACTIVIDAD_UNO, "requiere visto bueno"),
                saliente(ARCO_DOS, ACTIVIDAD_DOS, "requiere auditoria"));

        gatewayService.editar(PROCESO_ID, GATEWAY_ID,
                formularioEdicion(TipoGateway.INCLUSIVO, condiciones(ARCO_DOS, "requiere doble firma")), USERNAME);

        assertThat(salientes.get(0).getCondicion()).isEqualTo("requiere visto bueno");
        assertThat(salientes.get(1).getCondicion()).isEqualTo("requiere doble firma");
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("gateway #12: condiciones actualizadas en 1 arco de salida");
    }
}
