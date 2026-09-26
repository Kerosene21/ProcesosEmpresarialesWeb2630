package co.edu.javeriana.procesosempresariales.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;

@Service
public class AccesoProcesoService {

    private static final String PROCESO_NO_EXISTE = "El proceso no existe";
    private static final String PROCESO_AJENO = "El proceso no pertenece a la empresa del usuario";
    private static final String PROCESO_ELIMINADO = "El proceso ya fue eliminado";

    private UsuarioService usuarioService;
    private ProcesoRepository procesoRepository;

    @Autowired
    public AccesoProcesoService(UsuarioService usuarioService, ProcesoRepository procesoRepository) {
        this.usuarioService = usuarioService;
        this.procesoRepository = procesoRepository;
    }

    @Transactional(readOnly = true)
    public Usuario usuarioAutenticado(String username) {
        return usuarioService.usuarioAutenticado(username);
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

    public boolean esAdministrador(Usuario usuario) {
        return usuario.getRol() == RolUsuario.ADMINISTRADOR;
    }

    public void validarRolDeEscritura(Usuario usuario, String mensaje) {
        if (!tieneRolDeEscritura(usuario)) {
            throw new UsuarioSinPermisoException(mensaje);
        }
    }

    public void validarRolAdministrador(Usuario usuario, String mensaje) {
        if (!esAdministrador(usuario)) {
            throw new UsuarioSinPermisoException(mensaje);
        }
    }
}
