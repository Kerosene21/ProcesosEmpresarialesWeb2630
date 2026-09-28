package co.edu.javeriana.procesosempresariales.dto;

import java.time.LocalDateTime;

import co.edu.javeriana.procesosempresariales.domain.AccionRolProceso;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class HistorialRolProcesoRespuestaDto {
    private LocalDateTime fecha;
    private String usuarioCorreo;
    private AccionRolProceso accion;
    private String cambiosRealizados;
}
