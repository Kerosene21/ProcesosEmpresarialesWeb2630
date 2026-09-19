package co.edu.javeriana.procesosempresariales.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.NombreProcesoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@Service
public class ProcesoService {

    private static final String NOMBRE_DUPLICADO = "Ya existe un proceso con ese nombre en la empresa";

    private final ProcesoRepository procesoRepository;
    // repo relacionar al usuario autenticado con su empresa
    private final UsuarioRepository usuarioRepository;
    // Cada modificación del proceso se acompaña de una entrada en historial
    private final HistorialProcesoRepository historialProcesoRepository;
    // ModelMapper para evitar escribir código repetitivo de conversión entre entidades y DTO
    private final ModelMapper modelMapper;

    // Las dependencias llegan por constructor, lo que hace explícito lo que necesita el servicio.
    public ProcesoService(ProcesoRepository procesoRepository, UsuarioRepository usuarioRepository,
            HistorialProcesoRepository historialProcesoRepository, ModelMapper modelMapper) {
        this.procesoRepository = procesoRepository;
        this.usuarioRepository = usuarioRepository;
        this.historialProcesoRepository = historialProcesoRepository;
        this.modelMapper = modelMapper;
    }

    @Transactional
    public ProcesoRespuestaDto crear(CrearProcesoDto dto, String username) {
        Usuario usuario = usuarioAutenticado(username);
        validarRolDeEscritura(usuario);
        Long empresaId = usuario.getEmpresa().getId();
        String nombre = dto.getNombre().trim();

        if (procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(empresaId, nombre)) {
            throw new NombreProcesoDuplicadoException(NOMBRE_DUPLICADO);
        }

        Proceso proceso = modelMapper.map(dto, Proceso.class);
        proceso.setNombre(nombre);
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setEmpresa(usuario.getEmpresa());
        proceso.setPool(new Pool(null, usuario.getEmpresa().getNombre()));
        try {
            return toDto(procesoRepository.save(proceso));
        } catch (DataIntegrityViolationException exception) {
            throw new NombreProcesoDuplicadoException(NOMBRE_DUPLICADO);
        }
    }

    @Transactional(readOnly = true)
    public ProcesoRespuestaDto obtener(Long procesoId, String username) {
        Usuario usuario = usuarioAutenticado(username);
        return toDto(procesoDeLaEmpresa(procesoId, usuario));
    }

    public boolean puedeEditar(String username) {
        Usuario usuario = usuarioAutenticado(username);
        return tieneRolDeEscritura(usuario);
    }

    @Transactional
    public ProcesoRespuestaDto editar(Long procesoId, EditarProcesoDto dto, String username) {
        Usuario usuario = usuarioAutenticado(username);
        validarRolDeEscritura(usuario);

        Proceso proceso = procesoDeLaEmpresa(procesoId, usuario);
        String nombreNuevo = dto.getNombre().trim();
        if (!proceso.getNombre().equalsIgnoreCase(nombreNuevo)
                && procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(usuario.getEmpresa().getId(), nombreNuevo)) {
            throw new NombreProcesoDuplicadoException(NOMBRE_DUPLICADO);
        }

        String cambios = construirCambios(proceso, dto, nombreNuevo);
        if (cambios.isEmpty()) {
            return toDto(proceso);
        }

        String estadoAnterior = proceso.getEstado().name();
        proceso.setNombre(nombreNuevo);
        proceso.setDescripcion(dto.getDescripcion());
        proceso.setCategoria(dto.getCategoria());
        proceso.setEstado(dto.getEstado());
        procesoRepository.save(proceso);

        historialProcesoRepository.save(new HistorialProceso(null, proceso, usuario, LocalDateTime.now(), cambios,
                estadoAnterior));
        return toDto(proceso);
    }

    @Transactional(readOnly = true)
    public List<HistorialProcesoRespuestaDto> consultarHistorial(Long procesoId, String username) {
        Usuario usuario = usuarioAutenticado(username);
        Proceso proceso = procesoDeLaEmpresa(procesoId, usuario);
        return historialProcesoRepository
                .findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(proceso.getId(), usuario.getEmpresa().getId())
                .stream()
                .map(this::toHistorialDto)
                .toList();
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

    private boolean tieneRolDeEscritura(Usuario usuario) {
        return usuario.getRol() == RolUsuario.ADMINISTRADOR || usuario.getRol() == RolUsuario.EDITOR;
    }

    private void validarRolDeEscritura(Usuario usuario) {
        if (!tieneRolDeEscritura(usuario)) {
            throw new UsuarioSinPermisoException("Solo un administrador o editor puede crear o modificar procesos");
        }
    }

    private String construirCambios(Proceso proceso, EditarProcesoDto dto, String nombreNuevo) {
        List<String> cambios = new ArrayList<>();
        agregarCambio(cambios, "nombre", proceso.getNombre(), nombreNuevo);
        agregarCambio(cambios, "descripcion", proceso.getDescripcion(), dto.getDescripcion());
        agregarCambio(cambios, "categoria", proceso.getCategoria(), dto.getCategoria());
        agregarCambio(cambios, "estado", proceso.getEstado().name(), dto.getEstado().name());
        return String.join("; ", cambios);
    }

    private void agregarCambio(List<String> cambios, String campo, String anterior, String nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            cambios.add(campo + ": '" + anterior + "' -> '" + nuevo + "'");
        }
    }

    private ProcesoRespuestaDto toDto(Proceso proceso) {
        ProcesoRespuestaDto response = modelMapper.map(proceso, ProcesoRespuestaDto.class);
        response.setPoolId(proceso.getPool().getId());
        return response;
    }

    private HistorialProcesoRespuestaDto toHistorialDto(HistorialProceso historial) {
        HistorialProcesoRespuestaDto respuesta = new HistorialProcesoRespuestaDto();
        respuesta.setFecha(historial.getFecha());
        respuesta.setUsuarioCorreo(historial.getUsuario().getUsername());
        respuesta.setEstadoAnterior(historial.getEstadoAnterior());
        respuesta.setCambiosRealizados(historial.getCambiosRealizados());
        return respuesta;
    }
}
