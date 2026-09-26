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

import co.edu.javeriana.procesosempresariales.dto.CrearLaneDto;
import co.edu.javeriana.procesosempresariales.dto.EditarLaneDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ReordenarLanesDto;
import co.edu.javeriana.procesosempresariales.service.LaneService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/pools/{poolId}/lanes")
public class LaneRestController {

    private LaneService laneService;

    @Autowired
    public LaneRestController(LaneService laneService) {
        this.laneService = laneService;
    }

    @GetMapping
    public ResponseEntity<List<LaneRespuestaDto>> listar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, Principal principal) {
        return ResponseEntity.ok(laneService.listar(procesoId, poolId, principal.getName()));
    }

    @PostMapping
    public ResponseEntity<LaneRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, @Valid @RequestBody CrearLaneDto dto, Principal principal) {
        LaneRespuestaDto creada = laneService.crear(procesoId, poolId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creada.getId()).toUri();
        return ResponseEntity.created(location).body(creada);
    }

    @PutMapping("/orden")
    public ResponseEntity<List<LaneRespuestaDto>> reordenar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, @Valid @RequestBody ReordenarLanesDto dto, Principal principal) {
        return ResponseEntity.ok(laneService.reordenar(procesoId, poolId, dto, principal.getName()));
    }

    @PutMapping("/{laneId}")
    public ResponseEntity<LaneRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, @PathVariable("laneId") Long laneId,
            @Valid @RequestBody EditarLaneDto dto, Principal principal) {
        return ResponseEntity.ok(laneService.editar(procesoId, poolId, laneId, dto, principal.getName()));
    }

    @DeleteMapping("/{laneId}")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, @PathVariable("laneId") Long laneId, Principal principal) {
        laneService.eliminar(procesoId, poolId, laneId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
