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

import co.edu.javeriana.procesosempresariales.dto.ArcoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearArcoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarArcoDto;
import co.edu.javeriana.procesosempresariales.service.ArcoService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/arcos")
@Tag(name = "Arcos", description = "Flujos de secuencia entre actividades, gateways y eventos de un mismo pool.")
public class ArcoRestController {

    private static final String REGLAS_DEL_ARCO = "Conecta dos nodos activos distintos del mismo pool; un Message "
            + "Catch de INICIO no admite arcos entrantes. Solo los arcos que salen de un gateway EXCLUSIVO o INCLUSIVO "
            + "llevan condición. Requiere rol ADMINISTRADOR o EDITOR de la empresa propietaria.";
    private static final String ARCO_NO_VALIDO = "Nodo inexistente o inactivo (`NODO_NO_VALIDO`), nodos en pools "
            + "distintos (`SECUENCIA_ENTRE_POOLS`), destino Message Catch de inicio (`CATCH_INICIO_CON_ENTRADA`), "
            + "condición no permitida u omitida (`CONDICION_NO_VALIDA`) o datos no válidos.";
    private static final String ARCO_DUPLICADO = "Ya existe un arco activo entre ese origen y ese destino "
            + "(`ARCO_DUPLICADO`).";

    private ArcoService arcoService;

    @Autowired
    public ArcoRestController(ArcoService arcoService) {
        this.arcoService = arcoService;
    }

    @GetMapping
    @Operation(summary = "Listar arcos del proceso", description = "Arcos activos con las coordenadas de sus "
            + "extremos.")
    public ResponseEntity<List<ArcoRespuestaDto>> listar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(arcoService.consultarActivos(procesoId, principal.getName()));
    }

    @GetMapping("/{arcoId}")
    @Operation(summary = "Consultar arco")
    public ResponseEntity<ArcoRespuestaDto> obtener(@PathVariable("procesoId") Long procesoId,
            @PathVariable("arcoId") Long arcoId, Principal principal) {
        return ResponseEntity.ok(arcoService.obtener(procesoId, arcoId, principal.getName()));
    }

    @PostMapping
    @Operation(summary = "Crear arco", description = REGLAS_DEL_ARCO)
    @ApiResponse(responseCode = "201", description = "Arco creado.")
    @ApiResponse(responseCode = "400", description = ARCO_NO_VALIDO)
    @ApiResponse(responseCode = "409", description = ARCO_DUPLICADO)
    public ResponseEntity<ArcoRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody CrearArcoDto dto, Principal principal) {
        ArcoRespuestaDto creado = arcoService.crear(procesoId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{arcoId}")
    @Operation(summary = "Editar arco", description = REGLAS_DEL_ARCO)
    @ApiResponse(responseCode = "200", description = "Arco actualizado.")
    @ApiResponse(responseCode = "400", description = ARCO_NO_VALIDO)
    @ApiResponse(responseCode = "409", description = ARCO_DUPLICADO)
    public ResponseEntity<ArcoRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("arcoId") Long arcoId, @Valid @RequestBody EditarArcoDto dto, Principal principal) {
        return ResponseEntity.ok(arcoService.editar(procesoId, arcoId, dto, principal.getName()));
    }

    @DeleteMapping("/{arcoId}")
    @Operation(summary = "Eliminar arco", description = "Solo ADMINISTRADOR.")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("arcoId") Long arcoId, Principal principal) {
        arcoService.eliminar(procesoId, arcoId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
