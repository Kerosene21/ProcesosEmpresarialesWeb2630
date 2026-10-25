package co.edu.javeriana.procesosempresariales.controller;

import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import co.edu.javeriana.procesosempresariales.dto.RecolocarNodosDto;
import co.edu.javeriana.procesosempresariales.service.RecolocacionNodosService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/diagrama/posiciones")
@Tag(name = "Diagrama")
public class RecolocacionNodosRestController {

    private RecolocacionNodosService recolocacionNodosService;

    @Autowired
    public RecolocacionNodosRestController(RecolocacionNodosService recolocacionNodosService) {
        this.recolocacionNodosService = recolocacionNodosService;
    }

    @PutMapping
    @Operation(summary = "Recolocar nodos del diagrama", description = "Guarda en una sola operación la nueva posición "
            + "de varias actividades, gateways y eventos, por ejemplo para que acompañen a su lane al reordenarla. No "
            + "cambia lanes ni pools. Requiere rol ADMINISTRADOR o EDITOR de la empresa propietaria.")
    @ApiResponse(responseCode = "204", description = "Posiciones guardadas.")
    @ApiResponse(responseCode = "400", description = "Lista vacía o posición fuera de rango (`DATOS_NO_VALIDOS`).")
    @ApiResponse(responseCode = "404", description = "Algún nodo no existe en el proceso; no se aplica ningún cambio.")
    public ResponseEntity<Void> recolocar(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody RecolocarNodosDto dto, Principal principal) {
        recolocacionNodosService.recolocar(procesoId, dto, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
