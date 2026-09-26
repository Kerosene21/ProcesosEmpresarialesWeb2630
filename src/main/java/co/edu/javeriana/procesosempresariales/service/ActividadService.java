package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Actividad;
import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.EditarActividadDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.LaneNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.NombreActividadDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.LaneRepository;

@Service
public class ActividadService {

    private static final String NOMBRE_DUPLICADO = "Ya existe una actividad con ese nombre en el proceso";
    private static final String LANE_INVALIDA = "La lane indicada no existe o no pertenece a este proceso";
    private static final String ACTIVIDAD_ELIMINADA = "La actividad ya fue eliminada";
    private static final String SIN_PERMISO_ESCRITURA =
            "Solo un administrador o editor puede crear o modificar actividades";
    private static final String SIN_PERMISO_ELIMINAR = "Solo un administrador puede eliminar actividades";

    private ActividadRepository actividadRepository;
    private LaneRepository laneRepository;
    private AccesoProcesoService accesoProcesoService;
    private HistorialProcesoService historialProcesoService;
    private ConexionesService conexionesService;
    private ModelMapper modelMapper;

    @Autowired
    public ActividadService(ActividadRepository actividadRepository, LaneRepository laneRepository,
            AccesoProcesoService accesoProcesoService, HistorialProcesoService historialProcesoService,
            ConexionesService conexionesService, ModelMapper modelMapper) {
        this.actividadRepository = actividadRepository;
        this.laneRepository = laneRepository;
        this.accesoProcesoService = accesoProcesoService;
        this.historialProcesoService = historialProcesoService;
        this.conexionesService = conexionesService;
        this.modelMapper = modelMapper;
    }

    @Transactional
    public ActividadRespuestaDto crear(Long procesoId, CrearActividadDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);

        String nombre = dto.getNombre().trim();
        if (actividadRepository.existsByProcesoIdAndNombreIgnoreCase(procesoId, nombre)) {
            throw new NombreActividadDuplicadoException(NOMBRE_DUPLICADO);
        }

        Lane lane = laneDelProceso(dto.getLaneId(), proceso);

        Actividad actividad = new Actividad();
        actividad.setNombre(nombre);
        actividad.setTipo(dto.getTipo());
        actividad.setProceso(proceso);
        actividad.setLane(lane);
        actividad.setPosicionX(dto.getPosicionX());
        actividad.setPosicionY(dto.getPosicionY());
        actividad.setActivo(true);

        Actividad guardada;
        try {
            guardada = actividadRepository.save(actividad);
        } catch (DataIntegrityViolationException exception) {
            throw new NombreActividadDuplicadoException(NOMBRE_DUPLICADO);
        }

        historialProcesoService.registrar(proceso, usuario, "actividad creada: '" + nombre + "'");
        return toDto(guardada);
    }

    @Transactional(readOnly = true)
    public ActividadRespuestaDto obtener(Long procesoId, Long actividadId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        return toDto(actividadActivaDelProceso(actividadId,
                accesoProcesoService.procesoDeLaEmpresa(procesoId, usuario)));
    }

    @Transactional(readOnly = true)
    public List<ActividadRespuestaDto> consultarActivas(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoDeLaEmpresa(procesoId, usuario);
        return actividadRepository.activasDelProceso(proceso.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LaneRespuestaDto> lanesDelProceso(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoDeLaEmpresa(procesoId, usuario);
        return laneRepository.findByPoolIdOrderByIdAsc(proceso.getPool().getId()).stream()
                .map(lane -> new LaneRespuestaDto(lane.getId(), lane.getNombre()))
                .toList();
    }

    @Transactional
    public ActividadRespuestaDto editar(Long procesoId, Long actividadId, EditarActividadDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        Actividad actividad = actividadActivaDelProceso(actividadId, proceso);

        String nombreNuevo = dto.getNombre().trim();
        if (!actividad.getNombre().equalsIgnoreCase(nombreNuevo)
                && actividadRepository.existsByProcesoIdAndNombreIgnoreCase(procesoId, nombreNuevo)) {
            throw new NombreActividadDuplicadoException(NOMBRE_DUPLICADO);
        }

        Lane laneNueva = laneDelProceso(dto.getLaneId(), proceso);

        String cambios = construirCambios(actividad, dto, nombreNuevo, laneNueva);
        if (cambios.isEmpty()) {
            return toDto(actividad);
        }

        String resumen = "actividad '" + actividad.getNombre() + "': " + cambios;
        actividad.setNombre(nombreNuevo);
        actividad.setTipo(dto.getTipo());
        actividad.setLane(laneNueva);
        actividadRepository.save(actividad);

        historialProcesoService.registrar(proceso, usuario, resumen);
        return toDto(actividad);
    }

    @Transactional(readOnly = true)
    public ActividadRespuestaDto obtenerParaEliminar(Long procesoId, Long actividadId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        return toDto(actividadActivaDelProceso(actividadId,
                accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario)));
    }

    @Transactional
    public ActividadRespuestaDto eliminar(Long procesoId, Long actividadId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        Actividad actividad = actividadActivaDelProceso(actividadId, proceso);

        actividad.setActivo(false);
        actividadRepository.save(actividad);

        List<Arco> desactivados = conexionesService.desactivarConectadosA(proceso, TipoNodoFlujo.ACTIVIDAD,
                actividad.getId());
        historialProcesoService.registrar(proceso, usuario, "actividad eliminada: '" + actividad.getNombre() + "'"
                + resumenDeConexiones(desactivados));

        ActividadRespuestaDto respuesta = toDto(actividad);
        respuesta.setArcosDesactivados(desactivados.size());
        respuesta.setAdvertencias(conexionesService.advertenciasTrasDesactivar(proceso, desactivados,
                TipoNodoFlujo.ACTIVIDAD, actividad.getId()));
        return respuesta;
    }

    private String resumenDeConexiones(List<Arco> desactivados) {
        if (desactivados.isEmpty()) {
            return "";
        }
        return "; arcos desactivados: " + desactivados.size();
    }

    private Actividad actividadActivaDelProceso(Long actividadId, Proceso proceso) {
        Actividad actividad = actividadRepository.findByIdAndProcesoId(actividadId, proceso.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La actividad no existe en este proceso"));
        if (!actividad.isActivo()) {
            throw new RecursoNoEncontradoException(ACTIVIDAD_ELIMINADA);
        }
        return actividad;
    }

    private Lane laneDelProceso(Long laneId, Proceso proceso) {
        return laneRepository.findByIdAndPoolId(laneId, proceso.getPool().getId())
                .orElseThrow(() -> new LaneNoValidaException(LANE_INVALIDA));
    }

    private String construirCambios(Actividad actividad, EditarActividadDto dto, String nombreNuevo, Lane laneNueva) {
        List<String> cambios = new ArrayList<>();
        agregarCambio(cambios, "nombre", actividad.getNombre(), nombreNuevo);
        agregarCambio(cambios, "tipo", actividad.getTipo().name(), dto.getTipo().name());
        if (!Objects.equals(actividad.getLane().getId(), laneNueva.getId())) {
            cambios.add("lane: '" + actividad.getLane().getNombre() + "' -> '" + laneNueva.getNombre() + "'");
        }
        return String.join("; ", cambios);
    }

    private void agregarCambio(List<String> cambios, String campo, String anterior, String nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            cambios.add(campo + ": '" + anterior + "' -> '" + nuevo + "'");
        }
    }

    private ActividadRespuestaDto toDto(Actividad actividad) {
        ActividadRespuestaDto respuesta = modelMapper.map(actividad, ActividadRespuestaDto.class);
        respuesta.setProcesoId(actividad.getProceso().getId());
        respuesta.setLaneId(actividad.getLane().getId());
        respuesta.setLaneNombre(actividad.getLane().getNombre());
        return respuesta;
    }
}
