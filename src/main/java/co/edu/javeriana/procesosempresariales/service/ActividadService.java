package co.edu.javeriana.procesosempresariales.service;

import java.time.LocalDateTime;
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
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.EditarActividadDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.LaneNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.NombreActividadDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.LaneRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;

@Service
public class ActividadService {

    private static String NOMBRE_DUPLICADO = "Ya existe una actividad con ese nombre en el proceso";
    private static String LANE_INVALIDA = "La lane indicada no existe o no pertenece a este proceso";
    private static String PROCESO_ELIMINADO = "El proceso ya fue eliminado";
    private static String ACTIVIDAD_ELIMINADA = "La actividad ya fue eliminada";

    private ActividadRepository actividadRepository;
    private ProcesoRepository procesoRepository;
    private LaneRepository laneRepository;
    private UsuarioService usuarioService;
    private HistorialProcesoService historialProcesoService;
    private ConexionesService conexionesService;
    private ModelMapper modelMapper;

    @Autowired
    public ActividadService(ActividadRepository actividadRepository,
                            ProcesoRepository procesoRepository,
                            LaneRepository laneRepository,
                            UsuarioService usuarioService,
                            HistorialProcesoService historialProcesoService,
                            ConexionesService conexionesService,
                            ModelMapper modelMapper) {
        this.actividadRepository = actividadRepository;
        this.procesoRepository = procesoRepository;
        this.laneRepository = laneRepository;
        this.usuarioService = usuarioService;
        this.historialProcesoService = historialProcesoService;
        this.conexionesService = conexionesService;
        this.modelMapper = modelMapper;
    }

    @Transactional
    public ActividadRespuestaDto crear(Long procesoId, CrearActividadDto dto, String username) {
        Usuario usuario = usuarioAutenticado(username);
        validarRolDeEscritura(usuario);
        Proceso proceso = procesoActivoDeLaEmpresa(procesoId, usuario);

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

        registrarHistorial(proceso, usuario, "actividad creada: '" + nombre + "'");
        return toDto(guardada);
    }

    @Transactional(readOnly = true)
    public ActividadRespuestaDto obtener(Long procesoId, Long actividadId, String username) {
        Usuario usuario = usuarioAutenticado(username);
        return toDto(actividadActivaDelProceso(actividadId, procesoDeLaEmpresa(procesoId, usuario)));
    }

    @Transactional(readOnly = true)
    public List<ActividadRespuestaDto> consultarActivas(Long procesoId, String username) {
        Usuario usuario = usuarioAutenticado(username);
        Proceso proceso = procesoDeLaEmpresa(procesoId, usuario);
        return actividadRepository.activasDelProceso(proceso.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LaneRespuestaDto> lanesDelProceso(Long procesoId, String username) {
        Usuario usuario = usuarioAutenticado(username);
        Proceso proceso = procesoDeLaEmpresa(procesoId, usuario);
        return laneRepository.findByPoolIdOrderByIdAsc(proceso.getPool().getId()).stream()
                .map(lane -> new LaneRespuestaDto(lane.getId(), lane.getNombre()))
                .toList();
    }

    @Transactional
    public ActividadRespuestaDto editar(Long procesoId, Long actividadId, EditarActividadDto dto, String username) {
        Usuario usuario = usuarioAutenticado(username);
        validarRolDeEscritura(usuario);
        Proceso proceso = procesoActivoDeLaEmpresa(procesoId, usuario);
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

        registrarHistorial(proceso, usuario, resumen);
        return toDto(actividad);
    }

    @Transactional(readOnly = true)
    public ActividadRespuestaDto obtenerParaEliminar(Long procesoId, Long actividadId, String username) {
        Usuario usuario = usuarioAutenticado(username);
        validarRolAdministrador(usuario);
        return toDto(actividadActivaDelProceso(actividadId, procesoActivoDeLaEmpresa(procesoId, usuario)));
    }

    @Transactional
    public ActividadRespuestaDto eliminar(Long procesoId, Long actividadId, String username) {
        Usuario usuario = usuarioAutenticado(username);
        validarRolAdministrador(usuario);
        Proceso proceso = procesoActivoDeLaEmpresa(procesoId, usuario);
        Actividad actividad = actividadActivaDelProceso(actividadId, proceso);

        actividad.setActivo(false);
        actividadRepository.save(actividad);

        List<Arco> desactivados = conexionesService.desactivarConectadosA(proceso, TipoNodoFlujo.ACTIVIDAD,
                actividad.getId());
        registrarHistorial(proceso, usuario, "actividad eliminada: '" + actividad.getNombre() + "'"
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

    private Usuario usuarioAutenticado(String username) {
        return usuarioService.buscarPorUsername(username);
    }

    private Proceso procesoDeLaEmpresa(Long procesoId, Usuario usuario) {
        Proceso proceso = procesoRepository.findById(procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El proceso no existe"));
        if (!proceso.getEmpresa().getId().equals(usuario.getEmpresa().getId())) {
            throw new UsuarioSinPermisoException("El proceso no pertenece a la empresa del usuario");
        }
        return proceso;
    }

    private Proceso procesoActivoDeLaEmpresa(Long procesoId, Usuario usuario) {
        Proceso proceso = procesoDeLaEmpresa(procesoId, usuario);
        if (proceso.isEliminado()) {
            throw new RecursoNoEncontradoException(PROCESO_ELIMINADO);
        }
        return proceso;
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

    private boolean tieneRolDeEscritura(Usuario usuario) {
        return usuario.getRol() == RolUsuario.ADMINISTRADOR || usuario.getRol() == RolUsuario.EDITOR;
    }

    private void validarRolDeEscritura(Usuario usuario) {
        if (!tieneRolDeEscritura(usuario)) {
            throw new UsuarioSinPermisoException("Solo un administrador o editor puede crear o modificar actividades");
        }
    }

    private void validarRolAdministrador(Usuario usuario) {
        if (usuario.getRol() != RolUsuario.ADMINISTRADOR) {
            throw new UsuarioSinPermisoException("Solo un administrador puede eliminar actividades");
        }
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

    private void registrarHistorial(Proceso proceso, Usuario usuario, String cambios) {
        historialProcesoService.registrarHistorial(new HistorialProceso(null, proceso, usuario, LocalDateTime.now(), cambios,
                proceso.getEstado().name()));
    }

    private ActividadRespuestaDto toDto(Actividad actividad) {
        ActividadRespuestaDto respuesta = modelMapper.map(actividad, ActividadRespuestaDto.class);
        respuesta.setProcesoId(actividad.getProceso().getId());
        respuesta.setLaneId(actividad.getLane().getId());
        respuesta.setLaneNombre(actividad.getLane().getNombre());
        return respuesta;
    }
}
