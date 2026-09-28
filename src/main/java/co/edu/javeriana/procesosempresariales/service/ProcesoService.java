package co.edu.javeriana.procesosempresariales.service;

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
import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.AlcanceProceso;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroProcesosDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoResumenDto;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadProceso;
import co.edu.javeriana.procesosempresariales.exception.NombreProcesoDuplicadoException;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoSpecifications;

@Service
public class ProcesoService {

    private static final String NOMBRE_DUPLICADO = "Ya existe un proceso con ese nombre en la empresa";
    private static final String LANE_INICIAL = "General";
    private static final String SIN_PERMISO_ESCRITURA =
            "Solo un administrador o editor puede crear o modificar procesos";
    private static final String SIN_PERMISO_ELIMINAR = "Solo un administrador puede eliminar procesos";
    private static final int TAMANO_PAGINA = 10;
    private static final int LONGITUD_RESUMEN = 120;

    private ProcesoRepository procesoRepository;
    private ProcesoSpecifications procesoSpecifications;
    private AccesoProcesoService accesoProcesoService;
    private HistorialProcesoService historialProcesoService;
    private ValidacionModeloService validacionModeloService;
    private ModelMapper modelMapper;

    @Autowired
    public ProcesoService(ProcesoRepository procesoRepository, ProcesoSpecifications procesoSpecifications,
            AccesoProcesoService accesoProcesoService, HistorialProcesoService historialProcesoService,
            ValidacionModeloService validacionModeloService, ModelMapper modelMapper) {
        this.procesoRepository = procesoRepository;
        this.procesoSpecifications = procesoSpecifications;
        this.accesoProcesoService = accesoProcesoService;
        this.historialProcesoService = historialProcesoService;
        this.validacionModeloService = validacionModeloService;
        this.modelMapper = modelMapper;
    }

    @Transactional
    public ProcesoRespuestaDto crear(CrearProcesoDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Long empresaId = usuario.getEmpresa().getId();
        String nombre = dto.getNombre().trim();
        String categoria = dto.getCategoria().trim();

        if (procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(empresaId, nombre)) {
            throw new NombreProcesoDuplicadoException(NOMBRE_DUPLICADO);
        }

        Proceso proceso = modelMapper.map(dto, Proceso.class);
        proceso.setNombre(nombre);
        proceso.setCategoria(categoria);
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setEmpresa(usuario.getEmpresa());
        proceso.agregarPool(poolPropietarioConLaneInicial(usuario.getEmpresa().getNombre()));
        try {
            return toDto(procesoRepository.save(proceso));
        } catch (DataIntegrityViolationException exception) {
            throw new NombreProcesoDuplicadoException(NOMBRE_DUPLICADO);
        }
    }

    @Transactional(readOnly = true)
    public ProcesoRespuestaDto obtener(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        return toDto(accesoProcesoService.procesoDeLaEmpresa(procesoId, usuario));
    }

    @Transactional(readOnly = true)
    public ProcesoRespuestaDto obtenerVisible(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoVisiblePara(procesoId, usuario);
        return toDto(proceso, !accesoProcesoService.esPropietario(proceso, usuario));
    }

    @Transactional(readOnly = true)
    public Page<ProcesoResumenDto> consultarProcesos(FiltroProcesosDto filtro, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        normalizar(filtro);
        Pageable paginacion = PageRequest.of(filtro.getPage(), TAMANO_PAGINA,
                Sort.by(Sort.Direction.ASC, "nombre"));
        return procesoRepository
                .findAll(procesoSpecifications.deLaEmpresaCon(usuario.getEmpresa().getId(), filtro), paginacion)
                .map(proceso -> toResumen(proceso, usuario));
    }

    @Transactional(readOnly = true)
    public List<String> categoriasDisponibles(String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        return procesoRepository.categoriasDeLaEmpresa(usuario.getEmpresa().getId());
    }

    public boolean puedeEditar(String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        return accesoProcesoService.tieneRolDeEscritura(usuario);
    }

    public boolean puedeEliminar(String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        return accesoProcesoService.esAdministrador(usuario);
    }

    @Transactional
    public ProcesoRespuestaDto editar(Long procesoId, EditarProcesoDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);

        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
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

        historialProcesoService.registrar(proceso, usuario, cambios, estadoAnterior);
        return toDto(proceso);
    }

    @Transactional(readOnly = true)
    public ProcesoRespuestaDto obtenerParaEliminar(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        return toDto(accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario));
    }

    @Transactional
    public ProcesoRespuestaDto eliminar(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);

        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        String estadoAnterior = proceso.getEstado().name();
        proceso.setEliminado(true);
        procesoRepository.save(proceso);

        historialProcesoService.registrar(proceso, usuario, "proceso eliminado", estadoAnterior);
        return toDto(proceso);
    }

    @Transactional(readOnly = true)
    public List<HistorialProcesoRespuestaDto> consultarHistorial(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoDeLaEmpresa(procesoId, usuario);
        return historialProcesoService.consultarDelProceso(proceso.getId(), usuario.getEmpresa().getId());
    }

    private boolean saleDeBorrador(Proceso proceso, EditarProcesoDto dto) {
        return proceso.getEstado() == EstadoProceso.BORRADOR && dto.getEstado() != EstadoProceso.BORRADOR;
    }

    private Pool poolPropietarioConLaneInicial(String nombreEmpresa) {
        Pool pool = new Pool(null, null, nombreEmpresa, TipoPool.PROPIETARIO, 1, false, true, null,
                new ArrayList<>());
        pool.agregarLane(new Lane(null, LANE_INICIAL, null, null, 1, true));
        return pool;
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
        if (filtro.getAlcance() == null) {
            filtro.setAlcance(AlcanceProceso.PROPIOS);
        }
        filtro.setPage(filtro.getPage() == null ? 0 : Math.max(filtro.getPage(), 0));
    }

    private String textoONulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private ProcesoResumenDto toResumen(Proceso proceso, Usuario usuario) {
        ProcesoResumenDto resumen = modelMapper.map(proceso, ProcesoResumenDto.class);
        resumen.setDescripcion(resumir(proceso.getDescripcion()));
        resumen.setEmpresaPropietariaId(proceso.getEmpresa().getId());
        resumen.setEmpresaPropietariaNombre(proceso.getEmpresa().getNombre());
        resumen.setSoloLectura(!accesoProcesoService.esPropietario(proceso, usuario));
        return resumen;
    }

    private String resumir(String descripcion) {
        if (descripcion == null || descripcion.length() <= LONGITUD_RESUMEN) {
            return descripcion;
        }
        return descripcion.substring(0, LONGITUD_RESUMEN) + "...";
    }

    private ProcesoRespuestaDto toDto(Proceso proceso) {
        return toDto(proceso, false);
    }

    private ProcesoRespuestaDto toDto(Proceso proceso, boolean soloLectura) {
        ProcesoRespuestaDto response = modelMapper.map(proceso, ProcesoRespuestaDto.class);
        Pool propietario = proceso.poolPropietario();
        response.setPoolId(propietario.getId());
        response.setPoolNombre(propietario.getNombre());
        response.setEmpresaPropietariaId(proceso.getEmpresa().getId());
        response.setEmpresaPropietariaNombre(proceso.getEmpresa().getNombre());
        response.setSoloLectura(soloLectura);
        return response;
    }
}
