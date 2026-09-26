package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
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
import co.edu.javeriana.procesosempresariales.domain.PermisoEstructuraProceso;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolProceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CrearPoolDto;
import co.edu.javeriana.procesosempresariales.dto.EditarPoolDto;
import co.edu.javeriana.procesosempresariales.dto.PoolRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.PoolCajaNegraException;
import co.edu.javeriana.procesosempresariales.exception.PoolConContenidoException;
import co.edu.javeriana.procesosempresariales.exception.PoolNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.EmpresaRepository;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.HistorialRolProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.PermisoEstructuraProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.PoolRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.RolProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.RolProcesoSpecifications;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class PoolServiceTest {

    private static final String USERNAME = "editor@alpes.com";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final Long EMPRESA_SOCIA = 20L;
    private static final Long PROCESO_ID = 5L;
    private static final Long POOL_PROPIETARIO = 80L;
    private static final Long POOL_CLIENTE = 90L;
    private static final Long POOL_PROVEEDOR = 91L;

    @Mock
    private PoolRepository poolRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProcesoRepository procesoRepository;

    @Mock
    private HistorialProcesoRepository historialProcesoRepository;

    @Mock
    private PermisoEstructuraProcesoRepository permisoEstructuraProcesoRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private RolProcesoRepository rolProcesoRepository;

    @Mock
    private HistorialRolProcesoRepository historialRolProcesoRepository;

    private PoolService poolService;

    private Proceso proceso;

    @BeforeEach
    void inicializar() {
        UsuarioService usuarios = new UsuarioService(usuarioRepository, new ModelMapper(),
                new BCryptPasswordEncoder());
        AccesoProcesoService acceso = new AccesoProcesoService(usuarios, procesoRepository);
        HistorialProcesoService historial = new HistorialProcesoService(historialProcesoRepository);
        PermisoEstructuraService permisos = new PermisoEstructuraService(permisoEstructuraProcesoRepository, acceso,
                historial);
        EmpresaService empresas = new EmpresaService(empresaRepository, usuarios, new ModelMapper());
        RolProcesoService roles = new RolProcesoService(rolProcesoRepository, historialRolProcesoRepository,
                new RolProcesoSpecifications(), acceso);
        poolService = new PoolService(poolRepository, acceso, permisos, historial, empresas, roles);
    }

    private Empresa empresa(Long id) {
        return new Empresa(id, "Empresa " + id, "900123456-" + id, "contacto" + id + "@alpes.com");
    }

    private void autenticar(RolUsuario rol) {
        when(usuarioRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(new Usuario(1L, USERNAME, HASH, rol, true, empresa(EMPRESA_PROPIA))));
    }

    private Pool pool(Long id, String nombre, TipoPool tipo, int orden, boolean cajaNegra, Empresa participante) {
        Pool pool = new Pool(id, null, nombre, tipo, orden, cajaNegra, true, participante, new ArrayList<>());
        proceso.agregarPool(pool);
        return pool;
    }

    private Proceso construirProceso(Long empresaId, boolean eliminado) {
        proceso = new Proceso(PROCESO_ID, "Ventas", "Proceso comercial", "Comercial", EstadoProceso.BORRADOR,
                empresa(empresaId), new ArrayList<>(), eliminado);
        pool(POOL_PROPIETARIO, "Empresa " + empresaId, TipoPool.PROPIETARIO, 1, false, null);
        return proceso;
    }

    private Proceso existeElProceso(Long empresaId, boolean eliminado) {
        construirProceso(empresaId, eliminado);
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.of(proceso));
        return proceso;
    }

    private void losPoolsActivosSon(Pool... pools) {
        when(poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(PROCESO_ID)).thenReturn(List.of(pools));
    }

    private Pool existe(Pool pool) {
        when(poolRepository.findByIdAndProcesoId(pool.getId(), PROCESO_ID)).thenReturn(Optional.of(pool));
        return pool;
    }

    private void sinContenido(Long poolId) {
        when(poolRepository.lanesActivas(poolId)).thenReturn(0L);
        when(poolRepository.actividadesActivas(poolId)).thenReturn(0L);
        when(poolRepository.gatewaysActivos(poolId)).thenReturn(0L);
    }

    private void editorConPermisos(boolean crearPool, boolean editarPool, boolean eliminarPool) {
        when(permisoEstructuraProcesoRepository.findByProcesoIdAndRol(PROCESO_ID, RolUsuario.EDITOR))
                .thenReturn(Optional.of(new PermisoEstructuraProceso(1L, null, RolUsuario.EDITOR, crearPool,
                        editarPool, eliminarPool, true, true, false)));
    }

    private Pool poolGuardado() {
        ArgumentCaptor<Pool> capturado = ArgumentCaptor.forClass(Pool.class);
        verify(poolRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private HistorialProceso historialGuardado() {
        ArgumentCaptor<HistorialProceso> capturado = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private void devolverElPoolGuardado() {
        when(poolRepository.save(any(Pool.class))).thenAnswer(invocacion -> {
            Pool guardado = invocacion.getArgument(0);
            guardado.setId(POOL_CLIENTE);
            return guardado;
        });
    }

    private void noSeModificoNada() {
        verify(poolRepository, never()).save(any(Pool.class));
        verify(poolRepository, never()).saveAll(anyList());
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void elLectorListaLosPoolsActivosConLaEmpresaQueRepresentaCadaUno() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_PROPIA, false);
        Pool propietario = proceso.poolPropietario();
        Pool socio = pool(POOL_CLIENTE, "Socio", TipoPool.PARTICIPANTE, 2, true, empresa(EMPRESA_SOCIA));
        Pool externo = pool(POOL_PROVEEDOR, "Proveedor", TipoPool.EXTERNO, 3, false, null);
        losPoolsActivosSon(propietario, socio, externo);

        List<PoolRespuestaDto> pools = poolService.listar(PROCESO_ID, USERNAME);

        assertThat(pools).extracting(PoolRespuestaDto::getTipo)
                .containsExactly(TipoPool.PROPIETARIO, TipoPool.PARTICIPANTE, TipoPool.EXTERNO);
        assertThat(pools).extracting(PoolRespuestaDto::getEmpresaId)
                .containsExactly(EMPRESA_PROPIA, EMPRESA_SOCIA, null);
        assertThat(pools).extracting(PoolRespuestaDto::getOrden).containsExactly(1, 2, 3);
        assertThat(pools.get(1).isCajaNegra()).isTrue();
        assertThat(pools.get(1).getEmpresaNombre()).isEqualTo("Empresa 20");
        assertThat(pools).allMatch(dto -> dto.getProcesoId().equals(PROCESO_ID));
    }

    @Test
    void unaEmpresaInvitadaConsultaLosPoolsDelProcesoCompartido() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_AJENA, false);
        when(procesoRepository.estaCompartidoCon(PROCESO_ID, EMPRESA_PROPIA)).thenReturn(true);
        losPoolsActivosSon(proceso.poolPropietario());

        assertThat(poolService.listar(PROCESO_ID, USERNAME)).hasSize(1);
    }

    @Test
    void serParticipanteDeUnPoolNoDaAccesoAlProcesoDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);
        pool(POOL_CLIENTE, "Alpes", TipoPool.PARTICIPANTE, 2, false, empresa(EMPRESA_PROPIA));

        assertThatThrownBy(() -> poolService.listar(PROCESO_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El proceso no pertenece a la empresa del usuario");
    }

    @Test
    void unPoolDeOtroProcesoNoSeEncuentra() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_PROPIA, false);
        when(poolRepository.findByIdAndProcesoId(POOL_CLIENTE, PROCESO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> poolService.obtener(PROCESO_ID, POOL_CLIENTE, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(PoolService.POOL_NO_EXISTE);
    }

    @Test
    void unPoolEliminadoNoSeConsulta() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_PROPIA, false);
        Pool cliente = existe(pool(POOL_CLIENTE, "Cliente", TipoPool.EXTERNO, 2, false, null));
        cliente.setActivo(false);

        assertThatThrownBy(() -> poolService.obtener(PROCESO_ID, POOL_CLIENTE, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(PoolService.POOL_ELIMINADO);
    }

    @Test
    void obtenerDevuelveElPoolDelProceso() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_PROPIA, false);
        existe(proceso.poolPropietario());

        PoolRespuestaDto propietario = poolService.obtener(PROCESO_ID, POOL_PROPIETARIO, USERNAME);

        assertThat(propietario.getNombre()).isEqualTo("Empresa 7");
        assertThat(propietario.isActivo()).isTrue();
    }

    @Test
    void elEditorCreaUnPoolExternoCajaNegraAlFinalDelProceso() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        losPoolsActivosSon(proceso.poolPropietario());
        devolverElPoolGuardado();

        PoolRespuestaDto creado = poolService.crear(PROCESO_ID,
                new CrearPoolDto("  Cliente  ", TipoPool.EXTERNO, true, null), USERNAME);

        Pool guardado = poolGuardado();
        assertThat(guardado.getProceso()).isSameAs(proceso);
        assertThat(guardado.getNombre()).isEqualTo("Cliente");
        assertThat(guardado.getTipo()).isEqualTo(TipoPool.EXTERNO);
        assertThat(guardado.isCajaNegra()).isTrue();
        assertThat(guardado.isActivo()).isTrue();
        assertThat(guardado.getOrden()).isEqualTo(2);
        assertThat(guardado.getEmpresaParticipante()).isNull();
        assertThat(creado.getId()).isEqualTo(POOL_CLIENTE);
        assertThat(creado.getEmpresaId()).isNull();
    }

    @Test
    void unPoolParticipanteRepresentaAOtraEmpresaRegistrada() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        losPoolsActivosSon(proceso.poolPropietario());
        when(empresaRepository.findById(EMPRESA_SOCIA)).thenReturn(Optional.of(empresa(EMPRESA_SOCIA)));
        devolverElPoolGuardado();

        PoolRespuestaDto creado = poolService.crear(PROCESO_ID,
                new CrearPoolDto("Banco socio", TipoPool.PARTICIPANTE, false, EMPRESA_SOCIA), USERNAME);

        assertThat(poolGuardado().getEmpresaParticipante().getId()).isEqualTo(EMPRESA_SOCIA);
        assertThat(creado.getEmpresaId()).isEqualTo(EMPRESA_SOCIA);
        assertThat(creado.getEmpresaNombre()).isEqualTo("Empresa 20");
    }

    @Test
    void crearUnPoolQuedaEnElHistorialDelProceso() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        losPoolsActivosSon(proceso.poolPropietario());
        devolverElPoolGuardado();

        poolService.crear(PROCESO_ID, new CrearPoolDto("Cliente", TipoPool.EXTERNO, true, null), USERNAME);

        HistorialProceso historial = historialGuardado();
        assertThat(historial.getProceso()).isSameAs(proceso);
        assertThat(historial.getCambiosRealizados()).isEqualTo("pool creado: 'Cliente' (EXTERNO, caja negra)");
    }

    @Test
    void noSeCreaUnSegundoPoolPropietario() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> poolService.crear(PROCESO_ID,
                new CrearPoolDto("Otra", TipoPool.PROPIETARIO, false, null), USERNAME))
                .isInstanceOf(PoolNoValidoException.class)
                .hasMessage(PoolService.PROPIETARIO_UNICO);

        noSeModificoNada();
    }

    @Test
    void unPoolParticipanteDebeIndicarLaEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> poolService.crear(PROCESO_ID,
                new CrearPoolDto("Socio", TipoPool.PARTICIPANTE, false, null), USERNAME))
                .isInstanceOf(PoolNoValidoException.class)
                .hasMessage(PoolService.PARTICIPANTE_SIN_EMPRESA);

        noSeModificoNada();
    }

    @Test
    void laEmpresaPropietariaNoPuedeSerParticipanteDeSuPropioProceso() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> poolService.crear(PROCESO_ID,
                new CrearPoolDto("Nosotros", TipoPool.PARTICIPANTE, false, EMPRESA_PROPIA), USERNAME))
                .isInstanceOf(PoolNoValidoException.class)
                .hasMessage(PoolService.PARTICIPANTE_PROPIETARIA);

        noSeModificoNada();
    }

    @Test
    void unParticipanteConEmpresaInexistenteSeRechaza() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        when(empresaRepository.findById(EMPRESA_SOCIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> poolService.crear(PROCESO_ID,
                new CrearPoolDto("Socio", TipoPool.PARTICIPANTE, false, EMPRESA_SOCIA), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(EmpresaService.EMPRESA_NO_EXISTE);

        noSeModificoNada();
    }

    @Test
    void unPoolExternoNoLlevaEmpresaRegistrada() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> poolService.crear(PROCESO_ID,
                new CrearPoolDto("Proveedor", TipoPool.EXTERNO, false, EMPRESA_SOCIA), USERNAME))
                .isInstanceOf(PoolNoValidoException.class)
                .hasMessage(PoolService.EXTERNO_CON_EMPRESA);

        noSeModificoNada();
    }

    @Test
    void elUsuarioDeSoloLecturaNoCreaPools() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> poolService.crear(PROCESO_ID,
                new CrearPoolDto("Cliente", TipoPool.EXTERNO, true, null), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El rol SOLO_LECTURA no tiene permiso para crear pools en este proceso");

        noSeModificoNada();
    }

    @Test
    void unEditorSinPermisoConfiguradoNoCreaPools() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        editorConPermisos(false, true, false);

        assertThatThrownBy(() -> poolService.crear(PROCESO_ID,
                new CrearPoolDto("Cliente", TipoPool.EXTERNO, true, null), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El rol EDITOR no tiene permiso para crear pools en este proceso");

        noSeModificoNada();
    }

    @Test
    void unaEmpresaInvitadaNoCreaPools() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> poolService.crear(PROCESO_ID,
                new CrearPoolDto("Cliente", TipoPool.EXTERNO, true, null), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        noSeModificoNada();
    }

    @Test
    void noSeCreanPoolsEnUnProcesoEliminado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, true);

        assertThatThrownBy(() -> poolService.crear(PROCESO_ID,
                new CrearPoolDto("Cliente", TipoPool.EXTERNO, true, null), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);

        noSeModificoNada();
    }

    @Test
    void elEditorRenombraUnPoolYQuedaEnElHistorial() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        Pool cliente = existe(pool(POOL_CLIENTE, "Cliente", TipoPool.EXTERNO, 2, true, null));

        PoolRespuestaDto editado = poolService.editar(PROCESO_ID, POOL_CLIENTE,
                new EditarPoolDto("Clientes", true, null), USERNAME);

        assertThat(cliente.getNombre()).isEqualTo("Clientes");
        assertThat(editado.getNombre()).isEqualTo("Clientes");
        verify(poolRepository).save(cliente);
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("pool 'Cliente': nombre: 'Cliente' -> 'Clientes'");
    }

    @Test
    void unPoolVacioSePuedeConvertirEnCajaNegra() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        Pool cliente = existe(pool(POOL_CLIENTE, "Cliente", TipoPool.EXTERNO, 2, false, null));
        sinContenido(POOL_CLIENTE);

        poolService.editar(PROCESO_ID, POOL_CLIENTE, new EditarPoolDto("Cliente", true, null), USERNAME);

        assertThat(cliente.isCajaNegra()).isTrue();
        assertThat(historialGuardado().getCambiosRealizados()).isEqualTo("pool 'Cliente': caja negra: false -> true");
    }

    @Test
    void unPoolConElementosNoSeConvierteEnCajaNegra() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        Pool cliente = existe(pool(POOL_CLIENTE, "Cliente", TipoPool.EXTERNO, 2, false, null));
        when(poolRepository.lanesActivas(POOL_CLIENTE)).thenReturn(1L);
        when(poolRepository.actividadesActivas(POOL_CLIENTE)).thenReturn(2L);
        when(poolRepository.gatewaysActivos(POOL_CLIENTE)).thenReturn(1L);

        assertThatThrownBy(() -> poolService.editar(PROCESO_ID, POOL_CLIENTE,
                new EditarPoolDto("Cliente", true, null), USERNAME))
                .isInstanceOf(PoolConContenidoException.class)
                .hasMessage("El pool 'Cliente' no puede convertirse en caja negra: contiene 1 lanes, 2 actividades"
                        + " y 1 gateways activos");

        assertThat(cliente.isCajaNegra()).isFalse();
        noSeModificoNada();
    }

    @Test
    void elPoolPropietarioNuncaEsCajaNegra() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existe(proceso.poolPropietario());

        assertThatThrownBy(() -> poolService.editar(PROCESO_ID, POOL_PROPIETARIO,
                new EditarPoolDto("Empresa 7", true, null), USERNAME))
                .isInstanceOf(PoolNoValidoException.class)
                .hasMessage(PoolService.PROPIETARIO_NO_CAJA_NEGRA);

        noSeModificoNada();
    }

    @Test
    void elPoolPropietarioNoRepresentaAOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existe(proceso.poolPropietario());

        assertThatThrownBy(() -> poolService.editar(PROCESO_ID, POOL_PROPIETARIO,
                new EditarPoolDto("Empresa 7", false, EMPRESA_SOCIA), USERNAME))
                .isInstanceOf(PoolNoValidoException.class)
                .hasMessage(PoolService.PROPIETARIO_SIN_PARTICIPANTE);
    }

    @Test
    void cambiarLaEmpresaQueRepresentaUnParticipanteQuedaEnElHistorial() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        Pool socio = existe(pool(POOL_CLIENTE, "Socio", TipoPool.PARTICIPANTE, 2, false, empresa(EMPRESA_SOCIA)));
        when(empresaRepository.findById(21L)).thenReturn(Optional.of(empresa(21L)));

        poolService.editar(PROCESO_ID, POOL_CLIENTE, new EditarPoolDto("Socio", false, 21L), USERNAME);

        assertThat(socio.getEmpresaParticipante().getId()).isEqualTo(21L);
        assertThat(historialGuardado().getCambiosRealizados())
                .isEqualTo("pool 'Socio': empresa participante: 'Empresa 20' -> 'Empresa 21'");
    }

    @Test
    void editarUnPoolSinCambiosNoRegistraHistorial() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existe(pool(POOL_CLIENTE, "Cliente", TipoPool.EXTERNO, 2, true, null));

        PoolRespuestaDto respuesta = poolService.editar(PROCESO_ID, POOL_CLIENTE,
                new EditarPoolDto(" Cliente ", true, null), USERNAME);

        assertThat(respuesta.getNombre()).isEqualTo("Cliente");
        noSeModificoNada();
    }

    @Test
    void elUsuarioDeSoloLecturaNoEditaPools() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> poolService.editar(PROCESO_ID, POOL_CLIENTE,
                new EditarPoolDto("Cliente", true, null), USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        noSeModificoNada();
    }

    @Test
    void elAdministradorEliminaUnPoolVacioDeFormaLogicaYRenumeraLosDemas() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        Pool propietario = proceso.poolPropietario();
        Pool cliente = existe(pool(POOL_CLIENTE, "Cliente", TipoPool.EXTERNO, 2, true, null));
        Pool proveedor = pool(POOL_PROVEEDOR, "Proveedor", TipoPool.EXTERNO, 3, false, null);
        sinContenido(POOL_CLIENTE);
        losPoolsActivosSon(propietario, cliente, proveedor);

        PoolRespuestaDto eliminado = poolService.eliminar(PROCESO_ID, POOL_CLIENTE, USERNAME);

        assertThat(cliente.isActivo()).isFalse();
        assertThat(eliminado.isActivo()).isFalse();
        assertThat(propietario.getOrden()).isEqualTo(1);
        assertThat(proveedor.getOrden()).isEqualTo(2);
        verify(poolRepository).save(cliente);
        verify(poolRepository).saveAll(List.of(propietario, proveedor));
        verify(poolRepository, never()).delete(any(Pool.class));
        verify(poolRepository, never()).deleteById(anyLong());
        assertThat(historialGuardado().getCambiosRealizados()).isEqualTo("pool eliminado: 'Cliente'");
    }

    @Test
    void elPoolPropietarioNoSeElimina() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existe(proceso.poolPropietario());

        assertThatThrownBy(() -> poolService.eliminar(PROCESO_ID, POOL_PROPIETARIO, USERNAME))
                .isInstanceOf(PoolNoValidoException.class)
                .hasMessage(PoolService.PROPIETARIO_NO_SE_ELIMINA);

        noSeModificoNada();
    }

    @Test
    void unPoolConContenidoNoSeElimina() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        Pool cliente = existe(pool(POOL_CLIENTE, "Cliente", TipoPool.EXTERNO, 2, false, null));
        when(poolRepository.lanesActivas(POOL_CLIENTE)).thenReturn(1L);
        when(poolRepository.actividadesActivas(POOL_CLIENTE)).thenReturn(0L);
        when(poolRepository.gatewaysActivos(POOL_CLIENTE)).thenReturn(0L);

        assertThatThrownBy(() -> poolService.eliminar(PROCESO_ID, POOL_CLIENTE, USERNAME))
                .isInstanceOf(PoolConContenidoException.class)
                .hasMessage("El pool 'Cliente' no se puede eliminar: contiene 1 lanes, 0 actividades y 0 gateways"
                        + " activos");

        assertThat(cliente.isActivo()).isTrue();
        noSeModificoNada();
    }

    @Test
    void unPoolYaEliminadoNoSeEliminaDeNuevo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existe(pool(POOL_CLIENTE, "Cliente", TipoPool.EXTERNO, 2, false, null)).setActivo(false);

        assertThatThrownBy(() -> poolService.eliminar(PROCESO_ID, POOL_CLIENTE, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(PoolService.POOL_ELIMINADO);

        noSeModificoNada();
    }

    @Test
    void elEditorNoEliminaPoolsConLaPoliticaPorDefecto() {
        autenticar(RolUsuario.EDITOR);
        existeElProceso(EMPRESA_PROPIA, false);

        assertThatThrownBy(() -> poolService.eliminar(PROCESO_ID, POOL_CLIENTE, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("El rol EDITOR no tiene permiso para eliminar pools en este proceso");

        noSeModificoNada();
    }

    @Test
    void losRolesDisponiblesSonLosRolesActivosDeLaEmpresaPropietaria() {
        autenticar(RolUsuario.SOLO_LECTURA);
        existeElProceso(EMPRESA_PROPIA, false);
        existe(proceso.poolPropietario());
        when(rolProcesoRepository.findByEmpresaIdAndActivoTrueOrderByNombreAsc(EMPRESA_PROPIA)).thenReturn(List.of(
                new RolProceso(40L, "Analista", "Evalua", empresa(EMPRESA_PROPIA), true),
                new RolProceso(41L, "Supervisor", "Aprueba", empresa(EMPRESA_PROPIA), true)));

        List<RolProcesoRespuestaDto> roles = poolService.rolesDisponibles(PROCESO_ID, POOL_PROPIETARIO, USERNAME);

        assertThat(roles).extracting(RolProcesoRespuestaDto::getNombre).containsExactly("Analista", "Supervisor");
        verify(rolProcesoRepository, never()).findByEmpresaIdAndActivoTrueOrderByNombreAsc(EMPRESA_AJENA);
    }

    @Test
    void unPoolCajaNegraNoOfreceRolesParaLanes() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_PROPIA, false);
        existe(pool(POOL_CLIENTE, "Cliente", TipoPool.EXTERNO, 2, true, null));

        assertThatThrownBy(() -> poolService.rolesDisponibles(PROCESO_ID, POOL_CLIENTE, USERNAME))
                .isInstanceOf(PoolCajaNegraException.class)
                .hasMessage("El pool 'Cliente'" + PoolService.CAJA_NEGRA);
    }

    @Test
    void unaEmpresaInvitadaNoConsultaLosRolesDelPropietario() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);

        assertThatThrownBy(() -> poolService.rolesDisponibles(PROCESO_ID, POOL_PROPIETARIO, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(procesoRepository, never()).estaCompartidoCon(anyLong(), anyLong());
        verify(rolProcesoRepository, never()).findByEmpresaIdAndActivoTrueOrderByNombreAsc(anyLong());
    }

    @Test
    void sinPoolIndicadoUnNodoVaAlPoolPropietarioSiEsElUnico() {
        construirProceso(EMPRESA_PROPIA, false);
        losPoolsActivosSon(proceso.poolPropietario());

        assertThat(poolService.poolParaNodo(proceso, null)).isSameAs(proceso.poolPropietario());
    }

    @Test
    void sinPoolIndicadoYConVariosPoolsNoSeAdivina() {
        construirProceso(EMPRESA_PROPIA, false);
        losPoolsActivosSon(proceso.poolPropietario(), pool(POOL_CLIENTE, "Cliente", TipoPool.EXTERNO, 2, false,
                null));

        assertThatThrownBy(() -> poolService.poolParaNodo(proceso, null))
                .isInstanceOf(PoolNoValidoException.class)
                .hasMessage(PoolService.POOL_REQUERIDO);
    }

    @Test
    void unPoolIndicadoCajaNegraNoAdmiteNodos() {
        construirProceso(EMPRESA_PROPIA, false);
        existe(pool(POOL_CLIENTE, "Cliente", TipoPool.EXTERNO, 2, true, null));

        assertThatThrownBy(() -> poolService.poolParaNodo(proceso, POOL_CLIENTE))
                .isInstanceOf(PoolCajaNegraException.class);
    }

    @Test
    void unPoolIndicadoQueAdmiteElementosSeEntrega() {
        construirProceso(EMPRESA_PROPIA, false);
        Pool cliente = existe(pool(POOL_CLIENTE, "Cliente", TipoPool.EXTERNO, 2, false, null));

        assertThat(poolService.poolParaNodo(proceso, POOL_CLIENTE)).isSameAs(cliente);
    }
}
