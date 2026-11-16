package co.edu.javeriana.procesosempresariales.controller;

import java.net.URI;
import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

import co.edu.javeriana.procesosempresariales.dto.CambiarRolUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.UsuarioRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = "Usuarios de la empresa del administrador autenticado y su rol de acceso.")
public class UsuarioRestController {

    private static final String PROPIA_CUENTA = "Un administrador no puede cambiar su propio rol ni desactivar su "
            + "propia cuenta (`USUARIO_SIN_PERMISO`).";

    private UsuarioService usuarioService;

    @Autowired
    public UsuarioRestController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    @Operation(summary = "Listar usuarios de la empresa", description = "Usuarios activos e inactivos de la empresa "
            + "ordenados por correo. Solo ADMINISTRADOR.")
    public ResponseEntity<List<UsuarioRespuestaDto>> listar(Principal principal) {
        return ResponseEntity.ok(usuarioService.listarDeMiEmpresa(principal.getName()));
    }

    @GetMapping("/{usuarioId}")
    @Operation(summary = "Consultar usuario", description = "Solo usuarios de la empresa del administrador.")
    public ResponseEntity<UsuarioRespuestaDto> obtener(@PathVariable("usuarioId") Long usuarioId,
            Principal principal) {
        return ResponseEntity.ok(usuarioService.obtener(usuarioId, principal.getName()));
    }

    @PostMapping
    @Operation(summary = "Crear usuario", description = "Crea un usuario activo en la empresa del administrador; "
            + "la contraseña inicial se guarda cifrada con BCrypt. Solo ADMINISTRADOR.")
    @ApiResponse(responseCode = "201", description = "Usuario creado.")
    @ApiResponse(responseCode = "409", description = "El correo ya pertenece a otro usuario "
            + "(`USUARIO_CORREO_EN_USO`).")
    public ResponseEntity<UsuarioRespuestaDto> crear(@Valid @RequestBody CrearUsuarioDto dto, Principal principal) {
        UsuarioRespuestaDto creado = usuarioService.crear(dto, principal.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.getId()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @PutMapping("/{usuarioId}/rol")
    @Operation(summary = "Cambiar el rol de acceso", description = "Solo ADMINISTRADOR. " + PROPIA_CUENTA)
    public ResponseEntity<UsuarioRespuestaDto> cambiarRol(@PathVariable("usuarioId") Long usuarioId,
            @Valid @RequestBody CambiarRolUsuarioDto dto, Principal principal) {
        return ResponseEntity.ok(usuarioService.cambiarRol(usuarioId, dto, principal.getName()));
    }

    @PostMapping("/{usuarioId}/desactivacion")
    @Operation(summary = "Desactivar usuario", description = "Desactivación lógica: el usuario ya no puede iniciar "
            + "sesión y conserva su historial. Solo ADMINISTRADOR. " + PROPIA_CUENTA)
    public ResponseEntity<UsuarioRespuestaDto> desactivar(@PathVariable("usuarioId") Long usuarioId,
            Principal principal) {
        return ResponseEntity.ok(usuarioService.desactivar(usuarioId, principal.getName()));
    }
}
