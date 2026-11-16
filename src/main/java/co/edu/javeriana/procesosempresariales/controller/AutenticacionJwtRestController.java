package co.edu.javeriana.procesosempresariales.controller;

import java.security.Principal;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.javeriana.procesosempresariales.config.CookieRefresco;
import co.edu.javeriana.procesosempresariales.dto.InicioSesionDto;
import co.edu.javeriana.procesosempresariales.dto.SesionDto;
import co.edu.javeriana.procesosempresariales.dto.TokenAccesoDto;
import co.edu.javeriana.procesosempresariales.exception.SesionExpiradaException;
import co.edu.javeriana.procesosempresariales.service.AutenticacionJwtService;
import co.edu.javeriana.procesosempresariales.service.SesionJwt;
import co.edu.javeriana.procesosempresariales.service.SesionService;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación JWT", description = "Inicio de sesión con token de acceso JWT (15 minutos, en "
        + "`Authorization: Bearer`) y refresh token en la cookie HttpOnly `PE_REFRESH`, limitada a `/api/auth`. "
        + "`POST` exige el encabezado `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN` que emite "
        + "`GET /api/auth/csrf`.")
public class AutenticacionJwtRestController {

    private static final String TIPO_TOKEN = "Bearer";

    private AutenticacionJwtService autenticacionJwtService;
    private SesionService sesionService;
    private CookieRefresco cookieRefresco;

    @Autowired
    public AutenticacionJwtRestController(AutenticacionJwtService autenticacionJwtService,
            SesionService sesionService, CookieRefresco cookieRefresco) {
        this.autenticacionJwtService = autenticacionJwtService;
        this.sesionService = sesionService;
        this.cookieRefresco = cookieRefresco;
    }

    @GetMapping("/csrf")
    @Operation(summary = "Obtener el token CSRF", description = "Emite la cookie `XSRF-TOKEN`, legible por la SPA, "
            + "que debe reenviarse en `X-XSRF-TOKEN` en login, refresh y logout.")
    @ApiResponse(responseCode = "204", description = "Cookie `XSRF-TOKEN` emitida.")
    public ResponseEntity<Void> csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión con JWT", description = "Valida las credenciales y responde el token de "
            + "acceso y los datos del usuario. El refresh token se entrega solo en la cookie HttpOnly `PE_REFRESH`.")
    @ApiResponse(responseCode = "200", description = "Sesión iniciada.")
    @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos, o usuario desactivado "
            + "(`CREDENCIALES_INVALIDAS`).")
    public ResponseEntity<TokenAccesoDto> iniciarSesion(@Valid @RequestBody InicioSesionDto dto) {
        return respuesta(autenticacionJwtService.iniciarSesion(dto.getCorreo(), dto.getPassword()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar el token de acceso", description = "Usa la cookie `PE_REFRESH`, la rota y responde "
            + "un token de acceso nuevo. Reutilizar un refresh token ya rotado revoca toda la sesión.")
    @ApiResponse(responseCode = "200", description = "Token de acceso renovado.")
    @ApiResponse(responseCode = "401", description = "Refresh token ausente, vencido, revocado o reutilizado "
            + "(`SESION_EXPIRADA`).")
    public ResponseEntity<TokenAccesoDto> refrescar(HttpServletRequest request) {
        String codigo = cookieRefresco.leer(request)
                .orElseThrow(() -> new SesionExpiradaException("La sesión expiró; inicia sesión de nuevo"));
        return respuesta(autenticacionJwtService.refrescar(codigo));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión", description = "Revoca la sesión de la cookie `PE_REFRESH` y la elimina. "
            + "No necesita un token de acceso vigente.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada.")
    public ResponseEntity<Void> cerrarSesion(HttpServletRequest request) {
        cookieRefresco.leer(request).ifPresent(autenticacionJwtService::cerrarSesion);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresco.eliminar().toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar el usuario del token", description = "Requiere `Authorization: Bearer`. "
            + "Devuelve el correo, el rol y la empresa actuales según la base de datos.")
    @ApiResponse(responseCode = "200", description = "Datos actuales del usuario.")
    public ResponseEntity<SesionDto> actual(Principal principal) {
        return ResponseEntity.ok(sesionService.consultar(principal.getName()));
    }

    @ExceptionHandler(SesionExpiradaException.class)
    ResponseEntity<Map<String, String>> sesionExpirada(SesionExpiradaException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookieRefresco.eliminar().toString())
                .body(Map.of("codigo", "SESION_EXPIRADA", "mensaje", exception.getMessage()));
    }

    private ResponseEntity<TokenAccesoDto> respuesta(SesionJwt sesion) {
        TokenAccesoDto cuerpo = new TokenAccesoDto(sesion.acceso().valor(), TIPO_TOKEN,
                sesion.acceso().duracionEnSegundos(), sesion.usuario());
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok();
        if (sesion.renuevaRefresco()) {
            respuesta.header(HttpHeaders.SET_COOKIE,
                    cookieRefresco.crear(sesion.codigoRefresco(), sesion.refrescoExpiraEn()).toString());
        }
        return respuesta.body(cuerpo);
    }
}
