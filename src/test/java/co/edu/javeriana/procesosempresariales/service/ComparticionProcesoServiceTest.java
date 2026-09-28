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

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.ProcesoCompartidoEmpresa;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.EmpresaInvitadaDto;
import co.edu.javeriana.procesosempresariales.exception.ComparticionNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.ProcesoYaCompartidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.EmpresaRepository;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoCompartidoEmpresaRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class ComparticionProcesoServiceTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_INVITADA = 20L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final Long PROCESO_ID = 5L;

    @Mock
    private ProcesoCompartidoEmpresaRepository procesoCompartidoEmpresaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProcesoRepository procesoRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private HistorialProcesoRepository historialProcesoRepository;

    private ComparticionProcesoService comparticionProcesoService;

    @BeforeEach
    void inicializar() {
        UsuarioService usuarios = new UsuarioService(usuarioRepository, new ModelMapper(),
                new BCryptPasswordEncoder());
        AccesoProcesoService acceso = new AccesoProcesoService(usuarios, procesoRepository);
        comparticionProcesoService = new ComparticionProcesoService(procesoCompartidoEmpresaRepository, acceso,
                new EmpresaService(empresaRepository, usuarios, new ModelMapper()),
                new HistorialProcesoService(historialProcesoRepository));
    }

    private Empresa empresa(Long id) {
        return new Empresa(id, "Empresa " + id, "900123456-" + id, "contacto" + id + "@alpes.com");
    }

    private void autenticar(RolUsuario rol) {
        when(usuarioRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(new Usuario(1L, USERNAME, HASH, rol, true, empresa(EMPRESA_PROPIA))));
    }

    private Proceso existeElProceso(Long empresaId, boolean eliminado) {
        Proceso proceso = new Proceso(PROCESO_ID, "Ventas", "Proceso comercial", "Comercial",
                EstadoProceso.PUBLICADO, empresa(empresaId), new ArrayList<>(), eliminado);
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.of(proceso));
        return proceso;
    }

    private void existeLaEmpresaInvitada() {
        when(empresaRepository.findById(EMPRESA_INVITADA)).thenReturn(Optional.of(empresa(EMPRESA_INVITADA)));
    }

    private ProcesoCompartidoEmpresa comparticion(Proceso proceso, boolean activo) {
        ProcesoCompartidoEmpresa comparticion = new ProcesoCompartidoEmpresa(3L, proceso, empresa(EMPRESA_INVITADA),
                activo);
        when(procesoCompartidoEmpresaRepository.findByProcesoIdAndEmpresaInvitadaId(PROCESO_ID, EMPRESA_INVITADA))
                .thenReturn(Optional.of(comparticion));
        return comparticion;
    }

    private HistorialProceso historialGuardado() {
        ArgumentCaptor<HistorialProceso> capturado = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private void noSeModificoNada() {
        verify(procesoCompartidoEmpresaRepository, never()).save(any(ProcesoCompartidoEmpresa.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void cualquierUsuarioDeLaEmpresaPropietariaVeConQuienSeComparte() {
        autenticar(RolUsuario.SOLO_LECTURA);
        Proceso proceso = existeElProceso(EMPRESA_PROPIA, false);
        when(procesoCompartidoEmpresaRepository.findByProcesoIdAndActivoTrueOrderByEmpresaInvitadaNombreAsc(
                PROCESO_ID)).thenReturn(List.of(new ProcesoCompartidoEmpresa(3L, proceso, empresa(EMPRESA_INVITADA),
                        true)));

        List<EmpresaInvitadaDto> invitadas = comparticionProcesoService.listar(PROCESO_ID, USERNAME);

        assertThat(invitadas).extracting(EmpresaInvitadaDto::getEmpresaId).containsExactly(EMPRESA_INVITADA);
        assertThat(invitadas).extracting(EmpresaInvitadaDto::getNombre).containsExactly("Empresa 20");
    }

    @Test
    void unaEmpresaInvitadaNoVeConQuienMasSeComparte() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> comparticionProcesoService.listar(PROCESO_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);
    }

    @Test
    void elAdministradorComparteElProcesoEnSoloLectura() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProceso(EMPRESA_PROPIA, false);
        existeLaEmpresaInvitada();

        EmpresaInvitadaDto invitada = comparticionProcesoService.compartir(PROCESO_ID, EMPRESA_INVITADA, USERNAME);

        ArgumentCaptor<ProcesoCompartidoEmpresa> capturada = ArgumentCaptor.forClass(ProcesoCompartidoEmpresa.class);
        verify(procesoCompartidoEmpresaRepository).save(capturada.capture());
        assertThat(capturada.getValue().getProceso()).isSameAs(proceso);
        assertThat(capturada.getValue().getEmpresaInvitada().getId()).isEqualTo(EMPRESA_INVITADA);
        assertThat(capturada.getValue().isActivo()).isTrue();
        assertThat(invitada.getEmpresaId()).isEqualTo(EMPRESA_INVITADA);
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("proceso compartido en solo lectura con 'Empresa 20'");
    }

    @Test
    void volverACompartirReactivaLaMismaAsociacion() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProceso(EMPRESA_PROPIA, false);
        existeLaEmpresaInvitada();
        ProcesoCompartidoEmpresa anterior = comparticion(proceso, false);

        comparticionProcesoService.compartir(PROCESO_ID, EMPRESA_INVITADA, USERNAME);

        assertThat(anterior.isActivo()).isTrue();
        verify(procesoCompartidoEmpresaRepository).save(anterior);
    }

    @Test
    void compartirDosVecesConLaMismaEmpresaSeRechaza() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProceso(EMPRESA_PROPIA, false);
        existeLaEmpresaInvitada();
        comparticion(proceso, true);

        assertThatThrownBy(() -> comparticionProcesoService.compartir(PROCESO_ID, EMPRESA_INVITADA, USERNAME))
                .isInstanceOf(ProcesoYaCompartidoException.class)
                .hasMessage(ComparticionProcesoService.YA_COMPARTIDO);

        noSeModificoNada();
    }

    @Test
    void unProcesoNoSeComparteConSuPropiaEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> comparticionProcesoService.compartir(PROCESO_ID, EMPRESA_PROPIA, USERNAME))
                .isInstanceOf(ComparticionNoValidaException.class)
                .hasMessage(ComparticionProcesoService.CON_SU_PROPIETARIA);

        noSeModificoNada();
        verify(empresaRepository, never()).findById(anyLong());
    }

    @Test
    void noSeComparteConUnaEmpresaInexistente() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        when(empresaRepository.findById(EMPRESA_INVITADA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comparticionProcesoService.compartir(PROCESO_ID, EMPRESA_INVITADA, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(EmpresaService.EMPRESA_NO_EXISTE);

        noSeModificoNada();
    }

    @Test
    void elEditorNoCambiaLaComparticion() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> comparticionProcesoService.compartir(PROCESO_ID, EMPRESA_INVITADA, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage(ComparticionProcesoService.SIN_PERMISO);
        assertThatThrownBy(() -> comparticionProcesoService.dejarDeCompartir(PROCESO_ID, EMPRESA_INVITADA,
                USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        noSeModificoNada();
    }

    @Test
    void elUsuarioDeSoloLecturaNoCambiaLaComparticion() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> comparticionProcesoService.compartir(PROCESO_ID, EMPRESA_INVITADA, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        noSeModificoNada();
    }

    @Test
    void elAdministradorDeUnaEmpresaInvitadaNoCambiaLaComparticion() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> comparticionProcesoService.compartir(PROCESO_ID, EMPRESA_INVITADA, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");

        noSeModificoNada();
    }

    @Test
    void noSeCambiaLaComparticionDeUnProcesoEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, true);

        assertThatThrownBy(() -> comparticionProcesoService.compartir(PROCESO_ID, EMPRESA_INVITADA, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El proceso ya fue eliminado");

        noSeModificoNada();
    }

    @Test
    void elAdministradorRetiraElAccesoSinBorrarLaAsociacion() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProceso(EMPRESA_PROPIA, false);
        ProcesoCompartidoEmpresa activa = comparticion(proceso, true);

        comparticionProcesoService.dejarDeCompartir(PROCESO_ID, EMPRESA_INVITADA, USERNAME);

        assertThat(activa.isActivo()).isFalse();
        verify(procesoCompartidoEmpresaRepository).save(activa);
        verify(procesoCompartidoEmpresaRepository, never()).delete(any(ProcesoCompartidoEmpresa.class));
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("se retiró el acceso de 'Empresa 20' al proceso");
    }

    @Test
    void retirarElAccesoDeUnaEmpresaNoInvitadaInformaQueNoEstaCompartido() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> comparticionProcesoService.dejarDeCompartir(PROCESO_ID, EMPRESA_INVITADA,
                USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(ComparticionProcesoService.NO_COMPARTIDO);

        noSeModificoNada();
    }

    @Test
    void retirarDosVecesElAccesoInformaQueYaNoEstaCompartido() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProceso(EMPRESA_PROPIA, false);
        comparticion(proceso, false);

        assertThatThrownBy(() -> comparticionProcesoService.dejarDeCompartir(PROCESO_ID, EMPRESA_INVITADA,
                USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);

        noSeModificoNada();
    }
}
