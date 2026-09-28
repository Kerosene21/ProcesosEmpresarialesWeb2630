package co.edu.javeriana.procesosempresariales.controller;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import co.edu.javeriana.procesosempresariales.dto.DiagramaProcesoDto;
import co.edu.javeriana.procesosempresariales.service.DiagramaProcesoService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/diagrama")
@Tag(name = "Diagrama", description = "Vista consolidada del modelo BPMN de un proceso para dibujarlo. Solo "
        + "representa el modelo: el sistema no es un motor BPMN y no ejecuta procesos.")
public class DiagramaProcesoRestController {

    private DiagramaProcesoService diagramaProcesoService;

    @Autowired
    public DiagramaProcesoRestController(DiagramaProcesoService diagramaProcesoService) {
        this.diagramaProcesoService = diagramaProcesoService;
    }

    @GetMapping
    @Operation(summary = "Consultar diagrama del proceso", description = "Reúne los elementos activos del modelo: "
            + "pools, lanes, actividades, gateways, arcos (flujos de secuencia), eventos Message Throw, Message Catch "
            + "y envíos externos, y los flujos de mensaje entre pools. Disponible para la empresa propietaria y para "
            + "las empresas con las que se compartió el proceso.")
    public ResponseEntity<DiagramaProcesoDto> obtener(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(diagramaProcesoService.obtener(procesoId, principal.getName()));
    }
}
