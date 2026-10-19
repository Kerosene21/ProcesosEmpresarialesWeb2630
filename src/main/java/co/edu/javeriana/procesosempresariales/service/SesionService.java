package co.edu.javeriana.procesosempresariales.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.SesionDto;

@Service
public class SesionService {

    private UsuarioService usuarioService;

    @Autowired
    public SesionService(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Transactional(readOnly = true)
    public SesionDto consultar(String username) {
        Usuario usuario = usuarioService.usuarioAutenticado(username);
        SesionDto sesion = new SesionDto();
        sesion.setAutenticado(true);
        sesion.setCorreo(usuario.getUsername());
        sesion.setRol(usuario.getRol());
        sesion.setEmpresaId(usuario.getEmpresa().getId());
        sesion.setEmpresaNombre(usuario.getEmpresa().getNombre());
        return sesion;
    }
}
