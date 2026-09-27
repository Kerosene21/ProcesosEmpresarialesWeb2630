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

import co.edu.javeriana.procesosempresariales.dto.CrearMessageCatchDto;
import co.edu.javeriana.procesosempresariales.dto.EditarMessageCatchDto;
import co.edu.javeriana.procesosempresariales.dto.MessageCatchRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.MessageCatchService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/message-catches")
@Tag(name = "Message Catch", description = "Eventos que modelan la recepción de un mensaje en un pool: de INICIO "
        + "(inicia un caso nuevo) o INTERMEDIO (el caso en curso espera el mensaje). Solo se modela: el sistema no "
        + "recibe mensajes ni ejecuta procesos.")
public class MessageCatchRestController {

    private MessageCatchService messageCatchService;

    @Autowired
    public MessageCatchRestController(MessageCatchService messageCatchService) {
        this.messageCatchService = messageCatchService;
    }

    @GetMapping
    @Operation(summary = "Listar Message Catch del proceso", description = "Cada evento indica su Message Throw "
            + "homólogo (`throwHomologoId`) y las advertencias de correlación.")
    public ResponseEntity<List<MessageCatchRespuestaDto>> listar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(messageCatchService.listar(procesoId, principal.getName()));
    }

    @GetMapping("/{catchId}")
    @Operation(summary = "Consultar Message Catch")
    public ResponseEntity<MessageCatchRespuestaDto> obtener(@PathVariable("procesoId") Long procesoId,
            @PathVariable("catchId") Long catchId, Principal principal) {
        return ResponseEntity.ok(messageCatchService.obtener(procesoId, catchId, principal.getName()));
    }

    @PostMapping
    @Operation(summary = "Crear Message Catch", description = "Modela la variante, el pool, los datos esperados, las "
            + "actividades que los usan, si el origen es externo y la correlación. Un Message Catch de INICIO siempre "
            + "inicia un caso nuevo. Requiere rol ADMINISTRADOR o EDITOR de la empresa propietaria.")
    @ApiResponse(responseCode = "201", description = "Message Catch modelado.")
    @ApiResponse(responseCode = "400", description = "Un Message Catch de INICIO no puede descartar mensajes sin "
            + "caso (`CORRELACION_NO_VALIDA`), falta `poolId` con varios pools (`POOL_NO_VALIDO`) o los datos no son "
            + "válidos.")
    @ApiResponse(responseCode = "409", description = "El pool es una caja negra (`POOL_CAJA_NEGRA`).")
    public ResponseEntity<MessageCatchRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody CrearMessageCatchDto dto, Principal principal) {
        MessageCatchRespuestaDto creado = messageCatchService.crear(procesoId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{catchId}")
    @Operation(summary = "Editar Message Catch", description = "El pool no cambia. Requiere rol ADMINISTRADOR o "
            + "EDITOR de la empresa propietaria.")
    @ApiResponse(responseCode = "200", description = "Message Catch actualizado.")
    @ApiResponse(responseCode = "400", description = "Un Message Catch de INICIO no puede descartar mensajes sin "
            + "caso (`CORRELACION_NO_VALIDA`) ni tener flujos de secuencia entrantes (`CATCH_INICIO_CON_ENTRADA`), "
            + "o los datos no son válidos.")
    public ResponseEntity<MessageCatchRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("catchId") Long catchId, @Valid @RequestBody EditarMessageCatchDto dto,
            Principal principal) {
        return ResponseEntity.ok(messageCatchService.editar(procesoId, catchId, dto, principal.getName()));
    }

    @GetMapping("/{catchId}/eliminacion")
    @Operation(summary = "Previsualizar la eliminación de un Message Catch", description = "Devuelve el evento con "
            + "las advertencias: arcos que se desactivarían y Message Throw que quedarían sin homólogo. Solo "
            + "ADMINISTRADOR.")
    public ResponseEntity<MessageCatchRespuestaDto> confirmarEliminacion(@PathVariable("procesoId") Long procesoId,
            @PathVariable("catchId") Long catchId, Principal principal) {
        return ResponseEntity.ok(messageCatchService.obtenerParaEliminar(procesoId, catchId, principal.getName()));
    }

    @DeleteMapping("/{catchId}")
    @Operation(summary = "Eliminar Message Catch", description = "Desactiva el evento y los arcos conectados a él. "
            + "Solo ADMINISTRADOR.")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("catchId") Long catchId, Principal principal) {
        messageCatchService.eliminar(procesoId, catchId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
