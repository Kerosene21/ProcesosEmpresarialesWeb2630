package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ActividadRespuestaDto {
    private Long id;
    private String nombre;
    private TipoActividad tipo;
    private Long procesoId;
    private Long laneId;
    private Integer posicionX;
    private Integer posicionY;
    private boolean activo;
}
