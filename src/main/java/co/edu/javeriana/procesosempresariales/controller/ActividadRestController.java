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

import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.EditarActividadDto;
import co.edu.javeriana.procesosempresariales.service.ActividadService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/actividades")
@Tag(name = "Actividades", description = "Actividades (tareas) del modelo, ubicadas en una lane del proceso.")
public class ActividadRestController {

    private static final String NOMBRE_DUPLICADO = "Ya existe una actividad con ese nombre en el proceso "
            + "(`ACTIVIDAD_NOMBRE_DUPLICADO`).";

    private ActividadService actividadService;

    @Autowired
    public ActividadRestController(ActividadService actividadService) {
        this.actividadService = actividadService;
    }

    @GetMapping
    @Operation(summary = "Listar actividades del proceso", description = "Actividades activas del modelo.")
    public ResponseEntity<List<ActividadRespuestaDto>> listar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(actividadService.consultarActivas(procesoId, principal.getName()));
    }

    @GetMapping("/{actividadId}")
    @Operation(summary = "Consultar actividad")
    public ResponseEntity<ActividadRespuestaDto> obtener(@PathVariable("procesoId") Long procesoId,
            @PathVariable("actividadId") Long actividadId, Principal principal) {
        return ResponseEntity.ok(actividadService.obtener(procesoId, actividadId, principal.getName()));
    }

    @PostMapping
    @Operation(summary = "Crear actividad", description = "Ubica la actividad en una lane del proceso "
            + "(`LANE_NO_VALIDA` si no pertenece a él). Requiere rol ADMINISTRADOR o EDITOR de la empresa "
            + "propietaria.")
    @ApiResponse(responseCode = "201", description = "Actividad creada.")
    @ApiResponse(responseCode = "409", description = NOMBRE_DUPLICADO)
    public ResponseEntity<ActividadRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody CrearActividadDto dto, Principal principal) {
        ActividadRespuestaDto creada = actividadService.crear(procesoId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creada.getId()).toUri();
        return ResponseEntity.created(location).body(creada);
    }

    @PutMapping("/{actividadId}")
    @Operation(summary = "Editar actividad", description = "Cambia nombre, tipo y lane. Requiere rol ADMINISTRADOR "
            + "o EDITOR de la empresa propietaria.")
    @ApiResponse(responseCode = "200", description = "Actividad actualizada.")
    @ApiResponse(responseCode = "409", description = NOMBRE_DUPLICADO)
    public ResponseEntity<ActividadRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("actividadId") Long actividadId, @Valid @RequestBody EditarActividadDto dto,
            Principal principal) {
        return ResponseEntity.ok(actividadService.editar(procesoId, actividadId, dto, principal.getName()));
    }

    @DeleteMapping("/{actividadId}")
    @Operation(summary = "Eliminar actividad", description = "Desactiva la actividad y los arcos conectados a ella. "
            + "Solo ADMINISTRADOR.")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("actividadId") Long actividadId, Principal principal) {
        actividadService.eliminar(procesoId, actividadId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
