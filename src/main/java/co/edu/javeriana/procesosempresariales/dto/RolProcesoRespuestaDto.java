package co.edu.javeriana.procesosempresariales.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RolProcesoRespuestaDto {
    private Long id;
    private String nombre;
    private String descripcion;
    private boolean activo;
}
