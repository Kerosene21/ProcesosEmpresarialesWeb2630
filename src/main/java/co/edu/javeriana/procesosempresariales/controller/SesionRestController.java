package co.edu.javeriana.procesosempresariales.controller;

import java.security.Principal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.javeriana.procesosempresariales.dto.SesionDto;
import co.edu.javeriana.procesosempresariales.service.SesionService;

@RestController
@RequestMapping("/api/sesion")
@Tag(name = "Sesión", description = "Sesión del navegador para clientes SPA. `POST /api/sesion` inicia sesión con "
        + "los campos `correo` y `password` de un formulario `application/x-www-form-urlencoded` y responde 204 o "
        + "401 (`CREDENCIALES_INVALIDAS`). `DELETE /api/sesion` cierra la sesión y responde 204. Ambas exigen el "
        + "encabezado `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN`.")
public class SesionRestController {

    private SesionService sesionService;

    @Autowired
    public SesionRestController(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    @GetMapping
    @Operation(summary = "Consultar la sesión", description = "Indica si hay una sesión autenticada y, en ese caso, "
            + "el correo, el rol de acceso y la empresa del usuario. También emite la cookie `XSRF-TOKEN` que el "
            + "cliente debe reenviar en el encabezado `X-XSRF-TOKEN` de sus operaciones de escritura.")
    public ResponseEntity<SesionDto> consultar(Principal principal, @Parameter(hidden = true) CsrfToken csrfToken) {
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        return ResponseEntity.ok(principal == null ? new SesionDto() : sesionService.consultar(principal.getName()));
    }
}
