package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.ComportamientoSinCaso;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.Evento;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.MessageCatch;
import co.edu.javeriana.procesosempresariales.domain.MessageThrow;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
import co.edu.javeriana.procesosempresariales.dto.CrearMessageCatchDto;
import co.edu.javeriana.procesosempresariales.dto.EditarMessageCatchDto;
import co.edu.javeriana.procesosempresariales.dto.MessageCatchRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.CatchInicioConEntradaException;
import co.edu.javeriana.procesosempresariales.exception.CorrelacionNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.PoolCajaNegraException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.ArcoRepository;
import co.edu.javeriana.procesosempresariales.repository.EventoRepository;
import co.edu.javeriana.procesosempresariales.repository.GatewayRepository;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.MessageCatchRepository;
import co.edu.javeriana.procesosempresariales.repository.MessageThrowRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class MessageCatchServiceTest {

    private static final String USERNAME = "editor@alpes.com";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final Long PROCESO_ID = 5L;
    private static final Long POOL_ALPES = 80L;
    private static final Long POOL_BANCO = 90L;
    private static final Long CATCH_ID = 41L;
    private static final Long THROW_ID = 40L;
    private static final String NOMBRE = "Pago confirmado";

    @Mock
    private MessageThrowRepository messageThrowRepository;

    @Mock
    private MessageCatchRepository messageCatchRepository;

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

    private MessageCatchService messageCatchService;

    private final Pool alpes = new Pool(POOL_ALPES, null, "Alpes", TipoPool.PROPIETARIO, 1, false, true, null,
            new ArrayList<>());
    private final Pool banco = new Pool(POOL_BANCO, null, "Banco", TipoPool.PARTICIPANTE, 2, false, true, null,
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
        CorrelacionMensajeService correlacion = new CorrelacionMensajeService(messageThrowRepository,
                messageCatchRepository);
        messageCatchService = new MessageCatchService(messageCatchRepository, acceso, poolService, eventos,
                correlacion);
    }

    private Empresa empresa(Long id) {
        return new Empresa(id, "Empresa " + id, "900123456-" + id, "contacto" + id + "@alpes.com");
    }

    private void autenticar(RolUsuario rol) {
        when(usuarioRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(new Usuario(1L, USERNAME, HASH, rol, true, empresa(EMPRESA_PROPIA))));
    }

    private Proceso proceso(Long empresaId, boolean eliminado) {
        return new Proceso(PROCESO_ID, "Compras", "Proceso de compras", "Operaciones", EstadoProceso.BORRADOR,
                empresa(empresaId), List.of(alpes, banco), eliminado);
    }

    private Proceso existeElProceso(Long empresaId, boolean eliminado) {
        Proceso proceso = proceso(empresaId, eliminado);
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.of(proceso));
        return proceso;
    }

    private Proceso existeElProcesoActivo() {
        return existeElProceso(EMPRESA_PROPIA, false);
    }

    private void elPoolEsElDeAlpes() {
        when(poolService.poolParaNodo(any(Proceso.class), eq(POOL_ALPES))).thenReturn(alpes);
    }

    private void devolverElCatchGuardado() {
        when(messageCatchRepository.save(any(MessageCatch.class))).thenAnswer(invocacion -> {
            MessageCatch guardado = invocacion.getArgument(0);
            guardado.setId(CATCH_ID);
            return guardado;
        });
    }

    private CrearMessageCatchDto formulario(VarianteMessageCatch variante, ComportamientoSinCaso comportamiento) {
        return new CrearMessageCatchDto(" " + NOMBRE + " ", variante, " numeroFactura: texto ",
                " Registrar pago ", false, " factura ", comportamiento, POOL_ALPES, 40, 60);
    }

    private MessageCatch messageCatch(VarianteMessageCatch variante, boolean activo) {
        MessageCatch messageCatch = new MessageCatch();
        messageCatch.setId(CATCH_ID);
        messageCatch.setProceso(proceso(EMPRESA_PROPIA, false));
        messageCatch.setPool(alpes);
        messageCatch.setNombreMensaje(NOMBRE);
        messageCatch.setVariante(variante);
        messageCatch.setDatosEsperados("numeroFactura: texto");
        messageCatch.setActividadesUso("Registrar pago");
        messageCatch.setOrigenExterno(false);
        messageCatch.setClaveCorrelacion("factura");
        messageCatch.setComportamientoSinCaso(ComportamientoSinCaso.DESCARTAR);
        messageCatch.setPosicionX(40);
        messageCatch.setPosicionY(60);
        messageCatch.setActivo(activo);
        return messageCatch;
    }

    private MessageCatch existeElCatch(VarianteMessageCatch variante, boolean activo) {
        MessageCatch messageCatch = messageCatch(variante, activo);
        when(messageCatchRepository.findByIdAndProcesoId(CATCH_ID, PROCESO_ID)).thenReturn(Optional.of(messageCatch));
        return messageCatch;
    }

    private MessageThrow throwHaciaAlpes(String clave) {
        MessageThrow messageThrow = new MessageThrow();
        messageThrow.setId(THROW_ID);
        messageThrow.setProceso(proceso(EMPRESA_PROPIA, false));
        messageThrow.setPool(banco);
        messageThrow.setPoolDestino(alpes);
        messageThrow.setNombreMensaje(NOMBRE);
        messageThrow.setClaveCorrelacion(clave);
        messageThrow.setActivo(true);
        return messageThrow;
    }

    private EditarMessageCatchDto edicion(VarianteMessageCatch variante, ComportamientoSinCaso comportamiento) {
        return new EditarMessageCatchDto(NOMBRE, variante, "numeroFactura: texto", "Registrar pago", false,
                "factura", comportamiento);
    }

    private MessageCatch catchGuardado() {
        ArgumentCaptor<MessageCatch> capturado = ArgumentCaptor.forClass(MessageCatch.class);
        verify(messageCatchRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private HistorialProceso historialGuardado() {
        ArgumentCaptor<HistorialProceso> capturado = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private void noSeModificoNada() {
        verify(messageCatchRepository, never()).save(any(MessageCatch.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void unCatchDeInicioSiempreIniciaUnCasoNuevo() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        elPoolEsElDeAlpes();
        devolverElCatchGuardado();

        MessageCatchRespuestaDto creado = messageCatchService.crear(PROCESO_ID,
                formulario(VarianteMessageCatch.INICIO, null), USERNAME);

        MessageCatch guardado = catchGuardado();
        assertThat(guardado.getNombreMensaje()).isEqualTo(NOMBRE);
        assertThat(guardado.getVariante()).isEqualTo(VarianteMessageCatch.INICIO);
        assertThat(guardado.getDatosEsperados()).isEqualTo("numeroFactura: texto");
        assertThat(guardado.getActividadesUso()).isEqualTo("Registrar pago");
        assertThat(guardado.getClaveCorrelacion()).isEqualTo("factura");
        assertThat(guardado.getComportamientoSinCaso()).isEqualTo(ComportamientoSinCaso.INICIAR_NUEVO_CASO);
        assertThat(guardado.getPool()).isSameAs(alpes);
        assertThat(guardado.admiteEntradas()).isFalse();
        assertThat(creado.getPoolId()).isEqualTo(POOL_ALPES);
        assertThat(creado.getPoolNombre()).isEqualTo("Alpes");
        assertThat(creado.getEtiqueta()).isEqualTo("Message Catch: " + NOMBRE);
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("message catch creado: '" + NOMBRE + "' (INICIO en el pool 'Alpes')");
    }

    @Test
    void unCatchDeInicioNoPuedeDescartarMensajesSinCaso() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        elPoolEsElDeAlpes();

        assertThatThrownBy(() -> messageCatchService.crear(PROCESO_ID,
                formulario(VarianteMessageCatch.INICIO, ComportamientoSinCaso.DESCARTAR), USERNAME))
                .isInstanceOf(CorrelacionNoValidaException.class)
                .hasMessage(MessageCatchService.INICIO_SIEMPRE_INICIA);

        noSeModificoNada();
    }

    @Test
    void unCatchIntermedioConservaElComportamientoDocumentado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        elPoolEsElDeAlpes();
        devolverElCatchGuardado();

        MessageCatchRespuestaDto creado = messageCatchService.crear(PROCESO_ID,
                formulario(VarianteMessageCatch.INTERMEDIO, ComportamientoSinCaso.DESCARTAR), USERNAME);

        assertThat(catchGuardado().getComportamientoSinCaso()).isEqualTo(ComportamientoSinCaso.DESCARTAR);
        assertThat(creado.getVariante()).isEqualTo(VarianteMessageCatch.INTERMEDIO);
        assertThat(creado.getAdvertencias()).containsExactly("No existe un Message Throw '" + NOMBRE
                + "' dirigido al pool 'Alpes': indica el Throw homólogo o declara el origen como externo.");
    }

    @Test
    void unCatchConThrowHomologoNoAdvierteYReportaElThrow() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        elPoolEsElDeAlpes();
        devolverElCatchGuardado();
        when(messageThrowRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(throwHaciaAlpes("FACTURA")));

        MessageCatchRespuestaDto creado = messageCatchService.crear(PROCESO_ID,
                formulario(VarianteMessageCatch.INTERMEDIO, ComportamientoSinCaso.DESCARTAR), USERNAME);

        assertThat(creado.getThrowHomologoId()).isEqualTo(THROW_ID);
        assertThat(creado.getAdvertencias()).isEmpty();
    }

    @Test
    void unCatchDeOrigenExternoNoExigeThrowInterno() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        elPoolEsElDeAlpes();
        devolverElCatchGuardado();
        CrearMessageCatchDto dto = formulario(VarianteMessageCatch.INTERMEDIO, ComportamientoSinCaso.DESCARTAR);
        dto.setOrigenExterno(true);

        MessageCatchRespuestaDto creado = messageCatchService.crear(PROCESO_ID, dto, USERNAME);

        assertThat(catchGuardado().esOrigenExterno()).isTrue();
        assertThat(creado.isOrigenExterno()).isTrue();
        assertThat(creado.getAdvertencias()).isEmpty();
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("message catch creado: '" + NOMBRE + "' (INTERMEDIO en el pool 'Alpes', origen externo)");
    }

    @Test
    void unCatchIntermedioSinClaveNiComportamientoGeneraAdvertencias() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        elPoolEsElDeAlpes();
        devolverElCatchGuardado();
        CrearMessageCatchDto dto = formulario(VarianteMessageCatch.INTERMEDIO, null);
        dto.setClaveCorrelacion(" ");
        dto.setOrigenExterno(true);

        MessageCatchRespuestaDto creado = messageCatchService.crear(PROCESO_ID, dto, USERNAME);

        assertThat(catchGuardado().getClaveCorrelacion()).isNull();
        assertThat(creado.getAdvertencias()).hasSize(2)
                .anyMatch(advertencia -> advertencia.contains("no declara clave de correlación"))
                .anyMatch(advertencia -> advertencia.contains("no documenta qué ocurre"));
    }

    @Test
    void unCatchNoSeUbicaEnUnPoolQueNoAdmiteElementos() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        when(poolService.poolParaNodo(any(Proceso.class), eq(POOL_ALPES)))
                .thenThrow(new PoolCajaNegraException("El pool 'Alpes' es una caja negra"));

        assertThatThrownBy(() -> messageCatchService.crear(PROCESO_ID,
                formulario(VarianteMessageCatch.INICIO, null), USERNAME))
                .isInstanceOf(PoolCajaNegraException.class);

        noSeModificoNada();
    }

    @Test
    void elUsuarioDeSoloLecturaNoCreaMessageCatch() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> messageCatchService.crear(PROCESO_ID,
                formulario(VarianteMessageCatch.INICIO, null), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(MessageCatchService.SIN_PERMISO_ESCRITURA);

        noSeModificoNada();
    }

    @Test
    void unaEmpresaInvitadaConsultaLosCatchPeroNoLosCrea() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);
        when(procesoRepository.estaCompartidoCon(PROCESO_ID, EMPRESA_PROPIA)).thenReturn(true);
        when(messageCatchRepository.findByIdAndProcesoId(CATCH_ID, PROCESO_ID))
                .thenReturn(Optional.of(messageCatch(VarianteMessageCatch.INICIO, true)));

        assertThat(messageCatchService.obtener(PROCESO_ID, CATCH_ID, USERNAME).getId()).isEqualTo(CATCH_ID);
        assertThatThrownBy(() -> messageCatchService.crear(PROCESO_ID,
                formulario(VarianteMessageCatch.INICIO, null), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);
        noSeModificoNada();
    }

    @Test
    void listarDevuelveLosCatchActivosDelProceso() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProcesoActivo();
        when(messageCatchRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(messageCatch(VarianteMessageCatch.INTERMEDIO, true)));

        assertThat(messageCatchService.listar(PROCESO_ID, USERNAME)).extracting(MessageCatchRespuestaDto::getId)
                .containsExactly(CATCH_ID);
    }

    @Test
    void editarRegistraLosCamposModificados() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        MessageCatch messageCatch = existeElCatch(VarianteMessageCatch.INTERMEDIO, true);

        messageCatchService.editar(PROCESO_ID, CATCH_ID, new EditarMessageCatchDto("Pago recibido",
                VarianteMessageCatch.INTERMEDIO, "numeroFactura: texto; valor: decimal", "Registrar pago", true,
                "radicado", ComportamientoSinCaso.INICIAR_NUEVO_CASO), USERNAME);

        assertThat(messageCatch.getNombreMensaje()).isEqualTo("Pago recibido");
        assertThat(messageCatch.esOrigenExterno()).isTrue();
        verify(messageCatchRepository).save(messageCatch);
        assertThat(historialGuardado().getCambiosRealizados()).isEqualTo("message catch '" + NOMBRE
                + "': nombre: '" + NOMBRE + "' -> 'Pago recibido'; datos esperados: 'numeroFactura: texto' ->"
                + " 'numeroFactura: texto; valor: decimal'; origen externo: 'false' -> 'true';"
                + " clave de correlacion: 'factura' -> 'radicado'; comportamiento sin caso: 'DESCARTAR' ->"
                + " 'INICIAR_NUEVO_CASO'");
    }

    @Test
    void editarAgregaUnaClaveQueElCatchNoTenia() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        MessageCatch messageCatch = existeElCatch(VarianteMessageCatch.INTERMEDIO, true);
        messageCatch.setClaveCorrelacion(null);

        messageCatchService.editar(PROCESO_ID, CATCH_ID,
                edicion(VarianteMessageCatch.INTERMEDIO, ComportamientoSinCaso.DESCARTAR), USERNAME);

        assertThat(messageCatch.getClaveCorrelacion()).isEqualTo("factura");
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("message catch '" + NOMBRE + "': clave de correlacion: '' -> 'factura'");
    }

    @Test
    void crearSinClaveDeCorrelacionLaGuardaNula() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        elPoolEsElDeAlpes();
        devolverElCatchGuardado();
        CrearMessageCatchDto dto = formulario(VarianteMessageCatch.INICIO, null);
        dto.setClaveCorrelacion(null);

        messageCatchService.crear(PROCESO_ID, dto, USERNAME);

        assertThat(catchGuardado().getClaveCorrelacion()).isNull();
    }

    @Test
    void editarSinCambiosNoGuardaNiRegistraHistorial() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        existeElCatch(VarianteMessageCatch.INTERMEDIO, true);

        messageCatchService.editar(PROCESO_ID, CATCH_ID,
                edicion(VarianteMessageCatch.INTERMEDIO, ComportamientoSinCaso.DESCARTAR), USERNAME);

        noSeModificoNada();
    }

    @Test
    void pasarAInicioSinEntradasSePermiteYFijaElComportamiento() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        MessageCatch messageCatch = existeElCatch(VarianteMessageCatch.INTERMEDIO, true);

        messageCatchService.editar(PROCESO_ID, CATCH_ID, edicion(VarianteMessageCatch.INICIO, null), USERNAME);

        assertThat(messageCatch.getVariante()).isEqualTo(VarianteMessageCatch.INICIO);
        assertThat(messageCatch.getComportamientoSinCaso()).isEqualTo(ComportamientoSinCaso.INICIAR_NUEVO_CASO);
        assertThat(historialGuardado().getCambiosRealizados()).contains("variante: 'INTERMEDIO' -> 'INICIO'");
    }

    @Test
    void pasarAInicioConFlujosEntrantesSeRechaza() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        MessageCatch messageCatch = existeElCatch(VarianteMessageCatch.INTERMEDIO, true);
        when(arcoRepository.findByProcesoIdAndDestinoTipoAndDestinoIdAndActivoTrueOrderByIdAsc(PROCESO_ID,
                TipoNodoFlujo.EVENTO, CATCH_ID)).thenReturn(List.of(new Arco(60L, proceso(EMPRESA_PROPIA, false),
                        TipoNodoFlujo.ACTIVIDAD, 30L, TipoNodoFlujo.EVENTO, CATCH_ID, null, null, true)));

        assertThatThrownBy(() -> messageCatchService.editar(PROCESO_ID, CATCH_ID,
                edicion(VarianteMessageCatch.INICIO, null), USERNAME))
                .isInstanceOf(CatchInicioConEntradaException.class)
                .hasMessage("El Message Catch '" + NOMBRE + "' tiene 1" + MessageCatchService.INICIO_CON_ENTRADAS);

        assertThat(messageCatch.getVariante()).isEqualTo(VarianteMessageCatch.INTERMEDIO);
        noSeModificoNada();
    }

    @Test
    void editarRechazaUnCatchDeOtroProceso() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        when(messageCatchRepository.findByIdAndProcesoId(CATCH_ID, PROCESO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageCatchService.editar(PROCESO_ID, CATCH_ID,
                edicion(VarianteMessageCatch.INTERMEDIO, null), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(MessageCatchService.CATCH_NO_EXISTE);
    }

    @Test
    void elEditorNoEliminaMessageCatch() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> messageCatchService.eliminar(PROCESO_ID, CATCH_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(MessageCatchService.SIN_PERMISO_ELIMINAR);

        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void laConsultaPreviaAdvierteQueElThrowQuedaSinCatch() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElCatch(VarianteMessageCatch.INTERMEDIO, true);
        when(messageThrowRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(throwHaciaAlpes("factura")));
        when(messageCatchRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(messageCatch(VarianteMessageCatch.INTERMEDIO, true)));

        MessageCatchRespuestaDto previa = messageCatchService.obtenerParaEliminar(PROCESO_ID, CATCH_ID, USERNAME);

        assertThat(previa.getAdvertencias())
                .containsExactly("El Message Throw '" + NOMBRE + "' quedará sin Message Catch homólogo en el pool"
                        + " 'Alpes'.");
        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void elAdministradorEliminaDeFormaLogicaYQuedaEnElHistorial() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        MessageCatch messageCatch = existeElCatch(VarianteMessageCatch.INICIO, true);

        MessageCatchRespuestaDto eliminado = messageCatchService.eliminar(PROCESO_ID, CATCH_ID, USERNAME);

        assertThat(messageCatch.isActivo()).isFalse();
        assertThat(eliminado.isActivo()).isFalse();
        assertThat(eliminado.getArcosDesactivados()).isZero();
        verify(eventoRepository).save(messageCatch);
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("message catch eliminado: '" + NOMBRE + "'");
    }

    @Test
    void unaSegundaEliminacionSeRechazaComoRecursoInexistente() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElCatch(VarianteMessageCatch.INICIO, false);

        assertThatThrownBy(() -> messageCatchService.eliminar(PROCESO_ID, CATCH_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(MessageCatchService.CATCH_ELIMINADO);

        verify(eventoRepository, never()).save(any(Evento.class));
    }
}
