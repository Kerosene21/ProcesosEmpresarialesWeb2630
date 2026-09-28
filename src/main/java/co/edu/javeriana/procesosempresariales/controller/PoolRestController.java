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

import co.edu.javeriana.procesosempresariales.dto.CrearPoolDto;
import co.edu.javeriana.procesosempresariales.dto.EditarPoolDto;
import co.edu.javeriana.procesosempresariales.dto.PoolRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.PoolService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/pools")
public class PoolRestController {

    private PoolService poolService;

    @Autowired
    public PoolRestController(PoolService poolService) {
        this.poolService = poolService;
    }

    @GetMapping
    public ResponseEntity<List<PoolRespuestaDto>> listar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(poolService.listar(procesoId, principal.getName()));
    }

    @GetMapping("/{poolId}")
    public ResponseEntity<PoolRespuestaDto> obtener(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, Principal principal) {
        return ResponseEntity.ok(poolService.obtener(procesoId, poolId, principal.getName()));
    }

    @PostMapping
    public ResponseEntity<PoolRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody CrearPoolDto dto, Principal principal) {
        PoolRespuestaDto creado = poolService.crear(procesoId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{poolId}")
    public ResponseEntity<PoolRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, @Valid @RequestBody EditarPoolDto dto, Principal principal) {
        return ResponseEntity.ok(poolService.editar(procesoId, poolId, dto, principal.getName()));
    }

    @DeleteMapping("/{poolId}")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, Principal principal) {
        poolService.eliminar(procesoId, poolId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{poolId}/roles-disponibles")
    public ResponseEntity<List<RolProcesoRespuestaDto>> rolesDisponibles(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, Principal principal) {
        return ResponseEntity.ok(poolService.rolesDisponibles(procesoId, poolId, principal.getName()));
    }
}
