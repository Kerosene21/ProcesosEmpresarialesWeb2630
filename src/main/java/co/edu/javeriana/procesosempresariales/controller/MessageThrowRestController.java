package co.edu.javeriana.procesosempresariales.controller;

import java.net.URI;
import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import co.edu.javeriana.procesosempresariales.dto.CrearMessageThrowDto;
import co.edu.javeriana.procesosempresariales.dto.EditarMessageThrowDto;
import co.edu.javeriana.procesosempresariales.dto.MessageThrowRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.MessageThrowService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/message-throws")
@Tag(name = "Message Throw", description = "Eventos que modelan la emisión de un mensaje desde un pool hacia otro "
        + "pool del mismo proceso. Solo se modela el flujo de mensaje: el sistema no envía mensajes reales.")
public class MessageThrowRestController {

    private static final String DESTINO_NO_VALIDO = "El pool destino no es un pool activo del proceso "
            + "(`POOL_MENSAJE_NO_VALIDO`), coincide con el pool origen (`MENSAJE_ENTRE_MISMO_POOL`) o los datos no "
            + "son válidos.";

    private MessageThrowService messageThrowService;

    @Autowired
    public MessageThrowRestController(MessageThrowService messageThrowService) {
        this.messageThrowService = messageThrowService;
    }

    @GetMapping
    @Operation(summary = "Listar Message Throw del proceso", description = "Cada evento indica su Message Catch "
            + "homólogo en el pool destino (`catchHomologoId`: mismo nombre y clave de correlación) y las "
            + "advertencias de correlación.")
    public ResponseEntity<List<MessageThrowRespuestaDto>> listar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(messageThrowService.listar(procesoId, principal.getName()));
    }

    @GetMapping("/{throwId}")
    @Operation(summary = "Consultar Message Throw")
    public ResponseEntity<MessageThrowRespuestaDto> obtener(@PathVariable("procesoId") Long procesoId,
            @PathVariable("throwId") Long throwId, Principal principal) {
        return ResponseEntity.ok(messageThrowService.obtener(procesoId, throwId, principal.getName()));
    }

    @PostMapping
    @Operation(summary = "Crear Message Throw", description = "Modela la emisión: pool origen, pool destino "
            + "distinto, nombre del mensaje, contenido y clave de correlación opcional. No envía ningún mensaje. "
            + "Requiere rol ADMINISTRADOR o EDITOR de la empresa propietaria.")
    @ApiResponse(responseCode = "201", description = "Message Throw modelado.")
    @ApiResponse(responseCode = "400", description = DESTINO_NO_VALIDO)
    @ApiResponse(responseCode = "409", description = "El pool origen es una caja negra (`POOL_CAJA_NEGRA`).")
    public ResponseEntity<MessageThrowRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody CrearMessageThrowDto dto, Principal principal) {
        MessageThrowRespuestaDto creado = messageThrowService.crear(procesoId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{throwId}")
    @Operation(summary = "Editar Message Throw", description = "El pool origen no cambia. Requiere rol "
            + "ADMINISTRADOR o EDITOR de la empresa propietaria.")
    @ApiResponse(responseCode = "200", description = "Message Throw actualizado.")
    @ApiResponse(responseCode = "400", description = DESTINO_NO_VALIDO)
    public ResponseEntity<MessageThrowRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("throwId") Long throwId, @Valid @RequestBody EditarMessageThrowDto dto,
            Principal principal) {
        return ResponseEntity.ok(messageThrowService.editar(procesoId, throwId, dto, principal.getName()));
    }

    @GetMapping("/{throwId}/eliminacion")
    @Operation(summary = "Previsualizar la eliminación de un Message Throw", description = "Devuelve el evento con "
            + "las advertencias: arcos que se desactivarían y Message Catch que quedarían sin homólogo. Solo "
            + "ADMINISTRADOR.")
    public ResponseEntity<MessageThrowRespuestaDto> confirmarEliminacion(@PathVariable("procesoId") Long procesoId,
            @PathVariable("throwId") Long throwId, Principal principal) {
        return ResponseEntity.ok(messageThrowService.obtenerParaEliminar(procesoId, throwId, principal.getName()));
    }

    @DeleteMapping("/{throwId}")
    @Operation(summary = "Eliminar Message Throw", description = "Desactiva el evento y los arcos conectados a él. "
            + "Solo ADMINISTRADOR.")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("throwId") Long throwId, Principal principal) {
        messageThrowService.eliminar(procesoId, throwId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
