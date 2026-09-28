package co.edu.javeriana.procesosempresariales.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class LaneRespuestaDto {
    private Long id;
    private String nombre;
    private Long rolProcesoId;
    private Long poolId;
    private int orden;

    public LaneRespuestaDto(Long id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
}
