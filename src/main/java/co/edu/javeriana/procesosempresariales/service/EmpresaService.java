package co.edu.javeriana.procesosempresariales.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.EmpresaRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.NitEmpresaDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.EmpresaRepository;

import org.springframework.beans.factory.annotation.Autowired;

@Service
public class EmpresaService {

    private EmpresaRepository empresaRepository;
    private UsuarioService usuarioService;
    private ModelMapper modelMapper;
    

    @Autowired
    public EmpresaService(EmpresaRepository empresaRepository, UsuarioService usuarioService,
            ModelMapper modelMapper) {
        this.empresaRepository = empresaRepository;
        this.usuarioService = usuarioService;
        this.modelMapper = modelMapper;
        
    }

    @Transactional
    public EmpresaRespuestaDto registrar(RegistroEmpresaDto dto) {
        String nit = normalizar(dto.getNit());
        String correoContacto = normalizarCorreo(dto.getCorreoContacto());
        String credencialInicial = exigirCredencial(dto.getPasswordAdministrador());

        if (empresaRepository.existsByNit(nit)) {
            throw new NitEmpresaDuplicadoException("Ya existe una empresa registrada con el NIT " + nit);
        }
        if (usuarioService.existsByUsername(correoContacto)) {
            throw new CorreoAdministradorEnUsoException(
                    "El correo " + correoContacto + " ya esta asociado a otro usuario");
        }

        Empresa empresa = modelMapper.map(dto, Empresa.class);
        empresa.setNombre(normalizar(dto.getNombre()));
        empresa.setNit(nit);
        empresa.setCorreoContacto(correoContacto);
        Empresa registrada = empresaRepository.save(empresa);

        Usuario administrador = crearAdministradorInicial(registrada, credencialInicial);
        return toDto(registrada, administrador.getUsername());
    }

    @Transactional(readOnly = true)
    public List<EmpresaRespuestaDto> listarVisiblesPara(String username) {
        return List.of(toDto(empresaDelUsuario(username), null));
    }

    @Transactional(readOnly = true)
    public EmpresaRespuestaDto obtenerParaUsuario(Long empresaId, String username) {
        Empresa propia = empresaDelUsuario(username);
        if (!propia.getId().equals(empresaId)) {
            throw new UsuarioSinPermisoException("La empresa consultada no pertenece al usuario autenticado");
        }
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La empresa no existe"));
        String administrador = this.usuarioService
                .obtenerAdministradorDeEmpresa(empresa.getId())
                .map(Usuario::getUsername)
                .orElse(null);
        return toDto(empresa, administrador);
    }

    private Empresa empresaDelUsuario(String username) {
        return usuarioService.buscarPorUsername(username).getEmpresa();
    }

    private Usuario crearAdministradorInicial(Empresa empresa, String credencialInicial) {
        return this.usuarioService.crearAdministradorParaEmpresa(empresa, credencialInicial);
    }

    private EmpresaRespuestaDto toDto(Empresa empresa, String administradorUsername) {
        EmpresaRespuestaDto respuesta = modelMapper.map(empresa, EmpresaRespuestaDto.class);
        respuesta.setAdministradorUsername(administradorUsername);
        return respuesta;
    }

    private String normalizar(String valor) {
        return valor == null ? null : valor.trim();
    }

    private String normalizarCorreo(String correo) {
        return correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
    }

    private String exigirCredencial(String credencial) {
        if (credencial == null || credencial.isBlank()) {
            throw new IllegalArgumentException("El administrador inicial requiere una credencial de acceso");
        }
        return credencial;
    }
}
