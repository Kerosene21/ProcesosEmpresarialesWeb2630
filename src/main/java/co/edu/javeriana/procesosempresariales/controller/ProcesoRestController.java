package co.edu.javeriana.procesosempresariales.controller;

import java.net.URI;
import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;

@RestController
@RequestMapping("/api/procesos")
public class ProcesoRestController {
    private final ProcesoService procesoService;

    public ProcesoRestController(ProcesoService procesoService) {
        this.procesoService = procesoService;
    }

    @PostMapping
    public ResponseEntity<ProcesoRespuestaDto> crear(@Valid @RequestBody CrearProcesoDto dto, Principal principal) {
        // La empresa se toma de la sesión, nunca del JSON que manda el cliente.
        if (principal == null) {
            throw new co.edu.javeriana.procesosempresariales.exception.UsuarioNoAutorizadoException(
                    "Se requiere autenticación para crear un proceso");
        }
        ProcesoRespuestaDto creado = procesoService.crear(dto, principal.getName());
        // Indicamos en qué URL quedó el proceso nuevo.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProcesoRespuestaDto> editar(@PathVariable("id") Long procesoId,
            @Valid @RequestBody EditarProcesoDto dto, Principal principal) {
        if (principal == null) {
            throw new co.edu.javeriana.procesosempresariales.exception.UsuarioNoAutorizadoException(
                    "Se requiere autenticación para editar un proceso");
        }
        return ResponseEntity.ok(procesoService.editar(procesoId, dto, principal.getName()));
    }
}