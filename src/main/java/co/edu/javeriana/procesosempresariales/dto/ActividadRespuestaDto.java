package co.edu.javeriana.procesosempresariales.dto;

import java.util.ArrayList;
import java.util.List;

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
    private String laneNombre;
    private Integer posicionX;
    private Integer posicionY;
    private boolean activo;
    private int arcosDesactivados;
    private List<String> advertencias = new ArrayList<>();
}
