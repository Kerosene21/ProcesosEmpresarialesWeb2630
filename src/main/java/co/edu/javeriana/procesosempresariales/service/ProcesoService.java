package co.edu.javeriana.procesosempresariales.service;

import java.time.LocalDateTime;

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
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.NombreProcesoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@Service
public class ProcesoService {
    
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

    // La creación y la creación del pool deben confirmarse juntas o fallar juntas.
    @Transactional
    public ProcesoRespuestaDto crear(CrearProcesoDto dto, String username) {
        // La empresa sale del usuario el cliente no puede escoger otra porque solo puede trabajar en la suya obviamenete xd
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario autenticado no existe"));
        // revisar que la consulta de duplicados se haga dentro de la empresa correcta
        Long empresaId = usuario.getEmpresa().getId();

        // manda error si el nombre ya está ocupado.
        if (procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(empresaId, dto.getNombre().trim())) {
            throw new NombreProcesoDuplicadoException("Ya existe un proceso con ese nombre en la empresa");
        }

        // el dto solo trae los datos permitidos por el usuario
        Proceso proceso = modelMapper.map(dto, Proceso.class);
        // quitamos espacios al inicio y al final para que no se creen nombres iguales pero con espacios
        proceso.setNombre(dto.getNombre().trim());
        // proceso nuevo comienza como borrador
        proceso.setEstado(EstadoProceso.BORRADOR);
        // la empresa se toma del usuario autenticado 
        proceso.setEmpresa(usuario.getEmpresa());
        // El pool inicial deja preparada la estructura mínima del diagrama
        proceso.setPool(new Pool(null, usuario.getEmpresa().getNombre()));
        try {
            // CascadeType.ALL guarda el pool asociado al proceso
            return toDto(procesoRepository.save(proceso));
        } catch (DataIntegrityViolationException exception) {
            //manda error si el nombre ya está ocupado aunque la base de datos se encarga de evitarlo se supone xd
            throw new NombreProcesoDuplicadoException("Ya existe un proceso con ese nombre en la empresa");
        }
    }

    // convierte la entidad en un dto para no exponer relaciones JPA en las respuestas por seguridad y para que el cliente reciba solo lo que necesita
    private ProcesoRespuestaDto toDto(Proceso proceso) {
        ProcesoRespuestaDto response = modelMapper.map(proceso, ProcesoRespuestaDto.class);
        // ModelMapper no necesita recorrer el pool completo; solo devolvemos su identificador.
        response.setPoolId(proceso.getPool().getId());
        return response;
    }

        @Transactional(readOnly = true)
    public ProcesoRespuestaDto obtener(Long procesoId, String username) {
        // identificar usuario para conocer su empresa
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario autenticado no existe"));
        // el proceso se busca por id y se informa si no existe o no se encuentra
        Proceso proceso = procesoRepository.findById(procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El proceso no existe"));
        // evitar que un usuario consulte procesos de otra empresa
        if (!proceso.getEmpresa().getId().equals(usuario.getEmpresa().getId())) {
            throw new UsuarioSinPermisoException("El proceso no pertenece a la empresa del usuario");
        }
        return toDto(proceso);
    }

    public boolean puedeEditar(String username) {
        // el rol se consulta en el servidor
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario autenticado no existe"));
        // solo rol administrador o editor puede modificar procesos
        return usuario.getRol() == RolUsuario.ADMINISTRADOR || usuario.getRol() == RolUsuario.EDITOR;
    }

        // actualiza los datos básicos y guarda su historial dentro de la misma transaccion
    @Transactional
    public ProcesoRespuestaDto editar(Long procesoId, EditarProcesoDto dto, String username) {
        // permisos y empresa del usuario autenticado se toman de la sesion
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario autenticado no existe"));
        // edición no esta permitida a usuarios con rol de solo lectura
        validarRolEditor(usuario);

        // se carga la entidad existente para modificarla sin crear un proceso nuevo
        Proceso proceso = procesoRepository.findById(procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El proceso no existe"));
        // qunque alguien conozca el id solo puede modificar procesos de su empresa
        if (!proceso.getEmpresa().getId().equals(usuario.getEmpresa().getId())) {
            throw new UsuarioSinPermisoException("El proceso no pertenece a la empresa del usuario");
        }
        // si el nombre cambió debe seguir siendo único dentro de la empresa
        if (!proceso.getNombre().equalsIgnoreCase(dto.getNombre().trim())
                && procesoRepository.existsByEmpresaIdAndNombreIgnoreCase(usuario.getEmpresa().getId(), dto.getNombre().trim())) {
            throw new NombreProcesoDuplicadoException("Ya existe un proceso con ese nombre en la empresa");
        }

        // guardamos el valor anterior antes de sobrescribirlo en el historial 
        String estadoAnterior = proceso.getEstado().name();
        // resumen permite saber qué información se modificó en esta operación
        String cambios = construirCambios(proceso, dto);
        // solo se actualizan los campos básicos solicitados 
        proceso.setNombre(dto.getNombre().trim());
        proceso.setDescripcion(dto.getDescripcion());
        proceso.setCategoria(dto.getCategoria());
        proceso.setEstado(dto.getEstado());
        // no se toca el pool ni la empresa porque no se pueden cambiar desde la edición básica
        procesoRepository.save(proceso);

        // el historial queda ligado al proceso y al usuario que hizo el cambio
        HistorialProceso historial = new HistorialProceso(null, proceso, usuario, LocalDateTime.now(), cambios,
                estadoAnterior);
        // proceso e historial se guardan juntos por la transacción
        historialProcesoRepository.save(historial);
        return toDto(proceso);
    }

    // centraliza la regla de permisos para que no dependa de algo mas que solo del rol del usuario
    private void validarRolEditor(Usuario usuario) {
        if (usuario.getRol() != RolUsuario.ADMINISTRADOR && usuario.getRol() != RolUsuario.EDITOR) {
            throw new UsuarioSinPermisoException("Solo un administrador o editor puede modificar procesos");
        }
    }

    private String construirCambios(Proceso proceso, EditarProcesoDto dto) {
        // Se conserva un resumen y los valores estructurados siguen en las tablas principales
        return "nombre: '" + proceso.getNombre() + "' -> '" + dto.getNombre().trim()
                + "'; descripcion actualizada; categoria: '" + proceso.getCategoria() + "' -> '"
                + dto.getCategoria() + "'; estado: '" + proceso.getEstado() + "' -> '" + dto.getEstado() + "'";
    }
}