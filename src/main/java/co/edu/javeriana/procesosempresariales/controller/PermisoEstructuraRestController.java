package co.edu.javeriana.procesosempresariales.controller;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.dto.ConfigurarPermisoEstructuraDto;
import co.edu.javeriana.procesosempresariales.dto.PermisoEstructuraDto;
import co.edu.javeriana.procesosempresariales.service.PermisoEstructuraService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/permisos-estructura")
public class PermisoEstructuraRestController {

    private PermisoEstructuraService permisoEstructuraService;

    @Autowired
    public PermisoEstructuraRestController(PermisoEstructuraService permisoEstructuraService) {
        this.permisoEstructuraService = permisoEstructuraService;
    }

    @GetMapping
    public ResponseEntity<List<PermisoEstructuraDto>> consultar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(permisoEstructuraService.consultar(procesoId, principal.getName()));
    }

    @PutMapping("/{rol}")
    public ResponseEntity<List<PermisoEstructuraDto>> configurar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("rol") RolUsuario rol, @Valid @RequestBody ConfigurarPermisoEstructuraDto dto,
            Principal principal) {
        return ResponseEntity.ok(permisoEstructuraService.configurar(procesoId, rol, dto, principal.getName()));
    }
}
