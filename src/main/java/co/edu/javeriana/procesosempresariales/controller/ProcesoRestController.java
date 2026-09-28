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

import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroProcesosDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoResumenDto;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;

@RestController
@RequestMapping("/api/procesos")
@Tag(name = "Procesos", description = "Procesos de la empresa del usuario autenticado. Los procesos que otras "
        + "empresas comparten se consultan en solo lectura.")
public class ProcesoRestController {
    private ProcesoService procesoService;

    @Autowired
    public ProcesoRestController(ProcesoService procesoService) {
        this.procesoService = procesoService;
    }

    @GetMapping
    @Operation(summary = "Listar procesos", description = "Página de 10 procesos ordenados por nombre. Por defecto "
            + "devuelve los procesos activos propios; con `alcance` COMPARTIDOS o TODOS incluye los procesos que otras "
            + "empresas compartieron, marcados con `soloLectura=true`.")
    public ResponseEntity<PagedModel<ProcesoResumenDto>> consultar(@ModelAttribute FiltroProcesosDto filtro,
            Principal principal) {
        return ResponseEntity.ok(new PagedModel<>(procesoService.consultarProcesos(filtro, principal.getName())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar proceso", description = "Devuelve un proceso propio o compartido con la empresa "
            + "del usuario. En un proceso compartido `soloLectura` es `true`.")
    public ResponseEntity<ProcesoRespuestaDto> obtener(@PathVariable("id") Long procesoId, Principal principal) {
        return ResponseEntity.ok(procesoService.obtenerVisible(procesoId, principal.getName()));
    }

    @GetMapping("/{id}/historial")
    @Operation(summary = "Consultar historial del proceso", description = "Cambios registrados sobre el proceso y "
            + "su modelo. Solo para la empresa propietaria.")
    public ResponseEntity<List<HistorialProcesoRespuestaDto>> historial(@PathVariable("id") Long procesoId,
            Principal principal) {
        return ResponseEntity.ok(procesoService.consultarHistorial(procesoId, principal.getName()));
    }

    @PostMapping
    @Operation(summary = "Crear proceso", description = "Crea el proceso en estado BORRADOR con su pool propietario "
            + "y una lane inicial. Requiere rol ADMINISTRADOR o EDITOR.")
    @ApiResponse(responseCode = "201", description = "Proceso creado en estado BORRADOR.")
    @ApiResponse(responseCode = "409", description = "Ya existe un proceso con ese nombre en la empresa "
            + "(`PROCESO_NOMBRE_DUPLICADO`).")
    public ResponseEntity<ProcesoRespuestaDto> crear(@Valid @RequestBody CrearProcesoDto dto, Principal principal) {
        ProcesoRespuestaDto creado = procesoService.crear(dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar proceso", description = "Actualiza nombre, descripción, categoría y estado. Para "
            + "salir de BORRADOR el modelo debe superar la validación (`MODELO_NO_VALIDO`). Requiere rol "
            + "ADMINISTRADOR o EDITOR de la empresa propietaria.")
    @ApiResponse(responseCode = "200", description = "Proceso actualizado.")
    @ApiResponse(responseCode = "409", description = "Ya existe un proceso con ese nombre en la empresa "
            + "(`PROCESO_NOMBRE_DUPLICADO`).")
    public ResponseEntity<ProcesoRespuestaDto> editar(@PathVariable("id") Long procesoId,
            @Valid @RequestBody EditarProcesoDto dto, Principal principal) {
        return ResponseEntity.ok(procesoService.editar(procesoId, dto, principal.getName()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar proceso", description = "Eliminación lógica registrada en el historial. Solo "
            + "ADMINISTRADOR de la empresa propietaria.")
    public ResponseEntity<Void> eliminar(@PathVariable("id") Long procesoId, Principal principal) {
        procesoService.eliminar(procesoId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
