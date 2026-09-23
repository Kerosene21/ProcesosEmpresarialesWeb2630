package co.edu.javeriana.procesosempresariales.service;

import java.util.List;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.EstadoMensaje;
import co.edu.javeriana.procesosempresariales.domain.MensajeThrow;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.MessageThrowDto;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.MensajeThrowNoValidoException;
import co.edu.javeriana.procesosempresariales.repository.MensajeThrowRepository;

@Service
public class MessageThrowService {

    private static final String MENSAJE_NO_EXISTE = "El mensaje throw no existe en este proceso";
    private static final String SIN_PERMISO_ESCRITURA ="Solo un administrador o editor puede crear o modificar mensajes throw";
    private static final String SIN_PERMISO_ELIMINAR = "Solo un administrador puede eliminar mensajes throw";
    private static final String POOL_DESTINO_IGUAL = "El Message Throw debe cruzar a un pool diferente al de origen";
    private static final String CATCH_NO_EXISTE ="No existe un Message Catch de HU-27 para el codigo de referencia indicado";

    private final MensajeThrowRepository mensajeThrowRepository;
    private final ModelMapper modelMapper;
    private final AccesoProcesoService accesoProcesoService;

    public MessageThrowService(MensajeThrowRepository mensajeThrowRepository, ModelMapper modelMapper,
            AccesoProcesoService accesoProcesoService) {
        this.mensajeThrowRepository = mensajeThrowRepository;
        this.modelMapper = modelMapper;
        this.accesoProcesoService = accesoProcesoService;
    }

    //mantener las entidades JPA fuera del controlador y de Thymeleaf devolviendo DTOs 
    @Transactional(readOnly = true)
    public List<MessageThrowDto> listar(Long procesoId, String username) {
        Proceso proceso = procesoVisible(procesoId, username);
        return mensajeThrowRepository.findByProcesoIdOrderByIdAsc(proceso.getId()).stream()
            .map(mensaje -> conAdvertencias(toDto(mensaje)))
                .toList();
    }

    @Transactional(readOnly = true)
    public MessageThrowDto buscar(Long procesoId, Long mensajeId, String username) {
        Proceso proceso = procesoVisible(procesoId, username);
        return conAdvertencias(toDto(mensajeActivoDelProceso(mensajeId, proceso)));
    }

    @Transactional
    public MessageThrowDto crear(Long procesoId, MessageThrowDto dto, String username) {
        // La autorizacion y si esta en empresa se validan antes de modificar datos
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        validarPoolDestino(dto, proceso);

        MensajeThrow mensaje = modelMapper.map(dto, MensajeThrow.class);
        mensaje.setId(null);
        mensaje.setProceso(proceso);
        mensaje.setEstado(EstadoMensaje.ACTIVO);
        MensajeThrow guardado = mensajeThrowRepository.save(mensaje);
        accesoProcesoService.registrarHistorial(proceso, usuario,
                "mensaje throw creado: " + guardado.getNombre());
        return conAdvertencias(toDto(guardado));
    }

    @Transactional
    public MessageThrowDto actualizar(Long procesoId, Long mensajeId, MessageThrowDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        MensajeThrow mensaje = mensajeActivoDelProceso(mensajeId, proceso);
        validarPoolDestino(dto, proceso);

        modelMapper.map(dto, mensaje);
        mensaje.setId(mensajeId);
        mensaje.setProceso(proceso);
        mensaje.setEstado(EstadoMensaje.ACTIVO);
        MensajeThrow actualizado = mensajeThrowRepository.save(mensaje);
        accesoProcesoService.registrarHistorial(proceso, usuario,
                "mensaje throw actualizado: " + actualizado.getNombre());
        return conAdvertencias(toDto(actualizado));
    }

    @Transactional
    public MessageThrowDto eliminar(Long procesoId, Long mensajeId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        MensajeThrow mensaje = mensajeActivoDelProceso(mensajeId, proceso);
        MessageThrowDto eliminado = toDto(mensaje);

        // @SQLDelete transforma esta llamada en estado inactivo sin borrar el registro 
        mensajeThrowRepository.delete(mensaje);
        accesoProcesoService.registrarHistorial(proceso, usuario,
                "mensaje throw eliminado: " + eliminado.getNombre());
        return eliminado;
    }

    private Proceso procesoVisible(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        return accesoProcesoService.procesoDeLaEmpresa(procesoId, usuario);
    }

    private MensajeThrow mensajeActivoDelProceso(Long mensajeId, Proceso proceso) {
        // Tambien se valida el proceso para evitar acceder a mensajes de otra empresa o proceso.
        return mensajeThrowRepository.findByIdAndProcesoId(mensajeId, proceso.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_NO_EXISTE));
    }

    private MessageThrowDto toDto(MensajeThrow mensaje) {
        return modelMapper.map(mensaje, MessageThrowDto.class);
    }

    private void validarPoolDestino(MessageThrowDto dto, Proceso proceso) {
        String poolOrigen = proceso.getPool().getNombre();
        if (poolOrigen != null && poolOrigen.trim().equalsIgnoreCase(dto.getPoolDestino().trim())) {
            throw new MensajeThrowNoValidoException(POOL_DESTINO_IGUAL);
        }
    }

    private MessageThrowDto conAdvertencias(MessageThrowDto dto) {
        // HU-27 aun no existe en el modelo, por eso se informa la ausencia del catch sin ejecutar nada.
        if (dto.getAdvertencias() == null) {
            dto.setAdvertencias(new java.util.ArrayList<>());
        }
        dto.getAdvertencias().add(CATCH_NO_EXISTE);
        return dto;
    }
}
