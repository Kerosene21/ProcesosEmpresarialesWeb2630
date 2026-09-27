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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import co.edu.javeriana.procesosempresariales.dto.CrearRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroRolesProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialRolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoResumenDto;
import co.edu.javeriana.procesosempresariales.service.RolProcesoService;

@RestController
@RequestMapping("/api/roles-proceso")
@Tag(name = "Roles de proceso", description = "Catálogo de roles de proceso de la empresa del usuario, que se "
        + "asignan a las lanes de los pools.")
public class RolProcesoRestController {

    private static final String NOMBRE_DUPLICADO = "Ya existe un rol de proceso con ese nombre en la empresa "
            + "(`ROL_PROCESO_NOMBRE_DUPLICADO`).";

    private RolProcesoService rolProcesoService;

    @Autowired
    public RolProcesoRestController(RolProcesoService rolProcesoService) {
        this.rolProcesoService = rolProcesoService;
    }

    @PostMapping
    @Operation(summary = "Crear rol de proceso", description = "Solo ADMINISTRADOR.")
    @ApiResponse(responseCode = "201", description = "Rol de proceso creado.")
    @ApiResponse(responseCode = "409", description = NOMBRE_DUPLICADO)
    public ResponseEntity<RolProcesoRespuestaDto> crear(@Valid @RequestBody CrearRolProcesoDto dto,
            Principal principal) {
        RolProcesoRespuestaDto creado = rolProcesoService.crear(dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @GetMapping
    @Operation(summary = "Listar roles de proceso", description = "Página de 10 roles de la empresa ordenados por "
            + "nombre, con los procesos que los usan. Por defecto solo los activos.")
    public ResponseEntity<PagedModel<RolProcesoResumenDto>> consultar(
            @ModelAttribute FiltroRolesProcesoDto filtro, Principal principal) {
        return ResponseEntity.ok(new PagedModel<>(rolProcesoService.consultar(filtro, principal.getName())));
    }

    @GetMapping("/{rolProcesoId}")
    @Operation(summary = "Consultar rol de proceso", description = "Incluye los procesos cuyas lanes usan el rol.")
    public ResponseEntity<RolProcesoResumenDto> obtener(@PathVariable("rolProcesoId") Long rolProcesoId,
            Principal principal) {
        return ResponseEntity.ok(rolProcesoService.obtener(rolProcesoId, principal.getName()));
    }

    @PutMapping("/{rolProcesoId}")
    @Operation(summary = "Editar rol de proceso", description = "Requiere rol ADMINISTRADOR o EDITOR.")
    @ApiResponse(responseCode = "200", description = "Rol de proceso actualizado.")
    @ApiResponse(responseCode = "409", description = NOMBRE_DUPLICADO)
    public ResponseEntity<RolProcesoRespuestaDto> editar(@PathVariable("rolProcesoId") Long rolProcesoId,
            @Valid @RequestBody EditarRolProcesoDto dto, Principal principal) {
        return ResponseEntity.ok(rolProcesoService.editar(rolProcesoId, dto, principal.getName()));
    }

    @GetMapping("/{rolProcesoId}/eliminacion")
    @Operation(summary = "Previsualizar la eliminación de un rol de proceso", description = "Devuelve el rol con "
            + "los procesos que lo usan para confirmar la eliminación. Solo ADMINISTRADOR.")
    public ResponseEntity<RolProcesoResumenDto> confirmarEliminacion(
            @PathVariable("rolProcesoId") Long rolProcesoId, Principal principal) {
        return ResponseEntity.ok(rolProcesoService.obtenerParaEliminar(rolProcesoId, principal.getName()));
    }

    @DeleteMapping("/{rolProcesoId}")
    @Operation(summary = "Eliminar rol de proceso", description = "Eliminación lógica. Solo ADMINISTRADOR.")
    @ApiResponse(responseCode = "409", description = "Lanes activas usan el rol (`ROL_PROCESO_EN_USO`); la "
            + "respuesta incluye `procesos`.")
    public ResponseEntity<Void> eliminar(@PathVariable("rolProcesoId") Long rolProcesoId, Principal principal) {
        rolProcesoService.eliminar(rolProcesoId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{rolProcesoId}/historial")
    @Operation(summary = "Consultar historial del rol de proceso", description = "Creación, ediciones y "
            + "eliminación del rol.")
    public ResponseEntity<List<HistorialRolProcesoRespuestaDto>> historial(
            @PathVariable("rolProcesoId") Long rolProcesoId, Principal principal) {
        return ResponseEntity.ok(rolProcesoService.consultarHistorial(rolProcesoId, principal.getName()));
    }
}
