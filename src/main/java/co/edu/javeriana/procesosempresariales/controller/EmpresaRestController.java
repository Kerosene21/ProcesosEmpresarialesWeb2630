package co.edu.javeriana.procesosempresariales.controller;

import java.security.Principal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.javeriana.procesosempresariales.dto.EmpresaRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;

@RestController
@RequestMapping("/api/empresas")
@Tag(name = "Empresas", description = "Registro público de empresas y consulta de la empresa del usuario "
        + "autenticado.")
public class EmpresaRestController {

    private EmpresaService empresaService;

    @Autowired
    public EmpresaRestController(EmpresaService empresaService) {
        this.empresaService = empresaService;
    }

    @PostMapping
    @Operation(summary = "Registrar empresa", description = "Registra la empresa y crea su usuario administrador "
            + "inicial con el correo de contacto. No requiere sesión, pero sí el encabezado `X-XSRF-TOKEN`.")
    @ApiResponse(responseCode = "201", description = "Empresa registrada con su administrador inicial.")
    @ApiResponse(responseCode = "409", description = "El NIT ya está registrado (`EMPRESA_NIT_DUPLICADO`) o el "
            + "correo ya pertenece a otro usuario (`USUARIO_CORREO_EN_USO`).")
    public ResponseEntity<EmpresaRespuestaDto> registrar(@Valid @RequestBody RegistroEmpresaDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(empresaService.registrar(dto));
    }

    @GetMapping("/actual")
    @Operation(summary = "Consultar mi empresa", description = "Empresa a la que pertenece el usuario autenticado.")
    public ResponseEntity<EmpresaRespuestaDto> actual(Principal principal) {
        EmpresaRespuestaDto propia = empresaService.listarVisiblesPara(principal.getName()).getFirst();
        return ResponseEntity.ok(empresaService.obtenerParaUsuario(propia.getId(), principal.getName()));
    }
}
