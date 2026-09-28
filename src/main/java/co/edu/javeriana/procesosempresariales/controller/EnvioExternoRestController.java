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

import co.edu.javeriana.procesosempresariales.dto.CrearEnvioExternoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarEnvioExternoDto;
import co.edu.javeriana.procesosempresariales.dto.EnvioExternoRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.EnvioExternoService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/envios-externos")
@Tag(name = "Envíos externos", description = "Modelado de envíos hacia un sistema externo, representado por un pool "
        + "EXTERNO caja negra, por CORREO, SERVICIO_WEB o COLA. Solo se documenta el envío: el sistema no hace "
        + "llamadas reales ni guarda credenciales.")
public class EnvioExternoRestController {

    private static final String DESTINO_NO_VALIDO = "El pool destino no es un pool EXTERNO caja negra "
            + "(`ENVIO_EXTERNO_NO_VALIDO`), no pertenece al proceso (`POOL_MENSAJE_NO_VALIDO`), coincide con el pool "
            + "origen (`MENSAJE_ENTRE_MISMO_POOL`) o los datos no son válidos.";

    private EnvioExternoService envioExternoService;

    @Autowired
    public EnvioExternoRestController(EnvioExternoService envioExternoService) {
        this.envioExternoService = envioExternoService;
    }

    @GetMapping
    @Operation(summary = "Listar envíos externos del proceso")
    public ResponseEntity<List<EnvioExternoRespuestaDto>> listar(@PathVariable("procesoId") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(envioExternoService.listar(procesoId, principal.getName()));
    }

    @GetMapping("/{envioId}")
    @Operation(summary = "Consultar envío externo")
    public ResponseEntity<EnvioExternoRespuestaDto> obtener(@PathVariable("procesoId") Long procesoId,
            @PathVariable("envioId") Long envioId, Principal principal) {
        return ResponseEntity.ok(envioExternoService.obtener(procesoId, envioId, principal.getName()));
    }

    @PostMapping
    @Operation(summary = "Crear envío externo", description = "Documenta qué datos se envían, en qué momento del "
            + "proceso, por qué medio y qué hacer si el envío falla. No se ejecuta ningún envío. Requiere rol "
            + "ADMINISTRADOR o EDITOR de la empresa propietaria.")
    @ApiResponse(responseCode = "201", description = "Envío externo modelado.")
    @ApiResponse(responseCode = "400", description = DESTINO_NO_VALIDO)
    @ApiResponse(responseCode = "409", description = "El pool origen es una caja negra (`POOL_CAJA_NEGRA`).")
    public ResponseEntity<EnvioExternoRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody CrearEnvioExternoDto dto, Principal principal) {
        EnvioExternoRespuestaDto creado = envioExternoService.crear(procesoId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{envioId}")
    @Operation(summary = "Editar envío externo", description = "El pool origen no cambia. Requiere rol ADMINISTRADOR "
            + "o EDITOR de la empresa propietaria.")
    @ApiResponse(responseCode = "200", description = "Envío externo actualizado.")
    @ApiResponse(responseCode = "400", description = DESTINO_NO_VALIDO)
    public ResponseEntity<EnvioExternoRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("envioId") Long envioId, @Valid @RequestBody EditarEnvioExternoDto dto,
            Principal principal) {
        return ResponseEntity.ok(envioExternoService.editar(procesoId, envioId, dto, principal.getName()));
    }

    @GetMapping("/{envioId}/eliminacion")
    @Operation(summary = "Previsualizar la eliminación de un envío externo", description = "Devuelve el envío con "
            + "las advertencias sobre los arcos que se desactivarían. Solo ADMINISTRADOR.")
    public ResponseEntity<EnvioExternoRespuestaDto> confirmarEliminacion(@PathVariable("procesoId") Long procesoId,
            @PathVariable("envioId") Long envioId, Principal principal) {
        return ResponseEntity.ok(envioExternoService.obtenerParaEliminar(procesoId, envioId, principal.getName()));
    }

    @DeleteMapping("/{envioId}")
    @Operation(summary = "Eliminar envío externo", description = "Desactiva el envío y los arcos conectados a él. "
            + "Solo ADMINISTRADOR.")
    public ResponseEntity<Void> eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("envioId") Long envioId, Principal principal) {
        envioExternoService.eliminar(procesoId, envioId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
