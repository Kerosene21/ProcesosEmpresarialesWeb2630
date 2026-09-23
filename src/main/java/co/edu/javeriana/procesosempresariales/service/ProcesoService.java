package co.edu.javeriana.procesosempresariales.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroProcesosDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoResumenDto;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadProceso;
import co.edu.javeriana.procesosempresariales.exception.NombreProcesoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
//import co.edu.javeriana.procesosempresariales.specification.ProcesoSpecifications;

@Service
public class ProcesoService {

    private static final String NOMBRE_DUPLICADO = "Ya existe un proceso con ese nombre en la empresa";
    private static final String LANE_INICIAL = "General";
    private static final int TAMANO_PAGINA = 10;
    private static final int LONGITUD_RESUMEN = 120;

    private ProcesoRepository procesoRepository;
    private UsuarioService usuarioService;
    private HistorialProcesoService historialProcesoService;
    private ValidacionModeloService validacionModeloService;
    private ModelMapper modelMapper;

    @Autowired
    public ProcesoService(ProcesoRepository procesoRepository,
                          UsuarioService usuarioService,
                          HistorialProcesoService historialProcesoService,
                          ValidacionModeloService validacionModeloService,
                          ModelMapper modelMapper) {
        this.procesoRepository = procesoRepository;
        this.usuarioService = usuarioService;
        this.historialProcesoService = historialProcesoService;
        this.validacionModeloService = validacionModeloService;
        this.modelMapper = modelMapper;
    }

    @Transactional
    public ProcesoRespuestaDto crear(CrearProcesoDto dto, String username) {
        Usuario usuario = usuarioAutenticado(username);
        Long empresaId = usuario.getEmpresa().getId();

        if (procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(empresaId, dto.getNombre().trim())) {
            throw new NombreProcesoDuplicadoException(NOMBRE_DUPLICADO);
        }

        Proceso proceso = modelMapper.map(dto, Proceso.class);
        proceso.setNombre(dto.getNombre().trim());
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setEmpresa(usuario.getEmpresa());
        proceso.setPool(poolConLaneInicial(usuario.getEmpresa().getNombre()));

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

   @Transactional(readOnly = true)
    public Page<ProcesoResumenDto> consultarProcesos(FiltroProcesosDto filtro, String username) {
        Usuario usuario = usuarioAutenticado(username);
        normalizar(filtro);
        Pageable paginacion = PageRequest.of(filtro.getPage(), TAMANO_PAGINA,
            Sort.by(Sort.Direction.ASC, "nombre"));
        return procesoRepository
            .findByEmpresaIdAndEliminadoFalse(usuario.getEmpresa().getId(), paginacion)
            .map(this::toResumen);
    }

    @Transactional(readOnly = true)
    public List<String> categoriasDisponibles(String username) {
        Usuario usuario = usuarioAutenticado(username);
        return procesoRepository.categoriasDeLaEmpresa(usuario.getEmpresa().getId());
    }

    public boolean puedeEditar(String username) {
        Usuario usuario = usuarioAutenticado(username);
        return tieneRolDeEscritura(usuario);
    }

    public boolean puedeEliminar(String username) {
        Usuario usuario = usuarioAutenticado(username);
        return esAdministrador(usuario);
    }

    @Transactional
    public ProcesoRespuestaDto editar(Long procesoId, EditarProcesoDto dto, String username) {
        Usuario usuario = usuarioAutenticado(username);
        validarRolDeEscritura(usuario);

        Proceso proceso = procesoActivoDeLaEmpresa(procesoId, usuario);
        String nombreNuevo = dto.getNombre().trim();
        String categoriaNueva = dto.getCategoria().trim();
        if (!proceso.getNombre().equalsIgnoreCase(nombreNuevo)
                && procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(usuario.getEmpresa().getId(), nombreNuevo)) {
            throw new NombreProcesoDuplicadoException(NOMBRE_DUPLICADO);
        }

        String cambios = construirCambios(proceso, dto, nombreNuevo, categoriaNueva);
        if (cambios.isEmpty()) {
            return toDto(proceso);
        }

        if (saleDeBorrador(proceso, dto)) {
            validacionModeloService.validarParaSalirDeBorrador(proceso);
        }

        String estadoAnterior = proceso.getEstado().name();
        proceso.setNombre(nombreNuevo);
        proceso.setDescripcion(dto.getDescripcion());
        proceso.setCategoria(categoriaNueva);
        proceso.setEstado(dto.getEstado());
        procesoRepository.save(proceso);

        registrarHistorial(proceso, usuario, cambios, estadoAnterior);
        return toDto(proceso);
    }

    @Transactional(readOnly = true)
    public ProcesoRespuestaDto obtenerParaEliminar(Long procesoId, String username) {
        Usuario usuario = usuarioAutenticado(username);
        validarRolAdministrador(usuario);
        return toDto(procesoActivoDeLaEmpresa(procesoId, usuario));
    }

    @Transactional
    public ProcesoRespuestaDto eliminar(Long procesoId, String username) {
        Usuario usuario = usuarioAutenticado(username);
        validarRolAdministrador(usuario);

        Proceso proceso = procesoActivoDeLaEmpresa(procesoId, usuario);
        String estadoAnterior = proceso.getEstado().name();
        proceso.setEliminado(true);
        procesoRepository.save(proceso);

        registrarHistorial(proceso, usuario, "proceso eliminado", estadoAnterior);
        return toDto(proceso);
    }

    @Transactional(readOnly = true)
    public List<HistorialProcesoRespuestaDto> consultarHistorial(Long procesoId, String username) {
        Usuario usuario = usuarioAutenticado(username);
        Proceso proceso = procesoDeLaEmpresa(procesoId, usuario);
        return historialProcesoService.consultarPorProcesoYEmpresa(proceso.getId(), usuario.getEmpresa().getId());
    }

    private boolean saleDeBorrador(Proceso proceso, EditarProcesoDto dto) {
        return proceso.getEstado() == EstadoProceso.BORRADOR && dto.getEstado() != EstadoProceso.BORRADOR;
    }

    private Pool poolConLaneInicial(String nombreEmpresa) {
        Pool pool = new Pool(null, nombreEmpresa, new ArrayList<>());
        pool.getLanes().add(new Lane(null, LANE_INICIAL, pool));
        return pool;
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
            throw new RecursoNoEncontradoException("El proceso ya fue eliminado");
        }
        return proceso;
    }

    private void registrarHistorial(Proceso proceso, Usuario usuario, String cambios, String estadoAnterior) {
        historialProcesoService.registrarHistorial(new HistorialProceso(null, proceso, usuario, LocalDateTime.now(), cambios,
                estadoAnterior));
    }

    private boolean tieneRolDeEscritura(Usuario usuario) {
        return usuario.getRol() == RolUsuario.ADMINISTRADOR || usuario.getRol() == RolUsuario.EDITOR;
    }

    private void validarRolDeEscritura(Usuario usuario) {
        if (!tieneRolDeEscritura(usuario)) {
            throw new UsuarioSinPermisoException("Solo un administrador o editor puede crear o modificar procesos");
        }
    }

    private boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() == RolUsuario.ADMINISTRADOR;
    }

    private void validarRolAdministrador(Usuario usuario) {
        if (!esAdministrador(usuario)) {
            throw new UsuarioSinPermisoException("Solo un administrador puede eliminar procesos");
        }
    }

    private String construirCambios(Proceso proceso, EditarProcesoDto dto, String nombreNuevo,
            String categoriaNueva) {
        List<String> cambios = new ArrayList<>();
        agregarCambio(cambios, "nombre", proceso.getNombre(), nombreNuevo);
        agregarCambio(cambios, "descripcion", proceso.getDescripcion(), dto.getDescripcion());
        agregarCambio(cambios, "categoria", proceso.getCategoria(), categoriaNueva);
        agregarCambio(cambios, "estado", proceso.getEstado().name(), dto.getEstado().name());
        return String.join("; ", cambios);
    }

    private void agregarCambio(List<String> cambios, String campo, String anterior, String nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            cambios.add(campo + ": '" + anterior + "' -> '" + nuevo + "'");
        }
    }

    private void normalizar(FiltroProcesosDto filtro) {
        filtro.setQ(textoONulo(filtro.getQ()));
        filtro.setCategoria(textoONulo(filtro.getCategoria()));
        if (filtro.getVisibilidad() == null) {
            filtro.setVisibilidad(VisibilidadProceso.ACTIVOS);
        }
        filtro.setPage(filtro.getPage() == null ? 0 : Math.max(filtro.getPage(), 0));
    }

    private String textoONulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private ProcesoResumenDto toResumen(Proceso proceso) {
        ProcesoResumenDto resumen = modelMapper.map(proceso, ProcesoResumenDto.class);
        resumen.setDescripcion(resumir(proceso.getDescripcion()));
        return resumen;
    }

    private String resumir(String descripcion) {
        if (descripcion == null || descripcion.length() <= LONGITUD_RESUMEN) {
            return descripcion;
        }
        return descripcion.substring(0, LONGITUD_RESUMEN) + "...";
    }

    private ProcesoRespuestaDto toDto(Proceso proceso) {
        ProcesoRespuestaDto response = modelMapper.map(proceso, ProcesoRespuestaDto.class);
        response.setPoolId(proceso.getPool().getId());
        return response;
    }
}

