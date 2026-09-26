package co.edu.javeriana.procesosempresariales.controller;

import java.net.URI;
import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import co.edu.javeriana.procesosempresariales.dto.CrearGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.EditarGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.GatewayService;

@RestController
@RequestMapping("/api/procesos/{procesoId}/gateways")
public class GatewayRestController {

    private GatewayService gatewayService;

    @Autowired
    public GatewayRestController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping
    public ResponseEntity<GatewayRespuestaDto> crear(@PathVariable("procesoId") Long procesoId,
            @Valid @RequestBody CrearGatewayDto dto, Principal principal) {
        GatewayRespuestaDto creado = gatewayService.crear(procesoId, dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{gatewayId}")
    public ResponseEntity<GatewayRespuestaDto> editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("gatewayId") Long gatewayId, @Valid @RequestBody EditarGatewayDto dto,
            Principal principal) {
        return ResponseEntity.ok(gatewayService.editar(procesoId, gatewayId, dto, principal.getName()));
    }
}
