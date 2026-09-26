package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AccesoProcesoServiceTest {

    private static final String USERNAME = "editor@alpes.com";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final String MENSAJE = "Operacion no permitida para este rol";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final Long PROCESO_ID = 5L;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProcesoRepository procesoRepository;

    private AccesoProcesoService accesoProcesoService;

    @BeforeEach
    void inicializar() {
        UsuarioService usuarioService = new UsuarioService(usuarioRepository, new ModelMapper(),
                new BCryptPasswordEncoder());
        accesoProcesoService = new AccesoProcesoService(usuarioService, procesoRepository);
    }

    private Empresa empresa(Long id) {
        return new Empresa(id, "Empresa " + id, "900123456-" + id, "contacto" + id + "@alpes.com");
    }

    private Usuario usuario(RolUsuario rol) {
        return new Usuario(1L, USERNAME, HASH, rol, true, empresa(EMPRESA_PROPIA));
    }

    private Proceso proceso(Long empresaId, boolean eliminado) {
        return new Proceso(PROCESO_ID, "Ventas", "Proceso comercial", "Comercial", EstadoProceso.BORRADOR,
                empresa(empresaId), new Pool(80L, "Empresa " + empresaId, List.of()), eliminado);
    }

    private void existeElProceso(Proceso proceso) {
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.of(proceso));
    }

    @Test
    void elUsuarioAutenticadoSeResuelveDesdeElServicioDeUsuarios() {
        Usuario usuario = usuario(RolUsuario.EDITOR);
        when(usuarioRepository.findByUsername(USERNAME)).thenReturn(Optional.of(usuario));

        assertThat(accesoProcesoService.usuarioAutenticado(USERNAME)).isSameAs(usuario);
    }

    @Test
    void unUsuarioAutenticadoInexistenteNoPuedeOperar() {
        when(usuarioRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accesoProcesoService.usuarioAutenticado(USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El usuario autenticado no existe");
    }

    @Test
    void unProcesoDeLaEmpresaDelUsuarioSeEntrega() {
        Proceso proceso = proceso(EMPRESA_PROPIA, false);
        existeElProceso(proceso);

        assertThat(accesoProcesoService.procesoDeLaEmpresa(PROCESO_ID, usuario(RolUsuario.SOLO_LECTURA)))
                .isSameAs(proceso);
    }

    @Test
    void unProcesoInexistenteSeInformaComoNoEncontrado() {
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accesoProcesoService.procesoDeLaEmpresa(PROCESO_ID, usuario(RolUsuario.EDITOR)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso no existe");
    }

    @Test
    void unProcesoDeOtraEmpresaNuncaSeEntrega() {
        existeElProceso(proceso(EMPRESA_AJENA, false));

        assertThatThrownBy(() -> accesoProcesoService.procesoDeLaEmpresa(PROCESO_ID,
                usuario(RolUsuario.ADMINISTRADOR)))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");
    }

    @Test
    void unProcesoEliminadoSigueDisponibleParaConsulta() {
        Proceso eliminado = proceso(EMPRESA_PROPIA, true);
        existeElProceso(eliminado);

        assertThat(accesoProcesoService.procesoDeLaEmpresa(PROCESO_ID, usuario(RolUsuario.EDITOR)))
                .isSameAs(eliminado);
    }

    @Test
    void unProcesoEliminadoNoSeEntregaParaModificarlo() {
        existeElProceso(proceso(EMPRESA_PROPIA, true));

        assertThatThrownBy(() -> accesoProcesoService.procesoActivoDeLaEmpresa(PROCESO_ID,
                usuario(RolUsuario.ADMINISTRADOR)))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");
    }

    @Test
    void unProcesoActivoDeOtraEmpresaTampocoSeEntregaParaModificarlo() {
        existeElProceso(proceso(EMPRESA_AJENA, false));

        assertThatThrownBy(() -> accesoProcesoService.procesoActivoDeLaEmpresa(PROCESO_ID,
                usuario(RolUsuario.ADMINISTRADOR)))
                .isInstanceOf(UsuarioSinPermisoException.class);
    }

    @Test
    void unProcesoActivoDeLaEmpresaSeEntregaParaModificarlo() {
        Proceso proceso = proceso(EMPRESA_PROPIA, false);
        existeElProceso(proceso);

        assertThat(accesoProcesoService.procesoActivoDeLaEmpresa(PROCESO_ID, usuario(RolUsuario.EDITOR)))
                .isSameAs(proceso);
    }

    @Test
    void administradorYEditorTienenRolDeEscrituraPeroSoloLecturaNo() {
        assertThat(accesoProcesoService.tieneRolDeEscritura(usuario(RolUsuario.ADMINISTRADOR))).isTrue();
        assertThat(accesoProcesoService.tieneRolDeEscritura(usuario(RolUsuario.EDITOR))).isTrue();
        assertThat(accesoProcesoService.tieneRolDeEscritura(usuario(RolUsuario.SOLO_LECTURA))).isFalse();
    }

    @Test
    void soloElAdministradorEsAdministrador() {
        assertThat(accesoProcesoService.esAdministrador(usuario(RolUsuario.ADMINISTRADOR))).isTrue();
        assertThat(accesoProcesoService.esAdministrador(usuario(RolUsuario.EDITOR))).isFalse();
        assertThat(accesoProcesoService.esAdministrador(usuario(RolUsuario.SOLO_LECTURA))).isFalse();
    }

    @Test
    void validarRolDeEscrituraRechazaSoloLecturaConElMensajeIndicado() {
        Usuario lector = usuario(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> accesoProcesoService.validarRolDeEscritura(lector, MENSAJE))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(MENSAJE);
    }

    @Test
    void validarRolDeEscrituraAceptaAlEditor() {
        Usuario editor = usuario(RolUsuario.EDITOR);

        assertThatCode(() -> accesoProcesoService.validarRolDeEscritura(editor, MENSAJE))
                .doesNotThrowAnyException();
    }

    @Test
    void validarRolAdministradorRechazaAlEditorConElMensajeIndicado() {
        Usuario editor = usuario(RolUsuario.EDITOR);

        assertThatThrownBy(() -> accesoProcesoService.validarRolAdministrador(editor, MENSAJE))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(MENSAJE);
    }

    @Test
    void validarRolAdministradorAceptaAlAdministrador() {
        Usuario administrador = usuario(RolUsuario.ADMINISTRADOR);

        assertThatCode(() -> accesoProcesoService.validarRolAdministrador(administrador, MENSAJE))
                .doesNotThrowAnyException();
    }
}
