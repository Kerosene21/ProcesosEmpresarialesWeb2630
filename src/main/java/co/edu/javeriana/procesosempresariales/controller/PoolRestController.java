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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import co.edu.javeriana.procesosempresariales.dto.CrearPoolDto;
import co.edu.javeriana.procesosempresariales.dto.EditarPoolDto;
import co.edu.javeriana.procesosempresariales.dto.PoolRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.PoolService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/pools")
@Tag(name = "Pools", description = "Pools del diagrama. El PROPIETARIO se crea con el proceso; un PARTICIPANTE "
        + "representa otra empresa registrada y un EXTERNO a un participante no registrado. Un pool caja negra no "
        + "tiene elementos internos. Las escrituras dependen de los permisos de estructura del rol.")
public class PoolRestController {

    private static final String POOL_NO_VALIDO = "Tipo, caja negra o empresa participante no válidos para el pool "
            + "(`POOL_NO_VALIDO`).";

    private PoolService poolService;

    @Autowired
    public PoolRestController(PoolService poolService) {
        this.poolService = poolService;
    }

    @GetMapping
    @Operation(summary = "Listar pools del proceso", description = "Pools activos en su orden de presentación.")
    public ResponseEntity<List<PoolRespuestaDto>> listar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(poolService.listar(procesoId, principal.getName()));
    }

    @GetMapping("/{poolId}")
    @Operation(summary = "Consultar pool")
    public ResponseEntity<PoolRespuestaDto> obtener(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, Principal principal) {
        return ResponseEntity.ok(poolService.obtener(procesoId, poolId, principal.getName()));
    }

    @PostMapping
    @Operation(summary = "Crear pool", description = "Crea un pool PARTICIPANTE o EXTERNO; el PROPIETARIO ya existe. "
            + "Requiere el permiso de estructura `crear pools`.")
    @ApiResponse(responseCode = "201", description = "Pool creado.")
    @ApiResponse(responseCode = "400", description = POOL_NO_VALIDO)
    public ResponseEntity<PoolRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody CrearPoolDto dto, Principal principal) {
        PoolRespuestaDto creado = poolService.crear(procesoId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{poolId}")
    @Operation(summary = "Editar pool", description = "Cambia el nombre, la condición de caja negra y la empresa "
            + "participante. Requiere el permiso de estructura `editar pools`.")
    @ApiResponse(responseCode = "200", description = "Pool actualizado.")
    @ApiResponse(responseCode = "400", description = POOL_NO_VALIDO)
    @ApiResponse(responseCode = "409", description = "El pool tiene lanes, actividades, gateways o eventos y no "
            + "puede convertirse en caja negra (`POOL_CON_CONTENIDO`).")
    public ResponseEntity<PoolRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, @Valid @RequestBody EditarPoolDto dto, Principal principal) {
        return ResponseEntity.ok(poolService.editar(procesoId, poolId, dto, principal.getName()));
    }

    @DeleteMapping("/{poolId}")
    @Operation(summary = "Eliminar pool", description = "Desactiva el pool y reordena los restantes. El pool "
            + "propietario no se elimina. Requiere el permiso de estructura `eliminar pools`.")
    @ApiResponse(responseCode = "400", description = "El pool propietario no se puede eliminar (`POOL_NO_VALIDO`).")
    @ApiResponse(responseCode = "409", description = "El pool tiene contenido o es destino de flujos de mensaje "
            + "activos (`POOL_CON_CONTENIDO`).")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, Principal principal) {
        poolService.eliminar(procesoId, poolId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{poolId}/roles-disponibles")
    @Operation(summary = "Listar roles disponibles para las lanes del pool", description = "Roles de proceso activos "
            + "de la empresa propietaria que pueden asignarse a una lane. Solo para la empresa propietaria.")
    @ApiResponse(responseCode = "200", description = "Roles de proceso disponibles para las lanes.")
    @ApiResponse(responseCode = "409", description = "El pool es una caja negra y no admite lanes "
            + "(`POOL_CAJA_NEGRA`).")
    public ResponseEntity<List<RolProcesoRespuestaDto>> rolesDisponibles(@PathVariable("procesoId") Long procesoId,
            @PathVariable("poolId") Long poolId, Principal principal) {
        return ResponseEntity.ok(poolService.rolesDisponibles(procesoId, poolId, principal.getName()));
    }
}
