package co.edu.javeriana.procesosempresariales.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class HistorialProcesoRespuestaDto {
    private LocalDateTime fecha;
    private String usuarioCorreo;
    private String estadoAnterior;
    private String cambiosRealizados;
}
