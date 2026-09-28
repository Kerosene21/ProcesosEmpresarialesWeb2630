package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.OperacionEstructura;
import co.edu.javeriana.procesosempresariales.domain.PermisoEstructuraProceso;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.ConfigurarPermisoEstructuraDto;
import co.edu.javeriana.procesosempresariales.dto.PermisoEstructuraDto;
import co.edu.javeriana.procesosempresariales.exception.PermisoEstructuraNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.PermisoEstructuraProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class PermisoEstructuraServiceTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final Long PROCESO_ID = 5L;

    @Mock
    private PermisoEstructuraProcesoRepository permisoEstructuraProcesoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProcesoRepository procesoRepository;

    @Mock
    private HistorialProcesoRepository historialProcesoRepository;

    private PermisoEstructuraService permisoEstructuraService;

    @BeforeEach
    void inicializar() {
        UsuarioService usuarios = new UsuarioService(usuarioRepository, new ModelMapper(),
                new BCryptPasswordEncoder());
        AccesoProcesoService acceso = new AccesoProcesoService(usuarios, procesoRepository);
        permisoEstructuraService = new PermisoEstructuraService(permisoEstructuraProcesoRepository, acceso,
                new HistorialProcesoService(historialProcesoRepository));
    }

    private Empresa empresa(Long id) {
        return new Empresa(id, "Empresa " + id, "900123456-" + id, "contacto" + id + "@alpes.com");
    }

    private Usuario usuario(RolUsuario rol) {
        return new Usuario(1L, USERNAME, HASH, rol, true, empresa(EMPRESA_PROPIA));
    }

    private void autenticar(RolUsuario rol) {
        when(usuarioRepository.findByUsername(USERNAME)).thenReturn(Optional.of(usuario(rol)));
    }

    private Proceso proceso(Long empresaId, boolean eliminado) {
        return new Proceso(PROCESO_ID, "Ventas", "Proceso comercial", "Comercial", EstadoProceso.BORRADOR,
                empresa(empresaId), new ArrayList<>(), eliminado);
    }

    private Proceso existeElProceso(Long empresaId, boolean eliminado) {
        Proceso proceso = proceso(empresaId, eliminado);
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.of(proceso));
        return proceso;
    }

    private PermisoEstructuraProceso editorConfigurado(boolean eliminarPool, boolean eliminarLane) {
        PermisoEstructuraProceso permiso = new PermisoEstructuraProceso(3L, null, RolUsuario.EDITOR, true, true,
                eliminarPool, true, true, eliminarLane);
        when(permisoEstructuraProcesoRepository.findByProcesoIdAndRol(PROCESO_ID, RolUsuario.EDITOR))
                .thenReturn(Optional.of(permiso));
        return permiso;
    }

    private ConfigurarPermisoEstructuraDto todos(boolean valor) {
        return new ConfigurarPermisoEstructuraDto(valor, valor, valor, valor, valor, valor);
    }

    private PermisoEstructuraDto fila(List<PermisoEstructuraDto> matriz, RolUsuario rol) {
        return matriz.stream().filter(permiso -> permiso.getRol() == rol).findFirst().orElseThrow();
    }

    private void noSeModificoNada() {
        verify(permisoEstructuraProcesoRepository, never()).save(any(PermisoEstructuraProceso.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void elAdministradorPuedeTodasLasOperacionesDeEstructura() {
        Proceso proceso = proceso(EMPRESA_PROPIA, false);

        for (OperacionEstructura operacion : OperacionEstructura.values()) {
            assertThat(permisoEstructuraService.permite(proceso, usuario(RolUsuario.ADMINISTRADOR), operacion))
                    .isTrue();
        }
        verify(permisoEstructuraProcesoRepository, never()).findByProcesoIdAndRol(anyLong(), any(RolUsuario.class));
    }

    @Test
    void elUsuarioDeSoloLecturaNuncaModificaLaEstructura() {
        Proceso proceso = proceso(EMPRESA_PROPIA, false);

        for (OperacionEstructura operacion : OperacionEstructura.values()) {
            assertThat(permisoEstructuraService.permite(proceso, usuario(RolUsuario.SOLO_LECTURA), operacion))
                    .isFalse();
        }
    }

    @Test
    void elEditorSinConfiguracionCreaYEditaPeroNoElimina() {
        Proceso proceso = proceso(EMPRESA_PROPIA, false);
        Usuario editor = usuario(RolUsuario.EDITOR);

        assertThat(permisoEstructuraService.permite(proceso, editor, OperacionEstructura.CREAR_POOL)).isTrue();
        assertThat(permisoEstructuraService.permite(proceso, editor, OperacionEstructura.EDITAR_POOL)).isTrue();
        assertThat(permisoEstructuraService.permite(proceso, editor, OperacionEstructura.ELIMINAR_POOL)).isFalse();
        assertThat(permisoEstructuraService.permite(proceso, editor, OperacionEstructura.CREAR_LANE)).isTrue();
        assertThat(permisoEstructuraService.permite(proceso, editor, OperacionEstructura.EDITAR_LANE)).isTrue();
        assertThat(permisoEstructuraService.permite(proceso, editor, OperacionEstructura.ELIMINAR_LANE)).isFalse();
    }

    @Test
    void elEditorUsaLaConfiguracionGuardadaDelProceso() {
        editorConfigurado(true, false);

        assertThat(permisoEstructuraService.permite(proceso(EMPRESA_PROPIA, false), usuario(RolUsuario.EDITOR),
                OperacionEstructura.ELIMINAR_POOL)).isTrue();
    }

    @Test
    void exigirRechazaUnaOperacionNoPermitidaConUnMensajeClaro() {
        Proceso proceso = proceso(EMPRESA_PROPIA, false);
        Usuario editor = usuario(RolUsuario.EDITOR);

        assertThatThrownBy(() -> permisoEstructuraService.exigir(proceso, editor, OperacionEstructura.ELIMINAR_LANE))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El rol EDITOR no tiene permiso para eliminar lanes en este proceso");
        assertThatCode(() -> permisoEstructuraService.exigir(proceso, editor, OperacionEstructura.CREAR_LANE))
                .doesNotThrowAnyException();
    }

    @Test
    void cualquierUsuarioDeLaEmpresaConsultaLaMatrizDePermisos() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_PROPIA, false);

        List<PermisoEstructuraDto> matriz = permisoEstructuraService.consultar(PROCESO_ID, USERNAME);

        assertThat(matriz).extracting(PermisoEstructuraDto::getRol)
                .containsExactly(RolUsuario.ADMINISTRADOR, RolUsuario.EDITOR, RolUsuario.SOLO_LECTURA);
        assertThat(matriz).extracting(PermisoEstructuraDto::isConfigurable).containsExactly(false, true, false);
        PermisoEstructuraDto administrador = fila(matriz, RolUsuario.ADMINISTRADOR);
        assertThat(administrador.isCrearPool() && administrador.isEliminarPool() && administrador.isEliminarLane())
                .isTrue();
        PermisoEstructuraDto editor = fila(matriz, RolUsuario.EDITOR);
        assertThat(editor.isCrearPool()).isTrue();
        assertThat(editor.isEliminarPool()).isFalse();
        PermisoEstructuraDto lector = fila(matriz, RolUsuario.SOLO_LECTURA);
        assertThat(lector.isCrearPool() || lector.isEditarPool() || lector.isEliminarPool() || lector.isCrearLane()
                || lector.isEditarLane() || lector.isEliminarLane()).isFalse();
    }

    @Test
    void unaEmpresaAjenaNoConsultaLosPermisos() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> permisoEstructuraService.consultar(PROCESO_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);
    }

    @Test
    void elAdministradorConfiguraLosPermisosDelEditorYQuedaEnElHistorial() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProceso(EMPRESA_PROPIA, false);

        List<PermisoEstructuraDto> matriz = permisoEstructuraService.configurar(PROCESO_ID, RolUsuario.EDITOR,
                new ConfigurarPermisoEstructuraDto(true, true, true, false, true, true), USERNAME);

        ArgumentCaptor<PermisoEstructuraProceso> capturado = ArgumentCaptor.forClass(PermisoEstructuraProceso.class);
        verify(permisoEstructuraProcesoRepository).save(capturado.capture());
        PermisoEstructuraProceso guardado = capturado.getValue();
        assertThat(guardado.getProceso()).isSameAs(proceso);
        assertThat(guardado.getRol()).isEqualTo(RolUsuario.EDITOR);
        assertThat(guardado.isEliminarPool()).isTrue();
        assertThat(guardado.isCrearLane()).isFalse();
        assertThat(guardado.isEliminarLane()).isTrue();
        ArgumentCaptor<HistorialProceso> historial = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(historial.capture());
        assertThat(historial.getValue().getCambiosRealizados()).isEqualTo("permisos de estructura del rol EDITOR:"
                + " eliminar pools: false -> true; crear lanes: true -> false; eliminar lanes: false -> true");
        assertThat(fila(matriz, RolUsuario.EDITOR).isEliminarPool()).isTrue();
        assertThat(fila(matriz, RolUsuario.EDITOR).isCrearLane()).isFalse();
    }

    @Test
    void reconfigurarActualizaLaMismaFilaDelEditor() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        PermisoEstructuraProceso existente = editorConfigurado(true, true);

        List<PermisoEstructuraDto> matriz = permisoEstructuraService.configurar(PROCESO_ID, RolUsuario.EDITOR,
                new ConfigurarPermisoEstructuraDto(true, true, false, true, true, false), USERNAME);

        verify(permisoEstructuraProcesoRepository).save(existente);
        assertThat(existente.isEliminarPool()).isFalse();
        assertThat(existente.isEliminarLane()).isFalse();
        assertThat(fila(matriz, RolUsuario.EDITOR).isEliminarLane()).isFalse();
    }

    @Test
    void configurarSinCambiosNoGuardaNiRegistraHistorial() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);

        permisoEstructuraService.configurar(PROCESO_ID, RolUsuario.EDITOR,
                new ConfigurarPermisoEstructuraDto(true, true, false, true, true, false), USERNAME);

        noSeModificoNada();
    }

    @Test
    void noSePuedeDarPermisosDeEscrituraASoloLectura() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> permisoEstructuraService.configurar(PROCESO_ID, RolUsuario.SOLO_LECTURA,
                new ConfigurarPermisoEstructuraDto(false, false, false, true, false, false), USERNAME))
                .isInstanceOf(PermisoEstructuraNoValidoException.class)
                .hasMessage(PermisoEstructuraService.LECTURA_FIJA);

        noSeModificoNada();
    }

    @Test
    void confirmarQueSoloLecturaNoModificaEsAceptadoSinGuardarNada() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);

        List<PermisoEstructuraDto> matriz = permisoEstructuraService.configurar(PROCESO_ID, RolUsuario.SOLO_LECTURA,
                todos(false), USERNAME);

        assertThat(matriz).hasSize(3);
        noSeModificoNada();
    }

    @Test
    void noSePuedenQuitarPermisosAlAdministrador() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> permisoEstructuraService.configurar(PROCESO_ID, RolUsuario.ADMINISTRADOR,
                new ConfigurarPermisoEstructuraDto(true, true, false, true, true, true), USERNAME))
                .isInstanceOf(PermisoEstructuraNoValidoException.class)
                .hasMessage(PermisoEstructuraService.ADMINISTRADOR_FIJO);

        noSeModificoNada();
    }

    @Test
    void confirmarQueElAdministradorLoPuedeTodoEsAceptadoSinGuardarNada() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);

        permisoEstructuraService.configurar(PROCESO_ID, RolUsuario.ADMINISTRADOR, todos(true), USERNAME);

        noSeModificoNada();
    }

    @Test
    void elEditorNoConfiguraPermisos() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> permisoEstructuraService.configurar(PROCESO_ID, RolUsuario.EDITOR, todos(true),
                USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(PermisoEstructuraService.SIN_PERMISO_CONFIGURAR);

        noSeModificoNada();
    }

    @Test
    void elAdministradorDeUnaEmpresaInvitadaNoConfiguraPermisos() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> permisoEstructuraService.configurar(PROCESO_ID, RolUsuario.EDITOR, todos(true),
                USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");

        noSeModificoNada();
    }

    @Test
    void noSeConfiguranPermisosEnUnProcesoEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, true);

        assertThatThrownBy(() -> permisoEstructuraService.configurar(PROCESO_ID, RolUsuario.EDITOR, todos(true),
                USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);

        noSeModificoNada();
    }
}
