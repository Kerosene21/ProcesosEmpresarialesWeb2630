package co.edu.javeriana.procesosempresariales.service;

import java.util.List;
import java.util.Locale;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.EmpresaRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.NitEmpresaDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.EmpresaRepository;

@Service
public class EmpresaService {

    static final String EMPRESA_NO_EXISTE = "La empresa no existe";

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
        if (usuarioService.existeUsuarioConCorreo(correoContacto)) {
            throw new CorreoAdministradorEnUsoException(
                    "El correo " + correoContacto + " ya esta asociado a otro usuario");
        }

        Empresa empresa = modelMapper.map(dto, Empresa.class);
        empresa.setNombre(normalizar(dto.getNombre()));
        empresa.setNit(nit);
        empresa.setCorreoContacto(correoContacto);
        Empresa registrada = empresaRepository.save(empresa);

        Usuario administrador = usuarioService.crearAdministradorInicial(registrada, credencialInicial);
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
        Empresa empresa = buscarPorId(empresaId);
        String administrador = usuarioService.correoDelAdministrador(empresa.getId()).orElse(null);
        return toDto(empresa, administrador);
    }

    @Transactional(readOnly = true)
    public Empresa buscarPorId(Long empresaId) {
        return empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(EMPRESA_NO_EXISTE));
    }

    private Empresa empresaDelUsuario(String username) {
        return usuarioService.usuarioAutenticado(username).getEmpresa();
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
