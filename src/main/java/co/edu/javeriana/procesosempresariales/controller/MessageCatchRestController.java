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

import co.edu.javeriana.procesosempresariales.dto.CrearMessageCatchDto;
import co.edu.javeriana.procesosempresariales.dto.EditarMessageCatchDto;
import co.edu.javeriana.procesosempresariales.dto.MessageCatchRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.MessageCatchService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/message-catches")
public class MessageCatchRestController {

    private MessageCatchService messageCatchService;

    @Autowired
    public MessageCatchRestController(MessageCatchService messageCatchService) {
        this.messageCatchService = messageCatchService;
    }

    @GetMapping
    public ResponseEntity<List<MessageCatchRespuestaDto>> listar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(messageCatchService.listar(procesoId, principal.getName()));
    }

    @GetMapping("/{catchId}")
    public ResponseEntity<MessageCatchRespuestaDto> obtener(@PathVariable("procesoId") Long procesoId,
            @PathVariable("catchId") Long catchId, Principal principal) {
        return ResponseEntity.ok(messageCatchService.obtener(procesoId, catchId, principal.getName()));
    }

    @PostMapping
    public ResponseEntity<MessageCatchRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody CrearMessageCatchDto dto, Principal principal) {
        MessageCatchRespuestaDto creado = messageCatchService.crear(procesoId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{catchId}")
    public ResponseEntity<MessageCatchRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("catchId") Long catchId, @Valid @RequestBody EditarMessageCatchDto dto,
            Principal principal) {
        return ResponseEntity.ok(messageCatchService.editar(procesoId, catchId, dto, principal.getName()));
    }

    @GetMapping("/{catchId}/eliminacion")
    public ResponseEntity<MessageCatchRespuestaDto> confirmarEliminacion(@PathVariable("procesoId") Long procesoId,
            @PathVariable("catchId") Long catchId, Principal principal) {
        return ResponseEntity.ok(messageCatchService.obtenerParaEliminar(procesoId, catchId, principal.getName()));
    }

    @DeleteMapping("/{catchId}")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("catchId") Long catchId, Principal principal) {
        messageCatchService.eliminar(procesoId, catchId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
