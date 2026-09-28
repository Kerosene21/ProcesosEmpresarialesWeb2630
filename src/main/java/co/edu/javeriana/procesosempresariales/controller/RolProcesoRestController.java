package co.edu.javeriana.procesosempresariales.controller;

import java.net.URI;
import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import co.edu.javeriana.procesosempresariales.dto.CrearRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroRolesProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialRolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoResumenDto;
import co.edu.javeriana.procesosempresariales.service.RolProcesoService;

@RestController
@RequestMapping("/api/roles-proceso")
public class RolProcesoRestController {

    private RolProcesoService rolProcesoService;

    @Autowired
    public RolProcesoRestController(RolProcesoService rolProcesoService) {
        this.rolProcesoService = rolProcesoService;
    }

    @PostMapping
    public ResponseEntity<RolProcesoRespuestaDto> crear(@Valid @RequestBody CrearRolProcesoDto dto,
            Principal principal) {
        RolProcesoRespuestaDto creado = rolProcesoService.crear(dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @GetMapping
    public ResponseEntity<PagedModel<RolProcesoResumenDto>> consultar(
            @ModelAttribute FiltroRolesProcesoDto filtro, Principal principal) {
        return ResponseEntity.ok(new PagedModel<>(rolProcesoService.consultar(filtro, principal.getName())));
    }

    @GetMapping("/{rolProcesoId}")
    public ResponseEntity<RolProcesoResumenDto> obtener(@PathVariable("rolProcesoId") Long rolProcesoId,
            Principal principal) {
        return ResponseEntity.ok(rolProcesoService.obtener(rolProcesoId, principal.getName()));
    }

    @PutMapping("/{rolProcesoId}")
    public ResponseEntity<RolProcesoRespuestaDto> editar(@PathVariable("rolProcesoId") Long rolProcesoId,
            @Valid @RequestBody EditarRolProcesoDto dto, Principal principal) {
        return ResponseEntity.ok(rolProcesoService.editar(rolProcesoId, dto, principal.getName()));
    }

    @GetMapping("/{rolProcesoId}/eliminacion")
    public ResponseEntity<RolProcesoResumenDto> confirmarEliminacion(
            @PathVariable("rolProcesoId") Long rolProcesoId, Principal principal) {
        return ResponseEntity.ok(rolProcesoService.obtenerParaEliminar(rolProcesoId, principal.getName()));
    }

    @DeleteMapping("/{rolProcesoId}")
    public ResponseEntity<Void> eliminar(@PathVariable("rolProcesoId") Long rolProcesoId, Principal principal) {
        rolProcesoService.eliminar(rolProcesoId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{rolProcesoId}/historial")
    public ResponseEntity<List<HistorialRolProcesoRespuestaDto>> historial(
            @PathVariable("rolProcesoId") Long rolProcesoId, Principal principal) {
        return ResponseEntity.ok(rolProcesoService.consultarHistorial(rolProcesoId, principal.getName()));
    }
}
