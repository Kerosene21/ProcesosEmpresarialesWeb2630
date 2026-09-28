package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.OperacionEstructura;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolProceso;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CrearLaneDto;
import co.edu.javeriana.procesosempresariales.dto.EditarLaneDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ReordenarLanesDto;
import co.edu.javeriana.procesosempresariales.exception.LaneConActividadesException;
import co.edu.javeriana.procesosempresariales.exception.LaneDuplicadaException;
import co.edu.javeriana.procesosempresariales.exception.LaneNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.repository.LaneRepository;

@Service
public class LaneService {

    static final String LANE_INVALIDA = "La lane indicada no existe o no pertenece a este proceso";
    static final String LANE_NO_EXISTE = "La lane no existe en este pool";
    static final String ROL_REPETIDO = "El pool ya tiene una lane activa para ese rol de proceso";
    static final String ORDEN_FUERA_DE_RANGO = "El orden de la lane debe estar entre 1 y ";
    static final String ORDEN_INCOMPLETO = "El nuevo orden debe incluir una sola vez cada lane activa del pool";

    private LaneRepository laneRepository;
    private AccesoProcesoService accesoProcesoService;
    private RolProcesoService rolProcesoService;
    private HistorialProcesoService historialProcesoService;
    private PoolService poolService;
    private PermisoEstructuraService permisoEstructuraService;

    @Autowired
    public LaneService(LaneRepository laneRepository, AccesoProcesoService accesoProcesoService,
            RolProcesoService rolProcesoService, HistorialProcesoService historialProcesoService,
            PoolService poolService, PermisoEstructuraService permisoEstructuraService) {
        this.laneRepository = laneRepository;
        this.accesoProcesoService = accesoProcesoService;
        this.rolProcesoService = rolProcesoService;
        this.historialProcesoService = historialProcesoService;
        this.poolService = poolService;
        this.permisoEstructuraService = permisoEstructuraService;
    }

    @Transactional(readOnly = true)
    public Lane laneDelProceso(Long laneId, Proceso proceso) {
        return laneRepository.findByIdAndPoolProcesoIdAndActivoTrueAndPoolActivoTrue(laneId, proceso.getId())
                .orElseThrow(() -> new LaneNoValidaException(LANE_INVALIDA));
    }

    @Transactional(readOnly = true)
    public List<LaneRespuestaDto> lanesDe(Proceso proceso) {
        return laneRepository.activasDelProceso(proceso.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LaneRespuestaDto> listar(Long procesoId, Long poolId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoVisiblePara(procesoId, usuario);
        Pool pool = poolService.poolActivoDelProceso(poolId, proceso);
        return lanesDelPool(pool).stream().map(this::toDto).toList();
    }

    @Transactional
    public LaneRespuestaDto crear(Long procesoId, Long poolId, CrearLaneDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        permisoEstructuraService.exigir(proceso, usuario, OperacionEstructura.CREAR_LANE);
        Pool pool = poolService.poolConElementos(poolId, proceso);
        RolProceso rol = rolProcesoService.rolActivoDeLaEmpresa(dto.getRolProcesoId(), usuario);
        if (laneRepository.existsByPoolIdAndRolProcesoIdAndActivoTrue(pool.getId(), rol.getId())) {
            throw new LaneDuplicadaException(ROL_REPETIDO);
        }

        List<Lane> lanes = lanesDelPool(pool);
        int posicion = posicionSolicitada(dto.getOrden(), lanes.size() + 1);
        Lane lane = new Lane(null, null, pool, rol, posicion, true);
        lanes.add(posicion - 1, lane);
        numerar(lanes);
        laneRepository.saveAll(lanes);
        historialProcesoService.registrar(proceso, usuario, "lane creada en el pool '" + pool.getNombre() + "': '"
                + rol.getNombre() + "' en la posición " + posicion);
        return toDto(lane);
    }

    @Transactional
    public LaneRespuestaDto editar(Long procesoId, Long poolId, Long laneId, EditarLaneDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        permisoEstructuraService.exigir(proceso, usuario, OperacionEstructura.EDITAR_LANE);
        Pool pool = poolService.poolActivoDelProceso(poolId, proceso);
        Lane lane = laneDelPool(laneId, pool);
        RolProceso rol = rolProcesoService.rolActivoDeLaEmpresa(dto.getRolProcesoId(), usuario);

        String nombreAnterior = lane.nombreFuncional();
        List<String> cambios = new ArrayList<>();
        if (lane.getRolProceso() == null || !rol.getId().equals(lane.getRolProceso().getId())) {
            if (laneRepository.existsByPoolIdAndRolProcesoIdAndActivoTrueAndIdNot(pool.getId(), rol.getId(),
                    lane.getId())) {
                throw new LaneDuplicadaException(ROL_REPETIDO);
            }
            cambios.add("rol de proceso: '" + nombreAnterior + "' -> '" + rol.getNombre() + "'");
            lane.setRolProceso(rol);
        }
        List<Lane> reordenadas = List.of(lane);
        if (dto.getOrden() != null && dto.getOrden() != lane.getOrden()) {
            List<Lane> lanes = lanesDelPool(pool);
            int destino = posicionSolicitada(dto.getOrden(), lanes.size());
            cambios.add("orden: " + lane.getOrden() + " -> " + destino);
            lanes.removeIf(otra -> otra.getId().equals(lane.getId()));
            lanes.add(destino - 1, lane);
            numerar(lanes);
            reordenadas = lanes;
        }
        if (cambios.isEmpty()) {
            return toDto(lane);
        }

        laneRepository.saveAll(reordenadas);
        historialProcesoService.registrar(proceso, usuario,
                "lane '" + nombreAnterior + "' del pool '" + pool.getNombre() + "': " + String.join("; ", cambios));
        return toDto(lane);
    }

    @Transactional
    public List<LaneRespuestaDto> reordenar(Long procesoId, Long poolId, ReordenarLanesDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        permisoEstructuraService.exigir(proceso, usuario, OperacionEstructura.EDITAR_LANE);
        Pool pool = poolService.poolActivoDelProceso(poolId, proceso);

        List<Lane> actuales = lanesDelPool(pool);
        Map<Long, Lane> porId = actuales.stream().collect(Collectors.toMap(Lane::getId, Function.identity()));
        if (dto.getLanes().size() != actuales.size() || !porId.keySet().equals(new HashSet<>(dto.getLanes()))) {
            throw new LaneNoValidaException(ORDEN_INCOMPLETO);
        }
        List<Lane> nuevas = dto.getLanes().stream().map(porId::get).toList();
        if (nuevas.equals(actuales)) {
            return nuevas.stream().map(this::toDto).toList();
        }

        numerar(nuevas);
        laneRepository.saveAll(nuevas);
        historialProcesoService.registrar(proceso, usuario, "lanes del pool '" + pool.getNombre()
                + "' reordenadas: " + nuevas.stream().map(lane -> "'" + lane.nombreFuncional() + "'")
                        .collect(Collectors.joining(", ")));
        return nuevas.stream().map(this::toDto).toList();
    }

    @Transactional
    public LaneRespuestaDto eliminar(Long procesoId, Long poolId, Long laneId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        permisoEstructuraService.exigir(proceso, usuario, OperacionEstructura.ELIMINAR_LANE);
        Pool pool = poolService.poolActivoDelProceso(poolId, proceso);
        Lane lane = laneDelPool(laneId, pool);

        long actividades = laneRepository.actividadesActivas(lane.getId());
        if (actividades > 0) {
            throw new LaneConActividadesException("La lane '" + lane.nombreFuncional() + "' tiene " + actividades
                    + " actividades activas: reasígnalas a otra lane o elimínalas antes de eliminarla");
        }

        lane.setActivo(false);
        List<Lane> restantes = lanesDelPool(pool);
        restantes.removeIf(otra -> otra.getId().equals(lane.getId()));
        numerar(restantes);
        restantes.add(lane);
        laneRepository.saveAll(restantes);
        historialProcesoService.registrar(proceso, usuario, "lane eliminada del pool '" + pool.getNombre() + "': '"
                + lane.nombreFuncional() + "'");
        return toDto(lane);
    }

    private Lane laneDelPool(Long laneId, Pool pool) {
        return laneRepository.findByIdAndPoolIdAndActivoTrue(laneId, pool.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(LANE_NO_EXISTE));
    }

    private List<Lane> lanesDelPool(Pool pool) {
        return new ArrayList<>(laneRepository.findByPoolIdAndActivoTrueOrderByOrdenAscIdAsc(pool.getId()));
    }

    private int posicionSolicitada(Integer orden, int maximo) {
        if (orden == null) {
            return maximo;
        }
        if (orden < 1 || orden > maximo) {
            throw new LaneNoValidaException(ORDEN_FUERA_DE_RANGO + maximo);
        }
        return orden;
    }

    private void numerar(List<Lane> lanes) {
        for (int indice = 0; indice < lanes.size(); indice++) {
            lanes.get(indice).setOrden(indice + 1);
        }
    }

    private LaneRespuestaDto toDto(Lane lane) {
        LaneRespuestaDto respuesta = new LaneRespuestaDto(lane.getId(), lane.nombreFuncional());
        respuesta.setRolProcesoId(lane.getRolProceso() == null ? null : lane.getRolProceso().getId());
        respuesta.setPoolId(lane.getPool().getId());
        respuesta.setOrden(lane.getOrden());
        return respuesta;
    }
}
