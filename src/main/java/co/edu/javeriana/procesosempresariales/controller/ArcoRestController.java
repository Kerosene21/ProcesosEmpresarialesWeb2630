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

import co.edu.javeriana.procesosempresariales.dto.ArcoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearArcoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarArcoDto;
import co.edu.javeriana.procesosempresariales.service.ArcoService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/arcos")
public class ArcoRestController {

    private ArcoService arcoService;

    @Autowired
    public ArcoRestController(ArcoService arcoService) {
        this.arcoService = arcoService;
    }

    @GetMapping
    public ResponseEntity<List<ArcoRespuestaDto>> listar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(arcoService.consultarActivos(procesoId, principal.getName()));
    }

    @GetMapping("/{arcoId}")
    public ResponseEntity<ArcoRespuestaDto> obtener(@PathVariable("procesoId") Long procesoId,
            @PathVariable("arcoId") Long arcoId, Principal principal) {
        return ResponseEntity.ok(arcoService.obtener(procesoId, arcoId, principal.getName()));
    }

    @PostMapping
    public ResponseEntity<ArcoRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody CrearArcoDto dto, Principal principal) {
        ArcoRespuestaDto creado = arcoService.crear(procesoId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{arcoId}")
    public ResponseEntity<ArcoRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("arcoId") Long arcoId, @Valid @RequestBody EditarArcoDto dto, Principal principal) {
        return ResponseEntity.ok(arcoService.editar(procesoId, arcoId, dto, principal.getName()));
    }

    @DeleteMapping("/{arcoId}")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("arcoId") Long arcoId, Principal principal) {
        arcoService.eliminar(procesoId, arcoId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
