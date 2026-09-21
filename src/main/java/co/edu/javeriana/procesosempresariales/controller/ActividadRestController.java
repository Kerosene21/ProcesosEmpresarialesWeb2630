package co.edu.javeriana.procesosempresariales.controller;

import java.net.URI;
import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.EditarActividadDto;
import co.edu.javeriana.procesosempresariales.service.ActividadService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/actividades")
public class ActividadRestController {

    private final ActividadService actividadService;

    public ActividadRestController(ActividadService actividadService) {
        this.actividadService = actividadService;
    }

    @PostMapping
    public ResponseEntity<ActividadRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody CrearActividadDto dto, Principal principal) {
        ActividadRespuestaDto creada = actividadService.crear(procesoId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creada.getId()).toUri();
        return ResponseEntity.created(location).body(creada);
    }

    @PutMapping("/{actividadId}")
    public ResponseEntity<ActividadRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("actividadId") Long actividadId, @Valid @RequestBody EditarActividadDto dto,
            Principal principal) {
        return ResponseEntity.ok(actividadService.editar(procesoId, actividadId, dto, principal.getName()));
    }

    @DeleteMapping("/{actividadId}")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("actividadId") Long actividadId, Principal principal) {
        actividadService.eliminar(procesoId, actividadId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
