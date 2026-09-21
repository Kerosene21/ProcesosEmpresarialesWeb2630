package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroProcesosDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoResumenDto;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadProceso;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.ModeloDeProcesoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.NombreProcesoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class ProcesoServiceTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final String HASH_CREDENCIAL = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;

    @Mock
    private ProcesoRepository procesoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private HistorialProcesoRepository historialProcesoRepository;

    @Mock
    private ValidacionModeloService validacionModeloService;

    private ProcesoService procesoService;

    @BeforeEach
    void inicializar() {
        procesoService = new ProcesoService(procesoRepository, usuarioRepository, historialProcesoRepository,
                validacionModeloService, new ModelMapper());
    }

    private Empresa empresa(Long id, String nombre) {
        return new Empresa(id, nombre, "900123456-7", "contacto@alpes.com");
    }

    private Usuario usuarioAutenticado(RolUsuario rol) {
        return new Usuario(1L, USERNAME, HASH_CREDENCIAL, rol, true, empresa(EMPRESA_PROPIA, "Alpes Logistica"));
    }

    private void autenticar(Usuario usuario) {
        when(usuarioRepository.findByUsername(USERNAME)).thenReturn(Optional.of(usuario));
    }

    private void sinUsuarioAutenticado() {
        when(usuarioRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
    }

    private void asignarIdentificadoresAlGuardar(Long procesoId, Long poolId) {
        when(procesoRepository.save(any(Proceso.class))).thenAnswer(invocacion -> {
            Proceso guardado = invocacion.getArgument(0);
            guardado.setId(procesoId);
            guardado.getPool().setId(poolId);
            return guardado;
        });
    }

    private Proceso procesoExistente(Long empresaId) {
        return procesoExistente(empresaId, false);
    }

    private Proceso procesoExistente(Long empresaId, boolean eliminado) {
        return new Proceso(5L, "Ventas", "Proceso comercial", "Comercial", EstadoProceso.BORRADOR,
                empresa(empresaId, "Alpes Logistica"), new Pool(80L, "Alpes Logistica", List.of()), eliminado);
    }

    private void existeElProceso(Proceso proceso) {
        when(procesoRepository.findById(proceso.getId())).thenReturn(Optional.of(proceso));
    }

    private Proceso procesoGuardado() {
        ArgumentCaptor<Proceso> capturado = ArgumentCaptor.forClass(Proceso.class);
        verify(procesoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private HistorialProceso historialGuardado() {
        ArgumentCaptor<HistorialProceso> capturado = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private CrearProcesoDto formularioCreacion() {
        return new CrearProcesoDto("Ventas", "Proceso comercial de la compania", "Comercial");
    }

    private EditarProcesoDto formularioEdicion() {
        return new EditarProcesoDto("Ventas Corporativas", "Descripcion actualizada", "Operaciones",
                EstadoProceso.PUBLICADO);
    }

    @Test
    void crearIdentificaAlUsuarioPorSuUsername() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        asignarIdentificadoresAlGuardar(30L, 80L);

        procesoService.crear(formularioCreacion(), USERNAME);

        verify(usuarioRepository).findByUsername(USERNAME);
    }

    @Test
    void crearRechazaUnUsuarioAutenticadoQueNoExiste() {
        sinUsuarioAutenticado();

        assertThatThrownBy(() -> procesoService.crear(formularioCreacion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El usuario autenticado no existe");

        verify(procesoRepository, never()).save(any(Proceso.class));
    }

    @Test
    void crearBuscaNombresDuplicadosUnicamenteEnLaEmpresaDelUsuario() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        asignarIdentificadoresAlGuardar(30L, 80L);

        procesoService.crear(formularioCreacion(), USERNAME);

        verify(procesoRepository).existsByEmpresaIdAndNombreIgnoreCase(EMPRESA_PROPIA, "Ventas");
    }

    @Test
    void crearRechazaUnNombreYaUsadoEnLaMismaEmpresa() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        when(procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(EMPRESA_PROPIA, "Ventas")).thenReturn(true);

        assertThatThrownBy(() -> procesoService.crear(formularioCreacion(), USERNAME))
                .isInstanceOf(NombreProcesoDuplicadoException.class)
                .hasMessage("Ya existe un proceso con ese nombre en la empresa");

        verify(procesoRepository, never()).save(any(Proceso.class));
    }

    @Test
    void crearGuardaElProcesoCuandoElNombreEstaDisponible() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        asignarIdentificadoresAlGuardar(30L, 80L);

        procesoService.crear(formularioCreacion(), USERNAME);

        assertThat(procesoGuardado().getNombre()).isEqualTo("Ventas");
    }

    @Test
    void crearDejaElPoolConUnaLaneInicialParaUbicarActividades() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        asignarIdentificadoresAlGuardar(30L, 80L);

        procesoService.crear(formularioCreacion(), USERNAME);

        Pool pool = procesoGuardado().getPool();
        assertThat(pool.getLanes()).hasSize(1);
        assertThat(pool.getLanes().get(0).getNombre()).isEqualTo("General");
        assertThat(pool.getLanes().get(0).getPool()).isSameAs(pool);
    }

    @Test
    void crearAdmiteElMismoNombreEnUnaEmpresaDistinta() {
        Usuario otraEmpresa = new Usuario(2L, USERNAME, HASH_CREDENCIAL, RolUsuario.ADMINISTRADOR, true,
                empresa(EMPRESA_AJENA, "Andes Consultores"));
        autenticar(otraEmpresa);
        when(procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(anyLong(), eq("Ventas")))
                .thenAnswer(invocacion -> EMPRESA_PROPIA.equals(invocacion.getArgument(0)));
        asignarIdentificadoresAlGuardar(31L, 81L);

        procesoService.crear(formularioCreacion(), USERNAME);

        verify(procesoRepository).existsByEmpresaIdAndNombreIgnoreCase(EMPRESA_AJENA, "Ventas");
        assertThat(procesoGuardado().getEmpresa().getId()).isEqualTo(EMPRESA_AJENA);
    }

    @Test
    void crearDejaElProcesoEnEstadoBorrador() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        asignarIdentificadoresAlGuardar(30L, 80L);

        ProcesoRespuestaDto respuesta = procesoService.crear(formularioCreacion(), USERNAME);

        assertThat(procesoGuardado().getEstado()).isEqualTo(EstadoProceso.BORRADOR);
        assertThat(respuesta.getEstado()).isEqualTo(EstadoProceso.BORRADOR);
    }

    @Test
    void crearAsociaElProcesoALaEmpresaDelUsuarioAutenticado() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        asignarIdentificadoresAlGuardar(30L, 80L);

        procesoService.crear(formularioCreacion(), USERNAME);

        assertThat(procesoGuardado().getEmpresa().getId()).isEqualTo(EMPRESA_PROPIA);
    }

    @Test
    void crearPreparaElPoolInicialConElNombreDeLaEmpresa() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        asignarIdentificadoresAlGuardar(30L, 80L);

        procesoService.crear(formularioCreacion(), USERNAME);

        Pool pool = procesoGuardado().getPool();
        assertThat(pool).isNotNull();
        assertThat(pool.getNombre()).isEqualTo("Alpes Logistica");
    }

    @Test
    void crearDevuelveElIdentificadorDelPoolAsociadoAlProceso() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        asignarIdentificadoresAlGuardar(30L, 80L);

        ProcesoRespuestaDto respuesta = procesoService.crear(formularioCreacion(), USERNAME);

        assertThat(respuesta.getPoolId()).isEqualTo(80L);
    }

    @Test
    void crearEliminaLosEspaciosSobrantesDelNombre() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        asignarIdentificadoresAlGuardar(30L, 80L);

        procesoService.crear(new CrearProcesoDto("   Ventas   ", "Proceso comercial", "Comercial"), USERNAME);

        verify(procesoRepository).existsByEmpresaIdAndNombreIgnoreCase(EMPRESA_PROPIA, "Ventas");
        assertThat(procesoGuardado().getNombre()).isEqualTo("Ventas");
    }

    @Test
    void crearDevuelveLosDatosDelProcesoRegistrado() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        asignarIdentificadoresAlGuardar(30L, 80L);

        ProcesoRespuestaDto respuesta = procesoService.crear(formularioCreacion(), USERNAME);

        assertThat(respuesta.getId()).isEqualTo(30L);
        assertThat(respuesta.getNombre()).isEqualTo("Ventas");
        assertThat(respuesta.getDescripcion()).isEqualTo("Proceso comercial de la compania");
        assertThat(respuesta.getCategoria()).isEqualTo("Comercial");
    }

    @Test
    void crearTraduceLaViolacionDeIntegridadEnNombreDuplicado() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        when(procesoRepository.save(any(Proceso.class)))
                .thenThrow(new DataIntegrityViolationException("uk_proceso_empresa_nombre"));

        assertThatThrownBy(() -> procesoService.crear(formularioCreacion(), USERNAME))
                .isInstanceOf(NombreProcesoDuplicadoException.class)
                .hasMessage("Ya existe un proceso con ese nombre en la empresa");
    }

    @Test
    void puedeEditarAutorizaAlAdministrador() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));

        assertThat(procesoService.puedeEditar(USERNAME)).isTrue();
    }

    @Test
    void puedeEditarAutorizaAlEditor() {
        autenticar(usuarioAutenticado(RolUsuario.EDITOR));

        assertThat(procesoService.puedeEditar(USERNAME)).isTrue();
    }

    @Test
    void puedeEditarNiegaAlUsuarioDeSoloLectura() {
        autenticar(usuarioAutenticado(RolUsuario.SOLO_LECTURA));

        assertThat(procesoService.puedeEditar(USERNAME)).isFalse();
    }

    @Test
    void puedeEditarRechazaUnUsuarioAutenticadoQueNoExiste() {
        sinUsuarioAutenticado();

        assertThatThrownBy(() -> procesoService.puedeEditar(USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void obtenerDevuelveElProcesoDeLaEmpresaDelUsuario() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        ProcesoRespuestaDto respuesta = procesoService.obtener(5L, USERNAME);

        assertThat(respuesta.getId()).isEqualTo(5L);
        assertThat(respuesta.getNombre()).isEqualTo("Ventas");
        assertThat(respuesta.getPoolId()).isEqualTo(80L);
    }

    @Test
    void obtenerEstaPermitidoParaUnUsuarioDeSoloLectura() {
        autenticar(usuarioAutenticado(RolUsuario.SOLO_LECTURA));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        assertThat(procesoService.obtener(5L, USERNAME).getNombre()).isEqualTo("Ventas");
    }

    @Test
    void obtenerRechazaUnUsuarioAutenticadoQueNoExiste() {
        sinUsuarioAutenticado();

        assertThatThrownBy(() -> procesoService.obtener(5L, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El usuario autenticado no existe");
    }

    @Test
    void obtenerRechazaUnProcesoInexistente() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        when(procesoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> procesoService.obtener(404L, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso no existe");
    }

    @Test
    void obtenerRechazaUnProcesoDeOtraEmpresa() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_AJENA));

        assertThatThrownBy(() -> procesoService.obtener(5L, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");
    }

    @Test
    void editarRechazaUnUsuarioAutenticadoQueNoExiste() {
        sinUsuarioAutenticado();

        assertThatThrownBy(() -> procesoService.editar(5L, formularioEdicion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(procesoRepository, never()).save(any(Proceso.class));
    }

    @Test
    void editarPermiteAlAdministrador() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, formularioEdicion(), USERNAME);

        verify(procesoRepository).save(any(Proceso.class));
    }

    @Test
    void editarPermiteAlEditor() {
        autenticar(usuarioAutenticado(RolUsuario.EDITOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, formularioEdicion(), USERNAME);

        verify(procesoRepository).save(any(Proceso.class));
    }

    @Test
    void editarRechazaAlUsuarioDeSoloLectura() {
        autenticar(usuarioAutenticado(RolUsuario.SOLO_LECTURA));

        assertThatThrownBy(() -> procesoService.editar(5L, formularioEdicion(), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("Solo un administrador o editor puede crear o modificar procesos");

        verify(procesoRepository, never()).save(any(Proceso.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void editarRechazaUnProcesoInexistente() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        when(procesoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> procesoService.editar(404L, formularioEdicion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso no existe");
    }

    @Test
    void editarRechazaUnProcesoDeOtraEmpresa() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_AJENA));

        assertThatThrownBy(() -> procesoService.editar(5L, formularioEdicion(), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(procesoRepository, never()).save(any(Proceso.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void editarActualizaNombreDescripcionYCategoria() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, formularioEdicion(), USERNAME);

        Proceso guardado = procesoGuardado();
        assertThat(guardado.getNombre()).isEqualTo("Ventas Corporativas");
        assertThat(guardado.getDescripcion()).isEqualTo("Descripcion actualizada");
        assertThat(guardado.getCategoria()).isEqualTo("Operaciones");
    }

    @Test
    void editarLlevaElProcesoDeBorradorAPublicado() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        ProcesoRespuestaDto respuesta = procesoService.editar(5L, formularioEdicion(), USERNAME);

        assertThat(procesoGuardado().getEstado()).isEqualTo(EstadoProceso.PUBLICADO);
        assertThat(respuesta.getEstado()).isEqualTo(EstadoProceso.PUBLICADO);
    }

    @Test
    void editarEliminaLosEspaciosSobrantesDelNombre() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, new EditarProcesoDto("   Ventas Corporativas   ", "Descripcion actualizada",
                "Operaciones", EstadoProceso.PUBLICADO), USERNAME);

        assertThat(procesoGuardado().getNombre()).isEqualTo("Ventas Corporativas");
    }

    @Test
    void editarRechazaUnNombreYaUsadoPorOtroProcesoDeLaEmpresa() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));
        when(procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(EMPRESA_PROPIA, "Ventas Corporativas"))
                .thenReturn(true);

        assertThatThrownBy(() -> procesoService.editar(5L, formularioEdicion(), USERNAME))
                .isInstanceOf(NombreProcesoDuplicadoException.class);

        verify(procesoRepository, never()).save(any(Proceso.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void editarConservaElMismoNombreSinReportarUnDuplicadoInexistente() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, new EditarProcesoDto("Ventas", "Otra descripcion", "Comercial",
                EstadoProceso.PUBLICADO), USERNAME);

        verify(procesoRepository, never()).existsByEmpresaIdAndNombreIgnoreCase(anyLong(), anyString());
        verify(procesoRepository).save(any(Proceso.class));
    }

    @Test
    void editarAdmiteCambiarSoloLasMayusculasDelNombre() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, new EditarProcesoDto("VENTAS", "Otra descripcion", "Comercial",
                EstadoProceso.BORRADOR), USERNAME);

        verify(procesoRepository, never()).existsByEmpresaIdAndNombreIgnoreCase(anyLong(), anyString());
        assertThat(procesoGuardado().getNombre()).isEqualTo("VENTAS");
    }

    @Test
    void editarDevuelveElProcesoActualizado() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        ProcesoRespuestaDto respuesta = procesoService.editar(5L, formularioEdicion(), USERNAME);

        assertThat(respuesta.getId()).isEqualTo(5L);
        assertThat(respuesta.getNombre()).isEqualTo("Ventas Corporativas");
        assertThat(respuesta.getCategoria()).isEqualTo("Operaciones");
        assertThat(respuesta.getPoolId()).isEqualTo(80L);
    }

    @Test
    void editarRegistraUnaEntradaDeHistorialPorCadaModificacion() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, formularioEdicion(), USERNAME);

        verify(historialProcesoRepository).save(any(HistorialProceso.class));
    }

    @Test
    void editarAsociaElHistorialAlProcesoYAlUsuarioQueLoModifico() {
        Usuario editor = usuarioAutenticado(RolUsuario.EDITOR);
        autenticar(editor);
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, formularioEdicion(), USERNAME);

        HistorialProceso historial = historialGuardado();
        assertThat(historial.getProceso().getId()).isEqualTo(5L);
        assertThat(historial.getUsuario()).isSameAs(editor);
        assertThat(historial.getUsuario().getUsername()).isEqualTo(USERNAME);
    }

    @Test
    void editarRegistraLaFechaDelCambioEnElHistorial() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));
        LocalDateTime antes = LocalDateTime.now();

        procesoService.editar(5L, formularioEdicion(), USERNAME);

        LocalDateTime fecha = historialGuardado().getFecha();
        assertThat(fecha).isNotNull();
        assertThat(fecha).isBetween(antes, LocalDateTime.now());
    }

    @Test
    void editarGuardaElEstadoAnteriorEnElHistorial() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, formularioEdicion(), USERNAME);

        assertThat(historialGuardado().getEstadoAnterior()).isEqualTo("BORRADOR");
    }

    @Test
    void editarResumeLosValoresAnterioresYNuevosDeTodosLosCamposModificados() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, formularioEdicion(), USERNAME);

        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("nombre: 'Ventas' -> 'Ventas Corporativas'; "
                        + "descripcion: 'Proceso comercial' -> 'Descripcion actualizada'; "
                        + "categoria: 'Comercial' -> 'Operaciones'; estado: 'BORRADOR' -> 'PUBLICADO'");
    }

    @Test
    void crearRechazaAlUsuarioDeSoloLectura() {
        autenticar(usuarioAutenticado(RolUsuario.SOLO_LECTURA));

        assertThatThrownBy(() -> procesoService.crear(formularioCreacion(), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(procesoRepository, never()).save(any(Proceso.class));
    }

    @Test
    void crearCompruebaElRolAntesDeBuscarNombresDuplicados() {
        autenticar(usuarioAutenticado(RolUsuario.SOLO_LECTURA));

        assertThatThrownBy(() -> procesoService.crear(formularioCreacion(), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(procesoRepository, never()).existsByEmpresaIdAndNombreIgnoreCase(anyLong(), anyString());
    }

    @Test
    void crearPermiteAlAdministrador() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        asignarIdentificadoresAlGuardar(30L, 80L);

        ProcesoRespuestaDto respuesta = procesoService.crear(formularioCreacion(), USERNAME);

        assertThat(respuesta.getId()).isEqualTo(30L);
    }

    @Test
    void crearPermiteAlEditor() {
        autenticar(usuarioAutenticado(RolUsuario.EDITOR));
        asignarIdentificadoresAlGuardar(31L, 81L);

        ProcesoRespuestaDto respuesta = procesoService.crear(formularioCreacion(), USERNAME);

        assertThat(respuesta.getId()).isEqualTo(31L);
        assertThat(procesoGuardado().getEstado()).isEqualTo(EstadoProceso.BORRADOR);
        assertThat(procesoGuardado().getPool()).isNotNull();
    }

    @Test
    void editarSinNingunCambioNoRegistraHistorial() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, new EditarProcesoDto("Ventas", "Proceso comercial", "Comercial",
                EstadoProceso.BORRADOR), USERNAME);

        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void editarSinNingunCambioTampocoVuelveAGuardarElProceso() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, new EditarProcesoDto("  Ventas  ", "Proceso comercial", "Comercial",
                EstadoProceso.BORRADOR), USERNAME);

        verify(procesoRepository, never()).save(any(Proceso.class));
    }

    @Test
    void editarSinNingunCambioDevuelveElProcesoTalComoEsta() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        ProcesoRespuestaDto respuesta = procesoService.editar(5L, new EditarProcesoDto("Ventas",
                "Proceso comercial", "Comercial", EstadoProceso.BORRADOR), USERNAME);

        assertThat(respuesta.getNombre()).isEqualTo("Ventas");
        assertThat(respuesta.getEstado()).isEqualTo(EstadoProceso.BORRADOR);
    }

    @Test
    void editarSoloElNombreResumeUnicamenteElNombre() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, new EditarProcesoDto("Ventas Corporativas", "Proceso comercial", "Comercial",
                EstadoProceso.BORRADOR), USERNAME);

        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("nombre: 'Ventas' -> 'Ventas Corporativas'");
    }

    @Test
    void editarSoloLaDescripcionResumeUnicamenteLaDescripcion() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, new EditarProcesoDto("Ventas", "Descripcion actualizada", "Comercial",
                EstadoProceso.BORRADOR), USERNAME);

        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("descripcion: 'Proceso comercial' -> 'Descripcion actualizada'");
    }

    @Test
    void editarSoloLaCategoriaResumeUnicamenteLaCategoria() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, new EditarProcesoDto("Ventas", "Proceso comercial", "Operaciones",
                EstadoProceso.BORRADOR), USERNAME);

        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("categoria: 'Comercial' -> 'Operaciones'");
    }

    @Test
    void editarSoloElEstadoResumeUnicamenteElEstado() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, new EditarProcesoDto("Ventas", "Proceso comercial", "Comercial",
                EstadoProceso.PUBLICADO), USERNAME);

        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("estado: 'BORRADOR' -> 'PUBLICADO'");
    }

    @Test
    void elHistorialDeUnCambioParcialConservaUsuarioFechaYEstadoAnterior() {
        Usuario editor = usuarioAutenticado(RolUsuario.EDITOR);
        autenticar(editor);
        existeElProceso(procesoExistente(EMPRESA_PROPIA));
        LocalDateTime antes = LocalDateTime.now();

        procesoService.editar(5L, new EditarProcesoDto("Ventas", "Proceso comercial", "Comercial",
                EstadoProceso.PUBLICADO), USERNAME);

        HistorialProceso historial = historialGuardado();
        assertThat(historial.getUsuario()).isSameAs(editor);
        assertThat(historial.getEstadoAnterior()).isEqualTo("BORRADOR");
        assertThat(historial.getFecha()).isBetween(antes, LocalDateTime.now());
    }

    @Test
    void consultarHistorialDevuelveLasEntradasDelProcesoDeLaEmpresaDelUsuario() {
        Usuario editor = usuarioAutenticado(RolUsuario.EDITOR);
        autenticar(editor);
        Proceso proceso = procesoExistente(EMPRESA_PROPIA);
        existeElProceso(proceso);
        when(historialProcesoRepository.findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(5L, EMPRESA_PROPIA))
                .thenReturn(List.of(
                        new HistorialProceso(2L, proceso, editor, LocalDateTime.now(),
                                "estado: 'BORRADOR' -> 'PUBLICADO'", "BORRADOR"),
                        new HistorialProceso(1L, proceso, editor, LocalDateTime.now().minusDays(1),
                                "nombre: 'Ventas' -> 'Ventas Corporativas'", "BORRADOR")));

        List<HistorialProcesoRespuestaDto> historial = procesoService.consultarHistorial(5L, USERNAME);

        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).getCambiosRealizados()).isEqualTo("estado: 'BORRADOR' -> 'PUBLICADO'");
        assertThat(historial.get(0).getUsuarioCorreo()).isEqualTo(USERNAME);
        assertThat(historial.get(0).getEstadoAnterior()).isEqualTo("BORRADOR");
        assertThat(historial.get(0).getFecha()).isNotNull();
    }

    @Test
    void consultarHistorialPideLasEntradasOrdenadasPorFechaDescendenteYAcotadasALaEmpresa() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));
        when(historialProcesoRepository.findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(5L, EMPRESA_PROPIA))
                .thenReturn(List.of());

        procesoService.consultarHistorial(5L, USERNAME);

        verify(historialProcesoRepository).findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(5L, EMPRESA_PROPIA);
        verify(historialProcesoRepository, never()).findAll();
    }

    @Test
    void consultarHistorialEstaPermitidoParaUnUsuarioDeSoloLectura() {
        autenticar(usuarioAutenticado(RolUsuario.SOLO_LECTURA));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));
        when(historialProcesoRepository.findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(5L, EMPRESA_PROPIA))
                .thenReturn(List.of());

        assertThat(procesoService.consultarHistorial(5L, USERNAME)).isEmpty();
    }

    @Test
    void consultarHistorialRechazaUnProcesoDeOtraEmpresa() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_AJENA));

        assertThatThrownBy(() -> procesoService.consultarHistorial(5L, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(historialProcesoRepository, never())
                .findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(anyLong(), anyLong());
    }

    @Test
    void consultarHistorialRechazaUnProcesoInexistente() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        when(procesoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> procesoService.consultarHistorial(404L, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void consultarHistorialRechazaUnUsuarioAutenticadoQueNoExiste() {
        sinUsuarioAutenticado();

        assertThatThrownBy(() -> procesoService.consultarHistorial(5L, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void unProcesoNuevoNaceSinEstarEliminado() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        asignarIdentificadoresAlGuardar(30L, 80L);

        ProcesoRespuestaDto respuesta = procesoService.crear(formularioCreacion(), USERNAME);

        assertThat(procesoGuardado().isEliminado()).isFalse();
        assertThat(respuesta.isEliminado()).isFalse();
    }

    @Test
    void unAdministradorEliminaUnProceso() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        ProcesoRespuestaDto respuesta = procesoService.eliminar(5L, USERNAME);

        assertThat(respuesta.isEliminado()).isTrue();
    }

    @Test
    void unEditorNoPuedeEliminarUnProcesoAunqueSiPuedaEditarlo() {
        autenticar(usuarioAutenticado(RolUsuario.EDITOR));

        assertThatThrownBy(() -> procesoService.eliminar(5L, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("Solo un administrador puede eliminar procesos");

        verify(procesoRepository, never()).save(any(Proceso.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void unUsuarioDeSoloLecturaNoPuedeEliminarUnProceso() {
        autenticar(usuarioAutenticado(RolUsuario.SOLO_LECTURA));

        assertThatThrownBy(() -> procesoService.eliminar(5L, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(procesoRepository, never()).save(any(Proceso.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void eliminarCompruebaElRolAntesDeBuscarElProceso() {
        autenticar(usuarioAutenticado(RolUsuario.EDITOR));

        assertThatThrownBy(() -> procesoService.eliminar(5L, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(procesoRepository, never()).findById(anyLong());
    }

    @Test
    void obtenerParaEliminarDevuelveElProcesoActivoAlAdministrador() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        ProcesoRespuestaDto respuesta = procesoService.obtenerParaEliminar(5L, USERNAME);

        assertThat(respuesta.getNombre()).isEqualTo("Ventas");
        assertThat(respuesta.isEliminado()).isFalse();
        verify(procesoRepository, never()).save(any(Proceso.class));
    }

    @Test
    void obtenerParaEliminarExigeRolAdministrador() {
        autenticar(usuarioAutenticado(RolUsuario.EDITOR));

        assertThatThrownBy(() -> procesoService.obtenerParaEliminar(5L, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(procesoRepository, never()).findById(anyLong());
    }

    @Test
    void obtenerParaEliminarRechazaUnProcesoYaEliminado() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA, true));

        assertThatThrownBy(() -> procesoService.obtenerParaEliminar(5L, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");
    }

    @Test
    void obtenerParaEliminarRechazaUnProcesoDeOtraEmpresa() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_AJENA));

        assertThatThrownBy(() -> procesoService.obtenerParaEliminar(5L, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);
    }

    @Test
    void puedeEliminarSoloAutorizaAlAdministrador() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));

        assertThat(procesoService.puedeEliminar(USERNAME)).isTrue();
    }

    @Test
    void puedeEliminarNiegaAlEditor() {
        autenticar(usuarioAutenticado(RolUsuario.EDITOR));

        assertThat(procesoService.puedeEliminar(USERNAME)).isFalse();
    }

    @Test
    void puedeEliminarNiegaAlUsuarioDeSoloLectura() {
        autenticar(usuarioAutenticado(RolUsuario.SOLO_LECTURA));

        assertThat(procesoService.puedeEliminar(USERNAME)).isFalse();
    }

    @Test
    void unEditorSiguePudiendoCrearYEditarAunqueNoPuedaEliminar() {
        autenticar(usuarioAutenticado(RolUsuario.EDITOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        ProcesoRespuestaDto respuesta = procesoService.editar(5L, formularioEdicion(), USERNAME);

        assertThat(respuesta.getNombre()).isEqualTo("Ventas Corporativas");
        assertThat(procesoService.puedeEditar(USERNAME)).isTrue();
    }

    @Test
    void eliminarMarcaElProcesoComoEliminadoSinTocarSusDatos() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.eliminar(5L, USERNAME);

        Proceso guardado = procesoGuardado();
        assertThat(guardado.isEliminado()).isTrue();
        assertThat(guardado.getId()).isEqualTo(5L);
        assertThat(guardado.getNombre()).isEqualTo("Ventas");
        assertThat(guardado.getDescripcion()).isEqualTo("Proceso comercial");
        assertThat(guardado.getCategoria()).isEqualTo("Comercial");
        assertThat(guardado.getEstado()).isEqualTo(EstadoProceso.BORRADOR);
        assertThat(guardado.getEmpresa().getId()).isEqualTo(EMPRESA_PROPIA);
        assertThat(guardado.getPool().getId()).isEqualTo(80L);
    }

    @Test
    void eliminarNuncaBorraFisicamenteElProceso() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.eliminar(5L, USERNAME);

        verify(procesoRepository, never()).delete(any(Proceso.class));
        verify(procesoRepository, never()).deleteById(anyLong());
        verify(procesoRepository).save(any(Proceso.class));
    }

    @Test
    void eliminarRechazaUnProcesoDeOtraEmpresa() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_AJENA));

        assertThatThrownBy(() -> procesoService.eliminar(5L, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(procesoRepository, never()).save(any(Proceso.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void eliminarRechazaUnProcesoInexistente() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        when(procesoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> procesoService.eliminar(404L, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void eliminarRechazaUnUsuarioAutenticadoQueNoExiste() {
        sinUsuarioAutenticado();

        assertThatThrownBy(() -> procesoService.eliminar(5L, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void eliminarRegistraLaEliminacionEnElHistorialConUsuarioFechaYEstadoAnterior() {
        Usuario administrador = usuarioAutenticado(RolUsuario.ADMINISTRADOR);
        autenticar(administrador);
        existeElProceso(procesoExistente(EMPRESA_PROPIA));
        LocalDateTime antes = LocalDateTime.now();

        procesoService.eliminar(5L, USERNAME);

        HistorialProceso historial = historialGuardado();
        assertThat(historial.getProceso().getId()).isEqualTo(5L);
        assertThat(historial.getUsuario()).isSameAs(administrador);
        assertThat(historial.getFecha()).isBetween(antes, LocalDateTime.now());
        assertThat(historial.getEstadoAnterior()).isEqualTo("BORRADOR");
        assertThat(historial.getCambiosRealizados()).isEqualTo("proceso eliminado");
    }

    @Test
    void eliminarDosVecesRechazaLaSegundaEliminacion() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA, true));

        assertThatThrownBy(() -> procesoService.eliminar(5L, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");

        verify(procesoRepository, never()).save(any(Proceso.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void editarRechazaUnProcesoEliminado() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA, true));

        assertThatThrownBy(() -> procesoService.editar(5L, formularioEdicion(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");

        verify(procesoRepository, never()).save(any(Proceso.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void obtenerDevuelveElProcesoEliminadoMarcadoComoTalParaPoderAuditarlo() {
        autenticar(usuarioAutenticado(RolUsuario.SOLO_LECTURA));
        existeElProceso(procesoExistente(EMPRESA_PROPIA, true));

        ProcesoRespuestaDto respuesta = procesoService.obtener(5L, USERNAME);

        assertThat(respuesta.getId()).isEqualTo(5L);
        assertThat(respuesta.getNombre()).isEqualTo("Ventas");
        assertThat(respuesta.isEliminado()).isTrue();
    }

    @Test
    void consultarHistorialSigueDisponibleParaUnProcesoEliminado() {
        Usuario editor = usuarioAutenticado(RolUsuario.EDITOR);
        autenticar(editor);
        Proceso eliminado = procesoExistente(EMPRESA_PROPIA, true);
        existeElProceso(eliminado);
        when(historialProcesoRepository.findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(5L, EMPRESA_PROPIA))
                .thenReturn(List.of(
                        new HistorialProceso(2L, eliminado, editor, LocalDateTime.now(), "proceso eliminado",
                                "BORRADOR"),
                        new HistorialProceso(1L, eliminado, editor, LocalDateTime.now().minusDays(1),
                                "estado: 'BORRADOR' -> 'PUBLICADO'", "BORRADOR")));

        List<HistorialProcesoRespuestaDto> historial = procesoService.consultarHistorial(5L, USERNAME);

        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).getCambiosRealizados()).isEqualTo("proceso eliminado");
        assertThat(historial.get(1).getCambiosRealizados()).isEqualTo("estado: 'BORRADOR' -> 'PUBLICADO'");
    }

    @Test
    void elNombreDeUnProcesoEliminadoSigueReservadoEnLaEmpresa() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        when(procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(EMPRESA_PROPIA, "Ventas")).thenReturn(true);

        assertThatThrownBy(() -> procesoService.crear(formularioCreacion(), USERNAME))
                .isInstanceOf(NombreProcesoDuplicadoException.class);

        verify(procesoRepository, never()).save(any(Proceso.class));
    }

    private void devolverPagina(Proceso... procesos) {
        when(procesoRepository.findAll(ArgumentMatchers.<Specification<Proceso>>any(), any(Pageable.class)))
                .thenAnswer(invocacion -> new PageImpl<>(List.of(procesos), invocacion.getArgument(1),
                        procesos.length));
    }

    private Pageable paginacionUsada() {
        ArgumentCaptor<Pageable> capturada = ArgumentCaptor.forClass(Pageable.class);
        verify(procesoRepository).findAll(ArgumentMatchers.<Specification<Proceso>>any(), capturada.capture());
        return capturada.getValue();
    }

    @Test
    void consultarProcesosDevuelveLosProcesosDeLaEmpresaDelUsuarioComoResumen() {
        autenticar(usuarioAutenticado(RolUsuario.SOLO_LECTURA));
        devolverPagina(procesoExistente(EMPRESA_PROPIA));

        Page<ProcesoResumenDto> pagina = procesoService.consultarProcesos(new FiltroProcesosDto(), USERNAME);

        assertThat(pagina.getContent()).hasSize(1);
        ProcesoResumenDto resumen = pagina.getContent().get(0);
        assertThat(resumen.getId()).isEqualTo(5L);
        assertThat(resumen.getNombre()).isEqualTo("Ventas");
        assertThat(resumen.getCategoria()).isEqualTo("Comercial");
        assertThat(resumen.getEstado()).isEqualTo(EstadoProceso.BORRADOR);
        assertThat(resumen.isEliminado()).isFalse();
        assertThat(resumen.getDescripcion()).isEqualTo("Proceso comercial");
    }

    @Test
    void consultarProcesosNuncaUsaUnListadoSinFiltroDeEmpresa() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        devolverPagina();

        procesoService.consultarProcesos(new FiltroProcesosDto(), USERNAME);

        verify(procesoRepository, never()).findAll();
        verify(procesoRepository).findAll(ArgumentMatchers.<Specification<Proceso>>any(), any(Pageable.class));
    }

    @Test
    void consultarProcesosExigeUnUsuarioAutenticadoReal() {
        sinUsuarioAutenticado();

        assertThatThrownBy(() -> procesoService.consultarProcesos(new FiltroProcesosDto(), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void consultarProcesosEstaPermitidoParaLosTresRoles() {
        devolverPagina(procesoExistente(EMPRESA_PROPIA));

        for (RolUsuario rol : RolUsuario.values()) {
            autenticar(usuarioAutenticado(rol));

            assertThat(procesoService.consultarProcesos(new FiltroProcesosDto(), USERNAME).getContent()).hasSize(1);
        }
    }

    @Test
    void consultarProcesosUsaLaPaginaSolicitadaConUnTamanoEstable() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        devolverPagina();
        FiltroProcesosDto filtro = new FiltroProcesosDto();
        filtro.setPage(2);

        procesoService.consultarProcesos(filtro, USERNAME);

        Pageable paginacion = paginacionUsada();
        assertThat(paginacion.getPageNumber()).isEqualTo(2);
        assertThat(paginacion.getPageSize()).isEqualTo(10);
        assertThat(paginacion.getSort().isSorted()).isTrue();
    }

    @Test
    void consultarProcesosCorrigeUnNumeroDePaginaNegativoEnLugarDeFallar() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        devolverPagina();
        FiltroProcesosDto filtro = new FiltroProcesosDto();
        filtro.setPage(-3);

        Page<ProcesoResumenDto> pagina = procesoService.consultarProcesos(filtro, USERNAME);

        assertThat(paginacionUsada().getPageNumber()).isZero();
        assertThat(pagina.getContent()).isEmpty();
    }

    @Test
    void consultarProcesosDevuelvePaginaVaciaCuandoSePideUnaPaginaFueraDeRango() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        when(procesoRepository.findAll(ArgumentMatchers.<Specification<Proceso>>any(), any(Pageable.class)))
                .thenAnswer(invocacion -> new PageImpl<>(List.of(), invocacion.getArgument(1), 3));
        FiltroProcesosDto filtro = new FiltroProcesosDto();
        filtro.setPage(99);

        Page<ProcesoResumenDto> pagina = procesoService.consultarProcesos(filtro, USERNAME);

        assertThat(pagina.getContent()).isEmpty();
        assertThat(pagina.getTotalElements()).isEqualTo(3);
    }

    @Test
    void consultarProcesosNormalizaLosFiltrosDeTextoEnBlancoComoAusentes() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        devolverPagina();
        FiltroProcesosDto filtro = new FiltroProcesosDto();
        filtro.setQ("   ");
        filtro.setCategoria("  ");

        procesoService.consultarProcesos(filtro, USERNAME);

        assertThat(filtro.getQ()).isNull();
        assertThat(filtro.getCategoria()).isNull();
    }

    @Test
    void consultarProcesosRecortaLosFiltrosDeTexto() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        devolverPagina();
        FiltroProcesosDto filtro = new FiltroProcesosDto();
        filtro.setQ("  ventas  ");
        filtro.setCategoria("  Comercial  ");

        procesoService.consultarProcesos(filtro, USERNAME);

        assertThat(filtro.getQ()).isEqualTo("ventas");
        assertThat(filtro.getCategoria()).isEqualTo("Comercial");
    }

    @Test
    void consultarProcesosUsaVisibilidadDeActivosCuandoNoSeIndicaNinguna() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        devolverPagina();
        FiltroProcesosDto filtro = new FiltroProcesosDto();
        filtro.setVisibilidad(null);

        procesoService.consultarProcesos(filtro, USERNAME);

        assertThat(filtro.getVisibilidad()).isEqualTo(VisibilidadProceso.ACTIVOS);
    }

    @Test
    void consultarProcesosConservaLaVisibilidadDeInactivosCuandoSePideExplicitamente() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        devolverPagina(procesoExistente(EMPRESA_PROPIA, true));
        FiltroProcesosDto filtro = new FiltroProcesosDto();
        filtro.setVisibilidad(VisibilidadProceso.INACTIVOS);

        Page<ProcesoResumenDto> pagina = procesoService.consultarProcesos(filtro, USERNAME);

        assertThat(filtro.getVisibilidad()).isEqualTo(VisibilidadProceso.INACTIVOS);
        assertThat(pagina.getContent().get(0).isEliminado()).isTrue();
    }

    @Test
    void elResumenRecortaLasDescripcionesLargasSinPerderElTextoCompletoEnElDetalle() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        Proceso largo = procesoExistente(EMPRESA_PROPIA);
        largo.setDescripcion("D".repeat(200));
        devolverPagina(largo);

        Page<ProcesoResumenDto> pagina = procesoService.consultarProcesos(new FiltroProcesosDto(), USERNAME);

        String descripcion = pagina.getContent().get(0).getDescripcion();
        assertThat(descripcion).hasSizeLessThan(200).endsWith("...");
    }

    @Test
    void elDetalleExponeElPoolDelDiagramaQueHoyExiste() {
        autenticar(usuarioAutenticado(RolUsuario.SOLO_LECTURA));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        ProcesoRespuestaDto respuesta = procesoService.obtener(5L, USERNAME);

        assertThat(respuesta.getPoolId()).isEqualTo(80L);
        assertThat(respuesta.getPoolNombre()).isEqualTo("Alpes Logistica");
    }

    @Test
    void lasCategoriasDisponiblesSeConsultanSoloParaLaEmpresaDelUsuario() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        when(procesoRepository.categoriasDeLaEmpresa(EMPRESA_PROPIA)).thenReturn(List.of("Comercial", "Operaciones"));

        List<String> categorias = procesoService.categoriasDisponibles(USERNAME);

        assertThat(categorias).containsExactly("Comercial", "Operaciones");
        verify(procesoRepository).categoriasDeLaEmpresa(EMPRESA_PROPIA);
        verify(procesoRepository, never()).categoriasDeLaEmpresa(EMPRESA_AJENA);
    }

    @Test
    void crearEliminaLosEspaciosSobrantesDeLaCategoria() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        asignarIdentificadoresAlGuardar(30L, 80L);

        ProcesoRespuestaDto respuesta = procesoService.crear(
                new CrearProcesoDto("Ventas", "Proceso comercial", "  Comercial  "), USERNAME);

        assertThat(procesoGuardado().getCategoria()).isEqualTo("Comercial");
        assertThat(respuesta.getCategoria()).isEqualTo("Comercial");
    }

    @Test
    void editarEliminaLosEspaciosSobrantesDeLaCategoria() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        ProcesoRespuestaDto respuesta = procesoService.editar(5L, new EditarProcesoDto("Ventas",
                "Proceso comercial", "  Operaciones  ", EstadoProceso.BORRADOR), USERNAME);

        assertThat(procesoGuardado().getCategoria()).isEqualTo("Operaciones");
        assertThat(respuesta.getCategoria()).isEqualTo("Operaciones");
    }

    @Test
    void elHistorialResumeLaCategoriaYaRecortada() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, new EditarProcesoDto("Ventas", "Proceso comercial", "  Operaciones  ",
                EstadoProceso.BORRADOR), USERNAME);

        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("categoria: 'Comercial' -> 'Operaciones'");
    }

    @Test
    void anadirEspaciosAlrededorDeLaCategoriaNoCuentaComoCambio() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, new EditarProcesoDto("Ventas", "Proceso comercial", "  Comercial  ",
                EstadoProceso.BORRADOR), USERNAME);

        verify(procesoRepository, never()).save(any(Proceso.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void laCategoriaGuardadaCoincideConLaQueNormalizaElFiltroDeConsulta() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        asignarIdentificadoresAlGuardar(30L, 80L);
        procesoService.crear(new CrearProcesoDto("Ventas", "Proceso comercial", "  Comercial  "), USERNAME);
        String categoriaPersistida = procesoGuardado().getCategoria();

        FiltroProcesosDto filtro = new FiltroProcesosDto();
        filtro.setCategoria("  Comercial  ");
        devolverPagina();
        procesoService.consultarProcesos(filtro, USERNAME);

        assertThat(filtro.getCategoria()).isEqualTo(categoriaPersistida);
    }

    @Test
    void salirDeBorradorValidaElModeloDelProceso() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        Proceso proceso = procesoExistente(EMPRESA_PROPIA);
        existeElProceso(proceso);

        procesoService.editar(5L, formularioEdicion(), USERNAME);

        verify(validacionModeloService).validarParaSalirDeBorrador(proceso);
        assertThat(procesoGuardado().getEstado()).isEqualTo(EstadoProceso.PUBLICADO);
    }

    @Test
    void unModeloIncompletoImpideSalirDeBorradorYNoGuardaNada() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        Proceso proceso = procesoExistente(EMPRESA_PROPIA);
        existeElProceso(proceso);
        doThrow(new ModeloDeProcesoNoValidoException("El proceso no puede salir de borrador: Gateway EXCLUSIVO #12"))
                .when(validacionModeloService).validarParaSalirDeBorrador(proceso);

        assertThatThrownBy(() -> procesoService.editar(5L, formularioEdicion(), USERNAME))
                .isInstanceOf(ModeloDeProcesoNoValidoException.class);

        assertThat(proceso.getEstado()).isEqualTo(EstadoProceso.BORRADOR);
        verify(procesoRepository, never()).save(any(Proceso.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void editarSinSacarElProcesoDeBorradorNoValidaElModelo() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        existeElProceso(procesoExistente(EMPRESA_PROPIA));

        procesoService.editar(5L, new EditarProcesoDto("Ventas Corporativas", "Descripcion actualizada",
                "Operaciones", EstadoProceso.BORRADOR), USERNAME);

        verify(validacionModeloService, never()).validarParaSalirDeBorrador(any(Proceso.class));
        verify(procesoRepository).save(any(Proceso.class));
    }

    @Test
    void editarUnProcesoYaPublicadoNoVuelveAValidarElModelo() {
        autenticar(usuarioAutenticado(RolUsuario.ADMINISTRADOR));
        Proceso proceso = procesoExistente(EMPRESA_PROPIA);
        proceso.setEstado(EstadoProceso.PUBLICADO);
        existeElProceso(proceso);

        procesoService.editar(5L, formularioEdicion(), USERNAME);

        verify(validacionModeloService, never()).validarParaSalirDeBorrador(any(Proceso.class));
    }
}
