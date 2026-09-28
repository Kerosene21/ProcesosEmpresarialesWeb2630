package co.edu.javeriana.procesosempresariales.controller;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.javeriana.procesosempresariales.dto.DiagramaProcesoDto;
import co.edu.javeriana.procesosempresariales.service.DiagramaProcesoService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/diagrama")
public class DiagramaProcesoRestController {

    private DiagramaProcesoService diagramaProcesoService;

    @Autowired
    public DiagramaProcesoRestController(DiagramaProcesoService diagramaProcesoService) {
        this.diagramaProcesoService = diagramaProcesoService;
    }

    @GetMapping
    public ResponseEntity<DiagramaProcesoDto> obtener(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(diagramaProcesoService.obtener(procesoId, principal.getName()));
    }
}
