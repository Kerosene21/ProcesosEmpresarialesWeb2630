package co.edu.javeriana.procesosempresariales.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.AccionRolProceso;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.HistorialRolProceso;
import co.edu.javeriana.procesosempresariales.domain.RolProceso;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CrearRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroRolesProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialRolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoUsoRolDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoResumenDto;
import co.edu.javeriana.procesosempresariales.dto.UsoRolProceso;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadRolProceso;
import co.edu.javeriana.procesosempresariales.exception.NombreRolProcesoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.RolProcesoEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.HistorialRolProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.RolProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.RolProcesoSpecifications;

@Service
public class RolProcesoService {

    static final String NOMBRE_DUPLICADO = "Ya existe un rol de proceso con ese nombre en la empresa";
    static final String ROL_NO_EXISTE = "El rol de proceso no existe";
    static final String ROL_AJENO = "El rol de proceso no pertenece a la empresa del usuario";
    static final String ROL_ELIMINADO = "El rol de proceso ya fue eliminado";
    static final String SIN_PERMISO_CREAR = "Solo un administrador puede crear roles de proceso";
    static final String SIN_PERMISO_EDITAR = "Solo un administrador o editor puede modificar roles de proceso";
    static final String SIN_PERMISO_ELIMINAR = "Solo un administrador puede eliminar roles de proceso";
    static final String EN_USO = " no se puede eliminar porque lo usan lanes de los procesos: ";
    private static final int TAMANO_PAGINA = 10;

    private RolProcesoRepository rolProcesoRepository;
    private HistorialRolProcesoRepository historialRolProcesoRepository;
    private RolProcesoSpecifications rolProcesoSpecifications;
    private AccesoProcesoService accesoProcesoService;

    @Autowired
    public RolProcesoService(RolProcesoRepository rolProcesoRepository,
            HistorialRolProcesoRepository historialRolProcesoRepository,
            RolProcesoSpecifications rolProcesoSpecifications, AccesoProcesoService accesoProcesoService) {
        this.rolProcesoRepository = rolProcesoRepository;
        this.historialRolProcesoRepository = historialRolProcesoRepository;
        this.rolProcesoSpecifications = rolProcesoSpecifications;
        this.accesoProcesoService = accesoProcesoService;
    }

    @Transactional
    public RolProcesoRespuestaDto crear(CrearRolProcesoDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_CREAR);
        Empresa empresa = usuario.getEmpresa();
        String nombre = dto.getNombre().trim();

        if (rolProcesoRepository.existsByEmpresaIdAndNombreIgnoreCase(empresa.getId(), nombre)) {
            throw new NombreRolProcesoDuplicadoException(NOMBRE_DUPLICADO);
        }

        RolProceso guardado = guardar(new RolProceso(null, nombre, dto.getDescripcion().trim(), empresa, true));
        registrarHistorial(guardado, usuario, AccionRolProceso.CREACION, "rol de proceso creado: '" + nombre + "'");
        return toDto(guardado);
    }

    @Transactional(readOnly = true)
    public Page<RolProcesoResumenDto> consultar(FiltroRolesProcesoDto filtro, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        normalizar(filtro);
        Pageable paginacion = PageRequest.of(filtro.getPage(), TAMANO_PAGINA, Sort.by(Sort.Direction.ASC, "nombre"));
        Page<RolProceso> roles = rolProcesoRepository.findAll(
                rolProcesoSpecifications.deLaEmpresaCon(usuario.getEmpresa().getId(), filtro), paginacion);
        Map<Long, List<UsoRolProceso>> usos = usosPorRol(roles.getContent());
        boolean administrador = accesoProcesoService.esAdministrador(usuario);
        return roles.map(rol -> toResumen(rol, usos.getOrDefault(rol.getId(), List.of()), administrador));
    }

    @Transactional(readOnly = true)
    public RolProcesoResumenDto obtener(Long rolProcesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        RolProceso rol = rolDeLaEmpresa(rolProcesoId, usuario);
        return toResumen(rol, usosDe(rol), accesoProcesoService.esAdministrador(usuario));
    }

    @Transactional
    public RolProcesoRespuestaDto editar(Long rolProcesoId, EditarRolProcesoDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_EDITAR);
        RolProceso rol = rolActivoDeLaEmpresa(rolProcesoId, usuario);

        String nombreNuevo = dto.getNombre().trim();
        String descripcionNueva = dto.getDescripcion().trim();
        if (!rol.getNombre().equalsIgnoreCase(nombreNuevo) && rolProcesoRepository
                .existsByEmpresaIdAndNombreIgnoreCase(usuario.getEmpresa().getId(), nombreNuevo)) {
            throw new NombreRolProcesoDuplicadoException(NOMBRE_DUPLICADO);
        }

        String cambios = construirCambios(rol, nombreNuevo, descripcionNueva);
        if (cambios.isEmpty()) {
            return toDto(rol);
        }

        rol.setNombre(nombreNuevo);
        rol.setDescripcion(descripcionNueva);
        guardar(rol);
        registrarHistorial(rol, usuario, AccionRolProceso.EDICION, cambios);
        return toDto(rol);
    }

    @Transactional(readOnly = true)
    public RolProcesoResumenDto obtenerParaEliminar(Long rolProcesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        RolProceso rol = rolActivoDeLaEmpresa(rolProcesoId, usuario);
        return toResumen(rol, usosDe(rol), true);
    }

    @Transactional
    public RolProcesoRespuestaDto eliminar(Long rolProcesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        RolProceso rol = rolActivoDeLaEmpresa(rolProcesoId, usuario);

        List<UsoRolProceso> usos = usosDe(rol);
        if (!usos.isEmpty()) {
            List<String> procesos = procesosDe(usos).stream().map(ProcesoUsoRolDto::getNombre).toList();
            throw new RolProcesoEnUsoException("El rol de proceso '" + rol.getNombre() + "'" + EN_USO
                    + String.join(", ", procesos), procesos);
        }

        rol.setActivo(false);
        rolProcesoRepository.save(rol);
        registrarHistorial(rol, usuario, AccionRolProceso.ELIMINACION,
                "rol de proceso eliminado: '" + rol.getNombre() + "'");
        return toDto(rol);
    }

    @Transactional(readOnly = true)
    public List<HistorialRolProcesoRespuestaDto> consultarHistorial(Long rolProcesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        RolProceso rol = rolDeLaEmpresa(rolProcesoId, usuario);
        return historialRolProcesoRepository
                .findByRolProcesoIdAndRolProcesoEmpresaIdOrderByFechaDescIdDesc(rol.getId(),
                        usuario.getEmpresa().getId())
                .stream()
                .map(this::toHistorialDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RolProcesoRespuestaDto> activosDeLaEmpresa(Long empresaId) {
        return rolProcesoRepository.findByEmpresaIdAndActivoTrueOrderByNombreAsc(empresaId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public RolProceso rolActivoDeLaEmpresa(Long rolProcesoId, Usuario usuario) {
        RolProceso rol = rolDeLaEmpresa(rolProcesoId, usuario);
        if (!rol.isActivo()) {
            throw new RecursoNoEncontradoException(ROL_ELIMINADO);
        }
        return rol;
    }

    private RolProceso rolDeLaEmpresa(Long rolProcesoId, Usuario usuario) {
        RolProceso rol = rolProcesoRepository.findById(rolProcesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ROL_NO_EXISTE));
        if (!rol.getEmpresa().getId().equals(usuario.getEmpresa().getId())) {
            throw new UsuarioSinPermisoException(ROL_AJENO);
        }
        return rol;
    }

    private RolProceso guardar(RolProceso rol) {
        try {
            return rolProcesoRepository.saveAndFlush(rol);
        } catch (DataIntegrityViolationException exception) {
            throw new NombreRolProcesoDuplicadoException(NOMBRE_DUPLICADO);
        }
    }

    private List<UsoRolProceso> usosDe(RolProceso rol) {
        return rolProcesoRepository.usosEnProcesosActivos(List.of(rol.getId()));
    }

    private Map<Long, List<UsoRolProceso>> usosPorRol(List<RolProceso> roles) {
        if (roles.isEmpty()) {
            return Map.of();
        }
        List<Long> identificadores = roles.stream().map(RolProceso::getId).toList();
        return rolProcesoRepository.usosEnProcesosActivos(identificadores).stream()
                .collect(Collectors.groupingBy(UsoRolProceso::rolProcesoId, LinkedHashMap::new,
                        Collectors.toList()));
    }

    private List<ProcesoUsoRolDto> procesosDe(List<UsoRolProceso> usos) {
        Map<Long, ProcesoUsoRolDto> procesos = new LinkedHashMap<>();
        for (UsoRolProceso uso : usos) {
            ProcesoUsoRolDto proceso = procesos.computeIfAbsent(uso.procesoId(),
                    procesoId -> new ProcesoUsoRolDto(procesoId, uso.procesoNombre(), 0, 0));
            proceso.setLanes(proceso.getLanes() + 1);
            proceso.setActividadesActivas(proceso.getActividadesActivas() + uso.actividadesActivas());
        }
        return new ArrayList<>(procesos.values());
    }

    private String construirCambios(RolProceso rol, String nombreNuevo, String descripcionNueva) {
        List<String> cambios = new ArrayList<>();
        agregarCambio(cambios, "nombre", rol.getNombre(), nombreNuevo);
        agregarCambio(cambios, "descripcion", rol.getDescripcion(), descripcionNueva);
        return String.join("; ", cambios);
    }

    private void agregarCambio(List<String> cambios, String campo, String anterior, String nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            cambios.add(campo + ": '" + anterior + "' -> '" + nuevo + "'");
        }
    }

    private void registrarHistorial(RolProceso rol, Usuario usuario, AccionRolProceso accion, String cambios) {
        historialRolProcesoRepository.save(new HistorialRolProceso(null, rol, usuario,
                LocalDateTime.now(ZoneId.systemDefault()), accion, cambios));
    }

    private void normalizar(FiltroRolesProcesoDto filtro) {
        filtro.setQ(textoONulo(filtro.getQ()));
        if (filtro.getVisibilidad() == null) {
            filtro.setVisibilidad(VisibilidadRolProceso.ACTIVOS);
        }
        filtro.setPage(filtro.getPage() == null ? 0 : Math.max(filtro.getPage(), 0));
    }

    private String textoONulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private RolProcesoRespuestaDto toDto(RolProceso rol) {
        RolProcesoRespuestaDto respuesta = new RolProcesoRespuestaDto();
        respuesta.setId(rol.getId());
        respuesta.setNombre(rol.getNombre());
        respuesta.setDescripcion(rol.getDescripcion());
        respuesta.setActivo(rol.isActivo());
        return respuesta;
    }

    private RolProcesoResumenDto toResumen(RolProceso rol, List<UsoRolProceso> usos, boolean administrador) {
        RolProcesoResumenDto resumen = new RolProcesoResumenDto();
        resumen.setId(rol.getId());
        resumen.setNombre(rol.getNombre());
        resumen.setDescripcion(rol.getDescripcion());
        resumen.setActivo(rol.isActivo());
        resumen.setEnUso(!usos.isEmpty());
        resumen.setProcesos(procesosDe(usos));
        resumen.setPuedeEliminar(administrador && rol.isActivo() && usos.isEmpty());
        return resumen;
    }

    private HistorialRolProcesoRespuestaDto toHistorialDto(HistorialRolProceso historial) {
        HistorialRolProcesoRespuestaDto respuesta = new HistorialRolProcesoRespuestaDto();
        respuesta.setFecha(historial.getFecha());
        respuesta.setUsuarioCorreo(historial.getUsuario().getUsername());
        respuesta.setAccion(historial.getAccion());
        respuesta.setCambiosRealizados(historial.getCambiosRealizados());
        return respuesta;
    }
}
