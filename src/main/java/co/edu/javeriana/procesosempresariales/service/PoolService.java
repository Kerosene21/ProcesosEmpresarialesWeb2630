package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.OperacionEstructura;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
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
import co.edu.javeriana.procesosempresariales.repository.PoolRepository;

@Service
public class PoolService {

    static final String POOL_NO_EXISTE = "El pool no existe en este proceso";
    static final String POOL_ELIMINADO = "El pool ya fue eliminado";
    static final String PROPIETARIO_UNICO =
            "El pool propietario se crea con el proceso: no se puede crear otro";
    static final String PROPIETARIO_NO_CAJA_NEGRA = "El pool propietario no puede ser una caja negra";
    static final String PROPIETARIO_NO_SE_ELIMINA = "El pool propietario no se puede eliminar";
    static final String PROPIETARIO_SIN_PARTICIPANTE =
            "El pool propietario ya representa a la empresa dueña del proceso";
    static final String PARTICIPANTE_SIN_EMPRESA = "Un pool participante debe indicar la empresa que representa";
    static final String PARTICIPANTE_PROPIETARIA =
            "La empresa propietaria ya está representada por el pool propietario";
    static final String EXTERNO_CON_EMPRESA =
            "Un pool externo representa a un participante no registrado y no lleva empresa";
    static final String CAJA_NEGRA = " es una caja negra y no admite elementos internos";
    static final String POOL_REQUERIDO = "El proceso tiene varios pools: indica en qué pool va el elemento";

    private PoolRepository poolRepository;
    private AccesoProcesoService accesoProcesoService;
    private PermisoEstructuraService permisoEstructuraService;
    private HistorialProcesoService historialProcesoService;
    private EmpresaService empresaService;
    private RolProcesoService rolProcesoService;

    @Autowired
    public PoolService(PoolRepository poolRepository, AccesoProcesoService accesoProcesoService,
            PermisoEstructuraService permisoEstructuraService, HistorialProcesoService historialProcesoService,
            EmpresaService empresaService, RolProcesoService rolProcesoService) {
        this.poolRepository = poolRepository;
        this.accesoProcesoService = accesoProcesoService;
        this.permisoEstructuraService = permisoEstructuraService;
        this.historialProcesoService = historialProcesoService;
        this.empresaService = empresaService;
        this.rolProcesoService = rolProcesoService;
    }

    @Transactional(readOnly = true)
    public List<PoolRespuestaDto> listar(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoVisiblePara(procesoId, usuario);
        return activosDe(proceso).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PoolRespuestaDto obtener(Long procesoId, Long poolId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoVisiblePara(procesoId, usuario);
        return toDto(poolActivoDelProceso(poolId, proceso));
    }

    @Transactional
    public PoolRespuestaDto crear(Long procesoId, CrearPoolDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        permisoEstructuraService.exigir(proceso, usuario, OperacionEstructura.CREAR_POOL);
        if (dto.getTipo() == TipoPool.PROPIETARIO) {
            throw new PoolNoValidoException(PROPIETARIO_UNICO);
        }
        Empresa participante = empresaRepresentada(dto.getTipo(), dto.getEmpresaParticipanteId(), proceso);

        String nombre = dto.getNombre().trim();
        Pool pool = new Pool(null, proceso, nombre, dto.getTipo(), activosDe(proceso).size() + 1, dto.esCajaNegra(),
                true, participante, new ArrayList<>());
        Pool guardado = poolRepository.save(pool);
        historialProcesoService.registrar(proceso, usuario,
                "pool creado: '" + nombre + "' (" + descripcion(guardado) + ")");
        return toDto(guardado);
    }

    @Transactional
    public PoolRespuestaDto editar(Long procesoId, Long poolId, EditarPoolDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        permisoEstructuraService.exigir(proceso, usuario, OperacionEstructura.EDITAR_POOL);
        Pool pool = poolActivoDelProceso(poolId, proceso);
        if (pool.esPropietario() && dto.esCajaNegra()) {
            throw new PoolNoValidoException(PROPIETARIO_NO_CAJA_NEGRA);
        }
        Empresa participante = empresaRepresentada(pool.getTipo(), dto.getEmpresaParticipanteId(), proceso);
        if (dto.esCajaNegra() && !pool.isCajaNegra()) {
            exigirSinContenido(pool, "no puede convertirse en caja negra");
        }

        String nombreNuevo = dto.getNombre().trim();
        List<String> cambios = new ArrayList<>();
        if (!pool.getNombre().equals(nombreNuevo)) {
            cambios.add("nombre: '" + pool.getNombre() + "' -> '" + nombreNuevo + "'");
        }
        if (pool.isCajaNegra() != dto.esCajaNegra()) {
            cambios.add("caja negra: " + pool.isCajaNegra() + " -> " + dto.esCajaNegra());
        }
        if (!Objects.equals(idDe(pool.getEmpresaParticipante()), idDe(participante))) {
            cambios.add("empresa participante: '" + nombreDe(pool.getEmpresaParticipante()) + "' -> '"
                    + nombreDe(participante) + "'");
        }
        if (cambios.isEmpty()) {
            return toDto(pool);
        }

        String resumen = "pool '" + pool.getNombre() + "': " + String.join("; ", cambios);
        pool.setNombre(nombreNuevo);
        pool.setCajaNegra(dto.esCajaNegra());
        pool.setEmpresaParticipante(participante);
        poolRepository.save(pool);
        historialProcesoService.registrar(proceso, usuario, resumen);
        return toDto(pool);
    }

    @Transactional
    public PoolRespuestaDto eliminar(Long procesoId, Long poolId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        permisoEstructuraService.exigir(proceso, usuario, OperacionEstructura.ELIMINAR_POOL);
        Pool pool = poolActivoDelProceso(poolId, proceso);
        if (pool.esPropietario()) {
            throw new PoolNoValidoException(PROPIETARIO_NO_SE_ELIMINA);
        }
        exigirSinContenido(pool, "no se puede eliminar");

        pool.setActivo(false);
        poolRepository.save(pool);
        List<Pool> restantes = activosDe(proceso).stream()
                .filter(activo -> !activo.getId().equals(pool.getId()))
                .toList();
        for (int indice = 0; indice < restantes.size(); indice++) {
            restantes.get(indice).setOrden(indice + 1);
        }
        poolRepository.saveAll(restantes);
        historialProcesoService.registrar(proceso, usuario, "pool eliminado: '" + pool.getNombre() + "'");
        return toDto(pool);
    }

    @Transactional(readOnly = true)
    public List<RolProcesoRespuestaDto> rolesDisponibles(Long procesoId, Long poolId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoDeLaEmpresa(procesoId, usuario);
        poolConElementos(poolId, proceso);
        return rolProcesoService.activosDeLaEmpresa(proceso.getEmpresa().getId());
    }

    @Transactional(readOnly = true)
    public Pool poolActivoDelProceso(Long poolId, Proceso proceso) {
        Pool pool = poolRepository.findByIdAndProcesoId(poolId, proceso.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(POOL_NO_EXISTE));
        if (!pool.isActivo()) {
            throw new RecursoNoEncontradoException(POOL_ELIMINADO);
        }
        return pool;
    }

    @Transactional(readOnly = true)
    public Pool poolConElementos(Long poolId, Proceso proceso) {
        Pool pool = poolActivoDelProceso(poolId, proceso);
        exigirQueAdmitaElementos(pool);
        return pool;
    }

    @Transactional(readOnly = true)
    public Pool poolParaNodo(Proceso proceso, Long poolId) {
        if (poolId != null) {
            return poolConElementos(poolId, proceso);
        }
        List<Pool> activos = activosDe(proceso);
        if (activos.size() != 1 || !activos.get(0).esPropietario()) {
            throw new PoolNoValidoException(POOL_REQUERIDO);
        }
        return activos.get(0);
    }

    private List<Pool> activosDe(Proceso proceso) {
        return poolRepository.findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(proceso.getId());
    }

    private void exigirQueAdmitaElementos(Pool pool) {
        if (pool.isCajaNegra()) {
            throw new PoolCajaNegraException("El pool '" + pool.getNombre() + "'" + CAJA_NEGRA);
        }
    }

    private void exigirSinContenido(Pool pool, String accion) {
        long lanes = poolRepository.lanesActivas(pool.getId());
        long actividades = poolRepository.actividadesActivas(pool.getId());
        long gateways = poolRepository.gatewaysActivos(pool.getId());
        if (lanes + actividades + gateways > 0) {
            throw new PoolConContenidoException("El pool '" + pool.getNombre() + "' " + accion + ": contiene "
                    + lanes + " lanes, " + actividades + " actividades y " + gateways + " gateways activos");
        }
    }

    private Empresa empresaRepresentada(TipoPool tipo, Long empresaId, Proceso proceso) {
        return switch (tipo) {
            case PROPIETARIO -> {
                if (empresaId != null) {
                    throw new PoolNoValidoException(PROPIETARIO_SIN_PARTICIPANTE);
                }
                yield null;
            }
            case EXTERNO -> {
                if (empresaId != null) {
                    throw new PoolNoValidoException(EXTERNO_CON_EMPRESA);
                }
                yield null;
            }
            case PARTICIPANTE -> {
                if (empresaId == null) {
                    throw new PoolNoValidoException(PARTICIPANTE_SIN_EMPRESA);
                }
                if (empresaId.equals(proceso.getEmpresa().getId())) {
                    throw new PoolNoValidoException(PARTICIPANTE_PROPIETARIA);
                }
                yield empresaService.buscarPorId(empresaId);
            }
        };
    }

    private String descripcion(Pool pool) {
        return pool.getTipo() + (pool.isCajaNegra() ? ", caja negra" : "");
    }

    private Long idDe(Empresa empresa) {
        return empresa == null ? null : empresa.getId();
    }

    private String nombreDe(Empresa empresa) {
        return empresa == null ? "" : empresa.getNombre();
    }

    private PoolRespuestaDto toDto(Pool pool) {
        PoolRespuestaDto respuesta = new PoolRespuestaDto();
        respuesta.setId(pool.getId());
        respuesta.setProcesoId(pool.getProceso().getId());
        respuesta.setNombre(pool.getNombre());
        respuesta.setTipo(pool.getTipo());
        respuesta.setOrden(pool.getOrden());
        respuesta.setCajaNegra(pool.isCajaNegra());
        respuesta.setActivo(pool.isActivo());
        Empresa representada = pool.esPropietario() ? pool.getProceso().getEmpresa() : pool.getEmpresaParticipante();
        respuesta.setEmpresaId(idDe(representada));
        respuesta.setEmpresaNombre(representada == null ? null : representada.getNombre());
        return respuesta;
    }
}
