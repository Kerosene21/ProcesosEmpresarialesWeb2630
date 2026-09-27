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

import co.edu.javeriana.procesosempresariales.dto.CrearEnvioExternoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarEnvioExternoDto;
import co.edu.javeriana.procesosempresariales.dto.EnvioExternoRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.EnvioExternoService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/envios-externos")
public class EnvioExternoRestController {

    private EnvioExternoService envioExternoService;

    @Autowired
    public EnvioExternoRestController(EnvioExternoService envioExternoService) {
        this.envioExternoService = envioExternoService;
    }

    @GetMapping
    public ResponseEntity<List<EnvioExternoRespuestaDto>> listar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(envioExternoService.listar(procesoId, principal.getName()));
    }

    @GetMapping("/{envioId}")
    public ResponseEntity<EnvioExternoRespuestaDto> obtener(@PathVariable("procesoId") Long procesoId,
            @PathVariable("envioId") Long envioId, Principal principal) {
        return ResponseEntity.ok(envioExternoService.obtener(procesoId, envioId, principal.getName()));
    }

    @PostMapping
    public ResponseEntity<EnvioExternoRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody CrearEnvioExternoDto dto, Principal principal) {
        EnvioExternoRespuestaDto creado = envioExternoService.crear(procesoId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{envioId}")
    public ResponseEntity<EnvioExternoRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("envioId") Long envioId, @Valid @RequestBody EditarEnvioExternoDto dto,
            Principal principal) {
        return ResponseEntity.ok(envioExternoService.editar(procesoId, envioId, dto, principal.getName()));
    }

    @GetMapping("/{envioId}/eliminacion")
    public ResponseEntity<EnvioExternoRespuestaDto> confirmarEliminacion(@PathVariable("procesoId") Long procesoId,
            @PathVariable("envioId") Long envioId, Principal principal) {
        return ResponseEntity.ok(envioExternoService.obtenerParaEliminar(procesoId, envioId, principal.getName()));
    }

    @DeleteMapping("/{envioId}")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("envioId") Long envioId, Principal principal) {
        envioExternoService.eliminar(procesoId, envioId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
