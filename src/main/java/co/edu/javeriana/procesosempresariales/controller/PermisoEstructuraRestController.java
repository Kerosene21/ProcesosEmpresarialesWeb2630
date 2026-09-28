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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.dto.ConfigurarPermisoEstructuraDto;
import co.edu.javeriana.procesosempresariales.dto.PermisoEstructuraDto;
import co.edu.javeriana.procesosempresariales.service.PermisoEstructuraService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/permisos-estructura")
@Tag(name = "Permisos de estructura", description = "Permisos por rol de usuario para crear, editar y eliminar pools "
        + "y lanes de un proceso.")
public class PermisoEstructuraRestController {

    private static final String ROL_DE_ACCESO = "Rol de acceso del usuario cuyos permisos se configuran (no es un "
            + "rol de proceso).";

    private PermisoEstructuraService permisoEstructuraService;

    @Autowired
    public PermisoEstructuraRestController(PermisoEstructuraService permisoEstructuraService) {
        this.permisoEstructuraService = permisoEstructuraService;
    }

    @GetMapping
    @Operation(summary = "Consultar permisos de estructura", description = "Matriz por rol: ADMINISTRADOR tiene "
            + "todos los permisos, SOLO_LECTURA ninguno y EDITOR es configurable. Solo para la empresa propietaria.")
    public ResponseEntity<List<PermisoEstructuraDto>> consultar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(permisoEstructuraService.consultar(procesoId, principal.getName()));
    }

    @PutMapping("/{rol}")
    @Operation(summary = "Configurar permisos de estructura", description = "Solo el rol EDITOR es configurable; "
            + "para ADMINISTRADOR y SOLO_LECTURA solo se aceptan sus valores fijos. Devuelve la matriz actualizada. "
            + "Solo ADMINISTRADOR de la empresa propietaria.")
    @ApiResponse(responseCode = "200", description = "Matriz de permisos actualizada.")
    @ApiResponse(responseCode = "400", description = "Se intentó cambiar un permiso fijo "
            + "(`PERMISO_ESTRUCTURA_NO_VALIDO`), el rol no existe o los datos no son válidos.")
    public ResponseEntity<List<PermisoEstructuraDto>> configurar(@PathVariable("procesoId") Long procesoId,
            @Parameter(description = ROL_DE_ACCESO) @PathVariable("rol") RolUsuario rol,
            @Valid @RequestBody ConfigurarPermisoEstructuraDto dto, Principal principal) {
        return ResponseEntity.ok(permisoEstructuraService.configurar(procesoId, rol, dto, principal.getName()));
    }
}
