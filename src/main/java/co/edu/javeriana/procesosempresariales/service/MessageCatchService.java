package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.MessageCatch;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.MessageCatchDto;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.repository.MessageCatchRepository;
import co.edu.javeriana.procesosempresariales.repository.MensajeThrowRepository;

// CRUD, permisos y validacion del origen de un Message Catch
// conecta HU27 con HU25 sin ejecutar ningun mensaje real

@Service
public class MessageCatchService {

    private static final int ACTIVO = 0;
    private static final String CATCH_NO_EXISTE = "El Message Catch no existe en este proceso";
    private static final String SIN_PERMISO_ESCRITURA =
            "Solo un administrador o editor puede crear o modificar Message Catch";
    private static final String SIN_PERMISO_ELIMINAR = "Solo un administrador puede eliminar Message Catch";
    private static final String THROW_NO_EXISTE =
            "No existe un Message Throw de HU-25 con el mismo nombre en este proceso";
        private static final String INICIO_SIN_ENTRADAS =
            "La variante INICIO debe modelarse sin arcos entrantes";

    private final MessageCatchRepository messageCatchRepository;
    private final MensajeThrowRepository mensajeThrowRepository;
    private final ModelMapper modelMapper;
    private final AccesoProcesoService accesoProcesoService;

    // Constructor injection hace visibles las dependencias y facilita las pruebas unitarias.
    public MessageCatchService(MessageCatchRepository messageCatchRepository,
            MensajeThrowRepository mensajeThrowRepository, ModelMapper modelMapper,
            AccesoProcesoService accesoProcesoService) {
        this.messageCatchRepository = messageCatchRepository;
        this.mensajeThrowRepository = mensajeThrowRepository;
        this.modelMapper = modelMapper;
        this.accesoProcesoService = accesoProcesoService;
    }

    // Thymeleaf recibe DTOs y nunca entidades JPA.
    @Transactional(readOnly = true)
    public List<MessageCatchDto> listar(Long procesoId, String username) {
        Proceso proceso = procesoVisible(procesoId, username);
        return messageCatchRepository.findByProcesoIdOrderByIdAsc(proceso.getId()).stream()
                .map(catchMessage -> conAdvertencias(toDto(catchMessage), proceso))
                .toList();
    }

    @Transactional(readOnly = true)
    public MessageCatchDto buscar(Long procesoId, Long catchId, String username) {
        Proceso proceso = procesoVisible(procesoId, username);
        return conAdvertencias(toDto(catchActivoDelProceso(catchId, proceso)), proceso);
    }

    @Transactional
    public MessageCatchDto crear(Long procesoId, MessageCatchDto dto, String username) {
        Usuario usuario = usuarioConPermisoDeEscritura(username);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        MessageCatch messageCatch = modelMapper.map(dto, MessageCatch.class);
        messageCatch.setId(null);
        messageCatch.setStatus(ACTIVO);
        messageCatch.setProceso(proceso);

        MessageCatch guardado = messageCatchRepository.save(messageCatch);
        accesoProcesoService.registrarHistorial(proceso, usuario,
                "message catch creado: " + guardado.getNombreMensaje());
        return conAdvertencias(toDto(guardado), proceso);
    }

    @Transactional
    public MessageCatchDto actualizar(Long procesoId, Long catchId, MessageCatchDto dto, String username) {
        Usuario usuario = usuarioConPermisoDeEscritura(username);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        MessageCatch messageCatch = catchActivoDelProceso(catchId, proceso);
        modelMapper.map(dto, messageCatch);
        messageCatch.setId(catchId);
        messageCatch.setStatus(ACTIVO);
        messageCatch.setProceso(proceso);

        MessageCatch actualizado = messageCatchRepository.save(messageCatch);
        accesoProcesoService.registrarHistorial(proceso, usuario,
                "message catch actualizado: " + actualizado.getNombreMensaje());
        return conAdvertencias(toDto(actualizado), proceso);
    }

    @Transactional
    public MessageCatchDto eliminar(Long procesoId, Long catchId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        MessageCatch messageCatch = catchActivoDelProceso(catchId, proceso);
        MessageCatchDto eliminado = toDto(messageCatch);

        // @SQLDelete convierte delete() en UPDATE status = 1 y conserva el historial.
        messageCatchRepository.delete(messageCatch);
        accesoProcesoService.registrarHistorial(proceso, usuario,
                "message catch eliminado: " + eliminado.getNombreMensaje());
        return eliminado;
    }

    private MessageCatchDto conAdvertencias(MessageCatchDto dto, Proceso proceso) {
        if (dto.getAdvertencias() == null) {
            dto.setAdvertencias(new ArrayList<>());
        }
        if (!dto.isOrigenExterno()
                && !mensajeThrowRepository.existsByProcesoIdAndNombreIgnoreCase(proceso.getId(), dto.getNombreMensaje())) {
            dto.getAdvertencias().add(THROW_NO_EXISTE);
        }
        if (dto.getVariante() == co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch.INICIO) {
            // El modelo actual aun no conecta Message Catch con Arco; se deja la regla visible para el modelado.
            dto.getAdvertencias().add(INICIO_SIN_ENTRADAS);
        }
        return dto;
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

    private MessageCatch catchActivoDelProceso(Long catchId, Proceso proceso) {
        return messageCatchRepository.findByIdAndProcesoId(catchId, proceso.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(CATCH_NO_EXISTE));
    }

    private MessageCatchDto toDto(MessageCatch messageCatch) {
        return modelMapper.map(messageCatch, MessageCatchDto.class);
    }
}
