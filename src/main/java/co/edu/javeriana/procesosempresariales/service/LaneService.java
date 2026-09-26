package co.edu.javeriana.procesosempresariales.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolProceso;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.LaneNoValidaException;
import co.edu.javeriana.procesosempresariales.repository.LaneRepository;

@Service
public class LaneService {

    static final String LANE_INVALIDA = "La lane indicada no existe o no pertenece a este proceso";
    static final String SIN_PERMISO_ESCRITURA = "Solo un administrador o editor puede modificar las lanes del proceso";

    private LaneRepository laneRepository;
    private AccesoProcesoService accesoProcesoService;
    private RolProcesoService rolProcesoService;
    private HistorialProcesoService historialProcesoService;

    @Autowired
    public LaneService(LaneRepository laneRepository, AccesoProcesoService accesoProcesoService,
            RolProcesoService rolProcesoService, HistorialProcesoService historialProcesoService) {
        this.laneRepository = laneRepository;
        this.accesoProcesoService = accesoProcesoService;
        this.rolProcesoService = rolProcesoService;
        this.historialProcesoService = historialProcesoService;
    }

    @Transactional(readOnly = true)
    public Lane laneDelProceso(Long laneId, Proceso proceso) {
        return laneRepository.findByIdAndPoolId(laneId, proceso.getPool().getId())
                .orElseThrow(() -> new LaneNoValidaException(LANE_INVALIDA));
    }

    @Transactional(readOnly = true)
    public List<LaneRespuestaDto> lanesDe(Proceso proceso) {
        return laneRepository.findByPoolIdOrderByIdAsc(proceso.getPool().getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public LaneRespuestaDto asignarRolProceso(Long procesoId, Long laneId, Long rolProcesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        Lane lane = laneDelProceso(laneId, proceso);
        RolProceso rol = rolProcesoService.rolActivoDeLaEmpresa(rolProcesoId, usuario);

        if (lane.getRolProceso() != null && rol.getId().equals(lane.getRolProceso().getId())) {
            return toDto(lane);
        }

        String nombreAnterior = lane.nombreFuncional();
        lane.setRolProceso(rol);
        laneRepository.save(lane);
        historialProcesoService.registrar(proceso, usuario,
                "lane '" + nombreAnterior + "': rol de proceso -> '" + rol.getNombre() + "'");
        return toDto(lane);
    }

    private LaneRespuestaDto toDto(Lane lane) {
        Long rolProcesoId = lane.getRolProceso() == null ? null : lane.getRolProceso().getId();
        return new LaneRespuestaDto(lane.getId(), lane.nombreFuncional(), rolProcesoId);
    }
}
