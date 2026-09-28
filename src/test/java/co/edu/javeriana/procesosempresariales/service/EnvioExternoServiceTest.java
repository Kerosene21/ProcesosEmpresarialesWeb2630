package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.ComportamientoFallo;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EnvioExterno;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.Evento;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoDestinoExterno;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CrearEnvioExternoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarEnvioExternoDto;
import co.edu.javeriana.procesosempresariales.dto.EnvioExternoRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.EnvioExternoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.MensajeEntreMismoPoolException;
import co.edu.javeriana.procesosempresariales.exception.PoolMensajeNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.ArcoRepository;
import co.edu.javeriana.procesosempresariales.repository.EnvioExternoRepository;
import co.edu.javeriana.procesosempresariales.repository.EventoRepository;
import co.edu.javeriana.procesosempresariales.repository.GatewayRepository;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class EnvioExternoServiceTest {

    private static final String USERNAME = "editor@alpes.com";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final Long PROCESO_ID = 5L;
    private static final Long POOL_ALPES = 80L;
    private static final Long POOL_PASARELA = 90L;
    private static final Long POOL_SOCIO = 91L;
    private static final Long POOL_BANCO_ABIERTO = 92L;
    private static final Long POOL_DIAN = 93L;
    private static final Long ENVIO_ID = 42L;
    private static final String NOMBRE = "Notificar despacho";

    @Mock
    private EnvioExternoRepository envioExternoRepository;

    @Mock
    private EventoRepository eventoRepository;

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

    @Mock
    private PoolService poolService;

    private EnvioExternoService envioExternoService;

    private final Pool alpes = new Pool(POOL_ALPES, null, "Alpes", TipoPool.PROPIETARIO, 1, false, true, null,
            new ArrayList<>());
    private final Pool pasarela = new Pool(POOL_PASARELA, null, "Pasarela de pagos", TipoPool.EXTERNO, 2, true,
            true, null, new ArrayList<>());
    private final Pool socio = new Pool(POOL_SOCIO, null, "Socio", TipoPool.PARTICIPANTE, 3, true, true, null,
            new ArrayList<>());
    private final Pool bancoAbierto = new Pool(POOL_BANCO_ABIERTO, null, "Banco", TipoPool.EXTERNO, 4, false, true,
            null, new ArrayList<>());
    private final Pool dian = new Pool(POOL_DIAN, null, "DIAN", TipoPool.EXTERNO, 5, true, true, null,
            new ArrayList<>());

    @BeforeEach
    void inicializar() {
        NodoFlujoResolver resolver = new NodoFlujoResolver(actividadRepository, gatewayRepository, eventoRepository);
        ConexionesService conexiones = new ConexionesService(arcoRepository, resolver);
        UsuarioService usuarios = new UsuarioService(usuarioRepository, new ModelMapper(),
                new BCryptPasswordEncoder());
        AccesoProcesoService acceso = new AccesoProcesoService(usuarios, procesoRepository);
        HistorialProcesoService historial = new HistorialProcesoService(historialProcesoRepository);
        EventoService eventos = new EventoService(eventoRepository, conexiones, historial);
        envioExternoService = new EnvioExternoService(envioExternoRepository, acceso, poolService, eventos);
    }

    private Empresa empresa(Long id) {
        return new Empresa(id, "Empresa " + id, "900123456-" + id, "contacto" + id + "@alpes.com");
    }

    private void autenticar(RolUsuario rol) {
        when(usuarioRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(new Usuario(1L, USERNAME, HASH, rol, true, empresa(EMPRESA_PROPIA))));
    }

    private Proceso proceso(Long empresaId, boolean eliminado) {
        return new Proceso(PROCESO_ID, "Despachos", "Proceso de despachos", "Logistica", EstadoProceso.BORRADOR,
                empresa(empresaId), List.of(alpes, pasarela, socio, bancoAbierto, dian), eliminado);
    }

    private Proceso existeElProceso(Long empresaId, boolean eliminado) {
        Proceso proceso = proceso(empresaId, eliminado);
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.of(proceso));
        return proceso;
    }

    private Proceso existeElProcesoActivo() {
        return existeElProceso(EMPRESA_PROPIA, false);
    }

    private void elDestinoEs(Pool destino) {
        when(poolService.poolDestinoDeMensaje(any(Proceso.class), eq(destino.getId()))).thenReturn(destino);
    }

    private void elOrigenEsAlpes() {
        when(poolService.poolParaNodo(any(Proceso.class), eq(POOL_ALPES))).thenReturn(alpes);
    }

    private void devolverElEnvioGuardado() {
        when(envioExternoRepository.save(any(EnvioExterno.class))).thenAnswer(invocacion -> {
            EnvioExterno guardado = invocacion.getArgument(0);
            guardado.setId(ENVIO_ID);
            return guardado;
        });
    }

    private CrearEnvioExternoDto formulario(Long destinoId, TipoDestinoExterno tipo, ComportamientoFallo fallo) {
        return new CrearEnvioExternoDto(" " + NOMBRE + " ", POOL_ALPES, destinoId, tipo,
                " numeroGuia: texto; fechaDespacho: fecha ", " Al confirmar el despacho ", fallo, " guia ", 700, 80);
    }

    private EnvioExterno envio(boolean activo) {
        EnvioExterno envio = new EnvioExterno();
        envio.setId(ENVIO_ID);
        envio.setProceso(proceso(EMPRESA_PROPIA, false));
        envio.setPool(alpes);
        envio.setPoolDestino(pasarela);
        envio.setNombreMensaje(NOMBRE);
        envio.setTipoDestino(TipoDestinoExterno.SERVICIO_WEB);
        envio.setDatosEnviados("numeroGuia: texto");
        envio.setMomentoProceso("Al confirmar el despacho");
        envio.setComportamientoFallo(ComportamientoFallo.CONTINUAR);
        envio.setPosicionX(700);
        envio.setPosicionY(80);
        envio.setActivo(activo);
        return envio;
    }

    private EnvioExterno existeElEnvio(boolean activo) {
        EnvioExterno envio = envio(activo);
        when(envioExternoRepository.findByIdAndProcesoId(ENVIO_ID, PROCESO_ID)).thenReturn(Optional.of(envio));
        return envio;
    }

    private EditarEnvioExternoDto edicion(Long destinoId, TipoDestinoExterno tipo, ComportamientoFallo fallo) {
        return new EditarEnvioExternoDto(NOMBRE, destinoId, tipo, "numeroGuia: texto", "Al confirmar el despacho",
                fallo, null);
    }

    private EnvioExterno envioGuardado() {
        ArgumentCaptor<EnvioExterno> capturado = ArgumentCaptor.forClass(EnvioExterno.class);
        verify(envioExternoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private HistorialProceso historialGuardado() {
        ArgumentCaptor<HistorialProceso> capturado = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private void noSeModificoNada() {
        verify(envioExternoRepository, never()).save(any(EnvioExterno.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void elEditorDocumentaUnEnvioHaciaUnSistemaExternoCajaNegra() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        elDestinoEs(pasarela);
        elOrigenEsAlpes();
        devolverElEnvioGuardado();

        EnvioExternoRespuestaDto creado = envioExternoService.crear(PROCESO_ID,
                formulario(POOL_PASARELA, TipoDestinoExterno.SERVICIO_WEB, ComportamientoFallo.RUTA_ERROR), USERNAME);

        EnvioExterno guardado = envioGuardado();
        assertThat(guardado.getNombreMensaje()).isEqualTo(NOMBRE);
        assertThat(guardado.getPool()).isSameAs(alpes);
        assertThat(guardado.getPoolDestino()).isSameAs(pasarela);
        assertThat(guardado.getDatosEnviados()).isEqualTo("numeroGuia: texto; fechaDespacho: fecha");
        assertThat(guardado.getMomentoProceso()).isEqualTo("Al confirmar el despacho");
        assertThat(guardado.getClaveCorrelacion()).isEqualTo("guia");
        assertThat(guardado.isActivo()).isTrue();
        assertThat(creado.getId()).isEqualTo(ENVIO_ID);
        assertThat(creado.getPoolOrigenId()).isEqualTo(POOL_ALPES);
        assertThat(creado.getPoolDestinoId()).isEqualTo(POOL_PASARELA);
        assertThat(creado.getPoolDestinoNombre()).isEqualTo("Pasarela de pagos");
        assertThat(creado.getEtiqueta()).isEqualTo("Envío externo: " + NOMBRE);
        assertThat(creado.getAdvertencias()).isEmpty();
        assertThat(historialGuardado().getCambiosRealizados()).isEqualTo("envío externo creado: '" + NOMBRE
                + "' (pool 'Alpes' -> pool 'Pasarela de pagos', SERVICIO_WEB, ante fallo RUTA_ERROR)");
    }

    @ParameterizedTest
    @EnumSource(TipoDestinoExterno.class)
    void cadaTipoDeDestinoExternoSeDocumenta(TipoDestinoExterno tipo) {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        elDestinoEs(pasarela);
        elOrigenEsAlpes();
        devolverElEnvioGuardado();

        EnvioExternoRespuestaDto creado = envioExternoService.crear(PROCESO_ID,
                formulario(POOL_PASARELA, tipo, ComportamientoFallo.CONTINUAR), USERNAME);

        assertThat(envioGuardado().getTipoDestino()).isEqualTo(tipo);
        assertThat(creado.getTipoDestino()).isEqualTo(tipo);
    }

    @ParameterizedTest
    @EnumSource(ComportamientoFallo.class)
    void cadaComportamientoAnteFalloSeDocumenta(ComportamientoFallo fallo) {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        elDestinoEs(pasarela);
        elOrigenEsAlpes();
        devolverElEnvioGuardado();

        EnvioExternoRespuestaDto creado = envioExternoService.crear(PROCESO_ID,
                formulario(POOL_PASARELA, TipoDestinoExterno.COLA, fallo), USERNAME);

        assertThat(envioGuardado().getComportamientoFallo()).isEqualTo(fallo);
        assertThat(creado.getComportamientoFallo()).isEqualTo(fallo);
    }

    @Test
    void elPoolPropietarioNoEsUnSistemaExterno() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        elDestinoEs(alpes);

        assertThatThrownBy(() -> envioExternoService.crear(PROCESO_ID,
                formulario(POOL_ALPES, TipoDestinoExterno.CORREO, ComportamientoFallo.CONTINUAR), USERNAME))
                .isInstanceOf(EnvioExternoNoValidoException.class)
                .hasMessage("El pool 'Alpes' es de tipo PROPIETARIO" + EnvioExternoService.DESTINO_NO_EXTERNO);

        noSeModificoNada();
    }

    @Test
    void unPoolParticipanteNoEsUnSistemaExterno() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        elDestinoEs(socio);

        assertThatThrownBy(() -> envioExternoService.crear(PROCESO_ID,
                formulario(POOL_SOCIO, TipoDestinoExterno.CORREO, ComportamientoFallo.CONTINUAR), USERNAME))
                .isInstanceOf(EnvioExternoNoValidoException.class)
                .hasMessageContaining("PARTICIPANTE");

        noSeModificoNada();
    }

    @Test
    void unPoolExternoQueNoEsCajaNegraSeRechaza() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        elDestinoEs(bancoAbierto);

        assertThatThrownBy(() -> envioExternoService.crear(PROCESO_ID,
                formulario(POOL_BANCO_ABIERTO, TipoDestinoExterno.CORREO, ComportamientoFallo.CONTINUAR), USERNAME))
                .isInstanceOf(EnvioExternoNoValidoException.class)
                .hasMessage("El pool externo 'Banco" + EnvioExternoService.DESTINO_SIN_CAJA_NEGRA);

        noSeModificoNada();
    }

    @Test
    void elOrigenYElDestinoNoPuedenSerElMismoPool() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        elDestinoEs(pasarela);
        CrearEnvioExternoDto dto = formulario(POOL_PASARELA, TipoDestinoExterno.CORREO, ComportamientoFallo.CONTINUAR);
        dto.setPoolOrigenId(POOL_PASARELA);

        assertThatThrownBy(() -> envioExternoService.crear(PROCESO_ID, dto, USERNAME))
                .isInstanceOf(MensajeEntreMismoPoolException.class);

        verify(poolService, never()).poolParaNodo(any(Proceso.class), anyLong());
        noSeModificoNada();
    }

    @Test
    void unPoolDeOtroProcesoSeRechaza() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        when(poolService.poolDestinoDeMensaje(any(Proceso.class), eq(POOL_PASARELA)))
                .thenThrow(new PoolMensajeNoValidoException(PoolService.POOL_MENSAJE_AJENO));

        assertThatThrownBy(() -> envioExternoService.crear(PROCESO_ID,
                formulario(POOL_PASARELA, TipoDestinoExterno.CORREO, ComportamientoFallo.CONTINUAR), USERNAME))
                .isInstanceOf(PoolMensajeNoValidoException.class);

        noSeModificoNada();
    }

    @Test
    void elUsuarioDeSoloLecturaNoDocumentaEnvios() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> envioExternoService.crear(PROCESO_ID,
                formulario(POOL_PASARELA, TipoDestinoExterno.CORREO, ComportamientoFallo.CONTINUAR), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(EnvioExternoService.SIN_PERMISO_ESCRITURA);

        noSeModificoNada();
    }

    @Test
    void unaEmpresaInvitadaConsultaLosEnviosPeroNoLosEdita() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_AJENA, false);
        when(procesoRepository.estaCompartidoCon(PROCESO_ID, EMPRESA_PROPIA)).thenReturn(true);
        when(envioExternoRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(envio(true)));
        when(envioExternoRepository.findByIdAndProcesoId(ENVIO_ID, PROCESO_ID)).thenReturn(Optional.of(envio(true)));

        assertThat(envioExternoService.listar(PROCESO_ID, USERNAME)).extracting(EnvioExternoRespuestaDto::getId)
                .containsExactly(ENVIO_ID);
        assertThat(envioExternoService.obtener(PROCESO_ID, ENVIO_ID, USERNAME).getMomentoProceso())
                .isEqualTo("Al confirmar el despacho");
        assertThatThrownBy(() -> envioExternoService.editar(PROCESO_ID, ENVIO_ID,
                edicion(POOL_PASARELA, TipoDestinoExterno.CORREO, ComportamientoFallo.CONTINUAR), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);
        noSeModificoNada();
    }

    @Test
    void editarCambiaElDestinoYRegistraLosCampos() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        EnvioExterno envio = existeElEnvio(true);
        elDestinoEs(dian);

        EnvioExternoRespuestaDto editado = envioExternoService.editar(PROCESO_ID, ENVIO_ID,
                edicion(POOL_DIAN, TipoDestinoExterno.CORREO, ComportamientoFallo.FINALIZAR), USERNAME);

        assertThat(envio.getPoolDestino()).isSameAs(dian);
        assertThat(editado.getPoolDestinoNombre()).isEqualTo("DIAN");
        verify(envioExternoRepository).save(envio);
        assertThat(historialGuardado().getCambiosRealizados()).isEqualTo("envío externo '" + NOMBRE
                + "': pool destino: 'Pasarela de pagos' -> 'DIAN'; tipo de destino: 'SERVICIO_WEB' -> 'CORREO';"
                + " comportamiento ante fallo: 'CONTINUAR' -> 'FINALIZAR'");
    }

    @Test
    void editarLosDatosYElMomentoQuedaEnElHistorial() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        EnvioExterno envio = existeElEnvio(true);
        elDestinoEs(pasarela);

        envioExternoService.editar(PROCESO_ID, ENVIO_ID, new EditarEnvioExternoDto("Notificar entrega", POOL_PASARELA,
                TipoDestinoExterno.SERVICIO_WEB, "numeroGuia: texto; receptor: texto", "Al entregar el pedido",
                ComportamientoFallo.CONTINUAR, "guia"), USERNAME);

        assertThat(envio.getMomentoProceso()).isEqualTo("Al entregar el pedido");
        assertThat(historialGuardado().getCambiosRealizados()).isEqualTo("envío externo '" + NOMBRE
                + "': nombre: '" + NOMBRE + "' -> 'Notificar entrega'; datos enviados: 'numeroGuia: texto' ->"
                + " 'numeroGuia: texto; receptor: texto'; momento del proceso: 'Al confirmar el despacho' ->"
                + " 'Al entregar el pedido'; clave de correlacion: '' -> 'guia'");
    }

    @Test
    void editarSinCambiosNoGuardaNiRegistraHistorial() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        existeElEnvio(true);
        elDestinoEs(pasarela);
        EditarEnvioExternoDto dto = edicion(POOL_PASARELA, TipoDestinoExterno.SERVICIO_WEB,
                ComportamientoFallo.CONTINUAR);
        dto.setClaveCorrelacion("  ");

        envioExternoService.editar(PROCESO_ID, ENVIO_ID, dto, USERNAME);

        noSeModificoNada();
    }

    @Test
    void editarHaciaUnPoolQueNoEsExternoSeRechaza() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        existeElEnvio(true);
        elDestinoEs(socio);

        assertThatThrownBy(() -> envioExternoService.editar(PROCESO_ID, ENVIO_ID,
                edicion(POOL_SOCIO, TipoDestinoExterno.CORREO, ComportamientoFallo.CONTINUAR), USERNAME))
                .isInstanceOf(EnvioExternoNoValidoException.class);

        noSeModificoNada();
    }

    @Test
    void laConsultaPreviaAdelantaLosArcosQueSeDesactivaran() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElEnvio(true);
        when(arcoRepository.conectadosAlNodo(PROCESO_ID, TipoNodoFlujo.EVENTO, ENVIO_ID)).thenReturn(List.of(
                new Arco(60L, proceso(EMPRESA_PROPIA, false), TipoNodoFlujo.ACTIVIDAD, 30L, TipoNodoFlujo.EVENTO,
                        ENVIO_ID, null, null, true),
                new Arco(61L, proceso(EMPRESA_PROPIA, false), TipoNodoFlujo.EVENTO, ENVIO_ID, TipoNodoFlujo.ACTIVIDAD,
                        31L, null, null, true)));

        EnvioExternoRespuestaDto previa = envioExternoService.obtenerParaEliminar(PROCESO_ID, ENVIO_ID, USERNAME);

        assertThat(previa.getAdvertencias()).contains("Se desactivarán 2 arcos conectados a Envío externo: "
                + NOMBRE + ".");
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void elAdministradorEliminaElEnvioDeFormaLogica() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        EnvioExterno envio = existeElEnvio(true);

        EnvioExternoRespuestaDto eliminado = envioExternoService.eliminar(PROCESO_ID, ENVIO_ID, USERNAME);

        assertThat(envio.isActivo()).isFalse();
        assertThat(eliminado.isActivo()).isFalse();
        verify(eventoRepository).save(envio);
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("envío externo eliminado: '" + NOMBRE + "'");
    }

    @Test
    void elEditorNoEliminaEnviosExternos() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> envioExternoService.obtenerParaEliminar(PROCESO_ID, ENVIO_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(EnvioExternoService.SIN_PERMISO_ELIMINAR);
    }

    @Test
    void unaSegundaEliminacionSeRechazaComoRecursoInexistente() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElEnvio(false);

        assertThatThrownBy(() -> envioExternoService.eliminar(PROCESO_ID, ENVIO_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(EnvioExternoService.ENVIO_ELIMINADO);

        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void unEnvioDeOtroProcesoNoSeEncuentra() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        when(envioExternoRepository.findByIdAndProcesoId(ENVIO_ID, PROCESO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> envioExternoService.obtener(PROCESO_ID, ENVIO_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(EnvioExternoService.ENVIO_NO_EXISTE);
    }
}
