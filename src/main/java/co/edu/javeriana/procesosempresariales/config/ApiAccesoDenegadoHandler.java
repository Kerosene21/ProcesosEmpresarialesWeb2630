package co.edu.javeriana.procesosempresariales.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ApiAccesoDenegadoHandler implements AccessDeniedHandler {

    static final String CUERPO_JSON =
            "{\"codigo\":\"USUARIO_SIN_PERMISO\",\"mensaje\":\"No tienes permiso para realizar esta accion\"}";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(CUERPO_JSON);
    }
}
