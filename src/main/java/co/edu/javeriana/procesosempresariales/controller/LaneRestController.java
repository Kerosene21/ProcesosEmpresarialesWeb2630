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

import co.edu.javeriana.procesosempresariales.dto.CrearLaneDto;
import co.edu.javeriana.procesosempresariales.dto.EditarLaneDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ReordenarLanesDto;
import co.edu.javeriana.procesosempresariales.service.LaneService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/pools/{poolId}/lanes")
@Tag(name = "Lanes", description = "Lanes de un pool; cada lane corresponde a un rol de proceso de la empresa. Las "
        + "escrituras dependen de los permisos de estructura del rol.")
public class LaneRestController {

    private static final String LANE_DUPLICADA = "El pool ya tiene una lane activa para ese rol de proceso "
            + "(`LANE_DUPLICADA`).";

    private LaneService laneService;

    @Autowired
    public LaneRestController(LaneService laneService) {
        this.laneService = laneService;
    }

    @GetMapping
    @Operation(summary = "Listar lanes del pool", description = "Lanes activas en su orden dentro del pool.")
    public ResponseEntity<List<LaneRespuestaDto>> listar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, Principal principal) {
        return ResponseEntity.ok(laneService.listar(procesoId, poolId, principal.getName()));
    }

    @PostMapping
    @Operation(summary = "Crear lane", description = "Agrega una lane para un rol de proceso en la posición indicada "
            + "o al final. Requiere el permiso de estructura `crear lanes`.")
    @ApiResponse(responseCode = "201", description = "Lane creada.")
    @ApiResponse(responseCode = "409", description = LANE_DUPLICADA + " El pool es una caja negra "
            + "(`POOL_CAJA_NEGRA`).")
    public ResponseEntity<LaneRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, @Valid @RequestBody CrearLaneDto dto, Principal principal) {
        LaneRespuestaDto creada = laneService.crear(procesoId, poolId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creada.getId()).toUri();
        return ResponseEntity.created(location).body(creada);
    }

    @PutMapping("/orden")
    @Operation(summary = "Reordenar lanes", description = "Recibe todas las lanes activas del pool, una sola vez cada "
            + "una, en el nuevo orden. Requiere el permiso de estructura `editar lanes`.")
    public ResponseEntity<List<LaneRespuestaDto>> reordenar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, @Valid @RequestBody ReordenarLanesDto dto, Principal principal) {
        return ResponseEntity.ok(laneService.reordenar(procesoId, poolId, dto, principal.getName()));
    }

    @PutMapping("/{laneId}")
    @Operation(summary = "Editar lane", description = "Cambia el rol de proceso y la posición de la lane. Requiere el "
            + "permiso de estructura `editar lanes`.")
    @ApiResponse(responseCode = "200", description = "Lane actualizada.")
    @ApiResponse(responseCode = "409", description = LANE_DUPLICADA)
    public ResponseEntity<LaneRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, @PathVariable("laneId") Long laneId,
            @Valid @RequestBody EditarLaneDto dto, Principal principal) {
        return ResponseEntity.ok(laneService.editar(procesoId, poolId, laneId, dto, principal.getName()));
    }

    @DeleteMapping("/{laneId}")
    @Operation(summary = "Eliminar lane", description = "Requiere el permiso de estructura `eliminar lanes`.")
    @ApiResponse(responseCode = "409", description = "La lane todavía tiene actividades activas "
            + "(`LANE_CON_ACTIVIDADES`).")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, @PathVariable("laneId") Long laneId, Principal principal) {
        laneService.eliminar(procesoId, poolId, laneId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
