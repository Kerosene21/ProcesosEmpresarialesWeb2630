package co.edu.javeriana.procesosempresariales.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@Service
public class AccesoProcesoService {

    private static final String USUARIO_NO_EXISTE = "El usuario autenticado no existe";
    private static final String PROCESO_NO_EXISTE = "El proceso no existe";
    private static final String PROCESO_AJENO = "El proceso no pertenece a la empresa del usuario";
    private static final String PROCESO_ELIMINADO = "El proceso ya fue eliminado";

    private final UsuarioRepository usuarioRepository;
    private final ProcesoRepository procesoRepository;
    private final HistorialProcesoRepository historialProcesoRepository;

    public AccesoProcesoService(UsuarioRepository usuarioRepository, ProcesoRepository procesoRepository,
            HistorialProcesoRepository historialProcesoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.procesoRepository = procesoRepository;
        this.historialProcesoRepository = historialProcesoRepository;
    }

    @Transactional(readOnly = true)
    public Usuario usuarioAutenticado(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException(USUARIO_NO_EXISTE));
    }

    @Transactional(readOnly = true)
    public Proceso procesoDeLaEmpresa(Long procesoId, Usuario usuario) {
        Proceso proceso = procesoRepository.findById(procesoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(PROCESO_NO_EXISTE));
        if (!proceso.getEmpresa().getId().equals(usuario.getEmpresa().getId())) {
            throw new UsuarioSinPermisoException(PROCESO_AJENO);
        }
        return proceso;
    }

    @Transactional(readOnly = true)
    public Proceso procesoActivoDeLaEmpresa(Long procesoId, Usuario usuario) {
        Proceso proceso = procesoDeLaEmpresa(procesoId, usuario);
        if (proceso.isEliminado()) {
            throw new RecursoNoEncontradoException(PROCESO_ELIMINADO);
        }
        return proceso;
    }

    public boolean tieneRolDeEscritura(Usuario usuario) {
        return usuario.getRol() == RolUsuario.ADMINISTRADOR || usuario.getRol() == RolUsuario.EDITOR;
    }

    public void validarRolDeEscritura(Usuario usuario, String mensaje) {
        if (!tieneRolDeEscritura(usuario)) {
            throw new UsuarioSinPermisoException(mensaje);
        }
    }

    public void validarRolAdministrador(Usuario usuario, String mensaje) {
        if (usuario.getRol() != RolUsuario.ADMINISTRADOR) {
            throw new UsuarioSinPermisoException(mensaje);
        }
    }

    public void registrarHistorial(Proceso proceso, Usuario usuario, String cambios) {
        historialProcesoRepository.save(new HistorialProceso(null, proceso, usuario, LocalDateTime.now(), cambios,
                proceso.getEstado().name()));
    }
}
