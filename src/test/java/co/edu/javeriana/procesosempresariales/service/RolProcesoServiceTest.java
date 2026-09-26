package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import co.edu.javeriana.procesosempresariales.domain.AccionRolProceso;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.HistorialRolProceso;
import co.edu.javeriana.procesosempresariales.domain.RolProceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CrearRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroRolesProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialRolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoUsoRolDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoResumenDto;
import co.edu.javeriana.procesosempresariales.dto.UsoRolProceso;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadRolProceso;
import co.edu.javeriana.procesosempresariales.exception.NombreRolProcesoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.RolProcesoEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.HistorialRolProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.RolProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.RolProcesoSpecifications;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class RolProcesoServiceTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final Long ROL_ID = 40L;
    private static final Long OTRO_ROL_ID = 41L;
    private static final String ANALISTA = "Analista de credito";
    private static final String DESCRIPCION = "Evalua el riesgo de cada solicitud";

    @Mock
    private RolProcesoRepository rolProcesoRepository;

    @Mock
    private HistorialRolProcesoRepository historialRolProcesoRepository;

    @Mock
    private RolProcesoSpecifications rolProcesoSpecifications;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProcesoRepository procesoRepository;

    private RolProcesoService rolProcesoService;

    @BeforeEach
    void inicializar() {
        UsuarioService usuarios = new UsuarioService(usuarioRepository, new ModelMapper(),
                new BCryptPasswordEncoder());
        AccesoProcesoService acceso = new AccesoProcesoService(usuarios, procesoRepository);
        rolProcesoService = new RolProcesoService(rolProcesoRepository, historialRolProcesoRepository,
                rolProcesoSpecifications, acceso);
    }

    private Empresa empresa(Long id) {
        return new Empresa(id, "Empresa " + id, "900123456-" + id, "contacto" + id + "@alpes.com");
    }

    private Usuario autenticar(RolUsuario rol) {
        Usuario usuario = new Usuario(1L, USERNAME, HASH, rol, true, empresa(EMPRESA_PROPIA));
        when(usuarioRepository.findByUsername(USERNAME)).thenReturn(Optional.of(usuario));
        return usuario;
    }

    private RolProceso rol(Long id, String nombre, Long empresaId, boolean activo) {
        return new RolProceso(id, nombre, DESCRIPCION, empresa(empresaId), activo);
    }

    private RolProceso existeElRol(Long empresaId, boolean activo) {
        RolProceso rol = rol(ROL_ID, ANALISTA, empresaId, activo);
        when(rolProcesoRepository.findById(ROL_ID)).thenReturn(Optional.of(rol));
        return rol;
    }

    private void asignarIdentificadorAlGuardar() {
        when(rolProcesoRepository.saveAndFlush(any(RolProceso.class))).thenAnswer(invocacion -> {
            RolProceso guardado = invocacion.getArgument(0);
            guardado.setId(ROL_ID);
            return guardado;
        });
    }

    private RolProceso rolGuardado() {
        ArgumentCaptor<RolProceso> capturado = ArgumentCaptor.forClass(RolProceso.class);
        verify(rolProcesoRepository).saveAndFlush(capturado.capture());
        return capturado.getValue();
    }

    private HistorialRolProceso historialGuardado() {
        ArgumentCaptor<HistorialRolProceso> capturado = ArgumentCaptor.forClass(HistorialRolProceso.class);
        verify(historialRolProcesoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private UsoRolProceso uso(Long rolId, Long procesoId, String proceso, Long laneId, long actividades) {
        return new UsoRolProceso(rolId, procesoId, proceso, laneId, actividades);
    }

    private void usadoEn(UsoRolProceso... usos) {
        when(rolProcesoRepository.usosEnProcesosActivos(List.of(ROL_ID))).thenReturn(List.of(usos));
    }

    private void noSeModificoNada() {
        verify(rolProcesoRepository, never()).saveAndFlush(any(RolProceso.class));
        verify(rolProcesoRepository, never()).save(any(RolProceso.class));
        verify(historialRolProcesoRepository, never()).save(any(HistorialRolProceso.class));
    }

    private void devolverPagina(List<RolProceso> roles) {
        when(rolProcesoRepository.findAll(ArgumentMatchers.<Specification<RolProceso>>any(), any(Pageable.class)))
                .thenAnswer(invocacion -> new PageImpl<>(roles, invocacion.getArgument(1), roles.size()));
    }

    private FiltroRolesProcesoDto filtro() {
        return new FiltroRolesProcesoDto(null, null, null);
    }

    @Test
    void elAdministradorCreaUnRolActivoEnSuEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        asignarIdentificadorAlGuardar();

        RolProcesoRespuestaDto creado = rolProcesoService.crear(
                new CrearRolProcesoDto("  " + ANALISTA + "  ", "  " + DESCRIPCION + "  "), USERNAME);

        RolProceso guardado = rolGuardado();
        assertThat(guardado.getNombre()).isEqualTo(ANALISTA);
        assertThat(guardado.getDescripcion()).isEqualTo(DESCRIPCION);
        assertThat(guardado.getEmpresa().getId()).isEqualTo(EMPRESA_PROPIA);
        assertThat(guardado.isActivo()).isTrue();
        assertThat(creado.getId()).isEqualTo(ROL_ID);
        assertThat(creado.getNombre()).isEqualTo(ANALISTA);
        assertThat(creado.getDescripcion()).isEqualTo(DESCRIPCION);
        assertThat(creado.isActivo()).isTrue();
    }

    @Test
    void laCreacionQuedaEnElHistorialDelRol() {
        Usuario administrador = autenticar(RolUsuario.ADMINISTRADOR);
        asignarIdentificadorAlGuardar();

        rolProcesoService.crear(new CrearRolProcesoDto(ANALISTA, DESCRIPCION), USERNAME);

        HistorialRolProceso historial = historialGuardado();
        assertThat(historial.getId()).isNull();
        assertThat(historial.getRolProceso()).isSameAs(rolGuardado());
        assertThat(historial.getUsuario()).isSameAs(administrador);
        assertThat(historial.getAccion()).isEqualTo(AccionRolProceso.CREACION);
        assertThat(historial.getFecha()).isNotNull();
        assertThat(historial.getCambiosRealizados()).isEqualTo("rol de proceso creado: '" + ANALISTA + "'");
    }

    @Test
    void elEditorNoCreaRolesDeProceso() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> rolProcesoService.crear(new CrearRolProcesoDto(ANALISTA, DESCRIPCION), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(RolProcesoService.SIN_PERMISO_CREAR);

        noSeModificoNada();
        verify(rolProcesoRepository, never()).existsByEmpresaIdAndNombreIgnoreCase(anyLong(), anyString());
    }

    @Test
    void elUsuarioDeSoloLecturaNoCreaRolesDeProceso() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> rolProcesoService.crear(new CrearRolProcesoDto(ANALISTA, DESCRIPCION), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(RolProcesoService.SIN_PERMISO_CREAR);

        noSeModificoNada();
    }

    @Test
    void noSeRepiteElNombreDeUnRolDentroDeLaEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        when(rolProcesoRepository.existsByEmpresaIdAndNombreIgnoreCase(EMPRESA_PROPIA, ANALISTA)).thenReturn(true);

        assertThatThrownBy(() -> rolProcesoService.crear(new CrearRolProcesoDto(" " + ANALISTA, DESCRIPCION),
                USERNAME))
                .isInstanceOf(NombreRolProcesoDuplicadoException.class)
                .hasMessage(RolProcesoService.NOMBRE_DUPLICADO);

        noSeModificoNada();
    }

    @Test
    void laUnicidadDelNombreSoloSeRevisaEnLaEmpresaDelUsuario() {
        autenticar(RolUsuario.ADMINISTRADOR);
        asignarIdentificadorAlGuardar();

        rolProcesoService.crear(new CrearRolProcesoDto(ANALISTA, DESCRIPCION), USERNAME);

        verify(rolProcesoRepository).existsByEmpresaIdAndNombreIgnoreCase(EMPRESA_PROPIA, ANALISTA);
        verify(rolProcesoRepository, never()).existsByEmpresaIdAndNombreIgnoreCase(eq(EMPRESA_AJENA), anyString());
    }

    @Test
    void unaViolacionDeUnicidadEnLaBaseDeDatosSeReportaComoNombreDuplicado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        when(rolProcesoRepository.saveAndFlush(any(RolProceso.class)))
                .thenThrow(new DataIntegrityViolationException("uk_rol_proceso_empresa_nombre"));

        assertThatThrownBy(() -> rolProcesoService.crear(new CrearRolProcesoDto(ANALISTA, DESCRIPCION), USERNAME))
                .isInstanceOf(NombreRolProcesoDuplicadoException.class);

        verify(historialRolProcesoRepository, never()).save(any(HistorialRolProceso.class));
    }

    @Test
    void unUsuarioAutenticadoInexistenteNoCreaRoles() {
        when(usuarioRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rolProcesoService.crear(new CrearRolProcesoDto(ANALISTA, DESCRIPCION), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);

        noSeModificoNada();
    }

    @Test
    void elAdministradorRenombraElRolConservandoSuIdentificadorYSuEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        RolProceso rol = existeElRol(EMPRESA_PROPIA, true);

        RolProcesoRespuestaDto editado = rolProcesoService.editar(ROL_ID,
                new EditarRolProcesoDto("Analista senior", DESCRIPCION), USERNAME);

        assertThat(rolGuardado()).isSameAs(rol);
        assertThat(rol.getId()).isEqualTo(ROL_ID);
        assertThat(rol.getNombre()).isEqualTo("Analista senior");
        assertThat(rol.getEmpresa().getId()).isEqualTo(EMPRESA_PROPIA);
        assertThat(editado.getId()).isEqualTo(ROL_ID);
        assertThat(editado.getNombre()).isEqualTo("Analista senior");
        assertThat(editado.isActivo()).isTrue();
    }

    @Test
    void elEditorModificaLaDescripcionPorqueEsUnUsuarioAutorizado() {
        autenticar(RolUsuario.EDITOR);
        RolProceso rol = existeElRol(EMPRESA_PROPIA, true);

        RolProcesoRespuestaDto editado = rolProcesoService.editar(ROL_ID,
                new EditarRolProcesoDto(ANALISTA, "Aprueba o rechaza solicitudes"), USERNAME);

        assertThat(rol.getDescripcion()).isEqualTo("Aprueba o rechaza solicitudes");
        assertThat(editado.getDescripcion()).isEqualTo("Aprueba o rechaza solicitudes");
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("descripcion: '" + DESCRIPCION + "' -> 'Aprueba o rechaza solicitudes'");
    }

    @Test
    void elUsuarioDeSoloLecturaNoModificaRolesDeProceso() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> rolProcesoService.editar(ROL_ID, new EditarRolProcesoDto("Otro", DESCRIPCION),
                USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(RolProcesoService.SIN_PERMISO_EDITAR);

        noSeModificoNada();
        verify(rolProcesoRepository, never()).findById(anyLong());
    }

    @Test
    void laEdicionQuedaEnElHistorialDelRolConLosValoresAnterioresYNuevos() {
        Usuario editor = autenticar(RolUsuario.EDITOR);
        RolProceso rol = existeElRol(EMPRESA_PROPIA, true);

        rolProcesoService.editar(ROL_ID, new EditarRolProcesoDto("Analista senior", "Nueva descripcion"), USERNAME);

        HistorialRolProceso historial = historialGuardado();
        assertThat(historial.getRolProceso()).isSameAs(rol);
        assertThat(historial.getUsuario()).isSameAs(editor);
        assertThat(historial.getAccion()).isEqualTo(AccionRolProceso.EDICION);
        assertThat(historial.getCambiosRealizados()).isEqualTo("nombre: '" + ANALISTA + "' -> 'Analista senior';"
                + " descripcion: '" + DESCRIPCION + "' -> 'Nueva descripcion'");
    }

    @Test
    void elNuevoNombreNoPuedeRepetirOtroRolDeLaEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        RolProceso rol = existeElRol(EMPRESA_PROPIA, true);
        when(rolProcesoRepository.existsByEmpresaIdAndNombreIgnoreCase(EMPRESA_PROPIA, "Supervisor"))
                .thenReturn(true);

        assertThatThrownBy(() -> rolProcesoService.editar(ROL_ID, new EditarRolProcesoDto("Supervisor", DESCRIPCION),
                USERNAME))
                .isInstanceOf(NombreRolProcesoDuplicadoException.class)
                .hasMessage(RolProcesoService.NOMBRE_DUPLICADO);

        assertThat(rol.getNombre()).isEqualTo(ANALISTA);
        noSeModificoNada();
    }

    @Test
    void cambiarSoloMayusculasDelNombreNoSeConsideraDuplicado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        RolProceso rol = existeElRol(EMPRESA_PROPIA, true);

        rolProcesoService.editar(ROL_ID, new EditarRolProcesoDto("ANALISTA DE CREDITO", DESCRIPCION), USERNAME);

        verify(rolProcesoRepository, never()).existsByEmpresaIdAndNombreIgnoreCase(anyLong(), anyString());
        assertThat(rol.getNombre()).isEqualTo("ANALISTA DE CREDITO");
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("nombre: '" + ANALISTA + "' -> 'ANALISTA DE CREDITO'");
    }

    @Test
    void unaEdicionSinCambiosNoGeneraHistorialNiGuarda() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElRol(EMPRESA_PROPIA, true);

        RolProcesoRespuestaDto respuesta = rolProcesoService.editar(ROL_ID,
                new EditarRolProcesoDto("  " + ANALISTA + " ", " " + DESCRIPCION), USERNAME);

        assertThat(respuesta.getNombre()).isEqualTo(ANALISTA);
        noSeModificoNada();
    }

    @Test
    void noSeModificaUnRolDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        RolProceso ajeno = existeElRol(EMPRESA_AJENA, true);

        assertThatThrownBy(() -> rolProcesoService.editar(ROL_ID, new EditarRolProcesoDto("Otro", DESCRIPCION),
                USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(RolProcesoService.ROL_AJENO);

        assertThat(ajeno.getNombre()).isEqualTo(ANALISTA);
        noSeModificoNada();
    }

    @Test
    void noSeModificaUnRolInexistente() {
        autenticar(RolUsuario.ADMINISTRADOR);
        when(rolProcesoRepository.findById(ROL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rolProcesoService.editar(ROL_ID, new EditarRolProcesoDto("Otro", DESCRIPCION),
                USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(RolProcesoService.ROL_NO_EXISTE);
    }

    @Test
    void noSeModificaUnRolEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElRol(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> rolProcesoService.editar(ROL_ID, new EditarRolProcesoDto("Otro", DESCRIPCION),
                USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(RolProcesoService.ROL_ELIMINADO);

        noSeModificoNada();
    }

    @Test
    void unaViolacionDeUnicidadAlRenombrarSeReportaComoNombreDuplicado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElRol(EMPRESA_PROPIA, true);
        when(rolProcesoRepository.saveAndFlush(any(RolProceso.class)))
                .thenThrow(new DataIntegrityViolationException("uk_rol_proceso_empresa_nombre"));

        assertThatThrownBy(() -> rolProcesoService.editar(ROL_ID, new EditarRolProcesoDto("Supervisor", DESCRIPCION),
                USERNAME))
                .isInstanceOf(NombreRolProcesoDuplicadoException.class);

        verify(historialRolProcesoRepository, never()).save(any(HistorialRolProceso.class));
    }

    @Test
    void elAdministradorEliminaUnRolLibreDeFormaLogica() {
        autenticar(RolUsuario.ADMINISTRADOR);
        RolProceso rol = existeElRol(EMPRESA_PROPIA, true);
        usadoEn();

        RolProcesoRespuestaDto eliminado = rolProcesoService.eliminar(ROL_ID, USERNAME);

        assertThat(rol.isActivo()).isFalse();
        assertThat(eliminado.isActivo()).isFalse();
        assertThat(eliminado.getId()).isEqualTo(ROL_ID);
        verify(rolProcesoRepository).save(rol);
        verify(rolProcesoRepository, never()).delete(any(RolProceso.class));
        verify(rolProcesoRepository, never()).deleteById(anyLong());
        verify(rolProcesoRepository, never()).deleteAll();
    }

    @Test
    void laEliminacionQuedaEnElHistorialDelRol() {
        Usuario administrador = autenticar(RolUsuario.ADMINISTRADOR);
        RolProceso rol = existeElRol(EMPRESA_PROPIA, true);
        usadoEn();

        rolProcesoService.eliminar(ROL_ID, USERNAME);

        HistorialRolProceso historial = historialGuardado();
        assertThat(historial.getRolProceso()).isSameAs(rol);
        assertThat(historial.getUsuario()).isSameAs(administrador);
        assertThat(historial.getAccion()).isEqualTo(AccionRolProceso.ELIMINACION);
        assertThat(historial.getCambiosRealizados()).isEqualTo("rol de proceso eliminado: '" + ANALISTA + "'");
    }

    @Test
    void elEditorNoEliminaRolesDeProceso() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> rolProcesoService.eliminar(ROL_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(RolProcesoService.SIN_PERMISO_ELIMINAR);

        noSeModificoNada();
        verify(rolProcesoRepository, never()).usosEnProcesosActivos(anyCollection());
    }

    @Test
    void elUsuarioDeSoloLecturaNoEliminaRolesDeProceso() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> rolProcesoService.eliminar(ROL_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(RolProcesoService.SIN_PERMISO_ELIMINAR);

        noSeModificoNada();
    }

    @Test
    void unRolUsadoPorLanesNoSeEliminaEIndicaLosProcesosDondeSeUsa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        RolProceso rol = existeElRol(EMPRESA_PROPIA, true);
        usadoEn(uso(ROL_ID, 5L, "Ventas", 11L, 3), uso(ROL_ID, 5L, "Ventas", 12L, 1),
                uso(ROL_ID, 6L, "Compras", 21L, 0));

        assertThatThrownBy(() -> rolProcesoService.eliminar(ROL_ID, USERNAME))
                .isInstanceOf(RolProcesoEnUsoException.class)
                .hasMessage("El rol de proceso '" + ANALISTA + "' no se puede eliminar porque lo usan lanes de los"
                        + " procesos: Ventas, Compras")
                .satisfies(error -> assertThat(((RolProcesoEnUsoException) error).getProcesos())
                        .containsExactly("Ventas", "Compras"));

        assertThat(rol.isActivo()).isTrue();
        noSeModificoNada();
    }

    @Test
    void noSeEliminaUnRolDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        RolProceso ajeno = existeElRol(EMPRESA_AJENA, true);

        assertThatThrownBy(() -> rolProcesoService.eliminar(ROL_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(RolProcesoService.ROL_AJENO);

        assertThat(ajeno.isActivo()).isTrue();
        noSeModificoNada();
    }

    @Test
    void unRolYaEliminadoNoSeEliminaDeNuevo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElRol(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> rolProcesoService.eliminar(ROL_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(RolProcesoService.ROL_ELIMINADO);

        noSeModificoNada();
    }

    @Test
    void laConsultaPreviaDeEliminacionMuestraElUsoAgrupadoPorProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElRol(EMPRESA_PROPIA, true);
        usadoEn(uso(ROL_ID, 5L, "Ventas", 11L, 3), uso(ROL_ID, 5L, "Ventas", 12L, 1));

        RolProcesoResumenDto impacto = rolProcesoService.obtenerParaEliminar(ROL_ID, USERNAME);

        assertThat(impacto.isEnUso()).isTrue();
        assertThat(impacto.isPuedeEliminar()).isFalse();
        assertThat(impacto.getProcesos()).hasSize(1);
        ProcesoUsoRolDto ventas = impacto.getProcesos().get(0);
        assertThat(ventas.getId()).isEqualTo(5L);
        assertThat(ventas.getNombre()).isEqualTo("Ventas");
        assertThat(ventas.getLanes()).isEqualTo(2);
        assertThat(ventas.getActividadesActivas()).isEqualTo(4);
        noSeModificoNada();
    }

    @Test
    void laConsultaPreviaDeUnRolLibreIndicaQueSePuedeEliminar() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElRol(EMPRESA_PROPIA, true);
        usadoEn();

        RolProcesoResumenDto impacto = rolProcesoService.obtenerParaEliminar(ROL_ID, USERNAME);

        assertThat(impacto.isEnUso()).isFalse();
        assertThat(impacto.isPuedeEliminar()).isTrue();
        assertThat(impacto.getProcesos()).isEmpty();
    }

    @Test
    void laConsultaPreviaDeEliminacionExigeAdministrador() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> rolProcesoService.obtenerParaEliminar(ROL_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(RolProcesoService.SIN_PERMISO_ELIMINAR);
    }

    @Test
    void unLectorConsultaElDetalleDeUnRolDeSuEmpresa() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElRol(EMPRESA_PROPIA, true);
        usadoEn(uso(ROL_ID, 5L, "Ventas", 11L, 2));

        RolProcesoResumenDto detalle = rolProcesoService.obtener(ROL_ID, USERNAME);

        assertThat(detalle.getId()).isEqualTo(ROL_ID);
        assertThat(detalle.getNombre()).isEqualTo(ANALISTA);
        assertThat(detalle.getDescripcion()).isEqualTo(DESCRIPCION);
        assertThat(detalle.isActivo()).isTrue();
        assertThat(detalle.isEnUso()).isTrue();
        assertThat(detalle.getProcesos()).extracting(ProcesoUsoRolDto::getNombre).containsExactly("Ventas");
        assertThat(detalle.isPuedeEliminar()).isFalse();
    }

    @Test
    void unRolEliminadoSigueConsultableParaAuditoriaPeroNoSePuedeEliminar() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElRol(EMPRESA_PROPIA, false);
        usadoEn();

        RolProcesoResumenDto detalle = rolProcesoService.obtener(ROL_ID, USERNAME);

        assertThat(detalle.isActivo()).isFalse();
        assertThat(detalle.isPuedeEliminar()).isFalse();
    }

    @Test
    void noSeConsultaUnRolDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElRol(EMPRESA_AJENA, true);

        assertThatThrownBy(() -> rolProcesoService.obtener(ROL_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(RolProcesoService.ROL_AJENO);

        verify(rolProcesoRepository, never()).usosEnProcesosActivos(anyCollection());
    }

    @Test
    void consultarUnRolInexistenteInformaQueNoExiste() {
        autenticar(RolUsuario.SOLO_LECTURA);
        when(rolProcesoRepository.findById(ROL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rolProcesoService.obtener(ROL_ID, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(RolProcesoService.ROL_NO_EXISTE);
    }

    @Test
    void cualquierUsuarioDeLaEmpresaConsultaLosRolesConSuUso() {
        autenticar(RolUsuario.SOLO_LECTURA);
        devolverPagina(List.of(rol(ROL_ID, ANALISTA, EMPRESA_PROPIA, true),
                rol(OTRO_ROL_ID, "Supervisor", EMPRESA_PROPIA, true)));
        when(rolProcesoRepository.usosEnProcesosActivos(List.of(ROL_ID, OTRO_ROL_ID)))
                .thenReturn(List.of(uso(ROL_ID, 5L, "Ventas", 11L, 3)));

        Page<RolProcesoResumenDto> pagina = rolProcesoService.consultar(filtro(), USERNAME);

        assertThat(pagina.getContent()).extracting(RolProcesoResumenDto::getNombre)
                .containsExactly(ANALISTA, "Supervisor");
        RolProcesoResumenDto analista = pagina.getContent().get(0);
        assertThat(analista.isEnUso()).isTrue();
        assertThat(analista.getProcesos()).extracting(ProcesoUsoRolDto::getNombre).containsExactly("Ventas");
        RolProcesoResumenDto supervisor = pagina.getContent().get(1);
        assertThat(supervisor.isEnUso()).isFalse();
        assertThat(supervisor.getProcesos()).isEmpty();
        assertThat(pagina.getContent()).noneMatch(RolProcesoResumenDto::isPuedeEliminar);
    }

    @Test
    void elAdministradorSoloPuedeEliminarRolesActivosQueNoEstanEnUso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        devolverPagina(List.of(rol(ROL_ID, ANALISTA, EMPRESA_PROPIA, true),
                rol(OTRO_ROL_ID, "Supervisor", EMPRESA_PROPIA, true), rol(42L, "Auditor", EMPRESA_PROPIA, false)));
        when(rolProcesoRepository.usosEnProcesosActivos(List.of(ROL_ID, OTRO_ROL_ID, 42L)))
                .thenReturn(List.of(uso(OTRO_ROL_ID, 5L, "Ventas", 11L, 0)));

        Page<RolProcesoResumenDto> pagina = rolProcesoService.consultar(filtro(), USERNAME);

        assertThat(pagina.getContent()).extracting(RolProcesoResumenDto::isPuedeEliminar)
                .containsExactly(true, false, false);
    }

    @Test
    void laConsultaFiltraPorLaEmpresaDelUsuarioEnPaginasDeDiezOrdenadasPorNombre() {
        autenticar(RolUsuario.EDITOR);
        devolverPagina(List.of());
        FiltroRolesProcesoDto filtro = new FiltroRolesProcesoDto(null, VisibilidadRolProceso.TODOS, 2);

        rolProcesoService.consultar(filtro, USERNAME);

        verify(rolProcesoSpecifications).deLaEmpresaCon(EMPRESA_PROPIA, filtro);
        verify(rolProcesoSpecifications, never()).deLaEmpresaCon(eq(EMPRESA_AJENA), any());
        ArgumentCaptor<Pageable> paginacion = ArgumentCaptor.forClass(Pageable.class);
        verify(rolProcesoRepository).findAll(ArgumentMatchers.<Specification<RolProceso>>any(), paginacion.capture());
        assertThat(paginacion.getValue()).isEqualTo(PageRequest.of(2, 10, Sort.by(Sort.Direction.ASC, "nombre")));
    }

    @Test
    void laConsultaMuestraActivosPorDefectoYNormalizaElFiltro() {
        autenticar(RolUsuario.SOLO_LECTURA);
        devolverPagina(List.of());
        FiltroRolesProcesoDto filtro = new FiltroRolesProcesoDto("   analista  ", null, -3);

        rolProcesoService.consultar(filtro, USERNAME);

        assertThat(filtro.getQ()).isEqualTo("analista");
        assertThat(filtro.getVisibilidad()).isEqualTo(VisibilidadRolProceso.ACTIVOS);
        assertThat(filtro.getPage()).isZero();
    }

    @Test
    void unaBusquedaEnBlancoNoFiltraPorNombre() {
        autenticar(RolUsuario.SOLO_LECTURA);
        devolverPagina(List.of());
        FiltroRolesProcesoDto filtro = new FiltroRolesProcesoDto("   ", VisibilidadRolProceso.INACTIVOS, null);

        rolProcesoService.consultar(filtro, USERNAME);

        assertThat(filtro.getQ()).isNull();
        assertThat(filtro.getVisibilidad()).isEqualTo(VisibilidadRolProceso.INACTIVOS);
        assertThat(filtro.getPage()).isZero();
    }

    @Test
    void unaPaginaVaciaNoConsultaElUsoDeLosRoles() {
        autenticar(RolUsuario.SOLO_LECTURA);
        devolverPagina(List.of());

        assertThat(rolProcesoService.consultar(filtro(), USERNAME)).isEmpty();

        verify(rolProcesoRepository, never()).usosEnProcesosActivos(anyCollection());
    }

    @Test
    void elHistorialDelRolSeConsultaDentroDeLaEmpresa() {
        Usuario lector = autenticar(RolUsuario.SOLO_LECTURA);
        RolProceso rol = existeElRol(EMPRESA_PROPIA, false);
        LocalDateTime fecha = LocalDateTime.of(2026, 9, 1, 10, 30);
        when(historialRolProcesoRepository.findByRolProcesoIdAndRolProcesoEmpresaIdOrderByFechaDescIdDesc(ROL_ID,
                EMPRESA_PROPIA)).thenReturn(List.of(new HistorialRolProceso(70L, rol, lector, fecha,
                        AccionRolProceso.ELIMINACION, "rol de proceso eliminado: '" + ANALISTA + "'")));

        List<HistorialRolProcesoRespuestaDto> historial = rolProcesoService.consultarHistorial(ROL_ID, USERNAME);

        assertThat(historial).hasSize(1);
        HistorialRolProcesoRespuestaDto entrada = historial.get(0);
        assertThat(entrada.getFecha()).isEqualTo(fecha);
        assertThat(entrada.getUsuarioCorreo()).isEqualTo(USERNAME);
        assertThat(entrada.getAccion()).isEqualTo(AccionRolProceso.ELIMINACION);
        assertThat(entrada.getCambiosRealizados()).isEqualTo("rol de proceso eliminado: '" + ANALISTA + "'");
    }

    @Test
    void elHistorialDeUnRolDeOtraEmpresaNoSeEntrega() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElRol(EMPRESA_AJENA, true);

        assertThatThrownBy(() -> rolProcesoService.consultarHistorial(ROL_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(historialRolProcesoRepository, never())
                .findByRolProcesoIdAndRolProcesoEmpresaIdOrderByFechaDescIdDesc(anyLong(), anyLong());
    }

    @Test
    void rolActivoDeLaEmpresaEntregaElRolPropio() {
        Usuario usuario = new Usuario(1L, USERNAME, HASH, RolUsuario.EDITOR, true, empresa(EMPRESA_PROPIA));
        RolProceso rol = existeElRol(EMPRESA_PROPIA, true);

        assertThat(rolProcesoService.rolActivoDeLaEmpresa(ROL_ID, usuario)).isSameAs(rol);
    }

    @Test
    void rolActivoDeLaEmpresaRechazaUnRolEliminado() {
        Usuario usuario = new Usuario(1L, USERNAME, HASH, RolUsuario.EDITOR, true, empresa(EMPRESA_PROPIA));
        existeElRol(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> rolProcesoService.rolActivoDeLaEmpresa(ROL_ID, usuario))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(RolProcesoService.ROL_ELIMINADO);
    }
}
