package co.edu.javeriana.procesosempresariales.service;

import java.util.List;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.EnvioMensajeExterno;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.EnvioMensajeExternoDto;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.repository.EnvioMensajeExternoRepository;


 //concentra el CRUD y las reglas de acceso de la configuracion externa
@Service
public class EnvioMensajeExternoService {

    private static final int ACTIVO = 0;
    private static final String ENVIO_NO_EXISTE = "El envio de mensaje externo no existe en este proceso";
    private static final String SIN_PERMISO_ESCRITURA ="Solo un administrador o editor puede crear o modificar envios externos";
    private static final String SIN_PERMISO_ELIMINAR = "Solo un administrador puede eliminar envios externos";

    private final EnvioMensajeExternoRepository envioRepository;
    private final ModelMapper modelMapper;
    private final AccesoProcesoService accesoProcesoService;

    // La inyeccion por constructor hace explicitas las dependencias y facilita las pruebas unitarias.
    public EnvioMensajeExternoService(EnvioMensajeExternoRepository envioRepository, ModelMapper modelMapper,
            AccesoProcesoService accesoProcesoService) {
        this.envioRepository = envioRepository;
        this.modelMapper = modelMapper;
        this.accesoProcesoService = accesoProcesoService;
    }

    // Se devuelve una lista de DTOs: las entidades JPA no salen hacia Thymeleaf.
    @Transactional(readOnly = true)
    public List<EnvioMensajeExternoDto> listar(Long procesoId, String username) {
        Proceso proceso = procesoVisible(procesoId, username);
        return envioRepository.findByProcesoIdOrderByIdAsc(proceso.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public EnvioMensajeExternoDto buscar(Long procesoId, Long envioId, String username) {
        Proceso proceso = procesoVisible(procesoId, username);
        return toDto(envioActivoDelProceso(envioId, proceso));
    }

    @Transactional
    public EnvioMensajeExternoDto crear(Long procesoId, EnvioMensajeExternoDto dto, String username) {
        Usuario usuario = usuarioConPermisoDeEscritura(username);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);

        EnvioMensajeExterno envio = modelMapper.map(dto, EnvioMensajeExterno.class);
        envio.setId(null);
        envio.setStatus(ACTIVO);
        envio.setProceso(proceso);
        EnvioMensajeExterno guardado = envioRepository.save(envio);
        accesoProcesoService.registrarHistorial(proceso, usuario,
                "envio de mensaje externo creado: " + guardado.getNombreSistemaExterno());
        return toDto(guardado);
    }

    @Transactional
    public EnvioMensajeExternoDto actualizar(Long procesoId, Long envioId, EnvioMensajeExternoDto dto,
            String username) {
        Usuario usuario = usuarioConPermisoDeEscritura(username);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        EnvioMensajeExterno envio = envioActivoDelProceso(envioId, proceso);

        modelMapper.map(dto, envio);
        envio.setId(envioId);
        envio.setStatus(ACTIVO);
        envio.setProceso(proceso);
        EnvioMensajeExterno actualizado = envioRepository.save(envio);
        accesoProcesoService.registrarHistorial(proceso, usuario,
                "envio de mensaje externo actualizado: " + actualizado.getNombreSistemaExterno());
        return toDto(actualizado);
    }

    @Transactional
    public EnvioMensajeExternoDto eliminar(Long procesoId, Long envioId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        EnvioMensajeExterno envio = envioActivoDelProceso(envioId, proceso);
        EnvioMensajeExternoDto eliminado = toDto(envio);

        // @SQLDelete convierte delete() en UPDATE status = 1 y conserva el registro historico.
        envioRepository.delete(envio);
        accesoProcesoService.registrarHistorial(proceso, usuario,
                "envio de mensaje externo eliminado: " + eliminado.getNombreSistemaExterno());
        return eliminado;
    }

    private Usuario usuarioConPermisoDeEscritura(String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        return usuario;
    }

    private Proceso procesoVisible(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        return accesoProcesoService.procesoDeLaEmpresa(procesoId, usuario);
    }

    private EnvioMensajeExterno envioActivoDelProceso(Long envioId, Proceso proceso) {
        // El Optional evita devolver null y tambien impide acceder a otro proceso.
        return envioRepository.findByIdAndProcesoId(envioId, proceso.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ENVIO_NO_EXISTE));
    }

    private EnvioMensajeExternoDto toDto(EnvioMensajeExterno envio) {
        return modelMapper.map(envio, EnvioMensajeExternoDto.class);
    }
}
