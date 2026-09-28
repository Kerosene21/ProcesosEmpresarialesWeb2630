package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PoolRespuestaDto {
    private Long id;
    private Long procesoId;
    private String nombre;
    private TipoPool tipo;
    private int orden;
    private boolean cajaNegra;
    private boolean activo;
    private Long empresaId;
    private String empresaNombre;
}
