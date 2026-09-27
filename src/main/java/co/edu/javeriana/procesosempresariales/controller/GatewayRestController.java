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

import co.edu.javeriana.procesosempresariales.dto.CrearGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.EditarGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.GatewayService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/gateways")
@Tag(name = "Gateways", description = "Gateways EXCLUSIVO (X), PARALELO (+) e INCLUSIVO (O) que bifurcan o unen "
        + "flujos de secuencia.")
public class GatewayRestController {

    private GatewayService gatewayService;

    @Autowired
    public GatewayRestController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @GetMapping
    @Operation(summary = "Listar gateways del proceso", description = "Gateways activos con sus advertencias de "
            + "modelado.")
    public ResponseEntity<List<GatewayRespuestaDto>> listar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(gatewayService.consultarActivos(procesoId, principal.getName()));
    }

    @GetMapping("/{gatewayId}")
    @Operation(summary = "Consultar gateway")
    public ResponseEntity<GatewayRespuestaDto> obtener(@PathVariable("procesoId") Long procesoId,
            @PathVariable("gatewayId") Long gatewayId, Principal principal) {
        return ResponseEntity.ok(gatewayService.obtener(procesoId, gatewayId, principal.getName()));
    }

    @PostMapping
    @Operation(summary = "Crear gateway", description = "`poolId` es obligatorio cuando el proceso tiene más de un "
            + "pool activo (`POOL_NO_VALIDO`). Requiere rol ADMINISTRADOR o EDITOR de la empresa propietaria.")
    @ApiResponse(responseCode = "201", description = "Gateway creado.")
    @ApiResponse(responseCode = "409", description = "El pool es una caja negra (`POOL_CAJA_NEGRA`).")
    public ResponseEntity<GatewayRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody CrearGatewayDto dto, Principal principal) {
        GatewayRespuestaDto creado = gatewayService.crear(procesoId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{gatewayId}")
    @Operation(summary = "Editar gateway", description = "Cambia el tipo y las condiciones de los arcos salientes. "
            + "EXCLUSIVO e INCLUSIVO exigen condición en cada salida (`CONDICION_NO_VALIDA`); al pasar a PARALELO se "
            + "quitan. Requiere rol ADMINISTRADOR o EDITOR de la empresa propietaria.")
    public ResponseEntity<GatewayRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("gatewayId") Long gatewayId, @Valid @RequestBody EditarGatewayDto dto,
            Principal principal) {
        return ResponseEntity.ok(gatewayService.editar(procesoId, gatewayId, dto, principal.getName()));
    }

    @GetMapping("/{gatewayId}/eliminacion")
    @Operation(summary = "Previsualizar la eliminación de un gateway", description = "Devuelve el gateway con las "
            + "advertencias sobre los arcos que se desactivarían. Solo ADMINISTRADOR.")
    public ResponseEntity<GatewayRespuestaDto> confirmarEliminacion(@PathVariable("procesoId") Long procesoId,
            @PathVariable("gatewayId") Long gatewayId, Principal principal) {
        return ResponseEntity.ok(gatewayService.obtenerParaEliminar(procesoId, gatewayId, principal.getName()));
    }

    @DeleteMapping("/{gatewayId}")
    @Operation(summary = "Eliminar gateway", description = "Desactiva el gateway y los arcos conectados a él. Solo "
            + "ADMINISTRADOR.")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("gatewayId") Long gatewayId, Principal principal) {
        gatewayService.eliminar(procesoId, gatewayId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
