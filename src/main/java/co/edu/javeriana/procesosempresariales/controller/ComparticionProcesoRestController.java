package co.edu.javeriana.procesosempresariales.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.javeriana.procesosempresariales.dto.EmpresaInvitadaDto;
import co.edu.javeriana.procesosempresariales.service.ComparticionProcesoService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/compartido-con")
public class ComparticionProcesoRestController {

    private ComparticionProcesoService comparticionProcesoService;

    @Autowired
    public ComparticionProcesoRestController(ComparticionProcesoService comparticionProcesoService) {
        this.comparticionProcesoService = comparticionProcesoService;
    }

    @GetMapping
    public ResponseEntity<List<EmpresaInvitadaDto>> listar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(comparticionProcesoService.listar(procesoId, principal.getName()));
    }

    @PostMapping("/{empresaId}")
    public ResponseEntity<EmpresaInvitadaDto> compartir(@PathVariable("procesoId") Long procesoId,
            @PathVariable("empresaId") Long empresaId, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(comparticionProcesoService.compartir(procesoId, empresaId, principal.getName()));
    }

    @DeleteMapping("/{empresaId}")
    public ResponseEntity<Void> dejarDeCompartir(@PathVariable("procesoId") Long procesoId,
            @PathVariable("empresaId") Long empresaId, Principal principal) {
        comparticionProcesoService.dejarDeCompartir(procesoId, empresaId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
