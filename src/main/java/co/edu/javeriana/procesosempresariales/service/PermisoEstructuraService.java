package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.OperacionEstructura;
import co.edu.javeriana.procesosempresariales.domain.PermisoEstructuraProceso;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.ConfigurarPermisoEstructuraDto;
import co.edu.javeriana.procesosempresariales.dto.PermisoEstructuraDto;
import co.edu.javeriana.procesosempresariales.exception.PermisoEstructuraNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.PermisoEstructuraProcesoRepository;

@Service
public class PermisoEstructuraService {

    static final String SIN_PERMISO_CONFIGURAR =
            "Solo un administrador de la empresa propietaria puede configurar los permisos de estructura";
    static final String ADMINISTRADOR_FIJO = "El administrador conserva siempre todos los permisos de estructura";
    static final String LECTURA_FIJA = "El rol SOLO_LECTURA nunca puede modificar la estructura del proceso";

    private PermisoEstructuraProcesoRepository permisoEstructuraProcesoRepository;
    private AccesoProcesoService accesoProcesoService;
    private HistorialProcesoService historialProcesoService;

    @Autowired
    public PermisoEstructuraService(PermisoEstructuraProcesoRepository permisoEstructuraProcesoRepository,
            AccesoProcesoService accesoProcesoService, HistorialProcesoService historialProcesoService) {
        this.permisoEstructuraProcesoRepository = permisoEstructuraProcesoRepository;
        this.accesoProcesoService = accesoProcesoService;
        this.historialProcesoService = historialProcesoService;
    }

    @Transactional(readOnly = true)
    public boolean permite(Proceso proceso, Usuario usuario, OperacionEstructura operacion) {
        return switch (usuario.getRol()) {
            case ADMINISTRADOR -> true;
            case SOLO_LECTURA -> false;
            case EDITOR -> permisoDelEditor(proceso).permite(operacion);
        };
    }

    @Transactional(readOnly = true)
    public void exigir(Proceso proceso, Usuario usuario, OperacionEstructura operacion) {
        if (!permite(proceso, usuario, operacion)) {
            throw new UsuarioSinPermisoException("El rol " + usuario.getRol() + " no tiene permiso para "
                    + operacion.getDescripcion() + " en este proceso");
        }
    }

    @Transactional(readOnly = true)
    public List<PermisoEstructuraDto> consultar(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        return matriz(permisoDelEditor(accesoProcesoService.procesoDeLaEmpresa(procesoId, usuario)));
    }

    @Transactional
    public List<PermisoEstructuraDto> configurar(Long procesoId, RolUsuario rol, ConfigurarPermisoEstructuraDto dto,
            String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_CONFIGURAR);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        PermisoEstructuraProceso editor = switch (rol) {
            case ADMINISTRADOR -> {
                exigirValorFijo(dto, true, ADMINISTRADOR_FIJO);
                yield permisoDelEditor(proceso);
            }
            case SOLO_LECTURA -> {
                exigirValorFijo(dto, false, LECTURA_FIJA);
                yield permisoDelEditor(proceso);
            }
            case EDITOR -> configurarEditor(proceso, usuario, dto);
        };
        return matriz(editor);
    }

    private PermisoEstructuraProceso configurarEditor(Proceso proceso, Usuario usuario,
            ConfigurarPermisoEstructuraDto dto) {
        PermisoEstructuraProceso permiso = permisoDelEditor(proceso);
        List<String> cambios = new ArrayList<>();
        for (OperacionEstructura operacion : OperacionEstructura.values()) {
            boolean actual = permiso.permite(operacion);
            boolean solicitado = solicitado(dto, operacion);
            if (actual != solicitado) {
                cambios.add(operacion.getDescripcion() + ": " + actual + " -> " + solicitado);
            }
        }
        if (cambios.isEmpty()) {
            return permiso;
        }
        permiso.setCrearPool(dto.getCrearPool());
        permiso.setEditarPool(dto.getEditarPool());
        permiso.setEliminarPool(dto.getEliminarPool());
        permiso.setCrearLane(dto.getCrearLane());
        permiso.setEditarLane(dto.getEditarLane());
        permiso.setEliminarLane(dto.getEliminarLane());
        permisoEstructuraProcesoRepository.save(permiso);
        historialProcesoService.registrar(proceso, usuario,
                "permisos de estructura del rol EDITOR: " + String.join("; ", cambios));
        return permiso;
    }

    private void exigirValorFijo(ConfigurarPermisoEstructuraDto dto, boolean valor, String mensaje) {
        for (OperacionEstructura operacion : OperacionEstructura.values()) {
            if (solicitado(dto, operacion) != valor) {
                throw new PermisoEstructuraNoValidoException(mensaje);
            }
        }
    }

    private boolean solicitado(ConfigurarPermisoEstructuraDto dto, OperacionEstructura operacion) {
        return switch (operacion) {
            case CREAR_POOL -> dto.getCrearPool();
            case EDITAR_POOL -> dto.getEditarPool();
            case ELIMINAR_POOL -> dto.getEliminarPool();
            case CREAR_LANE -> dto.getCrearLane();
            case EDITAR_LANE -> dto.getEditarLane();
            case ELIMINAR_LANE -> dto.getEliminarLane();
        };
    }

    private PermisoEstructuraProceso permisoDelEditor(Proceso proceso) {
        return permisoEstructuraProcesoRepository.findByProcesoIdAndRol(proceso.getId(), RolUsuario.EDITOR)
                .orElseGet(() -> new PermisoEstructuraProceso(null, proceso, RolUsuario.EDITOR, true, true, false,
                        true, true, false));
    }

    private List<PermisoEstructuraDto> matriz(PermisoEstructuraProceso editor) {
        return List.of(
                new PermisoEstructuraDto(RolUsuario.ADMINISTRADOR, false, true, true, true, true, true, true),
                new PermisoEstructuraDto(RolUsuario.EDITOR, true, editor.isCrearPool(), editor.isEditarPool(),
                        editor.isEliminarPool(), editor.isCrearLane(), editor.isEditarLane(),
                        editor.isEliminarLane()),
                new PermisoEstructuraDto(RolUsuario.SOLO_LECTURA, false, false, false, false, false, false, false));
    }
}
