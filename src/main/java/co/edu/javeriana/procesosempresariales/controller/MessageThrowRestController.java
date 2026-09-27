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

import co.edu.javeriana.procesosempresariales.dto.CrearMessageThrowDto;
import co.edu.javeriana.procesosempresariales.dto.EditarMessageThrowDto;
import co.edu.javeriana.procesosempresariales.dto.MessageThrowRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.MessageThrowService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/message-throws")
public class MessageThrowRestController {

    private MessageThrowService messageThrowService;

    @Autowired
    public MessageThrowRestController(MessageThrowService messageThrowService) {
        this.messageThrowService = messageThrowService;
    }

    @GetMapping
    public ResponseEntity<List<MessageThrowRespuestaDto>> listar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(messageThrowService.listar(procesoId, principal.getName()));
    }

    @GetMapping("/{throwId}")
    public ResponseEntity<MessageThrowRespuestaDto> obtener(@PathVariable("procesoId") Long procesoId,
            @PathVariable("throwId") Long throwId, Principal principal) {
        return ResponseEntity.ok(messageThrowService.obtener(procesoId, throwId, principal.getName()));
    }

    @PostMapping
    public ResponseEntity<MessageThrowRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody CrearMessageThrowDto dto, Principal principal) {
        MessageThrowRespuestaDto creado = messageThrowService.crear(procesoId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{throwId}")
    public ResponseEntity<MessageThrowRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("throwId") Long throwId, @Valid @RequestBody EditarMessageThrowDto dto,
            Principal principal) {
        return ResponseEntity.ok(messageThrowService.editar(procesoId, throwId, dto, principal.getName()));
    }

    @GetMapping("/{throwId}/eliminacion")
    public ResponseEntity<MessageThrowRespuestaDto> confirmarEliminacion(@PathVariable("procesoId") Long procesoId,
            @PathVariable("throwId") Long throwId, Principal principal) {
        return ResponseEntity.ok(messageThrowService.obtenerParaEliminar(procesoId, throwId, principal.getName()));
    }

    @DeleteMapping("/{throwId}")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("throwId") Long throwId, Principal principal) {
        messageThrowService.eliminar(procesoId, throwId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
