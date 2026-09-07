package co.edu.javeriana.procesosempresariales.service;

import java.util.List;
import java.util.Locale;

import org.modelmapper.ModelMapper;
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
import co.edu.javeriana.procesosempresariales.repository.EmpresaRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@Service
public class EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ModelMapper modelMapper;

    public EmpresaService(EmpresaRepository empresaRepository, UsuarioRepository usuarioRepository,
            ModelMapper modelMapper) {
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.modelMapper = modelMapper;
    }

    @Transactional
    public EmpresaRespuestaDto registrar(RegistroEmpresaDto dto) {
        String nit = normalizar(dto.getNit());
        String correoContacto = normalizarCorreo(dto.getCorreoContacto());

        if (empresaRepository.existsByNit(nit)) {
            throw new NitEmpresaDuplicadoException("Ya existe una empresa registrada con el NIT " + nit);
        }
        if (usuarioRepository.existsByUsername(correoContacto)) {
            throw new CorreoAdministradorEnUsoException(
                    "El correo " + correoContacto + " ya esta asociado a otro usuario");
        }

        Empresa empresa = modelMapper.map(dto, Empresa.class);
        empresa.setNombre(normalizar(dto.getNombre()));
        empresa.setNit(nit);
        empresa.setCorreoContacto(correoContacto);
        Empresa registrada = empresaRepository.save(empresa);

        Usuario administrador = crearAdministradorInicial(registrada);
        return toDto(registrada, administrador.getUsername());
    }

    @Transactional(readOnly = true)
    public List<EmpresaRespuestaDto> listar() {
        return empresaRepository.findAll().stream()
                .map(empresa -> toDto(empresa, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public EmpresaRespuestaDto obtener(Long empresaId) {
        Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La empresa no existe"));
        String administrador = usuarioRepository
                .findFirstByEmpresaIdAndRolOrderByIdAsc(empresa.getId(), RolUsuario.ADMINISTRADOR)
                .map(Usuario::getUsername)
                .orElse(null);
        return toDto(empresa, administrador);
    }

    private Usuario crearAdministradorInicial(Empresa empresa) {
        Usuario administrador = new Usuario();
        administrador.setUsername(empresa.getCorreoContacto());
        administrador.setRol(RolUsuario.ADMINISTRADOR);
        administrador.setEmpresa(empresa);
        usuarioRepository.save(administrador);
        return administrador;
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
}
