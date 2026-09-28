package co.edu.javeriana.procesosempresariales.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CambiarRolUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.UsuarioRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@Service
public class UsuarioService {

    static final String USUARIO_AUTENTICADO_NO_EXISTE = "El usuario autenticado no existe";

    private UsuarioRepository usuarioRepository;
    private ModelMapper modelMapper;
    private PasswordEncoder passwordEncoder;

    @Autowired
    public UsuarioService(UsuarioRepository usuarioRepository, ModelMapper modelMapper,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.modelMapper = modelMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UsuarioRespuestaDto> listarDeMiEmpresa(String administradorUsername) {
        Usuario administrador = exigirAdministrador(administradorUsername);
        return usuarioRepository.findByEmpresaIdOrderByUsernameAsc(administrador.getEmpresa().getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioRespuestaDto obtener(Long usuarioId, String administradorUsername) {
        Usuario administrador = exigirAdministrador(administradorUsername);
        return toDto(usuarioDeLaEmpresa(usuarioId, administrador));
    }

    @Transactional
    public UsuarioRespuestaDto crear(CrearUsuarioDto dto, String administradorUsername) {
        Usuario administrador = exigirAdministrador(administradorUsername);
        String correo = normalizarCorreo(dto.getCorreo());
        String credencialInicial = exigirCredencial(dto.getPassword());

        if (usuarioRepository.existsByUsername(correo)) {
            throw new CorreoAdministradorEnUsoException("El correo " + correo + " ya esta asociado a otro usuario");
        }

        Empresa empresa = administrador.getEmpresa();
        Usuario nuevo = new Usuario();
        nuevo.setUsername(correo);
        nuevo.setPassword(passwordEncoder.encode(credencialInicial));
        nuevo.setRol(dto.getRol());
        nuevo.setActivo(true);
        nuevo.setEmpresa(empresa);
        return toDto(usuarioRepository.save(nuevo));
    }

    @Transactional
    public UsuarioRespuestaDto cambiarRol(Long usuarioId, CambiarRolUsuarioDto dto, String administradorUsername) {
        Usuario administrador = exigirAdministrador(administradorUsername);
        Usuario objetivo = usuarioDeLaEmpresa(usuarioId, administrador);
        exigirQueNoSeaSuPropiaCuenta(administrador, objetivo);
        objetivo.setRol(dto.getRol());
        return toDto(usuarioRepository.save(objetivo));
    }

    @Transactional
    public UsuarioRespuestaDto desactivar(Long usuarioId, String administradorUsername) {
        Usuario administrador = exigirAdministrador(administradorUsername);
        Usuario objetivo = usuarioDeLaEmpresa(usuarioId, administrador);
        exigirQueNoSeaSuPropiaCuenta(administrador, objetivo);
        objetivo.setActivo(false);
        return toDto(usuarioRepository.save(objetivo));
    }

    @Transactional(readOnly = true)
    public Usuario usuarioAutenticado(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException(USUARIO_AUTENTICADO_NO_EXISTE));
    }

    @Transactional(readOnly = true)
    public boolean existeUsuarioConCorreo(String correo) {
        return usuarioRepository.existsByUsername(correo);
    }

    @Transactional(readOnly = true)
    public Optional<String> correoDelAdministrador(Long empresaId) {
        return usuarioRepository.findFirstByEmpresaIdAndRolOrderByIdAsc(empresaId, RolUsuario.ADMINISTRADOR)
                .map(Usuario::getUsername);
    }

    @Transactional
    public Usuario crearAdministradorInicial(Empresa empresa, String credencialInicial) {
        Usuario administrador = new Usuario();
        administrador.setUsername(empresa.getCorreoContacto());
        administrador.setPassword(passwordEncoder.encode(credencialInicial));
        administrador.setRol(RolUsuario.ADMINISTRADOR);
        administrador.setActivo(true);
        administrador.setEmpresa(empresa);
        usuarioRepository.save(administrador);
        return administrador;
    }

    private Usuario exigirAdministrador(String username) {
        Usuario usuario = usuarioAutenticado(username);
        if (usuario.getRol() != RolUsuario.ADMINISTRADOR) {
            throw new UsuarioSinPermisoException("Solo un administrador puede gestionar los usuarios de la empresa");
        }
        return usuario;
    }

    private Usuario usuarioDeLaEmpresa(Long usuarioId, Usuario administrador) {
        return usuarioRepository.findByIdAndEmpresaId(usuarioId, administrador.getEmpresa().getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe en tu empresa"));
    }

    private void exigirQueNoSeaSuPropiaCuenta(Usuario administrador, Usuario objetivo) {
        if (administrador.getId().equals(objetivo.getId())) {
            throw new UsuarioSinPermisoException(
                    "Un administrador no puede cambiar su propio rol ni desactivar su propia cuenta");
        }
    }

    private UsuarioRespuestaDto toDto(Usuario usuario) {
        UsuarioRespuestaDto respuesta = modelMapper.map(usuario, UsuarioRespuestaDto.class);
        respuesta.setCorreo(usuario.getUsername());
        return respuesta;
    }

    private String normalizarCorreo(String correo) {
        return correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
    }

    private String exigirCredencial(String credencial) {
        if (credencial == null || credencial.isBlank()) {
            throw new IllegalArgumentException("El usuario nuevo requiere una credencial de acceso");
        }
        return credencial;
    }
}
