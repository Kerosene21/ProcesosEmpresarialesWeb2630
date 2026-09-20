package co.edu.javeriana.procesosempresariales.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Actividad;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.EditarActividadDto;
import co.edu.javeriana.procesosempresariales.exception.LaneNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.NombreActividadDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.LaneRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@Service
public class ActividadService {

    private static final String NOMBRE_DUPLICADO = "Ya existe una actividad con ese nombre en el proceso";
    private static final String LANE_INVALIDA = "La lane indicada no existe o no pertenece a este proceso";

    private final ActividadRepository actividadRepository;
    private final ProcesoRepository procesoRepository;
    private final LaneRepository laneRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistorialProcesoRepository historialProcesoRepository;
    private final ModelMapper modelMapper;

    public ActividadService(ActividadRepository actividadRepository, ProcesoRepository procesoRepository,
            LaneRepository laneRepository, UsuarioRepository usuarioRepository,
            HistorialProcesoRepository historialProcesoRepository, ModelMapper modelMapper) {
        this.actividadRepository = actividadRepository;
        this.procesoRepository = procesoRepository;
        this.laneRepository = laneRepository;
        this.usuarioRepository = usuarioRepository;
        this.historialProcesoRepository = historialProcesoRepository;
        this.modelMapper = modelMapper;
    }

    @Transactional
    public ActividadRespuestaDto crear(Long procesoId, CrearActividadDto dto, String username) {
        Usuario usuario = usuarioAutenticado(username);
        validarRolDeEscritura(usuario);
        Proceso proceso = procesoDeLaEmpresa(procesoId, usuario);

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

        try {
            actividad = actividadRepository.save(actividad);
        } catch (DataIntegrityViolationException exception) {
            throw new NombreActividadDuplicadoException(NOMBRE_DUPLICADO);
        }

        registrarHistorial(proceso, usuario, "Actividad creada: '" + nombre + "'");
        return toDto(actividad);
    }

    @Transactional
    public ActividadRespuestaDto editar(Long procesoId, Long actividadId, EditarActividadDto dto, String username) {
        Usuario usuario = usuarioAutenticado(username);
        validarRolDeEscritura(usuario);
        Proceso proceso = procesoDeLaEmpresa(procesoId, usuario);
        Actividad actividad = actividadDelProceso(actividadId, proceso);

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

        // Cambiar de lane mueve la actividad a la banda del nuevo rol
        // responsable (no es solo un dato, es dónde se dibuja en el diagrama).
        actividad.setNombre(nombreNuevo);
        actividad.setTipo(dto.getTipo());
        actividad.setLane(laneNueva);
        // Los arcos conectados no se tocan: siguen apuntando al mismo id de
        // actividad, solo cambia la banda donde se dibuja.
        actividadRepository.save(actividad);

        registrarHistorial(proceso, usuario, cambios);
        return toDto(actividad);
    }

    @Transactional
    public void eliminar(Long procesoId, Long actividadId, String username) {
        Usuario usuario = usuarioAutenticado(username);
        validarRolAdministrador(usuario);
        Proceso proceso = procesoDeLaEmpresa(procesoId, usuario);
        Actividad actividad = actividadDelProceso(actividadId, proceso);

        // Eliminación lógica: la fila se conserva, solo se marca inactiva.
        actividad.setActivo(false);
        actividadRepository.save(actividad);

        // NOTA: aquí falta eliminar (lógicamente) los arcos que entraban y
        // salían de esta actividad y advertir si el proceso queda con
        // elementos desconectados, tal como pide HU-10. Esa parte depende
        // de la entidad Arco, que todavía no existe en el proyecto. Cuando
        // se implemente Arco, agregar aquí una llamada a un
        // ArcoRepository para desactivar los arcos que referencien esta
        // actividad (como origen o destino) y para chequear huérfanos.
        registrarHistorial(proceso, usuario, "Actividad eliminada: '" + actividad.getNombre() + "'");
    }

    private Usuario usuarioAutenticado(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario autenticado no existe"));
    }

    private Proceso procesoDeLaEmpresa(Long procesoId, Usuario usuario) {
        Proceso proceso = procesoRepository.findById(procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El proceso no existe"));
        if (!proceso.getEmpresa().getId().equals(usuario.getEmpresa().getId())) {
            throw new UsuarioSinPermisoException("El proceso no pertenece a la empresa del usuario");
        }
        return proceso;
    }

    private Actividad actividadDelProceso(Long actividadId, Proceso proceso) {
        Actividad actividad = actividadRepository.findByIdAndProcesoId(actividadId, proceso.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La actividad no existe en este proceso"));
        if (!actividad.isActivo()) {
            throw new RecursoNoEncontradoException("La actividad ya fue eliminada");
        }
        return actividad;
    }

    private Lane laneDelProceso(Long laneId, Proceso proceso) {
        // La lane debe pertenecer al mismo pool del proceso: así la actividad
        // queda dibujada dentro del pool correcto.
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
        historialProcesoRepository.save(new HistorialProceso(null, proceso, usuario, LocalDateTime.now(), cambios,
                proceso.getEstado().name()));
    }

    private ActividadRespuestaDto toDto(Actividad actividad) {
        ActividadRespuestaDto response = modelMapper.map(actividad, ActividadRespuestaDto.class);
        response.setProcesoId(actividad.getProceso().getId());
        response.setLaneId(actividad.getLane().getId());
        return response;
    }
}
