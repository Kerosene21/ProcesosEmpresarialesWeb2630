package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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
import co.edu.javeriana.procesosempresariales.dto.CrearMessageThrowDto;
import co.edu.javeriana.procesosempresariales.dto.EditarMessageThrowDto;
import co.edu.javeriana.procesosempresariales.dto.MessageThrowRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.MensajeEntreMismoPoolException;
import co.edu.javeriana.procesosempresariales.exception.PoolMensajeNoValidoException;
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
class MessageThrowServiceTest {

    private static final String USERNAME = "editor@alpes.com";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final Long PROCESO_ID = 5L;
    private static final Long POOL_ALPES = 80L;
    private static final Long POOL_BANCO = 90L;
    private static final Long POOL_PROVEEDOR = 91L;
    private static final Long THROW_ID = 40L;
    private static final Long CATCH_ID = 41L;
    private static final String NOMBRE = "Solicitud de pago";

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

    private MessageThrowService messageThrowService;

    private final Pool alpes = new Pool(POOL_ALPES, null, "Alpes", TipoPool.PROPIETARIO, 1, false, true, null,
            new ArrayList<>());
    private final Pool banco = new Pool(POOL_BANCO, null, "Banco", TipoPool.PARTICIPANTE, 2, false, true, null,
            new ArrayList<>());
    private final Pool proveedor = new Pool(POOL_PROVEEDOR, null, "Proveedor", TipoPool.EXTERNO, 3, true, true,
            null, new ArrayList<>());

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
        messageThrowService = new MessageThrowService(messageThrowRepository, acceso, poolService, eventos,
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
                empresa(empresaId), List.of(alpes, banco, proveedor), eliminado);
    }

    private Proceso existeElProceso(Long empresaId, boolean eliminado) {
        Proceso proceso = proceso(empresaId, eliminado);
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.of(proceso));
        return proceso;
    }

    private Proceso existeElProcesoActivo() {
        return existeElProceso(EMPRESA_PROPIA, false);
    }

    private void losPoolsSonValidos(Pool origen, Pool destino) {
        when(poolService.poolDestinoDeMensaje(any(Proceso.class), eq(destino.getId()))).thenReturn(destino);
        when(poolService.poolParaNodo(any(Proceso.class), eq(origen.getId()))).thenReturn(origen);
    }

    private void devolverElThrowGuardado() {
        when(messageThrowRepository.save(any(MessageThrow.class))).thenAnswer(invocacion -> {
            MessageThrow guardado = invocacion.getArgument(0);
            guardado.setId(THROW_ID);
            return guardado;
        });
    }

    private CrearMessageThrowDto formularioCreacion() {
        return new CrearMessageThrowDto("  " + NOMBRE + " ", " numeroFactura: texto; valor: decimal ", POOL_ALPES,
                POOL_BANCO, " factura ", ComportamientoSinCaso.DESCARTAR, 400, 60);
    }

    private MessageThrow messageThrow(boolean activo) {
        MessageThrow messageThrow = new MessageThrow();
        messageThrow.setId(THROW_ID);
        messageThrow.setProceso(proceso(EMPRESA_PROPIA, false));
        messageThrow.setPool(alpes);
        messageThrow.setPoolDestino(banco);
        messageThrow.setNombreMensaje(NOMBRE);
        messageThrow.setContenido("numeroFactura: texto");
        messageThrow.setClaveCorrelacion("factura");
        messageThrow.setPosicionX(400);
        messageThrow.setPosicionY(60);
        messageThrow.setActivo(activo);
        return messageThrow;
    }

    private MessageThrow existeElThrow(boolean activo) {
        MessageThrow messageThrow = messageThrow(activo);
        when(messageThrowRepository.findByIdAndProcesoId(THROW_ID, PROCESO_ID)).thenReturn(Optional.of(messageThrow));
        return messageThrow;
    }

    private MessageCatch catchEnElBanco(String nombre, String clave) {
        MessageCatch messageCatch = new MessageCatch();
        messageCatch.setId(CATCH_ID);
        messageCatch.setProceso(proceso(EMPRESA_PROPIA, false));
        messageCatch.setPool(banco);
        messageCatch.setNombreMensaje(nombre);
        messageCatch.setVariante(VarianteMessageCatch.INTERMEDIO);
        messageCatch.setClaveCorrelacion(clave);
        messageCatch.setPosicionX(100);
        messageCatch.setPosicionY(60);
        messageCatch.setActivo(true);
        return messageCatch;
    }

    private MessageThrow throwGuardado() {
        ArgumentCaptor<MessageThrow> capturado = ArgumentCaptor.forClass(MessageThrow.class);
        verify(messageThrowRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private HistorialProceso historialGuardado() {
        ArgumentCaptor<HistorialProceso> capturado = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private void noSeModificoNada() {
        verify(messageThrowRepository, never()).save(any(MessageThrow.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void elEditorCreaUnMessageThrowEntrePoolsDistintos() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        losPoolsSonValidos(alpes, banco);
        devolverElThrowGuardado();

        MessageThrowRespuestaDto creado = messageThrowService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        MessageThrow guardado = throwGuardado();
        assertThat(guardado.getNombreMensaje()).isEqualTo(NOMBRE);
        assertThat(guardado.getContenido()).isEqualTo("numeroFactura: texto; valor: decimal");
        assertThat(guardado.getPool()).isSameAs(alpes);
        assertThat(guardado.getPoolDestino()).isSameAs(banco);
        assertThat(guardado.getClaveCorrelacion()).isEqualTo("factura");
        assertThat(guardado.getComportamientoSinCaso()).isEqualTo(ComportamientoSinCaso.DESCARTAR);
        assertThat(guardado.isActivo()).isTrue();
        assertThat(creado.getId()).isEqualTo(THROW_ID);
        assertThat(creado.getProcesoId()).isEqualTo(PROCESO_ID);
        assertThat(creado.getPoolOrigenId()).isEqualTo(POOL_ALPES);
        assertThat(creado.getPoolOrigenNombre()).isEqualTo("Alpes");
        assertThat(creado.getPoolDestinoId()).isEqualTo(POOL_BANCO);
        assertThat(creado.getPoolDestinoNombre()).isEqualTo("Banco");
        assertThat(creado.getEtiqueta()).isEqualTo("Message Throw: " + NOMBRE);
        assertThat(creado.getPosicionX()).isEqualTo(400);
        assertThat(creado.getPosicionY()).isEqualTo(60);
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("message throw creado: '" + NOMBRE + "' (pool 'Alpes' -> pool 'Banco')");
    }

    @Test
    void elAdministradorCreaUnMessageThrowYSeAdvierteQueNoTieneCatchHomologo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        losPoolsSonValidos(alpes, banco);
        devolverElThrowGuardado();

        MessageThrowRespuestaDto creado = messageThrowService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        assertThat(creado.getCatchHomologoId()).isNull();
        assertThat(creado.getAdvertencias()).containsExactly("No existe un Message Catch '" + NOMBRE
                + "' en el pool destino 'Banco': el mensaje no tiene un receptor modelado.");
    }

    @Test
    void unMessageThrowConCatchHomologoNoGeneraAdvertenciasYReportaElCatch() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        losPoolsSonValidos(alpes, banco);
        devolverElThrowGuardado();
        when(messageCatchRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(catchEnElBanco("SOLICITUD  de pago", "Factura")));

        MessageThrowRespuestaDto creado = messageThrowService.crear(PROCESO_ID, formularioCreacion(), USERNAME);

        assertThat(creado.getCatchHomologoId()).isEqualTo(CATCH_ID);
        assertThat(creado.getAdvertencias()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void unaClaveDeCorrelacionAusenteOVaciaSeGuardaComoNula(String clave) {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        losPoolsSonValidos(alpes, banco);
        devolverElThrowGuardado();
        CrearMessageThrowDto dto = formularioCreacion();
        dto.setClaveCorrelacion(clave);
        dto.setComportamientoSinCaso(null);

        messageThrowService.crear(PROCESO_ID, dto, USERNAME);

        assertThat(throwGuardado().getClaveCorrelacion()).isNull();
        assertThat(throwGuardado().getComportamientoSinCaso()).isNull();
    }

    @Test
    void elUsuarioDeSoloLecturaNoCreaMessageThrow() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> messageThrowService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(MessageThrowService.SIN_PERMISO_ESCRITURA);

        noSeModificoNada();
    }

    @Test
    void crearRechazaQueOrigenYDestinoSeanElMismoPool() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        when(poolService.poolDestinoDeMensaje(any(Proceso.class), eq(POOL_ALPES))).thenReturn(alpes);
        CrearMessageThrowDto dto = formularioCreacion();
        dto.setPoolDestinoId(POOL_ALPES);

        assertThatThrownBy(() -> messageThrowService.crear(PROCESO_ID, dto, USERNAME))
                .isInstanceOf(MensajeEntreMismoPoolException.class)
                .hasMessage("El pool 'Alpes" + EventoService.MISMO_POOL);

        verify(poolService, never()).poolParaNodo(any(Proceso.class), anyLong());
        noSeModificoNada();
    }

    @Test
    void crearRechazaUnPoolDestinoQueNoEsDelDiagrama() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        when(poolService.poolDestinoDeMensaje(any(Proceso.class), eq(POOL_BANCO)))
                .thenThrow(new PoolMensajeNoValidoException(PoolService.POOL_MENSAJE_AJENO));

        assertThatThrownBy(() -> messageThrowService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(PoolMensajeNoValidoException.class)
                .hasMessage(PoolService.POOL_MENSAJE_AJENO);

        noSeModificoNada();
    }

    @Test
    void crearRechazaUnPoolOrigenQueNoEsDelProceso() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        when(poolService.poolDestinoDeMensaje(any(Proceso.class), eq(POOL_BANCO))).thenReturn(banco);
        when(poolService.poolParaNodo(any(Proceso.class), eq(POOL_ALPES)))
                .thenThrow(new RecursoNoEncontradoException("El pool no existe en este proceso"));

        assertThatThrownBy(() -> messageThrowService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);

        noSeModificoNada();
    }

    @Test
    void crearRechazaUnProcesoEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, true);

        assertThatThrownBy(() -> messageThrowService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");

        noSeModificoNada();
    }

    @Test
    void unaEmpresaInvitadaConsultaLosMessageThrowPeroNoLosModifica() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);
        when(procesoRepository.estaCompartidoCon(PROCESO_ID, EMPRESA_PROPIA)).thenReturn(true);
        when(messageThrowRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(messageThrow(true)));

        assertThat(messageThrowService.listar(PROCESO_ID, USERNAME)).extracting(MessageThrowRespuestaDto::getId)
                .containsExactly(THROW_ID);
        assertThatThrownBy(() -> messageThrowService.crear(PROCESO_ID, formularioCreacion(), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);
        assertThatThrownBy(() -> messageThrowService.eliminar(PROCESO_ID, THROW_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);
        noSeModificoNada();
    }

    @Test
    void unaEmpresaNoInvitadaNoConsultaLosMessageThrow() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);
        when(procesoRepository.estaCompartidoCon(PROCESO_ID, EMPRESA_PROPIA)).thenReturn(false);

        assertThatThrownBy(() -> messageThrowService.listar(PROCESO_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);
    }

    @Test
    void obtenerDevuelveElThrowConSuCorrelacion() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProcesoActivo();
        existeElThrow(true);

        MessageThrowRespuestaDto obtenido = messageThrowService.obtener(PROCESO_ID, THROW_ID, USERNAME);

        assertThat(obtenido.getNombreMensaje()).isEqualTo(NOMBRE);
        assertThat(obtenido.getClaveCorrelacion()).isEqualTo("factura");
        assertThat(obtenido.isActivo()).isTrue();
    }

    @Test
    void obtenerRechazaUnThrowQueNoEsDeEsteProceso() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        when(messageThrowRepository.findByIdAndProcesoId(THROW_ID, PROCESO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageThrowService.obtener(PROCESO_ID, THROW_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(MessageThrowService.THROW_NO_EXISTE);
    }

    @Test
    void editarRegistraSoloLosCamposModificados() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        MessageThrow messageThrow = existeElThrow(true);
        when(poolService.poolDestinoDeMensaje(any(Proceso.class), eq(POOL_PROVEEDOR))).thenReturn(proveedor);

        MessageThrowRespuestaDto editado = messageThrowService.editar(PROCESO_ID, THROW_ID,
                new EditarMessageThrowDto(NOMBRE, "numeroFactura: texto", POOL_PROVEEDOR, " radicado ",
                        ComportamientoSinCaso.INICIAR_NUEVO_CASO),
                USERNAME);

        assertThat(messageThrow.getPoolDestino()).isSameAs(proveedor);
        assertThat(messageThrow.getClaveCorrelacion()).isEqualTo("radicado");
        assertThat(editado.getPoolDestinoNombre()).isEqualTo("Proveedor");
        verify(messageThrowRepository).save(messageThrow);
        assertThat(historialGuardado().getCambiosRealizados()).isEqualTo("message throw '" + NOMBRE
                + "': pool destino: 'Banco' -> 'Proveedor'; clave de correlacion: 'factura' -> 'radicado';"
                + " comportamiento sin caso: '' -> 'INICIAR_NUEVO_CASO'");
    }

    @Test
    void editarCambiaElNombreYElContenido() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        MessageThrow messageThrow = existeElThrow(true);
        when(poolService.poolDestinoDeMensaje(any(Proceso.class), eq(POOL_BANCO))).thenReturn(banco);

        messageThrowService.editar(PROCESO_ID, THROW_ID, new EditarMessageThrowDto("Orden de pago",
                "numeroOrden: texto", POOL_BANCO, "factura", null), USERNAME);

        assertThat(messageThrow.getNombreMensaje()).isEqualTo("Orden de pago");
        assertThat(historialGuardado().getCambiosRealizados()).isEqualTo("message throw '" + NOMBRE
                + "': nombre: '" + NOMBRE + "' -> 'Orden de pago'; contenido: 'numeroFactura: texto' ->"
                + " 'numeroOrden: texto'");
    }

    @Test
    void editarSinCambiosNoGuardaNiRegistraHistorial() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        existeElThrow(true);
        when(poolService.poolDestinoDeMensaje(any(Proceso.class), eq(POOL_BANCO))).thenReturn(banco);

        MessageThrowRespuestaDto editado = messageThrowService.editar(PROCESO_ID, THROW_ID,
                new EditarMessageThrowDto(" " + NOMBRE, "numeroFactura: texto ", POOL_BANCO, "factura", null),
                USERNAME);

        assertThat(editado.getId()).isEqualTo(THROW_ID);
        noSeModificoNada();
    }

    @Test
    void editarRechazaDirigirElMensajeAlPoolDeOrigen() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        existeElThrow(true);
        when(poolService.poolDestinoDeMensaje(any(Proceso.class), eq(POOL_ALPES))).thenReturn(alpes);

        assertThatThrownBy(() -> messageThrowService.editar(PROCESO_ID, THROW_ID,
                new EditarMessageThrowDto(NOMBRE, "numeroFactura: texto", POOL_ALPES, null, null), USERNAME))
                .isInstanceOf(MensajeEntreMismoPoolException.class);

        noSeModificoNada();
    }

    @Test
    void editarRechazaUnThrowYaEliminado() {
        autenticar(RolUsuario.EDITOR);
        existeElProcesoActivo();
        existeElThrow(false);

        assertThatThrownBy(() -> messageThrowService.editar(PROCESO_ID, THROW_ID,
                new EditarMessageThrowDto(NOMBRE, "x", POOL_BANCO, null, null), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(MessageThrowService.THROW_ELIMINADO);
    }

    @Test
    void elUsuarioDeSoloLecturaNoEditaMessageThrow() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> messageThrowService.editar(PROCESO_ID, THROW_ID,
                new EditarMessageThrowDto(NOMBRE, "x", POOL_BANCO, null, null), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        noSeModificoNada();
    }

    @Test
    void elEditorNoEliminaMessageThrow() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> messageThrowService.eliminar(PROCESO_ID, THROW_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(MessageThrowService.SIN_PERMISO_ELIMINAR);
        assertThatThrownBy(() -> messageThrowService.obtenerParaEliminar(PROCESO_ID, THROW_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(eventoRepository, never()).save(any(Evento.class));
    }

    @Test
    void laConsultaPreviaAdelantaLosArcosYElCatchQueQuedaSinThrow() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElThrow(true);
        when(arcoRepository.conectadosAlNodo(PROCESO_ID, TipoNodoFlujo.EVENTO, THROW_ID)).thenReturn(List.of(
                new Arco(60L, proceso(EMPRESA_PROPIA, false), TipoNodoFlujo.ACTIVIDAD, 30L, TipoNodoFlujo.EVENTO,
                        THROW_ID, null, null, true)));
        when(messageThrowRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(messageThrow(true)));
        when(messageCatchRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(PROCESO_ID))
                .thenReturn(List.of(catchEnElBanco(NOMBRE, "factura")));

        MessageThrowRespuestaDto previa = messageThrowService.obtenerParaEliminar(PROCESO_ID, THROW_ID, USERNAME);

        assertThat(previa.getAdvertencias()).contains(
                "Se desactivará 1 arco conectado a Message Throw: " + NOMBRE + ".",
                "El Message Catch '" + NOMBRE + "' del pool 'Banco' quedará sin Message Throw homólogo.");
        verify(eventoRepository, never()).save(any(Evento.class));
        verify(arcoRepository, never()).saveAll(anyList());
    }

    @Test
    void elAdministradorEliminaDeFormaLogicaYDesactivaSusArcos() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        MessageThrow messageThrow = existeElThrow(true);
        Arco entrante = new Arco(60L, proceso(EMPRESA_PROPIA, false), TipoNodoFlujo.ACTIVIDAD, 30L,
                TipoNodoFlujo.EVENTO, THROW_ID, null, null, true);
        when(arcoRepository.conectadosAlNodo(PROCESO_ID, TipoNodoFlujo.EVENTO, THROW_ID)).thenReturn(List.of(entrante));

        MessageThrowRespuestaDto eliminado = messageThrowService.eliminar(PROCESO_ID, THROW_ID, USERNAME);

        assertThat(messageThrow.isActivo()).isFalse();
        assertThat(entrante.isActivo()).isFalse();
        assertThat(eliminado.isActivo()).isFalse();
        assertThat(eliminado.getArcosDesactivados()).isEqualTo(1);
        verify(eventoRepository).save(messageThrow);
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("message throw eliminado: '" + NOMBRE + "'; arcos desactivados: 1");
    }

    @Test
    void unaSegundaEliminacionSeRechazaComoRecursoInexistente() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProcesoActivo();
        existeElThrow(false);

        assertThatThrownBy(() -> messageThrowService.eliminar(PROCESO_ID, THROW_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(MessageThrowService.THROW_ELIMINADO);

        verify(eventoRepository, never()).save(any(Evento.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }
}
